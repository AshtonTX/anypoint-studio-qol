package com.studioqol.core.index;

/** A property's value in ONE environment file. */
public final class EnvValue {

	public enum State {
		/** Key present with a non-empty value. */
		PRESENT,
		/** Key present but value blank (e.g. the example template). */
		EMPTY,
		/** Value is an encrypted {@code ![...]} secure value. */
		SECURE
	}

	public final String env;             // "local", "test", "prod", "example", ...
	public final String rawValue;        // decoded properties value, without resolving ${...}; "" when empty
	public final SourceLocation location; // location of the KEY in that env file (may be null)
	public final State state;

	public EnvValue(String env, String rawValue, SourceLocation location, State state) {
		this.env = env;
		this.rawValue = rawValue;
		this.location = location;
		this.state = state;
	}
}
