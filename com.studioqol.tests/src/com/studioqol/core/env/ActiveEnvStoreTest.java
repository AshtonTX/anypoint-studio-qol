package com.studioqol.core.env;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.UUID;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Test;

public class ActiveEnvStoreTest {

	private final String projectName = "preferences-test-" + UUID.randomUUID();
	private final IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(ActiveEnvService.NODE);

	@After
	public void cleanPreferences() throws Exception {
		for (String key : preferences.keys()) {
			if (key.endsWith(projectName)) {
				preferences.remove(key);
			}
		}
		preferences.flush();
	}

	@Test
	public void launchedEnvironmentIsIsolatedBySelectorAndPreservesLegacyDefault() {
		ActiveEnvStore.set(projectName, "local");
		ActiveEnvStore.set(projectName, "mule.env", "prod");
		assertEquals("local", ActiveEnvStore.get(projectName));
		assertEquals("local", ActiveEnvStore.get(projectName, "env"));
		assertEquals("prod", ActiveEnvStore.get(projectName, "mule.env"));
		assertNull(ActiveEnvStore.get(projectName, "other.selector"));
		assertNull(ActiveEnvStore.get("unrelated-" + projectName, "mule.env"));
	}

	@Test
	public void clearingAManualOverridePreservesTheRememberedLaunch() {
		ActiveEnvStore.set(projectName, "prod");
		ActiveEnvStore.setManual(projectName, "test");
		assertEquals("test", ActiveEnvStore.getManual(projectName));
		ActiveEnvStore.clearManual(projectName);
		assertNull(ActiveEnvStore.getManual(projectName));
		assertEquals("prod", ActiveEnvStore.get(projectName));
	}
}
