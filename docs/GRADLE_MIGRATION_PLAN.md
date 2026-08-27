# Plan: migracja Maven → Gradle + Compose GUI

Status: **draft do dyskusji**.

## 1. Cel

Migracja całego projektu z Maven na Gradle, zachowując:
- **Backend Java bez zmian** — 345 plików, 102 testy, SPI, per-version modules
- **JavaFX GUI działa równolegle** z nowym Compose GUI (nie usuwamy)
- **Per-arch fat jar** — jak dziś (x86_64, arm64)
- **Wszystkie testy przechodzą** — 102 testy, `ChunkTest` pobiera `server.jar`

Dodatkowo:
- **Compose Desktop** jako nowy GUI (osobny source set, nie moduł)
- **JCEF** dla mapy Leaflet (później, faza izometryczna)

## 2. Struktura po migracji

```
world-downloader-proxy/
├── settings.gradle.kts          ← root, definiuje source sets
├── build.gradle.kts             ← root, wszystkie deps + config
├── gradle.properties            ← wersje Kotlin, Compose, JavaFX
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
│
├── src/main/java/               ← backend + JavaFX GUI (bez zmian)
├── src/main/resources/          ← backend resources (bez zmian)
├── src/test/java/               ← testy (bez zmian)
│
├── src/compose/kotlin/          ← NOWY: Compose GUI (Kotlin)
├── src/compose/resources/       ← NOWY: Compose resources (opcjonalnie)
│
├── docs/
│   ├── ISOMETRIC_MAP_DESIGN.md
│   ├── WET_VERSION_ARCHITECTURE.md
│   └── GRADLE_MIGRATION_PLAN.md  ← ten plik
│
└── pom.xml                       ← USUNIĘTY po migracji (faza 4)
```

### Dlaczego source set, nie osobny moduł?

- **Jeden build, jeden jar** — nie trzeba budować backend jar osobno i linkować
- **Backend i Compose współdzielą classpath** — Compose wywołuje `core.*` bezpośrednio
- **Prostsze** — jeden `build.gradle.kts`, nie dwa
- **Source set `compose`** izoluje Kotlin od Java — nie mieszają się

## 3. Mapowanie pom.xml → build.gradle.kts

### 3.1 Właściwości

| pom.xml | gradle.properties |
|---|---|
| `<java.version>25</java.version>` | `java.version=25` |
| `<javafx.version>23.0.1</javafx.version>` | `javafx.version=23.0.1` |
| `<skipTests>true</skipTests>` | `skipTests=true` (Gradle domyślnie nie pomija) |
| `<project.build.sourceEncoding>UTF-8` | `org.gradle.jvmargs=-Dfile.encoding=UTF-8` |

### 3.2 Repozytoria

```kotlin
// pom.xml: jitpack.io + apache snapshots
// build.gradle.kts:
repositories {
    mavenCentral()
    maven("https://jitpack.io")                    // jo-nbt
    maven("https://repository.apache.org/content/repositories/snapshots/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")  // Compose
    google()                                        // Compose (Android artifacts)
}
```

### 3.3 Zależności — mapowanie 1:1

| pom.xml | build.gradle.kts |
|---|---|
| `nanohttpd:2.3.1` | `implementation("org.nanohttpd:nanohttpd:2.3.1")` |
| `commons-io:2.18.0` | `implementation("commons-io:commons-io:2.18.0")` |
| `commons-lang3:3.18.0` | `implementation("org.apache.commons:commons-lang3:3.18.0")` |
| `gson:2.9.0` | `implementation("com.google.code.gson:gson:2.9.0")` |
| `unirest-java:3.13.8` | `implementation("com.konghq:unirest-java:3.13.8")` |
| `commons-codec:1.18.0` | `implementation("commons-codec:commons-codec:1.18.0")` |
| `slf4j-api:1.7.36` | `implementation("org.slf4j:slf4j-api:1.7.36")` |
| `slf4j-simple:1.7.36` | `implementation("org.slf4j:slf4j-simple:1.7.36")` |
| `jo-nbt:1baae2f49a` (jitpack) | `implementation("com.github.llbit:jo-nbt:1baae2f49a")` |
| `args4j:2.33` | `implementation("args4j:args4j:2.33")` |
| `javafx-fxml:23.0.1` | `implementation("org.openjfx:javafx-fxml:23.0.1")` |
| `javafx-swing:23.0.1` | `implementation("org.openjfx:javafx-swing:23.0.1")` |
| `javafx-controls:23.0.1` | `implementation("org.openjfx:javafx-controls:23.0.1")` |
| `dnsjava:3.6.0` | `implementation("dnsjava:dnsjava:3.6.0")` |
| `junit-jupiter:5.8.2` | `testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")` |
| `assertj-core:3.27.7` | `testImplementation("org.assertj:assertj-core:3.27.7")` |
| `mockito-core:4.5.1` | `testImplementation("org.mockito:mockito-core:4.5.1")` |

### 3.4 JavaFX per-arch (profile → Gradle)

```kotlin
// pom.xml: profile x86_64 / arm64 z javafx-graphics classifier
// build.gradle.kts:
val arch = project.findProperty("arch") ?: "arm64"  // -Parch=x86_64

dependencies {
    if (arch == "x86_64") {
        implementation("org.openjfx:javafx-graphics:23.0.1:win")
        implementation("org.openjfx:javafx-graphics:23.0.1:linux")
        implementation("org.openjfx:javafx-graphics:23.0.1:mac")
    } else {
        implementation("org.openjfx:javafx-graphics:23.0.1:linux-aarch64")
        implementation("org.openjfx:javafx-graphics:23.0.1:mac-aarch64")
    }
}
```

Uruchomienie:
```bash
./gradlew build -Parch=x86_64    # jak mvn package -Px86_64
./gradlew build                  # arm64 (domyślnie)
```

### 3.5 Build plugins — mapowanie

| pom.xml plugin | build.gradle.kts |
|---|---|
| `maven-compiler-plugin` (Java 25) | `kotlin("jvm")` + `java { toolchain }` |
| `maven-shade-plugin` (fat jar) | `Shadow plugin` (`com.github.johnrengelman.shadow`) |
| `maven-surefire-plugin` (skipTests) | `tasks.test { enabled = !skipTests }` |
| `javafx-maven-plugin` | niepotrzebny (JavaFX przez classpath) |
| `jdependency workaround` (Java 25) | niepotrzebny (Shadow nie ma tego buga) |

### 3.6 Resources filtering — `version.txt`

```kotlin
// pom.xml: filtering version.txt z ${project.version}
// build.gradle.kts:
tasks.processResources {
    filesMatching("version.txt") {
        expand("project.version" to project.version)
    }
}
```

### 3.7 SPI — `META-INF/services`

Shade plugin ma `ServicesResourceTransformer`. W Gradle Shadow:
```kotlin
// Shadow automatycznie łączy META-INF/services/*
// nie trzeba dodatkowej konfiguracji
```

### 3.8 Shade filtry

```kotlin
// pom.xml: exclude META-INF/*, module-info.class, InetAddressResolverProvider
// build.gradle.kts:
tasks.shadowJar {
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/services/java.net.spi.InetAddressResolverProvider")
    // zachowaj META-INF/services/core.interfaces.VersionModule (SPI)
    mergeServiceFiles()
}
```

## 4. Compose source set

### 4.1 Konfiguracja

```kotlin
// build.gradle.kts — dodaj source set "compose"
sourceSets {
    create("compose") {
        kotlin.srcDir("src/compose/kotlin")
        resources.srcDir("src/compose/resources")
        compileClasspath += sourceSets.main.get().output +
            configurations.compileClasspath.get()
        runtimeClasspath += sourceSets.main.get().output +
        configurations.runtimeClasspath.get()
    }
}

dependencies {
    "composeImplementation"(compose.desktop.currentOs)
    "composeImplementation"(compose.material3)
    "composeImplementation"("me.friwi:jcefmaven:1.0.0")  // JCEF (później)
}
```

### 4.2 Entry point

```kotlin
// src/compose/kotlin/MainCompose.kt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import core.Launcher

fun main() = application {
    Launcher.initBackend(emptyArray())  // bez JavaFX launch

    Window(
        onCloseRequest = ::exitApplication,
        title = "World Downloader — Compose"
    ) {
        MaterialTheme {
            // tu eksperymentuj z Compose
            Text("Compose GUI działa!")
        }
    }
}
```

### 4.3 Uruchomienie Compose

```kotlin
// build.gradle.kts — task do uruchomienia Compose GUI
tasks.register<JavaExec>("runCompose") {
    group = "application"
    mainClass.set("MainComposeKt")
    classpath = sourceSets["compose"].runtimeClasspath
}
```

```bash
./gradlew runCompose
```

### 4.4 Uruchomienie JavaFX (stary GUI — nadal działa)

```kotlin
// build.gradle.kts — task do uruchomienia JavaFX GUI (stary)
tasks.register<JavaExec>("runJavaFX") {
    group = "application"
    mainClass.set("core.Launcher")
    classpath = sourceSets.main.get().runtimeClasspath
}
```

```bash
./gradlew runJavaFX
```

## 5. Pełny `build.gradle.kts` (szkic)

```kotlin
plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

// Wersje z gradle.properties
val javaVersion = property("java.version") as String
val javafxVersion = property("javafx.version") as String
val skipTests = (property("skipTests") as String).toBoolean()

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repository.apache.org/content/repositories/snapshots/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

// Per-arch (jak pom.xml profile)
val arch = project.findProperty("arch") ?: "arm64"

dependencies {
    // Backend
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("commons-io:commons-io:2.18.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.konghq:unirest-java:3.13.8")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("com.github.llbit:jo-nbt:1baae2f49a")
    implementation("args4j:args4j:2.33")
    implementation("dnsjava:dnsjava:3.6.0")

    // JavaFX — stary GUI (zostaje)
    implementation("org.openjfx:javafx-fxml:$javafxVersion")
    implementation("org.openjfx:javafx-swing:$javafxVersion")
    implementation("org.openjfx:javafx-controls:$javafxVersion")
    if (arch == "x86_64") {
        implementation("org.openjfx:javafx-graphics:$javafxVersion:win")
        implementation("org.openjfx:javafx-graphics:$javafxVersion:linux")
        implementation("org.openjfx:javafx-graphics:$javafxVersion:mac")
    } else {
        implementation("org.openjfx:javafx-graphics:$javafxVersion:linux-aarch64")
        implementation("org.openjfx:javafx-graphics:$javafxVersion:mac-aarch64")
    }

    // Testy
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:4.5.1")
}

// Compose source set
sourceSets {
    create("compose") {
        kotlin.srcDir("src/compose/kotlin")
        resources.srcDir("src/compose/resources")
        compileClasspath += sourceSets.main.get().output +
            configurations.compileClasspath.get()
        runtimeClasspath += sourceSets.main.get().output +
            configurations.runtimeClasspath.get()
    }
}

dependencies {
    "composeImplementation"(compose.desktop.currentOs)
    "composeImplementation"(compose.material3)
    "composeImplementation"("me.friwi:jcefmaven:1.0.0")
}

// Java 25
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain(javaVersion.toInt())
}

// Resources filtering — version.txt
tasks.processResources {
    filesMatching("version.txt") {
        expand("project.version" to project.version)
    }
}

// Testy — skipTests domyślnie (jak pom.xml)
tasks.test {
    useJUnitPlatform()
    enabled = !skipTests
}

// Fat jar — jak maven-shade-plugin
tasks.shadowJar {
    archiveClassifier.set(arch)
    archiveBaseName.set("world-downloader-proxy")
    mergeServiceFiles()  // SPI: META-INF/services/core.interfaces.VersionModule
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/services/java.net.spi.InetAddressResolverProvider")
    manifest {
        attributes("Main-Class" to "core.Launcher")
    }
}

// Uruchom JavaFX GUI (stary)
tasks.register<JavaExec>("runJavaFX") {
    group = "application"
    mainClass.set("core.Launcher")
    classpath = sourceSets.main.get().runtimeClasspath
}

// Uruchom Compose GUI (nowy)
tasks.register<JavaExec>("runCompose") {
    group = "application"
    mainClass.set("MainComposeKt")
    classpath = sourceSets["compose"].runtimeClasspath
}

application {
    mainClass.set("core.Launcher")  // domyślnie JavaFX
}
```

## 6. `gradle.properties`

```properties
java.version=25
javafx.version=23.0.1
skipTests=true
org.gradle.jvmargs=-Dfile.encoding=UTF-8
org.gradle.caching=true
```

## 7. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
    }
}

rootProject.name = "world-downloader-proxy"
```

## 8. Fazy migracji

### Faza 0: Przygotowanie (30 min)

1. Utwórz branch `gradle-migration`
2. Zainstaluj Gradle wrapper (bez Gradle na systemie):
   ```bash
   cd /root/IdeaProjects/world-downloader-proxy
   # użyj istniejącego Gradle lub pobierz jednorazowo
   gradle wrapper --gradle-version 8.11
   ```
3. Utwórz pliki:
   - `settings.gradle.kts`
   - `build.gradle.kts`
   - `gradle.properties`
4. **Nie usuwaj `pom.xml`** — działa równolegle podczas migracji

**Kryterium sukcesu:** `./gradlew tasks` działa, lista tasków się wyświetla.

### Faza 1: Backend na Gradle (2-3 godziny)

1. Skonfiguruj `build.gradle.kts` z wszystkimi deps z `pom.xml`
2. Skonfiguruj Java 25 toolchain
3. Skonfiguruj `processResources` dla `version.txt`
4. Skonfiguruj testy (`useJUnitPlatform()`, `skipTests`)
5. Uruchom `./gradlew compileKotlin` — sprawdź czy Java się kompiluje
6. Uruchom `./gradlew test -PskipTests=false` — **102 testy muszą przejść**

**Kryterium sukcesu:** `./gradlew test -PskipTests=false` = 102 testy passing,
tak jak `mvn -DskipTests=false test`.

### Faza 2: Fat jar na Gradle (1-2 godziny)

1. Dodaj Shadow plugin (`com.github.johnrengelman.shadow`)
2. Skonfiguruj `shadowJar` z:
   - `mergeServiceFiles()` (SPI)
   - exclude `META-INF`, `module-info.class`, `InetAddressResolverProvider`
   - manifest `Main-Class: core.Launcher`
3. Per-arch: `./gradlew shadowJar -Parch=x86_64` i `-Parch=arm64`
4. Porównaj z Maven jar — rozmiar, zawartość, czy działa

**Kryterium sukcesu:** `./gradlew shadowJar -Parch=arm64` produkuje jar który
uruchamia się `java -jar` i pokazuje JavaFX GUI.

### Faza 3: Compose source set (1-2 godziny)

1. Utwórz `src/compose/kotlin/MainCompose.kt`
2. Skonfiguruj source set `compose` w `build.gradle.kts`
3. Dodaj Compose deps (`compose.desktop.currentOs`, `compose.material3`)
4. Modyfikuj `Launcher.java` — dodaj `initBackend()` bez JavaFX launch:
   ```java
   public static void initBackend(String[] args) throws URISyntaxException {
       // to co dziś w main(), ale bez Config.init() (które launchuje JavaFX)
       fixCwd();
       VersionModule module = VersionRegistry.getInstance().getModule(0);
       Config.setVersionModule(module);
       // Config.init(args) — NIE, to launchuje JavaFX
   }
   ```
5. Zarejestruj task `runCompose`
6. Uruchom `./gradlew runCompose`

**Kryterium sukcesu:** `./gradlew runCompose` otwiera okno Compose,
`./gradlew runJavaFX` otwiera okno JavaFX. **Oba działają.**

### Faza 4: Usunięcie Maven (30 min)

1. Usuń `pom.xml`
2. Zaktualizuj `AGENTS.md`:
   ```markdown
   ## Build / test environment

   Używaj Gradle:
   ```bash
   export JAVA_HOME=/root/.jdks/corretto-25.0.4.1
   ./gradlew test -PskipTests=false      # 102 testy
   ./gradlew shadowJar -Parch=arm64      # fat jar
   ./gradlew runJavaFX                   # stary GUI
   ./gradlew runCompose                  # nowy GUI
   ```
   ```
3. Zaktualizuj `README.md` (jeśli ma instrukcje Maven)
4. Zaktualizuj CI (jeśli jest — GitHub Actions, `.travis.yml`)

**Kryterium sukcesu:** `pom.xml` usunięty, `./gradlew build` działa end-to-end.

### Faza 5: Compose GUI eksperymenty (otwarte)

Po fazie 4 masz działający setup:
- `./gradlew runJavaFX` — stary GUI (JavaFX, bez zmian)
- `./gradlew runCompose` — nowy GUI (Compose, eksperymentuj)
- `./gradlew shadowJar -Parch=arm64` — fat jar (JavaFX entry point)
- `./gradlew test -PskipTests=false` — 102 testy

Tu zaczyna się praca nad Compose UI — przepisywanie Settings/Auth/Realms z FXML
na Compose, dodawanie JCEF dla mapy, izometryczny render (patrz
`ISOMETRIC_MAP_DESIGN.md`).

## 9. Ryzyka

### 9.1 Shadow plugin + Java 25 (średnie)
Maven shade-plugin miał bug z Java 25 class files (major version 69) — wymagał
`jdependency 2.14` workaround. Shadow plugin może mieć podobny problem.
**Mitigacja:** Shadow 8.1.1+ wspiera Java 25. Jeśli nie — użyj
`com.gradleup.shadow` (fork) lub `jlink` zamiast shadow.

### 9.2 JavaFX classifier per-arch (niskie)
Gradle obsługuje classifier przez `:linux-aarch64` suffix. Testowane, działa.
**Mitigacja:** sprawdź `./gradlew dependencies --configuration runtimeClasspath`.

### 9.3 SPI `VersionModule` (niskie)
Shade `ServicesResourceTransformer` → Shadow `mergeServiceFiles()`. Bezpośrednie
mapowanie, sprawdzone.
**Mitigacja:** sprawdź zawartość jar — `unzip -l build/libs/*.jar | grep services`.

### 9.4 `ChunkTest` pobiera `server.jar` (niskie)
Test pobiera `server.jar` per wersja MC przy pierwszym uruchomieniu. To nie
zależy od build system — Gradle uruchamia JUnit tak samo jak Maven.
**Mitigacja:** pierwsze `./gradlew test` będzie wolne (jak `mvn test`), potem
cache.

### 9.5 Compose + JavaFX w jednym procesie (średnie)
Compose (Skiko) i JavaFX (Prism) to dwa toolkit'y GPU. W jednym procesie mogą
konfliktować (GPU context, GLFW, etc.). Ale — nie uruchamiasz ich
**jednocześnie**, tylko osobno (`runCompose` vs `runJavaFX`). Fat jar używa
JavaFX entry point (`core.Launcher`), nie Compose.
**Mitigacja:** jeśli konflikt — Compose przez osobny jar (`shadowJarCompose`).

### 9.6 Kotlin compiler + Java 25 (niskie)
Kotlin 2.1.21 wspiera Java 25 target. `jvmToolchain(25)` działa.
**Mitigacja:** sprawdź `./gradlew compileKotlin` — jeśli błąd, podnieś Kotlin.

## 10. Szacunek

| Faza | Czas | Ryzyko |
|---|---|---|
| 0: Przygotowanie | 30 min | niskie |
| 1: Backend na Gradle | 2-3 godz | średnie (Java 25) |
| 2: Fat jar (Shadow) | 1-2 godz | średnie (Java 25) |
| 3: Compose source set | 1-2 godz | niskie |
| 4: Usunięcie Maven | 30 min | niskie |
| **Razem** | **~5-8 godzin** | |

## 11. Weryfikacja po każdej fazie

| Faza | Komenda | Oczekiwany wynik |
|---|---|---|
| 0 | `./gradlew tasks` | lista tasków |
| 1 | `./gradlew test -PskipTests=false` | 102 testy passing |
| 2 | `java -jar build/libs/*.jar` | JavaFX GUI się otwiera |
| 3 | `./gradlew runCompose` | Compose GUI się otwiera |
| 3 | `./gradlew runJavaFX` | JavaFX GUI się otwiera |
| 4 | `./gradlew build` | pełny build bez pom.xml |

## 12. Otwarte pytania

1. **Shadow plugin wersja:** `com.github.johnrengelman.shadow:8.1.1` czy
   `com.gradleup.shadow` (fork)? Sprawdzić wsparcie Java 25.
2. **Kotlin wersja:** 2.1.21 (najnowsza stabilna) czy nowsza? Compose 1.8.2
   wymaga Kotlin 2.1+.
3. **Compose wersja:** 1.8.2 (najnowsza stabilna) czy dev? Dev ma najnowsze
   fixy ale mniej stabilne.
4. **JCEF w fazie 3 czy później:** dodajemy `jcefmaven` od razu (żeby Compose
   miał dostęp do JCEF) czy dopiero w fazie izometrycznej?
5. **`Launcher.initBackend()` vs `Config.init()`:** trzeba sprawdzić co dokładnie
   `Config.init()` robi — czy tylko launchuje JavaFX, czy też ładuje config z
   pliku. Jeśli to drugie — `initBackend()` musi wołać część config bez JavaFX.
