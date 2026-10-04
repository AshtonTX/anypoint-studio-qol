package com.studioqol.feature.compare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;

public class ComparisonModelTest {

	@Test
	public void absentIndexProducesAnEmptyModel() {
		ComparisonModel model = ComparisonModel.build(null);
		assertTrue(model.rows.isEmpty());
		assertTrue(model.environments.isEmpty());
	}

	@Test
	public void onlyEnvironmentDefinitionsAppearInKeyOrder() {
		PropertyIndex index = new PropertyIndex(Map.of(
				"zebra", PropertyDefinition.envFile("zebra", Map.of()),
				"alpha", PropertyDefinition.envFile("alpha", Map.of()),
				"global", PropertyDefinition.global("global", "value", null),
				"secret", PropertyDefinition.secure("secret", null)), List.of("local", "prod"));
		ComparisonModel model = ComparisonModel.build(index);
		assertEquals(List.of("alpha", "zebra"), model.rows.stream().map(row -> row.key).toList());
		assertEquals(List.of("local", "prod"), model.environments);
	}

	@Test
	public void identicalValuesAreNeitherProblemsNorDifferences() {
		ComparisonModel.Row row = row(value("local", "same"), value("prod", "same"));
		assertFalse(row.hasProblem);
		assertFalse(row.hasDifferences);
		assertFalse(row.matches("", false, true));
	}

	@Test
	public void differentPresentValuesAreDifferencesButNotMissingProblems() {
		ComparisonModel.Row row = row(value("local", "localhost"), value("prod", "production"));
		assertFalse(row.hasProblem);
		assertTrue(row.hasDifferences);
		assertTrue(row.matches("db", false, true));
		assertFalse(row.matches("db", true, true));
	}

	@Test
	public void emptyDefinitionIsPresentAndDifferentFromNonemptyValue() {
		ComparisonModel.Row row = row(value("local", ""), value("prod", "production"));
		assertFalse(row.hasProblem);
		assertTrue(row.hasDifferences);
		assertEquals(EnvValue.State.EMPTY, row.byEnv.get("local").state);
	}

	@Test
	public void missingDefinitionIsDifferentEvenWhenPresentValueIsEmpty() {
		ComparisonModel.Row row = model(Map.of("local", value("local", ""))).rows.get(0);
		assertTrue(row.hasProblem);
		assertTrue(row.hasDifferences);
		assertTrue(row.matches(" HOST ", true, true));
	}

	@Test
	public void identicalEmptyDefinitionsHaveNoDifference() {
		assertFalse(row(value("local", ""), value("prod", "")).hasDifferences);
	}

	@Test
	public void encryptedValuesAreComparedAsStoredTextAndCannotBeSearched() {
		EnvValue local = new EnvValue("local", "![first-ciphertext]", null, EnvValue.State.SECURE);
		EnvValue prod = new EnvValue("prod", "![second-ciphertext]", null, EnvValue.State.SECURE);
		ComparisonModel.Row row = row(local, prod);
		assertTrue(row.hasDifferences);
		assertFalse(row.hasProblem);
		assertFalse(row.matches("ciphertext", false, false));
		assertFalse(row.matches("production", false, false));
		assertFalse(row(local, new EnvValue("prod", local.rawValue, null, EnvValue.State.SECURE))
				.hasDifferences);
	}

	@Test
	public void filterMatchesOnlyKeysIgnoringCaseAndSurroundingWhitespace() {
		ComparisonModel.Row row = row(value("local", "localhost"), value("prod", "production"));
		assertTrue(row.matches(" DB.HoST ", false, false));
		assertTrue(row.matches(null, false, false));
		assertFalse(row.matches("production", false, false));
		assertFalse(row.matches("unrelated", false, false));
	}

	private static ComparisonModel.Row row(EnvValue local, EnvValue prod) {
		return model(Map.of("local", local, "prod", prod)).rows.get(0);
	}

	private static ComparisonModel model(Map<String, EnvValue> values) {
		return ComparisonModel.build(new PropertyIndex(
				Map.of("db.host", PropertyDefinition.envFile("db.host", values)), List.of("local", "prod")));
	}

	private static EnvValue value(String env, String text) {
		return new EnvValue(env, text, null, text.isEmpty() ? EnvValue.State.EMPTY : EnvValue.State.PRESENT);
	}
}
