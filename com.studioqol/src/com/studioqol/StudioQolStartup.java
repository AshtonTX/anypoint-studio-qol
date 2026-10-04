package com.studioqol;

import org.eclipse.ui.IStartup;

/**
 * Eager-startup hook. Loading this class on workbench startup activates the bundle, so the activator
 * registers the launch listener immediately — before the user runs a config. Without this the bundle
 * only activates on the first hover, missing earlier launches and breaking active-env persistence.
 * The real work is in {@link StudioQolPlugin#start}; this is just the activation trigger.
 */
public final class StudioQolStartup implements IStartup {

	@Override
	public void earlyStartup() {
		StudioQolPlugin plugin = StudioQolPlugin.getDefault();
		if (plugin != null) {
			plugin.installEditorHooks();
		}
		// Markers are transient — populate them for all open Mule projects each session.
		if (plugin != null && plugin.validationScheduler() != null) {
			plugin.validationScheduler().validateAllOpen();
		}
	}
}
