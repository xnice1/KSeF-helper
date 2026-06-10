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
        AppUserPrincipal principal = currentPrincipal();
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new ForbiddenException("Authenticated user was not found."));
    }

    @Transactional(readOnly = true)
    public Membership currentMembership() {
        AppUserPrincipal principal = currentPrincipal();
        if (principal.organizationId() == null) {
            throw new ForbiddenException("Select an organization before using this endpoint.");
        }
        return membershipRepository.findByUserIdAndOrganizationId(principal.id(), principal.organizationId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to the selected organization."));
    }

    @Transactional(readOnly = true)
    public Organization currentOrganization() {
        return currentMembership().getOrganization();
    }

    @Transactional(readOnly = true)
    public UUID currentOrganizationId() {
        return currentOrganization().getId();
    }

    private AppUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ForbiddenException("Authenticated user is required.");
        }
        return principal;
    }
}
