package com.studioqol.feature.quickfix;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.IMarkerResolution;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.studioqol.core.ResourceText;
import com.studioqol.core.validate.QolMarkers;

public class PropertyQuickFixIntegrationTest {

	private IProject project;
	private IFile file;
	private IMarker marker;

	@Before
	public void createProject() throws Exception {
		project = ResourcesPlugin.getWorkspace().getRoot().getProject("qol-quickfix-" + UUID.randomUUID());
		project.create(null);
		project.open(null);
		for (String path : new String[] { "src", "src/main", "src/main/resources", "src/main/resources/envs" }) {
			IFolder folder = project.getFolder(path);
			folder.create(true, true, null);
		}
		file = project.getFile("src/main/resources/envs/local.properties");
		file.create(new ByteArrayInputStream("existing=original\n".getBytes(StandardCharsets.UTF_8)), true, null);
		file.setCharset("UTF-8", null);
		marker = file.createMarker(QolMarkers.TYPE_MISSING_ENV);
		marker.setAttribute(QolMarkers.ATTR_KEY, "new.key");
		marker.setAttribute(QolMarkers.ATTR_MISSING_ENVS, "local");
	}

	@After
	public void deleteProject() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, null);
		}
	}

	@Test
	public void updatesDirtyEditorWithoutSavingOrLosingUnsavedChanges() throws Exception {
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		manager.connect(file.getFullPath(), LocationKind.IFILE, null);
		try {
			ITextFileBuffer buffer = manager.getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
			buffer.getDocument().set("existing=edited\nunsaved=value\n");
			assertTrue(buffer.isDirty());
			IMarkerResolution[] fixes = new PropertyMarkerResolutionGenerator().getResolutions(marker);
			assertEquals(1, fixes.length);
			fixes[0].run(marker);
			String edited = buffer.getDocument().get();
			assertTrue(edited.startsWith("existing=edited\nunsaved=value\n"));
			assertTrue(edited.endsWith("new.key=\n"));
			assertTrue(buffer.isDirty());
			assertEquals("existing=original\n", ResourceText.read(file));
			fixes[0].run(marker);
			assertEquals(edited, buffer.getDocument().get());
		} finally {
			manager.disconnect(file.getFullPath(), LocationKind.IFILE, null);
		}
	}

	@Test
	public void commitsAClosedFileAndKeepsAStaleFixIdempotent() {
		IMarkerResolution[] fixes = new PropertyMarkerResolutionGenerator().getResolutions(marker);
		assertEquals(1, fixes.length);
		fixes[0].run(marker);
		String edited = ResourceText.read(file);
		assertTrue(edited.startsWith("existing=original\n"));
		assertTrue(edited.endsWith("new.key=\n"));
		fixes[0].run(marker);
		assertEquals(edited, ResourceText.read(file));
	}
}
