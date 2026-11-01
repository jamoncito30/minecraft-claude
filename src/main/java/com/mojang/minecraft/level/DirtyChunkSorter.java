package com.mojang.minecraft.level;

import com.mojang.minecraft.Player;

import java.util.Comparator;

/**
 * Ordena los Chunks pendientes de reconstruir priorizando los que están en pantalla
 * y más cerca del jugador (fiel a DirtyChunkSorter de Notch).
 */
public class DirtyChunkSorter implements Comparator<Chunk> {
    private final Player player;

    public DirtyChunkSorter(Player player) {
        this.player = player;
    }

    @Override
    public int compare(Chunk c0, Chunk c1) {
        if (c0.visible && !c1.visible) return -1;
        if (c1.visible && !c0.visible) return 1;
        return Float.compare(c0.distanceToSqr(this.player), c1.distanceToSqr(this.player));
    }
}
