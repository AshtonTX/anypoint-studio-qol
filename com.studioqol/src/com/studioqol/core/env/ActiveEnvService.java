package com.studioqol.core.env;

import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Resolves the configurable name of the active-environment selector property (default {@code env},
 * i.e. {@code <configuration-properties file="envs/${env}.properties"/>}). Configurable under
 * Preferences > Studio QoL for the current workspace.
 */
public final class ActiveEnvService {

	public static final String NODE = "com.studioqol";
	public static final String PREF_ENV_PROPERTY_NAME = "activeEnvPropertyName";
	public static final String DEFAULT_ENV_PROP = "env";

	public static boolean isValidPropertyName(String value) {
		return value != null && value.matches("[A-Za-z0-9_.-]+");
	}

	public String envPropertyName() {
		String v = InstanceScope.INSTANCE.getNode(NODE).get(PREF_ENV_PROPERTY_NAME, DEFAULT_ENV_PROP);
		return (v == null || v.isBlank()) ? DEFAULT_ENV_PROP : v.trim();
	}
}
