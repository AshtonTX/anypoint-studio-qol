package com.studioqol.core.env;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;

import com.studioqol.core.MuleProjectLayout;

/** Enumerates a project's {@code src/main/resources/envs/*.properties} files. */
public final class EnvFiles {

	private EnvFiles() {
	}

	/** All {@code .properties} files in the envs dir (including a secure file), or empty. */
	public static List<IFile> list(IProject project) {
		List<IFile> out = new ArrayList<>();
		if (project == null || !project.isAccessible()) {
			return out;
		}
		IFolder dir = project.getFolder(new Path(MuleProjectLayout.ENVS_DIR));
		if (!dir.exists()) {
			return out;
		}
		try {
			for (IResource r : dir.members()) {
				if (r.getType() == IResource.FILE
						&& "properties".equalsIgnoreCase(((IFile) r).getFileExtension())) {
					out.add((IFile) r);
				}
			}
		} catch (Exception e) {
			// fall through with what we have
		}
		return out;
	}

	/** The {@code envs/<env>.properties} file, or {@code null} when it does not exist. */
	public static IFile forEnv(IProject project, String env) {
		if (project == null || env == null || env.isEmpty()) {
			return null;
		}
		IFile file = project.getFile(new Path(MuleProjectLayout.ENVS_DIR + "/" + env + ".properties"));
		return file.exists() ? file : null;
	}
}
