package com.vox.application.user;

import com.vox.infrastructure.persistence.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserViewMapper {

    public UserView toView(User user) {
        if (user == null) {
            return null;
        }
        UserView view = new UserView();
        view.setUserId(user.getId());
        view.setUsername(user.getUsername());
        view.setAvatarUrl(user.getAvatarUrl());
        view.setStatus(user.getStatus());
        view.setCreatedAt(user.getCreateTime());
        return view;
    }
}

