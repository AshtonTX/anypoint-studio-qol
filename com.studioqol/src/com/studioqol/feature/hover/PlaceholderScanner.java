package com.studioqol.feature.hover;

import java.util.ArrayDeque;
import java.util.Deque;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * Detects the {@code ${...}} placeholder under a document offset. Line-scoped, brace-matching;
 * returns the innermost placeholder span containing the offset, so it also handles
 * {@code ${secure::key}} and nested {@code ${a${b}}}.
 */
public final class PlaceholderScanner {

	private PlaceholderScanner() {
	}

	/**
	 * @return the region of the innermost {@code ${...}} containing {@code offset}, or {@code null}
	 *         when the offset is not inside a placeholder.
	 */
	public static IRegion find(IDocument doc, int offset) {
		if (doc == null) {
			return null;
		}
		try {
			IRegion line = doc.getLineInformationOfOffset(offset);
			int base = line.getOffset();
			String text = doc.get(base, line.getLength());
			int rel = offset - base;
			Deque<Integer> stack = new ArrayDeque<>();
			IRegion best = null;
			for (int i = 0; i < text.length(); i++) {
				if (i + 1 < text.length() && text.charAt(i) == '$' && text.charAt(i + 1) == '{') {
					stack.push(i);
					i++; // skip the '{'
				} else if (text.charAt(i) == '}' && !stack.isEmpty()) {
					int start = stack.pop();
					int endExclusive = i + 1;
					boolean contains = rel >= start && rel < endExclusive;
					if (contains && (best == null || (endExclusive - start) < best.getLength())) {
						best = new Region(base + start, endExclusive - start);
					}
				}
			}
			return best;
		} catch (BadLocationException e) {
			return null;
		}
	}

	/** Strips the wrapping {@code ${ }} and an optional {@code secure::} prefix from a placeholder. */
	public static String extractKey(IDocument doc, IRegion region) {
		if (doc == null || region == null) {
			return null;
		}
		try {
			String s = doc.get(region.getOffset(), region.getLength());
			s = s.substring(2, s.length() - 1).trim(); // drop "${" and "}"
			return s.startsWith("secure::") ? s.substring("secure::".length()) : s;
		} catch (BadLocationException e) {
			return null;
		}
	}
}
