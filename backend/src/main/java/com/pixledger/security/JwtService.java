package com.pixledger.security;

import com.pixledger.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(long userId) {
        return Jwts.builder().subject(Long.toString(userId)).issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(900))).signWith(key).compact();
    }

    public long userId(String token) {
        return Long.parseLong(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
    }
}
