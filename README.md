# Cozy Voice Chat (`cozyvoicechat`)

Самостоятельный голосовой чат для Minecraft Forge 1.20.1 (47.3.0+), выделенный из
Union-MC SCP-RP (бывший пакет `umcvoice`, «union-mc Voice»). Голос идёт по собственному
UDP-протоколу (Opus через Concentus — чистая Java, без нативных библиотек).

## Структура
```
src/main/java/net/elgerelg/cozyvoicechat/
  CozyVoiceChat.java        — главный класс мода (@Mod), логгер, сетевой канал cozyvoicechat:main
  CozyVoiceNetwork.java     — регистрация forge-пакетов + sendTo/sendToServer
  CozyVoiceProtocol.java    — бинарный UDP-протокол (magic 'CZVC')
  VoiceHelloPacket.java     — S2C: токен, порты, битрейт, дальность
  VoiceStatePacket.java     — C2S/S2C: микрофон выключен / заглушен
  OpusCodec.java            — обёртка над Concentus
  api/
    CozyVoiceApi.java       — регистрация хуков маршрутизации из других модов
    VoiceRouteHook.java     — муты, спектаторы, глобальный голос, рации и т.п. для интеграций
  client/                   — захват микрофона, воспроизведение, HUD, меню, кейбинды
  server/                   — UDP-ретранслятор, маршрутизация, конфиг, эффекты, команда /cozyvoice
```

## Сборка
```
gradlew build
```
Готовый мод: `build/libs/cozyvoicechat-<версия>.jar` (Concentus вшит через jarJar).
Файл `*-slim.jar` — без вшитого кодека, на сервер/клиент его ставить не нужно.

## Сервер
- Открой UDP-порт **24455** (настраивается в `config/cozyvoicechat-server.json`, можно несколько портов).
- Клиент узнаёт порт/адрес автоматически из VoiceHelloPacket при входе.
- Мод нужен и на клиенте, и на сервере.

## Конфиги
- `config/cozyvoicechat-server.json` — порты, bindAddress, publicHost, битрейт, дальность, таймауты.
- `config/cozyvoicechat-client.json` — устройства, режим (PTT/VAD), усиление, громкость игроков.

## Управление
- **V** — меню настроек, **CAPS LOCK** — push-to-talk, остальные клавиши — в «Управление → Cozy Voice Chat».

## Команда `/cozyvoice` (OP 2)
- `debug` — состояние UDP-ретранслятора и сессий
- `reload` — перечитать серверный конфиг
- `effect <игроки> <radio|echo> <true|false>`
- `hearself <игроки> <true|false>`
- `passthrough filteradd|filterrem <блок>`, `passthrough filterlist` — блоки, глушащие голос
- `mute <игроки> [секунды]`, `unmute <игроки>`, `mutelist` — муты в памяти (до рестарта)

## Интеграция
```java
CozyVoiceApi.registerRouteHook(new VoiceRouteHook() {
    @Override public boolean isMuted(MinecraftServer s, ServerPlayer p) { return MyMutes.isMuted(p); }
    @Override public void routeExtra(MinecraftServer s, ServerPlayer sender, float level,
                                     boolean selfMonitor, Set<UUID> heardLive, Sink sink) {
        // например, рация: sink.send(listenerId, CozyVoiceProtocol.CAT_RADIO, 1f);
    }
});
```
Громкость говорящего на клиенте: `VoicePlayback.levelOf(uuid)` (0..1, по последнему проигранному кадру).

## Локализация

Все тексты интерфейса, клавиш и ответов команд переводятся: `assets/cozyvoicechat/lang/en_us.json`, `ru_ru.json`, `uk_ua.json`.
"# CozyVoiceChat" 
