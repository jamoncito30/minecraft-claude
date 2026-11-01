package com.mojang.minecraft;

/**
 * Controla el tiempo del juego al estilo original de Minecraft Classic (20 ticks por segundo).
 * Calcula cuántos ticks lógicos deben ejecutarse en cada cuadro y el factor de interpolación 'a'.
 */
public class Timer {
    private static final long NS_PER_SECOND = 1_000_000_000L;
    private static final long MAX_NS_PER_UPDATE = 1_000_000_000L;
    private static final int MAX_TICKS_PER_UPDATE = 100;

    public final float ticksPerSecond;
    private long lastTime;
    public int ticks;
    public float a;
    public float timeScale = 1.0f;
    public float fps = 0.0f;
    public float passedTime = 0.0f;

    public Timer(float ticksPerSecond) {
        this.ticksPerSecond = ticksPerSecond;
        this.lastTime = System.nanoTime();
    }

    public void advanceTime() {
        long now = System.nanoTime();
        long passedNs = now - this.lastTime;
        this.lastTime = now;

        if (passedNs < 0L) {
            passedNs = 0L;
        }
        if (passedNs > MAX_NS_PER_UPDATE) {
            passedNs = MAX_NS_PER_UPDATE;
        }

        if (passedNs > 0L) {
            this.fps = (float) (NS_PER_SECOND / passedNs);
        }

        this.passedTime += passedNs * this.timeScale * this.ticksPerSecond / (float) NS_PER_SECOND;
        this.ticks = (int) this.passedTime;
        if (this.ticks > MAX_TICKS_PER_UPDATE) {
            this.ticks = MAX_TICKS_PER_UPDATE;
        }
        this.passedTime -= this.ticks;
        this.a = this.passedTime;
    }
}
