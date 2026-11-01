package com.mojang.minecraft.level;

import com.mojang.minecraft.HitResult;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;

import java.io.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Representa el mundo completo de bloques (256 x 64 x 256) en Minecraft Classic 0.0.14a.
 * Administra el array de bloques, mapa de luz solar, motor de luz (cielo + bloques),
 * ticks de bloques, colisiones AABB, raycasting (DDA), hora del día y persistencia en level.dat.
 */
public class Level {
    private static final int TILE_UPDATE_INTERVAL = 200;
    private static final int LEGACY_MAGIC = 0x271BB788;
    private static final int SAVE_MAGIC = 0x4D43434C;
    private static final int SAVE_VERSION = 2;
    public static final String SAVE_FILE = "level.dat";

    public final int width;
    public final int height;
    public final int depth;

    public byte[] blocks;
    private final int[] lightDepths;
    private final List<LevelListener> levelListeners = new ArrayList<>();
    private final Random random = new Random();

    public int xSpawn;
    public int ySpawn;
    public int zSpawn;
    public float rotSpawn;

    /** Ticks transcurridos desde que se creó el mundo (controla el ciclo día/noche). */
    public long time = 0L;
    /** Datos del jugador leídos/escritos en level.dat: x, y, z, yRot, xRot (null si no hay). */
    public float[] savedPlayer = null;
    public boolean savedFlying = false;
    public int[] savedHotbar = null;

    public final LightEngine light;
    private Thread saveThread;

    private int unprocessed = 0;

    private static class TickEntry {
        int x, y, z, id, ticks;

        TickEntry(int x, int y, int z, int id) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.ticks = 5;
        }
    }

    // ArrayDeque en lugar de ArrayList.remove(0), que era O(n) por cada elemento procesado
    private final ArrayDeque<TickEntry> tickList = new ArrayDeque<>();

    public Level(int w, int h, int d) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.blocks = new byte[w * h * d];
        this.lightDepths = new int[w * h];
        this.light = new LightEngine(this);

        boolean mapLoaded = this.load();
        if (!mapLoaded) {
            this.generateNewMap();
        }
    }

    public void generateNewMap() {
        this.blocks = new LevelGen(this.width, this.depth, this.height).generateMap();
        this.calcLightDepths(0, 0, this.width, this.height);
        this.light.recalcAll();
        this.findSpawn();
        this.savedPlayer = null;
        this.tickList.clear();
        for (LevelListener listener : this.levelListeners) {
            listener.allChanged();
        }
    }

    public void findSpawn() {
        Random rand = new Random();
        int attempts = 0;
        while (attempts < 10000) {
            attempts++;
            int x = rand.nextInt(this.width / 2) + this.width / 4;
            int z = rand.nextInt(this.height / 2) + this.height / 4;
            int y = this.getHighestTile(x, z) + 1;
            if (y > 33 && y < this.depth - 2) {
                int below = this.getTile(x, y - 1, z);
                if (below != Tile.water.id && below != Tile.calmWater.id && below != Tile.lava.id) {
                    this.xSpawn = x;
                    this.ySpawn = y + 1;
                    this.zSpawn = z;
                    return;
                }
            }
        }
        this.xSpawn = this.width / 2;
        this.zSpawn = this.height / 2;
        this.ySpawn = this.depth - 2;
    }

    public int getHighestTile(int x, int z) {
        int y = this.depth;
        while ((this.getTile(x, y - 1, z) == 0 || !Tile.tiles[this.getTile(x, y - 1, z)].isSolid()) && y > 0) {
            y--;
        }
        return y;
    }

    public boolean load() {
        File file = new File(SAVE_FILE);
        if (!file.exists()) {
            return false;
        }
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            int magic = dis.readInt();
            if (magic == SAVE_MAGIC) {
                int version = dis.readInt();
                if (version != SAVE_VERSION) {
                    return false;
                }
                if (dis.readInt() != this.width || dis.readInt() != this.height || dis.readInt() != this.depth) {
                    return false;
                }
                this.xSpawn = dis.readInt();
                this.ySpawn = dis.readInt();
                this.zSpawn = dis.readInt();
                this.time = dis.readLong();
                if (dis.readBoolean()) {
                    this.savedPlayer = new float[5];
                    for (int i = 0; i < 5; i++) {
                        this.savedPlayer[i] = dis.readFloat();
                    }
                    this.savedFlying = dis.readBoolean();
                }
                int hotbarLen = dis.readInt();
                if (hotbarLen > 0 && hotbarLen <= 64) {
                    this.savedHotbar = new int[hotbarLen];
                    for (int i = 0; i < hotbarLen; i++) {
                        this.savedHotbar[i] = dis.readInt();
                    }
                }
            } else if (magic == LEGACY_MAGIC) {
                // Formato antiguo de Gemini (solo spawn + bloques)
                this.xSpawn = dis.readInt();
                this.ySpawn = dis.readInt();
                this.zSpawn = dis.readInt();
            } else {
                return false;
            }
            dis.readFully(this.blocks);
            this.calcLightDepths(0, 0, this.width, this.height);
            this.light.recalcAll();
            for (LevelListener listener : this.levelListeners) {
                listener.allChanged();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Guarda el mundo de forma síncrona (al cerrar el juego). */
    public void save() {
        this.waitForSave();
        writeSave(this.snapshot());
    }

    /**
     * Guarda en un hilo aparte usando una copia de los bloques, para que el autoguardado no congele el juego.
     * Devuelve false si ya había un guardado en curso.
     */
    public boolean saveAsync() {
        if (this.saveThread != null && this.saveThread.isAlive()) {
            return false;
        }
        SaveData data = this.snapshot();
        this.saveThread = new Thread(() -> writeSave(data), "Level saver");
        this.saveThread.start();
        return true;
    }

    private void waitForSave() {
        if (this.saveThread != null) {
            try {
                this.saveThread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record SaveData(int w, int h, int d, int xs, int ys, int zs, long time,
                            float[] player, boolean flying, int[] hotbar, byte[] blocks) {
    }

    private SaveData snapshot() {
        return new SaveData(this.width, this.height, this.depth, this.xSpawn, this.ySpawn, this.zSpawn, this.time,
                this.savedPlayer == null ? null : this.savedPlayer.clone(), this.savedFlying,
                this.savedHotbar == null ? new int[0] : this.savedHotbar.clone(), this.blocks.clone());
    }

    private static void writeSave(SaveData s) {
        // Se escribe en un archivo temporal y luego se reemplaza, para no corromper el mundo si algo falla
        File tmp = new File(SAVE_FILE + ".tmp");
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            dos.writeInt(SAVE_MAGIC);
            dos.writeInt(SAVE_VERSION);
            dos.writeInt(s.w);
            dos.writeInt(s.h);
            dos.writeInt(s.d);
            dos.writeInt(s.xs);
            dos.writeInt(s.ys);
            dos.writeInt(s.zs);
            dos.writeLong(s.time);
            dos.writeBoolean(s.player != null);
            if (s.player != null) {
                for (float f : s.player) {
                    dos.writeFloat(f);
                }
                dos.writeBoolean(s.flying);
            }
            dos.writeInt(s.hotbar.length);
            for (int id : s.hotbar) {
                dos.writeInt(id);
            }
            dos.write(s.blocks);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        File target = new File(SAVE_FILE);
        if (target.exists() && !target.delete()) {
            System.err.println("No se pudo reemplazar " + SAVE_FILE);
            return;
        }
        if (!tmp.renameTo(target)) {
            System.err.println("No se pudo renombrar " + tmp + " a " + SAVE_FILE);
        }
    }

    public void calcLightDepths(int x0, int z0, int x1, int z1) {
        for (int x = x0; x < x0 + x1; x++) {
            for (int z = z0; z < z0 + z1; z++) {
                int oldDepth = this.lightDepths[x + z * this.width];
                int y = this.depth - 1;
                while (y > 0 && !this.isLightBlocker(x, y, z)) {
                    y--;
                }
                this.lightDepths[x + z * this.width] = y;
                if (oldDepth != y) {
                    int yl0 = Math.min(oldDepth, y);
                    int yl1 = Math.max(oldDepth, y);
                    for (LevelListener listener : this.levelListeners) {
                        listener.lightColumnChanged(x, z, yl0, yl1);
                    }
                }
            }
        }
    }

    public void addListener(LevelListener levelListener) {
        this.levelListeners.add(levelListener);
    }

    public boolean isTile(int x, int y, int z) {
        return this.getTile(x, y, z) > 0;
    }

    public boolean isSolidTile(int x, int y, int z) {
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        return tile != null && tile.isSolid();
    }

    public boolean isLightBlocker(int x, int y, int z) {
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        return tile != null && tile.blocksLight();
    }

    public int getTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return 0;
        }
        return this.blocks[(y * this.height + z) * this.width + x] & 0xFF;
    }

    public boolean setTileNoUpdate(int x, int y, int z, int type) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return false;
        }
        int index = (y * this.height + z) * this.width + x;
        int old = this.blocks[index] & 0xFF;
        if (type == old) {
            return false;
        }
        this.blocks[index] = (byte) type;
        this.calcLightDepths(x, z, 1, 1);
        this.updateLight(x, y, z, old, type);
        return true;
    }

    private void updateLight(int x, int y, int z, int oldId, int newId) {
        Tile o = Tile.tiles[oldId];
        Tile n = Tile.tiles[newId];
        int oldOpacity = o == null ? 0 : o.getLightOpacity();
        int newOpacity = n == null ? 0 : n.getLightOpacity();
        int oldEmission = o == null ? 0 : o.getLightEmission();
        int newEmission = n == null ? 0 : n.getLightEmission();
        if (oldOpacity != newOpacity || oldEmission != newEmission) {
            this.light.blockChanged(x, y, z);
        }
    }

    void lightChanged(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (LevelListener listener : this.levelListeners) {
            listener.lightChanged(x0, y0, z0, x1, y1, z1);
        }
    }

    public boolean setTile(int x, int y, int z, int type) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return false;
        }
        int index = (y * this.height + z) * this.width + x;
        int old = this.blocks[index] & 0xFF;
        if (type == old) {
            return false;
        }
        this.blocks[index] = (byte) type;
        this.updateLight(x, y, z, old, type);

        this.updateNeighborAt(x - 1, y, z, type);
        this.updateNeighborAt(x + 1, y, z, type);
        this.updateNeighborAt(x, y - 1, z, type);
        this.updateNeighborAt(x, y + 1, z, type);
        this.updateNeighborAt(x, y, z - 1, type);
        this.updateNeighborAt(x, y, z + 1, type);

        this.calcLightDepths(x, z, 1, 1);
        for (LevelListener listener : this.levelListeners) {
            listener.tileChanged(x, y, z);
        }

        if (type > 0 && Tile.tiles[type] != null) {
            Tile.tiles[type].onPlace(this, x, y, z);
        }
        return true;
    }

    public void swap(int x0, int y0, int z0, int x1, int y1, int z1) {
        int t0 = this.getTile(x0, y0, z0);
        int t1 = this.getTile(x1, y1, z1);
        this.setTileNoUpdate(x0, y0, z0, t1);
        this.setTileNoUpdate(x1, y1, z1, t0);
        this.updateNeighborsAt(x0, y0, z0, t1);
        this.updateNeighborsAt(x1, y1, z1, t0);
        this.calcLightDepths(x0, z0, 1, 1);
        this.calcLightDepths(x1, z1, 1, 1);
        for (LevelListener listener : this.levelListeners) {
            listener.tileChanged(x0, y0, z0);
            listener.tileChanged(x1, y1, z1);
        }
    }

    private void updateNeighborsAt(int x, int y, int z, int type) {
        this.updateNeighborAt(x - 1, y, z, type);
        this.updateNeighborAt(x + 1, y, z, type);
        this.updateNeighborAt(x, y - 1, z, type);
        this.updateNeighborAt(x, y + 1, z, type);
        this.updateNeighborAt(x, y, z - 1, type);
        this.updateNeighborAt(x, y, z + 1, type);
    }

    private void updateNeighborAt(int x, int y, int z, int type) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return;
        }
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        if (tile != null) {
            tile.neighborChanged(this, x, y, z, type);
        }
    }

    public boolean isLit(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return true;
        }
        return y >= this.lightDepths[x + z * this.width];
    }

    public float getBrightness(int x, int y, int z) {
        return this.isLit(x, y, z) ? 1.0f : 0.6f;
    }

    /** Luz del cielo 0..15 (fuera del mapa por arriba o a los lados se considera cielo abierto). */
    public int getSkyLight(int x, int y, int z) {
        if (y >= this.depth) return LightEngine.MAX;
        if (y < 0) return 0;
        if (x < 0 || z < 0 || x >= this.width || z >= this.height) return LightEngine.MAX;
        return this.light.sky[(y * this.height + z) * this.width + x];
    }

    /** Luz de bloque 0..15 emitida por faroles y lava. */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) return 0;
        return this.light.block[(y * this.height + z) * this.width + x];
    }

    public List<AABB> getCubes(AABB aabb) {
        List<AABB> aabbs = new ArrayList<>();
        int x0 = (int) Math.floor(aabb.x0);
        int x1 = (int) Math.floor(aabb.x1 + 1.0f);
        int y0 = (int) Math.floor(aabb.y0);
        int y1 = (int) Math.floor(aabb.y1 + 1.0f);
        int z0 = (int) Math.floor(aabb.z0);
        int z1 = (int) Math.floor(aabb.z1 + 1.0f);

        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (z0 < 0) z0 = 0;
        if (x1 > this.width) x1 = this.width;
        if (y1 > this.depth) y1 = this.depth;
        if (z1 > this.height) z1 = this.height;

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Tile tile = Tile.tiles[this.getTile(x, y, z)];
                    if (tile != null) {
                        AABB box = tile.getAABB(x, y, z);
                        if (box != null) {
                            aabbs.add(box);
                        }
                    }
                }
            }
        }
        return aabbs;
    }

    public boolean containsAnyLiquid(AABB box) {
        int x0 = (int) Math.floor(box.x0);
        int x1 = (int) Math.floor(box.x1 + 1.0f);
        int y0 = (int) Math.floor(box.y0);
        int y1 = (int) Math.floor(box.y1 + 1.0f);
        int z0 = (int) Math.floor(box.z0);
        int z1 = (int) Math.floor(box.z1 + 1.0f);

        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (z0 < 0) z0 = 0;
        if (x1 > this.width) x1 = this.width;
        if (y1 > this.depth) y1 = this.depth;
        if (z1 > this.height) z1 = this.height;

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Tile tile = Tile.tiles[this.getTile(x, y, z)];
                    if (tile != null && tile.getLiquidType() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean containsLiquid(AABB box, int liquidId) {
        int x0 = (int) Math.floor(box.x0);
        int x1 = (int) Math.floor(box.x1 + 1.0f);
        int y0 = (int) Math.floor(box.y0);
        int y1 = (int) Math.floor(box.y1 + 1.0f);
        int z0 = (int) Math.floor(box.z0);
        int z1 = (int) Math.floor(box.z1 + 1.0f);

        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (z0 < 0) z0 = 0;
        if (x1 > this.width) x1 = this.width;
        if (y1 > this.depth) y1 = this.depth;
        if (z1 > this.height) z1 = this.height;

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Tile tile = Tile.tiles[this.getTile(x, y, z)];
                    if (tile != null && tile.getLiquidType() == liquidId) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void addToTickNextTick(int x, int y, int z, int id) {
        if (this.tickList.size() < 2000) {
            this.tickList.add(new TickEntry(x, y, z, id));
        }
    }

    public void tick() {
        // Procesar ticks programados (agua/lava)
        int size = this.tickList.size();
        for (int i = 0; i < size; i++) {
            TickEntry entry = this.tickList.poll();
            if (entry.ticks > 0) {
                entry.ticks--;
                this.tickList.add(entry);
            } else {
                int currentId = this.getTile(entry.x, entry.y, entry.z);
                if (currentId == entry.id && currentId > 0 && Tile.tiles[currentId] != null) {
                    Tile.tiles[currentId].tick(this, entry.x, entry.y, entry.z, this.random);
                }
            }
        }

        // Procesar ticks aleatorios (propagación de césped y crecimiento de árboles)
        this.unprocessed += this.width * this.height * this.depth;
        int ticks = this.unprocessed / TILE_UPDATE_INTERVAL;
        this.unprocessed -= ticks * TILE_UPDATE_INTERVAL;

        for (int i = 0; i < ticks; i++) {
            int x = this.random.nextInt(this.width);
            int y = this.random.nextInt(this.depth);
            int z = this.random.nextInt(this.height);
            Tile tile = Tile.tiles[this.getTile(x, y, z)];
            if (tile != null) {
                tile.tick(this, x, y, z, this.random);
            }
        }
    }

    public boolean maybeGrowTree(int x, int y, int z, Random rand) {
        int treeHeight = rand.nextInt(3) + 4;
        for (int yy = y; yy <= y + 1 + treeHeight; yy++) {
            int r = (yy == y) ? 0 : (yy >= y + treeHeight - 1 ? 2 : 1);
            for (int xx = x - r; xx <= x + r; xx++) {
                for (int zz = z - r; zz <= z + r; zz++) {
                    if (xx < 0 || yy < 0 || zz < 0 || xx >= this.width || yy >= this.depth || zz >= this.height) {
                        return false;
                    }
                    int id = this.getTile(xx, yy, zz);
                    if (id != 0 && !Tile.tiles[id].isReplaceable()) {
                        return false;
                    }
                }
            }
        }

        this.setTile(x, y - 1, z, Tile.dirt.id);
        for (int yy = y - 3 + treeHeight; yy <= y + treeHeight; yy++) {
            int yo = yy - (y + treeHeight);
            int r = 1 - yo / 2;
            for (int xx = x - r; xx <= x + r; xx++) {
                int xo = xx - x;
                for (int zz = z - r; zz <= z + r; zz++) {
                    int zo = zz - z;
                    if (Math.abs(xo) != r || Math.abs(zo) != r || (rand.nextInt(2) != 0 && yo != 0)) {
                        if (this.getTile(xx, yy, zz) == 0) {
                            this.setTile(xx, yy, zz, Tile.leaves.id);
                        }
                    }
                }
            }
        }
        for (int hh = 0; hh < treeHeight; hh++) {
            this.setTile(x, y + hh, z, Tile.log.id);
        }
        return true;
    }

    /**
     * Raycasting 3D exacto (algoritmo DDA de Amanatides & Woo) para detectar qué bloque y qué cara mira el jugador
     * en cualquier dirección (horizontal, vertical o diagonal).
     */
    public HitResult clip(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (Float.isNaN(x0) || Float.isNaN(y0) || Float.isNaN(z0)) return null;
        if (Float.isNaN(x1) || Float.isNaN(y1) || Float.isNaN(z1)) return null;

        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;

        int ix = (int) Math.floor(x0);
        int iy = (int) Math.floor(y0);
        int iz = (int) Math.floor(z0);

        int stepX = dx > 0.0f ? 1 : (dx < 0.0f ? -1 : 0);
        int stepY = dy > 0.0f ? 1 : (dy < 0.0f ? -1 : 0);
        int stepZ = dz > 0.0f ? 1 : (dz < 0.0f ? -1 : 0);

        float tDeltaX = stepX != 0 ? Math.abs(1.0f / dx) : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? Math.abs(1.0f / dy) : Float.POSITIVE_INFINITY;
        float tDeltaZ = stepZ != 0 ? Math.abs(1.0f / dz) : Float.POSITIVE_INFINITY;

        float tMaxX = stepX > 0 ? (ix + 1.0f - x0) * tDeltaX : (stepX < 0 ? (x0 - ix) * tDeltaX : Float.POSITIVE_INFINITY);
        float tMaxY = stepY > 0 ? (iy + 1.0f - y0) * tDeltaY : (stepY < 0 ? (y0 - iy) * tDeltaY : Float.POSITIVE_INFINITY);
        float tMaxZ = stepZ > 0 ? (iz + 1.0f - z0) * tDeltaZ : (stepZ < 0 ? (z0 - iz) * tDeltaZ : Float.POSITIVE_INFINITY);

        int maxSteps = 32;
        int face;

        for (int i = 0; i < maxSteps; i++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > 1.0f) break;
                ix += stepX;
                tMaxX += tDeltaX;
                face = stepX > 0 ? 4 : 5;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1.0f) break;
                iy += stepY;
                tMaxY += tDeltaY;
                face = stepY > 0 ? 0 : 1;
            } else {
                if (tMaxZ > 1.0f) break;
                iz += stepZ;
                tMaxZ += tDeltaZ;
                face = stepZ > 0 ? 2 : 3;
            }

            int id = this.getTile(ix, iy, iz);
            Tile tile = Tile.tiles[id];
            if (tile != null && tile.mayPick()) {
                return new HitResult(ix, iy, iz, face);
            }
        }
        return null;
    }
}
