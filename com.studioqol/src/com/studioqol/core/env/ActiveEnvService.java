package com.studioqol.core.env;

import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Resolves the configurable name of the active-environment selector property (default {@code env},
 * i.e. {@code <configuration-properties file="envs/${env}.properties"/>}). The preference UI is
 * added in Milestone D; for now this reads the instance-scoped value with the default.
 */
public final class ActiveEnvService {

	public static final String NODE = "com.studioqol";
	public static final String PREF_ENV_PROPERTY_NAME = "activeEnvPropertyName";
	public static final String DEFAULT_ENV_PROP = "env";

	public String envPropertyName() {
		String v = InstanceScope.INSTANCE.getNode(NODE).get(PREF_ENV_PROPERTY_NAME, DEFAULT_ENV_PROP);
		return (v == null || v.isBlank()) ? DEFAULT_ENV_PROP : v.trim();
	}
}
