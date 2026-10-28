# Cozy Voice Chat

**Lightweight proximity voice chat for Minecraft Forge 1.20.1.**
3D positional voice over its own UDP relay, with no external voice servers and no native libraries.

> 🇷🇺 Лёгкий голосовой чат по расстоянию для Forge 1.20.1. Нужен и на сервере, и на клиенте. Интерфейс на русском языке.
> 🇺🇦 Легкий голосовий чат за відстанню для Forge 1.20.1. Потрібен і на сервері, і на клієнті. Інтерфейс українською мовою.

---

## Features

- 🎙️ **Proximity voice:** 3D positional audio that fades with distance.
- 🔘 **Push-to-talk or voice activation**, with an adjustable activation threshold.
- 🔇 **Noise suppression**, microphone gain and a mic test (hear yourself).
- 🎚️ **Per-player volume:** turn anyone up, down or off.
- 🙊 **Mute and deafen** hotkeys, with voice icons above players' heads.
- 📻 **Radio and echo effects**, applied per player by admins.
- 🧱 **Voice-blocking blocks:** choose which blocks stop sound.
- 🛠️ **Admin command** `/cozyvoice` with debug info, mutes, effects and config reload.
- 🌍 **Languages:** English, Русский, Українська.
- ☕ **Opus codec** via [Concentus](https://github.com/lostromb/concentus), pure Java and bundled in the jar.
- 🔌 **Addon API:** events, routing hooks, voice groups, custom audio channels and client integration. See [API.md](API.md).

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.3.0+ |
| Java | 17 |
| Side | **Both:** server and client |

## Installation

1. Put `cozyvoicechat-<version>.jar` into the `mods` folder on **the server and every client**.
2. On the server, open **UDP port 24455** in the firewall or hosting panel.
3. Join the server. The client gets the voice port and address automatically.

> Use the main `cozyvoicechat-<version>.jar`. The `-slim.jar` has no bundled codec and isn't meant for players.

## Controls

| Key | Action |
|---|---|
| `V` | Voice chat settings |
| `Caps Lock` | Push to talk |
| *(unbound)* | Mute microphone, deafen, per-player volume |

All keys can be changed in **Options → Controls → Cozy Voice Chat**.

## Configuration

| File | What's inside |
|---|---|
| `config/cozyvoicechat-server.json` | Ports, bind address, public host, bitrate, voice range, timeouts |
| `config/cozyvoicechat-client.json` | Audio devices, PTT/VAD mode, gain, per-player volumes |

If the server sits behind NAT or a proxy, set `publicHost` to the address players connect to.

## Commands

All commands require **OP level 2**.

| Command | Description |
|---|---|
| `/cozyvoice debug` | UDP relay status and active sessions |
| `/cozyvoice reload` | Reload the server config |
| `/cozyvoice effect <players> <radio\|echo> <true\|false>` | Toggle a voice effect |
| `/cozyvoice hearself <players> <true\|false>` | Let players hear themselves |
| `/cozyvoice mute <players> [seconds]` | Mute players (until restart, or for N seconds) |
| `/cozyvoice unmute <players>` | Unmute players |
| `/cozyvoice mutelist` | List muted players |
| `/cozyvoice passthrough filteradd\|filterrem <block>` | Add or remove a voice-blocking block |
| `/cozyvoice passthrough filterlist` | List voice-blocking blocks |

## Addon API

Cozy Voice Chat has a public, documented API for addons: server and client events, mutes and effects, voice groups,
custom audio channels (static, locational and entity-bound), microphone and playback processing, and HUD/settings integration.

```gradle
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.ElgerElg.CozyVoiceChat:cozyvoicechat-api:<tag>' }
```

```java
CozyVoiceApi api = CozyVoiceApi.get();
api.server().mute(player.getUUID(), 60);
```

See **[API.md](API.md)** for setup, the full overview and examples.

📖 **JavaDoc:** https://elgerelg.github.io/CozyVoiceChat/ (API reference for every public class, generated from the sources).

## Building

```bash
./gradlew build
```

The output goes to `build/libs/`: `cozyvoicechat-<version>.jar` (the mod), `cozyvoicechat-api-<version>.jar` (API only, for addon developers) and sources jars.

## Localization

Language files are in `src/main/resources/assets/cozyvoicechat/lang/` (`en_us.json`, `ru_ru.json`, `uk_ua.json`). Pull requests with new translations are welcome.

## Credits

- **Author:** ElgerElg
- **Opus codec:** [Concentus](https://github.com/lostromb/concentus) by Logan Stromberg (BSD license), bundled via the `io.github.jaredmdobson:concentus` build

## License

Cozy Voice Chat is released under the [MIT License](LICENSE).