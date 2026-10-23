package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.api.server.AudioChannel;
import net.elgerelg.cozyvoicechat.api.server.AudioPlayer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Отправляет кадры в аудиоканал каждые 20 мс на общем фоновом потоке. */
public final class AudioPlayerImpl implements AudioPlayer {

    private static final ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "CozyVoice-AudioPlayer");
        t.setDaemon(true);
        return t;
    });

    private final AudioChannel channel;
    private final Supplier<short[]> frames;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final List<Runnable> onStopped = new CopyOnWriteArrayList<>();
    private volatile ScheduledFuture<?> future;

    public AudioPlayerImpl(AudioChannel channel, Supplier<short[]> frames) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.frames = Objects.requireNonNull(frames, "frames");
    }

    @Override
    public void start() {
        if (stopped.get() || !started.compareAndSet(false, true)) return;
        future = EXEC.scheduleAtFixedRate(this::step, 0, 20, TimeUnit.MILLISECONDS);
    }

    private void step() {
        if (stopped.get()) return;
        if (channel.isClosed()) { stop(); return; }
        try {
            short[] f = frames.get();
            if (f == null) { stop(); return; }
            channel.sendPcm(f);
        } catch (Throwable t) {
            CozyVoiceChat.LOGGER.warn("[CozyVoice] Audio player for channel {} failed, stopping", channel.getId(), t);
            stop();
        }
    }

    @Override
    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        ScheduledFuture<?> f = future;
        if (f != null) f.cancel(false);
        for (Runnable r : onStopped) {
            try { r.run(); } catch (Throwable t) {
                CozyVoiceChat.LOGGER.warn("[CozyVoice] Audio player stop callback failed", t);
            }
        }
    }

    @Override public boolean isPlaying() { return started.get() && !stopped.get(); }
    @Override public boolean isStopped() { return stopped.get(); }

    @Override
    public void onStopped(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        onStopped.add(callback);
        if (stopped.get() && onStopped.remove(callback)) callback.run();
    }

    @Override public AudioChannel getChannel() { return channel; }
}
