package com.ksefhelper.auth;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.auth.entity.AccountTokenType;
import com.ksefhelper.auth.mail.AccountMailService;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.common.exception.ForbiddenException;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.organizations.repository.OrganizationRepository;
import com.ksefhelper.security.AppUserPrincipal;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.security.JwtService;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CurrentUserService currentUserService;
    private final RefreshSessionService refreshSessionService;
    private final AccountTokenService accountTokenService;
    private final AccountMailService accountMailService;
    private final boolean emailVerificationRequired;
    private final Duration verificationExpiration;
    private final Duration passwordResetExpiration;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            CurrentUserService currentUserService,
            RefreshSessionService refreshSessionService,
            AccountTokenService accountTokenService,
            AccountMailService accountMailService,
            @Value("${app.auth.email-verification-required}") boolean emailVerificationRequired,
            @Value("${app.auth.email-verification-expiration}") Duration verificationExpiration,
            @Value("${app.auth.password-reset-expiration}") Duration passwordResetExpiration
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.currentUserService = currentUserService;
        this.refreshSessionService = refreshSessionService;
        this.accountTokenService = accountTokenService;
        this.accountMailService = accountMailService;
        this.emailVerificationRequired = emailVerificationRequired;
        this.verificationExpiration = verificationExpiration;
        this.passwordResetExpiration = passwordResetExpiration;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("An account with this email already exists.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(!emailVerificationRequired);
        user.setCredentialsChangedAt(Instant.now());
        User savedUser = userRepository.save(user);

        Organization organization = new Organization();
        organization.setName(request.organizationName().trim());
        organization.setType(request.organizationType());
        Organization savedOrganization = organizationRepository.save(organization);

        Membership membership = new Membership();
        membership.setUser(savedUser);
        membership.setOrganization(savedOrganization);
        membership.setRole(MembershipRole.OWNER);
        Membership savedMembership = membershipRepository.save(membership);
        List<Membership> memberships = List.of(savedMembership);

        if (emailVerificationRequired) {
            sendVerification(savedUser);
            return new AuthResult(response(savedUser, savedMembership, memberships, null), null, null);
        }
        return issueAuthenticated(savedUser, savedMembership, memberships);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
        ));

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password."));
        List<Membership> memberships = memberships(user);
        Membership membership = memberships.size() == 1 ? memberships.getFirst() : null;
        return issueAuthenticated(user, membership, memberships);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshSessionService.RotatedRefreshToken rotated = refreshSessionService.rotate(rawRefreshToken);
        User user = rotated.user();
        if (!user.isEnabled() || !user.isEmailVerified()) {
            throw new ForbiddenException("This account is not active.");
        }
        List<Membership> memberships = memberships(user);
        Membership membership = membershipFor(user, rotated.activeOrganization());
        AuthResponse response = authenticatedResponse(user, membership, memberships);
        return new AuthResult(response, rotated.rawToken(), rotated.expiresAt());
    }

    @Transactional(readOnly = true)
    public AuthResponse me() {
        User user = currentUserService.currentUser();
        List<Membership> memberships = memberships(user);
        Membership activeMembership = null;
        try {
            activeMembership = currentUserService.currentMembership();
        } catch (ForbiddenException ignored) {
            // An unscoped access token is valid while the user selects an organization.
        }
        return response(user, activeMembership, memberships, null);
    }

    @Transactional
    public AuthResponse switchOrganization(UUID organizationId, String rawRefreshToken) {
        User user = currentUserService.currentUser();
        Membership membership = membershipRepository.findByUserIdAndOrganizationId(user.getId(), organizationId)
                .orElseThrow(() -> new ForbiddenException("You do not belong to the selected organization."));
        refreshSessionService.updateOrganization(rawRefreshToken, membership.getOrganization());
        return authenticatedResponse(user, membership, memberships(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshSessionService.revoke(rawRefreshToken);
    }

    @Transactional
    public void requestVerification(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(User::isEnabled)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerification);
    }

    @Transactional
    public AuthResult verifyEmail(String rawToken) {
        User user = accountTokenService.consume(rawToken, AccountTokenType.EMAIL_VERIFICATION);
        user.setEmailVerified(true);
        userRepository.save(user);
        List<Membership> memberships = memberships(user);
        Membership membership = memberships.size() == 1 ? memberships.getFirst() : null;
        return issueAuthenticated(user, membership, memberships);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(User::isEnabled)
                .ifPresent(user -> {
                    String token = accountTokenService.issue(
                            user,
                            AccountTokenType.PASSWORD_RESET,
                            passwordResetExpiration
                    );
                    accountMailService.sendPasswordReset(user, token);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = accountTokenService.consume(rawToken, AccountTokenType.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialsChangedAt(Instant.now());
        user.incrementTokenVersion();
        userRepository.save(user);
        refreshSessionService.revokeAll(user);
    }

    private AuthResult issueAuthenticated(User user, Membership membership, List<Membership> memberships) {
        Organization organization = membership == null ? null : membership.getOrganization();
        RefreshSessionService.IssuedRefreshToken refreshToken = refreshSessionService.create(user, organization);
        return new AuthResult(
                authenticatedResponse(user, membership, memberships),
                refreshToken.rawToken(),
                refreshToken.expiresAt()
        );
    }

    private AuthResponse authenticatedResponse(User user, Membership membership, List<Membership> memberships) {
        UUID organizationId = membership == null ? null : membership.getOrganization().getId();
        String accessToken = jwtService.generateToken(new AppUserPrincipal(user).withOrganizationId(organizationId));
        return response(user, membership, memberships, accessToken);
    }

    private AuthResponse response(
            User user,
            Membership membership,
            List<Membership> memberships,
            String accessToken
    ) {
        return new AuthResponse(
                accessToken,
                accessToken == null ? null : jwtService.accessTokenExpiresAt(),
                new AuthResponse.UserProfile(
                        user.getId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.isEmailVerified()
                ),
                membership == null ? null : organizationProfile(membership),
                memberships.stream().map(this::organizationProfile).toList()
        );
    }

    private void sendVerification(User user) {
        String token = accountTokenService.issue(
                user,
                AccountTokenType.EMAIL_VERIFICATION,
                verificationExpiration
        );
        accountMailService.sendVerification(user, token);
    }

    private Membership membershipFor(User user, Organization organization) {
        if (organization == null) {
            return null;
        }
        return membershipRepository.findByUserIdAndOrganizationId(user.getId(), organization.getId()).orElse(null);
    }

    private List<Membership> memberships(User user) {
        List<Membership> memberships = membershipRepository.findAllByUserIdOrderByOrganizationNameAsc(user.getId());
        if (memberships.isEmpty()) {
            throw new BadRequestException("User does not belong to an organization.");
        }
        return memberships;
    }

    private AuthResponse.OrganizationProfile organizationProfile(Membership membership) {
        Organization organization = membership.getOrganization();
        return new AuthResponse.OrganizationProfile(
                organization.getId(),
                organization.getName(),
                organization.getType(),
                membership.getRole().name()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthResult(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {
    }
}
