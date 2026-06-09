package com.studioqol;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.studioqol.core.ProjectCache;
import com.studioqol.core.env.LaunchEnvTracker;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.index.PropertyIndexBuilder;
import com.studioqol.core.resource.MuleResourceListener;
import com.studioqol.core.resource.ProjectChangeBus;
import com.studioqol.core.usage.UsageIndex;
import com.studioqol.core.usage.UsageScanner;
import com.studioqol.feature.dwhover.DwHoverInstaller;

/**
 * Bundle activator and owner of the shared infrastructure. The property index is a static singleton
 * so the hover resolves even before activation; the activator additionally wires the resource listener
 * (index freshness) and the launch listener (persisting the active env per project).
 */
public class StudioQolPlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.studioqol";

	private static final ProjectCache<PropertyIndex> INDEX = new ProjectCache<>(new PropertyIndexBuilder()::build);
	private static final ProjectCache<UsageIndex> USAGES = new ProjectCache<>(new UsageScanner()::build);
	private static final ProjectChangeBus BUS = new ProjectChangeBus();

	private static StudioQolPlugin plugin;
	private MuleResourceListener resourceListener;
	private LaunchEnvTracker launchTracker;
	private DwHoverInstaller dwHoverInstaller;

	/** The shared per-project property index. Always available (does not require activation). */
	public static ProjectCache<PropertyIndex> indexManager() {
		return INDEX;
	}

	/** The shared per-project usage index ({@code ${key}} / {@code p('key')} occurrences). */
	public static ProjectCache<UsageIndex> usageManager() {
		return USAGES;
	}

	/** Per-project change notifications (index invalidated, active env switched). Always available. */
	public static ProjectChangeBus changeBus() {
		return BUS;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		resourceListener = new MuleResourceListener();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener,
				IResourceChangeEvent.POST_CHANGE);
		ILaunchManager lm = launchManager();
		if (lm != null) {
			launchTracker = new LaunchEnvTracker();
			lm.addLaunchListener(launchTracker);
		}
		// Feature 2: install the global SWT filter that adds the DataWeave p('...') hover. addFilter
		// must run on the UI thread; asyncExec defers it until the workbench event loop is pumping and
		// avoids any chance of a UI-thread deadlock during bundle start.
		dwHoverInstaller = new DwHoverInstaller();
		Display display = Display.getDefault();
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> dwHoverInstaller.install());
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
			if (dwHoverInstaller != null) {
				Display display = Display.getDefault();
				if (display != null && !display.isDisposed()) {
					try {
						display.syncExec(() -> dwHoverInstaller.uninstall());
					} catch (RuntimeException e) {
						// Display may already be tearing down on shutdown — safe to ignore.
					}
				}
				dwHoverInstaller = null;
			}
			INDEX.dispose();
			USAGES.dispose();
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
