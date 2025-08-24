package com.fintech.brokerage.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fintech.brokerage.repo.CustomerRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.crypto.SecretKey;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final String secret;
    private final CustomerRepository customerRepo;

    public JwtAuthFilter(@Value("${app.jwt.secret}") String secret, CustomerRepository customerRepo) {
        this.secret = secret;
        this.customerRepo = customerRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
            	SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            	Claims claims = Jwts.parser()
            	        .verifyWith(key)
            	        .build()
            	        .parseSignedClaims(token)
            	        .getPayload();

                String username = claims.getSubject();
                String customerId = claims.get("customerId", String.class);
                if (username != null && customerId != null && customerRepo.findByUsername(username).isPresent()) {
                    List<GrantedAuthority> auths = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                    Map<String,Object> details = new HashMap<>();
                    details.put("customerId", customerId);
                    Authentication a = new AbstractAuthenticationToken(auths) {
                        @Override public Object getCredentials() { return token; }
                        @Override public Object getPrincipal() { return username; }
                    };
                    ((AbstractAuthenticationToken)a).setDetails(details);
                    ((AbstractAuthenticationToken)a).setAuthenticated(true);
                    SecurityContextHolder.getContext().setAuthentication(a);
                }
            } catch (Exception e) {
                log.warn("Invalid JWT: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
