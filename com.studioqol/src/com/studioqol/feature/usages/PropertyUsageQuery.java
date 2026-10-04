package com.studioqol.feature.usages;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.Match;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.ResourceText;
import com.studioqol.core.usage.PropertyUsage;
import com.studioqol.core.usage.UsageIndex;

/**
 * Finds all usages of one property key in one Mule project — {@code ${key}} placeholders in the
 * config XML plus {@code p('key')} DataWeave calls (inline and in {@code .dwl} files). Runs in the
 * search Job (off the UI thread) and re-fetches the usage index on every run so rerun reflects
 * edits.
 */
public final class PropertyUsageQuery implements ISearchQuery {

	private final IProject project;
	private final String key;
	private final boolean secure;
	private final String display;
	private final PropertyUsageResult result;

	public PropertyUsageQuery(IProject project, String key, boolean secure) {
		this.project = project;
		this.key = key;
		this.secure = secure;
		this.display = secure ? "secure::" + key : key;
		this.result = new PropertyUsageResult(this);
	}

	@Override
	public IStatus run(IProgressMonitor monitor) {
		result.removeAll();
		if (monitor != null && monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		UsageIndex usages = StudioQolPlugin.usageManager().get(project);
		if (usages == null) {
			return Status.OK_STATUS;
		}
		Map<IFile, IDocument> docs = new HashMap<>();
		for (PropertyUsage usage : usages.usagesOf(key)) {
			if (monitor != null && monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (usage.secure != secure) {
				continue;
			}
			IFile file = usage.location.file;
			UsageMatchEntry entry = new UsageMatchEntry(file, usage.location.line,
					lineText(docs, file, usage.location.line), usage.origin);
			result.addMatch(new Match(entry, usage.location.offset, usage.location.length));
		}
		return Status.OK_STATUS;
	}

	/** The trimmed text of a 1-based line, reading each file once per run. */
	private static String lineText(Map<IFile, IDocument> docs, IFile file, int line) {
		IDocument doc = docs.computeIfAbsent(file, f -> {
			String text = ResourceText.read(f);
			return new Document(text == null ? "" : text);
		});
		try {
			IRegion info = doc.getLineInformation(line - 1);
			return doc.get(info.getOffset(), info.getLength()).trim();
		} catch (BadLocationException e) {
			return "";
		}
	}

	/** The key's display form ({@code secure::}-prefixed when invoked on a secure placeholder). */
	public String displayKey() {
		return display;
	}

	public IProject project() {
		return project;
	}

	@Override
	public String getLabel() {
		return "Usages of '" + display + "'";
	}

	@Override
	public boolean canRerun() {
		return true;
	}

	@Override
	public boolean canRunInBackground() {
		return true;
	}

	@Override
	public ISearchResult getSearchResult() {
		return result;
	}
}
