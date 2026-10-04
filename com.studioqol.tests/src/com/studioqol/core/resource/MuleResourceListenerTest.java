package com.studioqol.core.resource;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.usage.UsageIndex;

public class MuleResourceListenerTest {

	private IProject project;
	private IFile properties;
	private IFile script;
	private MuleResourceListener listener;

	@Before
	public void createProject() throws Exception {
		listener = new MuleResourceListener();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
		project = ResourcesPlugin.getWorkspace().getRoot()
				.getProject("qol-resource-events-" + UUID.randomUUID());
		project.create(null);
		project.open(null);
		project.getFile("mule-artifact.json").create(bytes("{}"), true, null);
		for (String path : new String[] { "src", "src/main", "src/main/resources", "src/main/resources/envs" }) {
			IFolder folder = project.getFolder(path);
			folder.create(true, true, null);
		}
		properties = project.getFile("src/main/resources/envs/local.properties");
		properties.create(bytes("host=before\n"), true, null);
		properties.setCharset("UTF-8", null);
		script = project.getFile("src/main/resources/transform.dwl");
		script.create(bytes("p('before')"), true, null);
		script.setCharset("UTF-8", null);
	}

	@After
	public void deleteProject() throws Exception {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
		if (project != null && project.exists()) {
			StudioQolPlugin.indexManager().invalidate(project);
			StudioQolPlugin.usageManager().invalidate(project);
			project.delete(true, true, null);
		}
	}

	@Test
	public void markerOnlyChangesKeepBothCachedSnapshots() throws Exception {
		PropertyIndex definitions = StudioQolPlugin.indexManager().get(project);
		UsageIndex usages = StudioQolPlugin.usageManager().get(project);
		assertNotNull(definitions.lookup("host"));
		assertEquals(1, usages.totalCount());
		for (IFile file : new IFile[] { properties, script }) {
			IMarker marker = file.createMarker(IMarker.PROBLEM);
			marker.setAttribute(IMarker.MESSAGE, "A marker-only edit");
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
			assertSame(definitions, StudioQolPlugin.indexManager().get(project));
			assertSame(usages, StudioQolPlugin.usageManager().get(project));
			marker.delete();
			assertSame(definitions, StudioQolPlugin.indexManager().get(project));
			assertSame(usages, StudioQolPlugin.usageManager().get(project));
		}
	}

	@Test
	public void reopenRebuildsSnapshotsAfterExternalChangesWhileClosed() throws Exception {
		PropertyIndex definitions = StudioQolPlugin.indexManager().get(project);
		UsageIndex usages = StudioQolPlugin.usageManager().get(project);
		assertEquals("before", definitions.lookup("host").valuesByEnv.get("local").rawValue);
		assertEquals(1, usages.usagesOf("before").size());
		Path propertiesPath = properties.getLocation().toFile().toPath();
		Path scriptPath = script.getLocation().toFile().toPath();
		project.close(null);
		Files.writeString(propertiesPath, "host=after\n", StandardCharsets.UTF_8);
		Files.writeString(scriptPath, "p('after')", StandardCharsets.UTF_8);
		project.open(null);
		PropertyIndex reopenedDefinitions = StudioQolPlugin.indexManager().get(project);
		UsageIndex reopenedUsages = StudioQolPlugin.usageManager().get(project);
		assertNotSame(definitions, reopenedDefinitions);
		assertNotSame(usages, reopenedUsages);
		assertEquals("after", reopenedDefinitions.lookup("host").valuesByEnv.get("local").rawValue);
		assertTrue(reopenedUsages.usagesOf("before").isEmpty());
		assertEquals(1, reopenedUsages.usagesOf("after").size());
	}

	private static ByteArrayInputStream bytes(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}
}
