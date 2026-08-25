# world-downloader-proxy

A Minecraft world downloader that works as a proxy server between the Minecraft client and server, reading and saving chunk data as you walk around the world. Downloaded chunks can also be sent back to the client to extend render distance.



### Downloads

Grab the latest jar for your platform from the [Releases](../../releases/latest) page.

| Jar | Platforms |
|-----|-----------|
| `world-downloader-proxy-<version>-win-linux-mac-x86_64.jar` | Windows, Linux, macOS (x86_64) |
| `world-downloader-proxy-<version>-linux-mac-arm64.jar` | Linux, macOS (ARM64/Apple Silicon) |

> Windows ARM users: use the x86_64 jar under Windows x86 emulation. OpenJFX does not publish Windows ARM64 native libraries.

### Basic usage

Run the jar, enter the server address in the address field, and press start.

<img src="https://i.imgur.com/yH8SH5C.png">

Instead of connecting to the server directly, connect to `localhost` in Minecraft to start downloading the world.

<img src="https://i.imgur.com/wKMnXfq.png">

### Features

- Requires no client modifications — works with every vanilla or modded client
- Automatically merge into previous downloads or existing worlds
- Save chests and other inventories by opening them
- Extend the client's render distance by sending previously downloaded chunks back to the client
- Supports Minecraft 26.1+'s reorganised world storage (dimensions under `dimensions/<namespace>/<name>`, world gen settings in their own file) — saved worlds open normally without manual fixing
- Overview map of saved chunks:

<img src="https://i.imgur.com/7FIJ6fZ.png" width="80%" title="Example of the GUI showing all the downloaded chunks as white squares, which ones from a previous download greyed out.">

### Requirements

- Java 25 or higher
- Minecraft 1.12.2+ / 1.13.2+ / 1.14.1+ / 1.15.2+ / 1.16.2+ / 1.17+ / 1.18+ / 1.19.3+ / 1.20+ / 1.21+ / 26.1+ / 26.2+
- For Minecraft **26.1+** servers: a **Java 25+** JDK must be installed somewhere on your system. Minecraft's `server.jar` is used to generate that version's block/item data; the downloader detects and uses it automatically on first connect.

### Command-line

```
java -jar world-downloader-proxy-<version>-linux-mac-arm64.jar
```

Run with `--help` to see all available options:

```
java -jar world-downloader-proxy-<version>-linux-mac-arm64.jar --help
```

Disable the GUI and specify the server address directly:

```
java -jar world-downloader-proxy-<version>-linux-mac-arm64.jar --no-gui -s address.to.server.com
```

### Running on Linux

```bash
wget <release-url-from-Releases-page>
java -jar world-downloader-proxy-<version>-linux-mac-arm64.jar -s address.to.server.com
```

Headless mode (no GUI):

```bash
java -jar world-downloader-proxy-<version>-linux-mac-arm64.jar -s address.to.server.com --no-gui
```

Some Linux distributions require `-Djdk.gtk.version=2` for the GUI to work:

```bash
java -Djdk.gtk.version=2 -jar world-downloader-proxy-<version>-linux-mac-arm64.jar
```

### Building from source

<details>
  <summary>Dependencies on Linux</summary>

  **Debian/Ubuntu:**
  ```
  sudo apt-get install default-jdk maven
  ```

  **Arch/Manjaro:**
  ```
  sudo pacman -S --needed jdk-openjdk maven
  ```
</details>

<details>
  <summary>Build executable jar</summary>

  The project uses two Maven profiles, one per CPU architecture, to bundle the correct JavaFX native libraries:

  ```bash
  git clone https://github.com/XInfiniterX/world-downloader-proxy
  cd world-downloader-proxy

  # ARM64 (default profile): Linux + macOS ARM64
  mvn clean package

  # x86_64: Windows + Linux + macOS x86_64
  mvn clean package -Px86_64
  ```

  To produce both jars without wiping the first one, build sequentially without `clean` on the second run:

  ```bash
  mvn clean package -Px86_64
  mvn package
  ```

  Output jars land in `target/`:
  ```
  world-downloader-proxy-<version>-win-linux-mac-x86_64.jar
  world-downloader-proxy-<version>-linux-mac-arm64.jar
  ```

</details>

### License

GPL-3.0. See [LICENSE](LICENSE).

---

Based on [minecraft-world-downloader](https://github.com/mircokroon/minecraft-world-downloader) by [mircokroon](https://github.com/mircokroon).
