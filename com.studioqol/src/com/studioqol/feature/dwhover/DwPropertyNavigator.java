package com.studioqol.feature.dwhover;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.DwProjectResolver;
import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.PropertyNavigation.Target;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.feature.dwhover.PScanner.PHit;

/**
 * Ctrl/⌘-click navigation from a DataWeave {@code p('key')} to its definition, reusing the shared
 * {@link PropertyNavigation} resolver/opener (the same targets Feature 1's hyperlink uses for
 * {@code ${key}}). A single target opens immediately; multiple env-file targets show a popup menu
 * chooser at the pointer (one item per environment), mirroring the multi-link chooser of Feature 1.
 */
public final class DwPropertyNavigator {

	private Menu menu;

	public void navigate(StyledText widget, PHit hit) {
		IProject project = DwProjectResolver.resolveActiveMuleProject();
		PropertyIndex index = (project == null) ? null : StudioQolPlugin.indexManager().get(project);
		if (index == null) {
			return;
		}
		List<Target> targets = PropertyNavigation.targets(index, hit.key);
		if (targets.isEmpty()) {
			return;
		}
		if (targets.size() == 1) {
			PropertyNavigation.open(targets.get(0).location);
			return;
		}
		showChooser(widget, targets);
	}

	private void showChooser(StyledText widget, List<Target> targets) {
		if (menu != null && !menu.isDisposed()) {
			menu.dispose();
		}
		menu = new Menu(widget);
		for (Target target : targets) {
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(target.label);
			item.addSelectionListener(
					SelectionListener.widgetSelectedAdapter(e -> PropertyNavigation.open(target.location)));
		}
		menu.setLocation(widget.getDisplay().getCursorLocation());
		menu.setVisible(true);
	}
}
