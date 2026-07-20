package com.ibm.saga.clientlra.api;

import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import java.net.URI;

/**
 * Public API utility class exported by the {@code usr:clientLRA-2.0} feature.
 *
 * <p>This class is part of the {@code com.ibm.saga.clientlra.api} package, which is
 * listed in the bundle's {@code Export-Package} header and in the feature manifest's
 * {@code IBM-API-Package} header.  Applications that list {@code usr:clientLRA-2.0}
 * in their {@code server.xml} can therefore use it on the default application
 * class path without any additional dependency.
 *
 * <p>For most use-cases the MicroProfile LRA annotations (e.g. {@link LRA}) and the
 * standard {@code Long-Running-Action} header constant are all you need; this class
 * just exposes them as convenient static helpers.
 */
public final class LraClientUtils {

    /** The standard JAX-RS header name for the LRA context URI. */
    public static final String LRA_HTTP_CONTEXT_HEADER = LRA.LRA_HTTP_CONTEXT_HEADER;

    /** The standard JAX-RS header name used to end an LRA. */
    public static final String LRA_HTTP_ENDED_CONTEXT_HEADER = LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;

    private LraClientUtils() {
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
}
