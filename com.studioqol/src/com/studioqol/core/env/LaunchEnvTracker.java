package com.studioqol.core.env;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.resource.ProjectChangeBus;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchListener;

/**
 * Records the environment of each launch (its {@code -D<env>=} VM arg) into {@link ActiveEnvStore},
 * keyed by project, so the active-env highlight persists across Studio restarts.
 */
public final class LaunchEnvTracker implements ILaunchListener {

	private final Map<ILaunch, Long> launchOrder = new IdentityHashMap<>();
	private final Map<IProject, Map<String, Long>> latestRecorded = new HashMap<>();
	private long sequence;

	@Override
	public void launchAdded(ILaunch launch) {
		record(launch);
	}

	@Override
	public void launchChanged(ILaunch launch) {
		record(launch);
	}

	@Override
	public synchronized void launchRemoved(ILaunch launch) {
		// Keep the last known env; removing a (terminated) launch shouldn't forget it.
		launchOrder.remove(launch);
	}

	private synchronized void record(ILaunch launch) {
		try {
			if (launch == null) {
				return;
			}
			// Capture ordering even when launch metadata is not available until launchChanged.
			long order = launchOrder.computeIfAbsent(launch, ignored -> ++sequence);
			ILaunchConfiguration cfg = launch.getLaunchConfiguration();
			if (cfg == null) {
				return;
			}
			String envProperty = new ActiveEnvService().envPropertyName();
			String env = ActiveEnvResolver.envArgOf(cfg, envProperty);
			if (env == null) {
				return;
			}
			IProject project = ActiveEnvResolver.projectOf(cfg);
			if (project != null) {
				Map<String, Long> bySelector = latestRecorded.computeIfAbsent(project, ignored -> new HashMap<>());
				if (order < bySelector.getOrDefault(envProperty, -1L)) {
					return;
				}
				bySelector.put(envProperty, order);
				ActiveEnvStore.set(project.getName(), envProperty, env);
				StudioQolPlugin.changeBus().fire(project, ProjectChangeBus.Kind.ACTIVE_ENV);
			}
		} catch (Exception e) {
			// best-effort
		}
	}
}
