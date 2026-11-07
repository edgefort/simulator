package com.edgefort.simulator.infrastructure.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller inside a provider package as part of the simulator control plane rather than the provider
 * surface. Such controllers are mounted exactly at their declared {@code /admin/...} paths: they never receive the
 * provider base path (which would move them out of the admin token's protection) and are not hidden when the
 * provider is disabled.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ControlPlaneEndpoint {
}
