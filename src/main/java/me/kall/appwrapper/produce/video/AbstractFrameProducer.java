package me.kall.appwrapper.produce.video;

import it.unimi.dsi.fastutil.doubles.Double2ObjectFunction;
import me.kall.appwrapper.data.MediaArgs;
import me.kall.appwrapper.produce.audio.AudioProducer;
import me.kall.appwrapper.produce.AbstractProducer;
import me.kall.appwrapper.util.LifetimeController;
import me.kall.appwrapper.util.TimeCostDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractFrameProducer<T> extends AbstractProducer {
    protected final MediaArgs mediaArgs;

    protected final int frameSize, bufferCapacity;

    protected final String absFFmpegPath;

    protected final LinkedBlockingQueue<Frame<T>> frames;
    protected final AtomicLong frameIndex = new AtomicLong(), setupTime = new AtomicLong();
    protected final AtomicReference<T> lastFrame = new AtomicReference<>();

    protected final AtomicBoolean fetchable = new AtomicBoolean();

    protected final TimeCostDebugger timeCostDebugger;

    protected final AtomicReference<LifetimeController> life = new AtomicReference<>();
    protected final AtomicReference<Double2ObjectFunction<LifetimeController>> lifeCreation = new AtomicReference<>();

    protected final AtomicReference<AudioProducer> audio = new AtomicReference<>();
    protected final AtomicReference<Double2ObjectFunction<AudioProducer>> audioCreation = new AtomicReference<>();

    protected AbstractFrameProducer(MediaArgs mediaArgs, int frameSize, int bufferEnlarger, String absFFmpegPath) {
        this.mediaArgs = mediaArgs;
        this.frameSize = frameSize;
        this.bufferCapacity = (int) (this.mediaArgs.fps() * bufferEnlarger);
        this.absFFmpegPath = absFFmpegPath;
        this.frames = new LinkedBlockingQueue<>(this.bufferCapacity);
        this.timeCostDebugger = new TimeCostDebugger(this.debugLength());
    }

    public AbstractFrameProducer<T> setLifeCreation(Double2ObjectFunction<LifetimeController> lifeCreation) {
        this.lifeCreation.set(lifeCreation);
        return this;
    }

    public AbstractFrameProducer<T> setAudioCreation(Double2ObjectFunction<AudioProducer> audioCreation) {
        this.audioCreation.set(audioCreation);
        return this;
    }

    public @Nullable AudioProducer audio() {
        return this.audio.get();
    }

    public @Nullable LifetimeController life() {
        return this.life.get();
    }

    public @Nullable T fetch() {
        if (!this.fetchable.get()) return null;

        T lastFrame = this.lastFrame.getAndSet(null);
        if (lastFrame != null) this.recycleFrame(lastFrame);

        LifetimeController life = this.life();
        if (life == null) return null;

        Frame<T> frame = this.frames.poll();
        if (frame == null) return null;

        long expected = (long) ((double) life.nanoTimeFromSetup() / 1_000_000_000L * this.mediaArgs.fps());

        while (frame != null && frame.index() < expected) {
            this.recycleFrame(frame.data());
            frame = this.frames.poll();
        }

        if (frame == null) {
            life.detectLagSpike();
            this.timeCostDebugger.printDebug();
            return null;
        }

        this.lastFrame.set(frame.data());
        return frame.data();
    }

    @Override
    protected void forInput(@NotNull InputStream input) throws IOException, InterruptedException {
        byte[] temp = new byte[Math.min(this.frameSize, 256 * 1024)];

        while (!this.isCanceled()) {
            ByteBuffer frame = this.frameCreation();
            frame.clear();

            long start = System.nanoTime();
            while (frame.hasRemaining()) {
                int toRead = Math.min(temp.length, frame.remaining());
                int read = input.read(temp, 0, toRead);
                if (read == -1) return;
                frame.put(temp, 0, read);
            }
            frame.flip();

            this.timeCostDebugger.record(System.nanoTime() - start);

            long index = this.frameIndex.incrementAndGet();
            this.handleFrame(frame, index);

            if (this.frames.remainingCapacity() == 0 && !this.fetchable.get()) {
                Double2ObjectFunction<LifetimeController> lifeCreation = this.lifeCreation.getAndSet(null);
                Double2ObjectFunction<AudioProducer> audioCreation = this.audioCreation.getAndSet(null);
                if (lifeCreation != null && audioCreation != null) {
                    double setupTime = Double.longBitsToDouble(this.setupTime.get());
                    this.audio.set(audioCreation.apply(setupTime));
                    this.life.set(lifeCreation.apply(setupTime));
                    this.fetchable.set(true);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();

        for (Frame<T> frame : this.frames) this.recycleFrame(frame.data());
        this.frames.clear();

        T lastFetched = this.lastFrame.getAndSet(null);
        if (lastFetched != null) this.recycleFrame(lastFetched);

        this.fetchable.set(false);
        this.life.set(null);

        this.timeCostDebugger.reset();

        AudioProducer audio = this.audio.getAndSet(null);
        if (audio != null) audio.shutdown();
    }

    @Override
    public void setup(double setupTime) {
        this.frameIndex.set((long) (setupTime * this.mediaArgs.fps()));
        this.setupTime.set(Double.doubleToRawLongBits(setupTime));

        super.setup(setupTime);
    }

    protected abstract void recycleFrame(T frame);
    protected abstract int debugLength();

    protected abstract ByteBuffer frameCreation();
    protected abstract void handleFrame(ByteBuffer frame, long frameIndex) throws InterruptedException;

    public record Frame<T>(long index, T data) {}
}
