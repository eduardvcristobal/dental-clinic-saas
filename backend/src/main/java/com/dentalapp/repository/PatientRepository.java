package com.dentalapp.repository;

import com.dentalapp.domain.entity.Patient;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No clinic_id filtering needed anywhere: Hibernate's @TenantId adds it to every query. */
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    @Query("""
        select p from Patient p
        where p.deletedAt is null
          and (lower(p.firstName) like lower(concat(:q, '%'))
            or lower(p.lastName)  like lower(concat(:q, '%'))
            or p.phone            like concat(:q, '%')
            or p.chartNo          like concat(:q, '%'))
        order by p.lastName, p.firstName
        """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);
}
