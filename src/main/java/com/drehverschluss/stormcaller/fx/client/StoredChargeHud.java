package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.shock.StormcallerEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Stored Charge (Amber Lightning Staff): the HUD icon shows the number of charges in its corner, like a stack count.
 * Minecraft draws effect stages only in the inventory ("Stored Charge V"); the charges matter while fighting, so the HUD
 * gets them too. Icon and blinking at the end are drawn as Minecraft does.
 */
public final class StoredChargeHud {
    private static final int COLOR = 0xFFFFD27A;

    private StoredChargeHud() {}

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z, float alpha) {
                Minecraft mc = Minecraft.getInstance();
                TextureAtlasSprite sprite = mc.getMobEffectTextures().get(instance.getEffect());
                graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
                graphics.blit(x + 3, y + 3, 0, 18, 18, sprite);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                String stage = Integer.toString(instance.getAmplifier() + 1);
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.drawString(mc.font, stage, x + 22 - mc.font.width(stage), y + 14, COLOR, true);
                graphics.pose().popPose();
                return true;
            }
        }, StormcallerEffects.STORED_CHARGE.get());
    }
}
