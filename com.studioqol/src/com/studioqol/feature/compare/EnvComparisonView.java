package com.studioqol.feature.compare;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.part.ViewPart;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.DwProjectResolver;
import com.studioqol.core.MuleProjects;
import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.env.ActiveEnvResolver;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.index.EnvValue;
import com.studioqol.core.resource.ProjectChangeBus;

/**
 * Project-wide environment comparison: rows = env-file property keys, columns = env files, cells
 * rendered with the hover's semantics ((empty) / ••• secure / missing). The active env column is
 * marked in its header; a toolbar toggle filters to problem rows only. Auto-refreshes via the
 * {@link ProjectChangeBus} (debounced rebuild Job, viewer update on the UI thread).
 */
public final class EnvComparisonView extends ViewPart {

	public static final String ID = "com.studioqol.view.envComparison";

	private static final String MISSING = "✗ missing";

	private ComboViewer projectCombo;
	private TableViewer viewer;
	private IProject project;
	private ComparisonModel model = ComparisonModel.build(null);
	private String activeEnv;
	private boolean problemsOnly;

	/** Rebuilds the model off the UI thread (the index may rebuild), then updates the viewer. */
	private final Job refreshJob = new Job("Studio QoL env comparison refresh") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			IProject p = project;
			ComparisonModel m = ComparisonModel.build(
					p == null ? null : StudioQolPlugin.indexManager().get(p));
			String env = p == null ? null
					: ActiveEnvResolver.resolve(p, new ActiveEnvService().envPropertyName());
			if (viewer != null && !viewer.getControl().isDisposed()) {
				viewer.getControl().getDisplay().asyncExec(() -> setModel(m, env));
			}
			return Status.OK_STATUS;
		}
	};

	private final ProjectChangeBus.Listener busListener = (changed, kind) -> {
		if (changed != null && changed.equals(project)) {
			if (kind == ProjectChangeBus.Kind.INDEX) {
				refreshJob.cancel();
				refreshJob.schedule(300);
			} else {
				getSite().getShell().getDisplay().asyncExec(this::updateActiveEnvHeaders);
			}
		}
	};

	@Override
	public void createPartControl(Composite parent) {
		refreshJob.setSystem(true);
		GridLayoutFactory.fillDefaults().numColumns(2).margins(4, 4).applyTo(parent);

		Label label = new Label(parent, SWT.NONE);
		label.setText("Mule project:");
		projectCombo = new ComboViewer(parent, SWT.READ_ONLY | SWT.DROP_DOWN);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(projectCombo.getControl());
		projectCombo.setContentProvider(ArrayContentProvider.getInstance());
		projectCombo.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((IProject) element).getName();
			}
		});
		projectCombo.addSelectionChangedListener(e -> {
			IProject selected = (IProject) ((IStructuredSelection) e.getSelection()).getFirstElement();
			if (selected != null && !selected.equals(project)) {
				project = selected;
				refreshJob.cancel();
				refreshJob.schedule();
			}
		});

		viewer = new TableViewer(parent,
				SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, true).applyTo(viewer.getControl());
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		viewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer v, Object parentElement, Object element) {
				return !problemsOnly || ((ComparisonModel.Row) element).hasProblem;
			}
		});
		viewer.getTable().addListener(SWT.MouseDoubleClick, e -> openCell(new Point(e.x, e.y)));

		Action toggle = new Action("Problems only", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				problemsOnly = isChecked();
				viewer.refresh();
			}
		};
		toggle.setToolTipText("Show only keys missing in at least one environment");
		getViewSite().getActionBars().getToolBarManager().add(toggle);

		StudioQolPlugin.changeBus().addListener(busListener);
		initProjects();
	}

	private void initProjects() {
		List<IProject> projects = new ArrayList<>();
		for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (p.isAccessible() && MuleProjects.isMuleProject(p)) {
				projects.add(p);
			}
		}
		projectCombo.setInput(projects);
		IProject initial = DwProjectResolver.resolveActiveMuleProject();
		if (initial == null && !projects.isEmpty()) {
			initial = projects.get(0);
		}
		if (initial != null) {
			final IProject sel = initial;
			projectCombo.setSelection(new org.eclipse.jface.viewers.StructuredSelection(sel));
		}
	}

	/** Replaces the model: rebuild env columns when needed, then swap the input. UI thread. */
	private void setModel(ComparisonModel newModel, String newActiveEnv) {
		if (viewer.getControl().isDisposed()) {
			return;
		}
		model = newModel;
		activeEnv = newActiveEnv;
		for (TableColumn column : viewer.getTable().getColumns()) {
			column.dispose();
		}
		createColumn("Property", 180, null);
		for (String env : model.environments) {
			createColumn(headerFor(env), 160, env);
		}
		viewer.setInput(model.rows);
	}

	private void createColumn(String header, int width, String env) {
		TableViewerColumn column = new TableViewerColumn(viewer, SWT.LEFT);
		column.getColumn().setText(header);
		column.getColumn().setWidth(width);
		column.getColumn().setData(env);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				ComparisonModel.Row row = (ComparisonModel.Row) element;
				if (env == null) {
					return row.key;
				}
				EnvValue value = row.byEnv.get(env);
				if (value == null) {
					return MISSING;
				}
				switch (value.state) {
				case SECURE:
					return "•••";
				case EMPTY:
					return "(empty)";
				default:
					return value.rawValue;
				}
			}

			@Override
			public Color getForeground(Object element) {
				ComparisonModel.Row row = (ComparisonModel.Row) element;
				if (env == null) {
					return null;
				}
				EnvValue value = row.byEnv.get(env);
				if (value == null) {
					return viewer.getControl().getDisplay().getSystemColor(SWT.COLOR_RED);
				}
				if (value.state != EnvValue.State.PRESENT) {
					return viewer.getControl().getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
				}
				return null;
			}
		});
	}

	private String headerFor(String env) {
		return env.equals(activeEnv) ? env + " ●" : env;
	}

	private void updateActiveEnvHeaders() {
		if (viewer == null || viewer.getControl().isDisposed() || project == null) {
			return;
		}
		activeEnv = ActiveEnvResolver.resolve(project, new ActiveEnvService().envPropertyName());
		for (TableColumn column : viewer.getTable().getColumns()) {
			String env = (String) column.getData();
			if (env != null) {
				column.setText(headerFor(env));
			}
		}
	}

	/** Double-click: open the key's definition in the clicked env file (or the first env on col 0). */
	private void openCell(Point point) {
		ViewerCell cell = viewer.getCell(point);
		if (cell == null) {
			return;
		}
		ComparisonModel.Row row = (ComparisonModel.Row) cell.getElement();
		String env = (String) viewer.getTable().getColumn(cell.getColumnIndex()).getData();
		EnvValue value = env != null ? row.byEnv.get(env) : firstValue(row);
		if (value != null && value.location != null) {
			PropertyNavigation.open(value.location);
		}
	}

	private EnvValue firstValue(ComparisonModel.Row row) {
		for (String env : model.environments) {
			EnvValue value = row.byEnv.get(env);
			if (value != null && value.location != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	public void setFocus() {
		if (viewer != null) {
			viewer.getControl().setFocus();
		}
	}

	@Override
	public void dispose() {
		StudioQolPlugin.changeBus().removeListener(busListener);
		refreshJob.cancel();
		super.dispose();
	}
}
