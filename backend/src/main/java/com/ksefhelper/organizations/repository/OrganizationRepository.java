package com.ksefhelper.organizations.repository;

import com.ksefhelper.organizations.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM organizations WHERE id = :id", nativeQuery = true)
    void deleteTenantDataById(@Param("id") UUID id);
}
