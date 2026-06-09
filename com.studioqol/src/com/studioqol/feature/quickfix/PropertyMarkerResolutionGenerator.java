package com.studioqol.feature.quickfix;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.ResourceText;
import com.studioqol.core.env.EnvFiles;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.validate.QolMarkers;

/**
 * Quick fixes for Studio QoL property problems. Adding a key always appends {@code key=} with an
 * EMPTY value — values are never copied across environments (wrong at best, a secrets leak at
 * worst). Destructive fixes (removing unused keys) are deliberately not offered.
 */
public final class PropertyMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

	private static final IMarkerResolution[] NONE = new IMarkerResolution[0];

	@Override
	public boolean hasResolutions(IMarker marker) {
		return getResolutions(marker).length > 0;
	}

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		try {
			String key = marker.getAttribute(QolMarkers.ATTR_KEY, null);
			IResource resource = marker.getResource();
			if (key == null || resource == null) {
				return NONE;
			}
			IProject project = resource.getProject();
			String type = marker.getType();
			if (QolMarkers.TYPE_MISSING_ENV.equals(type)) {
				return addKeyResolutions(key, missingEnvFiles(marker, project));
			}
			if (QolMarkers.TYPE_UNDEFINED.equals(type)
					&& !marker.getAttribute(QolMarkers.ATTR_SECURE, false)) {
				return addKeyResolutions(key, definedEnvFiles(project));
			}
			if (QolMarkers.TYPE_UNUSED.equals(type)) {
				return goToDefinitionResolutions(project, key);
			}
		} catch (Exception e) {
			StudioQolPlugin.log(e);
		}
		return NONE;
	}

	private static List<IFile> missingEnvFiles(IMarker marker, IProject project) {
		List<IFile> out = new ArrayList<>();
		for (String env : marker.getAttribute(QolMarkers.ATTR_MISSING_ENVS, "").split(",")) {
			IFile file = EnvFiles.forEnv(project, env.trim());
			if (file != null) {
				out.add(file);
			}
		}
		return out;
	}

	/** The project's known env files, in index order (excludes the secure file). */
	private static List<IFile> definedEnvFiles(IProject project) {
		List<IFile> out = new ArrayList<>();
		PropertyIndex index = StudioQolPlugin.indexManager().get(project);
		if (index != null) {
			for (String env : index.environments()) {
				IFile file = EnvFiles.forEnv(project, env);
				if (file != null) {
					out.add(file);
				}
			}
		}
		return out;
	}

	private static IMarkerResolution[] addKeyResolutions(String key, List<IFile> files) {
		List<IMarkerResolution> out = new ArrayList<>();
		for (IFile file : files) {
			out.add(new AddKeyResolution(key, file));
		}
		return out.toArray(NONE);
	}

	private static IMarkerResolution[] goToDefinitionResolutions(IProject project, String key) {
		PropertyIndex index = StudioQolPlugin.indexManager().get(project);
		if (index == null) {
			return NONE;
		}
		List<IMarkerResolution> out = new ArrayList<>();
		for (PropertyNavigation.Target target : PropertyNavigation.targets(index, key)) {
			out.add(new GoToDefinitionResolution(target));
		}
		return out.toArray(NONE);
	}

	/** Appends {@code key=} (empty value) to one env file; revalidation then clears the marker. */
	private static final class AddKeyResolution implements IMarkerResolution {
		private final String key;
		private final IFile file;

		AddKeyResolution(String key, IFile file) {
			this.key = key;
			this.file = file;
		}

		@Override
		public String getLabel() {
			return "Add '" + key + "=' to " + file.getName();
		}

		@Override
		public void run(IMarker marker) {
			try {
				String text = ResourceText.read(file);
				String prefix = (text == null || text.isEmpty() || text.endsWith("\n")) ? "" : "\n";
				String charset = file.getCharset();
				byte[] bytes = (prefix + key + "=\n")
						.getBytes(charset == null ? "ISO-8859-1" : charset);
				file.appendContents(new ByteArrayInputStream(bytes),
						IResource.FORCE | IResource.KEEP_HISTORY, null);
			} catch (Exception e) {
				StudioQolPlugin.log(e);
			}
		}
	}

	private static final class GoToDefinitionResolution implements IMarkerResolution {
		private final PropertyNavigation.Target target;

		GoToDefinitionResolution(PropertyNavigation.Target target) {
			this.target = target;
		}

		@Override
		public String getLabel() {
			return target.label;
		}

		@Override
		public void run(IMarker marker) {
			PropertyNavigation.open(target.location);
		}
	}
}
