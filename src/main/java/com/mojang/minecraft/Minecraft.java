package com.mojang.minecraft;

import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Font;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.SkyRenderer;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;
import com.mojang.minecraft.renderer.WorldShader;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Clase principal de Minecraft Classic 0.0.11a / 0.0.14a (versión mejorada).
 * Inicializa la ventana GLFW y el contexto OpenGL, ejecuta el Game Loop (20 TPS),
 * procesa entradas de teclado/ratón, calcula el Raycasting y renderiza el mundo con
 * iluminación dinámica, ciclo día/noche, nubes y el bloque en la mano.
 */
public class Minecraft implements Runnable {
    public static final String VERSION_STRING = "0.0.14a+ (mejorado)";
    private static final int AUTOSAVE_TICKS = 20 * 120;
    private static final int ACTION_REPEAT_TICKS = 5;
    private static final int SWING_TICKS = 6;

    private int width = 1024;
    private int height = 768;
    private int windowWidth = 1024;
    private int windowHeight = 768;
    private long window;
    private boolean fullscreen = false;
    private final int[] windowedBounds = new int[4];

    private final Timer timer = new Timer(20.0f);
    Level level;
    private LevelRenderer levelRenderer;
    Player player;
    private ParticleEngine particleEngine;
    private Textures textures;
    private Font font;
    private Gui gui;
    SkyRenderer sky;
    private WorldShader worldShader;

    final int[] hotbar = Gui.DEFAULT_HOTBAR.clone();
    int selectedSlot = 0;
    int renderDistance = 0; // 0..3 (Distancia de niebla con tecla F)
    boolean showHelp = true;
    boolean showDebug = false;
    boolean inventoryOpen = false;
    boolean cameraInLava = false;

    private boolean mouseGrabbed = false;
    private double lastMouseX = 0.0;
    private double lastMouseY = 0.0;
    private boolean firstMouse = true;
    float guiMouseX = 0.0f;
    float guiMouseY = 0.0f;

    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int actionCooldown = 0;
    private int swingTick = -1;
    private int swingTickO = -1;
    private float fovModifier = 1.0f;
    private long regenRequestTime = 0L;

    private HitResult hitResult = null;
    String fpsString = "";
    private int ticks = 0;

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("No se pudo inicializar GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        this.window = glfwCreateWindow(this.width, this.height, "Minecraft " + VERSION_STRING, NULL, NULL);
        if (this.window == NULL) {
            throw new RuntimeException("Error al crear la ventana GLFW");
        }

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidmode != null) {
            glfwSetWindowPos(
                    this.window,
                    (vidmode.width() - this.width) / 2,
                    (vidmode.height() - this.height) / 2
            );
        }

        this.setupCallbacks();

        glfwMakeContextCurrent(this.window);
        glfwSwapInterval(1); // V-Sync activado
        glfwShowWindow(this.window);

        GL.createCapabilities();

        int[] fbw = new int[1];
        int[] fbh = new int[1];
        glfwGetFramebufferSize(this.window, fbw, fbh);
        this.width = Math.max(1, fbw[0]);
        this.height = Math.max(1, fbh[0]);
        glfwGetWindowSize(this.window, fbw, fbh);
        this.windowWidth = Math.max(1, fbw[0]);
        this.windowHeight = Math.max(1, fbh[0]);
        glViewport(0, 0, this.width, this.height);

        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearDepth(1.0);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glEnable(GL_CULL_FACE);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);

        this.textures = new Textures();
        this.textures.loadTexture("/terrain.png", GL_NEAREST);
        this.font = new Font();
        this.gui = new Gui(this.font, this.textures);
        this.sky = new SkyRenderer(this.textures);
        this.worldShader = new WorldShader();

        this.level = new Level(256, 256, 64);
        this.levelRenderer = new LevelRenderer(this.level, this.textures);
        this.player = new Player(this.level);
        this.particleEngine = new ParticleEngine(this.level, this.textures);
        this.restorePlayerFromLevel();

        this.grabMouse();
    }

    private void restorePlayerFromLevel() {
        float[] p = this.level.savedPlayer;
        if (p != null) {
            this.player.setPos(p[0], p[1], p[2]);
            this.player.setRotation(p[3], p[4]);
            this.player.flying = this.level.savedFlying;
        }
        int[] saved = this.level.savedHotbar;
        if (saved != null && saved.length == this.hotbar.length) {
            for (int i = 0; i < saved.length; i++) {
                if (Gui.isPlaceable(saved[i])) {
                    this.hotbar[i] = saved[i];
                }
            }
        }
    }

    private void storePlayerInLevel() {
        this.level.savedPlayer = new float[]{this.player.x, this.player.y, this.player.z, this.player.yRot, this.player.xRot};
        this.level.savedFlying = this.player.flying;
        this.level.savedHotbar = this.hotbar.clone();
    }

    private void setupCallbacks() {
        glfwSetFramebufferSizeCallback(this.window, (win, w, h) -> {
            if (w > 0 && h > 0) {
                this.width = w;
                this.height = h;
                glViewport(0, 0, w, h);
            }
        });

        glfwSetWindowSizeCallback(this.window, (win, w, h) -> {
            if (w > 0 && h > 0) {
                this.windowWidth = w;
                this.windowHeight = h;
            }
        });

        glfwSetCursorPosCallback(this.window, (win, xpos, ypos) -> {
            this.guiMouseX = (float) (xpos * Gui.guiWidth(this.width, this.height) / this.windowWidth);
            this.guiMouseY = (float) (ypos * 240.0 / this.windowHeight);
            if (!this.mouseGrabbed) {
                return;
            }
            if (this.firstMouse) {
                this.lastMouseX = xpos;
                this.lastMouseY = ypos;
                this.firstMouse = false;
                return;
            }
            float dx = (float) (xpos - this.lastMouseX);
            float dy = (float) (this.lastMouseY - ypos);
            this.lastMouseX = xpos;
            this.lastMouseY = ypos;
            this.player.turn(dx, dy);
        });

        glfwSetScrollCallback(this.window, (win, xoffset, yoffset) -> {
            if (yoffset > 0) {
                this.selectedSlot = (this.selectedSlot - 1 + this.hotbar.length) % this.hotbar.length;
            } else if (yoffset < 0) {
                this.selectedSlot = (this.selectedSlot + 1) % this.hotbar.length;
            }
        });

        glfwSetMouseButtonCallback(this.window, (win, button, action, mods) -> {
            if (action == GLFW_RELEASE) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) this.leftHeld = false;
                if (button == GLFW_MOUSE_BUTTON_RIGHT) this.rightHeld = false;
                return;
            }
            if (action != GLFW_PRESS) {
                return;
            }
            if (this.inventoryOpen) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    int slot = this.gui.inventorySlotAt(Gui.guiWidth(this.width, this.height), 240, this.guiMouseX, this.guiMouseY);
                    if (slot >= 0) {
                        this.hotbar[this.selectedSlot] = Gui.ALL_TILES[slot];
                        this.setInventoryOpen(false);
                    }
                }
                return;
            }
            if (!this.mouseGrabbed) {
                this.grabMouse();
                return;
            }
            if (button == GLFW_MOUSE_BUTTON_LEFT) this.leftHeld = true;
            if (button == GLFW_MOUSE_BUTTON_RIGHT) this.rightHeld = true;
            this.handleMouseClick(button);
            this.actionCooldown = ACTION_REPEAT_TICKS;
        });

        glfwSetKeyCallback(this.window, (win, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) {
                return;
            }
            if (key == GLFW_KEY_ESCAPE) {
                if (this.inventoryOpen) {
                    this.setInventoryOpen(false);
                } else if (this.mouseGrabbed) {
                    this.releaseMouse();
                } else {
                    glfwSetWindowShouldClose(this.window, true);
                }
                return;
            }
            if (key == GLFW_KEY_E) {
                this.setInventoryOpen(!this.inventoryOpen);
                return;
            }
            if (key == GLFW_KEY_F11) {
                this.toggleFullscreen();
                return;
            }
            if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                int idx = key - GLFW_KEY_1;
                if (idx < this.hotbar.length) {
                    this.selectedSlot = idx;
                }
                return;
            }
            if (this.inventoryOpen || !this.mouseGrabbed) {
                return;
            }
            if (key == GLFW_KEY_R) {
                this.player.resetPos();
                this.gui.showMessage("De vuelta al punto de aparición");
            } else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
                this.level.xSpawn = (int) this.player.x;
                this.level.ySpawn = (int) this.player.y;
                this.level.zSpawn = (int) this.player.z;
                this.gui.showMessage("Punto de aparición fijado aquí");
            } else if (key == GLFW_KEY_G) {
                long now = System.currentTimeMillis();
                if (now - this.regenRequestTime < 3000L) {
                    this.regenRequestTime = 0L;
                    this.level.generateNewMap();
                    this.particleEngine.setLevel(this.level);
                    this.player.resetPos();
                    this.gui.showMessage("¡Mundo nuevo generado!");
                } else {
                    this.regenRequestTime = now;
                    this.gui.showMessage("Pulsa G otra vez para crear un mundo nuevo (se perderá el actual)");
                }
            } else if (key == GLFW_KEY_F) {
                this.renderDistance = (this.renderDistance + 1) % 4;
                this.gui.showMessage("Distancia de visión: " + (512 >> this.renderDistance) + " bloques");
            } else if (key == GLFW_KEY_V) {
                this.player.flying = !this.player.flying;
                this.player.yd = 0.0f;
                this.gui.showMessage(this.player.flying ? "Modo vuelo activado (Espacio sube, Shift baja)" : "Modo vuelo desactivado");
            } else if (key == GLFW_KEY_H || key == GLFW_KEY_F1) {
                this.showHelp = !this.showHelp;
            } else if (key == GLFW_KEY_F3) {
                this.showDebug = !this.showDebug;
            }
        });
    }

    boolean isPaused() {
        return !this.mouseGrabbed && !this.inventoryOpen;
    }

    private void setInventoryOpen(boolean open) {
        this.inventoryOpen = open;
        this.leftHeld = false;
        this.rightHeld = false;
        if (open) {
            this.releaseMouse();
        } else {
            this.grabMouse();
        }
    }

    private void toggleFullscreen() {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode == null) return;
        if (!this.fullscreen) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetWindowPos(this.window, x, y);
            glfwGetWindowSize(this.window, w, h);
            this.windowedBounds[0] = x[0];
            this.windowedBounds[1] = y[0];
            this.windowedBounds[2] = w[0];
            this.windowedBounds[3] = h[0];
            glfwSetWindowMonitor(this.window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(this.window, NULL, this.windowedBounds[0], this.windowedBounds[1],
                    this.windowedBounds[2], this.windowedBounds[3], GLFW_DONT_CARE);
        }
        this.fullscreen = !this.fullscreen;
        glfwSwapInterval(1);
        this.firstMouse = true;
    }

    private void grabMouse() {
        if (!this.mouseGrabbed) {
            this.mouseGrabbed = true;
            this.firstMouse = true;
            glfwSetInputMode(this.window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }
    }

    private void releaseMouse() {
        if (this.mouseGrabbed) {
            this.mouseGrabbed = false;
            this.leftHeld = false;
            this.rightHeld = false;
            glfwSetInputMode(this.window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
    }

    private void swing() {
        if (this.swingTick < 0 || this.swingTick >= SWING_TICKS / 2) {
            this.swingTick = 0;
        }
    }

    private void handleMouseClick(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            this.swing();
        }
        if (this.hitResult == null) {
            return;
        }

        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            // Romper bloque (excepto Bedrock)
            int oldTileId = this.level.getTile(this.hitResult.x, this.hitResult.y, this.hitResult.z);
            if (oldTileId > 0 && oldTileId != Tile.unbreakable.id) {
                Tile oldTile = Tile.tiles[oldTileId];
                boolean changed = this.level.setTile(this.hitResult.x, this.hitResult.y, this.hitResult.z, 0);
                if (oldTile != null && changed) {
                    oldTile.destroy(this.level, this.hitResult.x, this.hitResult.y, this.hitResult.z, this.particleEngine);
                }
            }
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            // Colocar bloque en la cara seleccionada
            int x = this.hitResult.x;
            int y = this.hitResult.y;
            int z = this.hitResult.z;

            if (this.hitResult.face == 0) y--;
            if (this.hitResult.face == 1) y++;
            if (this.hitResult.face == 2) z--;
            if (this.hitResult.face == 3) z++;
            if (this.hitResult.face == 4) x--;
            if (this.hitResult.face == 5) x++;

            int current = this.level.getTile(x, y, z);
            Tile currentTile = Tile.tiles[current];
            boolean replaceable = current == 0 || (currentTile != null && (currentTile.getLiquidType() > 0 || currentTile.isReplaceable()));
            int paint = this.hotbar[this.selectedSlot];
            Tile toPlace = Tile.tiles[paint];
            if (toPlace != null && replaceable) {
                AABB aabb = toPlace.getAABB(x, y, z);
                if (aabb == null || !this.player.bb.intersects(aabb)) {
                    if (this.level.setTile(x, y, z, paint)) {
                        this.swing();
                    }
                }
            }
        } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
            // Pick Block: selecciona la casilla con ese bloque o lo pone en la casilla actual
            int targetId = this.level.getTile(this.hitResult.x, this.hitResult.y, this.hitResult.z);
            if (targetId == Tile.calmWater.id) targetId = Tile.water.id;
            if (targetId == Tile.calmLava.id) targetId = Tile.lava.id;
            if (Gui.isPlaceable(targetId)) {
                for (int i = 0; i < this.hotbar.length; i++) {
                    if (this.hotbar[i] == targetId) {
                        this.selectedSlot = i;
                        return;
                    }
                }
                this.hotbar[this.selectedSlot] = targetId;
            }
        }
    }

    @Override
    public void run() {
        try {
            this.init();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        long lastTime = System.currentTimeMillis();
        int frames = 0;

        try {
            while (!glfwWindowShouldClose(this.window)) {
                this.timer.advanceTime();
                for (int i = 0; i < this.timer.ticks; i++) {
                    this.tick();
                }

                this.render(this.isPaused() ? 1.0f : this.timer.a);
                frames++;

                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    this.fpsString = frames + " fps, " + Chunk.updates + " chunk updates";
                    Chunk.updates = 0;
                    lastTime += 1000L;
                    frames = 0;
                }
            }
        } finally {
            this.destroy();
        }
    }

    private boolean key(int k) {
        return glfwGetKey(this.window, k) == GLFW_PRESS;
    }

    private void tick() {
        if (this.isPaused()) {
            return;
        }
        this.ticks++;
        boolean playing = this.mouseGrabbed && !this.inventoryOpen;

        // Tecla T: el tiempo pasa 40 veces más rápido
        this.level.time += (playing && this.key(GLFW_KEY_T)) ? 40 : 1;
        this.level.tick();
        this.particleEngine.tick();

        boolean forward = playing && (this.key(GLFW_KEY_W) || this.key(GLFW_KEY_UP));
        boolean back = playing && (this.key(GLFW_KEY_S) || this.key(GLFW_KEY_DOWN));
        boolean left = playing && (this.key(GLFW_KEY_A) || this.key(GLFW_KEY_LEFT));
        boolean right = playing && (this.key(GLFW_KEY_D) || this.key(GLFW_KEY_RIGHT));
        boolean jump = playing && this.key(GLFW_KEY_SPACE);
        boolean down = playing && (this.key(GLFW_KEY_LEFT_SHIFT) || this.key(GLFW_KEY_RIGHT_SHIFT));
        boolean sprint = playing && (this.key(GLFW_KEY_LEFT_CONTROL) || this.key(GLFW_KEY_RIGHT_CONTROL));

        this.player.tick(forward, back, left, right, jump, down, sprint);

        // Mantener pulsado el ratón repite romper/colocar
        if (this.actionCooldown > 0) {
            this.actionCooldown--;
        }
        if (playing && this.actionCooldown == 0 && (this.leftHeld || this.rightHeld)) {
            this.handleMouseClick(this.leftHeld ? GLFW_MOUSE_BUTTON_LEFT : GLFW_MOUSE_BUTTON_RIGHT);
            this.actionCooldown = ACTION_REPEAT_TICKS;
        }

        this.swingTickO = this.swingTick;
        if (this.swingTick >= 0) {
            this.swingTick++;
            if (this.swingTick > SWING_TICKS) {
                this.swingTick = -1;
                this.swingTickO = -1;
            }
        }

        if (this.ticks % AUTOSAVE_TICKS == 0) {
            this.storePlayerInLevel();
            if (this.level.saveAsync()) {
                this.gui.showMessage("Mundo guardado");
            }
        }
    }

    private void pick(float a) {
        float x = this.player.xo + (this.player.x - this.player.xo) * a;
        float y = this.player.yo + (this.player.y - this.player.yo) * a;
        float z = this.player.zo + (this.player.z - this.player.zo) * a;

        float yRotRad = (float) Math.toRadians(this.player.yRot);
        float xRotRad = (float) Math.toRadians(this.player.xRot);

        float cosY = (float) Math.cos(yRotRad);
        float sinY = (float) Math.sin(yRotRad);
        float cosX = (float) Math.cos(xRotRad);
        float sinX = (float) Math.sin(xRotRad);

        float reach = 5.0f;
        float dx = sinY * cosX * reach;
        float dy = -sinX * reach;
        float dz = -cosY * cosX * reach;

        this.hitResult = this.level.clip(x, y, z, x + dx, y + dy, z + dz);
    }

    private void setupPerspective(float fovy, float aspect, float zNear, float zFar) {
        float ymax = zNear * (float) Math.tan(fovy * Math.PI / 360.0);
        float ymin = -ymax;
        float xmin = ymin * aspect;
        float xmax = ymax * aspect;
        glFrustum(xmin, xmax, ymin, ymax, zNear, zFar);
    }

    /** Balanceo de la cámara al caminar. */
    private void applyBob(float a) {
        if (this.player.flying) return;
        float walk = this.player.walkDistO + (this.player.walkDist - this.player.walkDistO) * a;
        float bob = this.player.bobO + (this.player.bob - this.player.bobO) * a;
        float w = walk * (float) Math.PI;
        glTranslatef((float) Math.sin(w) * bob * 0.5f, -Math.abs((float) Math.cos(w) * bob), 0.0f);
        glRotatef((float) Math.sin(w) * bob * 3.0f, 0.0f, 0.0f, 1.0f);
        glRotatef(Math.abs((float) Math.cos(w - 0.2f) * bob) * 5.0f, 1.0f, 0.0f, 0.0f);
    }

    private void moveCameraToPlayer(float a) {
        glRotatef(this.player.xRot, 1.0f, 0.0f, 0.0f);
        glRotatef(this.player.yRot, 0.0f, 1.0f, 0.0f);

        float x = this.player.xo + (this.player.x - this.player.xo) * a;
        float y = this.player.yo + (this.player.y - this.player.yo) * a;
        float z = this.player.zo + (this.player.z - this.player.zo) * a;
        glTranslatef(-x, -y, -z);
    }

    private float farPlane() {
        return (float) (512 >> this.renderDistance);
    }

    private void setupCamera(float a) {
        float targetFov = this.player.sprinting ? 1.12f : 1.0f;
        this.fovModifier += (targetFov - this.fovModifier) * 0.15f;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        this.setupPerspective(70.0f * this.fovModifier, (float) this.width / (float) this.height, 0.05f, this.farPlane());
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        this.applyBob(a);
        this.moveCameraToPlayer(a);
    }

    private void bindWorldShader(boolean fog) {
        this.worldShader.bind();
        this.worldShader.setDaylight(this.sky.daylight);
        float far = this.farPlane();
        float[] h = this.sky.horizon;
        if (!fog) {
            this.worldShader.setFog(WorldShader.FOG_NONE, 0, 0, 0, 0, 1, 0);
        } else if (this.cameraInLava) {
            this.worldShader.setFog(WorldShader.FOG_EXP, 0.6f, 0.15f, 0.0f, 0, 1, 1.5f);
        } else if (this.player.isUnderWater()) {
            float d = this.sky.daylight;
            this.worldShader.setFog(WorldShader.FOG_EXP, 0.02f * d, 0.07f * d, 0.25f * d, 0, 1, 0.09f);
        } else {
            this.worldShader.setFog(WorldShader.FOG_LINEAR, h[0], h[1], h[2], far * 0.4f, far, 0);
        }
    }

    private void render(float a) {
        glfwPollEvents();
        this.pick(a);

        this.sky.update(this.level.time, a);
        boolean underWater = this.player.isUnderWater();
        int eyeTile = this.level.getTile((int) Math.floor(this.player.x), (int) Math.floor(this.player.y + 0.12f), (int) Math.floor(this.player.z));
        this.cameraInLava = eyeTile == Tile.lava.id || eyeTile == Tile.calmLava.id;

        if (this.cameraInLava) {
            glClearColor(0.6f, 0.15f, 0.0f, 1.0f);
        } else if (underWater) {
            float d = this.sky.daylight;
            glClearColor(0.02f * d, 0.07f * d, 0.25f * d, 1.0f);
        } else {
            glClearColor(this.sky.horizon[0], this.sky.horizon[1], this.sky.horizon[2], 1.0f);
        }
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        this.setupCamera(a);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glDisable(GL_FOG);

        Frustum frustum = Frustum.getFrustum();
        this.levelRenderer.cull(frustum);
        this.levelRenderer.updateDirtyChunks(this.player);

        float cx = this.player.xo + (this.player.x - this.player.xo) * a;
        float cy = this.player.yo + (this.player.y - this.player.yo) * a;
        float cz = this.player.zo + (this.player.z - this.player.zo) * a;

        if (!underWater && !this.cameraInLava) {
            this.sky.renderSky(cx, cy, cz, this.farPlane());
        }

        // Capa 0: Bloques opacos, hojas/arbustos con test alfa y partículas
        this.bindWorldShader(true);
        this.levelRenderer.render(this.player, 0);
        this.particleEngine.render(this.player, a);
        this.worldShader.unbind();

        if (!underWater && !this.cameraInLava) {
            this.sky.renderClouds(cx, cz, this.farPlane(), this.level.time, a);
        }

        // Capa 1: Agua y elementos translúcidos
        this.bindWorldShader(true);
        this.levelRenderer.render(this.player, 1);
        this.worldShader.unbind();

        // Resaltado del bloque apuntado
        if (this.hitResult != null && !this.isPaused() && !this.inventoryOpen) {
            glDisable(GL_ALPHA_TEST);
            this.levelRenderer.renderHit(this.hitResult);
            glEnable(GL_ALPHA_TEST);
        }

        this.renderHand(a);

        // HUD 2D / 3D
        this.gui.render(this, this.width, this.height);

        glfwSwapBuffers(this.window);
    }

    /** Bloque seleccionado en la mano derecha, con animación al golpear o colocar. */
    private void renderHand(float a) {
        Tile tile = Tile.tiles[this.hotbar[this.selectedSlot]];
        if (tile == null) {
            return;
        }
        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        this.setupPerspective(70.0f, (float) this.width / (float) this.height, 0.05f, 10.0f);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        this.applyBob(a);

        float swing = 0.0f;
        if (this.swingTick >= 0) {
            float prev = this.swingTickO < 0 ? 0 : this.swingTickO;
            swing = (prev + (this.swingTick - prev) * a) / SWING_TICKS;
        }
        float sq = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        float s1 = (float) Math.sin(swing * Math.PI);

        glTranslatef(-sq * 0.4f, (float) Math.sin(Math.sqrt(swing) * Math.PI * 2.0) * 0.2f, -s1 * 0.2f);
        glTranslatef(0.56f, -0.52f, -0.72f);
        glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
        glRotatef(-s1 * 20.0f, 0.0f, 1.0f, 0.0f);
        glRotatef(-sq * 80.0f, 1.0f, 0.0f, 0.0f);
        glScalef(0.4f, 0.4f, 0.4f);
        glTranslatef(-0.5f, -0.5f, -0.5f);

        int bx = (int) Math.floor(this.player.x);
        int by = (int) Math.floor(this.player.y);
        int bz = (int) Math.floor(this.player.z);
        float skyL = this.level.getSkyLight(bx, by, bz) / 15.0f;
        float blkL = tile.getLightEmission() > 0 ? 1.0f : this.level.getBlockLight(bx, by, bz) / 15.0f;

        this.bindWorldShader(false);
        glNormal3f(skyL, blkL, 0.0f);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, this.textures.loadTexture("/terrain.png", GL_NEAREST));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        tile.renderGui(Tesselator.instance);
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        this.worldShader.unbind();
    }

    public void destroy() {
        if (this.level != null) {
            if (this.player != null) {
                this.storePlayerInLevel();
            }
            this.level.save();
        }
        if (this.window != NULL) {
            glfwFreeCallbacks(this.window);
            glfwDestroyWindow(this.window);
        }
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }

    public static void main(String[] args) {
        new Minecraft().run();
    }
}
