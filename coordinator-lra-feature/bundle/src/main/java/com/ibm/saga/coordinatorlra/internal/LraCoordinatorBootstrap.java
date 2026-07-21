package com.ibm.saga.coordinatorlra.internal;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bootstraps and tears down the embedded Narayana LRA coordinator.
 *
 * <p>Reads the current {@link LraCoordinatorStoreConfig} and configures the Arjuna
 * object-store accordingly before starting the coordinator REST resources.  Registers
 * itself as the {@link LraCoordinatorStoreConfig#setReconfigureCallback reconfigureCallback}
 * so that a live change to {@code <lraCoordinatorStore>} in {@code server.xml} triggers
 * a clean restart.
 *
 * <h2>File store</h2>
 * <p>Uses Arjuna's default {@code ShadowNoFileLockStore}.  The store directory is
 * resolved from {@code storeDir}; any {@code ${server.output.dir}} token is expanded
 * to the value of the {@code server.output.dir} system property.
 *
 * <h2>DB store</h2>
 * <p>Uses Arjuna's {@code JDBCStore} backed by the embedded PostgreSQL driver.
 * The JDBC URL, user, and password come from {@code <lraCoordinatorStore>} attributes.
 */
public class LraCoordinatorBootstrap {

    private static final Logger LOG =
            Logger.getLogger(LraCoordinatorBootstrap.class.getName());

    /** Arjuna class name for the file-based object store. */
    private static final String FILE_STORE_CLASS =
            "com.arjuna.ats.internal.arjuna.objectstore.ShadowNoFileLockStore";

    /** Arjuna class name for the JDBC object store. */
    private static final String JDBC_STORE_CLASS =
            "com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore";

    /** Arjuna JDBC accessor class (simple pooled dynamic datasource). */
    private static final String JDBC_ACCESSOR_CLASS =
            "com.arjuna.ats.internal.arjuna.objectstore.jdbc.accessors"
            + ".SimplePooledDynamicDataSourceJDBCAccess";

    /** JDBC driver class name — PostgreSQL embedded in this bundle. */
    private static final String PG_DRIVER_CLASS =
            "org.postgresql.ds.PGSimpleDataSource";

    private final LraCoordinatorStoreConfig config;
    private volatile boolean running = false;

    public LraCoordinatorBootstrap(LraCoordinatorStoreConfig config) {
        this.config = config;
        // Register so that live server.xml edits trigger a restart.
        config.setReconfigureCallback(this::restart);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Configures the Arjuna store and marks the coordinator as running. */
    public void start() {
        configureArjuna();
        running = true;
        LOG.info("usr:coordinatorLRA-2.0 — coordinator started (storeType="
                + config.getStoreType() + ")");
    }

    /** Shuts down the coordinator. */
    public void stop() {
        running = false;
        LOG.info("usr:coordinatorLRA-2.0 — coordinator stopped");
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void restart() {
        if (running) {
            LOG.info("usr:coordinatorLRA-2.0 — store config changed, restarting coordinator");
            stop();
            start();
        }
    }

    /**
     * Applies the Arjuna {@link ObjectStoreEnvironmentBean} settings that correspond
     * to the current {@link LraCoordinatorStoreConfig}.
     */
    private void configureArjuna() {
        ObjectStoreEnvironmentBean oseb =
                BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);

        String storeType = config.getStoreType();

        if (LraCoordinatorStoreConfig.STORE_TYPE_DB.equalsIgnoreCase(storeType)) {
            configureJdbcStore(oseb);
        } else {
            configureFileStore(oseb);
        }
    }

    private void configureFileStore(ObjectStoreEnvironmentBean oseb) {
        String rawDir = config.getStoreDir();
        String resolvedDir = expandServerOutputDir(rawDir);

        // Ensure the directory exists
        File dir = new File(resolvedDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                LOG.warning("usr:coordinatorLRA-2.0 — could not create store directory: "
                        + resolvedDir);
            }
        }

        oseb.setObjectStoreType(FILE_STORE_CLASS);
        oseb.setObjectStoreDir(resolvedDir);
        LOG.info("usr:coordinatorLRA-2.0 — file store configured at: " + resolvedDir);
    }

    private void configureJdbcStore(ObjectStoreEnvironmentBean oseb) {
        String url      = config.getDbUrl();
        String user     = config.getDbUser();
        String password = config.getDbPassword();
        String prefix   = config.getTablePrefix();

        /*
         * Arjuna JDBC accessor connection string format:
         *   <AccessorClass>;ClassName=<DataSourceClass>;ServerName=<host>;PortNumber=<port>;
         *   DatabaseName=<db>;User=<user>;Password=<pwd>
         *
         * We derive host, port, and dbName from the JDBC URL so the admin only needs to
         * provide a single familiar jdbc:postgresql://host:port/db URL string.
         */
        String accessorString = buildAccessorString(url, user, password);

        oseb.setObjectStoreType(JDBC_STORE_CLASS);
        oseb.setJdbcAccess(accessorString);
        oseb.setTablePrefix(prefix);
        oseb.setCreateTable(true);
        LOG.info("usr:coordinatorLRA-2.0 — JDBC store configured (url=" + url
                + ", tablePrefix=" + prefix + ")");
    }

    /**
     * Builds the Arjuna JDBC accessor connection string from a standard JDBC URL.
     *
     * <p>Parses {@code jdbc:postgresql://host:port/database} and maps the parts to
     * the {@code SimplePooledDynamicDataSourceJDBCAccess} key=value format.
     */
    static String buildAccessorString(String jdbcUrl, String user, String password) {
        // Expected format: jdbc:postgresql://host:port/dbname[?params]
        String host     = "localhost";
        int    port     = 5432;
        String database = "sagadb";

        try {
            // Strip jdbc: prefix then parse as URI
            String rest = jdbcUrl;
            if (rest.startsWith("jdbc:")) {
                rest = rest.substring("jdbc:".length());
            }
            java.net.URI uri = new java.net.URI(rest);
            if (uri.getHost() != null) host = uri.getHost();
            if (uri.getPort() > 0)     port = uri.getPort();
            String path = uri.getPath();
            if (path != null && path.startsWith("/") && path.length() > 1) {
                // strip leading / and any query path
                database = path.substring(1).split("[/?]")[0];
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "usr:coordinatorLRA-2.0 — could not parse dbUrl '"
                    + jdbcUrl + "', using defaults", e);
        }

        return JDBC_ACCESSOR_CLASS
                + ";ClassName=" + PG_DRIVER_CLASS
                + ";ServerName=" + host
                + ";PortNumber=" + port
                + ";DatabaseName=" + database
                + ";User=" + user
                + ";Password=" + password;
    }

    /**
     * Replaces {@code ${server.output.dir}} in a path with the Liberty system property,
     * falling back to {@code java.io.tmpdir}/lra-logs if the property is not set.
     */
    private static String expandServerOutputDir(String path) {
        String serverOutputDir = System.getProperty("server.output.dir");
        if (serverOutputDir == null) {
            serverOutputDir = System.getProperty("java.io.tmpdir") + File.separator + "lra-logs";
        }
        return path.replace("${server.output.dir}", serverOutputDir);
    }
}
