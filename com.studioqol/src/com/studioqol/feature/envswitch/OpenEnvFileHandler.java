package com.studioqol.feature.envswitch;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.studioqol.core.DwProjectResolver;
import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.env.ActiveEnvResolver;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.env.EnvFiles;

/**
 * "Open Environment Properties File": jumps straight into an envs/*.properties file of the active
 * Mule project. One file opens directly; several show a type-to-filter list with the active env's
 * file preselected.
 */
public final class OpenEnvFileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		Shell shell = HandlerUtil.getActiveShell(event);
		IProject project = DwProjectResolver.resolveActiveMuleProject();
		if (project == null) {
			MessageDialog.openInformation(shell, "Open Environment File",
					"No Mule project found — open a file of a Mule project first.");
			return null;
		}
		List<IFile> files = EnvFiles.list(project);
		if (files.isEmpty()) {
			MessageDialog.openInformation(shell, "Open Environment File",
					"No envs/*.properties files found in " + project.getName() + ".");
			return null;
		}
		if (files.size() == 1) {
			open(files.get(0));
			return null;
		}
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((IFile) element).getName();
			}
		});
		dialog.setTitle("Open Environment File — " + project.getName());
		dialog.setMessage("Select a properties file (type to filter):");
		dialog.setElements(files.toArray());
		dialog.setMultipleSelection(false);
		IFile preselect = activeEnvFile(project, files);
		if (preselect != null) {
			dialog.setInitialSelections(preselect);
		}
		if (dialog.open() == Window.OK && dialog.getFirstResult() != null) {
			open((IFile) dialog.getFirstResult());
		}
		return null;
	}

	private static void open(IFile file) {
		PropertyNavigation.openAt(file, -1, 0);
	}

	private static IFile activeEnvFile(IProject project, List<IFile> files) {
		String env = ActiveEnvResolver.resolve(project, new ActiveEnvService().envPropertyName());
		if (env == null) {
			return null;
		}
		for (IFile file : files) {
			if (file.getName().equals(env + ".properties")) {
				return file;
			}
		}
		return null;
	}
}
