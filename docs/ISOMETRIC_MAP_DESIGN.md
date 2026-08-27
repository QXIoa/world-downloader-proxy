# Szkic: izometryczny podgląd mapy z prawdziwymi teksturami

Status: **draft do dyskusji**.

## 1. Cel

Zastąpić obecny top-down podgląd mapy (JavaFX `Canvas`, 1 kolor per blok) widokiem
izometrycznym 3D z prawdziwymi teksturami z `client.jar`, wyświetlanym przez JCEF +
Leaflet wewnątrz GUI Compose Desktop.

### 1.1 Założenia upraszczające (vs pełny Minecraft Overviewer)

- **Jedna wysokość**: tylko surface (najwyższy nie-powietrzny blok). Brak cross-sections,
  brak multi-height tiles dla zoomu w pionie.
- **2 kierunki patrzenia**: północny-wschód (NE) i południowy-zachód (SW) — przeciwległe
  kąty izometryczne, wybierane w ustawieniach.
- **Jedno oświetlenie**: 3 stałe mnożniki jasności per ściana (top 1.0, left 0.8, right 0.6).
  Brak smooth-lighting, brak light propagation, brak day/night.
- **Kształty bloków**: na start wszystko jako pełne kostki. Schody/słaby/płoty/drzwi
  renderowane jako kostka z odpowiednią teksturą. Później można dodać specjalne przypadki
  dla ~20 najważniejszych bloków.
- **Brak biome coloring**: jedna kolor per tekstura (bez tintowania trawy/liści per biom).
- **Brak cave/nether/night/mineral overlays**: tylko surface, tryb normalny.

### 1.2 Czego nie robimy

- Nie parsujemy `assets/minecraft/models/block/*.json` (modele bloków).
- Nie parsujemy `assets/minecraft/blockstates/*.json`.
- Nie implementujemy UV mapping, element culling, parent inheritance z modeli.
- Nie pobieramy `client.jar` runtime — tekstury bundlowane w jarze per wersja.

## 2. Architektura

```
┌─────────────────────────────────────────────────────────┐
│  Compose Desktop (Kotlin)                               │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │  SwingPanel { JCEF }                             │    │
│  │  ┌───────────────────────────────────────────┐   │    │
│  │  │  Chromium → Leaflet.js                     │   │    │
│  │  │  tiles: http://worlddl/tiles/{z}/{x}/{y}.png│   │    │
│  │  │  state: JSQuery (Java↔JS)                  │   │    │
│  │  └───────────────────────────────────────────┘   │    │
│  │         ▲ custom scheme handler                   │    │
│  │  ┌──────┴────────────────────────────────────┐    │    │
│  │  │  Java (backend, bez zmian logiki)          │    │    │
│  │  │  • client.jar textures (bundled per ver)   │    │    │
│  │  │  • konwencja nazw → top/side texture       │    │    │
│  │  │  • Chunk → isometric sprite (cached)       │    │    │
│  │  │  • Region → tile pyramid → .map/           │    │    │
│  │  │  • Custom scheme serves tiles to JCEF      │    │    │
│  │  └────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  Settings / Auth / Realms (Compose Material 3)          │
└─────────────────────────────────────────────────────────┘
```

### 2.1 Flow danych

1. Proxy pobiera chunk z serwera MC (istniejący kod, bez zmian)
2. `IsometricChunkRenderer` renderuje chunk jako obraz izometryczny z teksturami
3. `IsometricTileGenerator` łączy chunki w region → tile pyramid → `.map/{z}/{x}/{y}.png`
4. JCEF ładuje `http://worlddl/map.html` (z JAR przez custom scheme)
5. Leaflet requestuje tiles → custom scheme handler serwuje z `.map/`
6. Użytkownik zoom/pan w Leaflet, zmiana kierunku w settings → re-render tiles

### 2.2 Zero HTTP server

Wszystko przez custom scheme handler w JCEF — Chromium przekazuje requesty do Java
handlera, który serwuje z JAR (HTML/JS) i z dysku (tiles). Brak portów, brak firewall,
brak NanoHTTPD dla mapy (NanoHTTPD zostaje dla OAuth w `MicrosoftAuthServer`).

## 3. Komponenty

### 3.1 Tekstury — bundled per wersja (~100 linii)

**Źródło:** `client.jar` → `assets/minecraft/textures/block/*.png`

**Bundling (build-time):** skrypt Gradle/Maven który per wspierana wersja:
1. Pobiera `client.jar` z Mojang version_manifest
2. Ekstrahuje `assets/minecraft/textures/block/*.png` (~800 plików, ~5MB)
3. Pakuje do `src/main/resources/v<version>/textures/block/`

**Runtime:** `TextureLoader` ładuje tekstury z classpath:
```java
public class TextureLoader {
    private final String versionDir;  // "v26_1"
    private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();

    public BufferedImage load(String textureName) {
        // "grass_block_top" → /v26_1/textures/block/grass_block_top.png
        return cache.computeIfAbsent(textureName, this::doLoad);
    }

    private BufferedImage doLoad(String name) {
        String path = "/" + versionDir + "/textures/block/" + name + ".png";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return in != null ? ImageIO.read(in) : null;
        } catch (IOException e) { return null; }
    }
}
```

**Koszt w jarze:** ~5MB per wersja MC. Dla 2 wersji (26.1, 26.2) = ~10MB.

### 3.2 Konwencja nazw tekstur (~80 linii)

Bez parsowania modeli JSON. Konwencja nazw Minecrafta:

| Typ bloku | Top texture | Side texture | Przykłady |
|---|---|---|---|
| Jedna tekstura | `<name>.png` | `<name>.png` | stone, dirt, cobblestone |
| Top/side różne | `<name>_top.png` | `<name>_side.png` | grass_block, podzol |
| Log (pień) | `<name>_top.png` | `<name>.png` | oak_log, birch_log |
| Inne | `<name>_top.png` | `<name>.png` | sandstone |

```java
public class BlockTextureResolver {
    private final TextureLoader loader;

    public BufferedImage getTopTexture(String blockName) {
        // spróbuj: <name>_top, potem <name>
        BufferedImage t = loader.load(blockName + "_top");
        return t != null ? t : loader.load(blockName);
    }

    public BufferedImage getSideTexture(String blockName) {
        // spróbuj: <name>_side, potem <name>
        BufferedImage t = loader.load(blockName + "_side");
        return t != null ? t : loader.load(blockName);
    }
}
```

**Wyjątki** — ~20 bloków z różnymi side faces (furnace, crafting_table, piston...):
```java
private static final Map<String, String> FRONT_TEXTURES = Map.of(
    "furnace", "furnace_front",
    "crafting_table", "crafting_table_front",
    "piston", "piston_face",
    "dispenser", "dispenser_front",
    "observer", "observer_front",
    "loom", "loom_front",
    // ~20 wpisów
);
```

### 3.3 Isometric sprite per blok (~300 linii)

Dla każdego block type: renderuj 3 ściany kostki w rzucie izometrycznym.
Cache'uj sprite per (blockName, angle) — raz wygenerowane, używaj wielokrotnie.

```java
public class IsometricSpriteRenderer {
    private static final int TILE_W = 32;   // szerokość sprite
    private static final int TILE_H = 48;   // wysokość sprite (top + 2 sides)
    private static final int VOXEL_H = 16;  // wysokość w pionie per blok

    private static final double BRIGHT_TOP = 1.0;
    private static final double BRIGHT_LEFT = 0.8;
    private static final double BRIGHT_RIGHT = 0.6;

    private final BlockTextureResolver resolver;
    private final Map<String, BufferedImage> spriteCache = new ConcurrentHashMap<>();

    public BufferedImage getSprite(String blockName, BlockState state, IsoAngle angle) {
        String key = blockName + ":" + state.getPropertiesHash() + ":" + angle;
        return spriteCache.computeIfAbsent(key, k -> renderSprite(blockName, state, angle));
    }

    private BufferedImage renderSprite(String blockName, BlockState state, IsoAngle angle) {
        BufferedImage top = resolver.getTopTexture(blockName);
        BufferedImage side = resolver.getSideTexture(blockName);
        if (top == null) return fallbackColor(blockName);

        BufferedImage sprite = new BufferedImage(TILE_W, TILE_H, TYPE_INT_ARGB);
        Graphics2D g = sprite.createGraphics();

        // top face — rhombus (sheared texture)
        drawShearedTop(g, top, 0, 0, TILE_W, TILE_H / 3, BRIGHT_TOP);

        // left + right faces — zależnie od kąta i facing
        BufferedImage leftTex = resolveSideTexture(blockName, state, angle, WorldFace.LEFT);
        BufferedImage rightTex = resolveSideTexture(blockName, state, angle, WorldFace.RIGHT);
        drawShearedSide(g, leftTex, 0, TILE_H / 3, TILE_W / 2, 2 * TILE_H / 3, BRIGHT_LEFT);
        drawShearedSide(g, rightTex, TILE_W / 2, TILE_H / 3, TILE_W / 2, 2 * TILE_H / 3, BRIGHT_RIGHT);

        if (angle == IsoAngle.SW) sprite = mirrorHorizontal(sprite);
        return sprite;
    }
}
```

**Sheared drawing** — `AffineTransform` dla rhombus/parallelogram:
```java
private void drawShearedTop(Graphics2D g, BufferedImage tex, int x, int y, int w, int h, double brightness) {
    AffineTransform at = new AffineTransform();
    at.translate(x, y);
    at.shear(-0.5, 0.0);        // rhombus dla top face
    at.scale((double) w / tex.getWidth(), (double) h / tex.getHeight());
    g.setTransform(at);
    g.drawImage(applyBrightness(tex, brightness), 0, 0, null);
}
```

### 3.4 Chunk → isometric image (~380 linii)

Główny renderer. Iteruje bloki w kolejności painter's algorithm, rysuje sprite per blok.

```java
public class IsometricChunkRenderer {
    private final IsometricSpriteRenderer spriteRenderer;

    public BufferedImage render(Chunk chunk, IsoAngle angle) {
        int w = 16 * TILE_W;   // chunk = 16×16 bloków
        int h = 16 * TILE_H + 256 * VOXEL_H;  // + wysokość dla elevation
        BufferedImage img = new BufferedImage(w, h, TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // painter's algorithm: rysuj od tyłu do przodu
        // NE: od (max x+z) do (min x+z)
        // SW: od (min x+z) do (max x+z) — odbicie
        Iterable<int[]> order = painterOrder(angle);

        for (int[] pos : order) {
            int x = pos[0], z = pos[1];
            int y = chunk.getHeight(x, z);           // istniejące: heightAt()
            BlockState block = chunk.getBlockStateAt(x, y, z);  // istniejące
            if (block == null) continue;

            BufferedImage sprite = spriteRenderer.getSprite(block.getName(), block, angle);

            int screenX = projectX(x, z, angle);
            int screenY = projectY(x, z, y, angle);
            g.drawImage(sprite, screenX, screenY, null);
        }
        return img;
    }

    private int projectX(int x, int z, IsoAngle angle) {
        return angle == IsoAngle.NE
            ? (x - z) * TILE_W / 2
            : (z - x) * TILE_W / 2;
    }

    private int projectY(int x, int z, int y, IsoAngle angle) {
        int base = (x + z) * TILE_H / 4 - y * VOXEL_H;
        return angle == IsoAngle.NE ? base : -base + 16 * TILE_H;
    }
}
```

**Integracja z istniejącym kodem:**
- `ChunkImageFactory.createImage()` (linia 367) → dodaj `createIsometricImage(angle)`
  obok istniejącego `createImage(isSurface)`
- `Chunk.getHeight()` / `Chunk.getBlockStateAt()` — już istnieją, używane w linii 347-348
- `BlockColors` (linia 41) → fallback dla bloków bez tekstury

### 3.5 Lighting (~50 linii)

Jedno oświetlenie — 3 stałe mnożniki per ściana, rotują z kątem:

```java
// NE: top=1.0, left(south)=0.8, right(west)=0.6
// SW: top=1.0, left(north)=0.8, right(east)=0.6  (odbicie)
```

```java
private BufferedImage applyBrightness(BufferedImage tex, double factor) {
    BufferedImage out = new BufferedImage(tex.getWidth(), tex.getHeight(), TYPE_INT_ARGB);
    for (int y = 0; y < tex.getHeight(); y++) {
        for (int x = 0; x < tex.getWidth(); x++) {
            int argb = tex.getRGB(x, y);
            int a = (argb >> 24) & 0xFF;
            int r = (int) (((argb >> 16) & 0xFF) * factor);
            int g = (int) (((argb >> 8) & 0xFF) * factor);
            int b = (int) ((argb & 0xFF) * factor);
            out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
        }
    }
    return out;
}
```

**Optymalizacja:** cache'uj `applyBrightness` per (texture, brightness) — 3 wersje per textura.

### 3.6 Tile pyramid dla Leaflet (~200 linii)

Region (32×32 chunki) → tile pyramid w stylu Leaflet (`{z}/{x}/{y}.png`).

```java
public class IsometricTileGenerator {
    private static final int TILE_PX = 256;
    private static final int MAX_ZOOM = 8;

    private final Path mapDir;  // .map/ folder

    public void generateRegionTiles(BufferedImage regionImg, int regionX, int regionZ, IsoAngle angle) {
        // zoom 0: cały świat w 1 tile (downsample)
        // zoom N: 4^N tiles
        for (int z = 0; z <= MAX_ZOOM; z++) {
            int tilesPerSide = 1 << z;
            int sourceW = regionImg.getWidth() / tilesPerSide;
            int sourceH = regionImg.getHeight() / tilesPerSide;
            for (int tx = 0; tx < tilesPerSide; tx++) {
                for (int ty = 0; ty < tilesPerSide; ty++) {
                    BufferedImage tile = downsample(regionImg,
                        tx * sourceW, ty * sourceH, sourceW, sourceH, TILE_PX, TILE_PX);
                    Path path = mapDir.resolve(angle.name().toLowerCase())
                        .resolve(String.valueOf(z))
                        .resolve(String.valueOf(tx))
                        .resolve(ty + ".png");
                    Files.createDirectories(path.getParent());
                    ImageIO.write(tile, "png", path.toFile());
                }
            }
        }
    }
}
```

**Cache:** tiles zapisywane w `.map/<angle>/<z>/<x>/<y>.png` obok jar (jak dziś `image-cache/`).
Re-render tylko gdy chunk się zmienia.

### 3.7 Leaflet HTML/JS (~80 linii)

Statyczny `map.html` bundlowany w jarze (`src/main/resources/web/map.html`):

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <link rel="stylesheet" href="leaflet.css"/>
    <script src="leaflet.js"></script>
    <style>
        html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #1a1a1a; }
    </style>
</head>
<body>
<div id="map"></div>
<script>
const map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: 0,
    maxZoom: 8,
    zoomControl: true,
    attributionControl: false
});

// tiles z custom scheme — kąt w URL
const angle = window.worlddlAngle || 'ne';
L.tileLayer('http://worlddl/tiles/' + angle + '/{z}/{x}/{y}.png', {
    tileSize: 256,
    maxZoom: 8,
    maxNativeZoom: 8,
    minZoom: 0,
    noWrap: true,
    continuousWorld: true
}).addTo(map);

// player marker — aktualizowany z Java przez JSQuery
window.updatePlayer = function(x, z, rotation) {
    // konwersja world coords → leaflet pixel coords
    const px = worldToLeaflet(x, z);
    if (window.playerMarker) {
        window.playerMarker.setLatLng(px);
    } else {
        window.playerMarker = L.circleMarker(px, {
            radius: 6, color: '#99e6ff', fillColor: '#99e6ff', fillOpacity: 0.7
        }).addTo(map);
    }
};
</script>
</body>
</html>
```

`leaflet.js` + `leaflet.css` bundlowane w jarze (`src/main/resources/web/`).

### 3.8 JCEF + custom scheme (~150 linii)

```java
public class MapBrowser {
    private final Path mapDir;
    private JBCefBrowser browser;

    public void init() {
        // rejestracja custom scheme — serwuje z JAR i dysku
        CefApp.getInstance().registerSchemeHandlerFactory(
            "http", "worlddl",
            (scheme, domain, request) -> new MapResourceHandler(mapDir)
        );

        browser = new JBCefBrowser();
        browser.loadURL("http://worlddl/map.html");
    }

    public JComponent getComponent() {
        return browser.getComponent();
    }

    // push player position do JS
    public void updatePlayer(int x, int z, float rotation) {
        browser.executeJavaScript(
            String.format("updatePlayer(%d,%d,%.1f);", x, z, rotation),
            "http://worlddl/map.html", 0
        );
    }
}
```

```java
class MapResourceHandler extends CefResourceHandler {
    private final Path mapDir;

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String path = request.getURL().replace("http://worlddl", "");

        if (path.equals("/map.html")) {
            serveResource("/web/map.html", "text/html");
        } else if (path.startsWith("/tiles/")) {
            // /tiles/ne/3/5/2.png → .map/ne/3/5/2.png
            File tile = mapDir.resolve(path.substring(1)).toFile();
            serveFile(tile, "image/png");
        } else if (path.endsWith(".js")) {
            serveResource("/web" + path, "application/javascript");
        } else if (path.endsWith(".css")) {
            serveResource("/web" + path, "text/css");
        }
        callback.Continue();
        return true;
    }
}
```

### 3.9 Integracja z Compose Desktop (~200 linii)

```kotlin
@Composable
fun MapView(mapBrowser: MapBrowser) {
    SwingPanel(
        factory = { mapBrowser.getComponent() },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MapSettingsCard(
    mapAngle: IsoAngle,
    onAngleChange: (IsoAngle) -> Unit
) {
    Card {
        Column {
            Text("Kierunek patrzenia")
            Row {
                FilterChip(
                    selected = mapAngle == IsoAngle.NE,
                    onClick = { onAngleChange(IsoAngle.NE) },
                    label = { Text("Północny-wschód") }
                )
                FilterChip(
                    selected = mapAngle == IsoAngle.SW,
                    onClick = { onAngleChange(IsoAngle.SW) },
                    label = { Text("Południowy-zachód") }
                )
            }
        }
    }
}
```

Zmiana kątu → `IsometricTileGenerator.generateRegionTiles(..., newAngle)` →
Leaflet automatycznie przeładuje tiles z nowego URL.

## 4. Integracja z istniejącym kodem

### 4.1 Bez zmian (backend)

- `core.proxy.*` — proxy server, handshake sniffer
- `core.auth.*` — MicrosoftAuthServer (NanoHTTPD zostaje dla OAuth)
- `game.data.chunk.*` — chunk parser, NBT
- `version.v26_*` — per-version chunk/palette/registries
- `RegistryLoader` — bundluje `blocks.json`/`registries.json` (już działa)

### 4.2 Modyfikacje

| Plik | Zmiana |
|---|---|
| `ChunkImageFactory.java` | Dodaj `createIsometricImage(IsoAngle)` obok `createImage()` |
| `RegionImageHandler.java` | Dodaj isometric tile generation obok top-down |
| `RegionImage.java` | Dodaj isometric variant obok top-down |
| `Config` | Dodaj `mapAngle` ustawienie (NE/SW) |
| `GuiManager.java` | Zamień JavaFX launch na Compose launch (faza 2) |

### 4.3 Nowe pliki

| Plik | Odpowiedzialność | Linie |
|---|---|---|
| `core/gui/iso/TextureLoader.java` | Ładowanie tekstur z JAR | ~100 |
| `core/gui/iso/BlockTextureResolver.java` | Konwencja nazw + wyjątki | ~80 |
| `core/gui/iso/IsometricSpriteRenderer.java` | Sprite 3D per blok | ~300 |
| `core/gui/iso/IsometricChunkRenderer.java` | Chunk → obraz izometryczny | ~380 |
| `core/gui/iso/IsometricTileGenerator.java` | Tile pyramid dla Leaflet | ~200 |
| `core/gui/iso/IsoAngle.java` | Enum NE/SW + projekcja | ~60 |
| `core/gui/iso/MapBrowser.java` | JCEF + custom scheme | ~150 |
| `core/gui/iso/MapResourceHandler.java` | Serwowanie tiles/HTML | ~80 |
| `src/main/resources/web/map.html` | Leaflet HTML | ~80 |
| `src/main/resources/web/leaflet.js` | Leaflet (bundled) | — |
| `src/main/resources/web/leaflet.css` | Leaflet (bundled) | — |
| `src/main/resources/v26_1/textures/block/*.png` | Tekstury bloków 26.1 | ~800 plików |
| `src/main/resources/v26_2/textures/block/*.png` | Tekstury bloków 26.2 | ~800 plików |
| **Razem kod** | | **~1350** |

## 5. Fazy implementacji

### Faza 1: Spike (1 tydzień)
Cel: udowodnij że działa end-to-end z minimalnym zakresem.

1. Ekstrakcja ~50 tekstur z `client.jar` dla 26.1 (ręcznie, nie build script)
2. `TextureLoader` + `BlockTextureResolver` (konwencja nazw)
3. `IsometricSpriteRenderer` — tylko pełne kostki, 1 kąt (NE)
4. `IsometricChunkRenderer` — render 1 chunka
5. Zapisz PNG na dysk, otwórz w przeglądarce — **bez JCEF**

**Kryterium sukcesu:** 1 chunk z 26.1 zrenderowany izometrycznie z prawdziwymi teksturami,
zapisany jako PNG, wygląda jak Overviewer.

### Faza 2: Tile pyramid + Leaflet (1 tydzień)
Cel: mapa w Leaflet, zoom/pan.

1. `IsometricTileGenerator` — region → tile pyramid
2. `map.html` + Leaflet (statyczny, otwórz w przeglądarce systemowej)
3. NanoHTTPD endpoint dla tiles (tymczasowy, przed JCEF)
4. Połącz z `RegionImageHandler` — re-render gdy chunk się zmienia

**Kryterium sukcesu:** wiele chunków wyświetlanych w Leaflet z zoom/pan, aktualizowane live.

### Faza 3: JCEF + custom scheme (1 tydzień)
Cel: mapa w GUI, nie w zewnętrznej przeglądarce.

1. Dodaj JCEF dependency (`jcefmaven`)
2. `MapBrowser` + `MapResourceHandler` (custom scheme)
3. Osadź w JavaFX przez `SwingNode` (tymczasowo, przed Compose)
4. JSQuery dla player position

**Kryterium sukcesu:** mapa Leaflet w oknie JavaFX, zero zewnętrznej przeglądarki.

### Faza 4: 2 kąty + oświetlenie + directional blocks (1 tydzień)
Cel: pełna funkcjonalność renderowania.

1. `IsoAngle.SW` — mirror sprite + re-projekcja
2. Directional blocks (furnace, crafting_table...) — block state `facing`
3. Lighting rotuje z kątem
4. Settings: wybór NE/SW → re-render tiles

**Kryterium sukcesu:** 2 kąty patrzenia, furnace pokazuje front z odpowiedniej strony.

### Faza 5: Compose Desktop (opcjonalnie, osobny projekt)
Cel: zamiana JavaFX na Compose.

1. Gradle zamiast Maven
2. Compose Desktop + `SwingPanel` dla JCEF
3. Przepisanie Settings/Auth/Realms z FXML na Compose
4. Material 3 + animacje

**Kryterium sukcesu:** GUI w Compose, mapa w JCEF, backend Java bez zmian.

## 6. Szacunek

| Komponent | Linie | Trudność | Czas |
|---|---|---|---|
| `TextureLoader` | ~100 | łatwe | 1-2 dni |
| `BlockTextureResolver` | ~80 | łatwe | 1 dzień |
| `IsometricSpriteRenderer` | ~300 | średnie | 2-3 dni |
| `IsometricChunkRenderer` | ~380 | średnie | 2-3 dni |
| `IsoAngle` + projekcja | ~60 | łatwe | 0.5 dnia |
| Lighting | ~50 | trywialne | 0.5 dnia |
| `IsometricTileGenerator` | ~200 | łatwe | 1-2 dni |
| `map.html` + Leaflet | ~80 | trywialne | 0.5 dnia |
| `MapBrowser` + JCEF | ~150 | średnie | 2-3 dni |
| `MapResourceHandler` | ~80 | średnie | 1 dzień |
| Integracja z istniejącym | ~200 | średnie | 2-3 dni |
| **Razem** | **~1680** | | **~2-4 tygodnie** |

**Tekstury w jarze:** +~10MB (2 wersje × ~5MB).
**JCEF natywne:** +~100MB per arch (Chromium bundled).

## 7. Ryzyka

### 7.1 Kształty bloków (średnie)
Na start wszystko jako pełne kostki. Schody/słaby/płoty wyglądają jak kostki z teksturą.
**Mitigacja:** dodawaj specjalne przypadki stopniowo (~20 bloków pokrywa 90% przypadków).

### 7.2 Wydajność renderowania (średnie)
Izometryczny sprite per blok jest większy niż top-down piksel. Region 32×32 chunki =
16384 bloków × sprite 32×48px = duży obraz.
**Mitigacja:** cache'uj sprite per block type, renderuj asynchronicznie (jak dziś
`RegionImageHandler` na `ScheduledExecutorService`).

### 7.3 JCEF na macOS (niskie)
JCEF binaries nie są notarized przez Apple.
**Mitigacja:** `jcefmaven` pobiera binaries przy pierwszym uruchomieniu, użytkownik
musi potwierdzić "Open anyway" w System Preferences. Albo notarize samemu.

### 7.4 Tekstury per wersja (niskie)
Każda nowa wersja MC = nowy zestaw tekstur do bundlowania.
**Mitigacja:** build script automatyzuje ekstrakcję z `client.jar`. ~5MB per wersja
jest akceptowalne.

### 7.5 Leaflet w JCEF (niskie)
Leaflet działa w pełnym Chromium (JCEF), nie w JavaFX WebView.
**Mitigacja:** JCEF = Chromium, nie stary WebKit fork. Leaflet działa natywnie.

## 8. Otwarte pytania

1. **Compose vs JavaFX先**: czy faza 1-4 robi się na JavaFX (z `SwingNode` dla JCEF),
   czy od razu na Compose? Compose wymaga migracji Maven→Gradle.
2. **Tekstury — bundlować czy pobierać**: bundlowanie +10MB w jarze vs pobieranie
   `client.jar` przy pierwszym uruchomieniu (~30MB download).
3. **Cache tiles — dysk vs pamięć**: tiles na dysku w `.map/` (jak dziś `image-cache/`)
   vs w pamięci z LRU cache. Dysk jest prostszy, pamięć szybsza.
4. **Player marker — JSQuery vs polling**: JSQuery (push z Java) vs polling
   (`fetch('http://worlddl/api/state')` co 500ms). JSQuery jest czystsze ale wymaga
   więcej kodu JCEF.
5. **Zachowanie top-down**: czy zostawić stary top-down tryb jako opcja, czy całkowicie
   zastąpić izometrycznym?
