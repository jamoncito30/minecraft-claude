package com.mojang.minecraft.level;

/**
 * Listener para notificar a LevelRenderer cuando cambian bloques, columnas de luz o la luz propagada.
 */
public interface LevelListener {
    void tileChanged(int x, int y, int z);

    void lightColumnChanged(int x, int z, int y0, int y1);

    void lightChanged(int x0, int y0, int z0, int x1, int y1, int z1);

    void allChanged();
}
