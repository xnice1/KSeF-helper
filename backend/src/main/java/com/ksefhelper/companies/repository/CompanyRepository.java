package com.ksefhelper.companies.repository;

import com.ksefhelper.companies.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<Company> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
