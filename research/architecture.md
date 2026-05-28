# StudioQOL — Architecture Research (Feature-Extensible Plugin)

**Target verified against the live install:** Anypoint Studio **7.25.0** at
`/Applications/AnypointStudio.app/Contents/Eclipse`, running **Eclipse Platform
`org.eclipse.platform_4.31.100.v20240229-0520`**, OSGi `org.eclipse.osgi_3.19.0`,
runtime `org.eclipse.core.runtime_3.31.0`, bundled **JDK 17.0.12** (`org.mule.tooling.jdk.*_1.4.1`).
All extension-point ids and bundle versions below were read directly from the jars in
`Contents/Eclipse/plugins` (531 bundles), not just from docs.

Scope: only **Feature 1 (Property Value Hover + Ctrl+click + env value-comparison popup)** is
built now; this document fixes the layout and the seams so features #2..#N drop in cheaply.
Two pieces are owned by other workstreams and are *referenced, not re-derived* here:
- **Hover/editor-integration workstream** — owns the `ITextHover`/`AbstractHoverProcessor`
  implementation and the SSE wiring details.
- **Property-index workstream** — owns the internals of the per-project Mule property index
  (parsing `configuration-properties`/`global-property`, watching `envs/*.properties`, per-env
  resolution, source file+line tracking). This doc decides only **where it lives** and **how
  features obtain it**.

---

## 1. Recommended bundle/module layout

### Recommendation: **single bundle**, internal feature packages, behind one feature + one update site.

For a **solo dev targeting Studio**, ship ONE OSGi bundle `com.<org>.studioqol` containing
shared infra + all features as internal packages. Do **not** split into core + per-feature
bundles yet. Rationale:

- **PDE/Tycho friction.** Each extra bundle multiplies MANIFEST.MF, build.properties, version
  bumps, and (under Tycho) a `pom.xml` per module plus a parent reactor. A solo dev gets N× the
  housekeeping for zero runtime benefit at this scale. One bundle = one manifest, one
  `build.properties`, one version to bump.
- **Classloading.** A single bundle = single classloader, so shared infra (the property index,
  the active-env preference accessor) is reachable by every feature with a plain `import` and no
  `Export-Package`/`Import-Package` contract to maintain between your own modules. Splitting
  forces you to export internal packages (`com.<org>.studioqol.core.index`) just to consume them,
  which leaks "internal" API and invites version-range mismatches.
- **Install simplicity.** One feature → one update site → one IU the user installs. Multi-bundle
  still installs as one feature, so the *user* sees no difference — the cost is all on the dev
  side.
- **Versioning.** One bundle version tracks the whole product. Per-feature semantic versioning is
  premature for a QOL tool with a single author.
- **Studio precedent.** Studio's own tooling is *not* maximally split either — e.g.
  `org.mule.tooling.ui` bundles a lot of UI behind one `Bundle-Activator`
  (`org.mule.tooling.ui.MuleUIPlugin`). Coarse bundles are the house style.

**When to split later (the documented seam):** if a feature pulls a heavy/optional dependency
(e.g. a DataWeave parser for `p('...')` hover) or you want users to install features à la carte,
promote that feature's package to its own bundle and add it as a second `<plugin>` in the same
`feature.xml`. Because features are already isolated in packages behind an internal contract
(`§2`), promotion is mechanical: move the package, add a manifest, declare the same extensions.
This mirrors angelozerr's `eclipse-wtp-webresources` split (`.core` + `.ui` bundles under one
`*.feature` + one `*.repository`) — a good template *if/when* the split is justified.

### Concrete project tree (shared infra + Feature 1, seam for Feature 2)

```
studioqol/                                   ← git root (Tycho reactor parent lives here if/when used)
├─ com.<org>.studioqol/                      ← THE plug-in project (PDE)
│  ├─ META-INF/MANIFEST.MF                    ← Bundle-SymbolicName: com.<org>.studioqol;singleton:=true
│  │                                            Bundle-Activator: com.<org>.studioqol.StudioQolPlugin
│  │                                            Bundle-RequiredExecutionEnvironment: JavaSE-17
│  ├─ plugin.xml                              ← ALL extension contributions (hover, hyperlink, prefs, feature reg)
│  ├─ build.properties
│  ├─ schema/
│  │  └─ feature.exsd                         ← internal extension point: com.<org>.studioqol.feature (see §2)
│  ├─ icons/
│  ├─ OSGI-INF/l10n/bundle.properties         ← externalized strings
│  └─ src/com/<org>/studioqol/
│     ├─ StudioQolPlugin.java                 ← Activator: starts index registry, resource listener (§5)
│     ├─ core/                                ← SHARED INFRA (consumed by every feature)
│     │  ├─ index/
│     │  │  ├─ PropertyIndex.java             ← OWNED BY INDEX WORKSTREAM (interface lives here)
│     │  │  ├─ PropertyIndexManager.java      ← per-IProject index cache; singleton accessor (§5)
│     │  │  └─ PropertyEntry.java             ← key → {value-per-env, sourceFile, line}
│     │  ├─ env/
│     │  │  └─ ActiveEnvService.java          ← reads the configurable env-property-name pref (§4)
│     │  ├─ resource/
│     │  │  └─ MuleResourceListener.java      ← IResourceChangeListener feeding the index (§5)
│     │  └─ feature/
│     │     ├─ IStudioQolFeature.java         ← the feature contract (§2)
│     │     └─ FeatureRegistry.java           ← reads com.<org>.studioqol.feature contributions
│     ├─ feature/
│     │  └─ hover/                            ← FEATURE 1
│     │     ├─ PropertyHoverFeature.java      ← implements IStudioQolFeature
│     │     ├─ PropertyValueHoverProcessor.java   ← OWNED BY HOVER WORKSTREAM (extends AbstractHoverProcessor)
│     │     ├─ PropertyHyperlinkDetector.java     ← Ctrl+click → go-to-definition
│     │     └─ EnvComparisonPopup.java            ← env value-comparison popup (part of F1)
│     └─ ui/
│        └─ preferences/
│           └─ StudioQolPreferencePage.java   ← FieldEditor page (§4)
│
├─ com.<org>.studioqol.feature/              ← FEATURE project (p2 feature; lists the bundle)
│  ├─ feature.xml
│  └─ build.properties
│
└─ com.<org>.studioqol.repository/           ← UPDATE SITE / p2 repo project
   ├─ category.xml
   └─ (pom.xml only if Tycho)
```

**Seam for Feature 2** (e.g. quick env switcher): add `feature/envswitch/EnvSwitchFeature.java`
implementing `IStudioQolFeature`, register it in `plugin.xml` via the internal feature point
(`§2`) plus whatever platform extension points it needs from the catalog (`§3`). No manifest, no
feature.xml, no version change. It consumes `PropertyIndexManager` and `ActiveEnvService` by plain
import. That is the whole point of the single-bundle layout.

---

## 2. Independent feature registration

### Compared

| Approach | Friction at this scale | Verdict |
|---|---|---|
| **(a) Custom internal extension point** `com.<org>.studioqol.feature` + `IStudioQolFeature` | Write one `.exsd`; register each feature with ~4 lines of `plugin.xml`; discover via `IExtensionRegistry`; lazy class instantiation; declarative & inspectable in PDE | **Recommended** |
| (b) OSGi Declarative Services (`@Component`) | DS runtime IS present and auto-started in Studio (`org.apache.felix.scr_2.2.10`, start level 2, autostart=true in `bundles.info`); needs `Service-Component`/bnd annotations + DS lifecycle mental model. Studio core uses it (`org.mule.tooling.core`, `org.mule.tooling.branding` ship `OSGI-INF/*.xml`) but it's heavier than needed for in-bundle wiring | Viable, overkill |
| (c) Plain activator wiring / static registry | Zero ceremony but features are hard-coded in the activator → editing shared code to add a feature; defeats "low-friction seam" and is not inspectable | Reject |

DS (b) shines for **cross-bundle** service publication; since everything is in one bundle (`§1`),
its main advantage disappears while its lifecycle overhead remains. The custom extension point (a)
gives the cleanest *declarative* "list of features" with platform-native lazy loading and is the
idiomatic Eclipse pattern — it's exactly how angelozerr's bundles expose pluggable providers
(`webResourcesProviders.exsd` etc.).

### Snippet — `schema/feature.exsd` (essence)

```xml
<extension-point id="feature" name="StudioQOL Feature" schema="schema/feature.exsd"/>
<!-- element 'feature' with:
     id    : string (required, unique)
     name  : string
     class  : java (required) — implements com.<org>.studioqol.core.feature.IStudioQolFeature -->
```

### Snippet — registering Feature 1 in `plugin.xml`

```xml
<extension point="com.<org>.studioqol.feature">
   <feature
         id="com.<org>.studioqol.feature.hover"
         name="Property Value Hover + Ctrl+click"
         class="com.<org>.studioqol.feature.hover.PropertyHoverFeature"/>
</extension>
```

### Snippet — the contract + registry

```java
public interface IStudioQolFeature {
    String id();
    void start(StudioQolContext ctx);   // ctx exposes PropertyIndexManager, ActiveEnvService, etc.
    default void stop() {}
}

public final class FeatureRegistry {
    private static final String XP = "com.<org>.studioqol.feature";
    private final List<IStudioQolFeature> features = new ArrayList<>();

    public void startAll(StudioQolContext ctx) {
        IExtensionPoint ep = Platform.getExtensionRegistry().getExtensionPoint(XP);
        for (IConfigurationElement ce : ep.getConfigurationElements()) {
            try {
                IStudioQolFeature f = (IStudioQolFeature) ce.createExecutableExtension("class");
                f.start(ctx);                 // class is instantiated lazily, only when we start it
                features.add(f);
            } catch (CoreException e) {
                StudioQolPlugin.log(e);       // one bad feature must not kill the rest
            }
        }
    }
    public void stopAll() { features.forEach(IStudioQolFeature::stop); features.clear(); }
}
```

Note: the **editor-facing** parts of a feature (hover, hyperlink) are still registered through the
*platform* extension points in `§3` — the hover engine and hyperlink framework instantiate those
classes directly. The internal `feature` point is for **lifecycle/orchestration** (giving each
feature the shared context, letting the activator start/stop them, and making the feature set
inspectable). The two are complementary: platform points wire UI callbacks; the internal point
wires the feature into our own bootstrap.

---

## 3. Extension-point catalog (backlog item → platform extension point)

All ids below were confirmed present in the Studio 4.31 install (declaring bundle + version in the
"Confirmed in" column). The fully-qualified id = declaring-bundle id + `.` + the `<extension-point
id=...>` shown in that bundle's `plugin.xml`.

| Backlog item | Extension point id | Note | Confirmed in (this install) | Citation |
|---|---|---|---|---|
| Property value hover **(F1)**; later DataWeave `p('...')` hover | `org.eclipse.wst.sse.ui.editorConfiguration` | Contribute `<documentationTextHover class=... target="org.eclipse.wst.xml.XML_DEFAULT">` (or `<provisionalConfiguration type="texthoverdefault" ...>`); class extends `org.eclipse.wst.sse.ui.internal.taginfo.AbstractHoverProcessor`. Owned by hover workstream. | `org.eclipse.wst.sse.ui_1.7.900` declares `editorConfiguration`; `org.eclipse.wst.xml.ui_1.2.701` uses `<documentationTextHover ... target="org.eclipse.wst.xml.XML_DEFAULT">` | [SSE EditorConfiguration design](https://eclipse.dev/webtools/wst/components/sse/designs/EditorConfiguration.html) |
| Ctrl+click "go to property definition" **(F1)** | `org.eclipse.ui.workbench.texteditor.hyperlinkDetectors` | `<hyperlinkDetector class=... targetId="org.eclipse.wst.xml.core.xmlsource" activate="true">`; class implements `org.eclipse.jface.text.hyperlink.IHyperlinkDetector` | `org.eclipse.ui.workbench.texteditor_3.17.300` declares `hyperlinkDetectors` (+ `hyperlinkDetectorTargets`) | [Hyperlink Detectors](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workbench_texteditor_hyperlinkDetectors.html) |
| (supporting) register the hyperlink **target** for Mule XML | `org.eclipse.ui.workbench.texteditor.hyperlinkDetectorTargets` | Only needed if targeting a custom editor/context id; for the SSE XML editor reuse `org.eclipse.wst.xml.core.xmlsource` | `org.eclipse.ui.workbench.texteditor_3.17.300` declares `hyperlinkDetectorTargets` | [Hyperlink Detector Targets](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workbench_texteditor_hyperlinkDetectorTargets.html) |
| Markers for undefined / unused properties | `org.eclipse.core.resources.markers` | Define `com.<org>.studioqol.problem` with super type `org.eclipse.core.resources.problemmarker` (+ `textmarker` for line info), `persistent="true"` | `org.eclipse.core.resources_3.20.100` declares `markers` (and ships `problemmarker`, `textmarker`) | [Resource Markers](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_resources_markers.html) |
| The builder that creates/clears those markers | `org.eclipse.core.resources.builders` | `class` subclasses `IncrementalProjectBuilder`; clears `IMarker.PROBLEM` of our type in `clean()`. Attach via a project nature or `org.eclipse.ui.ide.addBuildPath`-style trigger | `org.eclipse.core.resources_3.20.100` declares `builders` | [Incremental Project Builders](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_resources_builders.html) |
| Quick-fixes for those markers | `org.eclipse.ui.ide.markerResolution` | `<markerResolutionGenerator markerType="com.<org>.studioqol.problem" class=...>` implementing `IMarkerResolutionGenerator2` | `org.eclipse.ui.ide_3.22.100` declares `markerResolution` (+ `markerHelp`, `markerImageProviders`) | [Marker Resolution](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_ide_markerResolution.html) |
| Quick env switcher / quick-open env file / find-all-usages — **command** | `org.eclipse.ui.commands` | Declare a `<command id=... categoryId=...>` | `org.eclipse.ui_3.205.100` declares `commands` | [Commands](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_commands.html) |
| …their **handler** | `org.eclipse.ui.handlers` | `<handler commandId=... class=...>` implementing `IHandler`/`AbstractHandler` | `org.eclipse.ui_3.205.100` declares `handlers` | [Handlers](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_handlers.html) |
| …their **menu/toolbar** placement | `org.eclipse.ui.menus` | `<menuContribution locationURI="popup:org.eclipse.ui.popup.any">` etc. | `org.eclipse.ui_3.205.100` declares `menus` | [Menus](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_menus.html) |
| …their **keybinding** | `org.eclipse.ui.bindings` | `<key commandId=... sequence="M1+M2+E" schemeId="org.eclipse.ui.defaultAcceleratorConfiguration">` | `org.eclipse.ui_3.205.100` declares `bindings` | [Bindings](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_bindings.html) |
| Preferences page (active-env property name, toggles) | `org.eclipse.ui.preferencePages` | `<page class=... id=... name=...>` extending `FieldEditorPreferencePage` (§4) | `org.eclipse.ui_3.205.100` declares `preferencePages` | [Preference Pages](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_preferencePages.html) |
| Per-project override of the env-property-name (optional) | `org.eclipse.ui.propertyPages` | Project-scoped property page writing to `ProjectScope` prefs | `org.eclipse.ui_3.205.100` declares `propertyPages` | [Property Pages](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_propertyPages.html) |
| Scoping hover/hyperlink to Mule config XML only (content-type binding) | `org.eclipse.core.contenttype.contentTypes` (+ SSE's `target`/`contentTypeId`) | Define/derive a content type for Mule config so contributions don't fire on every XML; SSE editors key off `org.eclipse.core.runtime.xml` / `org.eclipse.wst.xml.core.xmlsource` | `org.eclipse.core.runtime_3.31.0` provides `contentTypes`; `org.eclipse.wst.xml.ui` binds `contentTypeId="org.eclipse.core.runtime.xml"` | [Content Types](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_contenttype_contentTypes.html) |
| Eager bootstrap *without* an Activator (alternative to §5) | `org.eclipse.ui.startup` | `<startup class=...>` implementing `IStartup.earlyStartup()` — runs after workbench is up | `org.eclipse.ui_3.205.100` declares `startup` | [Startup](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_startup.html) |

Backlog → catalog mapping summary:
- **env value-comparison popup (F1):** no new extension point — rendered inside the F1 hover
  (`editorConfiguration`) and/or a popup from the hyperlink handler.
- **quick env switcher:** `commands` + `handlers` + `menus` + `bindings` (+ reads `ActiveEnvService`).
- **find all usages of a property:** `commands` + `handlers` + `menus` (+ a Search result view; optional).
- **markers/quick-fixes for undefined/unused:** `markers` + `builders` + `markerResolution`.
- **quick-open env file:** `commands` + `handlers` + `bindings`.
- **DataWeave `p('...')` hover:** `editorConfiguration` again, but `target` for the DataWeave
  content type (DW uses `org.mule.tooling.dfl.*` / `org.mule.alsp`; confirm the DW content-type id
  in that workstream — see Open Questions).

---

## 4. Preferences design

### Recommendation
Store the configurable **active-env property name** (default `env`) plus any toggles in a single,
**bundle-scoped** preference store shared by all features. Use one `org.eclipse.ui.preferencePages`
page (`FieldEditorPreferencePage`) writing to the bundle's store. Reads should go through a thin
`ActiveEnvService` in `core/env` so features never touch the store directly.

- **Default value:** set in `StudioQolPlugin.initializeDefaultPreferences()` (or via
  `DefaultScope`), so `env` is the out-of-box value and the preference page "Restore Defaults" works.
- **Storage:** `AbstractUIPlugin.getPreferenceStore()` returns a `ScopedPreferenceStore` bound to
  `InstanceScope.INSTANCE` + bundle id — i.e. workspace-wide, shared across features. Good default.
- **Per-project override (optional, future):** layer a `ProjectScope` lookup in `ActiveEnvService`
  (search order project → instance → default) and expose it via `org.eclipse.ui.propertyPages`.

### Snippet — the preference page (shared, one per product)

```java
public final class StudioQolPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {
    public static final String PREF_ENV_PROPERTY_NAME = "studioqol.activeEnvPropertyName";

    public StudioQolPreferencePage() { super(GRID); }

    @Override public void init(IWorkbench wb) {
        setPreferenceStore(StudioQolPlugin.getDefault().getPreferenceStore()); // bundle-scoped store
    }
    @Override protected void createFieldEditors() {
        addField(new StringFieldEditor(PREF_ENV_PROPERTY_NAME,
                 "Active-environment property name:", getFieldEditorParent()));
    }
}
```

```xml
<extension point="org.eclipse.ui.preferencePages">
   <page id="com.<org>.studioqol.preferences.main"
         name="StudioQOL"
         class="com.<org>.studioqol.ui.preferences.StudioQolPreferencePage"/>
</extension>
```

### Snippet — reading/writing via the shared service (what features call)

```java
public final class ActiveEnvService {
    public static final String DEFAULT_ENV_PROP = "env";

    // default registered once, in the Activator:
    static void initDefaults(IPreferenceStore s) {
        s.setDefault(StudioQolPreferencePage.PREF_ENV_PROPERTY_NAME, DEFAULT_ENV_PROP);
    }

    public String envPropertyName() {                 // JFace store (simple, fires change events)
        return StudioQolPlugin.getDefault().getPreferenceStore()
                 .getString(StudioQolPreferencePage.PREF_ENV_PROPERTY_NAME);
    }
    public void setEnvPropertyName(String v) {
        StudioQolPlugin.getDefault().getPreferenceStore()
                 .setValue(StudioQolPreferencePage.PREF_ENV_PROPERTY_NAME, v);
    }

    // equivalent low-level access (IEclipsePreferences) if you need IScopeContext / project scope:
    public String envPropertyNameScoped() {
        return InstanceScope.INSTANCE.getNode("com.<org>.studioqol")
                 .get(StudioQolPreferencePage.PREF_ENV_PROPERTY_NAME, DEFAULT_ENV_PROP);
    }
}
```

Use `getPreferenceStore()` (JFace `IPreferenceStore`) for the page and simple reads — it gives you
`IPropertyChangeListener` so the index/hover can react to env-name changes. Drop to
`IEclipsePreferences` (`InstanceScope`/`ProjectScope`/`DefaultScope` nodes) only when you need
explicit scoping or project overrides.

---

## 5. Activator / lifecycle

Use a classic **`AbstractUIPlugin` activator** (`Bundle-Activator:
com.<org>.studioqol.StudioQolPlugin`) — matches Studio's own UI house style
(`org.mule.tooling.ui.MuleUIPlugin`) and is the least-friction path. The activator is the single
owner of shared-infra lifetime; features get the infra through a **static singleton accessor** on
the plugin (plus the `StudioQolContext` passed into `IStudioQolFeature.start`).

What `start(BundleContext)` should do, in order:
1. `super.start(context)` and stash `plugin = this` (the singleton).
2. `ActiveEnvService.initDefaults(getPreferenceStore())` — register the `env` default.
3. Create `PropertyIndexManager` (the per-`IProject` index cache — internals owned by the index
   workstream) and hold it on the plugin.
4. Register `MuleResourceListener` with
   `ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, POST_CHANGE)` so edits to
   Mule config / `envs/*.properties` keep the index fresh.
5. Build a `StudioQolContext` (exposes `PropertyIndexManager`, `ActiveEnvService`) and call
   `FeatureRegistry.startAll(ctx)` (§2).

`stop(BundleContext)` reverses it: `FeatureRegistry.stopAll()`, remove the resource listener,
dispose the index manager, clear the singleton.

```java
public class StudioQolPlugin extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "com.<org>.studioqol";
    private static StudioQolPlugin plugin;

    private PropertyIndexManager indexManager;
    private MuleResourceListener resourceListener;
    private final FeatureRegistry features = new FeatureRegistry();

    @Override public void start(BundleContext ctx) throws Exception {
        super.start(ctx); plugin = this;
        ActiveEnvService.initDefaults(getPreferenceStore());
        indexManager = new PropertyIndexManager(new ActiveEnvService());      // index internals: other workstream
        resourceListener = new MuleResourceListener(indexManager);
        ResourcesPlugin.getWorkspace()
            .addResourceChangeListener(resourceListener, IResourceChangeEvent.POST_CHANGE);
        features.startAll(new StudioQolContext(indexManager, new ActiveEnvService()));
    }
    @Override public void stop(BundleContext ctx) throws Exception {
        features.stopAll();
        if (resourceListener != null)
            ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
        if (indexManager != null) indexManager.dispose();
        plugin = null; super.stop(ctx);
    }

    public static StudioQolPlugin getDefault() { return plugin; }            // singleton accessor
    public PropertyIndexManager getIndexManager() { return indexManager; }   // how non-feature code reaches infra
    public static void log(Throwable t) {
        getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, t.getMessage(), t));
    }
}
```

**Lazy-start caveat:** an OSGi bundle with only extension contributions may not have its activator
run until one of its classes is first needed. Two reliable triggers: (a) the SSE hover / hyperlink
classes get instantiated when the user opens a Mule XML editor, which loads the bundle and runs the
activator; (b) if eager startup is required before any editor opens (e.g. to pre-warm the index),
add an `org.eclipse.ui.startup` `IStartup` (catalog §3) that just touches
`StudioQolPlugin.getDefault()`. Prefer (a) for a QOL tool — pay the cost only when the user is
actually in a Mule file.

How a feature obtains shared infra (three equivalent paths, in order of preference):
1. The `StudioQolContext` handed to `IStudioQolFeature.start(ctx)` — cleanest, testable.
2. `StudioQolPlugin.getDefault().getIndexManager()` — for editor-callback classes (hover,
   hyperlink) that the *platform* instantiates and that therefore never receive the context.
3. (Only if/when split into multiple bundles) publish `PropertyIndexManager` as an OSGi/DS service
   — not needed while single-bundle.

---

## Open questions

1. **DataWeave content-type id.** The `p('...')` hover needs the DW editor's content type / SSE
   `target`. DW tooling is `org.mule.tooling.dfl*` + `org.mule.alsp*` (LSP-based), which may **not**
   be an SSE/WTP editor at all — if it's an LSP/generic editor, the hover path is
   `org.eclipse.ui.genericeditor.*` (present: `org.eclipse.ui.genericeditor_1.3.300`), not
   `editorConfiguration`. Hover workstream to confirm.
2. **Mule config content type.** Is there an existing Studio content type for Mule config XML to
   scope contributions to, or do we define our own derived from `org.eclipse.core.runtime.xml`?
   (Affects whether hover/hyperlink fire on *all* XML.) Index/hover workstream input needed.
3. **Builder attachment.** Markers/quick-fixes need a builder bound to Mule projects — via a Mule
   project nature (does Studio expose one we can extend?) or our own nature. Backlog-time decision.
4. **Tycho vs export-from-IDE.** For a solo dev, "Export → Deployable plug-ins and fragments" +
   manual update-site may beat a full Tycho reactor. Build workstream to decide; layout above
   supports either.
5. **`documentationTextHover` vs `provisionalConfiguration type="texthoverdefault"`.** The XML
   editor uses `documentationTextHover`; whether F1 should *replace* vs *combine with* the stock
   XML hover (combination hover ordering, and the angelozerr `org.eclipse.a.` alpha-prefix trick to
   win hover ordering) is a hover-workstream detail.

## Contradictions with assumptions

1. **"core bundle vs per-feature bundles" framed as the main axis** — for a solo Studio dev the
   evidence (PDE/Tycho per-module overhead, single-classloader simplicity, Studio's own coarse
   bundles) points to **one bundle**, deferring any split. The multi-bundle option is kept only as
   a documented later seam, not the recommendation.
2. **The brief leans toward a custom internal extension point for feature registration** — that IS
   the recommendation, but note DS is *not* unavailable as one might assume for an Eclipse target:
   Felix SCR is present **and auto-started** here, and Studio core bundles use DS. The reason to
   skip DS is "unnecessary for in-bundle wiring," not "unsupported."
3. **Hover via `editorConfiguration` assumes an SSE/WTP editor.** True for Mule **config XML**
   (confirmed: `org.eclipse.wst.xml.ui` drives it). It is likely **false for DataWeave**, so the
   backlog's "DW `p()` hover" probably does NOT use `editorConfiguration` — flagged above.
4. **"hyperlinkDetectors targetId" needs a custom target** — not necessarily; for Mule config XML
   you can reuse the existing SSE target `org.eclipse.wst.xml.core.xmlsource`, so
   `hyperlinkDetectorTargets` is optional, contrary to a strict reading of the brief.

## Citations (URL per claim)

- Studio install facts (Eclipse 4.31.100, bundle versions, extension-point ids, Felix SCR
  auto-start, JDK 17.0.12): read directly from
  `/Applications/AnypointStudio.app/Contents/Eclipse/plugins/*` and `.../configuration/.../bundles.info`
  on this machine (2026-05-28).
- SSE `editorConfiguration` (target attr, `org.eclipse.core.runtime.xml`, sourceViewerConfiguration):
  https://eclipse.dev/webtools/wst/components/sse/designs/EditorConfiguration.html
- SSE component overview: https://eclipse.dev/webtools/wst/components/sse/overview.html
- Hyperlink detectors extension point:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workbench_texteditor_hyperlinkDetectors.html
- Hyperlink detector targets:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workbench_texteditor_hyperlinkDetectorTargets.html
- Resource markers extension point:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_resources_markers.html
- Incremental builders extension point:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_resources_builders.html
- Marker resolution (quick fix):
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_ide_markerResolution.html
- Commands / Handlers / Menus / Bindings / PreferencePages / PropertyPages / Startup (platform
  extension-point index):
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/index.html
- Content types extension point:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_contenttype_contentTypes.html
- AbstractUIPlugin / getPreferenceStore (JFace store):
  https://help.eclipse.org/latest/nftopic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/ui/plugin/AbstractUIPlugin.html
- Scoped preferences (InstanceScope/DefaultScope/ScopedPreferenceStore, search order):
  https://gnu-mcu-eclipse.github.io/developer/eclipse/runtime-preferences/
- IncrementalProjectBuilder (clean() removes IMarker.PROBLEM):
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/resources/IncrementalProjectBuilder.html
- Layout template (core+ui bundle, feature, repository): angelozerr eclipse-wtp-webresources —
  https://github.com/angelozerr/eclipse-wtp-webresources (verified tree: `*.core`, `*.ui`,
  `*.feature`, `*.repository`; `*.ui/plugin.xml` uses `documentationTextHover` +
  `hyperlinkDetectors`).
