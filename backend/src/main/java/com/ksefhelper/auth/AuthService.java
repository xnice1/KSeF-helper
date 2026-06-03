package com.ksefhelper.auth;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.common.exception.BadRequestException;
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

        return response(savedUser, savedMembership);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
        ));

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password."));
        Membership membership = membershipRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                .orElseThrow(() -> new BadRequestException("User does not belong to an organization."));
        return response(user, membership);
    }

    @Transactional(readOnly = true)
    public AuthResponse me() {
        User user = currentUserService.currentUser();
        Membership membership = currentUserService.currentMembership();
        return response(user, membership);
    }

    private AuthResponse response(User user, Membership membership) {
        String token = jwtService.generateToken(new AppUserPrincipal(user));
        Organization organization = membership.getOrganization();
        return new AuthResponse(
                token,
                new AuthResponse.UserProfile(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName()),
                new AuthResponse.OrganizationProfile(
                        organization.getId(),
                        organization.getName(),
                        organization.getType(),
                        membership.getRole().name()
                )
        );
    }
}
