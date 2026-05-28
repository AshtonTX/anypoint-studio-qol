package com.studioqol.core.index;

import org.eclipse.core.resources.IFile;

/** Where a property/key/value lives: file + 1-based line + 0-based char offset + length. */
public final class SourceLocation {

	public final IFile file;
	public final int line;   // 1-based, for display
	public final int offset; // 0-based document offset
	public final int length;

	public SourceLocation(IFile file, int line, int offset, int length) {
		this.file = file;
		this.line = line;
		this.offset = offset;
		this.length = length;
	}
}
