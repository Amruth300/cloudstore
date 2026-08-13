package com.cloudstore.service;

import com.cloudstore.dto.auth.AuthResponse;
import com.cloudstore.dto.auth.LoginRequest;
import com.cloudstore.dto.auth.RegisterRequest;
import com.cloudstore.entity.Role;
import com.cloudstore.entity.User;
import com.cloudstore.entity.enums.RoleName;
import com.cloudstore.exception.DuplicateResourceException;
import com.cloudstore.repository.RoleRepository;
import com.cloudstore.repository.UserRepository;
import com.cloudstore.security.JwtService;
import com.cloudstore.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Default CUSTOMER role is not seeded"));

        User user = User.builder()
                .email(request.email().toLowerCase())
                .fullName(request.fullName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(customerRole)
                .enabled(true)
                .build();

        userRepository.save(user);

        SecurityUser principal = new SecurityUser(user);
        String token = jwtService.generateToken(principal, user.getRole().getName().name());
        return AuthResponse.of(token, jwtService.getExpirationSeconds(), user.getEmail(), user.getRole().getName().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password())
        );

        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished unexpectedly"));

        SecurityUser principal = new SecurityUser(user);
        String token = jwtService.generateToken(principal, user.getRole().getName().name());
        return AuthResponse.of(token, jwtService.getExpirationSeconds(), user.getEmail(), user.getRole().getName().name());
    }
}
