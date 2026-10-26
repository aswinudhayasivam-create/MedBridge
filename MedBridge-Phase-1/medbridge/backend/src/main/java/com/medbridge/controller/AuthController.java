package com.medbridge.controller;

import com.medbridge.dto.*;
import com.medbridge.service.AuthService;
import com.medbridge.config.RegistrationPolicy;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final RegistrationPolicy registration;
    public AuthController(AuthService auth, RegistrationPolicy registration) { this.auth=auth; this.registration=registration; }
    @PostMapping("/register") public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body) {
        // Reject before the transactional service is invoked or any repository is touched.
        registration.requireRegistration();
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(body));
    }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest body) { return auth.login(body); }
    @GetMapping("/me") public UserResponse me(Authentication authentication) { return auth.me(authentication.getName()); }
}
