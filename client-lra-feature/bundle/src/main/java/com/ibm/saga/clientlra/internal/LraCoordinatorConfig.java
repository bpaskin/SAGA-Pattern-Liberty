package com.ibm.saga.clientlra.internal;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.util.Dictionary;
import java.util.logging.Logger;

/**
 * OSGi {@link ManagedService} for the {@code lraCoordinator} server.xml element.
 *
 * <p>When the Liberty config pipeline processes an {@code <lraCoordinator>} stanza,
 * it looks up the service registered under this pid and calls
 * {@link #updated(Dictionary)}.  The coordinator URL is assembled from the
 * supplied {@code host}, {@code port}, and {@code path} attributes and stored
 * as the system property {@code lra.coordinator.url} where the Narayana LRA
 * client can read it.
 *
 * <p>The {@code <lraCoordinator>} element in {@code server.xml} is the
 * <strong>only</strong> supported configuration source.  MicroProfile Config
 * properties and environment variables are intentionally not consulted.
 * Omitting the element causes startup to fail with a clear error message.
 */
public class LraCoordinatorConfig implements ManagedService {

    static final String PID = "com.ibm.saga.clientlra.coordinator";

    /** System property key consumed by the Narayana LRA client at runtime. */
    static final String COORDINATOR_URL_PROP = "lra.coordinator.url";

    private static final Logger LOG = Logger.getLogger(LraCoordinatorConfig.class.getName());

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8070;
    private static final String DEFAULT_PATH = "/lra-coordinator";

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        if (properties == null) {
            // <lraCoordinator> was removed from server.xml — clear the property so
            // the Narayana client cannot use a stale value from a previous config.
            System.clearProperty(COORDINATOR_URL_PROP);
            LOG.warning("lraCoordinator element removed from server.xml; "
                    + COORDINATOR_URL_PROP + " has been cleared. "
                    + "Add <lraCoordinator host=\"…\" port=\"…\"/> to server.xml.");
            return;
        }

        String host = getString(properties, "host", DEFAULT_HOST);
        int    port = getInt(properties,    "port", DEFAULT_PORT);
        String path = getString(properties, "path", DEFAULT_PATH);

        // Normalise: path must start with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String url = "http://" + host + ":" + port + path;
        System.setProperty(COORDINATOR_URL_PROP, url);
        LOG.info("lraCoordinator configured — " + COORDINATOR_URL_PROP + "=" + url);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getString(Dictionary<String, ?> props, String key, String fallback) {
        Object v = props.get(key);
        return (v instanceof String s && !s.isBlank()) ? s : fallback;
    }

    private static int getInt(Dictionary<String, ?> props, String key, int fallback) {
        Object v = props.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }
}
