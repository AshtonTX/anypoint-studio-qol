package com.studioqol.ui.preferences;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.MuleProjects;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.resource.ProjectChangeBus;

/** Workspace settings shared by hovers, validation, launches, and the environment comparison. */
public final class StudioQolPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private Text envProperty;

	public StudioQolPreferencePage() {
		setDescription("Choose the property used to select your Mule environment.");
	}

	@Override
	public void init(IWorkbench workbench) {
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite content = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = layout.marginHeight = 0;
		content.setLayout(layout);
		new Label(content, SWT.NONE).setText("Environment &property:");
		envProperty = new Text(content, SWT.BORDER);
		envProperty.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		envProperty.setText(new ActiveEnvService().envPropertyName());
		envProperty.addModifyListener(event -> validateInput());
		Label help = new Label(content, SWT.WRAP);
		help.setText("For -Denv=local, use env. For -Dmule.env=local, use mule.env.\n"
				+ "Applies to all projects in this workspace. Existing run configurations are unchanged.");
		GridData helpData = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		helpData.widthHint = 420;
		help.setLayoutData(helpData);
		validateInput();
		return content;
	}

	private void validateInput() {
		boolean valid = ActiveEnvService.isValidPropertyName(envProperty.getText().trim());
		setErrorMessage(valid ? null : "Use letters, numbers, dots, underscores, or hyphens; the name cannot be empty.");
		setValid(valid);
	}

	@Override
	protected void performDefaults() {
		envProperty.setText(ActiveEnvService.DEFAULT_ENV_PROP);
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		validateInput();
		if (!isValid()) {
			return false;
		}
		String value = envProperty.getText().trim();
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(ActiveEnvService.NODE);
		String previous = new ActiveEnvService().envPropertyName();
		if (value.equals(previous)) {
			return true;
		}
		preferences.put(ActiveEnvService.PREF_ENV_PROPERTY_NAME, value);
		try {
			preferences.flush();
		} catch (Exception e) {
			preferences.put(ActiveEnvService.PREF_ENV_PROPERTY_NAME, previous);
			StudioQolPlugin.log(e);
			setErrorMessage("The setting could not be saved. See the Error Log for details.");
			return false;
		}
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (MuleProjects.isMuleProject(project)) {
				// Revalidate selector exclusions and refresh any open comparison immediately.
				StudioQolPlugin.changeBus().fire(project, ProjectChangeBus.Kind.INDEX);
			}
		}
		return true;
	}
}
