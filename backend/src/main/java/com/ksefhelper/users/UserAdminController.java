package com.ksefhelper.users;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disable(@PathVariable UUID userId) {
        userAdminService.setEnabled(userId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/enable")
    public ResponseEntity<Void> enable(@PathVariable UUID userId) {
        userAdminService.setEnabled(userId, true);
        return ResponseEntity.noContent().build();
    }
}
