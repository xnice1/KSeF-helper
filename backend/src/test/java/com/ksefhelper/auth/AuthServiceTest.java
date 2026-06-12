package com.ksefhelper.auth;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.auth.entity.AuthSession;
import com.ksefhelper.auth.mail.AccountMailService;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.entity.OrganizationType;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.organizations.repository.OrganizationRepository;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.security.JwtService;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = new JwtService(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            Duration.ofMinutes(10)
    );
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final CurrentUserService currentUserService = null;
    private final RefreshSessionService refreshSessionService = mock(RefreshSessionService.class);
    private final AccountTokenService accountTokenService = mock(AccountTokenService.class);
    private final AccountMailService accountMailService = mock(AccountMailService.class);
    private final AuditEventService auditEventService = mock(AuditEventService.class);

    private final AuthService authService = new AuthService(
            userRepository,
            organizationRepository,
            membershipRepository,
            passwordEncoder,
            jwtService,
            authenticationManager,
            currentUserService,
            refreshSessionService,
            accountTokenService,
            accountMailService,
            auditEventService,
            false,
            Duration.ofHours(24),
            Duration.ofMinutes(30)
    );

    @Test
    void registerHashesPasswordAndCreatesOwnerMembership() {
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
            return organization;
        });
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshSessionService.create(any(User.class), any(Organization.class))).thenReturn(
                new RefreshSessionService.IssuedRefreshToken(
                        "refresh-token",
                        Instant.now().plus(Duration.ofDays(30)),
                        mock(AuthSession.class)
                )
        );

        authService.register(new RegisterRequest(
                "OWNER@EXAMPLE.COM",
                "strong-password",
                "Ola",
                "Nowak",
                "Nowak Studio",
                OrganizationType.BUSINESS
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);
        verify(userRepository).save(userCaptor.capture());
        verify(membershipRepository).save(membershipCaptor.capture());

        assertThat(userCaptor.getValue().getEmail()).isEqualTo("owner@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(MembershipRole.OWNER);
    }
}
