package com.studioqol;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.studioqol.core.env.LaunchEnvTracker;
import com.studioqol.core.index.PropertyIndexManager;
import com.studioqol.core.resource.MuleResourceListener;

/**
 * Bundle activator and owner of the shared infrastructure. The property index is a static singleton
 * so the hover resolves even before activation; the activator additionally wires the resource listener
 * (index freshness) and the launch listener (persisting the active env per project).
 */
public class StudioQolPlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.studioqol";

	private static final PropertyIndexManager INDEX = new PropertyIndexManager();

	private static StudioQolPlugin plugin;
	private MuleResourceListener resourceListener;
	private LaunchEnvTracker launchTracker;

	/** The shared per-project index. Always available (does not require activation). */
	public static PropertyIndexManager indexManager() {
		return INDEX;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		resourceListener = new MuleResourceListener(INDEX);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener,
				IResourceChangeEvent.POST_CHANGE);
		ILaunchManager lm = launchManager();
		if (lm != null) {
			launchTracker = new LaunchEnvTracker();
			lm.addLaunchListener(launchTracker);
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		try {
			if (resourceListener != null) {
				ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
				resourceListener = null;
			}
			ILaunchManager lm = launchManager();
			if (lm != null && launchTracker != null) {
				lm.removeLaunchListener(launchTracker);
			}
			launchTracker = null;
			INDEX.dispose();
		} finally {
			plugin = null;
			super.stop(context);
		}
	}

	private static ILaunchManager launchManager() {
		DebugPlugin dp = DebugPlugin.getDefault();
		return (dp == null) ? null : dp.getLaunchManager();
	}

	public static StudioQolPlugin getDefault() {
		return plugin;
	}

	public static void log(Throwable t) {
		StudioQolPlugin p = getDefault();
		if (p != null) {
			p.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, t.getMessage(), t));
		}
	}
}
