# AGENTS.md

## Build / test environment

`java` and `mvn` are not on `PATH` by default in this sandbox. Use the bundled JDK 25 and the Maven
distribution bundled with the IntelliJ Maven plugin:

```bash
export JAVA_HOME=/root/.jdks/corretto-25.0.4.1
export PATH="$JAVA_HOME/bin:/root/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven-plugin/lib/maven3/bin:$PATH"
```

Run tests (tests are skipped by default by the pom, must override `skipTests`):

```bash
mvn -DskipTests=false test
```

Notes:
- `game.data.chunk.ChunkTest` downloads a `server.jar` per supported Minecraft version (via
  `RegistryLoader`) into `cache/<version>/` on first run to generate block/entity reports; subsequent runs
  are cached and much faster. Needs network access the first time.
- Full baseline run (`mvn -DskipTests=false test`) as of the legacy-version cleanup work: **102 tests, all
  passing** (dominant cost: `ChunkTest`, ~135s, due to server.jar downloads/report generation per version).
- Build a runnable jar: `mvn clean package` (arm64 profile, default) or `mvn clean package -Px86_64`.

## Ongoing work

See `docs/LEGACY_VERSION_REMOVAL_PLAN.md` for the plan to drop support for Minecraft versions older than
26.x and the SOLID-oriented cleanup that goes with it. Update the checklist at the bottom of that file as
phases complete.
