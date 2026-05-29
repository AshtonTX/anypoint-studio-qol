# Anypoint Studio QoL

A quality-of-life Eclipse plugin for **Anypoint Studio 7.25** (Eclipse 4.31). Two features:
**Property Value Hover** in the Mule **Configuration XML** editor, and the same hover (plus Ctrl/⌘+click
navigation) for **DataWeave** `p('...')` references.

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
- For the active-env highlight: set `-M-Denv=<env>` under **Run → Run Configurations → (your Mule app) →
  Arguments → VM arguments**, then run it once.

## Project layout / docs
- `com.studioqol/` — the plug-in (shared `core/` index + `feature/hover/`).
- `com.studioqol.feature/`, `com.studioqol.repository/` — p2 feature + update site.
- `CLAUDE.md`, `PLAN.md`, `research/` — design notes and the integration research.
- The DataWeave `p('...')` hover + navigation (Feature 2) and how it hooks the editor at the SWT level —
  see `research/feature2-dataweave-hover.md`.
