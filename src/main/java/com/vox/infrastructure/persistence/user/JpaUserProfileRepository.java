package com.vox.infrastructure.persistence.user;

import com.vox.infrastructure.persistence.entity.User;
import com.vox.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JpaUserProfileRepository implements UserProfileRepository {

    private final UserRepository userRepository;

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public List<User> findAllByIds(Set<Long> ids) {
        return userRepository.findAllById(ids);
    }
}

