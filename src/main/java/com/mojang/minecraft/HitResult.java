package com.mojang.minecraft;

/**
 * Representa el resultado de un Raycast (el bloque que el jugador está mirando).
 * face:
 *   0 = Abajo (-Y)
 *   1 = Arriba (+Y)
 *   2 = Norte (-Z)
 *   3 = Sur (+Z)
 *   4 = Oeste (-X)
 *   5 = Este (+X)
 */
public class HitResult {
    public final int x;
    public final int y;
    public final int z;
    public final int face;

    public HitResult(int x, int y, int z, int face) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }
}
