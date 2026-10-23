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
- 🔌 **API** for other mods to add their own routing (radios, global channels, custom mutes).

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

## For developers

Other mods can hook into voice routing:

```java
CozyVoiceApi.registerRouteHook(new VoiceRouteHook() {
    @Override
    public boolean isMuted(MinecraftServer server, ServerPlayer player) {
        return MyMutes.isMuted(player);
    }

    @Override
    public void routeExtra(MinecraftServer server, ServerPlayer sender, float level,
                           boolean selfMonitor, Set<UUID> heardLive, Sink sink) {
        // e.g. a radio: sink.send(listenerId, CozyVoiceProtocol.CAT_RADIO, 1f);
    }
});
```

On the client, `VoicePlayback.levelOf(uuid)` returns how loud a player is speaking (0 to 1).

## Building

```bash
./gradlew build
```

The output goes to `build/libs/`.

## Localization

Language files are in `src/main/resources/assets/cozyvoicechat/lang/` (`en_us.json`, `ru_ru.json`, `uk_ua.json`). Pull requests with new translations are welcome.

## Credits

- **Author:** ElgerElg
- **Opus codec:** [Concentus](https://github.com/lostromb/concentus) by Logan Stromberg

## License

All Rights Reserved.