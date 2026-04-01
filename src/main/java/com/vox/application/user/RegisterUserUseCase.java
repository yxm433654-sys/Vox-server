package com.vox.application.user;

import com.vox.infrastructure.persistence.entity.User;
import com.vox.infrastructure.persistence.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private final UserAccountRepository userAccountRepository;
    private final UserViewMapper userViewMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public UserView execute(com.vox.controller.auth.RegisterRequest request) {
        String username = request.getUsername().trim();
        if (userAccountRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus((byte) 1);

        return userViewMapper.toView(userAccountRepository.save(user));
    }
}

