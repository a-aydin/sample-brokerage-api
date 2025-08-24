package com.fintech.brokerage.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.fintech.brokerage.controller.dto.LoginRequest;
import com.fintech.brokerage.controller.dto.TokenResponse;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.repo.CustomerRepository;
import com.fintech.brokerage.service.JwtService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final CustomerRepository customerRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(CustomerRepository customerRepo, PasswordEncoder encoder, JwtService jwt) {
        this.customerRepo = customerRepo;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("Login attempt for username: {}", req.getUsername());

        Customer c = customerRepo.findByUsername(req.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed - username not found: {}", req.getUsername());
                    return new RuntimeException("Invalid credentials");
                });

        if (!encoder.matches(req.getPassword(), c.getPasswordHash())) {
            log.warn("Login failed - invalid password for username: {}", req.getUsername());
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwt.issueToken(c.getId(), c.getUsername(), c.getRole());
        log.info("Login successful for username: {}", req.getUsername());

        return ResponseEntity.ok(new TokenResponse(token));
    }
}
