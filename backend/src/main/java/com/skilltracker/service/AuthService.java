package com.skilltracker.service;

import com.skilltracker.domain.User;
import com.skilltracker.exception.ConflictException;
import com.skilltracker.exception.UnauthorizedException;
import com.skilltracker.repository.UserRepository;
import com.skilltracker.security.JwtService;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(String email, String username, String password) {
        String normalisedEmail = normaliseEmail(email);

        if (userRepository.findByEmail(normalisedEmail).isPresent()) {
            throw new ConflictException("Email already registered");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ConflictException("Username already taken");
        }

        return userRepository.save(new User(normalisedEmail, username, passwordEncoder.encode(password), "user"));
    }

    @Transactional(readOnly = true)
    public String login(String email, String password) {
        User user = userRepository
                .findByEmail(normaliseEmail(email))
                .filter(candidate -> passwordEncoder.matches(password, candidate.getHashedPassword()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        return jwtService.createAccessToken(user.getId());
    }

    private String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
