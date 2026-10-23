package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Серверные настройки голосового чата: config/cozyvoicechat-server.json.
 *
 * Портов можно указать несколько — каждый слушается своим сокетом и своим
 * потоком, а клиенты раскидываются по ним равномерно. Это снимает очередь
 * на одном сокете, из-за которой на больших онлайнах теряются пакеты.
 */
public final class CozyVoiceServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CozyVoiceServerConfig instance;

    /** UDP-порты ретранслятора. Первый используется как основной. */
    public List<Integer> ports = new ArrayList<>(List.of(CozyVoiceProtocol.DEFAULT_PORT));
    /** Адрес для bind: 0.0.0.0 = все интерфейсы. */
    public String bindAddress = "0.0.0.0";
    /** Адрес, который получит клиент. Пусто = адрес, на который он подключился. */
    public String publicHost = "";
    /** Битрейт Opus, бит/с (8000..96000). */
    public int bitrate = 32000;
    /** Дальность обычного голоса в блоках. */
    public int maxDistance = 48;
    /** С какой доли дальности начинается плавное затухание (0..1). */
    public double fadeStart = 0.5;
    /** Таймаут молчащей UDP-сессии, секунды. */
    public int sessionTimeoutSeconds = 15;
    /** Порог предупреждения о потерях пакетов, проценты. */
    public double packetLossWarnPercent = 5.0;
    /** Подробный лог: каждое подключение/отключение и периодическая статистика. */
    public boolean debugLog = false;
    /** Период вывода статистики в консоль, секунды (0 = не выводить). */
    public int statsIntervalSeconds = 60;

    private CozyVoiceServerConfig() {}

    public static synchronized CozyVoiceServerConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static File file() {
        return FMLPaths.CONFIGDIR.get().resolve("cozyvoicechat-server.json").toFile();
    }

    public static synchronized void load() {
        File f = file();
        if (f.isFile()) {
            try (FileReader r = new FileReader(f)) {
                CozyVoiceServerConfig c = GSON.fromJson(r, CozyVoiceServerConfig.class);
                instance = c != null ? c : new CozyVoiceServerConfig();
            } catch (Exception e) {
                CozyVoiceChat.LOGGER.warn("[CozyVoice] Битый cozyvoicechat-server.json, беру значения по умолчанию: {}", e.toString());
                instance = new CozyVoiceServerConfig();
            }
        } else {
            instance = new CozyVoiceServerConfig();
        }
        instance.sanitize();
        save();
    }

    private void sanitize() {
        if (ports == null || ports.isEmpty()) {
            ports = new ArrayList<>(List.of(CozyVoiceProtocol.DEFAULT_PORT));
        }
        List<Integer> clean = new ArrayList<>();
        for (Integer p : ports) {
            if (p != null && p > 0 && p < 65536 && !clean.contains(p)) clean.add(p);
        }
        if (clean.isEmpty()) clean.add(CozyVoiceProtocol.DEFAULT_PORT);
        ports = clean;

        if (bindAddress == null || bindAddress.isEmpty()) bindAddress = "0.0.0.0";
        if (publicHost == null) publicHost = "";
        bitrate = clamp(bitrate, 8000, 96000);
        maxDistance = clamp(maxDistance, 1, 512);
        fadeStart = Math.max(0.0, Math.min(0.95, fadeStart));
        sessionTimeoutSeconds = clamp(sessionTimeoutSeconds, 5, 300);
        if (packetLossWarnPercent < 0) packetLossWarnPercent = 0;
        if (statsIntervalSeconds < 0) statsIntervalSeconds = 0;
    }

    public static synchronized void save() {
        if (instance == null) return;
        File f = file();
        try {
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            CozyVoiceChat.LOGGER.warn("[CozyVoice] Не удалось сохранить cozyvoicechat-server.json: {}", e.toString());
        }
    }

    public int[] portArray() {
        int[] a = new int[ports.size()];
        for (int i = 0; i < a.length; i++) a[i] = ports.get(i);
        return a;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
