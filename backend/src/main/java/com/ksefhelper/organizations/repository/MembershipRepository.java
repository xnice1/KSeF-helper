package com.ksefhelper.organizations.repository;

import com.ksefhelper.organizations.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<Membership> findAllByOrganizationId(UUID organizationId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}
