package com.pixledger.service;

import com.pixledger.config.AppProperties;
import com.pixledger.domain.TokenDO;
import com.pixledger.domain.UserDO;
import com.pixledger.mapper.LedgerMapper;
import com.pixledger.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final LedgerMapper mapper;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final JavaMailSender mail;
    private final AppProperties props;

    public AuthService(
            LedgerMapper mapper,
            PasswordEncoder passwords,
            JwtService jwt,
            JavaMailSender mail,
            AppProperties props
    ) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.jwt = jwt;
        this.mail = mail;
        this.props = props;
    }

    public AuthSession login(String phone, String password) {
        UserDO user = mapper.userByPhone(phone);
        if (user == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new SecurityException("手机号或密码不正确");
        }
        return issueSession(user);
    }

    public AuthSession refresh(String refreshToken) {
        TokenDO token = mapper.validRefreshToken(hash(refreshToken));
        if (token == null || mapper.revokeRefreshToken(token.getId()) == 0) {
            throw new SecurityException("登录已过期");
        }
        UserDO user = mapper.userById(token.getUserId());
        if (user == null) {
            throw new SecurityException("用户不存在");
        }
        return issueSession(user);
    }

    public void logout(String refreshToken) {
        TokenDO token = mapper.validRefreshToken(hash(refreshToken));
        if (token != null) {
            mapper.revokeRefreshToken(token.getId());
        }
    }

    public void bindEmail(long userId, String email) {
        mapper.setEmail(userId, email);
        sendToken(userId, email, "VERIFY_EMAIL", "验证邮箱", "/auth/verify-email?token=");
    }

    public void verifyEmail(String token) {
        mapper.verifyEmail(consume(token, "VERIFY_EMAIL"));
    }

    public void forgotPassword(String email) {
        UserDO user = mapper.userByEmail(email);
        if (user != null) {
            sendToken(user.getId(), user.getEmail(), "RESET_PASSWORD", "重置密码", "/auth/reset-password?token=");
        }
    }

    public void resetPassword(String token, String password) {
        long userId = consume(token, "RESET_PASSWORD");
        mapper.setPassword(userId, passwords.encode(password));
    }

    public void changePassword(long userId, String password) {
        mapper.setPassword(userId, passwords.encode(password));
    }

    private AuthSession issueSession(UserDO user) {
        String refreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        mapper.createRefreshToken(user.getId(), hash(refreshToken));
        return new AuthSession(jwt.issue(user.getId()), refreshToken, user);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void sendToken(long userId, String email, String purpose, String subject, String path) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        mapper.createOneTimeToken(userId, purpose, hash(token));
        if (props.mailFrom() == null || props.mailFrom().isBlank()) {
            throw new IllegalArgumentException("邮件服务尚未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(email);
        message.setSubject("Pixel Ledger：" + subject);
        message.setText("请在 30 分钟内打开此链接：\n" + props.publicUrl() + path + token);
        mail.send(message);
    }

    private long consume(String token, String purpose) {
        TokenDO oneTimeToken = mapper.validOneTimeToken(hash(token), purpose);
        if (oneTimeToken == null || mapper.consumeOneTimeToken(oneTimeToken.getId()) == 0) {
            throw new SecurityException("链接无效或已过期");
        }
        return oneTimeToken.getUserId();
    }
}
