package com.ibm.saga.coordinatorlra.api;

import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import java.net.URI;

/**
 * Public API utility class exported by the {@code usr:coordinatorLRA-2.0} feature.
 *
 * <p>This class is part of the {@code com.ibm.saga.coordinatorlra.api} package which
 * is listed in the bundle's {@code Export-Package} and in the feature manifest's
 * {@code IBM-API-Package} header.  Applications that list {@code usr:coordinatorLRA-2.0}
 * in their {@code server.xml} can use it on the default application class path without
 * any additional Maven dependency.
 *
 * <p>The feature also serves the Narayana LRA coordinator REST endpoints at
 * {@code /lra-coordinator} on the configured HTTP port.
 */
public final class LraCoordinatorUtils {

    /** The standard JAX-RS header name for the LRA context URI. */
    public static final String LRA_HTTP_CONTEXT_HEADER = LRA.LRA_HTTP_CONTEXT_HEADER;

    /** The standard JAX-RS header name used to end an LRA. */
    public static final String LRA_HTTP_ENDED_CONTEXT_HEADER = LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;

    /** HTTP path at which the coordinator REST API is served. */
    public static final String COORDINATOR_PATH = "/lra-coordinator";

    private LraCoordinatorUtils() {
        // utility class
    }

    /**
     * Returns {@code true} if a non-null LRA context URI was propagated on the
     * current request.
     *
     * @param lraId the URI injected via {@code @HeaderParam(LRA_HTTP_CONTEXT_HEADER)}
     * @return {@code true} when an active LRA context is present
     */
    public static boolean hasActiveLra(URI lraId) {
        return lraId != null;
    }

    /**
     * Builds the coordinator base URL from a host and port.
     *
     * @param host coordinator hostname or IP
     * @param port coordinator HTTP port
     * @return fully-qualified coordinator URL, e.g. {@code http://localhost:8080/lra-coordinator}
     */
    public static String coordinatorUrl(String host, int port) {
        return "http://" + host + ":" + port + COORDINATOR_PATH;
    }
}
