package com.ksefhelper.auth;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
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

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("An account with this email already exists.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
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

        return response(savedUser, savedMembership, List.of(savedMembership));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
        ));

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password."));
        List<Membership> memberships = memberships(user);
        Membership membership = memberships.size() == 1 ? memberships.getFirst() : null;
        return response(user, membership, memberships);
    }

    @Transactional(readOnly = true)
    public AuthResponse me() {
        User user = currentUserService.currentUser();
        List<Membership> memberships = memberships(user);
        Membership activeMembership = null;
        try {
            activeMembership = currentUserService.currentMembership();
        } catch (com.ksefhelper.common.exception.ForbiddenException ignored) {
            // An unscoped login token is valid while the user selects an organization.
        }
        return response(user, activeMembership, memberships);
    }

    @Transactional(readOnly = true)
    public AuthResponse switchOrganization(UUID organizationId) {
        User user = currentUserService.currentUser();
        Membership membership = membershipRepository.findByUserIdAndOrganizationId(user.getId(), organizationId)
                .orElseThrow(() -> new ForbiddenException("You do not belong to the selected organization."));
        return response(user, membership, memberships(user));
    }

    private List<Membership> memberships(User user) {
        List<Membership> memberships = membershipRepository.findAllByUserIdOrderByOrganizationNameAsc(user.getId());
        if (memberships.isEmpty()) {
            throw new BadRequestException("User does not belong to an organization.");
        }
        return memberships;
    }

    private AuthResponse response(User user, Membership membership, List<Membership> memberships) {
        UUID organizationId = membership == null ? null : membership.getOrganization().getId();
        String token = jwtService.generateToken(new AppUserPrincipal(user).withOrganizationId(organizationId));
        return new AuthResponse(
                token,
                new AuthResponse.UserProfile(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName()),
                membership == null ? null : organizationProfile(membership),
                memberships.stream().map(this::organizationProfile).toList()
        );
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
}
