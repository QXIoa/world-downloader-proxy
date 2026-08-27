# Plan: nowe GUI w Jewel (Compose Desktop)

Status: **draft do dyskusji**.

## 0. Kontekst

Projekt ma obecnie **dwa GUI**:
- **JavaFX** (`core.gui.GuiManager extends Application`) — pełne, produkcyjne.
  Kontrolery: `GuiSettings`, `GuiMap`, `AuthTabController`, `RealmsTabController`,
  `RightClickMenu`. FXML w `src/main/resources/ui/`, motyw w `dark.css`, i18n w
  `messages-gui.properties` (`Messages.gui(key)`).
- **Compose spike** (`src/main/kotlin/core/gui/compose/MainCompose.kt`) — trivialny
  licznik kliknięć w Material3, nie podłączony do backendu.

Cel: **zastąpić JavaFX GUI pełnym Compose Desktop z motywem Jewel** (IntelliJ UI
look & feel), podłączonym do istniejącego backendu bez zmian logiki proxy.

### 0.1 Dlaczego Jewel

Jewel to biblioteka JetBrains implementująca IntelliJ Platform UI w Compose
Multiplatform. Daje natywny wygląd IDE (dark/light, Darcula), gotowe komponenty
(Tabs, TextField, Checkbox, Slider, ComboBox, LazyColumn, Tooltip, ContextMenu,
Banner, ProgressBar) — dokładnie to, czego potrzebuje to GUI.

**Kluczowa zgodność:** projekt używa Compose **1.8.2** + Kotlin **2.3.0**. Jewel
**0.29.0-252.24604** jest zbudowany dokładnie przeciw Compose **1.8.2** (patrz
`platform/jewel/RELEASE NOTES.md`, sekcja v0.29). To eliminuje ryzyko
niezgodności binarnej.

> Uwaga: repo `JetBrains/jewel` jest zarchiwizowane (kwi 2025); Jewel żyje dalej
> w `JetBrains/intellij-community/platform/jewel`. Artefakty standalone publikowane
> są jako `org.jetbrains.jewel:jewel-int-ui-standalone:<jewel>-<ijp-build>`.

---

## 1. Architektura docelowa

```
┌──────────────────────────────────────────────────────────┐
│  Compose Desktop (Kotlin) — src/main/kotlin/core/gui/jewel│
│                                                          │
│  JewelTheme(IntUiDarkTheme) {                            │
│    AppShell {                                            │
│      ┌─ SettingsScreen (TabbedLayout)                    │
│      │   ├ ConnectionTab                                 │
│      │   ├ GeneralTab                                    │
│      │   ├ WorldTab                                      │
│      │   ├ AuthTab                                       │
│      │   ├ RealmsTab                                     │
│      │   └ ErrorLogTab                                  │
│      ├─ MapScreen (Canvas / SwingPanel-JCEF — faza 3)    │
│      └─ StatusBar                                        │
│    }                                                     │
│  }                                                       │
│         ▲                                                │
│         │ GuiBridge (Kotlin↔Java, bez JavaFX)            │
│  ┌──────┴─────────────────────────────────────────────┐  │
│  │  Backend Java (bez zmian logiki)                   │  │
│  │  Config • VersionModule • proxy • auth • chunks    │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 1.1 Zasada: backend nie wie o JavaFX ani Compose

Obecnie `Config` i inne klasy backendu importują `javafx.application.Platform`
i wołają `GuiManager.*` (statyczne metody). To jest **główna blokada migracji**.
Rozwiązanie: warstwa abstrakcji `GuiBridge` — interfejs w Javie, implementacja
w Kotlin/Compose. Backend woła `GuiBridge` zamiast `GuiManager`.

---

## 2. Zmiany w build

### 2.1 `build.gradle.kts` — zależności Jewel

```kotlin
dependencies {
    // ... istniejące backend ...

    // Compose Desktop — NIE Material (Jewel zastępuje)
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    // compose.runtime/foundation/ui nadal potrzebne — currentOs je ciągnie

    // Jewel standalone — dopasowane do Compose 1.8.2
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.29.0-252.24604")
    // opcjonalnie, własne okna z dekoracją:
    // implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.29.0-252.24604")
}
```

Repozytorium `https://maven.pkg.jetbrains.space/public/p/compose/dev` już jest w
`build.gradle.kts` (linia 22) — Jewel standalone jest na Maven Central, ale
warto zostawić repo Compose dev dla ewentualnych -SNAPSHOT.

### 2.2 `settings.gradle.kts` — bez zmian

`pluginManagement` już ma `gradlePluginPortal` + Compose dev repo + Maven Central.

### 2.3 Pluginy — bez zmian

`kotlin("jvm") 2.3.0`, `org.jetbrains.compose 1.8.2`,
`org.jetbrains.kotlin.plugin.compose 2.3.0` — wszystkie zgodne z Jewel 0.29.

### 2.4 Task `runCompose`

Już istnieje (linia 131-135), `mainClass = core.gui.compose.MainComposeKt`.
Zostaje, ale `MainCompose.kt` zostanie przepisany (faza 1).

### 2.5 JavaFX — usunięcie (faza 4)

Po pełnej migracji usunąć wszystkie `org.openjfx:*` zależności (linie 41-72) oraz
plugin/źródła JavaFX. Do tego momentu JavaFX zostaje, bo `core.Launcher` (fat jar
Main-Class) nadal go używa.

---

## 3. Warstwa abstrakcji: `GuiBridge`

To najważniejszy i najtrudniejszy element. Backend obecnie woła statyczne metody
`GuiManager` z wielu miejsc (proxy, chunk parser, auth). Trzeba to odwrócić.

### 3.1 Nowy interfejs (Java, `core.gui.bridge`)

```java
package core.gui.bridge;

/** Platform-agnostyczny most backend→GUI. Implementacja w Compose. */
public interface GuiBridge {
    void runOnUi(Runnable r);
    void loadSettingsScene();
    void loadMapScene();
    void openSettingsWindow();
    void closeSettingsWindow();
    void setDimension(Object dimension);
    void setChunkLoaded(Object coord, Object chunk);
    void setChunkState(Object coords, Object state);
    void clearChunks();
    void setStatusMessage(String msg);
    void showMapError();
    void hideMapError();
    void notifyError(String message);
    void setAuthenticationFailed();
    boolean isStarted();
    void saveAndExit();
    boolean openWebLink(String url);
    boolean openFileLink(String path);
}
```

> Typy `Object` dla parametrów per-version (IChunk, IDimension, Coordinate*) —
> backend ich nie potrzebuje znać; implementacja castuje. Alternatywa: przenieść
> interfejs do `core.interfaces` i użyć typów z `core.interfaces.*` / `core.coordinates.*`.

### 3.2 Rejestracja

```java
// core.gui.bridge.GuiBridges
public final class GuiBridges {
    private static volatile GuiBridge active;
    public static GuiBridge get() { return active; }
    public static void set(GuiBridge b) { active = b; }
}
```

### 3.3 Migracja wywołań w backendzie

Wyszukać wszystkie `GuiManager.` wołania poza `core.gui.*` i zastąpić
`GuiBridges.get().`:

| Obecne wywołanie | Plik(i) | Zastąpić przez |
|---|---|---|
| `GuiManager.setStatusMessage(...)` | `Config`, proxy | `GuiBridges.get().setStatusMessage(...)` |
| `GuiManager.setChunkLoaded(...)` | chunk parser | `GuiBridges.get().setChunkLoaded(...)` |
| `GuiManager.setDimension(...)` | world manager | `GuiBridges.get().setDimension(...)` |
| `GuiManager.clearChunks()` | world manager | `GuiBridges.get().clearChunks()` |
| `GuiManager.redirectErrorOutput()` | `Config` | przez `GuiBridges.get().notifyError(...)` |
| `Platform.runLater(...)` w backendzie | `Config`, auth | `GuiBridges.get().runOnUi(...)` |

To jest mechaniczne, ale dotyka wielu plików. **Krytyczne:** zachować
`GuiManager` jako jednego z implementatorów `GuiBridge` (JavaFX) podczas migracji,
żeby stare GUI nadal działało aż do fazy 4.

### 3.4 Implementacja Compose (`core.gui.jewel.JewelGuiBridge`)

Kotlin, trzyma referencje do stanu Compose (`mutableStateOf`, `SnapshotStateMap`)
i dispatchuje przez `MainScope().launch { ... }` (odpowiednik `Platform.runLater`).

---

## 4. Mapowanie funkcjonalności JavaFX → Jewel

### 4.1 Settings — `Settings.fxml` + `GuiSettings` → `SettingsScreen.kt`

| JavaFX | Jewel odpowiednik |
|---|---|
| `TabPane` z 6 tabami | `TabbedLayout` / `LazyTabRow` + `HorizontalPager` (lub własny `AnimatedContent` na `selectedTab`) |
| `TextField` | `JewelTextField` (`org.jetbrains.jewel.ui.TextInput`) |
| `CheckBox` | `Checkbox` |
| `Slider` | `Slider` (Jewel ma własny) |
| `IntField`/`LongField`/`DefaultIntField` | wrapper kompozytowalny nad `JewelTextField` z `KeyboardActions`/`visualTransformation` filtrujący cyfry |
| `Hyperlink` | `Text` z `Modifier.clickable` + kolor linku z motywu |
| `Tooltip` (QuickTooltip) | `Tooltip` Jewel |
| `TitledPane` "Advanced" | `GroupHeader` / `Card` z `expandable` |
| `TextArea` (error log) | `TextArea` Jewel w `VerticallyScrollableContainer` |
| `Button` "Start/Save" | `PrimaryButton` / `ActionButton` |
| `dark.css` | `IntUiDarkTheme` (Jewel) — zielony akcent `#008943` przez custom `ThemeColorPalette` |

**Zakładka Connection:** server address + Microsoft login button + auth result
label + support link.

**Zakładka General:** extended render distance (slider + int field), mark
unsaved, grey out old, show players, cave mode, schematic mode, advanced
(info messages, draw extended chunks), downloader port + walidacja portu.

**Zakładka World:** output dir + open link, level seed, offset X/Z, prevent
chunk generation.

**Zakładka Authentication:** radio (Automatic/Microsoft/Manual) + pane per
method + check status button + status text + failed label. Logika z
`AuthTabController` przeniesiona do `AuthViewModel`.

**Zakładka Realms:** username field + load button + `LazyColumn` z realm items
(zamiast `ListView` + `RealmItem.fxml`). Każdy item: name, motd, request/select
button, loading indicator. Logika z `RealmsTabController`/`RealmEntry`/
`RealmItemController` przeniesiona do `RealmsViewModel`.

**Zakładka Error log:** `TextArea` read-only, bindowany do strumienia błędów.

### 4.2 Map — `Map.fxml` + `GuiMap` → `MapScreen.kt`

To najtrudniejszy element. Dwie ścieżki:

**Ścieżka A (faza 2, top-down Canvas):** Compose `Canvas` (Skiko) zamiast JavaFX
`Canvas`. Przeniesienie logiki rysowania:
- `RegionImageHandler` → emituje `BufferedImage`/`ImageBitmap` per region
- `AnimationTimer` → `LaunchedEffect` + `withFrameNanos`
- zoom/drag → `Modifier.pointerInput { detectTransformGestures }`
- `ZoomBehaviour` (Smooth/Snap) → ten sam algorytm, wywoływany z frame callback
- player marker, other players, cursor coords, marker distance — rysowane na
  overlay `Canvas`
- context menu → `DropdownMenu` Jewel (zamiast `RightClickMenu extends ContextMenu`)
- help/coords/status labels → `Text` z `Modifier.align`

**Ścieżka B (faza 3, izometryczna — wg `ISOMETRIC_MAP_DESIGN.md`):** `SwingPanel`
z JCEF + Leaflet. To jest osobny, większy projekt; ten plan go **przewiduje jako
fazę opcjonalną**, nie blokuje faz 1-2.

### 4.3 Context menu — `RightClickMenu` → `MapContextMenu.kt`

Pozycje menu (z `messages-gui.properties`):
- Pause/Resume saving → `Config.getVersionModule().getWorldManager().pause/resume`
- Delete all (z confirm dialog → `AlertDialog` Jewel)
- Redraw nearby / Redraw region
- Copy coordinates (schowek przez `java.awt.Toolkit`)
- Render mode (submenu: Automatic/Surface/Caves)
- Settings → `GuiBridges.get().openSettingsWindow()`
- Save & Exit → `GuiBridges.get().saveAndExit()`
- Dev options (warunkowe)

### 4.4 Lifecycle — `GuiManager.start()` → `MainCompose.kt`

Obecnie `Config.settingsComplete()` woła `GuiManager.loadSceneSettings()` lub
`loadSceneMap()`. Po migracji: `GuiBridges.get().loadSettingsScene()` itd.
`Launcher.main()` bootstrap bez zmian (VersionRegistry, Config.init) — tylko
`GuiBridges.set(JewelGuiBridge(...))` przed `Config.init`.

### 4.5 i18n

`Messages.gui(key)` / `Messages.gui(key, args)` (Java, `ResourceBundle`) —
wywoływalne z Kotlin bez zmian. W Compose: helper
`fun t(key: String, vararg args: Any?) = Messages.gui(key, *args)`. Klucze w
`messages-gui.properties` zostają te same.

### 4.6 Motyw / kolory

`dark.css` definiuje zielony akcent `#008943` i ciemne tło `#262626`. Jewel
`IntUiDarkTheme` jest ciemny z domyślnym akcentem IntelliJ. Aby zachować
tożsamość wizualną projektu: custom `ThemeColorPalette` z `#008943` jako
`accentKey` — Jewel pozwala na `IntUiThemeDefinition` z override'ami kolorów.

---

## 5. Struktura plików (nowe)

```
src/main/kotlin/core/gui/jewel/
├── MainCompose.kt              # entry point (przepisany)
├── JewelGuiBridge.kt           # implementacja GuiBridge
├── AppShell.kt                 # root composable, nawigacja Settings↔Map
├── theme/
│   └── Theme.kt                # IntUiDarkTheme + custom accent
├── i18n.kt                     # t() helper
├── settings/
│   ├── SettingsScreen.kt       # TabbedLayout + 6 tabów
│   ├── ConnectionTab.kt
│   ├── GeneralTab.kt
│   ├── WorldTab.kt
│   ├── AuthTab.kt
│   ├── RealmsTab.kt
│   ├── ErrorLogTab.kt
│   └── SettingsViewModel.kt    # bind do Config (odpowiednik GuiSettings.save())
├── auth/
│   ├── AuthViewModel.kt        # logika z AuthTabController
│   └── MicrosoftAuthFlow.kt
├── realms/
│   ├── RealmsViewModel.kt      # logika z RealmsTabController
│   └── RealmItem.kt
├── map/
│   ├── MapScreen.kt            # Canvas + overlay (faza 2)
│   ├── MapViewModel.kt         # stan: playerPos, center, zoom, bounds
│   ├── MapContextMenu.kt       # zamiast RightClickMenu
│   ├── ZoomController.kt       # Smooth/Snap (przeniesione z Java)
│   └── RegionRenderer.kt       # most do RegionImageHandler
└── components/
    ├── IntField.kt             # Compose wrapper (zamiast core.gui.components.IntField)
    ├── LongField.kt
    └── Tooltip.kt              # wrapper Jewel Tooltip
```

```
src/main/java/core/gui/bridge/
├── GuiBridge.java              # interfejs
└── GuiBridges.java             # rejestracja statyczna
```

```
src/main/java/core/gui/javafx/
└── JavaFxGuiBridge.java        # implementacja GuiBridge nad starym GuiManager (faza przejściowa)
```

---

## 6. Fazy implementacji

### Faza 0: Build + spike Jewel (bez logiki)

1. Dodać zależności Jewel do `build.gradle.kts` (sekcja 2.1).
2. Przepisać `MainCompose.kt`: `JewelTheme(IntUiDarkTheme) { ... }` z jednym
   `TabbedLayout` i parą `JewelTextField`/`Checkbox` — udowodnić, że Jewel się
   linkuje i renderuje.
3. `./gradlew runCompose` — **kryterium:** okno Jewel się otwiera, motyw ciemny.

### Faza 1: `GuiBridge` + Settings (tylko Connection + General + World)

1. Stworzyć `GuiBridge` / `GuiBridges` (Java).
2. Stworzyć `JavaFxGuiBridge` (implementuje przez istniejący `GuiManager`) —
   backend od razu działa ze starym GUI.
3. Zmigrować wywołania `GuiManager.*`/`Platform.runLater` w backendzie na
   `GuiBridges.get().*` (sekcja 3.3). **Testy (`./gradlew test -PskipTests=false`)
   muszą przejść — 173 metody.**
4. `SettingsViewModel.kt` — bind do `Config` (odczyt/zapis pól, walidacja portu,
   `settingsComplete()`).
5. `ConnectionTab`, `GeneralTab`, `WorldTab` w Compose/Jewel.
6. `JewelGuiBridge` — rejestracja w `MainCompose.kt`, `loadSettingsScene()`
   pokazuje `SettingsScreen`.
7. **Kryterium:** `./gradlew runCompose` → okno ustawień z 3 tabami, wpisany
   server, klik "Start" → proxy startuje, mapa (jeszcze JavaFX lub brak).

### Faza 2: Map (top-down Canvas) + reszta Settings

1. `MapScreen.kt` z Compose `Canvas` (Skiko) — przenieść rysowanie z `GuiMap`.
2. `MapViewModel` — player pos, center, zoom, bounds, `LaunchedEffect` frame loop.
3. `ZoomController` — Smooth/Snap (algorytm z `SmoothZooming`/`SnapZooming`).
4. `RegionRenderer` — most do `RegionImageHandler` (JavaFX `Image` →
   `ImageBitmap`; `RegionImage` może wymagać adaptera — lub renderować na
   `BufferedImage` i konwertować).
5. `MapContextMenu.kt` — wszystkie pozycje z `RightClickMenu`.
6. `AuthTab`, `RealmsTab`, `ErrorLogTab` — dokończenie Settings.
7. `AppShell` — nawigacja Settings↔Map, status bar.
8. **Kryterium:** pełne GUI w Compose/Jewel, proxy działa end-to-end, mapa
   rysuje chunki, zoom/pan/drag, context menu, save & exit.

### Faza 3 (opcjonalna): Mapa izometryczna (JCEF + Leaflet)

Zgodnie z `ISOMETRIC_MAP_DESIGN.md`. Nie blokuje faz 1-2. `MapScreen` zamienia
`Canvas` na `SwingPanel { JCEF }`. Backend izometryczny (`core.gui.iso.*`) jest
niezależny od warstwy GUI.

### Faza 4: Usunięcie JavaFX

1. Usunąć `core.gui.GuiManager`, `GuiSettings`, `GuiMap`, `AuthTabController`,
   `RealmsTabController`, `RightClickMenu`, `core.gui.components.*`,
   `core.gui.images.*`, `core.gui.markers.*`, `ZoomBehaviour`/`Smooth*`/`Snap*`.
2. Usunąć FXML (`src/main/resources/ui/*.fxml`) + `dark.css`.
3. Usunąć `org.openjfx:*` z `build.gradle.kts`.
4. `Launcher.main()` → ustawić `GuiBridges.set(JewelGuiBridge(...))` i wołać
   Compose `application { ... }` (lub nowy entry point Kotlin, a `Launcher` tylko
   bootstrapuje backend i deleguje).
5. `shadowJar` Main-Class → Compose entry point.
6. `runJavaFX` task → usunąć.
7. **Kryterium:** `./gradlew build` + `./gradlew test -PskipTests=false` zielone,
   fat jar uruchamia Jewel GUI.

---

## 7. Ryzyka i decyzje do potwierdzenia

### 7.1 `RegionImageHandler` zależy od JavaFX `Image`

`RegionImage`/`RegionImageHandler` używają `javafx.scene.image.Image`. W fazie 2
trzeba albo:
- **(a)** refaktorować `RegionImageHandler` na `BufferedImage` (java.awt) i
  konwertować do `ImageBitmap` w Kotlin — czystsze, ale dotyka backendu;
- **(b)** zostawić JavaFX `Image` na classpath do fazy 4 i adapter
  `Image → ImageBitmap`.

Decyzja: **(b)** dla fazy 2 (mniej ryzyka), **(a)** przy okazji fazy 4.

### 7.2 `Platform.runLater` w backendzie

`Config`, `MicrosoftAuthServer`, auth callbacks wołają `Platform.runLater`.
Po wprowadzeniu `GuiBridge.runOnUi` to zniknie, ale wymaga zmian w tych plikach.
Akceptowalne — to czysta poprawa (odwrócenie zależności).

### 7.3 Skiko vs JavaFX rendering performance

Compose Desktop na Skiko (OpenGL) — wydajność rysowania mapy powinna być
porównywalna lub lepsza niż JavaFX Canvas. Ryzyko: pierwsze uruchomienie Skiko
kompiluje shader (jeden raz). Do zweryfikowania w fazie 2.

### 7.4 Fat jar — JavaFX classifiers vs Jewel

Obecnie fat jar bundluje JavaFX per-OS (arm64/x86_64). Po fazie 4 JavaFX znika;
Jewel + Compose + Skiko są cross-platform (Skiko dobiera natywny backend per OS).
Shadow `duplicatesStrategy = WARN` może wymagać korekty dla natywnych lib Skiko.

### 7.5 Zakres faz — potwierdzenie

Czy faza 3 (izometryczna/JCEF) jest w zakresie, czy ten plan ma pokryć tylko
top-down w Jewel? (Domyślnie: faza 3 opcjonalna, osobny projekt.)

---

## 8. Weryfikacja

Po każdej fazie:
```bash
export JAVA_HOME=/root/.jdks/corretto-25.0.4.1
./gradlew test -PskipTests=false   # 173 metod musi przejść
./gradlew runCompose               # ręczne sprawdzenie GUI
./gradlew shadowJar                # fat jar się buduje
```

Po fazie 4 dodatkowo: uruchomienie fat jar bez JavaFX na classpath.
