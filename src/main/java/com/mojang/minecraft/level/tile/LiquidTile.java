package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.phys.AABB;

import java.util.Random;

/**
 * Bloques líquidos de Minecraft Classic 0.0.14a (Agua = liquidType 1, Lava = liquidType 2).
 * Se renderizan en la capa translúcida (layer 1 para agua) y se expanden hacia abajo y a los lados.
 */
public class LiquidTile extends Tile {
    protected final int liquidType;
    protected final int calmTileId;
    protected final int tileId;

    protected LiquidTile(int id, int liquidType) {
        super(id, liquidType == 1 ? 14 : 30, liquidType == 1 ? "Agua" : "Lava");
        if (liquidType == 2) {
            this.setLightEmission(15);
        }
        this.liquidType = liquidType;
        this.tileId = id;
        this.calmTileId = id + 1;
        float dd = 0.1f;
        this.setShape(0.0f, 0.0f - dd, 0.0f, 1.0f, 1.0f - dd, 1.0f);
    }

    @Override
    public void onPlace(Level level, int x, int y, int z) {
        level.addToTickNextTick(x, y, z, this.tileId);
    }

    @Override
    public void tick(Level level, int x, int y, int z, Random random) {
        boolean hasChanged = false;
        boolean canFall;
        do {
            y--;
            if (level.getTile(x, y, z) != 0) {
                break;
            }
            canFall = level.setTile(x, y, z, this.tileId);
            if (canFall) {
                hasChanged = true;
            }
        } while (canFall && this.liquidType != 2);

        y++;
        if (this.liquidType == 1 || !hasChanged) {
            hasChanged |= this.checkWater(level, x - 1, y, z);
            hasChanged |= this.checkWater(level, x + 1, y, z);
            hasChanged |= this.checkWater(level, x, y, z - 1);
            hasChanged |= this.checkWater(level, x, y, z + 1);
        }

        if (hasChanged) {
            level.addToTickNextTick(x, y, z, this.tileId);
        }
    }

    private boolean checkWater(Level level, int x, int y, int z) {
        if (level.getTile(x, y, z) == 0) {
            boolean changed = level.setTile(x, y, z, this.tileId);
            if (changed) {
                level.addToTickNextTick(x, y, z, this.tileId);
            }
        }
        return false;
    }

    @Override
    protected boolean shouldRenderFace(Level level, int x, int y, int z, int layer, int face) {
        if (x < 0 || y < 0 || z < 0 || x >= level.width || z >= level.height) {
            return false;
        }
        // Agua en la capa translúcida (1); lava opaca solo en la capa 0 (antes se dibujaba dos veces)
        if (layer != (this.liquidType == 1 ? 1 : 0)) {
            return false;
        }
        int id = level.getTile(x, y, z);
        if (id != this.tileId && id != this.calmTileId) {
            return !level.isSolidTile(x, y, z);
        }
        return false;
    }

    @Override
    public boolean mayPick() {
        return false;
    }

    @Override
    public AABB getAABB(int x, int y, int z) {
        return null;
    }

    @Override
    public boolean blocksLight() {
        return true;
    }

    @Override
    public int getLightOpacity() {
        // El agua deja pasar la luz atenuada: el fondo de lagos y mares se ve más oscuro con la profundidad
        return this.liquidType == 1 ? 2 : 15;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public int getLiquidType() {
        return this.liquidType;
    }

    @Override
    public void neighborChanged(Level level, int x, int y, int z, int type) {
        if (this.liquidType == 1 && (type == Tile.lava.id || type == Tile.calmLava.id)) {
            level.setTileNoUpdate(x, y, z, Tile.rock.id);
        }
        if (this.liquidType == 2 && (type == Tile.water.id || type == Tile.calmWater.id)) {
            level.setTileNoUpdate(x, y, z, Tile.rock.id);
        }
        level.addToTickNextTick(x, y, z, this.id);
    }
}
