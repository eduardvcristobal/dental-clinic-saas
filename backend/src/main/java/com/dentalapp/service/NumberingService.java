package com.dentalapp.service;

import com.dentalapp.tenancy.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-clinic running numbers (chart numbers, invoice numbers). Atomic via a single upsert. */
@Service
public class NumberingService {
    private final JdbcTemplate jdbc;

    public NumberingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public long next(String counterName) {
        return jdbc.queryForObject("""
            INSERT INTO clinic_counter (clinic_id, name, next_value) VALUES (?, ?, 2)
            ON CONFLICT (clinic_id, name) DO UPDATE SET next_value = clinic_counter.next_value + 1
            RETURNING next_value - 1
            """, Long.class, TenantContext.get(), counterName);
    }

    public String nextChartNo()   { return String.format("P-%06d", next("chart_no")); }
    public String nextInvoiceNo() { return String.format("INV-%06d", next("invoice_no")); }
}
