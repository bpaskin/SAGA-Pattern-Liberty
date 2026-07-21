package com.ibm.saga.coordinatorlra.internal;

import com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCAccess;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Arjuna {@link JDBCAccess} implementation that obtains database connections
 * from a Liberty-managed {@link DataSource} looked up via JNDI.
 *
 * <p>This lets the LRA coordinator's transaction-log store use a {@code <dataSource>}
 * element already declared in {@code server.xml} — with full Liberty connection
 * pooling, SSL, and credential management — instead of embedding its own raw
 * JDBC URL and credentials.
 *
 * <p>Usage: set as Arjuna's JDBC accessor via
 * {@link com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean#setJdbcAccess(String)}
 * using the serialised form {@code <fully-qualified-class-name>;<jndiName>},
 * where {@code <jndiName>} is the JNDI name of the Liberty {@code <dataSource>}
 * (e.g. {@code jdbc/LraCoordinatorDS}).
 *
 * <p>Arjuna instantiates this class via reflection (no-arg constructor) and then
 * calls {@link #initialise(Properties)} with any properties parsed from the
 * accessor string.  We store the JNDI name there so each
 * {@link #getConnection()} call performs a fresh JNDI lookup against Liberty's
 * naming context and delegates connection creation to Liberty's pool.
 */
public class JndiDataSourceJDBCAccess implements JDBCAccess {

    private static final Logger LOG =
            Logger.getLogger(JndiDataSourceJDBCAccess.class.getName());

    /** Property key used to pass the JNDI name through Arjuna's properties map. */
    static final String PROP_JNDI_NAME = "jndiName";

    private String jndiName;

    /** No-arg constructor required by Arjuna's reflective instantiation. */
    public JndiDataSourceJDBCAccess() {}

    /**
     * Called by Arjuna after instantiation.  Reads {@code jndiName} from the
     * supplied properties.
     */
    @Override
    public void initialise(Properties properties) {
        jndiName = properties.getProperty(PROP_JNDI_NAME);
        if (jndiName == null || jndiName.isBlank()) {
            throw new IllegalStateException(
                    "JndiDataSourceJDBCAccess: 'jndiName' property is required but was not set");
        }
        LOG.info("usr:coordinatorLRA-2.0 — JDBC store will use Liberty DataSource: " + jndiName);
    }

    /**
     * Returns a connection from the Liberty-managed {@link DataSource} bound at
     * {@link #jndiName}.  Arjuna closes the connection after each use.
     */
    @Override
    public Connection getConnection() throws SQLException {
        DataSource ds = lookupDataSource();
        return ds.getConnection();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private DataSource lookupDataSource() throws SQLException {
        try {
            return (DataSource) new InitialContext().lookup(jndiName);
        } catch (NamingException e) {
            throw new SQLException(
                    "usr:coordinatorLRA-2.0 — cannot look up DataSource '" + jndiName
                    + "' from JNDI: " + e.getMessage(), e);
        }
    }
}
