package com.mojang.minecraft.particle;

import com.mojang.minecraft.Player;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Motor de partículas de Minecraft Classic 0.0.11a / 0.0.14a.
 * Actualiza y dibuja todas las partículas activas orientadas siempre hacia la cámara (billboards).
 */
public class ParticleEngine {
    protected Level level;
    private final List<Particle> particles = new ArrayList<>();
    private final Textures textures;

    public ParticleEngine(Level level, Textures textures) {
        this.level = level;
        this.textures = textures;
    }

    public void setLevel(Level level) {
        this.level = level;
        this.particles.clear();
    }

    public void add(Particle p) {
        this.particles.add(p);
    }

    public void tick() {
        for (int i = 0; i < this.particles.size(); i++) {
            Particle p = this.particles.get(i);
            p.tick();
            if (p.removed) {
                this.particles.remove(i--);
            }
        }
    }

    public void render(Player player, float a) {
        if (this.particles.isEmpty()) {
            return;
        }

        glEnable(GL_TEXTURE_2D);
        int id = this.textures.loadTexture("/terrain.png", GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, id);

        float xa = -(float) Math.cos(player.yRot * Math.PI / 180.0);
        float za = -(float) Math.sin(player.yRot * Math.PI / 180.0);
        float xa2 = -za * (float) Math.sin(player.xRot * Math.PI / 180.0);
        float za2 = xa * (float) Math.sin(player.xRot * Math.PI / 180.0);
        float ya = (float) Math.cos(player.xRot * Math.PI / 180.0);

        Tesselator t = Tesselator.instance;
        t.begin();
        for (Particle p : this.particles) {
            p.render(t, a, xa, ya, za, xa2, za2);
        }
        t.end();
        glDisable(GL_TEXTURE_2D);
    }
}
