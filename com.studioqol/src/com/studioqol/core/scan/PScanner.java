package com.studioqol.core.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects DataWeave property references {@code p('key')} / {@code Mule::p('key')} within a single
 * line of text. Line-scoped and dependency-free (operates on a plain {@code String}, not an
 * {@code IDocument}) so it is cheap enough to run inside a global mouse filter and works for the
 * embedded editors where no document is reachable. Analogous to {@link PlaceholderScanner} for
 * {@code ${...}}; one matcher core ({@link #scanLine}) serves both the offset lookup used by the
 * DataWeave hover ({@link #find}) and whole-file usage scanning.
 *
 * <p>Accepts single- or double-quoted keys and whitespace around the call ({@code p ( 'k' )}). Rejects
 * identifier-prefixed look-alikes ({@code map(}, {@code prop(}, {@code dump(}) by requiring the
 * character before {@code p} to be a non-identifier character (the {@code ::} in {@code Mule::p}
 * qualifies, since {@code ':'} is not an identifier character). Multi-line {@code p(\n'k'\n)} is a
 * documented non-goal.
 */
public final class PScanner {

	private PScanner() {
	}

	/** A located {@code p('key')} token: the key plus its line-relative span (end exclusive). */
	public static final class PHit {
		public final String key;
		public final int start;
		public final int end;

		PHit(String key, int start, int end) {
			this.key = key;
			this.start = start;
			this.end = end;
		}
	}

	/** All {@code p('key')} tokens in one line, left to right. */
	public static List<PHit> scanLine(String line) {
		List<PHit> hits = new ArrayList<>();
		if (line == null || line.isEmpty()) {
			return hits;
		}
		int n = line.length();
		for (int i = 0; i < n; i++) {
			if (line.charAt(i) != 'p') {
				continue;
			}
			if (i > 0 && isIdentifierChar(line.charAt(i - 1))) {
				continue; // part of a longer identifier (map, prop, dump, …)
			}
			int j = skipWhitespace(line, i + 1);
			if (j >= n || line.charAt(j) != '(') {
				continue;
			}
			j = skipWhitespace(line, j + 1);
			if (j >= n) {
				continue;
			}
			char quote = line.charAt(j);
			if (quote != '\'' && quote != '"') {
				continue;
			}
			int keyStart = j + 1;
			int k = keyStart;
			while (k < n && line.charAt(k) != quote) {
				k++;
			}
			if (k >= n) {
				continue; // unterminated string on this line
			}
			String key = line.substring(keyStart, k);
			if (key.isEmpty()) {
				continue;
			}
			int end = k + 1; // include the closing quote
			int afterQuote = skipWhitespace(line, end);
			if (afterQuote < n && line.charAt(afterQuote) == ')') {
				end = afterQuote + 1; // include the closing paren when present
			}
			hits.add(new PHit(key, i, end));
			i = end - 1; // resume after this token
		}
		return hits;
	}

	/**
	 * @param line the line text (no trailing newline)
	 * @param relOffset a character offset within {@code line}
	 * @return the {@code p('key')} token whose span contains {@code relOffset}, or {@code null}.
	 */
	public static PHit find(String line, int relOffset) {
		for (PHit hit : scanLine(line)) {
			if (relOffset >= hit.start && relOffset < hit.end) {
				return hit;
			}
		}
		return null;
	}

	private static int skipWhitespace(String s, int from) {
		int i = from;
		while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
			i++;
		}
		return i;
	}

	private static boolean isIdentifierChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$';
	}
}
