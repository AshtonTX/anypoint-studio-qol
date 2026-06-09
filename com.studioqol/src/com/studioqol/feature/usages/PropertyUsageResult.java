package com.studioqol.feature.usages;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorPart;

/**
 * Search result for {@link PropertyUsageQuery}. The file/editor match adapters let the Search view
 * annotate open editors with the match ranges and track edits.
 */
public final class PropertyUsageResult extends AbstractTextSearchResult
		implements IEditorMatchAdapter, IFileMatchAdapter {

	private final PropertyUsageQuery query;

	PropertyUsageResult(PropertyUsageQuery query) {
		this.query = query;
	}

	@Override
	public String getLabel() {
		return "Usages of '" + query.displayKey() + "' — " + getMatchCount() + " in "
				+ query.project().getName();
	}

	@Override
	public String getTooltip() {
		return getLabel();
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return null;
	}

	@Override
	public ISearchQuery getQuery() {
		return query;
	}

	@Override
	public IEditorMatchAdapter getEditorMatchAdapter() {
		return this;
	}

	@Override
	public IFileMatchAdapter getFileMatchAdapter() {
		return this;
	}

	@Override
	public boolean isShownInEditor(Match match, IEditorPart editor) {
		IFile file = editor.getEditorInput().getAdapter(IFile.class);
		return file != null && file.equals(((UsageMatchEntry) match.getElement()).file);
	}

	@Override
	public Match[] computeContainedMatches(AbstractTextSearchResult result, IEditorPart editor) {
		IFile file = editor.getEditorInput().getAdapter(IFile.class);
		return file == null ? new Match[0] : computeContainedMatches(result, file);
	}

	@Override
	public Match[] computeContainedMatches(AbstractTextSearchResult result, IFile file) {
		List<Match> out = new ArrayList<>();
		for (Object element : result.getElements()) {
			if (file.equals(((UsageMatchEntry) element).file)) {
				for (Match match : result.getMatches(element)) {
					out.add(match);
				}
			}
		}
		return out.toArray(new Match[0]);
	}

	@Override
	public IFile getFile(Object element) {
		return ((UsageMatchEntry) element).file;
	}
}
