package com.ibm.saga.coordinatorlra.internal;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.util.Dictionary;
import java.util.logging.Logger;

/**
 * OSGi {@link ManagedService} for the {@code <lraCoordinatorStore>} server.xml element.
 *
 * <p>When Liberty's Config Admin processes a {@code <lraCoordinatorStore>} stanza it
 * calls {@link #updated(Dictionary)}.  Two store types are supported:
 *
 * <dl>
 *   <dt>{@code file} (default)</dt>
 *   <dd>Narayana {@code ShadowNoFileLockStore} — transaction logs are written to the
 *       directory specified by {@code storeDir} (defaults to {@code ${server.output.dir}/lra-logs}).
 *       No external dependencies required.</dd>
 *
 *   <dt>{@code db}</dt>
 *   <dd>Narayana {@code JDBCStore} — transaction logs are stored in a relational database
 *       accessed through a Liberty {@code <dataSource>} element.  The {@code dataSourceRef}
 *       attribute must match the {@code jndiName} of a {@code <dataSource>} declared in the
 *       same {@code server.xml}.  Liberty manages the connection pool, credentials, and SSL.</dd>
 * </dl>
 *
 * <p>Configuration changes in a running server are applied by restarting the coordinator
 * via {@link LraCoordinatorBootstrap}.
 */
public class LraCoordinatorStoreConfig implements ManagedService {

    static final String PID = "com.ibm.saga.coordinatorlra.store";

    /** Store type constant for file-based transaction logs. */
    public static final String STORE_TYPE_FILE = "file";

    /** Store type constant for JDBC-based transaction logs. */
    public static final String STORE_TYPE_DB = "db";

    private static final Logger LOG =
            Logger.getLogger(LraCoordinatorStoreConfig.class.getName());

    // Defaults
    private static final String DEFAULT_STORE_TYPE    = STORE_TYPE_FILE;
    private static final String DEFAULT_STORE_DIR     = "${server.output.dir}/lra-logs";
    private static final String DEFAULT_TABLE_PREFIX  = "lra_";
    private static final String DEFAULT_DATASOURCE_REF = "jdbc/LraCoordinatorDS";

    // Effective configuration — read by LraCoordinatorBootstrap
    private volatile String storeType      = DEFAULT_STORE_TYPE;
    private volatile String storeDir       = DEFAULT_STORE_DIR;
    private volatile String tablePrefix    = DEFAULT_TABLE_PREFIX;
    private volatile String dataSourceRef  = DEFAULT_DATASOURCE_REF;

    /** Notified when config changes so the bootstrap can restart the store. */
    private volatile Runnable reconfigureCallback;

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        if (properties == null) {
            LOG.warning("<lraCoordinatorStore> removed from server.xml — "
                    + "reverting to file-based defaults.");
            storeType     = DEFAULT_STORE_TYPE;
            storeDir      = DEFAULT_STORE_DIR;
            tablePrefix   = DEFAULT_TABLE_PREFIX;
            dataSourceRef = DEFAULT_DATASOURCE_REF;
        } else {
            storeType     = getString(properties, "storeType",     DEFAULT_STORE_TYPE);
            storeDir      = getString(properties, "storeDir",      DEFAULT_STORE_DIR);
            tablePrefix   = getString(properties, "tablePrefix",   DEFAULT_TABLE_PREFIX);
            dataSourceRef = getString(properties, "dataSourceRef", DEFAULT_DATASOURCE_REF);

            LOG.info("<lraCoordinatorStore> updated — storeType=" + storeType
                    + (STORE_TYPE_DB.equals(storeType)
                       ? ", dataSourceRef=" + dataSourceRef
                       : ", storeDir=" + storeDir));
        }

        if (reconfigureCallback != null) {
            reconfigureCallback.run();
        }
    }

    // -------------------------------------------------------------------------
    // Accessors used by LraCoordinatorBootstrap
    // -------------------------------------------------------------------------

    public String getStoreType()     { return storeType; }
    public String getStoreDir()      { return storeDir; }
    public String getTablePrefix()   { return tablePrefix; }
    public String getDataSourceRef() { return dataSourceRef; }

    /**
     * Registers a callback that is invoked whenever {@link #updated(Dictionary)} is
     * called after initial configuration.  Used by {@link LraCoordinatorBootstrap}
     * to restart the coordinator when store settings change at runtime.
     */
    public void setReconfigureCallback(Runnable callback) {
        this.reconfigureCallback = callback;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getString(Dictionary<String, ?> props, String key, String fallback) {
        Object v = props.get(key);
        return (v instanceof String s && !s.isBlank()) ? s : fallback;
    }
}
