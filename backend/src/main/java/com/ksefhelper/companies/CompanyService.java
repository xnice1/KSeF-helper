package com.ksefhelper.companies;

import com.ksefhelper.common.exception.NotFoundException;
import com.ksefhelper.companies.dto.CompanyRequest;
import com.ksefhelper.companies.dto.CompanyResponse;
import com.ksefhelper.companies.entity.Company;
import com.ksefhelper.companies.repository.CompanyRepository;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.OrganizationAuthorizationService;
import com.ksefhelper.organizations.OrganizationPermission;
import com.ksefhelper.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final OrganizationAuthorizationService authorizationService;

    public CompanyService(
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        authorizationService.require(OrganizationPermission.VIEW_COMPANIES);
        return companyRepository.findAllByOrganizationIdOrderByNameAsc(currentUserService.currentOrganizationId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID id) {
        authorizationService.require(OrganizationPermission.VIEW_COMPANIES);
        return toResponse(findScoped(id));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        authorizationService.require(OrganizationPermission.MANAGE_COMPANIES);
        Organization organization = currentUserService.currentOrganization();
        Company company = new Company();
        company.setOrganization(organization);
        apply(company, request);
        return toResponse(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse update(UUID id, CompanyRequest request) {
        authorizationService.require(OrganizationPermission.MANAGE_COMPANIES);
        Company company = findScoped(id);
        apply(company, request);
        return toResponse(company);
    }

    @Transactional
    public void delete(UUID id) {
        authorizationService.require(OrganizationPermission.MANAGE_COMPANIES);
        companyRepository.delete(findScoped(id));
    }

    public Company findScoped(UUID id) {
        authorizationService.require(OrganizationPermission.VIEW_COMPANIES);
        return companyRepository.findByIdAndOrganizationId(id, currentUserService.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Company was not found."));
    }

    private void apply(Company company, CompanyRequest request) {
        company.setName(request.name().trim());
        company.setNip(request.nip().replaceAll("\\s+", ""));
        company.setRegon(request.regon() == null || request.regon().isBlank() ? null : request.regon().trim());
        company.setStreet(request.street().trim());
        company.setCity(request.city().trim());
        company.setPostalCode(request.postalCode().trim());
        String country = request.country() == null || request.country().isBlank() ? "PL" : request.country();
        company.setCountry(country.toUpperCase(Locale.ROOT));
    }

    private CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getOrganization().getId(),
                company.getName(),
                company.getNip(),
                company.getRegon(),
                company.getStreet(),
                company.getCity(),
                company.getPostalCode(),
                company.getCountry(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
