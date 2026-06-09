package com.studioqol.feature.envswitch;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.DwProjectResolver;
import com.studioqol.core.env.ActiveEnvResolver;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.env.ActiveEnvStore;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.resource.ProjectChangeBus;

/**
 * The env switcher's dropdown, recomputed on each open: one radio item per environment of the
 * active editor's Mule project (checkmark on the resolved env), plus "Auto (from launches)" which
 * clears the manual override. Highlight-only by design — never touches run configurations.
 */
public final class EnvSwitcherMenu extends CompoundContributionItem {

	public EnvSwitcherMenu() {
	}

	public EnvSwitcherMenu(String id) {
		super(id);
	}

	@Override
	protected IContributionItem[] getContributionItems() {
		IProject project = DwProjectResolver.resolveActiveMuleProject();
		if (project == null) {
			return new IContributionItem[] { disabled("No Mule project") };
		}
		PropertyIndex index = StudioQolPlugin.indexManager().get(project);
		List<String> envs = index == null ? List.of() : index.environments();
		if (envs.isEmpty()) {
			return new IContributionItem[] { disabled("No envs/*.properties in " + project.getName()) };
		}
		String current = ActiveEnvResolver.resolve(project, new ActiveEnvService().envPropertyName());
		boolean manual = ActiveEnvStore.getManual(project.getName()) != null;

		List<IContributionItem> items = new ArrayList<>();
		items.add(disabled(project.getName()));
		items.add(new Separator());
		for (String env : envs) {
			items.add(item(env, env.equals(current),
					() -> ActiveEnvStore.setManual(project.getName(), env), project));
		}
		items.add(new Separator());
		items.add(item("Auto (from launches)", !manual,
				() -> ActiveEnvStore.clearManual(project.getName()), project));
		items.add(new Separator());
		items.add(openEnvFileItem());
		return items.toArray(new IContributionItem[0]);
	}

	private static IContributionItem openEnvFileItem() {
		CommandContributionItemParameter p = new CommandContributionItemParameter(
				PlatformUI.getWorkbench(), null, "com.studioqol.command.openEnvFile",
				CommandContributionItem.STYLE_PUSH);
		p.label = "Open Environment File…";
		return new CommandContributionItem(p);
	}

	private static IContributionItem item(String label, boolean checked, Runnable apply,
			IProject project) {
		Action action = new Action(label, IAction.AS_RADIO_BUTTON) {
			@Override
			public void run() {
				if (!isChecked()) {
					return; // radio deselection callback
				}
				apply.run();
				StudioQolPlugin.changeBus().fire(project, ProjectChangeBus.Kind.ACTIVE_ENV);
			}
		};
		action.setChecked(checked);
		return new ActionContributionItem(action);
	}

	private static IContributionItem disabled(String label) {
		Action action = new Action(label) {
		};
		action.setEnabled(false);
		return new ActionContributionItem(action);
	}
}
