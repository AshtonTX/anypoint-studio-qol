package com.studioqol.core.usage;

import com.studioqol.core.index.SourceLocation;

/** One occurrence of a property reference in project sources. */
public final class PropertyUsage {

	/** Where the reference syntax lives. */
	public enum Origin {
		/** A {@code ${key}} placeholder in Mule config XML. */
		XML_PLACEHOLDER,
		/** A DataWeave {@code p('key')} call — in a {@code .dwl} file or inline DW inside the XML. */
		DW_CALL
	}

	public final String key;
	public final boolean secure;
	public final SourceLocation location;
	public final Origin origin;

	public PropertyUsage(String key, boolean secure, SourceLocation location, Origin origin) {
		this.key = key;
		this.secure = secure;
		this.location = location;
		this.origin = origin;
	}
}
