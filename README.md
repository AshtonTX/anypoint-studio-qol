# Anypoint Studio QoL

A quality-of-life Eclipse plugin for **Anypoint Studio 7.25** (Eclipse 4.31). Features:
**Property Value Hover** in the Mule **Configuration XML** editor, the same hover (plus Ctrl/⌘+click
navigation) for **DataWeave** `p('...')` references, **Find Property Usages**, **property validation
markers with quick fixes**, a **quick env switcher**, **quick-open env file**, and a project-wide
**Env Comparison** view.

## What it does
Hovering a `${propertyName}` placeholder in the Configuration XML tab shows a popup with:
- the property's value **in every environment** (`local` / `test` / `prod` / `example` …) with each
  value's source `file:line`;
- honest state flags — **secure** (`${secure::…}` / encrypted `![…]`, never decrypted), **runtime-only**
  (`${env}` and other `-D` system props), **unresolved**, and **(empty)** template values;
- the **active environment** row highlighted (detected from the `-Denv` / `-M-Denv` VM arg of the
  run configuration you most recently launched; remembered across Studio restarts).

**Ctrl/⌘+click** a `${…}` placeholder to jump to its definition (the key in each env file, or an inline
`<global-property>` / secure file).

### Expected project convention
- Env files at `src/main/resources/envs/*.properties`, selected via
  `<configuration-properties file="envs/${env}.properties"/>`.
- The same keys across env files with different values; active env chosen at runtime with `-Denv=<env>`
  (`-M-Denv=<env>` in Studio). The selector property name (`env`) is configurable in code.

## DataWeave property hover (`p('...')`)
The same per-environment popup appears when you hover a `p('key')` / `Mule::p('key')` reference inside a
DataWeave editor — the **Transform Message** editor, a **Logger `fx`** (expression-mode) field, or a
standalone **`.dwl`** file. The popup header reads `p('key')`; move the pointer into it (or press **F2**)
to select and copy the text, exactly like the Configuration XML hover. **Ctrl/⌘+click** a `p('key')`
jumps to its definition. (Implemented at the SWT level because Studio's DataWeave editor exposes no hover
extension point — see `research/feature2-dataweave-hover.md`.)

## Find Property Usages
With the caret on a `${key}` in the Configuration XML editor, press **Cmd/Ctrl+Shift+8** (or right-click →
**Find Property Usages**). The standard **Search view** lists every occurrence — `${key}` placeholders in
the config XML, `p('key')` calls in `.dwl` files, and inline DataWeave inside the XML — with `file:line`
entries; double-click jumps to the exact span, and the view's rerun/next-match controls work as usual.

## Validation markers + quick fixes
Mule projects are validated automatically (on startup and ~0.5 s after every relevant save). The
**Problems view** shows:
- **Undefined property** (warning, at each usage) — `${key}` / `p('key')` resolves to nothing.
- **Property missing in env** (warning, at the definition) — a key defined in some env files but absent
  from others (the convention says every key exists everywhere, including `example.properties`).
- **Unused property** (info) — defined in env files but never referenced.

Quick fixes (**Ctrl+1** on the marker, or right-click → Quick Fix): *Add 'key=' to <env>.properties*
appends the key with an **empty** value (values are never copied across environments); unused keys offer
navigation to their definitions. Honest exclusions: the env selector (`${env}`), `secure::` keys when no
secure file is known, and dynamic `${a${b}}` placeholders are never flagged.

## Quick env switcher (highlight only)
The **Env** pulldown in the main toolbar lists the active project's environments — pick one to control
which row the hovers highlight and which column the Env Comparison view marks. **Auto (from launches)**
returns to detection from the most recent `-Denv` launch. The choice persists across restarts and **never
touches run configurations**.

## Quick-open env file
**Cmd/Ctrl+Shift+9** (or the Env pulldown → **Open Environment File…**) opens an `envs/*.properties` file
of the active project — directly when there's only one, otherwise via a filterable list preselecting the
active env's file.

## Env Comparison view
**Window → Show View → Other… → Studio QoL → Env Comparison**: all property keys × all environments in
one table — values, `(empty)`, `•••` for secure, red `✗ missing` cells; the active env column is
dot-marked. Toolbar toggle **Problems only** filters to keys missing somewhere; double-click a cell to
jump to that key in that env file. Refreshes automatically as you edit.

## Build
Requires **JDK 17** and **Maven 3.9+** (internet access on the first build to download Tycho and the
Eclipse target platform). No Anypoint Studio install is needed to build — the target platform resolves
standard Eclipse + WTP bundles from the public Eclipse 2024-03 p2 repository.

```bash
export JAVA_HOME=/path/to/jdk-17        # any JDK 17
mvn -f pom.xml clean verify             # run from the repository root
```

Output: a p2 update site at `com.studioqol.repository/target/repository/`.

> Building on Linux/Windows? Edit the `<environment>` in `studio-7.25.target` and the `<environments>`
> in `pom.xml` to your OS/ws/arch (e.g. `linux`/`gtk`/`x86_64`). The produced plugin is OS-independent.

## Install into Anypoint Studio
1. **Help → Install New Software… → Add… → Local…** and select `com.studioqol.repository/target/repository`.
2. Uncheck *Group items by category*, tick **Anypoint Studio QoL**, then **Next → Finish**.
3. Accept the unsigned-content warning (**Install anyway**) and **restart** Studio.

> Installing writes inside the Studio app bundle, so re-install after a Studio auto-update.

## Use
- Open a Mule config → **Configuration XML** tab → hover a `${key}`.
- **Ctrl/⌘+click** a `${key}` to go to its definition.
- In a **Transform Message** / **Logger `fx`** / `.dwl` editor → hover or **Ctrl/⌘+click** a `p('key')`.
- **Cmd/Ctrl+Shift+8** on a `${key}` → all usages in the Search view.
- **Problems view** → property warnings/infos; **Ctrl+1** for quick fixes.
- Toolbar **Env** pulldown → switch the highlighted environment (or **Auto**).
- **Cmd/Ctrl+Shift+9** → open an env properties file.
- **Window → Show View → Studio QoL → Env Comparison** → all keys × envs.
- For launch-derived env detection: set `-M-Denv=<env>` under **Run → Run Configurations → (your Mule
  app) → Arguments → VM arguments**, then run it once. (Both keybindings are suggestions — rebindable
  under **Preferences → Keys**.)

## Project layout / docs
- `com.studioqol/` — the plug-in (shared `core/` infrastructure + `feature/` packages: `hover`,
  `dwhover`, `usages`, `quickfix`, `envswitch`, `compare`).
- `com.studioqol.feature/`, `com.studioqol.repository/` — p2 feature + update site.
- `CLAUDE.md`, `PLAN.md`, `research/` — design notes and the integration research.
- The DataWeave `p('...')` hover + navigation (Feature 2) and how it hooks the editor at the SWT level —
  see `research/feature2-dataweave-hover.md`.
