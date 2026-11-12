package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.client.ShaderCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Stormbringer Staff in the hand: the 3D model plus small arcs that jump between the crystal and the tines every 1 to 3
 * seconds for a few ticks.
 * <p>
 * Why an item renderer: the arcs are drawn in the item's own space, right after the hand transform of the item model,
 * so they sit exactly on the crystal in every hand view (first person, third person, other players, armor stands and
 * mobs holding it), follow every Better Combat or Spell Engine pose and need no entity, bone or network data. The item
 * model ({@code staff_stormbringer.json}) stays a normal model: only for the four hand contexts the baked model is
 * wrapped so that Minecraft calls this renderer; inventory, item frame and ground keep the plain model (no arcs there).
 * Client only, nothing is sent over the network. Switched off in {@code stormcaller-client.toml} ({@code crystal_arcs}),
 * the hand uses the plain model again.
 */
public final class StormbringerStaffRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation ITEM_ID = Stormcaller.id("staff_stormbringer");
    /** The 3D model, loaded on its own so this renderer can draw its quads. */
    public static final ModelResourceLocation IN_HAND = ModelResourceLocation.standalone(Stormcaller.id("item/staff_stormbringer_in_hand"));

    /** Crystal colour of the tier 2/3 robes (gem2 of robe_palette.json) for the glow, white core like all arcs. */
    private static final int GLOW_R = 0x6a, GLOW_G = 0xe6, GLOW_B = 0xff;
    /** Bursts: one per window of {@link #WINDOW} ticks, starting between tick 5 and 25 of it (so 1 to 3 s apart). */
    private static final int WINDOW = 40;
    private static final double MAX_DISTANCE = 48.0;
    private static StormbringerStaffRenderer instance;

    private StormbringerStaffRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    // ------------------------------------------------------------------ registration (client mod bus)

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(IN_HAND);
    }

    /** Hand contexts get a model that asks for this renderer; everything else stays as baked. */
    public static void wrapModel(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation key = ModelResourceLocation.inventory(ITEM_ID);
        BakedModel original = models.get(key);
        if (original == null) {
            return;
        }
        models.put(key, new BakedModelWrapper<>(original) {
            @Override
            public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
                BakedModel transformed = originalModel.applyTransform(context, poseStack, leftHand);
                if (isHand(context) && StormcallerClientConfig.crystalArcs()) {
                    return new BakedModelWrapper<>(transformed) {
                        @Override
                        public boolean isCustomRenderer() {
                            return true;
                        }
                    };
                }
                return transformed;
            }
        });
    }

    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        Item item = BuiltInRegistries.ITEM.get(ITEM_ID);
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) {
                    instance = new StormbringerStaffRenderer();
                }
                return instance;
            }
        }, item);
    }

    private static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(IN_HAND);
        ItemRenderer items = mc.getItemRenderer();
        // the same calls as ItemRenderer.render uses for a normal item model (the pose is already in item space)
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType type : pass.getRenderTypes(stack, true)) {
                VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil());
                items.renderModelLists(pass, stack, light, overlay, pose, vc);
            }
        }
        if (mc.level != null && !ShaderCompat.renderingShadowPass()
                && RenderSystem.getVertexSorting() != VertexSorting.ORTHOGRAPHIC_Z) {   // not in a GUI player preview
            arcs(stack, context, pose, buffers, mc.level.getGameTime());
        }
    }

    private static void arcs(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, long time) {
        // every holder has its own ItemStack object: its identity spreads the bursts of several holders apart
        long seed = System.identityHashCode(stack) * 0x9E3779B97F4A7C15L;
        long shifted = time + Math.floorMod(seed >>> 17, WINDOW);
        long window = Math.floorDiv(shifted, WINDOW);
        long h = mix(seed ^ window);
        int start = 5 + (int) Math.floorMod(h, 21);
        int duration = 3 + (int) Math.floorMod(h >>> 12, 3);
        int local = (int) (shifted - window * WINDOW) - start;
        if (local < 0 || local >= duration) {
            return;
        }
        Matrix4f m = pose.last().pose();
        Vector3f crystal = m.transformPosition(0.5F, 0.85F, 0.5F, new Vector3f());
        if (crystal.lengthSquared() > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        // camera position in item space (view space has the camera at its origin), so the strips face the camera
        Vector3f cam = new Matrix4f(m).invert().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        Vec3 camPos = new Vec3(cam.x, cam.y, cam.z);
        Matrix4f drawMatrix = new Matrix4f(m).translate(cam);
        boolean firstPerson = context.firstPerson();
        float fade = local == duration - 1 ? 0.55F : 1.0F;
        float alpha = (firstPerson ? 0.6F : 0.95F) * fade;
        float widthScale = firstPerson ? 0.75F : 1.0F;
        RandomSource burst = RandomSource.create(h);
        int count = burst.nextFloat() < 0.3F ? 2 : 1;
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.ARC);
        for (int i = 0; i < count; i++) {
            // the path of an arc stays for the whole burst, its zigzag changes every tick (flicker)
            RandomSource path = RandomSource.create(mix(h + 31L * i));
            RandomSource jag = RandomSource.create(mix(h + 31L * i + 7919L * local));
            Vec3[] ends = endpoints(path);
            List<Vec3> points = ArcGeometry.jagged(ends[0], ends[1], 0.35F, 0.3F / 16.0F, 0.22F / 16.0F, 6, jag);
            ArcGeometry.strip(vc, drawMatrix, camPos, points, 0.16F / 16.0F * widthScale, 0.62F / 16.0F * widthScale, alpha,
                    false, GLOW_R, GLOW_G, GLOW_B);
        }
    }

    /**
     * Start and end of one arc in item space (model units / 16), from the faces of staff_stormbringer_in_hand.json:
     * crystal side x 7.25 / 8.75, y 12.25-15, z 7.5-8.5; tines (front plates z 8.5-9, back plates z 7-7.5) with the hook
     * end at x 7 / 9, y 14.25-15 and the inner face of the outer part at x 6.5 / 9.5, y 12.75-14; crystal tip at y 16.
     */
    private static Vec3[] endpoints(RandomSource r) {
        boolean right = r.nextBoolean();
        double side = right ? 1.0 : -1.0;
        double plateZ = r.nextBoolean() ? 8.75 : 7.25;
        int kind = r.nextInt(5);
        Vec3 a;
        Vec3 b;
        if (kind == 4) {
            // from the tip of the crystal over to the top of a hook
            a = new Vec3(8.0 + side * 0.15, 16.0, 8.0);
            b = new Vec3(8.0 + side * 1.9, 15.0, plateZ);
        } else {
            double y = 12.7 + r.nextDouble() * 2.0;
            a = new Vec3(8.0 + side * 0.75, y, 7.6 + r.nextDouble() * 0.8);
            b = kind < 2
                    ? new Vec3(8.0 + side * 1.0, 14.35 + r.nextDouble() * 0.55, plateZ)      // hook end
                    : new Vec3(8.0 + side * 1.5, 12.85 + r.nextDouble() * 1.05, plateZ);     // inner face of the tine
        }
        return new Vec3[]{a.scale(1.0 / 16.0), b.scale(1.0 / 16.0)};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
