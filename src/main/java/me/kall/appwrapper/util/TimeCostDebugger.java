package me.kall.appwrapper.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class TimeCostDebugger {
    private final int size;
    private final long[] costs;
    private int timeIndex = 0;
    private int count = 0;
    private long lastPrint = -1;

    public TimeCostDebugger(int size) {
        this.size = size;
        this.costs = new long[size];
    }

    public synchronized void reset() {
        Arrays.fill(this.costs, 0L);
        this.timeIndex = 0;
        this.count = 0;
    }

    public synchronized void record(long nanoseconds) {
        this.costs[this.timeIndex] = nanoseconds;
        this.timeIndex = (this.timeIndex + 1) % this.size;
        if (this.count < this.size) {
            this.count++;
        }
    }

    public synchronized void printDebug() {
        if (System.nanoTime() - this.lastPrint < 2_000_000_000L) return;
        this.lastPrint = System.nanoTime();
        System.out.println("Costs of the last " + this.count + " records in ms: " + Arrays.toString(this.costsMillis()));
    }

    @Contract(pure = true)
    private double @NotNull [] costsMillis() {
        double[] millis = new double[this.count];
        if (this.count == 0) return millis;

        int start = (this.timeIndex - this.count + this.size) % this.size;
        for (int i = 0; i < this.count; i++) {
            millis[i] = this.costs[(start + i) % this.size] / 1_000_000.0;
        }
        return millis;
    }
}
