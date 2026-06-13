package com.ksefhelper.organizations;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.auth.RefreshSessionService;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.common.exception.ForbiddenException;
import com.ksefhelper.common.exception.NotFoundException;
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
import java.util.Map;
import java.util.UUID;

@Service
public class OrganizationService {
    private final CurrentUserService currentUserService;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final AuditEventService auditEventService;
    private final RefreshSessionService refreshSessionService;

    public OrganizationService(
            CurrentUserService currentUserService,
            MembershipRepository membershipRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationAuthorizationService authorizationService,
            AuditEventService auditEventService,
            RefreshSessionService refreshSessionService
    ) {
        this.currentUserService = currentUserService;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.auditEventService = auditEventService;
        this.refreshSessionService = refreshSessionService;
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
        auditEventService.recordForUser(
                AuditEventType.ORGANIZATION_CREATED,
                user,
                savedOrganization.getId(),
                "organization",
                savedOrganization.getId(),
                Map.of("name", savedOrganization.getName(), "type", savedOrganization.getType())
        );

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
        lockOrganization(organizationId);
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
        Membership saved = membershipRepository.save(membership);
        auditEventService.record(
                AuditEventType.ORGANIZATION_MEMBER_INVITED,
                organizationId,
                "membership",
                saved.getId(),
                Map.of(
                        "invitedUserId", invitedUser.getId(),
                        "invitedEmail", invitedUser.getEmail(),
                        "role", request.role()
                )
        );
        return toResponse(saved);
    }

    @Transactional
    public MembershipResponse changeRole(UUID organizationId, UUID membershipId, MembershipRole role) {
        lockOrganization(organizationId);
        Membership actorMembership = ensureActiveOrganization(organizationId);
        authorizationService.require(OrganizationPermission.MANAGE_MEMBERS);
        Membership target = membership(organizationId, membershipId);
        MembershipRole previousRole = target.getRole();
        if (previousRole == role) {
            return toResponse(target);
        }
        if (previousRole == MembershipRole.OWNER
                && role != MembershipRole.OWNER
                && ownerCount(organizationId) == 1) {
            throw new BadRequestException("Promote another owner before changing the last owner's role.");
        }

        target.setRole(role);
        Membership saved = membershipRepository.save(target);
        auditEventService.record(
                AuditEventType.ORGANIZATION_MEMBER_ROLE_CHANGED,
                organizationId,
                "membership",
                saved.getId(),
                Map.of(
                        "userId", saved.getUser().getId(),
                        "email", saved.getUser().getEmail(),
                        "previousRole", previousRole,
                        "newRole", role,
                        "changedByMembershipId", actorMembership.getId()
                )
        );
        return toResponse(saved);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID membershipId) {
        lockOrganization(organizationId);
        Membership actorMembership = ensureActiveOrganization(organizationId);
        authorizationService.require(OrganizationPermission.MANAGE_MEMBERS);
        Membership target = membership(organizationId, membershipId);
        if (target.getId().equals(actorMembership.getId())) {
            throw new BadRequestException("Use the leave organization action to remove your own membership.");
        }
        ensureOwnerCanBeRemoved(target);

        auditEventService.record(
                AuditEventType.ORGANIZATION_MEMBER_REMOVED,
                organizationId,
                "membership",
                target.getId(),
                Map.of(
                        "userId", target.getUser().getId(),
                        "email", target.getUser().getEmail(),
                        "role", target.getRole()
                )
        );
        refreshSessionService.clearActiveOrganization(target.getUser(), target.getOrganization());
        membershipRepository.delete(target);
    }

    @Transactional
    public void leave(UUID organizationId) {
        lockOrganization(organizationId);
        Membership membership = ensureActiveOrganization(organizationId);
        ensureOwnerCanBeRemoved(membership);

        auditEventService.recordForUser(
                AuditEventType.ORGANIZATION_MEMBER_LEFT,
                membership.getUser(),
                organizationId,
                "membership",
                membership.getId(),
                Map.of("role", membership.getRole())
        );
        refreshSessionService.clearActiveOrganization(membership.getUser(), membership.getOrganization());
        membershipRepository.delete(membership);
    }

    @Transactional
    public MembershipResponse transferOwnership(UUID organizationId, UUID membershipId) {
        lockOrganization(organizationId);
        Membership currentOwner = ensureActiveOrganization(organizationId);
        authorizationService.require(OrganizationPermission.MANAGE_MEMBERS);
        if (currentOwner.getRole() != MembershipRole.OWNER) {
            throw new ForbiddenException("Only an owner can transfer ownership.");
        }

        Membership nextOwner = membership(organizationId, membershipId);
        if (nextOwner.getId().equals(currentOwner.getId())) {
            throw new BadRequestException("Choose another member to receive ownership.");
        }

        MembershipRole previousTargetRole = nextOwner.getRole();
        nextOwner.setRole(MembershipRole.OWNER);
        currentOwner.setRole(MembershipRole.ACCOUNTANT);
        membershipRepository.save(nextOwner);
        membershipRepository.save(currentOwner);
        auditEventService.record(
                AuditEventType.ORGANIZATION_OWNERSHIP_TRANSFERRED,
                organizationId,
                "membership",
                nextOwner.getId(),
                Map.of(
                        "fromUserId", currentOwner.getUser().getId(),
                        "fromEmail", currentOwner.getUser().getEmail(),
                        "toUserId", nextOwner.getUser().getId(),
                        "toEmail", nextOwner.getUser().getEmail(),
                        "previousTargetRole", previousTargetRole
                )
        );
        return toResponse(nextOwner);
    }

    private Membership ensureActiveOrganization(UUID organizationId) {
        Membership membership = currentUserService.currentMembership();
        if (!membership.getOrganization().getId().equals(organizationId)) {
            throw new ForbiddenException("Switch to this organization before accessing it.");
        }
        return membership;
    }

    private Membership membership(UUID organizationId, UUID membershipId) {
        return membershipRepository.findById(membershipId)
                .filter(value -> value.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new NotFoundException("Organization member was not found."));
    }

    private void ensureOwnerCanBeRemoved(Membership membership) {
        if (membership.getRole() == MembershipRole.OWNER
                && ownerCount(membership.getOrganization().getId()) == 1) {
            throw new BadRequestException("Transfer ownership before removing the last owner.");
        }
    }

    private long ownerCount(UUID organizationId) {
        return membershipRepository.countByOrganizationIdAndRole(organizationId, MembershipRole.OWNER);
    }

    private void lockOrganization(UUID organizationId) {
        organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization was not found."));
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
