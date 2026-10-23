package net.elgerelg.cozyvoicechat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.elgerelg.cozyvoicechat.api.event.client.ClientVoiceSettingsChangedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Клиентские настройки войсчата. Сохраняются в config/cozyvoicechat-client.json.
 */
public final class CozyVoiceSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /**
     * volatile, а не synchronized-геттер: настройки читают и поток рендера, и
     * поток микрофона, и поток воспроизведения по несколько раз за кадр.
     * Общий монитор на этом пути заставлял главный поток ждать аудиопотоки.
     */
    private static volatile CozyVoiceSettings instance;

    /** Имя микшера устройства ввода; "" = системный по умолчанию. */
    public volatile String inputDevice = "";
    /** Имя микшера устройства вывода; "" = системный по умолчанию. */
    public volatile String outputDevice = "";
    /** true = активация голосом, false = push-to-talk. */
    public volatile boolean vadMode = false;
    /** Порог активации голосом, 0..1 (амплитуда). */
    public volatile double vadThreshold = 0.05;
    /** Общая громкость войсчата, 0..2. */
    public volatile double masterVolume = 1.0;
    /** Усиление микрофона, 0..4. Полтора — обычный микрофон без этого тихий. */
    public volatile double micGain = 1.5;
    /** Шумоподавление: экспандер по оценке уровня шума + обрезка низов. */
    public volatile boolean noiseSuppression = true;
    /** Индивидуальная громкость игроков: uuid -> множитель 0..2. */
    public java.util.Map<String, Double> playerVolumes = new java.util.concurrent.ConcurrentHashMap<>();
    /** Тест микрофона: локальный loopback mic->speaker. */
    public volatile boolean monitor = false;
    /** Микрофон выключен (клавиша M): нас не слышно, слышим всех. */
    public volatile boolean micMuted = false;
    /** Глушилка (клавиша N): не слышим никого и сами молчим. */
    public volatile boolean deafened = false;
    /**
     * Ванильные «Социальные взаимодействия» уже сняты с клавиши, занятой нашим
     * меню игроков. Снимаем один раз, иначе перебивали бы игрока каждый запуск.
     */
    public volatile boolean socialKeyFreed = false;

    private CozyVoiceSettings() {}

    public static CozyVoiceSettings get() {
        CozyVoiceSettings i = instance;
        if (i != null) return i;
        synchronized (CozyVoiceSettings.class) {
            if (instance == null) load();
            return instance;
        }
    }

    private static File file() {
        return FMLPaths.CONFIGDIR.get().resolve("cozyvoicechat-client.json").toFile();
    }

    public static synchronized void load() {
        File f = file();
        if (f.isFile()) {
            try (FileReader r = new FileReader(f)) {
                CozyVoiceSettings s = GSON.fromJson(r, CozyVoiceSettings.class);
                instance = s != null ? s : new CozyVoiceSettings();
            } catch (Exception e) {
                instance = new CozyVoiceSettings();
            }
        } else {
            instance = new CozyVoiceSettings();
        }
        // защита от мусора в конфиге
        instance.vadThreshold = clamp(instance.vadThreshold, 0, 1);
        instance.masterVolume = clamp(instance.masterVolume, 0, 2);
        instance.micGain = clamp(instance.micGain, 0, 4);
        // Gson подставляет свою реализацию карты, а читает её поток
        // воспроизведения — возвращаем потокобезопасную.
        instance.playerVolumes = instance.playerVolumes == null
                ? new java.util.concurrent.ConcurrentHashMap<>()
                : new java.util.concurrent.ConcurrentHashMap<>(instance.playerVolumes);
    }

    /** Множитель громкости конкретного игрока, 1.0 — без изменений. */
    public static double playerVolume(java.util.UUID id) {
        Double v = get().playerVolumes.get(id.toString());
        return v == null ? 1.0 : clamp(v, 0, 2);
    }

    public static void setPlayerVolume(java.util.UUID id, double v) {
        CozyVoiceSettings cfg = get();
        if (Math.abs(v - 1.0) < 0.005) cfg.playerVolumes.remove(id.toString());
        else cfg.playerVolumes.put(id.toString(), clamp(v, 0, 2));
    }

    public static void save() {
        if (!saveInternal()) return;
        // Вне блокировки: обработчик может сразу перечитать настройки.
        try {
            MinecraftForge.EVENT_BUS.post(new ClientVoiceSettingsChangedEvent());
        } catch (Throwable ignored) {}
    }

    private static synchronized boolean saveInternal() {
        if (instance == null) return false;
        File f = file();
        try {
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception ignored) {}
        return true;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
