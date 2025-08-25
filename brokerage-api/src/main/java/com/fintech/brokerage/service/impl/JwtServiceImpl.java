package com.fintech.brokerage.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fintech.brokerage.enums.Role;
import com.fintech.brokerage.service.JwtService;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtServiceImpl implements JwtService{

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtServiceImpl(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-seconds:900}") long ttlSeconds) {
        
    	// Secret key should be 256 bit length at least.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
        
        log.info("JwtService initialized with TTL: {} seconds", ttlSeconds);
    }

    @Override
    public String issueToken(UUID customerId, String username, Role role) {
        Instant now = Instant.now();
        try {
	        String token = Jwts.builder()
	                .subject(username)
	                .claim("customerId", customerId.toString())
	                .claim("role", role.name())
	                .issuedAt(Date.from(now))
	                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
	                .signWith(key, Jwts.SIG.HS256)
	                .compact();
	        
	        log.debug("Issued JWT for customerId: {}, username: {}", customerId, username);
	        return token;
        } catch (IllegalArgumentException | JwtException e) {
            log.error("Failed to issue JWT for customerId: {}, username: {}", customerId, username, e);
            throw e;
        }
    }
}
