package com.studioqol.core;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Resolves the Mule {@link IProject} for a DataWeave hover/navigation. Unlike Feature 1 (which derives
 * the project from the editor's {@code IDocument}), Feature 2 hooks DataWeave editors from outside and
 * only has an SWT widget — there is no document to resolve from. So we resolve via the workbench:
 * <ol>
 *   <li>the active editor's input file's project (for an embedded expression field the active editor is
 *       the Mule config/flow editor; for a standalone {@code .dwl} it is the {@code DFLEditor} — both
 *       inputs map to the Mule project);</li>
 *   <li>failing that, the sole Mule-nature project in the workspace (the common single-app case).</li>
 * </ol>
 * Returns {@code null} when neither applies; the hover renderer treats that as "unresolved".
 *
 * <p>Must be called on the UI thread (it touches the active workbench window).
 */
public final class DwProjectResolver {

	private DwProjectResolver() {
	}

	public static IProject resolveActiveMuleProject() {
		IProject fromEditor = projectOfActiveEditor();
		if (fromEditor != null && MuleProjects.isMuleProject(fromEditor)) {
			return fromEditor;
		}
		return soleMuleProject();
	}

	private static IProject projectOfActiveEditor() {
		if (!PlatformUI.isWorkbenchRunning()) {
			return null;
		}
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = (window == null) ? null : window.getActivePage();
		IEditorPart editor = (page == null) ? null : page.getActiveEditor();
		IEditorInput input = (editor == null) ? null : editor.getEditorInput();
		if (input == null) {
			return null;
		}
		IFile file = (input instanceof IFileEditorInput) ? ((IFileEditorInput) input).getFile()
				: Adapters.adapt(input, IFile.class);
		return (file == null) ? null : file.getProject();
	}

	private static IProject soleMuleProject() {
		IProject only = null;
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (MuleProjects.isMuleProject(project)) {
				if (only != null) {
					return null; // more than one Mule project — ambiguous
				}
				only = project;
			}
		}
		return only;
	}
}
