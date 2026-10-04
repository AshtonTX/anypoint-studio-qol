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
 * identifier-prefixed look-alikes and other modules' functions. Only a complete call with one
 * literal argument is recognized; concatenated and interpolated keys remain unresolved.
 * Multi-line {@code p(\n'k'\n)} is a documented non-goal.
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
			if (!isPropertyFunction(line, i)) {
				continue;
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
			StringBuilder key = new StringBuilder();
			boolean literal = true;
			int k = j + 1;
			while (k < n && line.charAt(k) != quote) {
				char ch = line.charAt(k++);
				if (ch == '$') {
					literal = false; // DataWeave interpolation (including $name) is runtime-only
				} else if (ch == '\\') {
					if (k >= n) {
						literal = false;
						break;
					}
					ch = line.charAt(k++);
					switch (ch) {
					case '\\': case '\'': case '"': case '$': case '`': break;
					case 'n': ch = '\n'; break;
					case 'r': ch = '\r'; break;
					case 't': ch = '\t'; break;
					case 'b': ch = '\b'; break;
					case 'f': ch = '\f'; break;
					case 'u':
						if (k + 4 > n) {
							literal = false;
						} else {
							try {
								ch = (char) Integer.parseInt(line.substring(k, k + 4), 16);
								k += 4;
							} catch (NumberFormatException e) {
								literal = false;
							}
						}
						break;
					default: literal = false;
					}
				}
				key.append(ch);
			}
			if (k >= n) {
				continue; // unterminated string on this line
			}
			if (!literal || key.length() == 0) {
				continue;
			}
			int afterQuote = skipWhitespace(line, k + 1);
			if (afterQuote >= n || line.charAt(afterQuote) != ')') {
				continue;
			}
			int end = afterQuote + 1;
			hits.add(new PHit(key.toString(), i, end));
			i = end - 1; // resume after this token
		}
		return hits;
	}

	private static boolean isPropertyFunction(String line, int at) {
		if (at > 0 && isIdentifierChar(line.charAt(at - 1))) {
			return false;
		}
		int before = at - 1;
		while (before >= 0 && Character.isWhitespace(line.charAt(before))) {
			before--;
		}
		if (before >= 0 && line.charAt(before) == '.') {
			return false;
		}
		if (before >= 0 && line.charAt(before) == ':') {
			int moduleStart = at - "Mule::".length();
			return moduleStart >= 0 && line.startsWith("Mule::", moduleStart)
					&& (moduleStart == 0 || (!isIdentifierChar(line.charAt(moduleStart - 1))
							&& line.charAt(moduleStart - 1) != ':' && line.charAt(moduleStart - 1) != '.'));
		}
		return true;
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
