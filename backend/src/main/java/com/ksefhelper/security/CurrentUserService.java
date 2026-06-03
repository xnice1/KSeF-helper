package com.ksefhelper.security;

import com.ksefhelper.common.exception.ForbiddenException;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    public CurrentUserService(UserRepository userRepository, MembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Authenticated user is required.");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("Authenticated user was not found."));
    }

    @Transactional(readOnly = true)
    public Membership currentMembership() {
        User user = currentUser();
        return membershipRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                .orElseThrow(() -> new ForbiddenException("User does not belong to an organization."));
    }

    @Transactional(readOnly = true)
    public Organization currentOrganization() {
        return currentMembership().getOrganization();
    }

    @Transactional(readOnly = true)
    public UUID currentOrganizationId() {
        return currentOrganization().getId();
    }
}
