package com.studioqol.feature.hover;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;

public class PropertyHoverTextTest {
	@Test public void keepsMissingEnvironmentsVisibleAndDistinctFromEmpty() {
		PropertyDefinition def = PropertyDefinition.envFile("url", Map.of(
				"local", new EnvValue("local", "", null, EnvValue.State.EMPTY)));
		PropertyIndex index = new PropertyIndex(Map.of("url", def), List.of("local", "prod"));
		String html = PropertyHoverText.render("url", false, "env", "prod", index);
		assertTrue(html.contains("<b>local</b>"));
		assertTrue(html.contains("(empty)"));
		assertTrue(html.contains("<b>prod</b>"));
		assertTrue(html.contains("(missing)"));
		assertTrue(html.contains("← active"));
	}

	@Test public void escapesValuesAndMasksEncryptedContent() {
		PropertyDefinition def = PropertyDefinition.envFile("url", Map.of(
				"local", new EnvValue("local", "<host>&port", null, EnvValue.State.PRESENT),
				"prod", new EnvValue("prod", "![do-not-show]", null, EnvValue.State.SECURE)));
		PropertyIndex index = new PropertyIndex(Map.of("url", def), List.of("local", "prod"));
		String html = PropertyHoverText.render("url", false, "env", null, index);
		assertTrue(html.contains("&lt;host&gt;&amp;port"));
		assertTrue(html.contains("(secure)"));
		assertFalse(html.contains("do-not-show"));
	}

	@Test public void secureDataWeaveReferencesStaySecureEvenWithPlainNameCollision() {
		PropertyIndex index = new PropertyIndex(Map.of("password",
				PropertyDefinition.global("password", "do-not-show", null)), List.of());
		String html = PropertyHoverText.render("secure::password", false, "env", null, index,
				PropertyHoverText.LabelStyle.DATAWEAVE);
		assertTrue(html.contains("p('secure::password')"));
		assertTrue(html.contains("Value not shown"));
		assertFalse(html.contains("do-not-show"));
		assertTrue(PropertyNavigation.targets(index, "password", true).isEmpty());
	}
}
