package com.studioqol.core.env;

import static org.junit.Assert.*;

import org.junit.Test;

public class EnvArgumentsTest {
	@Test public void readsStandardAndMuleArguments() {
		assertEquals("local", EnvArguments.find("-Xmx1g -Denv=local", "env"));
		assertEquals("test", EnvArguments.find("-M-Denv=test -Xmx1g", "env"));
		assertEquals("prod", EnvArguments.find("-Dmule.env=prod", "mule.env"));
	}

	@Test public void handlesQuotedValuesAndEntireArguments() {
		assertEquals("local", EnvArguments.find("-Denv=\"local\"", "env"));
		assertEquals("qa west", EnvArguments.find("'-M-Denv=qa west'", "env"));
		assertEquals("test", EnvArguments.find("\"-Denv=test\"", "env"));
	}

	@Test public void honorsLastDefinitionAndEmptyReset() {
		assertEquals("prod", EnvArguments.find("-Denv=local -Denv=prod", "env"));
		assertNull(EnvArguments.find("-Denv=local -Denv=", "env"));
	}

	@Test public void ignoresLookalikesAndIncompleteQuotes() {
		assertNull(EnvArguments.find("-Dother=-Denv=prod", "env"));
		assertNull(EnvArguments.find("prefix-Denv=prod -Denvironment=test", "env"));
		assertNull(EnvArguments.find("-DmuleXenv=prod", "mule.env"));
		assertNull(EnvArguments.find("-Denv=\"unfinished", "env"));
		assertNull(EnvArguments.find(null, "env"));
	}

	@Test public void validatesPreferenceNames() {
		assertTrue(ActiveEnvService.isValidPropertyName("mule.env"));
		assertTrue(ActiveEnvService.isValidPropertyName("app_env-name"));
		assertFalse(ActiveEnvService.isValidPropertyName(""));
		assertFalse(ActiveEnvService.isValidPropertyName("${env}"));
		assertFalse(ActiveEnvService.isValidPropertyName("env=local"));
	}
}
