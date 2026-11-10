package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.neoforged.fml.ModList;

/**
 * Optional link to the Iris shader API ({@code net.irisshaders.iris.api.v0.IrisApi}, also offered by Oculus), only by
 * reflection: no compile or runtime dependency. Without Iris both checks are simply {@code false}.
 */
public final class ShaderCompat {
    private static boolean initDone;
    private static Object api;
    private static MethodHandle packInUse;
    private static MethodHandle shadowPass;

    private ShaderCompat() {}

    /** A shader pack is active right now (Iris/Oculus installed and shaders switched on). */
    public static boolean shaderPackInUse() {
        init();
        return call(packInUse);
    }

    /** Iris is drawing the shadow map right now (entities are rendered once more from the sun or moon). */
    public static boolean renderingShadowPass() {
        init();
        return call(shadowPass);
    }

    private static boolean call(MethodHandle handle) {
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void init() {
        if (initDone) {
            return;
        }
        initDone = true;
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) {
            return;
        }
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            api = lookup.findStatic(type, "getInstance", MethodType.methodType(type)).invoke();
            packInUse = lookup.findVirtual(type, "isShaderPackInUse", MethodType.methodType(boolean.class));
            shadowPass = lookup.findVirtual(type, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            Stormcaller.LOGGER.info("Stormcaller: Iris shader API found, cloud puffs adapt to shader packs");
        } catch (Throwable t) {
            api = null;
            packInUse = null;
            shadowPass = null;
            Stormcaller.LOGGER.warn("Stormcaller: Iris is installed, but its API could not be read ({})", t.toString());
        }
    }
}
