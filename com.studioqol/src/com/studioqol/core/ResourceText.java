package com.studioqol.core;

import java.io.InputStream;

import org.eclipse.core.resources.IFile;

/** Charset-aware whole-file read of a workspace file (project charset, ISO-8859-1 fallback). */
public final class ResourceText {

	private ResourceText() {
	}

	/** @return the file's text, or {@code null} when it cannot be read. */
	public static String read(IFile file) {
		if (file == null || !file.exists()) {
			return null;
		}
		String charset;
		try {
			charset = file.getCharset();
		} catch (Exception e) {
			charset = "ISO-8859-1";
		}
		try (InputStream in = file.getContents()) {
			return new String(in.readAllBytes(), charset == null ? "ISO-8859-1" : charset);
		} catch (Exception e) {
			return null;
		}
	}
}
