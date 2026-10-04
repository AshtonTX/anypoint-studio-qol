package com.studioqol.feature.usages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.studioqol.StudioQolPlugin;

public class PropertyUsageQueryTest {

	private IProject project;
	private IFile source;

	@Before
	public void createProject() throws Exception {
		project = ResourcesPlugin.getWorkspace().getRoot()
				.getProject("usage-query-test-" + UUID.randomUUID());
		project.create(null);
		project.open(null);
		project.getFile("mule-artifact.json").create(bytes("{}"), true, null);
		for (String path : new String[] { "src", "src/main", "src/main/mule" }) {
			IFolder folder = project.getFolder(path);
			folder.create(true, true, null);
		}
		source = project.getFile("src/main/mule/flow.xml");
		source.create(bytes("<mule>\n<logger message=\"${password} ${secure::password}\"/>\n"
				+ "<logger message=\"#[p('password') ++ p('secure::password') ++ p('secure::')]\"/>\n</mule>"),
				true, null);
	}

	@After
	public void deleteProject() throws Exception {
		if (project != null) {
			StudioQolPlugin.usageManager().invalidate(project);
			StudioQolPlugin.indexManager().invalidate(project);
			project.delete(true, true, null);
		}
	}

	@Test
	public void secureAndOrdinaryReferencesStayInSeparateNamespaces() {
		PropertyUsageQuery ordinary = new PropertyUsageQuery(project, "password", false);
		PropertyUsageQuery secure = new PropertyUsageQuery(project, "password", true);
		assertTrue(ordinary.run(new NullProgressMonitor()).isOK());
		assertTrue(secure.run(new NullProgressMonitor()).isOK());
		assertEquals(2, ((PropertyUsageResult) ordinary.getSearchResult()).getMatchCount());
		PropertyUsageResult result = (PropertyUsageResult) secure.getSearchResult();
		assertEquals(2, result.getMatchCount());
		UsageMatchEntry entry = Arrays.stream(result.getElements()).map(UsageMatchEntry.class::cast)
				.filter(match -> match.line == 2).findFirst().orElseThrow();
		assertEquals(source, entry.file);
		assertEquals(2, entry.line);
		assertEquals("${secure::password}".length(), result.getMatches(entry)[0].getLength());
		UsageMatchEntry dwEntry = Arrays.stream(result.getElements()).map(UsageMatchEntry.class::cast)
				.filter(match -> match.line == 3).findFirst().orElseThrow();
		assertEquals("p('secure::password')".length(), result.getMatches(dwEntry)[0].getLength());
		PropertyUsageQuery empty = new PropertyUsageQuery(project, "", true);
		empty.run(new NullProgressMonitor());
		assertEquals(0, ((PropertyUsageResult) empty.getSearchResult()).getMatchCount());
	}

	@Test
	public void rerunReplacesOldMatchesAfterAnEdit() throws Exception {
		PropertyUsageQuery query = new PropertyUsageQuery(project, "password", false);
		query.run(new NullProgressMonitor());
		assertEquals(2, ((PropertyUsageResult) query.getSearchResult()).getMatchCount());
		source.setContents(bytes("<mule><logger message=\"${password}\"/></mule>"), true, false, null);
		StudioQolPlugin.usageManager().invalidate(project);
		query.run(new NullProgressMonitor());
		assertEquals(1, ((PropertyUsageResult) query.getSearchResult()).getMatchCount());
	}

	@Test
	public void canceledQueryDoesNotBuildAnIndexOrReportMatches() {
		NullProgressMonitor monitor = new NullProgressMonitor();
		monitor.setCanceled(true);
		PropertyUsageQuery query = new PropertyUsageQuery(null, "password", false);
		assertTrue(query.run(monitor).matches(org.eclipse.core.runtime.IStatus.CANCEL));
		assertEquals(0, ((PropertyUsageResult) query.getSearchResult()).getMatchCount());
	}

	private static ByteArrayInputStream bytes(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}
}
