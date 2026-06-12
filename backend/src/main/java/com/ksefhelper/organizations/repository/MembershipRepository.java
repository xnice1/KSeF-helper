package com.ksefhelper.organizations.repository;

import com.ksefhelper.organizations.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<Membership> findAllByUserIdOrderByOrganizationNameAsc(UUID userId);

    List<Membership> findAllByOrganizationId(UUID organizationId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndRole(UUID organizationId, com.ksefhelper.organizations.entity.MembershipRole role);
}
