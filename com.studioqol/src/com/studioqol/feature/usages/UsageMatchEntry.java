package com.studioqol.feature.usages;

import org.eclipse.core.resources.IFile;

import com.studioqol.core.usage.PropertyUsage;

/**
 * One line-level element in the usage search result (each holds exactly one match). Carries the
 * display data captured at query time so the label provider never touches the filesystem.
 */
public final class UsageMatchEntry {

	public final IFile file;
	public final int line;
	public final String lineText;
	public final PropertyUsage.Origin origin;

	UsageMatchEntry(IFile file, int line, String lineText, PropertyUsage.Origin origin) {
		this.file = file;
		this.line = line;
		this.lineText = lineText;
		this.origin = origin;
	}
}
