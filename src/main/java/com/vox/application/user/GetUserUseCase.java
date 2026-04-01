package com.vox.application.user;

import com.vox.infrastructure.persistence.entity.User;
import com.vox.infrastructure.persistence.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetUserUseCase {

    private final UserAccountRepository userAccountRepository;
    private final UserViewMapper userViewMapper;

    public UserView byId(Long id) {
        User user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return userViewMapper.toView(user);
    }

    public UserView byUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        User user = userAccountRepository.findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return userViewMapper.toView(user);
    }
}

