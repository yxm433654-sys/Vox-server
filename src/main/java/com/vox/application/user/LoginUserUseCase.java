package com.vox.application.user;

import com.vox.infrastructure.persistence.entity.User;
import com.vox.controller.auth.LoginRequest;
import com.vox.infrastructure.persistence.user.UserAccountRepository;
import com.vox.infrastructure.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUserUseCase {

    private final UserAccountRepository userAccountRepository;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginResult execute(LoginRequest request) {
        User user = userAccountRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        LoginResult result = new LoginResult();
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setToken(tokenService.issueToken(user.getId(), user.getUsername()));
        result.setExpiresAt(tokenService.expiresAt());
        return result;
    }
}

