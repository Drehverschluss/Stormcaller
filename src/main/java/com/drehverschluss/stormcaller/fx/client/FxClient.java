package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.fx.ArcStyle;
import com.drehverschluss.stormcaller.fx.FxPayload;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client side of the lightning visuals: receives {@link FxPayload}s, keeps the short-lived effects, ages them every
 * tick and draws them (plus the lightning bodies of flying projectiles) after the translucent blocks.
 */
public final class FxClient {
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final long DROP_LOG_INTERVAL_MS = 5000L;
    /** Projectile bodies stay this far away from the camera (blocks). */
    private static final double MIN_CAMERA_DISTANCE = 1.8;

    private static long droppedTotal;
    private static long lastDropLog;
    private static int peakArcs;

    private FxClient() {}

    public static void init() {
        ProjectileTrails.registerDefaults();
        StormcallerFx.setClientHandler(FxClient::accept);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, FxClient::render);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> EFFECTS.clear());
    }

    // ------------------------------------------------------------------------------------------------ state for tests and debugging

    /** Arcs (and rings) alive right now. */
    public static int activeArcs() {
        int count = 0;
        for (Effect effect : EFFECTS) {
            if (effect.countsAsArc()) {
                count++;
            }
        }
        return count;
    }

    /** Arcs removed because of the limit since the game started. */
    public static long droppedArcs() {
        return droppedTotal;
    }

    /** Most arcs alive at the same time since the last call. */
    public static int takePeakArcs() {
        int peak = peakArcs;
        peakArcs = 0;
        return peak;
    }

    // ------------------------------------------------------------------------------------------------ receive

    private static void accept(FxPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        boolean arcs = StormcallerClientConfig.arcsEnabled();
        RandomSource random = RandomSource.create(payload.seed());
        switch (payload.kind()) {
            case ARC -> {
                if (arcs) {
                    addArc(new ArcFx(payload.a(), payload.b(), ArcStyle.byId(payload.style()), payload.size(), payload.seed()));
                }
            }
            case FLASH -> addFlash(level, payload.a(), payload.size(), random);
            case IMPACT -> {
                addFlash(level, payload.a(), payload.size(), random);
                if (arcs) {
                    for (int i = 0; i < payload.count(); i++) {
                        Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.7, random.nextGaussian()).normalize();
                        double length = payload.size() * (0.7 + random.nextDouble() * 0.8);
                        addArc(new ArcFx(payload.a(), payload.a().add(dir.scale(length)), ArcStyle.CHAIN, 0.8F, random.nextInt()));
                    }
                }
            }
            case STRIKE -> {
                Vec3 ground = payload.a();
                if (arcs) {
                    Vec3 top = ground.add(random.nextGaussian() * 1.2, payload.size(), random.nextGaussian() * 1.2);
                    addArc(new ArcFx(top, ground, ArcStyle.STRIKE, 1.0F, payload.seed()));
                }
                addFlash(level, ground.add(0.0, 0.3, 0.0), 2.0F, random);
                if (!Minecraft.getInstance().options.hideLightningFlash().get() && !StormcallerClientConfig.reduced()) {
                    level.setSkyFlashTime(2);
                }
            }
            case RING -> {
                if (arcs) {
                    addArc(new RingFx(payload.a(), payload.size(), payload.seed()));
                }
            }
        }
    }

    /**
     * Short arc that crawls over the surface of an entity's hitbox and moves with the entity (Static Shield).
     *
     * @param minHeight lowest point as a fraction of the hitbox height
     * @param maxHeight highest point as a fraction of the hitbox height
     * @param alpha     strength (1 = normal)
     */
    public static void addBodyArc(Entity entity, float minHeight, float maxHeight, float alpha, RandomSource random) {
        if (!StormcallerClientConfig.arcsEnabled()) {
            return;
        }
        addArc(new BodyArcFx(entity.getId(), entity.getBbWidth(), entity.getBbHeight(), minHeight, maxHeight, alpha, random.nextInt()));
    }

    private static void addArc(Effect arc) {
        int max = StormcallerClientConfig.maxArcs();
        if (max <= 0) {
            countDrop(1);
            return;
        }
        int alive = activeArcs();
        if (alive >= max) {
            int toRemove = alive - max + 1;
            Iterator<Effect> it = EFFECTS.iterator();
            while (toRemove > 0 && it.hasNext()) {
                if (it.next().countsAsArc()) {
                    it.remove();
                    toRemove--;
                    countDrop(1);
                }
            }
        }
        EFFECTS.add(arc);
        peakArcs = Math.max(peakArcs, Math.min(alive + 1, max));
    }

    private static void countDrop(int n) {
        droppedTotal += n;
        long now = System.currentTimeMillis();
        if (now - lastDropLog >= DROP_LOG_INTERVAL_MS) {
            lastDropLog = now;
            Stormcaller.LOGGER.debug("Stormcaller FX: arc limit ({}) reached, {} arcs removed so far",
                    StormcallerClientConfig.maxArcs(), droppedTotal);
        }
    }

    private static void addFlash(ClientLevel level, Vec3 pos, float size, RandomSource random) {
        boolean hide = Minecraft.getInstance().options.hideLightningFlash().get();
        boolean reduced = StormcallerClientConfig.reduced();
        float effective = size * (hide ? 0.6F : 1.0F) * (reduced ? 0.8F : 1.0F);
        EFFECTS.add(new FlashFx(pos, effective, hide ? 0.3F : 1.0F));
        int sparks = Math.round((3.0F + 4.0F * size) * (reduced ? 0.5F : 1.0F) * (hide ? 0.5F : 1.0F));
        for (int i = 0; i < sparks; i++) {
            // the electric spark particle scales its speed by 0.25
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z,
                    random.nextGaussian() * 0.9, random.nextGaussian() * 0.9 + 0.3, random.nextGaussian() * 0.9);
        }
    }

    // ------------------------------------------------------------------------------------------------ tick

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            EFFECTS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        EFFECTS.removeIf(effect -> {
            effect.age++;
            if (effect.age >= effect.life) {
                return true;
            }
            effect.tick();
            return false;
        });
    }

    // ------------------------------------------------------------------------------------------------ render

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;

        if (!EFFECTS.isEmpty()) {
            vc = buffers.getBuffer(FxRenderTypes.ARC);
            RenderContext ctx = new RenderContext(vc, matrix, cam, camera.getLeftVector(), camera.getUpVector(), partialTick,
                    StormcallerClientConfig.reduced());
            for (Effect effect : EFFECTS) {
                effect.render(ctx);
            }
        }
        if (!ProjectileTrails.isEmpty()) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof SpellProjectile projectile) {
                    ProjectileTrails.Trail trail = trailOf(projectile);
                    if (trail != null) {
                        if (vc == null) {
                            vc = buffers.getBuffer(FxRenderTypes.ARC);
                        }
                        renderTrail(vc, matrix, cam, camera, projectile, trail, partialTick);
                    }
                }
            }
        }
        if (vc != null) {
            buffers.endBatch(FxRenderTypes.ARC);
        }
    }

    private static ProjectileTrails.Trail trailOf(SpellProjectile projectile) {
        Holder<Spell> spell = projectile.getSpellEntry();
        if (spell == null) {
            return null;
        }
        return spell.unwrapKey().map(ResourceKey::location).map(ProjectileTrails::get).orElse(null);
    }

    private static void renderTrail(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Camera camera, SpellProjectile projectile,
                                    ProjectileTrails.Trail trail, float partialTick) {
        double half = projectile.getBbHeight() * 0.5;
        Vec3 previous = new Vec3(projectile.xo, projectile.yo + half, projectile.zo);
        Vec3 current = new Vec3(projectile.getX(), projectile.getY() + half, projectile.getZ());
        Vec3 head = previous.lerp(current, partialTick);
        Vec3 motion = current.subtract(previous);
        double speed = motion.length();
        Vec3 dir;
        if (speed > 1.0E-3) {
            dir = motion.scale(1.0 / speed);
        } else {
            // Spell Engine projectiles: yRot = atan2(x, z), xRot = atan2(y, horizontal)
            float yaw = projectile.getYRot() * Mth.DEG_TO_RAD;
            float pitch = projectile.getXRot() * Mth.DEG_TO_RAD;
            dir = new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        }
        // grows out of the hand instead of reaching behind the caster on the first ticks
        double travelled = Math.max(0.15, Math.max(speed, 0.5) * (projectile.tickCount + partialTick));
        double length = Math.min(trail.length(), travelled);
        // never reach closer than MIN_CAMERA_DISTANCE to the camera: in first person the bolt starts in front of the
        // eyes and would fill the screen
        while (length > 0.1 && head.subtract(dir.scale(length)).distanceTo(cam) < MIN_CAMERA_DISTANCE) {
            length *= 0.7;
        }
        // and fade in close to the camera
        float alpha = (float) Mth.clamp((head.distanceTo(cam) - MIN_CAMERA_DISTANCE) / 2.5, 0.0, 1.0);
        if (alpha <= 0.01F || length <= 0.1) {
            return;
        }
        boolean reduced = StormcallerClientConfig.reduced();
        float widthScale = reduced ? 0.7F : 1.0F;
        RandomSource random = RandomSource.create(projectile.getId() * 7919L + projectile.tickCount);
        Vec3 tail = head.subtract(dir.scale(length));
        List<Vec3> points = ArcGeometry.jagged(tail, head, trail.jag(), 0.25F, 0.3F, 10, random);
        ArcGeometry.strip(vc, matrix, cam, points, trail.coreWidth() * widthScale, trail.glowWidth() * widthScale, alpha, false);
        ArcGeometry.flash(vc, matrix, cam, head, camera.getLeftVector(), camera.getUpVector(),
                trail.headGlow() * widthScale * (0.9F + random.nextFloat() * 0.2F), alpha * 0.8F);
    }

    // ------------------------------------------------------------------------------------------------ effects

    private record RenderContext(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vector3f left, Vector3f up,
                                 float partialTick, boolean reduced) {}

    private abstract static class Effect {
        int age;
        final int life;

        Effect(int life) {
            this.life = life;
        }

        boolean countsAsArc() {
            return false;
        }

        void tick() {}

        abstract void render(RenderContext ctx);

        /** 1 on the first tick, then fading linearly to 0 at the end of the life. */
        float fade(float partialTick) {
            float t = (age + partialTick - 1.0F) / Math.max(1.0F, life - 1.0F);
            return Mth.clamp(1.0F - t, 0.0F, 1.0F);
        }
    }

    private static final class ArcFx extends Effect {
        private final Vec3 from;
        private final Vec3 to;
        private final ArcStyle style;
        private final float scale;
        private final int seed;
        private List<Vec3> main;
        private List<List<Vec3>> branches = List.of();

        ArcFx(Vec3 from, Vec3 to, ArcStyle style, float scale, int seed) {
            super(style.lifeTicks);
            this.from = from;
            this.to = to;
            this.style = style;
            this.scale = scale;
            this.seed = seed;
            rebuild();
        }

        @Override
        boolean countsAsArc() {
            return true;
        }

        @Override
        void tick() {
            if (age % style.flickerTicks == 0) {
                rebuild();
            }
        }

        private void rebuild() {
            RandomSource random = RandomSource.create(seed * 31L + age / style.flickerTicks);
            float segment = style == ArcStyle.STRIKE ? 0.6F : 0.4F;
            main = ArcGeometry.jagged(from, to, style.jag, style.maxJag, segment, 40, random);
            int count = style.minBranches + (style.maxBranches > style.minBranches
                    ? random.nextInt(style.maxBranches - style.minBranches + 1) : 0);
            branches = StormcallerClientConfig.reduced() ? List.of()
                    : ArcGeometry.branches(main, count, style.jag, style.maxJag, style == ArcStyle.STRIKE, random);
        }

        @Override
        void render(RenderContext ctx) {
            float alpha = fade(ctx.partialTick()) * (ctx.reduced() ? 0.75F : 1.0F);
            float glow = style.glowWidth * scale * (ctx.reduced() ? 0.6F : 1.0F);
            float core = style.coreWidth * scale;
            ArcGeometry.strip(ctx.vc(), ctx.matrix(), ctx.cam(), main, core, glow, alpha, false);
            for (List<Vec3> branch : branches) {
                ArcGeometry.strip(ctx.vc(), ctx.matrix(), ctx.cam(), branch, core * 0.6F, glow * 0.6F, alpha * 0.85F, true);
            }
        }
    }

    /** Arc on the surface of a (moving) entity, see {@link #addBodyArc}. Offsets are relative to the entity position. */
    private static final class BodyArcFx extends Effect {
        private final int entityId;
        private final float alpha;
        private final int seed;
        private final double radius;
        private final double startAngle;
        private final double span;
        private final double y0;
        private final double y1;
        private List<Vec3> offsets = List.of();

        BodyArcFx(int entityId, float width, float height, float minHeight, float maxHeight, float alpha, int seed) {
            super(5);
            this.entityId = entityId;
            this.alpha = alpha;
            this.seed = seed;
            RandomSource random = RandomSource.create(seed);
            this.radius = width * 0.5 + 0.12 + random.nextDouble() * 0.12;
            this.startAngle = random.nextDouble() * Math.PI * 2.0;
            this.span = Math.toRadians(50.0 + random.nextDouble() * 60.0) * (random.nextBoolean() ? 1.0 : -1.0);
            double low = height * minHeight;
            double high = height * maxHeight;
            this.y0 = low + random.nextDouble() * (high - low);
            this.y1 = Mth.clamp(y0 + (random.nextDouble() * 2.0 - 1.0) * height * 0.25, low, high);
            rebuild();
        }

        @Override
        boolean countsAsArc() {
            return true;
        }

        @Override
        void tick() {
            rebuild();
        }

        private void rebuild() {
            RandomSource random = RandomSource.create(seed * 31L + age);
            int n = 7;
            List<Vec3> points = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                double angle = startAngle + span * t;
                double jitter = (i == 0 || i == n - 1) ? 0.0 : 1.0;
                double r = radius + (random.nextDouble() * 2.0 - 1.0) * 0.07 * jitter;
                double y = Mth.lerp(t, y0, y1) + (random.nextDouble() * 2.0 - 1.0) * 0.09 * jitter;
                points.add(new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r));
            }
            offsets = points;
        }

        @Override
        void render(RenderContext ctx) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : level.getEntity(entityId);
            if (entity == null || entity.isRemoved()) {
                return;
            }
            float pt = ctx.partialTick();
            Vec3 base = new Vec3(Mth.lerp(pt, entity.xo, entity.getX()), Mth.lerp(pt, entity.yo, entity.getY()),
                    Mth.lerp(pt, entity.zo, entity.getZ()));
            List<Vec3> points = new ArrayList<>(offsets.size());
            for (Vec3 offset : offsets) {
                points.add(base.add(offset));
            }
            float a = fade(pt) * alpha * (ctx.reduced() ? 0.75F : 1.0F);
            float widthScale = ctx.reduced() ? 0.7F : 1.0F;
            ArcGeometry.strip(ctx.vc(), ctx.matrix(), ctx.cam(), points, 0.03F * widthScale, 0.15F * widthScale, a, false);
        }
    }

    private static final class FlashFx extends Effect {
        private final Vec3 pos;
        private final float size;
        private final float strength;

        FlashFx(Vec3 pos, float size, float strength) {
            super(4);
            this.pos = pos;
            this.size = size;
            this.strength = strength;
        }

        @Override
        void render(RenderContext ctx) {
            float t = age + ctx.partialTick();
            float radius = size * (0.45F + 0.55F * Math.min(1.0F, t / 1.5F));
            float alpha = Mth.clamp(1.0F - t / life, 0.0F, 1.0F) * strength;
            ArcGeometry.flash(ctx.vc(), ctx.matrix(), ctx.cam(), pos, ctx.left(), ctx.up(), radius, alpha);
        }
    }

    private static final class RingFx extends Effect {
        private static final int EXPAND_TICKS = 5;
        private final Vec3 center;
        private final float radius;
        private final int seed;

        RingFx(Vec3 center, float radius, int seed) {
            super(9);
            this.center = center;
            this.radius = radius;
            this.seed = seed;
        }

        @Override
        boolean countsAsArc() {
            return true;
        }

        @Override
        void render(RenderContext ctx) {
            float t = age + ctx.partialTick();
            float x = Math.min(1.0F, t / EXPAND_TICKS);
            float r = Math.max(0.3F, radius * (1.0F - (1.0F - x) * (1.0F - x)));
            float alpha = t < EXPAND_TICKS ? 1.0F : Mth.clamp(1.0F - (t - EXPAND_TICKS) / (life - EXPAND_TICKS), 0.0F, 1.0F);
            RandomSource random = RandomSource.create(seed * 31L + age);
            int n = Mth.clamp((int) (r * 9.0F), 12, 96);
            double amp = 0.12 + r * 0.03;
            List<Vec3> points = new ArrayList<>(n + 1);
            for (int i = 0; i < n; i++) {
                double angle = Math.PI * 2.0 * i / n;
                double rr = r + (random.nextDouble() * 2.0 - 1.0) * amp;
                double dy = (random.nextDouble() * 2.0 - 1.0) * amp * 0.6;
                points.add(center.add(Math.cos(angle) * rr, dy, Math.sin(angle) * rr));
            }
            points.add(points.get(0));
            float widthScale = ctx.reduced() ? 0.7F : 1.0F;
            ArcGeometry.strip(ctx.vc(), ctx.matrix(), ctx.cam(), points, 0.06F * widthScale, 0.32F * widthScale,
                    alpha * (ctx.reduced() ? 0.75F : 1.0F), false);
        }
    }
}
