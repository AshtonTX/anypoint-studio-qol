package com.studioqol.core.env;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;

/**
 * Best-effort detection of the active environment from the project's launch configurations, reading
 * the {@code -D<envProperty>=<value>} VM argument (which also matches the {@code -M-D<envProperty>=}
 * form). Searches every string/collection attribute, so it works regardless of which attribute the
 * Mule launch type stores arguments under.
 *
 * <p>Resolution order: (0) a manual override set via the env switcher (persists until "Auto" is
 * chosen); (1) the most recently launched env-bearing config this session; (2) the env persisted
 * from a previous session by {@link LaunchEnvTracker} (survives restarts); (3) a single env if all
 * the project's configs agree on it. Distinct per-env configs with nothing launched are ambiguous
 * → {@code null} (the hover then shows all envs without a highlight).
 */
public final class ActiveEnvResolver {

	private static final String JDT_PROJECT_ATTR = "org.eclipse.jdt.launching.PROJECT_ATTR";

	private ActiveEnvResolver() {
	}

	public static String resolve(IProject project, String envProperty) {
		if (project == null || envProperty == null || envProperty.isEmpty()) {
			return null;
		}
		// 0) An explicit user choice from the env switcher outranks everything until cleared.
		String manual = ActiveEnvStore.getManual(project.getName());
		if (manual != null) {
			return manual;
		}
		if (DebugPlugin.getDefault() == null) {
			return ActiveEnvStore.get(project.getName(), envProperty);
		}
		try {
			ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();

			// 1) Most recently launched env-bearing config for this project this session.
			ILaunch[] launches = lm.getLaunches();
			for (int i = launches.length - 1; i >= 0; i--) {
				ILaunchConfiguration cfg = launches[i].getLaunchConfiguration();
				if (cfg == null || !belongsTo(cfg, project)) {
					continue;
				}
				String value = findEnvArg(cfg, envProperty);
				if (value != null) {
					return value;
				}
			}

			// 2) Persisted from a previous session (LaunchEnvTracker records each launch).
			String stored = ActiveEnvStore.get(project.getName(), envProperty);
			if (stored != null) {
				return stored;
			}

			// 3) Nothing launched and nothing persisted: highlight only if the project's configs
			//    agree on one env. Distinct per-env configs are ambiguous -> no highlight.
			Set<String> envs = new LinkedHashSet<>();
			String last = null;
			for (ILaunchConfiguration cfg : lm.getLaunchConfigurations()) {
				if (!belongsTo(cfg, project)) {
					continue;
				}
				String value = findEnvArg(cfg, envProperty);
				if (value != null) {
					envs.add(value);
					last = value;
				}
			}
			if (envs.size() == 1) {
				return last;
			}
		} catch (Exception e) {
			// best-effort: any failure means "unknown active env"
		}
		return null;
	}

	/** The {@code -D<envProperty>=} value in a single launch config, or {@code null}. */
	public static String envArgOf(ILaunchConfiguration cfg, String envProperty) {
		if (cfg == null || envProperty == null || envProperty.isEmpty()) {
			return null;
		}
		try {
			return findEnvArg(cfg, envProperty);
		} catch (CoreException e) {
			return null;
		}
	}

	/** The project a launch config targets (first mapped resource, else the JDT project attribute). */
	public static IProject projectOf(ILaunchConfiguration cfg) {
		try {
			IResource[] mapped = cfg.getMappedResources();
			if (mapped != null) {
				for (IResource r : mapped) {
					if (r != null && r.getProject() != null) {
						return r.getProject();
					}
				}
			}
			String name = cfg.getAttribute(JDT_PROJECT_ATTR, "");
			if (name != null && !name.isEmpty()) {
				return ResourcesPlugin.getWorkspace().getRoot().getProject(name);
			}
		} catch (CoreException e) {
			// ignore
		}
		return null;
	}

	private static boolean belongsTo(ILaunchConfiguration cfg, IProject project) {
		try {
			IResource[] mapped = cfg.getMappedResources();
			if (mapped != null) {
				for (IResource r : mapped) {
					if (r != null && project.equals(r.getProject())) {
						return true;
					}
				}
			}
			return project.getName().equals(cfg.getAttribute(JDT_PROJECT_ATTR, ""));
		} catch (CoreException e) {
			return false;
		}
	}

	private static String findEnvArg(ILaunchConfiguration cfg, String envProperty) throws CoreException {
		Map<String, Object> attributes = cfg.getAttributes();
		if (attributes == null) {
			return null;
		}
		for (Object value : attributes.values()) {
			String text = asText(value);
			if (text == null) {
				continue;
			}
			String env = EnvArguments.find(text, envProperty);
			if (env != null) {
				return env;
			}
		}
		return null;
	}

	private static String asText(Object value) {
		if (value instanceof String) {
			return (String) value;
		}
		if (value instanceof Collection) {
			StringBuilder b = new StringBuilder();
			for (Object o : (Collection<?>) value) {
				if (o != null) {
					b.append(o).append(' ');
				}
			}
			return b.toString();
		}
		return null;
	}

}
