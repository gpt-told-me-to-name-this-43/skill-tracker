package com.skilltracker.security;

import com.skilltracker.domain.User;
import com.skilltracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a bearer token when one is present and otherwise leaves the request anonymous.
 *
 * <p>Rejecting bad credentials outright would change the contract: several read endpoints are public
 * and must keep answering even when the caller sends a stale token.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = extractToken(request);
        if (token.isEmpty()) {
            request.setAttribute(AuthenticationFailure.REQUEST_ATTRIBUTE, AuthenticationFailure.MISSING);
            chain.doFilter(request, response);
            return;
        }

        Integer userId = jwtService.parseSubject(token.get());
        Optional<User> user = userId == null ? Optional.empty() : userRepository.findById(userId);
        if (user.isEmpty()) {
            request.setAttribute(AuthenticationFailure.REQUEST_ATTRIBUTE, AuthenticationFailure.INVALID);
            chain.doFilter(request, response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                user.get(),
                null,
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + user.get().getRole().toUpperCase())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
