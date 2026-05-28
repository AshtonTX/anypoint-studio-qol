package com.studioqol.core.env;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchListener;

/**
 * Records the environment of each launch (its {@code -D<env>=} VM arg) into {@link ActiveEnvStore},
 * keyed by project, so the active-env highlight persists across Studio restarts.
 */
public final class LaunchEnvTracker implements ILaunchListener {

	@Override
	public void launchAdded(ILaunch launch) {
		record(launch);
	}

	@Override
	public void launchChanged(ILaunch launch) {
		record(launch);
	}

	@Override
	public void launchRemoved(ILaunch launch) {
		// Keep the last known env; removing a (terminated) launch shouldn't forget it.
	}

	private void record(ILaunch launch) {
		try {
			if (launch == null) {
				return;
			}
			ILaunchConfiguration cfg = launch.getLaunchConfiguration();
			if (cfg == null) {
				return;
			}
			String env = ActiveEnvResolver.envArgOf(cfg, new ActiveEnvService().envPropertyName());
			if (env == null) {
				return;
			}
			IProject project = ActiveEnvResolver.projectOf(cfg);
			if (project != null) {
				ActiveEnvStore.set(project.getName(), env);
			}
		} catch (Exception e) {
			// best-effort
		}
	}
}
