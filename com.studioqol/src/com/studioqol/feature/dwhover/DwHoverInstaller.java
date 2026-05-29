package com.studioqol.feature.dwhover;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

/**
 * Installs and removes the global SWT filter(s) that power the DataWeave property hover (and, from
 * Milestone 3, Ctrl/⌘-click navigation). Owned by {@code StudioQolPlugin}; {@link #install()} and
 * {@link #uninstall()} must be called on the UI thread (that is where {@code Display.addFilter} is
 * valid and where the popup manager touches its widgets). Both are idempotent.
 */
public final class DwHoverInstaller {

	private boolean installed;
	private DwEditorEventFilter filter;
	private DwPropertyHoverManager hoverManager;

	public void install() {
		if (installed) {
			return;
		}
		Display display = Display.getDefault();
		if (display == null || display.isDisposed()) {
			return;
		}
		hoverManager = new DwPropertyHoverManager();
		filter = new DwEditorEventFilter(new DataWeaveWidgetMatcher(), hoverManager,
				new DwPropertyNavigator());
		display.addFilter(SWT.MouseHover, filter);  // hover popup
		display.addFilter(SWT.MouseDown, filter);   // Ctrl/⌘-click navigation
		installed = true;
	}

	public void uninstall() {
		if (!installed) {
			return;
		}
		Display display = Display.getDefault();
		if (display != null && !display.isDisposed()) {
			display.removeFilter(SWT.MouseHover, filter);
			display.removeFilter(SWT.MouseDown, filter);
		}
		if (hoverManager != null) {
			hoverManager.dispose();
			hoverManager = null;
		}
		filter = null;
		installed = false;
	}
}
