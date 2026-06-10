package com.ksefhelper.organizations;

import com.ksefhelper.common.exception.ForbiddenException;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class OrganizationAuthorizationService {
    private static final Map<MembershipRole, Set<OrganizationPermission>> PERMISSIONS = permissions();

    private final CurrentUserService currentUserService;

    public OrganizationAuthorizationService(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public void require(OrganizationPermission permission) {
        MembershipRole role = currentUserService.currentMembership().getRole();
        if (!PERMISSIONS.getOrDefault(role, Set.of()).contains(permission)) {
            throw new ForbiddenException("Your organization role does not allow this action.");
        }
    }

    private static Map<MembershipRole, Set<OrganizationPermission>> permissions() {
        Map<MembershipRole, Set<OrganizationPermission>> permissions = new EnumMap<>(MembershipRole.class);
        permissions.put(MembershipRole.OWNER, EnumSet.allOf(OrganizationPermission.class));
        permissions.put(MembershipRole.ACCOUNTANT, EnumSet.of(
                OrganizationPermission.VIEW_ORGANIZATION,
                OrganizationPermission.VIEW_MEMBERS,
                OrganizationPermission.INVITE_MEMBERS,
                OrganizationPermission.VIEW_COMPANIES,
                OrganizationPermission.MANAGE_COMPANIES,
                OrganizationPermission.VIEW_INVOICES,
                OrganizationPermission.UPLOAD_INVOICES,
                OrganizationPermission.REVALIDATE_INVOICES,
                OrganizationPermission.DELETE_INVOICES,
                OrganizationPermission.DOWNLOAD_INVOICES,
                OrganizationPermission.VIEW_REPORTS
        ));
        permissions.put(MembershipRole.EMPLOYEE, EnumSet.of(
                OrganizationPermission.VIEW_ORGANIZATION,
                OrganizationPermission.VIEW_COMPANIES,
                OrganizationPermission.VIEW_INVOICES,
                OrganizationPermission.UPLOAD_INVOICES,
                OrganizationPermission.REVALIDATE_INVOICES,
                OrganizationPermission.DOWNLOAD_INVOICES,
                OrganizationPermission.VIEW_REPORTS
        ));
        permissions.put(MembershipRole.CLIENT, EnumSet.of(
                OrganizationPermission.VIEW_ORGANIZATION,
                OrganizationPermission.VIEW_COMPANIES,
                OrganizationPermission.VIEW_INVOICES,
                OrganizationPermission.DOWNLOAD_INVOICES,
                OrganizationPermission.VIEW_REPORTS
        ));
        return Map.copyOf(permissions);
    }
}
