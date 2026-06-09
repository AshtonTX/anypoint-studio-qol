package com.studioqol.core.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * Detects {@code ${...}} placeholders. Line-scoped, brace-matching; handles
 * {@code ${secure::key}} and nested {@code ${a${b}}}. One matcher core ({@link #scanLine}) serves
 * both the offset lookup used by hover/hyperlink ({@link #find}) and whole-file usage scanning.
 */
public final class PlaceholderScanner {

	private PlaceholderScanner() {
	}

	/** One matched {@code ${...}} span within a line. Offsets are line-relative, end exclusive. */
	public static final class Hit {
		/** Inner text with {@code secure::} stripped and trimmed. Not statically usable when {@link #dynamic}. */
		public final String key;
		public final boolean secure;
		/** True when the inner text itself contains a nested {@code ${...}} (key unresolvable statically). */
		public final boolean dynamic;
		public final int start;
		public final int end;

		Hit(String key, boolean secure, boolean dynamic, int start, int end) {
			this.key = key;
			this.secure = secure;
			this.dynamic = dynamic;
			this.start = start;
			this.end = end;
		}
	}

	/**
	 * All placeholder spans in one line, in closing order (so an inner nested placeholder precedes
	 * the dynamic outer one that wraps it).
	 */
	public static List<Hit> scanLine(String text) {
		List<Hit> hits = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return hits;
		}
		Deque<Integer> stack = new ArrayDeque<>();
		for (int i = 0; i < text.length(); i++) {
			if (i + 1 < text.length() && text.charAt(i) == '$' && text.charAt(i + 1) == '{') {
				stack.push(i);
				i++; // skip the '{'
			} else if (text.charAt(i) == '}' && !stack.isEmpty()) {
				int start = stack.pop();
				String inner = text.substring(start + 2, i).trim();
				boolean dynamic = inner.contains("${");
				boolean secure = inner.startsWith("secure::");
				String key = secure ? inner.substring("secure::".length()).trim() : inner;
				hits.add(new Hit(key, secure, dynamic, start, i + 1));
			}
		}
		return hits;
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
			int rel = offset - base;
			Hit best = null;
			for (Hit hit : scanLine(doc.get(base, line.getLength()))) {
				boolean contains = rel >= hit.start && rel < hit.end;
				if (contains && (best == null || (hit.end - hit.start) < (best.end - best.start))) {
					best = hit;
				}
			}
			return best == null ? null : new Region(base + best.start, best.end - best.start);
		} catch (BadLocationException e) {
			return null;
		}
	}

	/** Strips the wrapping {@code ${ }} and an optional {@code secure::} prefix from a placeholder. */
	public static String extractKey(IDocument doc, IRegion region) {
		PlaceholderKey pk = PlaceholderKey.of(doc, region);
		return pk == null ? null : pk.key;
	}
}
