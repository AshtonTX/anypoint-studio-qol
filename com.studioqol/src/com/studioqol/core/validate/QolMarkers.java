package com.studioqol.core.validate;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import com.studioqol.core.validate.PropertyValidator.Finding;

/**
 * Marker types and the single batched replace used by the validation job. Markers are transient
 * (declared non-persistent in plugin.xml); startup validation repopulates them each session.
 */
public final class QolMarkers {

	public static final String TYPE_BASE = "com.studioqol.problem";
	public static final String TYPE_UNDEFINED = TYPE_BASE + ".undefined";
	public static final String TYPE_MISSING_ENV = TYPE_BASE + ".missingEnv";
	public static final String TYPE_UNUSED = TYPE_BASE + ".unused";

	public static final String ATTR_KEY = "qolKey";
	public static final String ATTR_SECURE = "qolSecure";
	public static final String ATTR_MISSING_ENVS = "qolMissingEnvs"; // comma-separated

	private QolMarkers() {
	}

	/**
	 * Replaces all Studio QoL markers of {@code project} with markers for {@code findings}. Called
	 * inside the validation {@code WorkspaceJob} (project rule held), so the delete+create batch
	 * lands as one resource-change notification — no Problems-view flicker.
	 */
	public static void replace(IProject project, List<Finding> findings) throws CoreException {
		project.deleteMarkers(TYPE_BASE, true, IResource.DEPTH_INFINITE);
		for (Finding finding : findings) {
			if (finding.location == null || finding.location.file == null
					|| !finding.location.file.exists()) {
				continue;
			}
			IMarker marker = finding.location.file.createMarker(typeOf(finding));
			marker.setAttribute(IMarker.MESSAGE, messageOf(finding));
			marker.setAttribute(IMarker.SEVERITY, severityOf(finding));
			marker.setAttribute(IMarker.LINE_NUMBER, finding.location.line);
			if (finding.location.offset >= 0) {
				marker.setAttribute(IMarker.CHAR_START, finding.location.offset);
				marker.setAttribute(IMarker.CHAR_END, finding.location.offset + finding.location.length);
			}
			marker.setAttribute(ATTR_KEY, finding.key);
			marker.setAttribute(ATTR_SECURE, finding.secure);
			if (!finding.missingEnvs.isEmpty()) {
				marker.setAttribute(ATTR_MISSING_ENVS, String.join(",", finding.missingEnvs));
			}
		}
	}

	private static String typeOf(Finding finding) {
		switch (finding.type) {
		case UNDEFINED:
			return TYPE_UNDEFINED;
		case MISSING_IN_ENV:
			return TYPE_MISSING_ENV;
		default:
			return TYPE_UNUSED;
		}
	}

	private static int severityOf(Finding finding) {
		return finding.type == Finding.Type.UNUSED ? IMarker.SEVERITY_INFO
				: IMarker.SEVERITY_WARNING;
	}

	private static String messageOf(Finding finding) {
		switch (finding.type) {
		case UNDEFINED:
			return finding.secure
					? "Secure property '" + finding.key + "' is not defined in the secure properties file"
					: "Property '" + finding.key
							+ "' is not defined in any environment file or global-property";
		case MISSING_IN_ENV:
			return "Property '" + finding.key + "' is missing in: "
					+ String.join(", ", finding.missingEnvs);
		default:
			return "Property '" + finding.key + "' is defined but never used";
		}
	}
}
