package com.mojang.minecraft;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.phys.AABB;

import java.util.List;

/**
 * Jugador en primera persona de Minecraft Classic 0.0.11a / 0.0.14a.
 * Incluye movimiento WASD, salto, gravedad, colisiones AABB, física de nado en agua/lava,
 * carrera (Ctrl), modo vuelo (V) y balanceo de cámara al caminar.
 */
public class Player {
    private Level level;
    public float xo, yo, zo;
    public float x, y, z;
    public float xd, yd, zd;
    public float yRot, xRot;
    public AABB bb;
    public boolean onGround = false;
    public boolean horizontalCollision = false;
    public float heightOffset = 1.62f;
    public boolean flying = false;
    public boolean sprinting = false;
    // Balanceo de cámara al caminar (valores actuales y del tick anterior para interpolar)
    public float walkDist, walkDistO;
    public float bob, bobO;
    private final float bbWidth = 0.6f;
    private final float bbHeight = 1.8f;

    public Player(Level level) {
        this.level = level;
        this.resetPos();
    }

    public void setLevel(Level level) {
        this.level = level;
        this.resetPos();
    }

    public void resetPos() {
        this.setPos(this.level.xSpawn + 0.5f, this.level.ySpawn + 1.5f, this.level.zSpawn + 0.5f);
        this.xd = 0.0f;
        this.yd = 0.0f;
        this.zd = 0.0f;
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        float w = this.bbWidth / 2.0f;
        float h = this.bbHeight / 2.0f;
        this.bb = new AABB(x - w, y - h, z - w, x + w, y + h, z + w);
    }

    public void turn(float xo, float yo) {
        this.yRot += xo * 0.15f;
        this.xRot -= yo * 0.15f;
        if (this.xRot < -90.0f) {
            this.xRot = -90.0f;
        }
        if (this.xRot > 90.0f) {
            this.xRot = 90.0f;
        }
    }

    public void tick(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean down, boolean sprint) {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.walkDistO = this.walkDist;
        this.bobO = this.bob;

        float xa = 0.0f;
        float za = 0.0f;

        boolean inWater = this.isInWater();
        boolean inLava = this.isInLava();

        if (forward) za -= 1.0f;
        if (back) za += 1.0f;
        if (left) xa -= 1.0f;
        if (right) xa += 1.0f;

        this.sprinting = sprint && forward && !back && !this.horizontalCollision;

        if (this.flying) {
            // Vuelo libre: sin gravedad, Espacio sube y Shift baja
            float speed = this.sprinting ? 0.11f : 0.06f;
            this.moveRelative(xa, za, speed);
            if (jump) this.yd += 0.06f;
            if (down) this.yd -= 0.06f;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.8f;
            this.yd *= 0.6f;
            this.zd *= 0.8f;
            this.updateBob(false);
            return;
        }

        if (inWater || inLava) {
            if (jump) {
                this.yd += 0.04f;
            }
        } else if (jump && this.onGround) {
            this.yd = 0.42f;
            if (this.sprinting) {
                // Pequeño impulso hacia delante al saltar corriendo
                float yRotRad = (float) Math.toRadians(this.yRot);
                this.xd += (float) Math.sin(yRotRad) * 0.2f;
                this.zd -= (float) Math.cos(yRotRad) * 0.2f;
            }
        }

        if (inWater) {
            float yo = this.y;
            this.moveRelative(xa, za, 0.02f);
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.8f;
            this.yd *= 0.8f;
            this.zd *= 0.8f;
            this.yd -= 0.02f;
            if (this.horizontalCollision && this.isFree(this.xd, this.yd + 0.6f - this.y + yo, this.zd)) {
                this.yd = 0.3f;
            }
        } else if (inLava) {
            float yo = this.y;
            this.moveRelative(xa, za, 0.02f);
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.5f;
            this.yd *= 0.5f;
            this.zd *= 0.5f;
            this.yd -= 0.02f;
            if (this.horizontalCollision && this.isFree(this.xd, this.yd + 0.6f - this.y + yo, this.zd)) {
                this.yd = 0.3f;
            }
        } else {
            float accel = this.onGround ? 0.1f : 0.02f;
            if (this.sprinting) accel *= 1.3f;
            this.moveRelative(xa, za, accel);
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.91f;
            this.yd *= 0.98f;
            this.zd *= 0.91f;
            this.yd -= 0.08f;
            if (this.onGround) {
                this.xd *= 0.6f;
                this.zd *= 0.6f;
            }
        }
        this.updateBob(!inWater && !inLava);
    }

    private void updateBob(boolean canBob) {
        float dx = this.x - this.xo;
        float dz = this.z - this.zo;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        this.walkDist += dist * 0.6f;
        float target = (canBob && this.onGround) ? Math.min(0.1f, dist) : 0.0f;
        this.bob += (target - this.bob) * 0.4f;
    }

    public void setRotation(float yRot, float xRot) {
        this.yRot = yRot;
        this.xRot = Math.max(-90.0f, Math.min(90.0f, xRot));
    }

    public boolean isInWater() {
        return this.level.containsLiquid(this.bb.grow(0.0f, -0.4f, 0.0f), 1);
    }

    public boolean isInLava() {
        return this.level.containsLiquid(this.bb.grow(0.0f, -0.4f, 0.0f), 2);
    }

    public boolean isUnderWater() {
        int xt = (int) Math.floor(this.x);
        int yt = (int) Math.floor(this.y + 0.12f);
        int zt = (int) Math.floor(this.z);
        int id = this.level.getTile(xt, yt, zt);
        return id == 8 || id == 9;
    }

    public boolean isFree(float xa, float ya, float za) {
        AABB box = this.bb.cloneMove(xa, ya, za);
        List<AABB> aabbs = this.level.getCubes(box);
        if (!aabbs.isEmpty()) {
            return false;
        }
        return !this.level.containsAnyLiquid(box);
    }

    public void move(float xa, float ya, float za) {
        float xaOrg = xa;
        float yaOrg = ya;
        float zaOrg = za;

        List<AABB> aabbs = this.level.getCubes(this.bb.expand(xa, ya, za));

        for (AABB aabb : aabbs) {
            ya = aabb.clipYCollide(this.bb, ya);
        }
        this.bb.move(0.0f, ya, 0.0f);

        for (AABB aabb : aabbs) {
            xa = aabb.clipXCollide(this.bb, xa);
        }
        this.bb.move(xa, 0.0f, 0.0f);

        for (AABB aabb : aabbs) {
            za = aabb.clipZCollide(this.bb, za);
        }
        this.bb.move(0.0f, 0.0f, za);

        this.horizontalCollision = (xaOrg != xa || zaOrg != za);
        this.onGround = (yaOrg != ya && yaOrg < 0.0f);

        if (xaOrg != xa) this.xd = 0.0f;
        if (yaOrg != ya) this.yd = 0.0f;
        if (zaOrg != za) this.zd = 0.0f;

        this.x = (this.bb.x0 + this.bb.x1) / 2.0f;
        this.y = this.bb.y0 + this.heightOffset;
        this.z = (this.bb.z0 + this.bb.z1) / 2.0f;
    }

    public void moveRelative(float xa, float za, float speed) {
        float dist = xa * xa + za * za;
        if (dist < 0.01f) {
            return;
        }
        dist = speed / (float) Math.sqrt(dist);
        xa *= dist;
        za *= dist;

        float sin = (float) Math.sin(this.yRot * Math.PI / 180.0);
        float cos = (float) Math.cos(this.yRot * Math.PI / 180.0);

        this.xd += xa * cos - za * sin;
        this.zd += za * cos + xa * sin;
    }
}
