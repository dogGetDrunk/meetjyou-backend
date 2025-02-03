package com.dogGetDrunk.meetjyou.jwt;

import com.dogGetDrunk.meetjyou.common.exception.business.IncorrectJwtSubjectException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtManager {

    private final SecretKey secretKey;
    @Value("${jwt.issuer}")
    private String issuer;
    @Value("${jwt.access-expiration}")
    private Long accessTokenExpiresIn;
    @Value("${jwt.refresh-expiration}")
    private Long refreshTokenExpiresIn;

    public JwtManager(@Value("${jwt.secret-key}") String secretKey) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateAccessToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .issuer(issuer)
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiresIn))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .issuer(issuer)
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiresIn))
                .signWith(secretKey)
                .compact();
    }

    public void validateToken(String token, String email) {
        String emailInToken = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();

        if (!emailInToken.equals(email)) {
            throw new IncorrectJwtSubjectException(email);
        }
    }
}
