package com.pixledger.dto.auth;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(String phone, String password) {}
    public record EmailRequest(String email) {}
    public record PasswordRequest(String password) {}
    public record ResetPasswordRequest(String token, String password) {}
    public record RefreshTokenRequest(String refreshToken) {}
    public record UserResponse(long id, String phone, String nickname, String email, boolean emailVerified) {}
    public record SessionResponse(String accessToken, String refreshToken, UserResponse user) {}
    public record MessageResponse(String message) {}
}
