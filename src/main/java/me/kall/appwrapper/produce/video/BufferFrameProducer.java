package me.kall.appwrapper.produce.video;

import it.unimi.dsi.fastutil.doubles.Double2ObjectFunction;
import me.kall.appwrapper.data.MediaArgs;
import me.kall.appwrapper.produce.audio.AudioProducer;
import me.kall.appwrapper.util.LifetimeController;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class BufferFrameProducer extends AbstractFrameProducer<ByteBuffer> {
    private final LinkedBlockingQueue<ByteBuffer> freeBuffers;
    private final Set<ByteBuffer> allBuffers = ConcurrentHashMap.newKeySet();

    private BufferFrameProducer(MediaArgs mediaArgs, int bufferEnlarger, String absFFmpegPath) {
        super(mediaArgs, mediaArgs.width() * mediaArgs.height() + (mediaArgs.width() / 2) * (mediaArgs.height() / 2) * 2, bufferEnlarger, absFFmpegPath);
        this.freeBuffers = new LinkedBlockingQueue<>(this.bufferCapacity);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull BufferFrameProducer create(MediaArgs mediaArgs, int bufferEnlarger, String absFFmpegPath) {
        return new BufferFrameProducer(mediaArgs, bufferEnlarger, absFFmpegPath);
    }

    @Override
    public BufferFrameProducer setAudioCreation(@NotNull Double2ObjectFunction<AudioProducer> func) {
        this.audioCreation.set(func);
        return this;
    }

    @Override
    public BufferFrameProducer setLifeCreation(@NotNull Double2ObjectFunction<LifetimeController> func) {
        this.lifeCreation.set(func);
        return this;
    }

    @Override
    protected void recycleFrame(ByteBuffer frame) {
        this.freeBuffers.offer(frame);
    }

    @Override
    protected int debugLength() {
        return 10;
    }

    @Override
    protected ByteBuffer frameCreation() {
        ByteBuffer buffer = this.freeBuffers.poll();
        if (buffer == null) {
            buffer = MemoryUtil.memAlloc(this.frameSize);
            this.allBuffers.add(buffer);
        }
        return buffer;
    }

    @Override
    protected void handleFrame(ByteBuffer frame, long frameIndex) throws InterruptedException {
        this.frames.put(new Frame<>(frameIndex, frame));
    }

    @Override
    protected String[] setCommand(double setupTime) {
        return new String[]{this.absFFmpegPath, "-loglevel", "quiet", "-hwaccel", "auto", "-ss", String.valueOf(setupTime), "-i", this.mediaArgs.absVideoPath(), "-map", "0:v:0", "-an", "-sn", "-dn", "-threads", "0", "-vf", "fps=" + this.mediaArgs.fps() + ",scale=" + this.mediaArgs.width() + ":" + this.mediaArgs.height() + ":flags=fast_bilinear,format=yuv420p", "-f", "rawvideo", "-vcodec", "rawvideo", "-tune", "zerolatency", "-"};
    }

    @Override
    public void setup(double setupTime) {
        for (int i = 0; i < this.bufferCapacity; i++) {
            ByteBuffer buffer = MemoryUtil.memAlloc(frameSize);
            this.allBuffers.add(buffer);
            this.freeBuffers.offer(buffer);
        }

        super.setup(setupTime);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        synchronized (this.allBuffers) {
            for (ByteBuffer buffer : this.allBuffers) MemoryUtil.memFree(buffer);
            this.allBuffers.clear();
        }
        this.freeBuffers.clear();
    }
}
