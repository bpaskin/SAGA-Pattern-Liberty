package com.ibm.saga.clientlra.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;

import java.util.Hashtable;
import java.util.logging.Logger;

/**
 * OSGi {@link BundleActivator} for the {@code usr:clientLRA-2.0} Liberty user feature.
 *
 * <p>Registers {@link LraCoordinatorConfig} as a {@link ManagedService} under the pid
 * {@code com.ibm.saga.clientlra.coordinator}.  Liberty's Config Admin service then calls
 * {@link LraCoordinatorConfig#updated} whenever an {@code <lraCoordinator>} element
 * appears (or changes) in {@code server.xml}, allowing the coordinator host and port
 * to be driven entirely from server configuration.
 */
public class LraClientActivator implements BundleActivator {

    private static final Logger LOG = Logger.getLogger(LraClientActivator.class.getName());

    private ServiceRegistration<ManagedService> configRegistration;

    @Override
    public void start(BundleContext context) {
        LOG.info("usr:clientLRA-2.0 — LRA client bundle starting (version "
                + context.getBundle().getVersion() + ")");

        // Register LraCoordinatorConfig so Liberty delivers <lraCoordinator> config to it.
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, LraCoordinatorConfig.PID);

        configRegistration = context.registerService(
                ManagedService.class,
                new LraCoordinatorConfig(),
                props);

        LOG.info("usr:clientLRA-2.0 — lraCoordinator ManagedService registered (pid="
                + LraCoordinatorConfig.PID + ")");
    }

    @Override
    public void stop(BundleContext context) {
        if (configRegistration != null) {
            configRegistration.unregister();
            configRegistration = null;
        }
        LOG.info("usr:clientLRA-2.0 — LRA client bundle stopped");
    }
}
