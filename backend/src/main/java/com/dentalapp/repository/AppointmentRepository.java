package com.dentalapp.repository;

import com.dentalapp.domain.entity.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /** Calendar view: everything that overlaps [from, to) in a branch. */
    @Query("""
        select a from Appointment a
          join fetch a.patient join fetch a.dentist
        where a.branch.id = :branchId and a.startsAt < :to and a.endsAt > :from
        order by a.startsAt
        """)
    List<Appointment> findForCalendar(@Param("branchId") UUID branchId,
                                      @Param("from") Instant from, @Param("to") Instant to);
}
