package com.mojang.minecraft.level;

import com.mojang.minecraft.level.tile.Tile;

import java.util.Arrays;

/**
 * Motor de iluminación por propagación (flood fill / BFS) con dos canales de 0 a 15:
 *   - Luz del cielo: baja sin perder intensidad por el aire y se difunde lateralmente bajo aleros y cuevas.
 *   - Luz de bloque: emitida por faroles y lava.
 * Cada bloque atenúa la luz según Tile.getLightOpacity() (aire 0, hojas 1, agua 2, opacos 15).
 * Los cambios de bloques se resuelven de forma incremental: se borra la luz que dependía
 * del bloque cambiado y se vuelve a propagar desde el borde iluminado.
 */
public class LightEngine {
    public static final int MAX = 15;

    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private final Level level;
    private final int w;
    private final int h;
    private final int d;
    private final int[] offsets;

    public final byte[] sky;
    public final byte[] block;

    private final IntQueue addQueue = new IntQueue();
    private final IntQueue removeQueue = new IntQueue();

    private boolean touched;
    private int minX, minY, minZ, maxX, maxY, maxZ;

    public LightEngine(Level level) {
        this.level = level;
        this.w = level.width;
        this.h = level.height;
        this.d = level.depth;
        this.sky = new byte[this.w * this.h * this.d];
        this.block = new byte[this.w * this.h * this.d];
        this.offsets = new int[6];
        for (int i = 0; i < 6; i++) {
            this.offsets[i] = DX[i] + (DY[i] * this.h + DZ[i]) * this.w;
        }
    }

    private int opacity(int index) {
        Tile tile = Tile.tiles[this.level.blocks[index] & 0xFF];
        return tile == null ? 0 : tile.getLightOpacity();
    }

    private int emission(int index) {
        Tile tile = Tile.tiles[this.level.blocks[index] & 0xFF];
        return tile == null ? 0 : tile.getLightEmission();
    }

    /** Recalcula toda la luz del mapa (al generar o cargar un mundo). */
    public void recalcAll() {
        Arrays.fill(this.sky, (byte) 0);
        Arrays.fill(this.block, (byte) 0);
        this.addQueue.clear();
        this.removeQueue.clear();

        for (int x = 0; x < this.w; x++) {
            for (int z = 0; z < this.h; z++) {
                int v = MAX;
                for (int y = this.d - 1; y >= 0; y--) {
                    int i = (y * this.h + z) * this.w + x;
                    int op = this.opacity(i);
                    if (op >= MAX) break;
                    int nv = (v == MAX && op == 0) ? MAX : v - 1 - op;
                    if (nv <= 0) break;
                    this.sky[i] = (byte) nv;
                    this.addQueue.push(i);
                    v = nv;
                }
            }
        }
        this.propagateAdd(this.sky, true);

        for (int i = 0; i < this.block.length; i++) {
            int e = this.emission(i);
            if (e > 0) {
                this.block[i] = (byte) e;
                this.addQueue.push(i);
            }
        }
        this.propagateAdd(this.block, false);
        this.touched = false;
    }

    /** Actualiza la luz alrededor de un bloque que acaba de cambiar y notifica la zona afectada. */
    public void blockChanged(int x, int y, int z) {
        this.touched = false;
        int i = (y * this.h + z) * this.w + x;
        this.updateChannel(this.sky, true, i, x, y, z);
        this.updateChannel(this.block, false, i, x, y, z);
        if (this.touched) {
            this.level.lightChanged(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }

    private void updateChannel(byte[] arr, boolean isSky, int i, int x, int y, int z) {
        int old = arr[i];
        if (old > 0) {
            arr[i] = 0;
            this.mark(x, y, z);
            this.removeQueue.push(i << 4 | old);
        }
        this.propagateRemove(arr, isSky);

        if (!isSky) {
            int e = this.emission(i);
            if (e > arr[i]) {
                arr[i] = (byte) e;
                this.mark(x, y, z);
                this.addQueue.push(i);
            }
        } else if (y == this.d - 1) {
            int op = this.opacity(i);
            if (op < MAX) {
                int nv = op == 0 ? MAX : MAX - 1 - op;
                if (nv > arr[i]) {
                    arr[i] = (byte) nv;
                    this.mark(x, y, z);
                    this.addQueue.push(i);
                }
            }
        }

        for (int dir = 0; dir < 6; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            int nz = z + DZ[dir];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= this.w || ny >= this.d || nz >= this.h) continue;
            int n = i + this.offsets[dir];
            if (arr[n] > 0) {
                this.addQueue.push(n);
            }
        }
        this.propagateAdd(arr, isSky);
    }

    private void propagateRemove(byte[] arr, boolean isSky) {
        int wh = this.w * this.h;
        while (!this.removeQueue.isEmpty()) {
            int entry = this.removeQueue.pop();
            int i = entry >>> 4;
            int v = entry & 15;
            int x = i % this.w;
            int z = (i / this.w) % this.h;
            int y = i / wh;
            for (int dir = 0; dir < 6; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                int nz = z + DZ[dir];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= this.w || ny >= this.d || nz >= this.h) continue;
                int n = i + this.offsets[dir];
                int nl = arr[n];
                if (nl == 0) continue;
                boolean dependent = nl < v || (isSky && dir == 0 && v == MAX);
                if (dependent) {
                    arr[n] = 0;
                    this.mark(nx, ny, nz);
                    this.removeQueue.push(n << 4 | nl);
                    if (!isSky) {
                        int e = this.emission(n);
                        if (e > 0) {
                            arr[n] = (byte) e;
                            this.addQueue.push(n);
                        }
                    }
                } else {
                    this.addQueue.push(n);
                }
            }
        }
    }

    private void propagateAdd(byte[] arr, boolean isSky) {
        int wh = this.w * this.h;
        while (!this.addQueue.isEmpty()) {
            int i = this.addQueue.pop();
            int v = arr[i];
            if (v <= 1) continue;
            int x = i % this.w;
            int z = (i / this.w) % this.h;
            int y = i / wh;
            for (int dir = 0; dir < 6; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                int nz = z + DZ[dir];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= this.w || ny >= this.d || nz >= this.h) continue;
                int n = i + this.offsets[dir];
                int op = this.opacity(n);
                if (op >= MAX) continue;
                int nv = (isSky && dir == 0 && v == MAX && op == 0) ? MAX : v - 1 - op;
                if (nv > arr[n]) {
                    arr[n] = (byte) nv;
                    this.mark(nx, ny, nz);
                    this.addQueue.push(n);
                }
            }
        }
    }

    private void mark(int x, int y, int z) {
        if (!this.touched) {
            this.touched = true;
            this.minX = this.maxX = x;
            this.minY = this.maxY = y;
            this.minZ = this.maxZ = z;
            return;
        }
        if (x < this.minX) this.minX = x;
        if (y < this.minY) this.minY = y;
        if (z < this.minZ) this.minZ = z;
        if (x > this.maxX) this.maxX = x;
        if (y > this.maxY) this.maxY = y;
        if (z > this.maxZ) this.maxZ = z;
    }

    /** Cola circular de enteros que crece sola (evita crear millones de objetos Integer). */
    private static final class IntQueue {
        private int[] data = new int[1 << 16];
        private int head;
        private int tail;
        private int size;

        void push(int value) {
            if (this.size == this.data.length) {
                int[] grown = new int[this.data.length * 2];
                for (int k = 0; k < this.size; k++) {
                    grown[k] = this.data[(this.head + k) & (this.data.length - 1)];
                }
                this.data = grown;
                this.head = 0;
                this.tail = this.size;
            }
            this.data[this.tail] = value;
            this.tail = (this.tail + 1) & (this.data.length - 1);
            this.size++;
        }

        int pop() {
            int value = this.data[this.head];
            this.head = (this.head + 1) & (this.data.length - 1);
            this.size--;
            return value;
        }

        boolean isEmpty() {
            return this.size == 0;
        }

        void clear() {
            this.head = 0;
            this.tail = 0;
            this.size = 0;
        }
    }
}
