package com.studioqol.feature.usages;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;

import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.usage.PropertyUsage;

/**
 * Flat per-line result page for property usage searches. The platform page supplies the chrome
 * (match navigation, counts, rerun); this only renders entries and opens matches.
 */
public final class PropertyUsageResultPage extends AbstractTextSearchViewPage {

	public PropertyUsageResultPage() {
		super(FLAG_LAYOUT_FLAT);
	}

	@Override
	protected void configureTableViewer(TableViewer viewer) {
		viewer.setContentProvider(new ResultContentProvider());
		viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				UsageMatchEntry entry = (UsageMatchEntry) element;
				String origin = entry.origin == PropertyUsage.Origin.DW_CALL ? "  [DataWeave]" : "";
				return entry.file.getName() + ":" + entry.line + "    " + entry.lineText + origin;
			}
		});
		viewer.setComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer v, Object a, Object b) {
				UsageMatchEntry ea = (UsageMatchEntry) a;
				UsageMatchEntry eb = (UsageMatchEntry) b;
				int byFile = ea.file.getFullPath().toString().compareTo(eb.file.getFullPath().toString());
				return byFile != 0 ? byFile : Integer.compare(ea.line, eb.line);
			}
		});
	}

	@Override
	protected void configureTreeViewer(TreeViewer viewer) {
		// FLAT-only page; the tree layout is never offered.
	}

	@Override
	protected void elementsChanged(Object[] objects) {
		getViewer().refresh();
	}

	@Override
	protected void clear() {
		getViewer().refresh();
	}

	@Override
	protected void showMatch(Match match, int offset, int length, boolean activate) {
		PropertyNavigation.openAt(((UsageMatchEntry) match.getElement()).file, offset, length);
	}

	/** Feeds the table straight from the result's element set. */
	private static final class ResultContentProvider implements IStructuredContentProvider {
		private AbstractTextSearchResult result;

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			result = newInput instanceof AbstractTextSearchResult ? (AbstractTextSearchResult) newInput
					: null;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return result == null ? new Object[0] : result.getElements();
		}
	}
}
