package com.vox.controller.user;

import com.vox.application.user.LoginResult;
import com.vox.application.user.UserView;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

    public UserResponse toResponse(UserView userView) {
        if (userView == null) {
            return null;
        }
        UserResponse response = new UserResponse();
        response.setUserId(userView.getUserId());
        response.setUsername(userView.getUsername());
        response.setAvatarUrl(userView.getAvatarUrl());
        response.setStatus(userView.getStatus());
        response.setCreatedAt(userView.getCreatedAt());
        return response;
    }

    public com.vox.controller.auth.LoginResponse toLoginResponse(LoginResult loginResult) {
        if (loginResult == null) {
            return null;
        }
        com.vox.controller.auth.LoginResponse response = new com.vox.controller.auth.LoginResponse();
        response.setUserId(loginResult.getUserId());
        response.setUsername(loginResult.getUsername());
        response.setToken(loginResult.getToken());
        response.setExpiresAt(loginResult.getExpiresAt());
        return response;
    }
}
