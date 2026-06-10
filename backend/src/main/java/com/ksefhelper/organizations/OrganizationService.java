package com.ksefhelper.organizations;

import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.common.exception.ForbiddenException;
import com.ksefhelper.organizations.dto.InviteMemberRequest;
import com.ksefhelper.organizations.dto.MembershipResponse;
import com.ksefhelper.organizations.dto.OrganizationRequest;
import com.ksefhelper.organizations.dto.OrganizationResponse;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.organizations.repository.OrganizationRepository;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {
    private final CurrentUserService currentUserService;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationAuthorizationService authorizationService;

    public OrganizationService(
            CurrentUserService currentUserService,
            MembershipRepository membershipRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationAuthorizationService authorizationService
    ) {
        this.currentUserService = currentUserService;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse current() {
        authorizationService.require(OrganizationPermission.VIEW_ORGANIZATION);
        Organization organization = currentUserService.currentOrganization();
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getType());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> members() {
        authorizationService.require(OrganizationPermission.VIEW_MEMBERS);
        return membershipRepository.findAllByOrganizationId(currentUserService.currentOrganizationId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        User user = currentUserService.currentUser();

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setType(request.type());
        Organization savedOrganization = organizationRepository.save(organization);

        Membership membership = new Membership();
        membership.setUser(user);
        membership.setOrganization(savedOrganization);
        membership.setRole(MembershipRole.OWNER);
        membershipRepository.save(membership);

        return toResponse(savedOrganization);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> members(UUID organizationId) {
        ensureActiveOrganization(organizationId);
        authorizationService.require(OrganizationPermission.VIEW_MEMBERS);
        return membershipRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MembershipResponse invite(UUID organizationId, InviteMemberRequest request) {
        Membership currentMembership = ensureActiveOrganization(organizationId);
        authorizationService.require(OrganizationPermission.INVITE_MEMBERS);
        if (currentMembership.getRole() == MembershipRole.ACCOUNTANT
                && (request.role() == MembershipRole.OWNER || request.role() == MembershipRole.ACCOUNTANT)) {
            throw new ForbiddenException("Accountants can invite clients and employees only.");
        }

        Organization organization = currentMembership.getOrganization();
        User invitedUser = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadRequestException("The invited user must register before they can be added."));
        if (membershipRepository.existsByUserIdAndOrganizationId(invitedUser.getId(), organizationId)) {
            throw new BadRequestException("This user already belongs to the organization.");
        }

        Membership membership = new Membership();
        membership.setUser(invitedUser);
        membership.setOrganization(organization);
        membership.setRole(request.role());
        return toResponse(membershipRepository.save(membership));
    }

    private Membership ensureActiveOrganization(UUID organizationId) {
        Membership membership = currentUserService.currentMembership();
        if (!membership.getOrganization().getId().equals(organizationId)) {
            throw new ForbiddenException("Switch to this organization before accessing it.");
        }
        return membership;
    }

    private OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getType());
    }

    private MembershipResponse toResponse(Membership membership) {
        String fullName = membership.getUser().getFirstName() + " " + membership.getUser().getLastName();
        return new MembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                fullName,
                membership.getRole()
        );
    }
}
