package com.skilltracker.controller;

import com.skilltracker.domain.User;
import com.skilltracker.dto.LoginRequest;
import com.skilltracker.dto.RegisterRequest;
import com.skilltracker.dto.TokenResponse;
import com.skilltracker.dto.UserReadResponse;
import com.skilltracker.security.CurrentUser;
import com.skilltracker.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserReadResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserReadResponse.from(authService.register(request.email(), request.username(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.bearer(authService.login(request.email(), request.password()));
    }

    @GetMapping("/me")
    public UserReadResponse me(@CurrentUser User currentUser) {
        return UserReadResponse.from(currentUser);
    }
}
