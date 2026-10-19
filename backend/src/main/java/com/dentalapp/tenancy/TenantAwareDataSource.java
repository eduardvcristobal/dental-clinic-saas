package com.dentalapp.tenancy;

import java.sql.Connection;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Stamps every connection checkout with the current tenant (session variable app.clinic_id) so the
 * PostgreSQL RLS policies apply. The value is overwritten on checkout and cleared before return so
 * a pooled connection cannot leak a previous request's tenant.
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
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                    try {
                        if (!c.isClosed()) {
                            // A caller may close while in an aborted transaction; rollback first.
                            if (!c.getAutoCommit()) c.rollback();
                            try (PreparedStatement reset = c.prepareStatement(
                                    "SELECT set_config('app.clinic_id', '', false)")) { reset.execute(); }
                        }
                    } finally { c.close(); }
                    return null;
                }
                try { return method.invoke(c, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
    }
}
