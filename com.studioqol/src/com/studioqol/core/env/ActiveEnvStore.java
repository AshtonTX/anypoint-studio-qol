package com.studioqol.core.env;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Persists the active environment per project in the workspace instance preferences, so the
 * active-env highlight survives Studio restarts. Two namespaces: the last-LAUNCHED env (updated by
 * {@link LaunchEnvTracker} on each launch) and an explicit MANUAL override (set by the env
 * switcher, outranks launches until cleared via its "Auto" item).
 */
public final class ActiveEnvStore {

	private static final String NODE = "com.studioqol";
	private static final String PREFIX = "activeEnv.";
	private static final String MANUAL_PREFIX = "manualEnv.";

	private ActiveEnvStore() {
	}

	/** The user's explicit env choice for a project, or {@code null} when on automatic. */
	public static String getManual(String projectName) {
		if (projectName == null || projectName.isEmpty()) {
			return null;
		}
		String v = InstanceScope.INSTANCE.getNode(NODE).get(MANUAL_PREFIX + projectName, null);
		return (v == null || v.isBlank()) ? null : v;
	}

	public static void setManual(String projectName, String env) {
		if (projectName == null || projectName.isEmpty() || env == null || env.isEmpty()) {
			return;
		}
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(NODE);
		node.put(MANUAL_PREFIX + projectName, env);
		flush(node);
	}

	/** Back to automatic (launch-derived) resolution. */
	public static void clearManual(String projectName) {
		if (projectName == null || projectName.isEmpty()) {
			return;
		}
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(NODE);
		node.remove(MANUAL_PREFIX + projectName);
		flush(node);
	}

	public static String get(String projectName) {
		return get(projectName, ActiveEnvService.DEFAULT_ENV_PROP);
	}

	public static String get(String projectName, String envProperty) {
		if (projectName == null || projectName.isEmpty()) {
			return null;
		}
		String v = InstanceScope.INSTANCE.getNode(NODE).get(launchKey(projectName, envProperty), null);
		return (v == null || v.isBlank()) ? null : v;
	}

	public static void set(String projectName, String env) {
		set(projectName, ActiveEnvService.DEFAULT_ENV_PROP, env);
	}

	public static void set(String projectName, String envProperty, String env) {
		if (projectName == null || projectName.isEmpty() || env == null || env.isEmpty()) {
			return;
		}
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(NODE);
		node.put(launchKey(projectName, envProperty), env);
		flush(node);
	}

	private static String launchKey(String projectName, String envProperty) {
		// Preserve existing default-selector preferences across upgrades.
		return ActiveEnvService.DEFAULT_ENV_PROP.equals(envProperty) ? PREFIX + projectName
				: "activeEnvByProperty." + envProperty.length() + ":" + envProperty + ":" + projectName;
	}

	private static void flush(IEclipsePreferences node) {
		try {
			node.flush();
		} catch (Exception e) {
			// best-effort persistence
		}
	}
}
