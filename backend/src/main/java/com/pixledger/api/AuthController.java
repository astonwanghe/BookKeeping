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

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/login")
    SessionResponse login(@RequestBody LoginRequest input) {
        return sessionResponse(authService.login(input.phone(), input.password()));
    }

    @PostMapping("/refresh")
    SessionResponse refresh(@RequestBody RefreshTokenRequest input) {
        return sessionResponse(authService.refresh(input.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestBody RefreshTokenRequest input) {
        authService.logout(input.refreshToken());
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void bindEmail(Authentication auth, @RequestBody EmailRequest input) {
        validateEmail(input.email());
        authService.bindEmail(userId(auth), input.email());
    }

    @GetMapping("/verify-email")
    MessageResponse verify(@RequestParam String token) {
        authService.verifyEmail(token);
        return new MessageResponse("邮箱验证成功");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgot(@RequestBody EmailRequest input) {
        authService.forgotPassword(input.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@RequestBody ResetPasswordRequest input) {
        validatePassword(input.password());
        authService.resetPassword(input.token(), input.password());
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
        validatePassword(input.password());
        authService.changePassword(userId(auth), input.password());
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
