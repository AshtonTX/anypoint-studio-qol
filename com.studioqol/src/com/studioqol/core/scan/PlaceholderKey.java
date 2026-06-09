package com.studioqol.core.scan;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

/**
 * The key and secure flag of a {@code ${...}} placeholder region — the one place that strips the
 * wrapping braces and the optional {@code secure::} prefix for document-based consumers
 * (hover, hyperlink, find-usages).
 */
public final class PlaceholderKey {

	public final String key;
	public final boolean secure;

	private PlaceholderKey(String key, boolean secure) {
		this.key = key;
		this.secure = secure;
	}

	/** @return the parsed key of the placeholder at {@code region}, or {@code null} when empty/invalid. */
	public static PlaceholderKey of(IDocument doc, IRegion region) {
		if (doc == null || region == null) {
			return null;
		}
		try {
			String full = doc.get(region.getOffset(), region.getLength()); // ${...}
			if (full.length() < 3) {
				return null;
			}
			String inner = full.substring(2, full.length() - 1).trim();
			boolean secure = inner.startsWith("secure::");
			String key = (secure ? inner.substring("secure::".length()) : inner).trim();
			return key.isEmpty() ? null : new PlaceholderKey(key, secure);
		} catch (BadLocationException e) {
			return null;
		}
	}
}
