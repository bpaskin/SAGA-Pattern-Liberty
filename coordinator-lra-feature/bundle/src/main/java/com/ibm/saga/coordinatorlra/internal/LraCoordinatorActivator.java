package com.ibm.saga.coordinatorlra.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;

import java.util.Hashtable;
import java.util.logging.Logger;

/**
 * OSGi {@link BundleActivator} for the {@code usr:coordinatorLRA-2.0} Liberty user feature.
 *
 * <p>On bundle start:
 * <ol>
 *   <li>Registers {@link LraCoordinatorStoreConfig} as a {@link ManagedService} so that
 *       Liberty delivers the {@code <lraCoordinatorStore>} server.xml element to it.</li>
 *   <li>Boots the Narayana Arjuna transaction-manager and initialises the LRA coordinator
 *       REST resources via {@link LraCoordinatorBootstrap}.</li>
 * </ol>
 */
public class LraCoordinatorActivator implements BundleActivator {

    private static final Logger LOG =
            Logger.getLogger(LraCoordinatorActivator.class.getName());

    private ServiceRegistration<ManagedService> storeConfigRegistration;
    private LraCoordinatorBootstrap bootstrap;

    @Override
    public void start(BundleContext context) throws Exception {
        LOG.info("usr:coordinatorLRA-2.0 — coordinator bundle starting (version "
                + context.getBundle().getVersion() + ")");

        // 1. Register the store config ManagedService so Liberty config-admin delivers
        //    <lraCoordinatorStore> attributes before we attempt to start the coordinator.
        LraCoordinatorStoreConfig storeConfig = new LraCoordinatorStoreConfig();

        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, LraCoordinatorStoreConfig.PID);

        storeConfigRegistration = context.registerService(
                ManagedService.class, storeConfig, props);

        LOG.info("usr:coordinatorLRA-2.0 — lraCoordinatorStore ManagedService registered (pid="
                + LraCoordinatorStoreConfig.PID + ")");

        // 2. Boot the Narayana coordinator using whatever store config is already available.
        //    LraCoordinatorStoreConfig.updated() will reconfigure on subsequent server.xml edits.
        bootstrap = new LraCoordinatorBootstrap(storeConfig);
        bootstrap.start();
    }

    @Override
    public void stop(BundleContext context) {
        if (bootstrap != null) {
            bootstrap.stop();
            bootstrap = null;
        }
        if (storeConfigRegistration != null) {
            storeConfigRegistration.unregister();
            storeConfigRegistration = null;
        }
        LOG.info("usr:coordinatorLRA-2.0 — coordinator bundle stopped");
    }
}
