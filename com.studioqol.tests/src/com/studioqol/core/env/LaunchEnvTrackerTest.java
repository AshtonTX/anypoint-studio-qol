package com.studioqol.core.env;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.resource.ProjectChangeBus;

public class LaunchEnvTrackerTest {

	private IProject project;
	private String previousSelector;
	private final IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(ActiveEnvService.NODE);

	@Before
	public void preparePreferences() {
		project = ResourcesPlugin.getWorkspace().getRoot().getProject("launch-test-" + UUID.randomUUID());
		previousSelector = preferences.get(ActiveEnvService.PREF_ENV_PROPERTY_NAME, null);
		preferences.put(ActiveEnvService.PREF_ENV_PROPERTY_NAME, "env");
	}

	@After
	public void restorePreferences() throws Exception {
		for (String key : preferences.keys()) {
			if (key.endsWith(project.getName())) {
				preferences.remove(key);
			}
		}
		if (previousSelector == null) {
			preferences.remove(ActiveEnvService.PREF_ENV_PROPERTY_NAME);
		} else {
			preferences.put(ActiveEnvService.PREF_ENV_PROPERTY_NAME, previousSelector);
		}
		preferences.flush();
	}

	@Test
	public void olderLaunchChangesCannotReplaceTheLastLaunchedEnvironment() {
		LaunchEnvTracker tracker = new LaunchEnvTracker();
		ILaunch local = launch(new AtomicReference<>(configuration("local")));
		ILaunch prod = launch(new AtomicReference<>(configuration("prod")));
		AtomicInteger changes = new AtomicInteger();
		ProjectChangeBus.Listener listener = (changed, kind) -> {
			if (project.equals(changed) && kind == ProjectChangeBus.Kind.ACTIVE_ENV) {
				changes.incrementAndGet();
			}
		};
		StudioQolPlugin.changeBus().addListener(listener);
		try {
			tracker.launchAdded(local);
			tracker.launchAdded(prod);
			tracker.launchChanged(local);
			assertEquals("prod", ActiveEnvStore.get(project.getName()));
			assertEquals(2, changes.get());
			tracker.launchRemoved(local);
			tracker.launchRemoved(prod);
			assertEquals("prod", ActiveEnvStore.get(project.getName()));
		} finally {
			StudioQolPlugin.changeBus().removeListener(listener);
		}
	}

	@Test
	public void delayedMetadataFromAnOlderLaunchCannotReplaceANewerLaunch() {
		LaunchEnvTracker tracker = new LaunchEnvTracker();
		AtomicReference<ILaunchConfiguration> delayedConfiguration = new AtomicReference<>();
		ILaunch local = launch(delayedConfiguration);
		ILaunch prod = launch(new AtomicReference<>(configuration("prod")));
		tracker.launchAdded(local);
		tracker.launchAdded(prod);
		delayedConfiguration.set(configuration("local"));
		tracker.launchChanged(local);
		assertEquals("prod", ActiveEnvStore.get(project.getName()));
	}

	@Test
	public void launchUsesTheConfiguredSelectorWithoutOverwritingTheDefaultSelector() {
		ActiveEnvStore.set(project.getName(), "env", "local");
		preferences.put(ActiveEnvService.PREF_ENV_PROPERTY_NAME, "mule.env");
		new LaunchEnvTracker().launchAdded(launch(new AtomicReference<>(configuration("prod"))));
		assertEquals("local", ActiveEnvStore.get(project.getName(), "env"));
		assertEquals("prod", ActiveEnvStore.get(project.getName(), "mule.env"));
	}

	@Test
	public void launchOrderingIsIndependentForEachSelector() {
		LaunchEnvTracker tracker = new LaunchEnvTracker();
		ILaunch customSelector = launch(new AtomicReference<>(configurationArguments("-Dmule.env=local")));
		ILaunch defaultSelector = launch(new AtomicReference<>(configurationArguments("-Denv=prod")));
		tracker.launchAdded(customSelector);
		tracker.launchAdded(defaultSelector);
		preferences.put(ActiveEnvService.PREF_ENV_PROPERTY_NAME, "mule.env");
		tracker.launchChanged(customSelector);
		assertEquals("prod", ActiveEnvStore.get(project.getName(), "env"));
		assertEquals("local", ActiveEnvStore.get(project.getName(), "mule.env"));
	}

	private ILaunchConfiguration configuration(String environment) {
		return configurationArguments("-Denv=" + environment + " -Dmule.env=" + environment);
	}

	private ILaunchConfiguration configurationArguments(String vmArguments) {
		return (ILaunchConfiguration) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ILaunchConfiguration.class }, (proxy, method, arguments) -> {
					switch (method.getName()) {
					case "getMappedResources":
						return new IResource[] { project };
					case "getAttributes":
						return Map.of("vmArguments", vmArguments);
					case "toString":
						return "Test launch configuration " + vmArguments;
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	private ILaunch launch(AtomicReference<ILaunchConfiguration> configuration) {
		return (ILaunch) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ILaunch.class },
				(proxy, method, arguments) -> {
					switch (method.getName()) {
					case "getLaunchConfiguration":
						return configuration.get();
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == arguments[0];
					case "toString":
						return "Test launch";
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}
}
