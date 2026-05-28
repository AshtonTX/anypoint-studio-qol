# research/feature2-dataweave-hover.md — DataWeave `p('...')` hover (Feature 2 handoff)

Status: **investigated; NOT implemented — no supported integration path found.** This note hands off
everything learned (Studio 7.25.0) so another agent can pick it up.

## Goal
Show the same per-environment property popup (and ideally Ctrl+click) when hovering a property
reference **inside DataWeave**, i.e. `p('db.url')` / `Mule::p('db.url')`, in:
- the standalone **Transform Message** editor (`.dwl`), and
- **expression-mode** fields in the Mule Properties panel (e.g. a logger Message with `fx` on).

Syntax note: DataWeave uses **`p('key')`**, not `${key}`. Feature 1 handles `${...}` in the
Configuration XML editor; Feature 2 is a different editor *and* a different syntax.

## What the DataWeave editor actually is (confirmed)
Evidence: `unzip`/`javap` on `/Applications/AnypointStudio.app/Contents/Eclipse/plugins/org.mule.tooling.dfl.ui_7.25.0.*.jar` and siblings.
- **Xtext editor.** `org.mule.tooling.dfl.ui.DFLEditor` via `org.mule.tooling.dfl.ui.DFLExecutableExtensionFactory`;
  content type `org.mule.tooling.dfl.DFL.contenttype`; language `org.mule.tooling.dfl.DFL`; registered on `org.eclipse.ui.editors`.
- **Embedded editors** for expression fields: `org.mule.tooling.dfl.ui.embedded.editor.DFLEmbeddedEditorBuilder`
  — so expression-mode logger fields ARE real Xtext source viewers (the literal/non-expression field is a plain SWT widget with no hook).
- **Existing hover:** `org.mule.tooling.dfl.ui.hover.DataWeaveHoverProvider extends
  org.eclipse.xtext.ui.editor.hover.html.DefaultEObjectHoverProvider` — a semantic (EObject-based) hover, bound via Guice in `DFLUiModule`.
- **Stacks present:** Xtext / Xtext.ui **2.34.0**, Guice **7.0.0**, Guava 33; DataWeave LSP `org.mule.alsp 4.0.1`;
  DFL grammar EMF model package `org.mule.tooling.dfl.dFL.*`.
- Studio registers `org.eclipse.xtext.ui.shared.overridingGuiceModule` → `DFLOverridingModule` (shared-injector scope),
  and its own extension point `org.mule.tooling.dfl.ui.weaveUiModule` (data-format modules CSV/XML/JSON/Java — **not** hovers).

## Why there is no supported hook
- In Xtext, the editor's text hover is the Guice-bound `IEObjectHover`/`IEObjectHoverProvider` resolved from the
  **per-language injector** (built by `DFLExecutableExtensionFactory` from `DFLRuntimeModule` + `DFLUiModule`).
  `XtextSourceViewerConfiguration.getTextHover()` returns that; there is no standard text-hover extension point an
  external bundle can contribute to (unlike WTP/SSE's `editorConfiguration`, which Feature 1 uses).
- `org.eclipse.xtext.ui.shared.overridingGuiceModule` overrides only the **shared** Xtext UI injector, NOT a
  language's injector — so it cannot replace the DFL hover. (Studio's `DFLOverridingModule` lives there for shared services.)
- The editor is **not** the Eclipse Generic Editor, so `org.eclipse.ui.genericeditor.hoverProviders` does not apply.
  `weaveUiModule` is for data formats, not hovers.
- ⇒ No public/clean way for a third-party plugin to add or wrap the DataWeave hover.

## What is reusable (no change needed)
- `com.studioqol.core.index.PropertyIndex` via `StudioQolPlugin.indexManager().get(project)` → `lookup(key)` returns the
  per-env `PropertyDefinition` (values, states, source locations).
- `com.studioqol.feature.hover.PropertyHoverText.render(key, secure, envProperty, activeEnv, index)` already produces the
  HTML popup. A DataWeave hover only needs to (a) detect the `p('key')` token + extract `key`, (b) call `render(...)`.

## p('...') detection (when a path exists)
- Text scan: `p\(\s*['"]([^'"]+)['"]\s*\)` (also `Mule::p(...)`), or
- Semantic: navigate the DFL EMF AST (`org.mule.tooling.dfl.dFL.*`) for a function call named `p` with a string-literal argument.

## Candidate routes (all unsupported / need a spike)
1. **Wrap the DFL hover via injector override (fragile).** Rebind `IEObjectHoverProvider` to a subclass of
   `DataWeaveHoverProvider` that calls `super` then appends property info for `p('...')`. Blocker: no external hook into
   the DFL *language* injector — would require reflection into Studio's DFL injector or an OSGi **fragment** hosted on
   `org.mule.tooling.dfl.ui`. Couples to internal `org.mule.tooling.dfl.ui` + `org.mule.tooling.dfl` (grammar); breaks across Studio versions.
2. **OSGi fragment** to `org.mule.tooling.dfl.ui` to inject/override bindings. Same fragility; fragments to third-party bundles are brittle and can clash with signing.
3. **Ctrl+click-only** (lesser, maybe attainable): spike whether `org.eclipse.ui.workbench.texteditor.hyperlinkDetectors`
   attaches to the DFL XtextEditor (its hyperlink target id). Xtext hyperlinking is also Guice-based, so uncertain — but a
   hyperlink needs no popup-control injection, so it may be more tractable than a hover.
4. **LSP angle:** if a future Studio routes DataWeave through `org.mule.alsp` + LSP4E **generic editor**, then
   `org.eclipse.ui.genericeditor.hoverProviders` (present: `org.eclipse.ui.genericeditor 1.3.300`) becomes a clean hook. Re-check per Studio version.

## Recommendation
Do not ship an unsupported hack in the QoL plugin (it would break on Studio updates and relies on undocumented internals).
Revisit if MuleSoft exposes a hover/extension point, or if the DataWeave editor migrates to the LSP4E generic editor.
Reproduce findings with: `unzip -p <dfl.ui jar> plugin.xml` and
`javap -p -cp <dfl.ui jar> org.mule.tooling.dfl.ui.hover.DataWeaveHoverProvider`.
