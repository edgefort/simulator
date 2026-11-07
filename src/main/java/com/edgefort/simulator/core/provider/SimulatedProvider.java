package com.edgefort.simulator.core.provider;

import java.util.List;

/**
 * Registration contract implemented by each provider module. The implementing class must live in the module's root
 * package: request handlers in that package and its sub-packages belong to the provider.
 */
public interface SimulatedProvider {

    /** Stable identifier used under {@code simulator.providers} and by the admin API. */
    String id();

    List<ProviderOperation> operations();

    default String basePackage() {
        return getClass().getPackageName();
    }
}
