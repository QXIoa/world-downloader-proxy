# world-downloader-proxy

A Minecraft world downloader that works as a proxy server between the Minecraft client and server, reading and saving chunk data as you walk around the world. Downloaded chunks can also be sent back to the client to extend render distance.



### Downloads

Grab the latest jar from the [Releases](../../releases/latest) page.

| Jar | Platforms |
|-----|-----------|
| `world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar` | Windows, Linux, macOS (x86_64 **and** ARM64/Apple Silicon) |

A single universal jar covers all platforms. Skiko selects the correct native libraries at runtime based on the host OS and CPU architecture, so there is no need to pick a jar per platform.

### Basic usage

Start Minecraft first, then run the jar, enter the server address in the address field, and press start.

<img src="https://i.imgur.com/5mybgtj.png">

Instead of connecting to the server directly, connect to `localhost` in Minecraft to start downloading the world.

<img src="https://i.imgur.com/wKMnXfq.png">

### Features

- Requires no client modifications — works with every vanilla or modded client
- Automatically merge into previous downloads or existing worlds
- Save chests and other inventories by opening them
- Extend the client's render distance by sending previously downloaded chunks back to the client
- Supports Minecraft 26.1+'s reorganised world storage (dimensions under `dimensions/<namespace>/<name>`, world gen settings in their own file) — saved worlds open normally without manual fixing
- In-game `/world-downloader-proxy` command with subcommands:
  - `area-selection` — toggle selection mode. Left-click sets pos1, right-click sets pos2.
  - `pos1` / `pos2` — set selection corners from chat.
  - `schematic-export` — export the current selection as a **Sponge Schematic v3** (`.schem`) file into the schematics output directory. The selection is cleared after each export so it can't be accidentally duplicated.
  - `fly` — undetectable flight. Your game mode is switched to creative locally and your server-side position is frozen, so the server never sees you move while you fly around freely. Useful for inspecting the world without tripping anti-cheat.
- Overview map of saved chunks:

<img src="https://i.imgur.com/qLVz99m.png" width="80%" title="Example of the GUI showing all the downloaded chunks as white squares, which ones from a previous download greyed out.">

### Requirements

- Java 25 or higher
- Minecraft 26.1+ (26.1 and 26.2 supported)
- For Minecraft **26.1+** servers: a **Java 25+** JDK must be installed somewhere on your system. Minecraft's `server.jar` is used to generate that version's block/item data; the downloader detects and uses it automatically on first connect.

### Command-line

```
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar
```

Run with `--help` to see all available options:

```
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar --help
```

Disable the GUI and specify the server address directly:

```
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar --no-gui -s address.to.server.com
```

Run everything from the command line (no GUI, implies `--no-gui`):

```
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar --cli -s address.to.server.com
```

`--cli` parses all command-line options and starts the proxy headless, so the whole session happens
in the terminal. Passing `--help` (or `-h`) prints the full option list and exits without opening
the GUI.

Allow multiple Minecraft clients to connect to the proxy at the same time:

```
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar --multi -s address.to.server.com
```

By default the proxy only handles one client at a time. With `--multi`, an acceptor thread
spawns a dedicated handler per connection, so several clients can connect simultaneously (for
example different people joining the same server through the proxy). Each connection forwards
independently with its own encryption/compression state, but they all share the single downloaded
world, so only one of them actively drives the download at a time.

### Running on Linux

```bash
wget <release-url-from-Releases-page>
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar -s address.to.server.com
```

Headless mode (no GUI):

```bash
java -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar -s address.to.server.com --no-gui
```

Some Linux distributions require `-Djdk.gtk.version=2` for the GUI to work:

```bash
java -Djdk.gtk.version=2 -jar world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar
```

### Building from source

<details>
  <summary>Dependencies on Linux</summary>

  **Debian/Ubuntu:**
  ```
  sudo apt-get install default-jdk
  ```

  **Arch/Manjaro:**
  ```
  sudo pacman -S --needed jdk-openjdk
  ```
</details>

<details>
  <summary>Build executable jar</summary>

  The project uses Gradle (the wrapper is committed, no local Gradle install required). A single `shadowJar` task produces one universal fat jar containing all six Skiko native runtime variants; Skiko picks the correct one at runtime based on the host OS and CPU arch.

  ```bash
  git clone https://github.com/XInfiniterX/world-downloader-proxy
  cd world-downloader-proxy
  ./gradlew shadowJar
  ```

  Output jar lands in `build/libs/`:
  ```
  world-downloader-proxy-<version>-mac-linux-win-arm-x86.jar
  ```

</details>

### License

GPL-3.0. See [LICENSE](LICENSE).

---

Based on [minecraft-world-downloader](https://github.com/mircokroon/minecraft-world-downloader) by [mircokroon](https://github.com/mircokroon).
