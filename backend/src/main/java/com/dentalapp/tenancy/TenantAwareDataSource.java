package com.dentalapp.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Stamps every connection checkout with the current tenant (session variable app.clinic_id) so the
 * PostgreSQL RLS policies in V8 apply. The value is overwritten on each checkout, so a pooled
 * connection can never leak the previous request's tenant.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) { super(target); }

    @Override
    public Connection getConnection() throws SQLException {
        return stamp(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return stamp(super.getConnection(username, password));
    }

    private Connection stamp(Connection c) throws SQLException {
        UUID tenant = TenantContext.get();
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.clinic_id', ?, false)")) {
            ps.setString(1, tenant == null ? "" : tenant.toString());
            ps.execute();
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }
}
