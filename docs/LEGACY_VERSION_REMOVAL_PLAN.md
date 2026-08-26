# Plan: usunięcie wsparcia dla wersji Minecrafta starszych niż 26.x

Status: **draft do realizacji** (checklisty w tym dokumencie są aktualizowane w miarę postępu prac).

## 1. Cel

- Wspierane wersje po zakończeniu prac: **wyłącznie 26.1 i 26.2** (obecnie: 1.12.2 → 26.2, czyli 17 gałęzi
  wersji protokołu).
- Usunąć cały kod, zasoby i testy dotyczące wersji < 26.1, **bez zmiany zachowania dla 26.1/26.2**.
- Zachować architektoniczny "punkt rozszerzenia" na przyszłe wersje (26.3, 27.x, ...), żeby dodanie kolejnej
  wersji w przyszłości nie wymagało ponownego przechodzenia przez cały ten proces czyszczenia.
- Przy okazji: umiarkowana refaktoryzacja zgodna z SOLID w miejscach, gdzie dziś wersjonowanie jest
  rozwiązane przez głębokie łańcuchy dziedziczenia (`Chunk_26_1 extends Chunk_1_20 extends Chunk_1_18 extends
  ... extends Chunk`) — bez szerokiego przeglądu całego projektu poza obszarem wersjonowania protokołu.

## 2. Stan obecny (analiza)

Wersjonowanie jest rozwiązane w projekcie na trzy współistniejące sposoby:

1. **`config.Version`** — enum z parą `(protocolVersion, dataVersion)` dla każdej wspieranej wersji gry
   (`V1_12` … `V26_2`, plus `ANY` jako fallback).
2. **`VersionReporter.select(...)` / `Option.of(Version, Supplier)`** — wzorzec "wybierz pierwszą pasującą
   implementację" używany w ok. 10 fabrykach (`ChunkFactory`, `MetaData`, `PlayerMap`, `EquipmentReader`,
   `DataTypeProvider`, `PluginChannelHandler`, `ClientBoundGamePacketHandler`, `ClientBoundConfigurationPacketHandler`, …).
3. **Łańcuchy dziedziczenia per wersja** — każda klasa wersji dziedziczy po poprzedniej i nadpisuje tylko
   deltę, np.:
   - `Chunk_1_12`, `Chunk_1_13` (dwie gałęzie startowe) → `Chunk_1_14` → `Chunk_1_15` → `Chunk_1_16` →
     `Chunk_1_17` → `Chunk_1_18` → `Chunk_1_20` → `Chunk_26_1` (aktywna gałąź dla 26.x).
   - Analogicznie: `ChunkSection_1_12/1_13` → … → `ChunkSection_26_1`.
   - `DataTypeProvider` → `_1_13` → `_1_14` → `_1_20_2` → `_1_20_6`.
   - `ClientBoundGamePacketHandler` → `_1_14` → `_1_15`/`_1_16` → `_1_17` → `_1_18` → `_1_19` → `_1_20_2` →
     `_1_20_6`.
   - `MetaData` → `_1_12` / `_1_13` → `_1_19_3`.
   - `PlayerMap` → `_1_12` / `_1_14` → `_1_17`.
   - `EquipmentReader` → `_1_13` / `_1_15`.
   - `Slot` → `Slot_1_12` (gałąź *wsteczna*, stary format; `Slot` sam w sobie to już nowszy format).
4. **`RegistryLoader`** ma osobną, twardo zakodowaną obsługę `"1.12.2"` i `"1.13.2"` (te wersje serwera nie
   potrafiły jeszcze generować `reports/blocks.json` / `registries.json`, więc używane są statyczne pliki
   `blocks-1.12.2.json`, `entities-1.12.2.json`, `entities-1.13.2.json`, `items-1.12.2.json`).
5. `LevelData` dobiera `world-gen-settings-1.16.dat` vs `world-gen-settings-1.19.dat` w zależności od wersji.
6. `protocol-versions.json` zawiera wpisy dla wszystkich 17 wersji; `ProtocolVersionHandler.bestMatch()`
   dla nierozpoznanej/starszej wersji dobiera *najbliższą niższą* — po przycięciu pliku do samego 26.x,
   klient starszy niż 26.1 zostanie błędnie dopasowany do protokołu 26.1 zamiast dostać czytelny błąd
   (patrz punkt 6.7 — trzeba dodać jawną blokadę).
7. Testy referencyjne (`ChunkTest`, `ProtocolVersionHandlerTest`) i dane testowe
   (`src/test/resources/chunkdata_1_12` … `chunkdata_1_21`, `chunkdata_26_1`, `chunkdata_26_2`) pokrywają
   wszystkie wersje.

**Ważna obserwacja:** nie każda klasa z "wersją" w nazwie to martwy kod. Część z nich to *aktualnie aktywna*
implementacja dla 26.x, tylko nazwana od wersji, w której dana zmiana została wprowadzona — np.
`BlockLocationEncoder_1_16` jest używany przez `ChunkSection_1_16`, które jest przodkiem `ChunkSection_26_1`
i nadal obowiązuje. Plan **spłaszcza łańcuch do zachowania efektywnego dla 26.x**, a nie usuwa
bezmyślnie wszystko z liczbą wersji w nazwie.

## 3. Docelowa architektura

### 3.1 Zasada ogólna: spłaszczenie do jednej "bieżącej" implementacji + jawny punkt rozszerzenia

Dla każdego łańcucha (`Chunk_*`, `ChunkSection_*`, `DataTypeProvider_*`, `*PacketHandler_*`, `MetaData_*`,
`PlayerMap_*`, `EquipmentReader_*`):

1. Wyznaczyć efektywne zachowanie dla 26.1/26.2, czyli metody z "liścia" łańcucha (np. `Chunk_26_1`) plus
   wszystko, co po drodze nie zostało nadpisane (odziedziczone z np. `Chunk_1_18`, `Chunk_1_16`, ...).
2. Scalić to w **jedną klasę bazową** (np. `Chunk`, `ChunkSection`, `DataTypeProvider`,
   `ClientBoundGamePacketHandler`, `MetaData`, `PlayerMap`, `EquipmentReader`) — bez sufiksu wersji, bo to
   teraz jedyne wspierane zachowanie.
3. Usunąć wszystkie pośrednie i historyczne klasy (`_1_12`, `_1_13`, ..., `_1_20`) oraz odpowiadające im
   gałęzie w `Option.of(Version.V1_XX, ...)`.
4. Zostawić w bazowej klasie **te same punkty rozszerzenia (protected/abstract metody), które już dziś
   istnieją i są używane do różnicowania wersji** (np. `parseHeightMaps`, `writeHeightMaps`,
   `createNewChunkSection`, `parseSection`, `readChunkColumn`). Dzięki temu w przyszłości "wersja 26.3" nie
   różni się mechanizmem rozszerzania od tego, co jest w kodzie dziś — po prostu znowu powstanie jedna
   podklasa nadpisująca deltę, zamiast N-tej warstwy w wielopoziomowym łańcuchu dziedziczenia po
   nieaktualnej już historii.
5. Tam, gdzie różnica między wersjami była tylko pojedynczą flagą/boolem (nie osobną klasą), stosować już
   istniejący wzorzec `PacketFormat` (klasa z metodami typu `particlePositionIsDouble()`) zamiast tworzyć
   nową klasę-wariant. `PacketFormat` zostaje, ale jego flagi historyczne (`isAtLeast(V1_15)` itd.) zostają
   uproszczone do stałych `true`, bo w wspieranym zakresie (26.x) są zawsze prawdziwe — a metody zostają,
   żeby przyszła wersja mogła ponownie wprowadzić warunek.

To jest **wybrany wariant "umiarkowany"**: nie przechodzimy na pełny wzorzec Strategy z osobnym rejestrem
"version deltas" (nadmiarowe przy jednej wspieranej wersji), ale też nie zostawiamy głębokiego dziedziczenia
przez martwą historię — punkt rozszerzenia zostaje maksymalnie płytki (jedna klasa bazowa, ewentualnie jedna
przyszła podklasa).

### 3.2 SOLID — konkretne zastosowania w tym zakresie

- **SRP**: `RegistryLoader` przestaje mieć gałęzie `if (version.equals("1.12.2"))`; jego odpowiedzialność
  zwęża się do "generuj/wczytaj raporty z server.jar", bez specjalnych przypadków historycznych.
- **OCP**: nowy punkt rozszerzenia (patrz 3.1.4) pozwala dodać wersję 26.3 przez nową podklasę +```Option```,
  bez modyfikowania spłaszczonej klasy bazowej.
- **LSP**: usunięcie długich łańcuchów, w których podklasa czasem subtelnie psuła założenia rodzica (ryzyko
  częste w dziedziczeniu wieloetapowym), redukuje powierzchnię błędów LSP.
- **ISP/DIP**: `VersionReporter`/`PacketFormat` jako wstrzykiwana zależność zostaje bez zmian — to już dziś
  dobry przykład DIP w projekcie i będzie wzorcem do naśladowania przy przyszłych wersjach.
- Zakres NIE obejmuje pełnego audytu SOLID całego projektu (np. `Config` jako "boski obiekt" statyczny) —
  to świadomie poza zakresem tej sesji czyszczenia, chyba że pojawi się bezpośrednio na drodze
  (np. `RegistryLoader`).

## 4. Szczegółowa inwentaryzacja (co usunąć / spłaszczyć / zachować)

### 4.1 Do usunięcia całkowicie (klasy)

| Obszar | Pliki |
|---|---|
| Chunk | `Chunk_1_12`, `Chunk_1_13`, `Chunk_1_14`, `Chunk_1_15`, `Chunk_1_16`, `Chunk_1_17`, `Chunk_1_18`, `Chunk_1_20` |
| ChunkSection | `ChunkSection_1_12`, `ChunkSection_1_13`, `ChunkSection_1_14`, `ChunkSection_1_15`, `ChunkSection_1_16`, `ChunkSection_1_18` |
| DataTypeProvider | `DataTypeProvider_1_13`, `DataTypeProvider_1_14`, `DataTypeProvider_1_20_2`, `DataTypeProvider_1_20_6` |
| Packet handlers (Game) | `ClientBoundGamePacketHandler_1_14/_1_15/_1_16/_1_17/_1_18/_1_19/_1_20_2/_1_20_6` |
| Packet handlers (Configuration) | `ClientBoundConfigurationPacketHandler_1_20_2`, `_1_20_6` |
| MetaData | `MetaData_1_12`, `MetaData_1_13` (behawior `MetaData_1_19_3` staje się bazą) |
| PlayerMap | `PlayerMap_1_12`, `PlayerMap_1_14` (behawior `PlayerMap_1_17` staje się bazą) |
| EquipmentReader | `EquipmentReader_1_13` (behawior `EquipmentReader_1_15` staje się bazą) |
| Slot | `Slot_1_12` (to gałąź *starszego* formatu, ślepy zaułek dla wersji < 1.13) |
| Forge/modded 1.12 | `PluginChannelHandler1_12`, `game/data/registries/modded/ForgeRegistryHandler` (Forge na 1.12.2 — sprawdzić, czy warto zachować jako no-op vs usunąć całkowicie, patrz 4.4) |

Po spłaszczeniu klasy "liście" (np. `Chunk_26_1`, `ChunkSection_26_1`, `DataTypeProvider_1_20_6`,
`ClientBoundGamePacketHandler_1_20_6`, `MetaData_1_19_3`, `PlayerMap_1_17`, `EquipmentReader_1_15`) **znikają
jako osobne pliki**, a ich zawartość trafia do klasy bazowej (patrz 3.1).

Wyjątek do zachowania mimo nazwy z numerem wersji: `game/data/chunk/version/encoder/BlockLocationEncoder_1_16`
— to aktywny algorytm dla 26.x, nie legacy. Do rozważenia: zostawić jako jest, albo (opcjonalnie, przy
refaktorze) przenieść/zmienić nazwę na neutralną (`BlockLocationEncoder` domyślny), skoro po usunięciu
starszych wersji nie ma już z czym go kontrastować. Decyzja: **zmienić nazwę pliku/klasy** żeby nie sugerować
fałszywie "kod tylko dla 1.16", np. scalić bezpośrednio jako domyślną implementację `BlockLocationEncoder`
(usuwając potrzebę wyboru wariantu, bo zostaje tylko jeden).

### 4.2 Do przycięcia (enum / dane, nie całe pliki)

- `config.Version`: usunąć `V1_12` … `V1_21_4`, zostawić `V26_1`, `V26_2`, `ANY`.
- `src/main/resources/protocol-versions.json`: usunąć wpisy dla wszystkich wersji poza 26.1/26.2.
- `config.Config.DEFAULT_VERSION`: dziś `340` (1.12.2) — zmienić na `Version.V26_2.protocolVersion` (albo
  `V26_1`), żeby domyślny stan przed handshakem też odzwierciedlał wspierany zakres.

### 4.3 Zasoby do usunięcia

- `src/main/resources/blocks-1.12.2.json`
- `src/main/resources/entities-1.12.2.json`
- `src/main/resources/entities-1.13.2.json`
- `src/main/resources/items-1.12.2.json`
- `src/main/resources/world-gen-settings-1.16.dat` (zostaje tylko `world-gen-settings-1.19.dat`, używany
  bezwarunkowo)
- Testowe: `src/test/resources/chunkdata_1_12`, `_1_13`, `_1_14`, `_1_15`, `_1_16`, `_1_17`, `_1_19`, `_1_21`
  (zostają `chunkdata_26_1`, `chunkdata_26_2`)

### 4.4 `RegistryLoader` / rejestry — uproszczenie

- Usunąć `hasExistingReports()` specjalny przypadek `version.equals("1.12.2")`.
- Usunąć gałęzie `version.equals("1.12.2")` / `"1.13.2"` w `generateEntityNames()`, `generateGlobalPalette()`,
  `generateItemRegistry()`.
- Usunąć `versionSupportsBlockGenerator()` / `versionSupportsGenerators()` (zawsze `true` dla 26.x) —
  albo zostawić jako no-op `return true`, jeśli chcemy zachować nazwę metody jako przyszły punkt
  rozszerzenia (np. gdyby 27.x znów zmieniło format generatora). **Rekomendacja: usunąć metody i wywołania
  bezpośrednio** — nie ma dziś przesłanek, że przyszłe wersje wrócą do generowania bez server.jar; jeśli się
  to zmieni, prościej będzie dodać nowy warunek niż utrzymywać dwie zawsze-`true` metody.
- `ForgeRegistryHandler`/`PluginChannelHandler1_12` obsługują Forge **tylko na 1.12.2**. Do potwierdzenia z
  właścicielem projektu: czy nowoczesny Forge/NeoForge na 26.x używa innego kanału pluginów (wtedy ten kod
  i tak nie działa na współczesnych serwerach modowanych i można go bezpiecznie usunąć), czy jest go warto
  zachować jako punkt wyjścia do przyszłej obsługi modów. **Domyślna rekomendacja w tym planie: usunąć**,
  ponieważ kod jawnie dotyczy tylko protokołu 1.12.2 (`Option.of(Version.V1_12, ...)`) i nie ma śladu
  wsparcia dla nowszych wersji Forge.

### 4.5 Inne miejsca z rozgałęzieniami do sprowadzenia do jednej ścieżki (bez usuwania całych klas)

Na podstawie przeglądu (do zweryfikowania grepem `Version.V1_` / `isAtLeast` w danym pliku tuż przed
edycją, bo repo może się zmienić między napisaniem planu a wykonaniem):

- `packets/DataTypeProvider.java` — `ofPacket()` wybór wariantu.
- `packets/handler/ClientBoundGamePacketHandler.java`, `ClientBoundLoginPacketHandler.java`,
  `ClientBoundConfigurationPacketHandler.java`, `ServerBoundGamePacketHandler.java`,
  `ServerBoundLoginPacketHandler.java`.
- `packets/handler/plugins/PluginChannelHandler.java`.
- `packets/builder/PacketBuilder.java` — kilka `isAtLeast`.
- `game/protocol/ConfigurationProtocol.java`.
- `game/data/LevelData.java` — wybór `world-gen-settings-*.dat` (patrz 4.3) oraz komentarze/gałęzie
  "1.12.2 superflat format" (sprawdzić, czy to nadal jedyny format, czy jest wersja alternatywna do
  usunięcia).
- `game/data/RenderDistanceExtender.java`.
- `game/data/dimension/Dimension.java`.
- `game/data/entity/{Entity,MobEntity,ObjectEntity,PrimitiveEntity}.java`,
  `game/data/entity/specific/ItemFrame.java`.
- `game/data/villagers/VillagerManager.java`.
- `proxy/EncryptionManager.java`.
- `schematic/ParticleRegistry.java`.
- `config/PacketFormat.java` — uprościć metody, patrz 3.1.5.

Dla każdego z tych plików: znaleźć gałąź `if (isAtLeast(V26_1)) {...} else {...}` (lub odwrotnie) i zostawić
tylko gałąź obowiązującą dla 26.x, usuwając martwą gałąź "else" dla starszych wersji — o ile analiza
potwierdzi, że warunek faktycznie zawsze rozstrzyga się w tę samą stronę po przycięciu `Version`.

### 4.6 Testy

- Usunąć/przepisać `ChunkTest.java` (dziś parametryzowany po `Version.V1_12` … `V1_21` — sprawdzić dokładne
  wersje w kodzie w momencie wykonania) tak, by testował tylko 26.1/26.2, korzystając z
  `chunkdata_26_1`/`chunkdata_26_2`.
- `ProtocolVersionHandlerTest.java` — dopasować do przyciętego `protocol-versions.json`, dodać test na
  "wersja poniżej 26.1 → jawny błąd/odrzucenie", nie ciche dopasowanie do 26.1.
- `PaletteTransformerTest.java` — zaktualizować import/nazwę `BlockLocationEncoder_1_16` jeśli klasa
  zostanie przemianowana (patrz 4.1).
- Pozostałe testy (`LpVec3Test`, `PacketBuilderAndParserTest`, `schematic/*`) nie są wersjo-zależne —
  bez zmian, ale muszą dalej przechodzić.

### 4.7 Twarda blokada starszych klientów (nowe zachowanie, nie tylko usunięcie)

Dziś `ServerBoundHandshakePacketHandler` wywołuje `Config.setProtocolVersion(protocolVersion)` bezwarunkowo.
Po przycięciu `protocol-versions.json` do 26.x, klient np. 1.20 zostanie przez
`ProtocolVersionHandler.bestMatch()` po cichu dopasowany do protokołu 26.1 (najniższy dostępny wpis) zamiast
dostać czytelny komunikat — to realne ryzyko regresji UX (ciche, mylące błędy parsowania zamiast jasnego
komunikatu). Trzeba dodać jawne sprawdzenie: jeśli `protocolVersion < Version.V26_1.protocolVersion`,
zgłosić `UnsupportedMinecraftVersionException` (klasa już istnieje) i pokazać w GUI/konsoli komunikat typu
"Ta wersja proxy wspiera tylko Minecraft 26.1+. Połącz się klientem 26.1 lub nowszym.", zamiast próbować
kontynuować. To nie jest tylko "sprzątanie", tylko świadome, nowe zabezpieczenie — wymienione osobno, żeby
review wiedział, że to zmiana zachowania (na plus), a nie przeoczenie.

## 5. Kolejność wykonania (fazy, każda kończy się `mvn test -DskipTests=false` + commit)

0. **Baseline**: upewnić się, że `mvn test -DskipTests=false` przechodzi na starcie (punkt odniesienia przed
   jakimikolwiek zmianami). Pracować na osobnych, małych commitach per faza, żeby łatwo było zbisekować
   regresję.
1. **Warstwa danych/enum**: zablokować stare wersje na wejściu (4.7) — to najbezpieczniej zrobić najpierw,
   bo nie usuwa jeszcze kodu, tylko dodaje strażnika. Napisać test na tę blokadę.
2. **Proste fabryki 1:1** (najniższe ryzyko, mało kodu): `EquipmentReader`, `Slot`/`Slot_1_12`, `PlayerMap`,
   `MetaData`. Każda: spłaszczyć do zachowania "liścia", usunąć stare klasy i gałęzie `Option.of`.
3. **`DataTypeProvider`**: spłaszczyć łańcuch `_1_13→_1_14→_1_20_2→_1_20_6` do jednej klasy. Wysokie pokrycie
   testami pośrednio przez `ChunkTest`/`PacketBuilderAndParserTest` — uruchamiać je często w trakcie.
4. **`ChunkSection_*` i `Chunk_*`**: najbardziej ryzykowna i największa faza. Zalecane podejście: pracować
   metoda-po-metodzie od `ChunkSection_26_1`/`Chunk_26_1` w dół, kopiując do nowej płaskiej klasy i
   każdorazowo odpalając `ChunkTest` z danymi `chunkdata_26_1`/`chunkdata_26_2`. Zachować
   `BlockLocationEncoder` (przemianowany, patrz 4.1).
5. **Packet handlery** (`ClientBoundGamePacketHandler_*`, `ClientBoundConfigurationPacketHandler_*`) —
   spłaszczyć analogicznie do fazy 4, ale mniejsze/łatwiejsze pliki.
6. **Rozproszone `isAtLeast`/`Version.V1_*`** w plikach z 4.5 — przejść listę, usunąć martwe gałęzie.
7. **`RegistryLoader` + zasoby 1.12.2/1.13.2 + Forge 1.12** (4.4) — po tym momencie żaden plik główny nie
   powinien już odwoływać się do `Version.V1_*`.
8. **Przycięcie `config.Version` i `protocol-versions.json`** (4.2) — dopiero teraz, bo wcześniej kompilacja
   by się wysypała przy każdym pozostałym odwołaniu. Skompilować i grepem potwierdzić zero odwołań do
   usuniętych stałych.
9. **Usunięcie starych zasobów testowych** (4.3, 4.6), aktualizacja `ChunkTest`/`ProtocolVersionHandlerTest`.
10. **Dokumentacja**: zaktualizować `README.md` (sekcja "Requirements" dziś reklamuje 1.12.2+ jako feature —
    zmienić na "Minecraft 26.1+ only"), `config/Config` help text jeśli wspomina stare wersje.
11. **Finalny przegląd**: pełne `mvn clean package` + `mvn test -DskipTests=false`, ręczny grep
    `grep -rn "1\.1[0-9]\|1\.2[0-4]" src/main` żeby złapać przeoczone wzmianki, manualny smoke-test (poza
    zakresem automatycznym w tym środowisku — wymaga realnego klienta/serwera 26.x, do wykonania przez
    właściciela projektu).

## 6. Jak dodać kolejną wersję (26.3 / 27.x) po tym refaktorze

Krótka instrukcja "on-boarding", którą warto potem przenieść też do `README`/`CONTRIBUTING`:

1. Dodać `Version.V26_3(protocolVersion, dataVersion)` do enuma i wpis w `protocol-versions.json`.
2. Jeśli różnica dotyczy pojedynczego pola/flagi: dodać metodę do `PacketFormat` z realnym warunkiem
   (`isAtLeast(V26_3)`), zamiast tworzyć nową klasę.
3. Jeśli różnica dotyczy formatu chunka/pakietu jako całości: utworzyć **jedną** nową podklasę
   (`Chunk_26_3 extends Chunk`, itd.) nadpisującą tylko zmienione metody, dodać wpis w
   `Option.of(Version.V26_3, ...)` na początku listy w odpowiedniej fabryce. Nie modyfikować klasy bazowej
   (poza ew. wydzieleniem nowego punktu rozszerzenia, jeśli zmiana nie pasuje do istniejących metod
   `protected`).
4. Dodać dane testowe `chunkdata_26_3` + rozszerzyć `ChunkTest`.
5. Zaktualizować `README.md` (lista wspieranych wersji, wymagania Java jeśli się zmieniły).

## 7. Ryzyka i mitygacja

- **Brak możliwości automatycznego testu end-to-end z prawdziwym klientem/serwerem 26.x w tym środowisku** —
  jedyna weryfikacja to testy jednostkowe na nagranych danych pakietów (`chunkdata_26_1/26_2`) + statyczna
  analiza. Rekomendacja: po zakończeniu refaktoru właściciel projektu powinien ręcznie przetestować
  połączenie z realnym serwerem 26.1 i 26.2 przed wydaniem.
- **Spłaszczanie łańcuchów dziedziczenia to najbardziej błędogenna część** — mitygacja: robić to metoda po
  metodzie, commitować małymi krokami, uruchamiać testy po każdej metodzie/klasie, nie "hurtowo".
- **`protocol-versions.json` i `bestMatch()`** — bez strażnika z punktu 4.7 przycięcie pliku samo w sobie
  pogorszyłoby UX (ciche błędne dopasowanie zamiast dziś przynajmniej częściowo działającej starszej
  wersji). Dlatego blokada wchodzi jako faza 1, przed jakimkolwiek usuwaniem.
- **Zakres Forge/modded (4.4)** — decyzja "usunąć" jest odwracalna (kod jest w historii gita), ale warto
  potwierdzić z właścicielem repo przed usunięciem na stałe, gdyby ktoś aktywnie używał tego z serwerem
  1.12.2 Forge (co i tak przestaje działać po usunięciu wsparcia dla 1.12.2 jako takiego).

## 8. Checklisty postępu

- [x] Faza 0: baseline testów — 102 testy, wszystkie przechodzą (patrz `AGENTS.md` po instrukcję
      uruchomienia `mvn`/`java` w tym środowisku).
- [x] Faza 1: blokada starszych klientów (4.7) — `Config.setProtocolVersion` odrzuca teraz protokoły
      poniżej `Version.V26_1` przez `UnsupportedMinecraftVersionException` (z komunikatem w konsoli i GUI).
      `DEFAULT_VERSION` zaktualizowany na `V26_2`. Dodano `ConfigTest` pokrywający tę blokadę.
      W ramach tej fazy od razu usunięto testy w `ChunkTest`/`ParticleRegistryTest`, które explicite
      wywoływały `Config.setProtocolVersion` dla wersji < 26.1 (inaczej od razu zaczęłyby czerwienić się
      po dodaniu blokady) oraz odpowiadające im zasoby `chunkdata_1_12..1_21` — to wyprzedzający fragment
      Fazy 9, zrobiony teraz żeby każdy commit zostawiał zielone testy. `testForWithLight`/`chunk_1_17` też
      usunięte przy okazji: się okazało że to martwy kod dla 26.x — od 1.18 światło jest zapisywane wprost
      w pakiecie `LevelChunkWithLight` (patrz `Chunk_1_18#toLightPacket` zwraca `null`), więc oddzielny
      pakiet światła i tak nigdy nie był używany dla 26.x; nie ma potrzeby odtwarzać tego testu.
- [x] Faza 2: EquipmentReader / Slot / PlayerMap / MetaData — spłaszczone do pojedynczych klas
      bazowych (commit `ca2a176`). `Slot_1_12` zostawiony do Fazy 3 (używany przez `readSlot()`
      w starym `DataTypeProvider`).
- [x] Faza 3: DataTypeProvider — łańcuch `_1_13→_1_14→_1_20_2→_1_20_6` i `Slot_1_12` spłaszczone
      do jednej klasy `DataTypeProvider` (commit `6c6e25c`). `ofPacket()` zwraca bezpośrednio
      `new DataTypeProvider(...)`. `readCoordinates`/`readNbtTag`/`readSlot` przyjmują zachowanie
      liścia (1.14+/1.20.2+/1.20.6+). Testy bezpośrednio używające usuniętych podklas
      (`LpVec3Test`, `PacketBuilderAndParserTest`, `SelectionInputInterceptorTest`) przestawione
      na `DataTypeProvider`; przypadki NBT w `PacketBuilderAndParserTest` używają teraz
      `writeNbtDirect()` (zgodne z nowym `readNbtTag()` czytającym bajt typu na początku).
- [x] Faza 4: Chunk / ChunkSection — łańcuchy `Chunk_1_13→…→Chunk_26_1` i
      `ChunkSection_1_13→…→ChunkSection_26_1` spłaszczone do pojedynczych klas `Chunk` i
      `ChunkSection` (zachowanie liścia 26.x). `BlockLocationEncoder_1_16` scalone jako jedyna
      implementacja `BlockLocationEncoder` (stary pre-1.16 multi-long encoder usunięty).
      `ChunkFactory.getVersionedChunk` zwraca bezpośrednio `new Chunk(...)`. `PaletteTransformer`
      używa `ChunkSection.longsRequired`. `Chunk.setWorldHeight` (było `Chunk_1_17.setWorldHeight`)
      wywoływane z `Dimension`/`WorldManager`/`ChunkTest`. `PaletteTransformerTest` używa
      `new BlockLocationEncoder()`. Usunięto 17 plików `*_1_XX`/`*_26_1`/`BlockLocationEncoder_1_16`.
      Martwy kod 26.x usunięty: `readChunkColumn(boolean,BitSet,…)`, `parse2D/3DBiomeData`,
      `parseLights`, `getNbtBiomes`/`setBiomes`/`writeBiomes`/`parseBiomes`, `writeSectionDataBiomes`,
      `writeBitMask`, `buildLightPacket`, standalone `toLightPacket` (1_14/1_17), `readBlockCount`,
      pre-1_16 `resizeBlocksIfRequired`/`setBlockAt`/`write`/`addNbtTags`. Punkty rozszerzenia
      (`parse`, `parseHeightMaps`, `writeHeightMaps`, `readChunkColumn`, `createNewChunkSection`,
      `parseSection`, `toPacket`, `toNbt`, `addLevelNbtTags`, `updateLight`, `getLocationEncoder`,
      `write`) zachowane jako protected/non-final.
- [x] Faza 5: Packet handlery (Game/Configuration) — łańcuchy
      `ClientBoundGamePacketHandler_1_14→…→_1_20_6` i warianty `ClientBoundConfigurationPacketHandler_1_20_2/_1_20_6`
      spłaszczone do pojedynczych klas bazowych. `of()` zwraca bezpośrednio `new XHandler(...)`.
      Efektywne operatory 26.x zarejestrowane w konstruktorze (Login/Respawn z `commonInfo`,
      `LevelChunkWithLight`, `BlockEntityData` z id z rejestru, `SectionBlocksUpdate` z
      `readSectionCoordinates`, `ContainerSetContent` ze stateId, `OpenScreen` z VarInt,
      `PlayerInfoUpdate`, `StartConfiguration`, `RegistryData` w formie per-registry z 1.20.6).
      Usunięto 10 plików `*_1_XX` oraz pusty katalog `packets/handler/version/`. Martwe importy
      handlerów w `PacketBuilder` usunięte.
- [ ] Faza 6: rozproszone `isAtLeast`/`Version.V1_*` (4.5)
- [ ] Faza 7: RegistryLoader + zasoby 1.12.2/1.13.2 + Forge 1.12
- [ ] Faza 8: przycięcie `Version` enum + `protocol-versions.json`
- [ ] Faza 9: zasoby/testy
- [ ] Faza 10: dokumentacja (README, help CLI)
- [ ] Faza 11: finalny przegląd + `mvn clean package`
