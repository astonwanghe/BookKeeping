package com.pixledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * 完整 API 集成测试：
 * 启动真实 Spring Boot、Security、MyBatis、Flyway，并通过真实 HTTP 请求访问接口。
 * MySQL 和 Redis 使用 backend/.env.test 中配置的本地服务，不使用 MockMvc 或 Testcontainers。
 */
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.data.redis.database=15")
@SpringBootTest(
        classes = PixelLedgerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ApiIntegrationTest {
    private static final long USER_A_ID = 1001L;
    private static final long USER_B_ID = 1002L;
    private static final String USER_A_PHONE = "13800001001";
    private static final String USER_B_PHONE = "13800001002";
    private static final String PASSWORD = "password-1234";
    private static final String NEW_PASSWORD = "new-password-1234";
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @MockitoBean JavaMailSender mail;
    @LocalServerPort int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private URI baseUri;

    /**
     * 每个测试前重建两个测试用户，并清理这两个用户产生的业务数据。
     * Redis 只清理限流键，避免影响其他 Redis 数据。
     */
    @BeforeEach
    void resetFixture() {
        baseUri = URI.create("http://localhost:" + port + "/");
        Integer conflictingUsers = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM t_user
                WHERE id IN (?, ?)
                  AND phone NOT IN (?, ?)
                """,
                Integer.class, USER_A_ID, USER_B_ID, USER_A_PHONE, USER_B_PHONE);
        assertEquals(0, conflictingUsers,
                "固定测试用户 ID 已被其他用户占用，请改用专用测试数据库或调整测试用户 ID");
        jdbc.update("DELETE FROM t_transaction WHERE user_id IN (?, ?)", USER_A_ID, USER_B_ID);
        jdbc.update("DELETE FROM t_budget WHERE user_id IN (?, ?)", USER_A_ID, USER_B_ID);
        jdbc.update("DELETE FROM t_refresh_token WHERE user_id IN (?, ?)", USER_A_ID, USER_B_ID);
        var oneTimeTokenKeys = redis.keys("auth:one-time:*");
        if (oneTimeTokenKeys != null && !oneTimeTokenKeys.isEmpty()) {
            redis.delete(oneTimeTokenKeys);
        }
        jdbc.update("DELETE FROM t_category WHERE user_id IN (?, ?)", USER_A_ID, USER_B_ID);
        jdbc.update("DELETE FROM t_user WHERE id IN (?, ?)", USER_A_ID, USER_B_ID);
        jdbc.update("""
                INSERT INTO t_user(id, phone, nickname, password_hash, email)
                VALUES (?, ?, ?, ?, NULL), (?, ?, ?, ?, NULL)
                """,
                USER_A_ID, USER_A_PHONE, "用户A", passwordEncoder.encode(PASSWORD),
                USER_B_ID, USER_B_PHONE, "用户B", passwordEncoder.encode(PASSWORD));
        var rateLimitKeys = redis.keys("rate:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            redis.delete(rateLimitKeys);
        }
    }

    /** 未携带 JWT 时，所有 /api 接口都必须拒绝访问。 */
    @Test
    void protectedApiRequiresAccessToken() throws Exception {
        ApiResponse response = request("GET", "/api/categories", null, null);

        assertEquals(401, response.status());
    }

    /** 验证登录、分类、流水、预算、仪表盘和删除流水的完整用户流程。 */
    @Test
    void loginAndLedgerFlowWorksThroughTheRealApplication() throws Exception {
        Session session = login(USER_A_PHONE, PASSWORD);
        assertEquals(USER_A_ID, json(session.raw()).path("user").path("id").asLong());

        ApiResponse categories = request("GET", "/api/categories", session.accessToken(), null);
        assertEquals(200, categories.status());
        long incomeCategory = categoryId(categories, "工资");
        long expenseCategory = categoryId(categories, "餐饮");

        ApiResponse income = request(
                "POST", "/api/transactions", session.accessToken(),
                "{\"categoryId\":" + incomeCategory + ",\"amount\":5000.00,\"occurredOn\":\"2026-08-01\",\"note\":\"八月工资\"}");
        ApiResponse expense = request(
                "POST", "/api/transactions", session.accessToken(),
                "{\"categoryId\":" + expenseCategory + ",\"amount\":120.50,\"occurredOn\":\"2026-08-03\",\"note\":\"午餐\"}");
        assertEquals(201, income.status());
        assertEquals(201, expense.status());

        ApiResponse budget = request(
                "PUT", "/api/budgets/2026-08", session.accessToken(),
                "{\"amount\":3000.00}");
        assertEquals(204, budget.status());

        ApiResponse dashboard = request("GET", "/api/dashboard?month=2026-08", session.accessToken(), null);
        assertEquals(200, dashboard.status());
        JsonNode dashboardJson = json(dashboard);
        assertEquals("2026-08", dashboardJson.path("month").asText());
        assertEquals(5000.00, dashboardJson.path("income").asDouble(), 0.001);
        assertEquals(120.50, dashboardJson.path("expense").asDouble(), 0.001);
        assertEquals(4879.50, dashboardJson.path("balance").asDouble(), 0.001);
        assertEquals(1, dashboardJson.path("expenseBreakdown").size());
        assertEquals(1, dashboardJson.path("budgets").size());

        ApiResponse transactions = request("GET", "/api/transactions?month=2026-08", session.accessToken(), null);
        assertEquals(200, transactions.status());
        assertEquals(2, json(transactions).size());

        long expenseId = json(expense).path("id").asLong();
        ApiResponse delete = request("DELETE", "/api/transactions/" + expenseId, session.accessToken(), null);
        assertEquals(204, delete.status());
        assertEquals(1, json(request("GET", "/api/transactions?month=2026-08", session.accessToken(), null)).size());
    }

    /** 验证一个用户不能读取或修改另一个用户的分类和流水。 */
    @Test
    void userDataCannotBeReadOrChangedByAnotherUser() throws Exception {
        Session userA = login(USER_A_PHONE, PASSWORD);
        Session userB = login(USER_B_PHONE, PASSWORD);

        ApiResponse category = request(
                "POST", "/api/categories", userA.accessToken(),
                "{\"name\":\"仅用户A可见\",\"type\":\"EXPENSE\",\"icon\":\"lock\"}");
        assertEquals(201, category.status());
        long privateCategoryId = json(category).path("id").asLong();

        ApiResponse userBCategories = request("GET", "/api/categories", userB.accessToken(), null);
        assertEquals(200, userBCategories.status());
        assertFalse(containsName(json(userBCategories), "仅用户A可见"));

        ApiResponse transaction = request(
                "POST", "/api/transactions", userA.accessToken(),
                "{\"categoryId\":" + privateCategoryId + ",\"amount\":88.00,\"occurredOn\":\"2026-08-03\"}");
        assertEquals(201, transaction.status());
        long transactionId = json(transaction).path("id").asLong();

        ApiResponse userBTransactions = request(
                "GET", "/api/transactions?month=2026-08", userB.accessToken(), null);
        assertEquals(200, userBTransactions.status());
        assertEquals(0, json(userBTransactions).size());

        ApiResponse editByUserB = request(
                "PUT", "/api/transactions/" + transactionId, userB.accessToken(),
                "{\"categoryId\":" + privateCategoryId + ",\"amount\":99.00,\"occurredOn\":\"2026-08-03\"}");
        ApiResponse deleteByUserB = request(
                "DELETE", "/api/transactions/" + transactionId, userB.accessToken(), null);
        assertEquals(400, editByUserB.status());
        assertEquals(400, deleteByUserB.status());
    }

    /** Refresh Token 使用后立即轮换，旧令牌再次使用必须失败。 */
    @Test
    void refreshTokenIsRotatedAndReplayIsRejected() throws Exception {
        Session initial = login(USER_A_PHONE, PASSWORD);

        ApiResponse refreshed = request(
                "POST", "/auth/refresh", null,
                "{\"refreshToken\":\"" + initial.refreshToken() + "\"}");
        assertEquals(200, refreshed.status());
        Session rotated = session(refreshed);
        assertNotEquals(initial.refreshToken(), rotated.refreshToken());

        ApiResponse replay = request(
                "POST", "/auth/refresh", null,
                "{\"refreshToken\":\"" + initial.refreshToken() + "\"}");
        assertEquals(401, replay.status());

        ApiResponse secondRefresh = request(
                "POST", "/auth/refresh", null,
                "{\"refreshToken\":\"" + rotated.refreshToken() + "\"}");
        assertEquals(200, secondRefresh.status());
    }

    /** 修改密码后旧密码失效，新密码可以重新登录。 */
    @Test
    void passwordChangeInvalidatesOldPassword() throws Exception {
        Session session = login(USER_A_PHONE, PASSWORD);

        ApiResponse change = request(
                "POST", "/auth/password", session.accessToken(), "{\"password\":\"" + NEW_PASSWORD + "\"}");
        assertEquals(204, change.status());
        assertEquals(401, loginResponse(USER_A_PHONE, PASSWORD).status());
        assertEquals(200, loginResponse(USER_A_PHONE, NEW_PASSWORD).status());
    }

    /** 邮箱验证和密码重置链接都只能消费一次。 */
    @Test
    void emailVerificationAndPasswordResetAreSingleUseFlows() throws Exception {
        Session session = login(USER_A_PHONE, PASSWORD);

        ApiResponse bind = request(
                "POST", "/auth/email", session.accessToken(),
                "{\"email\":\"user-a@pixel-ledger.test\"}");
        assertEquals(204, bind.status());
        String verificationToken = lastMailToken();
        assertOneTimeTokenTtl();

        ApiResponse verify = request("GET", "/auth/verify-email?token=" + verificationToken, null, null);
        assertEquals(200, verify.status());
        var remainingOneTimeTokenKeys = redis.keys("auth:one-time:*");
        assertTrue(remainingOneTimeTokenKeys == null || remainingOneTimeTokenKeys.isEmpty());
        assertEquals(401, request("GET", "/auth/verify-email?token=" + verificationToken, null, null).status());

        ApiResponse forgot = request(
                "POST", "/auth/forgot-password", null,
                "{\"email\":\"user-a@pixel-ledger.test\"}");
        assertEquals(204, forgot.status());
        String resetToken = lastMailToken();
        assertOneTimeTokenTtl();

        ApiResponse reset = request(
                "POST", "/auth/reset-password", null,
                "{\"token\":\"" + resetToken + "\",\"password\":\"" + NEW_PASSWORD + "\"}");
        assertEquals(204, reset.status());
        assertEquals(401, request(
                "POST", "/auth/reset-password", null,
                "{\"token\":\"" + resetToken + "\",\"password\":\"another-password-1234\"}").status());
        assertEquals(200, loginResponse(USER_A_PHONE, NEW_PASSWORD).status());
    }

    /** 验证金额精度和月份格式错误会通过真实 HTTP 接口返回 400。 */
    @Test
    void invalidAmountAndMonthAreRejectedByTheRealHttpApi() throws Exception {
        Session session = login(USER_A_PHONE, PASSWORD);
        long categoryId = categoryId(request("GET", "/api/categories", session.accessToken(), null), "餐饮");

        ApiResponse amount = request(
                "POST", "/api/transactions", session.accessToken(),
                "{\"categoryId\":" + categoryId + ",\"amount\":12.345,\"occurredOn\":\"2026-08-03\"}");
        ApiResponse month = request(
                "GET", "/api/transactions?month=2026/08", session.accessToken(), null);

        assertEquals(400, amount.status());
        assertEquals("金额必须为最多两位小数的正数", json(amount).path("error").asText());
        assertEquals(400, month.status());
        assertEquals("月份格式应为 YYYY-MM", json(month).path("error").asText());
    }

    /** 验证登录限流实际由 Redis 支撑，超过 10 次后返回 429。 */
    @Test
    void loginRateLimitIsBackedByRedis() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(401, loginResponse(USER_A_PHONE, "wrong-password").status());
        }

        ApiResponse limited = loginResponse(USER_A_PHONE, "wrong-password");
        assertEquals(429, limited.status());
        assertEquals("操作过于频繁，请稍后再试", json(limited).path("error").asText());
    }

    /** 使用真实接口登录，并把响应中的两个令牌封装为测试会话。 */
    private Session login(String phone, String password) throws Exception {
        ApiResponse response = loginResponse(phone, password);
        assertEquals(200, response.status(), response.body());
        return session(response);
    }

    /** 发起登录请求，但不强制要求成功，便于测试 401 和 429。 */
    private ApiResponse loginResponse(String phone, String password) throws Exception {
        return request(
                "POST", "/auth/login", null,
                "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
    }

    /** 将登录响应解析为后续请求使用的会话。 */
    private Session session(ApiResponse response) throws IOException {
        JsonNode body = json(response);
        return new Session(body.path("accessToken").asText(), body.path("refreshToken").asText(), response);
    }

    /** 从分类接口响应中按名称查找分类 ID，避免测试依赖固定自增 ID。 */
    private long categoryId(ApiResponse response, String name) throws IOException {
        Iterator<JsonNode> categories = json(response).elements();
        while (categories.hasNext()) {
            JsonNode category = categories.next();
            if (Objects.equals(name, category.path("name").asText())) {
                return category.path("id").asLong();
            }
        }
        fail("分类不存在：" + name);
        return -1;
    }

    /** 判断分类列表中是否存在指定名称。 */
    private boolean containsName(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    /** 从测试替身捕获最近一次邮件正文中的一次性令牌。 */
    private String lastMailToken() {
        var message = forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(message.capture());
        var messages = message.getAllValues();
        String text = messages.get(messages.size() - 1).getText();
        int tokenStart = text.lastIndexOf("token=");
        assertTrue(tokenStart >= 0, "邮件正文未包含 token");
        return text.substring(tokenStart + "token=".length()).trim();
    }

    /** 验证一次性令牌存入 Redis，并拥有不超过 30 分钟的 TTL。 */
    private void assertOneTimeTokenTtl() {
        var keys = redis.keys("auth:one-time:*");
        assertNotNull(keys);
        assertEquals(1, keys.size());
        long ttl = redis.getExpire(keys.iterator().next());
        assertTrue(ttl > 0 && ttl <= 30 * 60, "一次性令牌 TTL 不正确：" + ttl);
    }

    /** 将 HTTP 响应正文解析为 JSON。 */
    private JsonNode json(ApiResponse response) throws IOException {
        return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
    }

    /** 通过 JDK HttpClient 发起真实 HTTP 请求。 */
    private ApiResponse request(String method, String path, String accessToken, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        HttpRequest request = builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ApiResponse(response.statusCode(), response.body());
    }

    private record ApiResponse(int status, String body) {}
    private record Session(String accessToken, String refreshToken, ApiResponse raw) {}
}
