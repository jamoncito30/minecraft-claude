package com.mojang.minecraft.level.tile;

/**
 * Tronco de árbol (Tree Trunk) introducido en Minecraft Classic 0.0.14a:
 * - Caras superior e inferior (0, 1): anillos del tronco (tex 21)
 * - Caras laterales (2..5): corteza vertical (tex 20)
 */
public class LogTile extends Tile {
    protected LogTile(int id) {
        super(id, 20, "Tronco");
    }

    @Override
    protected int getTexture(int face) {
        return (face == 1 || face == 0) ? 21 : 20;
    }
}
