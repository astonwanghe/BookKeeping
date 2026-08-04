package com.pixledger.api;

import com.pixledger.domain.UserDO;
import com.pixledger.dto.auth.AuthDtos.*;
import com.pixledger.service.AuthService;
import com.pixledger.service.AuthSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthService authService;

    @PostMapping("/login")
    SessionResponse login(@RequestBody LoginRequest input) {
        long started = System.nanoTime();
        try {
            AuthSession session = authService.login(input.phone(), input.password());
            log.info("auth.login success phone={} userId={} elapsedMs={}",
                    maskPhone(input.phone()), session.user().getId(), elapsedMs(started));
            return sessionResponse(session);
        } catch (RuntimeException exception) {
            log.warn("auth.login failed phone={} reason={} elapsedMs={}",
                    maskPhone(input.phone()), exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    @PostMapping("/refresh")
    SessionResponse refresh(@RequestBody RefreshTokenRequest input) {
        long started = System.nanoTime();
        try {
            AuthSession session = authService.refresh(input.refreshToken());
            log.info("auth.refresh success userId={} elapsedMs={}", session.user().getId(), elapsedMs(started));
            return sessionResponse(session);
        } catch (RuntimeException exception) {
            log.warn("auth.refresh failed reason={} elapsedMs={}", exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestBody RefreshTokenRequest input) {
        long started = System.nanoTime();
        try {
            authService.logout(input.refreshToken());
            log.info("auth.logout success elapsedMs={}", elapsedMs(started));
        } catch (RuntimeException exception) {
            log.warn("auth.logout failed reason={} elapsedMs={}", exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void bindEmail(Authentication auth, @RequestBody EmailRequest input) {
        long userId = userId(auth);
        long started = System.nanoTime();
        try {
            validateEmail(input.email());
            authService.bindEmail(userId, input.email());
            log.info("auth.bind-email success userId={} elapsedMs={}", userId, elapsedMs(started));
        } catch (RuntimeException exception) {
            log.warn("auth.bind-email failed userId={} reason={} elapsedMs={}",
                    userId, exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    @GetMapping("/verify-email")
    MessageResponse verify(@RequestParam String token) {
        long started = System.nanoTime();
        try {
            authService.verifyEmail(token);
            log.info("auth.verify-email success elapsedMs={}", elapsedMs(started));
            return new MessageResponse("邮箱验证成功");
        } catch (RuntimeException exception) {
            log.warn("auth.verify-email failed reason={} elapsedMs={}",
                    exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgot(@RequestBody EmailRequest input) {
        long started = System.nanoTime();
        try {
            authService.forgotPassword(input.email());
            log.info("auth.forgot-password requested elapsedMs={}", elapsedMs(started));
        } catch (RuntimeException exception) {
            log.warn("auth.forgot-password failed reason={} elapsedMs={}",
                    exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@RequestBody ResetPasswordRequest input) {
        long started = System.nanoTime();
        try {
            validatePassword(input.password());
            authService.resetPassword(input.token(), input.password());
            log.info("auth.reset-password success elapsedMs={}", elapsedMs(started));
        } catch (RuntimeException exception) {
            log.warn("auth.reset-password failed reason={} elapsedMs={}",
                    exception.getMessage(), elapsedMs(started));
            throw exception;
        }
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
    void change(Authentication auth, @RequestBody PasswordRequest input) {
        long userId = userId(auth);
        long started = System.nanoTime();
        try {
            validatePassword(input.password());
            authService.changePassword(userId, input.password());
            log.info("auth.change-password success userId={} elapsedMs={}", userId, elapsedMs(started));
        } catch (RuntimeException exception) {
            log.warn("auth.change-password failed userId={} reason={} elapsedMs={}",
                    userId, exception.getMessage(), elapsedMs(started));
            throw exception;
        }
    }

    private SessionResponse sessionResponse(AuthSession session) {
        UserDO user = session.user();
        return new SessionResponse(
                session.accessToken(),
                session.refreshToken(),
                new UserResponse(
                        user.getId(),
                        user.getPhone(),
                        user.getNickname(),
                        user.getEmail() == null ? "" : user.getEmail(),
                        user.getEmailVerifiedAt() != null
                )
        );
    }

    private long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    private void validateEmail(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("密码至少需要 10 位");
        }
    }
}
