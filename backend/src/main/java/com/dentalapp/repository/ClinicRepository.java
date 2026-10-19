package com.dentalapp.repository;

import com.dentalapp.domain.entity.Clinic;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {
    /** Used at login to resolve the tenant from the subdomain/slug before authenticating. */
    Optional<Clinic> findBySlug(String slug);
}
