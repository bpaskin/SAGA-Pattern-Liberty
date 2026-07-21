package com.ibm.saga.coordinatorlra.internal;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import java.io.File;
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
 * <p>Uses Arjuna's {@code JDBCStore} backed by a Liberty-managed {@code <dataSource>}.
 * The JNDI name is taken from the {@code dataSourceRef} attribute of
 * {@code <lraCoordinatorStore>} and looked up at connection time by
 * {@link JndiDataSourceJDBCAccess}.  Liberty owns the connection pool, credentials,
 * and SSL — no raw JDBC URL or password is required in {@code server.xml}.
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

    /**
     * Configures Arjuna's {@code JDBCStore} to obtain connections from the
     * Liberty-managed {@link javax.sql.DataSource} referenced by
     * {@code <lraCoordinatorStore dataSourceRef="…"/>}.
     *
     * <p>The JNDI name is passed to {@link JndiDataSourceJDBCAccess} via Arjuna's
     * accessor string format:
     * <pre>
     *   &lt;AccessorClass&gt;;jndiName=&lt;jndiName&gt;
     * </pre>
     * Arjuna instantiates {@link JndiDataSourceJDBCAccess} reflectively, calls
     * {@code initialise(Properties)}, and then delegates all {@code getConnection()}
     * calls to it.
     */
    private void configureJdbcStore(ObjectStoreEnvironmentBean oseb) {
        String jndiName   = config.getDataSourceRef();
        String prefix     = config.getTablePrefix();

        // Arjuna accessor string: <class>;key=value pairs
        String accessorString = JndiDataSourceJDBCAccess.class.getName()
                + ";" + JndiDataSourceJDBCAccess.PROP_JNDI_NAME + "=" + jndiName;

        oseb.setObjectStoreType(JDBC_STORE_CLASS);
        oseb.setJdbcAccess(accessorString);
        oseb.setTablePrefix(prefix);
        oseb.setCreateTable(true);

        LOG.info("usr:coordinatorLRA-2.0 — JDBC store configured (dataSourceRef="
                + jndiName + ", tablePrefix=" + prefix + ")");
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
