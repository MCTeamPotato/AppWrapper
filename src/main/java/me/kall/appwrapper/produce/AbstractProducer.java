package me.kall.appwrapper.produce;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractProducer {
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private final AtomicReference<ExecutorService> executor = new AtomicReference<>();
    private final AtomicReference<Process> process = new AtomicReference<>();
    private final AtomicReference<InputStream> input = new AtomicReference<>();

    protected abstract String[] setCommand(double setupTime);
    protected abstract void forInput(@NotNull InputStream input) throws IOException, InterruptedException;

    public boolean isCanceled() {
        return this.canceled.get();
    }

    public void setCanceled(boolean canceled) {
        this.canceled.set(canceled);
    }

    public void setup(double setupTime) {
        this.setCanceled(false);

        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, this.getClass().getSimpleName());
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        });

        this.executor.set(executor);

        executor.submit(() -> {
            String[] command = this.setCommand(setupTime);
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                this.process.set(process);

                InputStream input = process.getInputStream();
                this.input.set(input);

                this.forInput(input);
            } catch (IOException | InterruptedException ioException) {
                if (this.isCanceled()) return;
                System.err.println("Exception executing " + Arrays.toString(command));
                ioException.printStackTrace(System.err);
                throw new RuntimeException(ioException);
            }
        });
    }

    public void shutdown() {
        this.setCanceled(true);

        Process process = this.process.getAndSet(null);
        if (process != null) process.destroyForcibly();

        ExecutorService executor = this.executor.getAndSet(null);
        if (executor != null) executor.shutdownNow();

        InputStream input = this.input.getAndSet(null);
        if (input != null) {
            try {
                input.close();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }
}
