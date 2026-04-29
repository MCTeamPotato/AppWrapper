package me.kall.appwrapper.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

public final class LifetimeController {
    private final AtomicLong absoluteSetupTime;
    private final AtomicLong lastFetch = new AtomicLong(-1L);

    private final AtomicLong lastLagSpike = new AtomicLong(-1L);

    private final AtomicLong pausedAt = new AtomicLong();
    private final AtomicBoolean paused = new AtomicBoolean();

    private final AtomicBoolean lagSpike = new AtomicBoolean();
    private final AtomicBoolean soundSync = new AtomicBoolean();

    private @Nullable Supplier<Runnable> endRestartFunc, pauseFunc, resumeFunc;

    private @Nullable Supplier<DoubleConsumer> synchronizeFunc, soundSyncFunc;

    private final double fps;
    private final long duration;

    private LifetimeController(long absoluteSetupTime, double fps, double duration) {
        this.absoluteSetupTime = new AtomicLong(absoluteSetupTime);
        this.fps = fps;
        this.duration = (long) (duration * 1000000D);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull LifetimeController create(long absoluteSetupTime, double fps, double duration) {
        return new LifetimeController(absoluteSetupTime, fps, duration);
    }

    public LifetimeController setEndRestartFunc(@NotNull Supplier<Runnable> endRestartFunc) {
        this.endRestartFunc = endRestartFunc;
        return this;
    }

    public LifetimeController setSynchronizeFunc(@NotNull Supplier<DoubleConsumer> synchronizeFunc) {
        this.synchronizeFunc = synchronizeFunc;
        return this;
    }

    public LifetimeController setSoundSyncFunc(@NotNull Supplier<DoubleConsumer> soundSyncFunc) {
        this.soundSyncFunc = soundSyncFunc;
        return this;
    }

    public LifetimeController setPauseFunc(@NotNull Supplier<Runnable> pauseFunc) {
        this.pauseFunc = pauseFunc;
        return this;
    }

    public LifetimeController setResumeFunc(@NotNull Supplier<Runnable> resumeFunc) {
        this.resumeFunc = resumeFunc;
        return this;
    }

    public LifetimeController seekTo(double at) {
        if (!Double.isNaN(at)) {
            this.absoluteSetupTime.set(System.nanoTime() - (long) (at * 1_000_000_000L));
            this.lastFetch.set(-1L);
        }
        return this;
    }

    public boolean paused() {
        return this.paused.get();
    }

    public void pause() {
        if (!this.paused()) {
            this.paused.set(true);
            this.pausedAt.set(System.nanoTime());
            if (this.pauseFunc != null) this.pauseFunc.get().run();
        }
    }

    public void resume() {
        if (this.paused()) {
            this.paused.set(false);
            this.absoluteSetupTime.addAndGet(System.nanoTime() - this.pausedAt.get());
            if (this.resumeFunc != null) this.resumeFunc.get().run();
        }
    }

    public void detectLagSpike() {
        this.lagSpike.set(true);
    }

    public void detectSoundSync() {
        this.soundSync.set(true);
    }

    public long nanoTimeFromSetup() {
        return System.nanoTime() - this.absoluteSetupTime.get();
    }

    public boolean shouldUpdateFrame() {
        if (this.paused()) return false;
        long now = System.nanoTime();
        long last = this.lastFetch.get();

        if (last == -1L) {
            this.lastFetch.set(now);
            return true;
        }

        double elapsed = (double) (now - last) * this.fps;
        if (elapsed >= 1_000_000_000.0) {
            this.lastFetch.set(now);
            return true;
        }

        return false;
    }

    public void tick() {
        if (this.paused()) return;

        if (this.endRestartFunc != null && this.nanoTimeFromSetup() >= this.duration) {
            this.endRestartFunc.get().run();
        }

        if (this.synchronizeFunc != null && this.lagSpike.compareAndSet(true, false)) {
            long last = this.lastLagSpike.get();
            if (last == -1L) {
                this.lastLagSpike.set(System.nanoTime());
                return;
            }

            long now = System.nanoTime();
            if (now - last > 2_000_000_000L) {
                this.synchronizeFunc.get().accept((double) this.nanoTimeFromSetup() / 1_000_000_000.0);
                System.err.println("Lag spike is detected. Restarting.");
                this.lastLagSpike.set(now);
            }
        }

        if (this.soundSyncFunc != null && this.soundSync.compareAndSet(true, false)) {
            this.soundSyncFunc.get().accept((double) this.nanoTimeFromSetup() / 1_000_000_000.0);
        }
    }
}