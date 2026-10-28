# Cozy Voice Chat API

A public API for addons to [Cozy Voice Chat](README.md) on Forge 1.20.1.
Everything in the `net.elgerelg.cozyvoicechat.api` package (and its subpackages) is public, documented with Javadoc
and kept backwards-compatible within the same `CozyVoiceApi.API_VERSION`.
Every other package (`impl`, `server`, `client`, ...) is internal and may change in any release.

- **API version:** `1` (`CozyVoiceApi.API_VERSION`)
- **Mod version:** `1.1.0` or newer
- **License:** MIT
- **Javadoc:** https://elgerelg.github.io/CozyVoiceChat/

---

## 1. Setup

### Gradle (ForgeGradle 6)

The API is published through [JitPack](https://jitpack.io/#ElgerElg/CozyVoiceChat). `<tag>` is a release tag of this
repository, e.g. `v1.1.0`.

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Compile against the API only. It has no Minecraft method references, so no fg.deobf is needed.
    compileOnly 'com.github.ElgerElg.CozyVoiceChat:cozyvoicechat-api:<tag>'

    // Optional: run the real mod in your dev client/server.
    runtimeOnly fg.deobf('com.github.ElgerElg.CozyVoiceChat:cozyvoicechat:<tag>')
    // Cozy Voice Chat bundles its Opus codec with jarJar; in dev runs add it explicitly.
    minecraftLibrary 'io.github.jaredmdobson:concentus:1.0.2'
}
```

| Artifact | Coordinates |
|---|---|
| API only (compile against this) | `com.github.ElgerElg.CozyVoiceChat:cozyvoicechat-api:<tag>` |
| Whole mod (for dev runs) | `com.github.ElgerElg.CozyVoiceChat:cozyvoicechat:<tag>` |

Do **not** ship the API jar inside your mod; players install Cozy Voice Chat itself.

### mods.toml

Required dependency:

```toml
[[dependencies.yourmodid]]
modId="cozyvoicechat"
mandatory=true
versionRange="[1.1.0,)"
ordering="AFTER"
side="BOTH"
```

Optional dependency: use `mandatory=false` and check `ModList.get().isLoaded(CozyVoiceApi.MOD_ID)` before touching
any API class (keep the API calls in a separate class, so it is only loaded when the mod is present).

---

## 2. Getting the API

```java
import net.elgerelg.cozyvoicechat.api.CozyVoiceApi;

if (ModList.get().isLoaded(CozyVoiceApi.MOD_ID)) {
    CozyVoiceApi api = CozyVoiceApi.get();          // works at any time, no init-order issues
    ServerVoiceApi server = api.server();           // both sides (on a logical server)
    ClientVoiceApi client = api.client();           // physical client only
}
```

- `CozyVoiceApi.get()` returns the singleton (it throws `IllegalStateException` if only the API jar is present;
  `CozyVoiceApi.isAvailable()` checks this without throwing).
- `api.getApiVersion()` returns the API version of the *installed* mod: compare it with the constant you compiled
  against if you use features from a newer API.
- **Events** are normal Forge events on `MinecraftForge.EVENT_BUS`. Subscribe with `@SubscribeEvent`/
  `@Mod.EventBusSubscriber` as usual.
- Optionally, register your addon with `@CozyVoiceAddon` (see [Addon registration](#addon-registration)) so it is
  listed in the log and in `/cozyvoice addons`.

### Addon registration

Annotate a class that implements `CozyVoicePlugin` with `@CozyVoiceAddon` (package `net.elgerelg.cozyvoicechat.api.addon`).
Cozy Voice Chat finds it automatically, there is nothing to call:

```java
@CozyVoiceAddon
public final class MyVoiceAddon implements CozyVoicePlugin {
    @Override
    public String getId() {
        return "myaddon";                     // shown in logs and /cozyvoice addons
    }

    @Override
    public void onRegister(CozyVoiceApi api) {
        MinecraftForge.EVENT_BUS.register(MyVoiceEvents.class);
        api.server().registerRouteHook(new MyRouteHook());
    }
}
```

How it works:

1. During `FMLCommonSetupEvent` (on the main thread, via `enqueueWork`) the mod reads Forge's annotation scan data of every
   loaded mod file (`ModList.get().getAllScanData()`) and collects the classes annotated with `@CozyVoiceAddon`.
2. Each class is loaded, checked (must implement `CozyVoicePlugin`, be non-abstract and have a public no-arg
   constructor) and instantiated once.
3. `onRegister(api)` is called. If it throws, the error is logged and that addon is skipped; the game keeps running.
   Duplicate ids are skipped with a warning.
4. The log gets a line like `[Cozy Voice] Found 1 addon(s): myaddon (mod mymod 1.0.0)`.
5. `/cozyvoice addons` (permission level 2) and `CozyVoiceApi.get().getAddons()` (a list of `AddonInfo`: `id`,
   `modId`, `modVersion`, `className`) list the registered addons.

Notes: the plugin class is created on both sides, so keep client-only code (rendering, `Minecraft`) out of it or behind a
`FMLEnvironment.dist` check. No server is running during `onRegister`; create groups or audio channels later, e.g. in
`ServerStartedEvent`. Because the class is only loaded when Cozy Voice Chat is present, it is also a convenient place for API
calls when the dependency is optional. Using the annotation is optional.

A complete example addon: [ElgerElg/CozyVoiceChat-SampleAddon](https://github.com/ElgerElg/CozyVoiceChat-SampleAddon).

### Threads

Voice is real-time and most of it runs off the main thread. Every event and method documents its thread:

| What | Thread |
|---|---|
| `VoiceConnectionEvent`, `VoiceRouteEvent`, `VoiceStateChangedEvent` | server thread |
| `MicrophonePacketEvent` | network thread (50×/s per talking player): no world access |
| `ClientMicrophoneEvent` | microphone thread |
| `ClientReceiveAudioEvent` | playback thread |
| other client events | client thread |
| `AudioChannel.send*` | any thread |

---

## 3. Overview

### `api` — entry point
| Type | Purpose |
|---|---|
| `CozyVoiceApi` | `get()`, `isAvailable()`, `API_VERSION`, `MOD_ID`, `server()`, `client()`, Opus `createEncoder()/createDecoder()`, `getAddons()` |
| `VoiceRouteHook` | low-level routing hook (mutes, spectators, exclusive or extra routes) |
| `VoiceCategory` | audio categories: `PROXIMITY`, `GLOBAL`, `RADIO`, `CCTV`; 4–15 free for addons |

### `api.server` — `CozyVoiceApi.get().server()`
| Area | Methods |
|---|---|
| Status | `isRunning`, `isConnected`, `getConnectedPlayers`, `isTalking`, `getVoiceRange`, `getFadeStart`, `getBitrate` |
| Mutes / state | `mute(uuid, seconds)`, `unmute`, `isMuted`, `getMuteRemainingSeconds`, `isMicMuted`, `isDeafened` |
| Effects | `setEffect(uuid, VoiceEffect.RADIO/ECHO, bool)`, `hasEffect`, `getEffects`, `setHearSelf`, `isHearingSelf` |
| Audio channels | `createStaticChannel`, `createLocationalChannel`, `createEntityChannel`, `createAudioPlayer` |
| Groups | `createGroup(name, exclusive)`, `getGroup`, `getGroupOf`, `getGroups` |
| Routing | `registerRouteHook`, `unregisterRouteHook` |

### `api.client` — `CozyVoiceApi.get().client()`
`getStatus`, `isConnected`, `isTalking(uuid)`, `getAudioLevel(uuid)`, `getTalkingPlayers`, `isLocalTalking`,
`getLocalMicLevel`, `isMicMuted`/`setMicMuted`, `isDeafened`/`setDeafened`, `isPlayerMicMuted`, `isPlayerDeafened`,
`getPlayerVolume`/`setPlayerVolume`, `getMasterVolume`, `isPushToTalkMode`, `isPushToTalkPressed`, `getVoiceRange`,
`openSettingsScreen`, `openPlayerVolumeScreen`.

### `api.event` — server events
| Event | When | Cancelable |
|---|---|---|
| `VoiceConnectionEvent.Connected` / `.Disconnected` (with `Reason`) | a player's voice session opens / closes | no |
| `MicrophonePacketEvent` | every received voice frame; read/replace the Opus data, edit recipients | yes (drop frame) |
| `VoiceRouteEvent` | the listener list for a talking player is (re)computed, every ~2 ticks | yes (nobody hears) |
| `VoiceMuteChangedEvent` | a player was muted/unmuted by a command or the API | no |
| `VoiceStateChangedEvent` | a player toggled their own mic mute / deafen | no |

### `api.event.client` — client events
| Event | When | Cancelable |
|---|---|---|
| `ClientMicrophoneEvent` | local mic frame before encoding; modify PCM in place | yes (don't send) |
| `ClientReceiveAudioEvent` | incoming frame per speaker/channel before mixing; modify PCM in place | yes (skip) |
| `ClientTalkingStateEvent` | a player (or the local player) started/stopped talking | no |
| `ClientVoiceConnectionEvent` | connection status changed | no |
| `ClientLocalVoiceStateEvent` | local mic mute / deafen changed | no |
| `ClientVoiceSettingsChangedEvent` | voice settings were saved | no |

### `api.addon`
`@CozyVoiceAddon`, `CozyVoicePlugin` (`getId()`, `onRegister(CozyVoiceApi)`), `AddonInfo`; see
[Addon registration](#addon-registration).

### `api.audio`
`VoiceAudioFormat` (48 kHz, mono, 960-sample/20 ms frames), `VoiceEncoder`, `VoiceDecoder` (Opus, from
`CozyVoiceApi.get().createEncoder()/createDecoder()`).

---

## 4. Examples

### Mute hook: silence players in a jail

```java
CozyVoiceApi.registerRouteHook(new VoiceRouteHook() {
    @Override
    public boolean isMuted(MinecraftServer server, ServerPlayer sender) {
        return Jail.isJailed(sender);       // checked on the server thread, cached for a few ticks
    }
});
```

Or temporarily mute through the API (fires `VoiceMuteChangedEvent`):

```java
CozyVoiceApi.get().server().mute(player.getUUID(), 300); // 5 minutes; 0 = until restart
```

### Radio: players holding a radio hear each other anywhere

```java
@SubscribeEvent
public static void onRoute(VoiceRouteEvent e) {
    ServerPlayer sender = e.getSender();
    if (!Radio.isTransmitting(sender)) return;
    for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
        if (p != sender && Radio.isListening(p)) {
            e.getTargets().add(new VoiceTarget(p.getUUID(), VoiceCategory.RADIO, 1f));
        }
    }
    e.setRadioEffect(true);   // radio crackle on this route
}
```

A simple party/team chat needs no routing code at all:

```java
VoiceGroup team = CozyVoiceApi.get().server().createGroup("Red team", false);
team.addMember(player.getUUID());   // members hear each other at any distance; exclusive=true hides them from others
```

### Client: talking indicator on the HUD

```java
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HudSetup {
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("talking", (gui, g, partialTick, w, h) -> {
            ClientVoiceApi voice = CozyVoiceApi.get().client();
            if (!voice.isConnected()) return;
            int y = 4;
            for (UUID id : voice.getTalkingPlayers()) {
                PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(id);
                String name = info == null ? id.toString() : info.getProfile().getName();
                g.drawString(Minecraft.getInstance().font, "🔊 " + name, 4, y, 0xFFFFFF);
                y += 10;
            }
            if (voice.isLocalTalking()) g.drawString(Minecraft.getInstance().font, "● REC", w - 40, 4, 0xFF5555);
        });
    }
}
```

Or react to changes instead of polling:

```java
@SubscribeEvent
public static void onTalking(ClientTalkingStateEvent e) {
    if (e.isLocalPlayer()) MyHud.setRecording(e.isTalking());
}
```

### Inject a sound: a jukebox-like speaker block

```java
ServerVoiceApi voice = CozyVoiceApi.get().server();
LocationalAudioChannel channel = voice.createLocationalChannel(UUID.randomUUID(), level, Vec3.atCenterOf(pos));
channel.setDistance(24);

short[][] frames = MyDecoder.loadPcm48kMono("speaker.ogg"); // 960 samples per frame
AtomicInteger i = new AtomicInteger();
AudioPlayer player = voice.createAudioPlayer(channel, () -> {
    int n = i.getAndIncrement();
    return n < frames.length ? frames[n] : null;   // null ends playback
});
player.onStopped(channel::close);
player.start();
```

`createStaticChannel` plays without position (e.g. an announcement to chosen players), `createEntityChannel` follows an
entity. Close channels you no longer need.

### Voice changer (client)

```java
@SubscribeEvent
public static void onMic(ClientMicrophoneEvent e) {
    short[] pcm = e.getPcm();
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (pcm[i] / 2);   // half volume
}
```

---

## 5. Compatibility notes

- The mod must be installed on both server and client; addon audio channels and groups need the server side.
- Groups and audio channels live in memory and are cleared when the server stops.
- Player voice uses categories 0–15; addon audio channels arrive on the client as `VoiceCategory.STATIC_SOURCE`,
  `LOCATIONAL_SOURCE` and `ENTITY_SOURCE`.
- `VoiceRouteHook` and the static `CozyVoiceApi.registerRouteHook(...)` from 1.0.0 keep working unchanged.
