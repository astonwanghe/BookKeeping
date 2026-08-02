package com.pixledger.api;

import com.pixledger.config.AppProperties;
import com.pixledger.mapper.LedgerMapper;
import com.pixledger.security.JwtService;

import java.security.MessageDigest;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final LedgerMapper mapper;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final JavaMailSender mail;
    private final AppProperties props;

    public AuthController(LedgerMapper mapper, PasswordEncoder passwords, JwtService jwt, JavaMailSender mail, AppProperties props) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.jwt = jwt;
        this.mail = mail;
        this.props = props;
    }

    record Login(String phone, String password) {
    }

    record Email(String email) {
    }

    record Password(String password) {
    }

    record Reset(String token, String password) {
    }

    record Refresh(String refreshToken) {
    }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody Login input) {
        var user = mapper.userByPhone(input.phone());
        if (user == null || !passwords.matches(input.password(), (String) user.get("passwordHash")))
            throw new SecurityException("手机号或密码不正确");
        long id = ((Number) user.get("id")).longValue();
        return session(id, publicUser(user));
    }

    @PostMapping("/refresh")
    Map<String, Object> refresh(@RequestBody Refresh input) {
        var row = mapper.validRefreshToken(hash(input.refreshToken()));
        if (row == null || mapper.revokeRefreshToken(((Number) row.get("id")).longValue()) == 0)
            throw new SecurityException("登录已过期");
        long id = ((Number) row.get("userId")).longValue();
        return session(id, publicUser(mapper.userById(id)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestBody Refresh input) {
        var row = mapper.validRefreshToken(hash(input.refreshToken()));
        if (row != null) mapper.revokeRefreshToken(((Number) row.get("id")).longValue());
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void bindEmail(Authentication auth, @RequestBody Email input) {
        long id = userId(auth);
        validateEmail(input.email());
        mapper.setEmail(id, input.email());
        sendToken(id, input.email(), "VERIFY_EMAIL", "验证邮箱", "/auth/verify-email?token=");
    }

    @GetMapping("/verify-email")
    Map<String, String> verify(@RequestParam String token) {
        long id = consume(token, "VERIFY_EMAIL");
        mapper.verifyEmail(id);
        return Map.of("message", "邮箱验证成功");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgot(@RequestBody Email input) {
        var user = mapper.userByEmail(input.email());
        if (user != null)
            sendToken(((Number) user.get("id")).longValue(), (String) user.get("email"), "RESET_PASSWORD", "重置密码", "/auth/reset-password?token=");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@RequestBody Reset input) {
        long id = consume(input.token(), "RESET_PASSWORD");
        validatePassword(input.password());
        mapper.setPassword(id, passwords.encode(input.password()));
    }

    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    String resetPage(@RequestParam String token) {
        String safe = token.replace("\"", "");
        return """
                <!doctype html><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>重置密码</title>
                <style>body{font:16px -apple-system,sans-serif;max-width:420px;margin:50px auto;padding:24px;background:#fff0d2;color:#1d412b}input,button{box-sizing:border-box;width:100%%;padding:14px;margin:8px 0;font-size:16px}button{background:#d2591f;color:white;border:3px solid #1d412b;font-weight:bold}</style>
                <h1>像素账本</h1><p>设置一个至少 10 位的新密码。</p><input id=p type=password minlength=10 placeholder=新密码><button onclick=go()>重置密码</button><p id=m></p>
                <script>async function go(){let r=await fetch('/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:'%s',password:p.value})});m.textContent=r.ok?'密码已重置，请回到 App 登录。':'重置失败，请检查链接是否已过期。'}</script>
                """.formatted(safe);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(Authentication auth, @RequestBody Password input) {
        validatePassword(input.password());
        mapper.setPassword(userId(auth), passwords.encode(input.password()));
    }

    private Map<String, Object> publicUser(Map<String, Object> u) {
        return Map.of("id", u.get("id"), "phone", u.get("phone"), "email", Optional.ofNullable(u.get("email")).orElse(""), "emailVerified", u.get("emailVerifiedAt") != null);
    }

    private Map<String, Object> session(long id, Map<String, Object> user) {
        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        mapper.createRefreshToken(id, hash(refresh));
        return Map.of("accessToken", jwt.issue(id), "refreshToken", refresh, "user", user);
    }

    private long userId(Authentication a) {
        return (Long) a.getPrincipal();
    }

    private void validateEmail(String e) {
        if (e == null || !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("邮箱格式不正确");
    }

    private void validatePassword(String p) {
        if (p == null || p.length() < 10) throw new IllegalArgumentException("密码至少需要 10 位");
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendToken(long id, String email, String purpose, String subject, String path) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        mapper.createOneTimeToken(id, purpose, hash(token));
        if (props.mailFrom() == null || props.mailFrom().isBlank())
            throw new IllegalArgumentException("邮件服务尚未配置");
        var message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(email);
        message.setSubject("Pixel Ledger：" + subject);
        message.setText("请在 30 分钟内打开此链接：\n" + props.publicUrl() + path + token);
        mail.send(message);
    }

    private long consume(String token, String purpose) {
        var row = mapper.validOneTimeToken(hash(token), purpose);
        if (row == null || mapper.consumeOneTimeToken(((Number) row.get("id")).longValue()) == 0)
            throw new SecurityException("链接无效或已过期");
        return ((Number) row.get("userId")).longValue();
    }
}
