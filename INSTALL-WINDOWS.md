# Installing Anypoint Studio QoL on Windows 11 (work laptop)

This guide installs the **Anypoint Studio QoL** plugin into **Anypoint Studio 7.25** on a
Windows 11 machine — written for a managed/corporate laptop where you may not have admin
rights, may be behind a proxy, and where SmartScreen/antivirus is active.

There are two ways in. Pick one:

- **Path A — Install a prebuilt update site (recommended).** Someone gives you the
  `repository` folder (or a zip of it); you just point Studio at it. No JDK, no Maven, no
  build. Start here unless you specifically need to build from source.
- **Path B — Build from source, then install.** You compile the plugin yourself with
  Maven + JDK 17. Needed only if you don't have a prebuilt site, or you changed the code.

---

## Before you start: where is Studio, and can you write to it?

Installing an Eclipse plugin **writes files inside the Studio installation folder**. On a
work laptop this is the single biggest thing that goes wrong, so check it first.

1. Find your Studio install folder. Common locations:
   - `C:\Users\<you>\AnypointStudio\` — extracted to your user profile (**writable, ideal**).
   - `C:\Program Files\AnypointStudio\` or `C:\AnypointStudio\` — **needs admin** to write into.
   - If unsure: right-click the Studio shortcut → **Open file location**.
2. Inside that folder you should see `AnypointStudio.exe` and a `plugins\` subfolder. That
   confirms it's the Eclipse home.
3. **Writability test:** try creating a new text file inside the Studio folder in File
   Explorer. If Windows says you need administrator permission, the install will fail unless
   you launch Studio elevated (see [Troubleshooting](#troubleshooting)). If it's under your
   own user profile, you're fine.

> **Tip for managed laptops:** if Studio lives under `C:\Program Files`, the cleanest fix is
> to have IT (or you, if allowed) re-extract the Anypoint Studio zip into
> `C:\Users\<you>\AnypointStudio` so plugin installs don't need elevation and survive without
> fighting folder permissions.

---

## Path A — Install a prebuilt update site (recommended)

You need the **`repository`** folder produced by a build. It contains `artifacts.jar`,
`content.jar`, a `features\` folder, and a `plugins\` folder. If you were given a `.zip`,
extract it somewhere simple first, e.g. `C:\Users\<you>\Downloads\studioqol-repository\`.

> Keep the folder path **short and without spaces/OneDrive** if you can. Paths under
> `C:\Users\<you>\OneDrive\...` sometimes confuse Eclipse's local-file picker because OneDrive
> may report files as "online-only." A plain `Downloads` or `C:\dev\` path avoids this.

1. Open **Anypoint Studio**.
2. **Help → Install New Software…**
3. Click **Add…** (top right) → in the dialog click **Local…**
4. Browse to your extracted `repository` folder and select it (select the folder that
   directly contains `content.jar` / `artifacts.jar`). Give it a name like `Studio QoL`,
   click **Add**.
5. In the list below, **uncheck** *Group items by category* (the feature isn't categorized),
   then tick **Anypoint Studio QoL**.
6. **Next → Next**, accept the license → **Finish**.
7. You'll get an **unsigned content** security warning — this is expected (the plugin isn't
   code-signed). Click **Install anyway**.
8. When prompted, **restart Anypoint Studio**.

Skip to [Verify the install](#verify-the-install).

---

## Path B — Build from source on Windows, then install

Do this if you don't have a prebuilt `repository` folder. The first build downloads Tycho and
the Eclipse target platform from the internet, so you need network access (and proxy config if
your company requires one — see [Troubleshooting](#troubleshooting)).

### B1. Install the build tools (no admin needed)

You can install both without admin rights:

- **JDK 17** — e.g. [Eclipse Temurin 17 (.zip)](https://adoptium.net/temurin/releases/?version=17).
  Download the **Windows x64 `.zip`** and extract to e.g. `C:\Users\<you>\jdk-17`. (The `.msi`
  works too but may prompt for admin.)
- **Maven 3.9+** — download the [binary `.zip`](https://maven.apache.org/download.cgi), extract
  to e.g. `C:\Users\<you>\apache-maven`.

> If your laptop already has a JDK 17 and Maven via the company software portal, use those —
> just confirm the versions below.

### B2. Open PowerShell and point it at JDK 17

Open **Windows Terminal** or **PowerShell** (no admin needed) and set the environment for this
session:

```powershell
$env:JAVA_HOME = "C:\Users\<you>\jdk-17"           # folder that contains \bin\java.exe
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\<you>\apache-maven\bin;$env:PATH"

java -version      # must report 17.x
mvn -version       # must report Maven 3.9+ and Java 17
```

### B3. Get the source and switch it to Windows

The repo's target platform and `pom.xml` are set for macOS by default. **For a Windows build
you must change two `<environment>` blocks to Windows** — otherwise Tycho resolves the wrong
SWT fragment.

In the repo root, edit **`studio-7.25.target`** — change the `<environment>` near the bottom to:

```xml
  <environment>
    <os>win32</os>
    <ws>win32</ws>
    <arch>x86_64</arch>
  </environment>
```

And edit **`pom.xml`** — change the `<environment>` inside `<environments>` to match:

```xml
            <environment>
              <os>win32</os>
              <ws>win32</ws>
              <arch>x86_64</arch>
            </environment>
```

> On an ARM-based Windows laptop (Snapdragon/Surface), Studio runs via x64 emulation, so
> `x86_64` is still correct here.

### B4. Build

From the repository root (the folder with `pom.xml`):

```powershell
mvn -f pom.xml clean verify
```

On success the update site is at:

```
com.studioqol.repository\target\repository\
```

### B5. Install it

Follow [Path A](#path-a--install-a-prebuilt-update-site-recommended) from step 1, pointing
**Local…** at `com.studioqol.repository\target\repository`.

> Don't commit the Windows edits from B3 back to the shared repo unless the team wants the
> default flipped — they only matter for building locally. The produced plugin itself is
> OS-independent.

---

## Verify the install

1. Open a Mule project that follows the env convention (`src/main/resources/envs/*.properties`
   selected via `<configuration-properties file="envs/${env}.properties"/>`).
2. Open a Mule config file and switch to the **Configuration XML** tab.
3. **Hover a `${someKey}`** placeholder — you should see a popup listing the value in every env
   file with `file:line`, and the active-env row highlighted.
4. **Ctrl+click** a `${key}` → jumps to its definition.
5. Check the main toolbar for the **Env** pulldown, and **Window → Show View → Other… →
   Studio QoL → Env Comparison** for the comparison table.

> **Windows keys:** everything is **Ctrl** (not ⌘). Find Property Usages is **Ctrl+Shift+8**;
> quick-open env file is **Ctrl+Shift+9**; quick fixes are **Ctrl+1** on a Problems marker.
> These two `Ctrl+Shift+8/9` bindings are suggestions and may be unbound or clash — set them
> under **Window → Preferences → General → Keys** if they don't fire.

---

## Working behind ZScaler (or any corporate TLS interception)

ZScaler decrypts and re-signs HTTPS traffic with a **ZScaler root CA**. Windows already trusts
that CA, but a freshly-downloaded JDK does **not** — which is why Java tools throw
`PKIX path building failed`. There are two situations:

- **Installing the plugin (Path A): nothing to do.** The *Local…* install reads the
  `repository` folder from disk and makes no HTTPS calls, so ZScaler never touches it. This is
  the simplest way around the whole problem — get the prebuilt folder and install from disk.
- **Building from source (Path B):** Maven fetches over HTTPS, so the JDK must trust the
  ZScaler CA. Do **one** of the following (in order of preference):

  1. **Use a JDK your company already provisioned.** A JDK from your software portal usually
     has the ZScaler CA baked into its `cacerts` already — point `JAVA_HOME` at that one and
     the build just works. Ask IT if unsure.
  2. **Import the ZScaler root CA into your JDK's truststore.** Export the cert from Windows,
     then add it to the JDK that `JAVA_HOME` points at. In PowerShell:

     ```powershell
     # 1) Export the ZScaler root CA from the Windows trust store (no admin needed to read it)
     $cert = Get-ChildItem Cert:\LocalMachine\Root |
             Where-Object { $_.Subject -like "*Zscaler*" } | Select-Object -First 1
     Export-Certificate -Cert $cert -FilePath "$env:USERPROFILE\zscaler-root.cer" | Out-Null

     # 2) Import it into the JDK 17 that JAVA_HOME points at (default cacerts password: changeit)
     & "$env:JAVA_HOME\bin\keytool.exe" -importcert -noprompt -trustcacerts `
        -alias zscaler-root -file "$env:USERPROFILE\zscaler-root.cer" `
        -keystore "$env:JAVA_HOME\lib\security\cacerts" -storepass changeit
     ```

     Then re-run the build. Notes: keep `JAVA_HOME` pointed at the **same** JDK you imported
     into (Maven uses it); if `keytool` says it can't write `cacerts`, your JDK is in a
     read-only location — use a JDK extracted under your user profile (per
     [B1](#b1-install-the-build-tools-no-admin-needed)); and if the company uses an
     **intermediate** ZScaler cert too, importing the root is normally enough, but you can
     repeat step 2 for each `*Zscaler*` cert with a distinct `-alias`.

> If IT hands you the CA as a `.pem`/`.cer` file directly, skip the export and use that file in
> step 2. Don't disable certificate validation to "fix" this — it defeats the control ZScaler
> exists to provide and isn't necessary once the CA is trusted.

---

## Troubleshooting

**"Unable to read repository at https://github.com/…/content.xml" — and/or "PKIX path
building failed: unable to find valid certification path to requested target."**
You're trying to install from a **GitHub URL**. Don't — there is **no hosted update site**
for this plugin. The GitHub page is source code, not an Eclipse p2 repository (it has no
`content.jar`/`artifacts.jar`), so that URL fails regardless of certificates. Use the
**local `repository` folder** instead: *Help → Install New Software → Add… → **Local…*** and
point it at the built `com.studioqol\repository\target\repository` folder ([Path A](#path-a--install-a-prebuilt-update-site-recommended)).
That installs from disk and never touches the network, so the cert error disappears too.

The **PKIX** part on its own means Studio's JDK doesn't trust the TLS certificate it was
handed — on a managed laptop that's corporate HTTPS interception (a proxy re-signing traffic
with a company root CA that isn't in the truststore). It's irrelevant for a local-folder
install. It only matters if you ever host a real p2 site behind the company proxy, in which
case have IT import the corporate root CA into the cacerts of the JDK that Studio runs on.

**"Could not write to … / permission denied" during install.**
Studio is installed somewhere you can't write (usually `C:\Program Files`). Either:
close Studio, then right-click `AnypointStudio.exe` → **Run as administrator** and redo the
install; *or* re-extract Studio into `C:\Users\<you>\AnypointStudio` (preferred — see the
top section). After a write-protected install fails, Studio sometimes needs a restart to clear
a half-applied state.

**The plugin disappears after a Studio update.**
Studio auto-updates overwrite the install folder, removing the plugin. Just **re-run the
install** (Path A) after any Studio update.

**First Maven build can't download (timeouts / `Connection refused` / `UnknownHost`).**
You're likely behind a corporate proxy. Create `C:\Users\<you>\.m2\settings.xml` with your
proxy, e.g.:

```xml
<settings>
  <proxies>
    <proxy>
      <id>corp</id><active>true</active><protocol>http</protocol>
      <host>proxy.yourcompany.com</host><port>8080</port>
      <nonProxyHosts>localhost|127.0.0.1</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
```

The build also fetches from `https://download.eclipse.org` — if your proxy does TLS
inspection (ZScaler and similar), you'll also hit `PKIX path building failed`; fix it per
[Working behind ZScaler](#working-behind-zscaler-or-any-corporate-tls-interception).

**SmartScreen / antivirus blocks `mvn` or the JDK.**
Use the `.zip` distributions (not installers) and extract under your user profile — that
usually avoids SmartScreen prompts. If Defender quarantines a downloaded jar during the build,
allow it and re-run `mvn ... clean verify`.

**Hover doesn't appear.**
Confirm the project is a Mule project, you're on the **Configuration XML** tab (not the visual
canvas), and the key exists in `envs\*.properties`. Then check **Help → About → Installation
Details → Installed Software** lists **Anypoint Studio QoL** — if not, the install didn't take
(see the permission item above).

**`mvn` says "JAVA_HOME is set to a JDK that is not 17."**
You picked up a different JDK on PATH. Re-run the two `$env:` lines from B2 in a fresh
PowerShell window and confirm with `java -version` before building.
