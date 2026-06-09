package com.studioqol.feature.envswitch;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.DwProjectResolver;
import com.studioqol.core.env.ActiveEnvResolver;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.env.ActiveEnvStore;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.resource.ProjectChangeBus;

/**
 * Direct click on the env switcher's toolbar button (the dropdown arrow shows
 * {@link EnvSwitcherMenu} instead): the same choices as a filterable dialog.
 */
public final class EnvSwitcherHandler extends AbstractHandler {

	private static final String AUTO = "Auto (from launches)";

	@Override
	public Object execute(ExecutionEvent event) {
		Shell shell = HandlerUtil.getActiveShell(event);
		IProject project = DwProjectResolver.resolveActiveMuleProject();
		if (project == null) {
			MessageDialog.openInformation(shell, "Active Mule Environment",
					"No Mule project found — open a file of a Mule project first.");
			return null;
		}
		PropertyIndex index = StudioQolPlugin.indexManager().get(project);
		List<String> envs = index == null ? List.of() : index.environments();
		if (envs.isEmpty()) {
			MessageDialog.openInformation(shell, "Active Mule Environment",
					"No envs/*.properties files found in " + project.getName() + ".");
			return null;
		}
		List<String> choices = new ArrayList<>(envs);
		choices.add(AUTO);
		String current = ActiveEnvResolver.resolve(project, new ActiveEnvService().envPropertyName());

		ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider());
		dialog.setTitle("Active Mule Environment — " + project.getName());
		dialog.setMessage("Select the environment to highlight in hovers and views:");
		dialog.setElements(choices.toArray());
		dialog.setMultipleSelection(false);
		if (current != null && choices.contains(current)) {
			dialog.setInitialSelections(current);
		}
		if (dialog.open() != Window.OK || dialog.getFirstResult() == null) {
			return null;
		}
		String chosen = (String) dialog.getFirstResult();
		if (AUTO.equals(chosen)) {
			ActiveEnvStore.clearManual(project.getName());
		} else {
			ActiveEnvStore.setManual(project.getName(), chosen);
		}
		StudioQolPlugin.changeBus().fire(project, ProjectChangeBus.Kind.ACTIVE_ENV);
		return null;
	}
}
