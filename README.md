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
  (`${env}` by default), **unresolved**, **(empty)** template values, and explicit **(missing)** environment rows;
- the **active environment** row highlighted (detected from the `-Denv` / `-M-Denv` VM arg of the
  run configuration you most recently launched; remembered across Studio restarts).

**Ctrl/⌘+click** a `${…}` placeholder to jump to its definition (the key in each env file, or an inline
`<global-property>` / secure file).

### Expected project convention
- Env files at `src/main/resources/envs/*.properties`, selected via
  `<configuration-properties file="envs/${env}.properties"/>`.
- The same keys across env files with different values; active env chosen at runtime with `-Denv=<env>`
  (`-M-Denv=<env>` in Studio). Change the selector property name under **Preferences → Studio QoL**
  (for example, `mule.env`); the setting applies to this workspace without editing code.

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
navigation to their definitions. Special characters in keys are escaped, stale fixes do not add
duplicates, and fixes preserve unsaved changes in an open properties editor. Save that editor to
trigger revalidation. Honest exclusions: the env selector (`${env}`), `secure::` keys when no
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
dot-marked. **Filter keys** searches property names without searching values. Toolbar toggles
**Problems only** (missing definitions) and **Differences only** (different values or missing definitions)
can be combined with the search. Secure values stay masked; differences compare their stored encrypted
text, not decrypted values. The footer shows the visible property count.

Double-click a cell to jump to that key in that env file, or press **Enter** on a selected row to open
its active-env definition (falling back to the first available definition). Refreshes automatically as
you edit, keeps column widths when environments are unchanged, and discards stale refreshes after
switching projects.

## Environment settings
Under **Preferences → Studio QoL**, set the environment selector to `env`, `mule.env`, or your custom
property name. Apply refreshes validation and the comparison view. Automatic launch history is stored
separately for each selector, while manual choices remain in effect until you choose **Auto**.
Quoted VM arguments such as `-Denv="local"` are supported; when a value occurs more than once in the
same argument string, the last occurrence wins. New launches refresh the comparison's active column.

## Build
Requires **JDK 17** and internet access for the first build. Use the included **Maven wrapper**,
which pins the Maven version compatible with Tycho; no global Maven or Anypoint Studio installation
is needed. Eclipse + WTP dependencies resolve from the public Eclipse 2024-03 p2 repository.
The target environment is selected from the host OS and architecture automatically.

```bash
export JAVA_HOME=/path/to/jdk-17
./mvnw -B clean verify
```

On Windows PowerShell, set `$env:JAVA_HOME` to your JDK 17 and run `./mvnw.cmd -B clean verify`.
The first invocation downloads Maven; later runs reuse it.

`verify` builds the plugin, runs the headless JUnit regression suite, and produces a p2 update site at
`com.studioqol.repository/target/repository/` plus the installable archive
`com.studioqol.repository/target/com.studioqol.repository-0.1.0-SNAPSHOT.zip`.
Test reports are under `com.studioqol.tests/target/surefire-reports/`.

GitHub Actions runs the same checks on Linux, Windows, and macOS. From a successful run of the
**Build and test** workflow, download an `anypoint-studio-qol-update-site` artifact. Extract the GitHub artifact
ZIP, then install the enclosed p2 ZIP through **Add… → Archive…**. GitHub's outer artifact ZIP is not
itself the p2 archive.

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
- `com.studioqol.tests/` — headless regression tests for parsing, scanning, validation, comparison,
  usage search, quick fixes, environment arguments, and hover rendering.
- `CLAUDE.md`, `PLAN.md`, `research/` — development guidance, historical design notes, and integration research.
- The DataWeave `p('...')` hover + navigation (Feature 2) and how it hooks the editor at the SWT level —
  see `research/feature2-dataweave-hover.md`.

## Supported scope and verification

This plugin targets the repository's `envs/*.properties` convention. It is a static aid, not the Mule
runtime resolver: YAML property files, arbitrary configuration-property locations, multiline DataWeave
calls, and dynamic property names are not resolved. Textual usage scanning may include comments and
quoted examples. Secure/plain properties with the same name still share an index slot, and a file
containing encrypted values is currently classified as a secure source. Secure navigation refuses to
jump to an ordinary definition on a name collision. Validation can still miss an undefined secure
key when an ordinary key has the same name, or count its secure reference as use of the ordinary key.

The automated suite runs without a Studio workbench. Before releasing, smoke-test in **Studio 7.25**:

1. Hover a key present in local but absent in prod: both rows appear, with prod marked missing.
2. Use escaped keys and continued `.properties` values; verify hover text and definition locations.
3. Run an add-key quick fix twice, including with unsaved property edits; verify one empty entry,
   intact existing values, and unchanged unsaved edits until you save.
4. Filter Env Comparison by name, missing definitions, and differences; switch projects while editing
   and verify the table stays on the selected project. Check double-click and Enter navigation.
5. Change the selector to `mule.env`, launch with `-Dmule.env="test"`, then restore defaults and test
   manual/Auto switching. Check the active column updates without reopening the view.
6. Hover and find usages for both `${secure::password}` and `p('secure::password')`; encrypted text
   must stay masked and ordinary `password` usages must stay separate.

The DataWeave widget matcher depends on Studio's internal package names; repeat its hover, F2,
Ctrl/⌘-click, and normal-editor interaction checks after Studio upgrades.
