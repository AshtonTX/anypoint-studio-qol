package com.studioqol.core.validate;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.usage.UsageIndex;

/**
 * Validates one project's properties off the UI thread. Getting the indexes may (re)build them —
 * that cost is exactly why this runs in a Job. Scheduled debounced by {@link ValidationScheduler}.
 */
final class PropertyValidationJob extends WorkspaceJob {

	private final IProject project;

	PropertyValidationJob(IProject project) {
		super("Studio QoL property validation (" + project.getName() + ")");
		this.project = project;
		setRule(project);
		setSystem(false);
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
		if (!project.isAccessible()) {
			return Status.OK_STATUS;
		}
		PropertyIndex index = StudioQolPlugin.indexManager().get(project);
		UsageIndex usages = StudioQolPlugin.usageManager().get(project);
		if (index == null || usages == null) {
			return Status.OK_STATUS;
		}
		QolMarkers.replace(project,
				PropertyValidator.validate(index, usages, new ActiveEnvService().envPropertyName()));
		return Status.OK_STATUS;
	}
}
