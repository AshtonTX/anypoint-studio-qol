package com.studioqol.core.env;

/** Reads complete VM argument tokens, including quoted values and Studio's -M-D form. */
public final class EnvArguments {

	private EnvArguments() {
	}

	/** Last matching argument wins. Incomplete quoted arguments and embedded substrings are ignored. */
	public static String find(String arguments, String property) {
		if (arguments == null || property == null || property.isBlank()) {
			return null;
		}
		String result = null;
		StringBuilder token = new StringBuilder();
		char quote = 0;
		for (int i = 0; i <= arguments.length(); i++) {
			char c = i == arguments.length() ? ' ' : arguments.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				} else if (c == '\\' && i + 1 < arguments.length()
						&& arguments.charAt(i + 1) == quote) {
					token.append(arguments.charAt(++i));
				} else {
					token.append(c);
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (Character.isWhitespace(c)) {
				String arg = token.toString();
				String prefix = arg.startsWith("-M-D") ? "-M-D" + property + "=" : "-D" + property + "=";
				if (arg.startsWith(prefix)) {
					String value = arg.substring(prefix.length());
					result = value.isBlank() ? null : value;
				}
				token.setLength(0);
			} else {
				token.append(c);
			}
		}
		return result;
	}
}
