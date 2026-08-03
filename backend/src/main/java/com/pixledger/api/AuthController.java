package com.pixledger.api;

import com.pixledger.config.AppProperties;
import com.pixledger.domain.TokenDO;
import com.pixledger.domain.UserDO;
import com.pixledger.dto.auth.AuthDtos.*;
import com.pixledger.mapper.LedgerMapper;
import com.pixledger.security.JwtService;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
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
        this.mapper = mapper; this.passwords = passwords; this.jwt = jwt; this.mail = mail; this.props = props;
    }

    @PostMapping("/login")
    SessionResponse login(@RequestBody LoginRequest input) {
        UserDO user = mapper.userByPhone(input.phone());
        if (user == null || !passwords.matches(input.password(), user.getPasswordHash())) throw new SecurityException("手机号或密码不正确");
        return session(user.getId(), publicUser(user));
    }

    @PostMapping("/refresh")
    SessionResponse refresh(@RequestBody RefreshTokenRequest input) {
        TokenDO token = mapper.validRefreshToken(hash(input.refreshToken()));
        if (token == null || mapper.revokeRefreshToken(token.getId()) == 0) throw new SecurityException("登录已过期");
        UserDO user = mapper.userById(token.getUserId());
        if (user == null) throw new SecurityException("用户不存在");
        return session(token.getUserId(), publicUser(user));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestBody RefreshTokenRequest input) {
        TokenDO token = mapper.validRefreshToken(hash(input.refreshToken()));
        if (token != null) mapper.revokeRefreshToken(token.getId());
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void bindEmail(Authentication auth, @RequestBody EmailRequest input) {
        long id = userId(auth); validateEmail(input.email()); mapper.setEmail(id, input.email());
        sendToken(id, input.email(), "VERIFY_EMAIL", "验证邮箱", "/auth/verify-email?token=");
    }

    @GetMapping("/verify-email")
    MessageResponse verify(@RequestParam String token) { mapper.verifyEmail(consume(token, "VERIFY_EMAIL")); return new MessageResponse("邮箱验证成功"); }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgot(@RequestBody EmailRequest input) {
        UserDO user = mapper.userByEmail(input.email());
        if (user != null) sendToken(user.getId(), user.getEmail(), "RESET_PASSWORD", "重置密码", "/auth/reset-password?token=");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@RequestBody ResetPasswordRequest input) {
        long id = consume(input.token(), "RESET_PASSWORD"); validatePassword(input.password()); mapper.setPassword(id, passwords.encode(input.password()));
    }

    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    String resetPage(@RequestParam String token) {
        String safe = token.replace("\"", "");
        return """
                <!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>重置密码</title>
                <style>body{font:16px -apple-system,sans-serif;max-width:420px;margin:50px auto;padding:24px;background:#fff0d2;color:#1d412b}input,button{box-sizing:border-box;width:100%%;padding:14px;margin:8px 0;font-size:16px}button{background:#d2591f;color:white;border:3px solid #1d412b;font-weight:bold}</style>
                <h1>像素账本</h1><p>设置一个至少 10 位的新密码。</p><input id=p type=password minlength=10 placeholder=新密码><button onclick=go()>重置密码</button><p id=m></p>
                <script>async function go(){let r=await fetch("/auth/reset-password",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({token:"%s",password:p.value})});m.textContent=r.ok?"密码已重置，请回到 App 登录。":"重置失败，请检查链接是否已过期。"}</script>
                """.formatted(safe);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(Authentication auth, @RequestBody PasswordRequest input) { validatePassword(input.password()); mapper.setPassword(userId(auth), passwords.encode(input.password())); }

    private UserResponse publicUser(UserDO user) { return new UserResponse(user.getId(), user.getPhone(), user.getNickname(), user.getEmail() == null ? "" : user.getEmail(), user.getEmailVerifiedAt() != null); }
    private SessionResponse session(long id, UserResponse user) { String refresh = UUID.randomUUID() + "." + UUID.randomUUID(); mapper.createRefreshToken(id, hash(refresh)); return new SessionResponse(jwt.issue(id), refresh, user); }
    private long userId(Authentication authentication) { return (Long) authentication.getPrincipal(); }
    private void validateEmail(String email) { if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("邮箱格式不正确"); }
    private void validatePassword(String password) { if (password == null || password.length() < 10) throw new IllegalArgumentException("密码至少需要 10 位"); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes())); } catch (Exception exception) { throw new IllegalStateException(exception); } }

    private void sendToken(long id, String email, String purpose, String subject, String path) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID(); mapper.createOneTimeToken(id, purpose, hash(token));
        if (props.mailFrom() == null || props.mailFrom().isBlank()) throw new IllegalArgumentException("邮件服务尚未配置");
        SimpleMailMessage message = new SimpleMailMessage(); message.setFrom(props.mailFrom()); message.setTo(email); message.setSubject("Pixel Ledger：" + subject);
        message.setText("请在 30 分钟内打开此链接：\n" + props.publicUrl() + path + token); mail.send(message);
    }

    private long consume(String token, String purpose) {
        TokenDO oneTimeToken = mapper.validOneTimeToken(hash(token), purpose);
        if (oneTimeToken == null || mapper.consumeOneTimeToken(oneTimeToken.getId()) == 0) throw new SecurityException("链接无效或已过期");
        return oneTimeToken.getUserId();
    }
}
