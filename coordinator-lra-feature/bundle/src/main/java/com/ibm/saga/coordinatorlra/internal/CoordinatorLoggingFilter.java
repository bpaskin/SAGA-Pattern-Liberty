package com.ibm.saga.coordinatorlra.internal;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JAX-RS request/response filter that logs every HTTP call received by the
 * embedded LRA coordinator REST API.
 *
 * <p>Log levels:
 * <ul>
 *   <li>{@link Level#FINE} — every inbound request (method + URI + optional LRA header)
 *       and its response (HTTP status + elapsed milliseconds).</li>
 *   <li>{@link Level#WARNING} — any response with status 400–599.</li>
 * </ul>
 *
 * <p>Enable FINE logging for this class to trace individual coordinator calls:
 * <pre>
 *   &lt;logging traceSpecification=
 *     "*=info:com.ibm.saga.coordinatorlra.internal.CoordinatorLoggingFilter=fine"/&gt;
 * </pre>
 *
 * <p>Registered explicitly via {@link LraCoordinatorApplication#getClasses()}.
 */
@Provider
public class CoordinatorLoggingFilter
        implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG =
            Logger.getLogger(CoordinatorLoggingFilter.class.getName());

    /** Property key used to pass the request start time between the two filter phases. */
    private static final String PROP_START_NS = "lra.coordinator.request.startNs";

    /** The standard LRA context header — logged when present on a request. */
    private static final String LRA_HEADER = "Long-Running-Action";

    // -------------------------------------------------------------------------
    // ContainerRequestFilter
    // -------------------------------------------------------------------------

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        req.setProperty(PROP_START_NS, System.nanoTime());

        if (LOG.isLoggable(Level.FINE)) {
            String lra = req.getHeaderString(LRA_HEADER);
            LOG.fine("LRA coordinator <<  " + req.getMethod()
                    + " " + req.getUriInfo().getRequestUri()
                    + (lra != null ? "  [LRA=" + lra + "]" : ""));
        }
    }

    // -------------------------------------------------------------------------
    // ContainerResponseFilter
    // -------------------------------------------------------------------------

    @Override
    public void filter(ContainerRequestContext req,
                       ContainerResponseContext res) throws IOException {

        int status = res.getStatus();
        long elapsedMs = elapsedMs(req);

        if (status >= 400) {
            LOG.warning("LRA coordinator >>  " + req.getMethod()
                    + " " + req.getUriInfo().getRequestUri()
                    + "  →  " + status
                    + "  (" + elapsedMs + " ms)");
        } else if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("LRA coordinator >>  " + req.getMethod()
                    + " " + req.getUriInfo().getRequestUri()
                    + "  →  " + status
                    + "  (" + elapsedMs + " ms)");
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static long elapsedMs(ContainerRequestContext req) {
        Object start = req.getProperty(PROP_START_NS);
        if (!(start instanceof Long startNs)) {
            return -1;
        }
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
