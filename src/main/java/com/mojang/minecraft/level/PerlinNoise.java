package com.mojang.minecraft.level;

import java.util.Random;

/**
 * Generador de ruido Perlin multi-octava usado en Minecraft Classic 0.0.14a
 * para generar colinas suaves, valles, acantilados y playas.
 */
public class PerlinNoise {
    private final int[][] perm;
    private final int octaves;

    public PerlinNoise(Random random, int octaves) {
        this.octaves = octaves;
        this.perm = new int[octaves][512];

        for (int o = 0; o < octaves; o++) {
            int[] p = this.perm[o];
            for (int i = 0; i < 256; i++) {
                p[i] = i;
            }
            for (int i = 0; i < 256; i++) {
                int j = random.nextInt(256 - i) + i;
                int tmp = p[i];
                p[i] = p[j];
                p[j] = tmp;
                p[i + 256] = p[i];
            }
        }
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private double sampleOctave(int[] p, double x, double y) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = 0;

        x -= Math.floor(x);
        y -= Math.floor(y);
        double z = 0.0;

        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        int A = p[X] + Y;
        int AA = p[A] + Z;
        int AB = p[A + 1] + Z;
        int B = p[X + 1] + Y;
        int BA = p[B] + Z;
        int BB = p[B + 1] + Z;

        return lerp(w,
                lerp(v,
                        lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                        lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
                lerp(v,
                        lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1)))
        );
    }

    public double getValue(double x, double y) {
        double total = 0.0;
        double scale = 1.0;
        for (int i = 0; i < this.octaves; i++) {
            total += sampleOctave(this.perm[i], x / scale, y / scale) * scale;
            scale *= 2.0;
        }
        return total;
    }
}
