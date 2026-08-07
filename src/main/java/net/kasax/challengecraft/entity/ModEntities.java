package net.kasax.challengecraft.entity;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * The mod's entity registry — currently just the Würfel of challenge 46, and the mod's first
 * custom entity of any kind. Follows {@code block/InfiniteChestRegistry}: declare the type as a
 * static final and do the {@code Registry.register} inside {@link #initialize()}, so registration
 * isn't a class-initialisation side effect.
 *
 * <p><b>Registering an entity type obliges you to register a renderer for it.</b> In a dev client
 * {@code MinecraftClient.checkGameData()} calls {@code EntityRenderers.isMissingRendererFactories()}
 * and throws {@code IllegalStateException("Your game data is foobar")} on resource reload if any
 * type lacks one — see {@code ChallengeCraftClient} for the matching
 * {@code EntityRendererRegistry.register} call.
 */
public final class ModEntities {
    public static final Identifier DICE_ID = Identifier.of(ChallengeCraft.MOD_ID, "dice");

    public static final RegistryKey<EntityType<?>> DICE_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, DICE_ID);

    public static final EntityType<DiceEntity> DICE =
            EntityType.Builder.<DiceEntity>create(DiceEntity::new, SpawnGroup.MISC)
                    .dimensions(0.35f, 0.35f)
                    .eyeHeight(0.175f)
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)   // the tumble needs a per-tick orientation feed
                    .disableSummon()
                    .disableSaving()           // transient; a die never survives a reload
                    .dropsNothing()
                    .build(DICE_KEY);

    private ModEntities() {
    }

    public static void initialize() {
        Registry.register(Registries.ENTITY_TYPE, DICE_KEY, DICE);
        ChallengeCraft.LOGGER.info("Registered entities for {}", ChallengeCraft.MOD_ID);
    }
}
