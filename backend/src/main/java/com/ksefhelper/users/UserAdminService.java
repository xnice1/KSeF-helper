package com.ksefhelper.users;

import com.ksefhelper.auth.RefreshSessionService;
import com.ksefhelper.common.exception.NotFoundException;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserAdminService {
    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;

    public UserAdminService(UserRepository userRepository, RefreshSessionService refreshSessionService) {
        this.userRepository = userRepository;
        this.refreshSessionService = refreshSessionService;
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        user.setEnabled(enabled);
        user.setCredentialsChangedAt(Instant.now());
        user.incrementTokenVersion();
        userRepository.save(user);
        if (!enabled) {
            refreshSessionService.revokeAll(user);
        }
    }
}
