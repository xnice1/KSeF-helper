package com.ksefhelper.users;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.organizations.DataDeletionService;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AccountService {
    private final CurrentUserService currentUserService;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataDeletionService dataDeletionService;
    private final AuditEventService auditEventService;

    public AccountService(
            CurrentUserService currentUserService,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DataDeletionService dataDeletionService,
            AuditEventService auditEventService
    ) {
        this.currentUserService = currentUserService;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataDeletionService = dataDeletionService;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public void deleteAccount(String password, String confirmation) {
        if (!"DELETE".equals(confirmation)) {
            throw new BadRequestException("Type DELETE to confirm permanent account deletion.");
        }
        User user = currentUserService.currentUser();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect.");
        }

        List<Membership> memberships = membershipRepository.findAllByUserIdOrderByOrganizationNameAsc(user.getId());
        for (Membership membership : memberships) {
            if (membership.getRole() != MembershipRole.OWNER) {
                continue;
            }
            var organization = membership.getOrganization();
            long ownerCount = membershipRepository.countByOrganizationIdAndRole(
                    organization.getId(),
                    MembershipRole.OWNER
            );
            long memberCount = membershipRepository.countByOrganizationId(organization.getId());
            if (ownerCount == 1 && memberCount > 1) {
                throw new BadRequestException(
                        "Transfer ownership or delete organization '" + organization.getName()
                                + "' before deleting your account."
                );
            }
        }

        for (Membership membership : memberships) {
            if (membership.getRole() == MembershipRole.OWNER
                    && membershipRepository.countByOrganizationId(membership.getOrganization().getId()) == 1) {
                dataDeletionService.deleteOrganization(membership.getOrganization(), user, "account_deletion");
            }
        }

        auditEventService.recordForUser(
                AuditEventType.ACCOUNT_DELETED,
                user,
                null,
                "user",
                user.getId(),
                Map.of("email", user.getEmail())
        );
        userRepository.deleteAccountDataById(user.getId());
    }
}
