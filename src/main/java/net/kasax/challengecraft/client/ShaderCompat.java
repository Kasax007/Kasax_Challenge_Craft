package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.ChallengeCraft;

import java.lang.reflect.Method;

/**
 * Everything this mod needs from Iris, reached by reflection so Iris stays optional.
 *
 * <p>Two separate jobs live here, and they are unrelated except for both talking to Iris:
 *
 * <ol>
 *   <li><b>Registering our render pipeline.</b> Iris swaps vanilla's render programs for the shader
 *       pack's and looks each one up by pipeline. Debug pipelines have no counterpart, so drawing
 *       the dice reach ring on {@code minecraft:pipeline/debug_quads} made Iris log
 *       {@code Missing program ... in override list. This is likely an Iris bug!!!}. Iris has an
 *       official answer for exactly this — {@code IrisApi#assignPipeline} — so the ring is
 *       registered as {@code IrisProgram.BASIC} (untextured coloured geometry, which is what it is)
 *       and renders normally with shaders on.</li>
 *   <li><b>Rebuilding the shader pipeline on demand.</b> A workaround for a modpack-side bug where
 *       item textures do not appear while shaders are on. See {@link #rebuildShaderPipeline()}.</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class ShaderCompat {
    private static boolean resolved = false;
    private static Object irisApi;
    private static Method isShaderPackInUse;
    private static Method getConfig;
    private static Method areShadersEnabled;
    private static Method setShadersEnabledAndApply;
    private static boolean pipelineRegistered = false;

    private ShaderCompat() {
    }

    public static boolean irisPresent() {
        resolve();
        return irisApi != null;
    }

    /**
     * Whether our debug pipeline is safe to draw on. True when no shader pack is running, or when
     * Iris accepted the pipeline registration — only the combination of "shaders on" and "Iris did
     * not take the registration" has to skip.
     */
    public static boolean canDrawDebugPipeline() {
        return !shadersActive() || pipelineRegistered;
    }

    public static boolean shadersActive() {
        resolve();
        if (isShaderPackInUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isShaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException e) {
            isShaderPackInUse = null;   // one failure is enough; do not ask again every frame
            return false;
        }
    }

    /**
     * Tells Iris which of its programs our custom pipelines belong to.
     *
     * <p>Called once at client start. Without it the dice reach ring draws on a pipeline Iris cannot
     * map, which it reports as its own bug.
     */
    public static void registerPipelines() {
        resolve();
        if (irisApi == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            // Taken from the constant rather than spelled as a string: 26.3 moved the class from
            // com.mojang.blaze3d.pipeline to com.mojang.renderpearl.api.pipeline, and the old
            // literal failed Class.forName silently - the ring simply stopped drawing with shaders.
            Class<?> pipelineClass = net.minecraft.client.renderer.RenderPipelines.DEBUG_QUADS.getClass();
            Object basic = Enum.valueOf(programClass.asSubclass(Enum.class), "BASIC");
            Method assign = apiClass.getMethod("assignPipeline", pipelineClass, programClass);
            assign.invoke(irisApi, net.minecraft.client.renderer.RenderPipelines.DEBUG_QUADS, basic);
            pipelineRegistered = true;
            ChallengeCraft.LOGGER.info("[ShaderCompat] DEBUG_QUADS bei Iris als BASIC angemeldet");
        } catch (ReflectiveOperationException | RuntimeException e) {
            ChallengeCraft.LOGGER.info("[ShaderCompat] Pipeline konnte nicht angemeldet werden ({}) — "
                    + "der Reichweitenring wird bei aktivem Shader übersprungen", e.toString());
        }
    }

    /**
     * Turns the shader pack off and straight back on, forcing Iris to rebuild its pipeline.
     *
     * <p>This is a workaround, not a fix, and it is not for a bug in this mod: on this modpack item
     * textures can fail to appear while shaders are on, and it happens with Challenge Craft removed
     * as well. A plain resource reload (F3+T) does not clear it; taking the shader pack down and
     * putting it back up does, because Iris rebuilds every program and re-binds its textures.
     *
     * <p>Deliberately kept in one place and easy to delete once the underlying mod is fixed.
     *
     * @return true if the toggle actually ran
     */
    public static boolean rebuildShaderPipeline() {
        resolve();
        if (setShadersEnabledAndApply == null || areShadersEnabled == null || getConfig == null) {
            return false;
        }
        try {
            Object config = getConfig.invoke(irisApi);
            if (!Boolean.TRUE.equals(areShadersEnabled.invoke(config))) {
                return false;               // nothing to rebuild
            }
            setShadersEnabledAndApply.invoke(config, false);
            setShadersEnabledAndApply.invoke(config, true);
            ChallengeCraft.LOGGER.info("[ShaderCompat] Shader-Pipeline neu aufgebaut");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ChallengeCraft.LOGGER.warn("[ShaderCompat] Neuaufbau fehlgeschlagen: {}", e.toString());
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = api.getMethod("getInstance").invoke(null);
            isShaderPackInUse = api.getMethod("isShaderPackInUse");
            getConfig = api.getMethod("getConfig");
            Class<?> cfg = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            areShadersEnabled = cfg.getMethod("areShadersEnabled");
            setShadersEnabledAndApply = cfg.getMethod("setShadersEnabledAndApply", boolean.class);
            ChallengeCraft.LOGGER.info("[ShaderCompat] Iris erkannt, API verfügbar");
        } catch (ReflectiveOperationException | RuntimeException e) {
            ChallengeCraft.LOGGER.info("[ShaderCompat] Iris vorhanden, API nicht erreichbar ({})",
                    e.toString());
        }
    }
}
