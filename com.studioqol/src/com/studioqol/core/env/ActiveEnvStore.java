package com.studioqol.core.env;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Persists the last-launched environment per project in the workspace instance preferences, so the
 * active-env highlight survives Studio restarts. Updated by {@link LaunchEnvTracker} on each launch.
 */
public final class ActiveEnvStore {

	private static final String NODE = "com.studioqol";
	private static final String PREFIX = "activeEnv.";

	private ActiveEnvStore() {
	}

	public static String get(String projectName) {
		if (projectName == null || projectName.isEmpty()) {
			return null;
		}
		String v = InstanceScope.INSTANCE.getNode(NODE).get(PREFIX + projectName, null);
		return (v == null || v.isBlank()) ? null : v;
	}

	public static void set(String projectName, String env) {
		if (projectName == null || projectName.isEmpty() || env == null || env.isEmpty()) {
			return;
		}
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(NODE);
		node.put(PREFIX + projectName, env);
		try {
			node.flush();
		} catch (Exception e) {
			// best-effort persistence
		}
	}
}
