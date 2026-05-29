# research/feature2-dataweave-hover.md — DataWeave `p('...')` hover + navigation (Feature 2)

Status: **BUILT (Studio 7.25.0).** Hovering `p('key')` / `Mule::p('key')` in DataWeave shows the same
per-environment property popup as Feature 1's `${key}` XML hover, with **F2 / move-into-popup focus**
(select + copy) and **Ctrl/⌘-click navigation** to the definition. Works in the **Transform Message**
editor, the **Logger `fx` expression field** (and other property-panel expression editors), and the
standalone **`.dwl`** editor.

> This supersedes the earlier "NOT feasible" verdict. That verdict was correct *only for the routes it
> considered* (all Xtext/DFL-internal). The breakthrough was to stop trying to hook the DataWeave editor
> from the inside and instead hook it **from the outside at the SWT level** — editor-agnostic.

## Why the "obvious" (supported, Xtext-internal) routes don't work — still true
Studio's DataWeave editors are a closed Xtext integration (`org.mule.tooling.dfl.ui`, Xtext 2.34, Guice 7):
- The editor's text hover is the Guice-bound `IEObjectHover` from the **per-language DFL injector**
  (`DataWeaveHoverProvider extends DefaultEObjectHoverProvider`). There is **no** text-hover extension
  point an external bundle can contribute to (unlike WTP/SSE's `editorConfiguration`, which Feature 1 uses).
- `org.eclipse.xtext.ui.shared.overridingGuiceModule` overrides only the **shared** UI injector, not a
  language injector → cannot replace the DFL hover. The editor is not the Generic Editor, so
  `org.eclipse.ui.genericeditor.hoverProviders` does not apply. `weaveUiModule` is for data formats.
- Injector-override / OSGi-fragment hacks would couple to internal DFL classes + the grammar and break
  across versions. Rejected.

## The approach that works — editor-agnostic SWT hook
All DataWeave editors — standalone **and** embedded expression fields — are JFace source viewers over an
SWT **`StyledText`**. So Feature 2 never touches Xtext/DFL types at all:

1. **Global `Display.addFilter`** for `SWT.MouseHover` (hover) and `SWT.MouseDown` (Ctrl/⌘-click),
   installed at plugin start, removed at stop (`DwHoverInstaller`, wired in `StudioQolPlugin`).
2. Display filters run **before** the widget's own listeners, so when the pointer is over / Ctrl-clicks a
   `p('key')` token in a DataWeave editor we act and **cancel the event** (`event.type = SWT.None`) to
   suppress the native Xtext hover/hyperlink. Every other widget/token/click is left untouched, so native
   behavior is preserved everywhere else (the native `DataWeaveHoverProvider` returns null when its LSP
   has nothing, so there is nothing to clash with on a plain `p('...')`). *Verified in Studio.*
3. No `StyledText → IDocument/ITextViewer` back-reference exists (JFace keeps none; `XtextDocumentUtil`
   has no widget path), so identification + text reading use the `StyledText` directly:
   `getOffsetAtPoint`, `getLineAtOffset`, `getOffsetAtLine`, `getLine`, `getLocationAtOffset`. Filter
   mouse coords are widget-relative. *Verified.*

## DataWeave-widget identification — the one Studio-internal coupling (class-NAME strings only)
`DataWeaveWidgetMatcher` walks the `StyledText`'s SWT parent chain to the `Shell` and matches package
prefixes (cached per widget). Two categories — **the second was the key finding**:
- **DataWeave-specific editor composites** (these *are* widgets in the chain):
  `org.mule.tooling.dfl.ui` (standalone `DFLEditor`), `org.mule.tooling.ui.dfl.contribution`
  (Transform Message `…editor.script.DataWeaveEditor`).
- **Mule property-panel hosts.** An embedded expression editor (Logger `fx`, etc.) is an Xtext
  **viewer**, not a widget, so it leaves **no DataWeave-specific class in the widget parent chain** — the
  chain is plain SWT `Composite`s up to the property page. The reliable markers (confirmed from a live
  ancestry dump) are `org.mule.tooling.ui.modules.core.widgets.meta.propertypage`
  (`PageWithControlsProvider`) and `org.mule.tooling.ui.widgets.tab` (`VerticalPropertiesTabFolder`).
  Safe because the filter only fires on a `StyledText`, the expression editor is the only `StyledText` in
  those panels, and the token scan further gates on a `p('...')` call.

If MuleSoft renames these packages, `DataWeaveWidgetMatcher.HOST_PREFIXES` is the single place to update
(the same "re-verify after a Studio update" caveat as Feature 1's install).

## Reused Feature 1 infrastructure (no duplication)
- `PropertyHoverText.render(key, false, …, LabelStyle.DATAWEAVE)` → the popup HTML (per-env table, active
  env, secure/runtime-only/unresolved). `LabelStyle.DATAWEAVE` renders the header as `p('key')` (vs
  `${key}` for XML); added additively, Feature 1 unchanged.
- `PropertyIndex` via `StudioQolPlugin.indexManager().get(project)`; `ActiveEnvService` / `ActiveEnvResolver`.
- `PropertyNavigation` (shared `core/`) — `targets(index, key)` + `open(SourceLocation)`; used by both the
  DataWeave click (`DwPropertyNavigator`) and Feature 1's `PropertyHyperlink` / `PropertyHyperlinkDetector`.
- `DwProjectResolver` (`core/`) — resolves the Mule project from the active editor (or the sole Mule
  project), since there is no document to resolve from.

## Popup focus behavior (mirrors the JFace XML hover, hand-rolled in `DwPropertyHoverManager`)
Manual (not via the `ITextHover` framework), so reproduced by hand:
- lightweight non-focusable hover with "Press F2 for focus";
- **moving the pointer into the popup** swaps it in place for a resizable, **selectable/copyable** control
  (affordance drops); the keep-alive zone is the **union of the token box and popup box**, so the
  token→popup move never falls through a gap; leaving that zone dismisses it;
- **F2** focus-locks it (keyboard path) until it loses focus.

## Files
`com.studioqol/src/com/studioqol/feature/dwhover/`: `DwHoverInstaller`, `DwEditorEventFilter`,
`DataWeaveWidgetMatcher`, `PScanner`, `DwPropertyHoverManager`, `DwPropertyNavigator`.
Shared `core/`: `DwProjectResolver`, `PropertyNavigation`. Wired in `StudioQolPlugin.start/stop`
(programmatic — **no `plugin.xml` / `MANIFEST` change**; no `org.mule.*` bundle dependency).

## Known limitations / future
- Multi-line `p(\n'key')` is a non-goal (line-scoped scanner).
- Identification depends on two Mule package-name prefixes (re-verify after major Studio updates).
- If MuleSoft ever exposes a DataWeave hover extension point or moves the editor to LSP4E's generic
  editor, a supported hook (`org.eclipse.ui.genericeditor.hoverProviders`) would become possible.
