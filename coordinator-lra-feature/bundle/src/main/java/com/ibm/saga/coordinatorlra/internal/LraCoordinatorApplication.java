package com.ibm.saga.coordinatorlra.internal;

import io.narayana.lra.coordinator.api.AppContextListener;
import io.narayana.lra.coordinator.api.Coordinator;
import io.narayana.lra.coordinator.api.CoordinatorContainerFilter;
import io.narayana.lra.coordinator.api.RecoveryCoordinator;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JAX-RS {@link Application} that registers the Narayana LRA coordinator REST resources
 * with the Liberty JAX-RS runtime.
 *
 * <p>Liberty scans the bundle for {@link Application} subclasses and registers each one
 * automatically when the {@code restfulWS-x.x} feature is active.  The
 * {@link ApplicationPath} annotation maps all coordinator endpoints under
 * {@code /lra-coordinator}, matching the path expected by
 * {@code usr:clientLRA-2.0}'s default {@code <lraCoordinator path="/lra-coordinator"/>}.
 */
@ApplicationPath("/lra-coordinator")
public class LraCoordinatorApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new LinkedHashSet<>();
        // Core coordinator resource: start/join/close/cancel/status LRA operations.
        resources.add(Coordinator.class);
        // Recovery coordinator resource: handles participant recovery.
        resources.add(RecoveryCoordinator.class);
        // Container filter: propagates the Long-Running-Action header on outbound calls.
        resources.add(CoordinatorContainerFilter.class);
        // Context listener: initialises LRAService on application startup.
        resources.add(AppContextListener.class);
        return resources;
    }
}
