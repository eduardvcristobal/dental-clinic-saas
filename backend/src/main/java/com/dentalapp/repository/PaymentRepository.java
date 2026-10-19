package com.dentalapp.repository;

import com.dentalapp.domain.entity.Payment;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id = :invoiceId and p.voidedAt is null")
    BigDecimal totalPaid(@Param("invoiceId") UUID invoiceId);
}
