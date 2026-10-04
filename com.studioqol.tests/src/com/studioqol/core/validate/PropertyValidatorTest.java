package com.studioqol.core.validate;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.junit.Test;

import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.index.SourceLocation;
import com.studioqol.core.usage.PropertyUsage;
import com.studioqol.core.usage.UsageIndex;
import com.studioqol.core.validate.PropertyValidator.Finding;

public class PropertyValidatorTest {

	private static final SourceLocation LOCATION = new SourceLocation(
			(IFile) Proxy.newProxyInstance(IFile.class.getClassLoader(), new Class<?>[] { IFile.class },
					(proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); }),
			3, 20, 7);

	@Test
	public void reportsEveryUndefinedUsageAtItsSource() {
		SourceLocation second = new SourceLocation(LOCATION.file, 8, 60, 7);
		UsageIndex usages = new UsageIndex(Map.of("missing", List.of(
				usage("missing", false, LOCATION), usage("missing", false, second))));
		List<Finding> findings = PropertyValidator.validate(index(Map.of()), usages, "env");
		assertEquals(2, findings.size());
		assertEquals(Finding.Type.UNDEFINED, findings.get(0).type);
		assertSame(LOCATION, findings.get(0).location);
		assertSame(second, findings.get(1).location);
	}

	@Test
	public void skipsTheConfiguredRuntimeEnvironmentSelector() {
		UsageIndex usages = new UsageIndex(Map.of("mule.env", List.of(usage("mule.env", false, LOCATION))));
		assertTrue(PropertyValidator.validate(index(Map.of()), usages, "mule.env").isEmpty());
		assertEquals(1, PropertyValidator.validate(index(Map.of()), usages, "env").size());
	}

	@Test
	public void secureReferencesRequireAKnownSecureSourceBeforeWarning() {
		UsageIndex usages = new UsageIndex(Map.of("password", List.of(usage("password", true, LOCATION))));
		assertTrue(PropertyValidator.validate(index(Map.of()), usages, "env").isEmpty());
		PropertyIndex knownSecureFile = index(Map.of("other", PropertyDefinition.secure("other", LOCATION)));
		List<Finding> findings = PropertyValidator.validate(knownSecureFile, usages, "env");
		assertEquals(1, findings.size());
		assertEquals(Finding.Type.UNDEFINED, findings.get(0).type);
		assertTrue(findings.get(0).secure);
	}

	@Test
	public void missingTemplateAndUnusedDefinitionAreIndependentFindings() {
		PropertyDefinition definition = PropertyDefinition.envFile("db.host", Map.of(
				"local", new EnvValue("local", "localhost", LOCATION, EnvValue.State.PRESENT)));
		List<Finding> findings = PropertyValidator.validate(index(Map.of("db.host", definition)),
				new UsageIndex(Map.of()), "env");
		assertEquals(2, findings.size());
		assertEquals(Finding.Type.MISSING_IN_ENV, findings.get(0).type);
		assertEquals(List.of("prod", "example"), findings.get(0).missingEnvs);
		assertEquals(Finding.Type.UNUSED, findings.get(1).type);
		assertSame(LOCATION, findings.get(0).location);
	}

	@Test
	public void blankTemplateValuesCountAsDefined() {
		PropertyDefinition definition = PropertyDefinition.envFile("db.host", Map.of(
				"local", new EnvValue("local", "localhost", LOCATION, EnvValue.State.PRESENT),
				"prod", new EnvValue("prod", "prod-host", LOCATION, EnvValue.State.PRESENT),
				"example", new EnvValue("example", "", LOCATION, EnvValue.State.EMPTY)));
		UsageIndex usages = new UsageIndex(Map.of("db.host", List.of(usage("db.host", false, LOCATION))));
		assertTrue(PropertyValidator.validate(index(Map.of("db.host", definition)), usages, "env").isEmpty());
	}

	@Test
	public void globalDefinitionsDoNotNeedPerEnvironmentCopies() {
		PropertyIndex properties = index(Map.of("app.name", PropertyDefinition.global("app.name", "demo", LOCATION)));
		assertTrue(PropertyValidator.validate(properties, new UsageIndex(Map.of()), "env").isEmpty());
	}

	private static PropertyIndex index(Map<String, PropertyDefinition> definitions) {
		return new PropertyIndex(definitions, List.of("local", "prod", "example"));
	}

	private static PropertyUsage usage(String key, boolean secure, SourceLocation location) {
		return new PropertyUsage(key, secure, location, PropertyUsage.Origin.XML_PLACEHOLDER);
	}
}
