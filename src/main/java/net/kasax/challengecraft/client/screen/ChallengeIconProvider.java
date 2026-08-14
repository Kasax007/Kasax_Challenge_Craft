package net.kasax.challengecraft.client.screen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Central icon lookup for challenge selection UI.
 *
 * <p>The table maps ids to {@link Item}, NOT to {@code ItemStack}, and that is load-bearing. In
 * 26.2 an item's data components live on its registry {@code Holder} and are bound during the
 * datapack load; {@code new ItemStack(item)} reads them in its constructor and throws
 * {@code NullPointerException: Components not bound yet} before that happens. Building the stacks
 * in a static initialiser therefore blew up the entire class the first time any screen touched it —
 * and because a failed {@code <clinit>} poisons the class forever, every later use died with
 * {@code NoClassDefFoundError}, taking the world-creation challenge tab down with it.
 *
 * <p>Item constants themselves are inert, so the table is safe to build eagerly. Stacks are created
 * on demand and cached, and {@link #getIcon(int)} refuses to build one while components are still
 * unbound — a window that really is reachable, since the title screen's Progress button can be
 * clicked mid-reload.
 */
public class ChallengeIconProvider {
    private static final Map<Integer, Item> ICONS = new HashMap<>();
    /** Lazily built stacks. Only populated once the item's components are bound. */
    private static final Map<Integer, ItemStack> STACK_CACHE = new HashMap<>();
    /** One-shot so a blank icon reports its cause once instead of every frame. */
    private static boolean warnedUnbound = false;
    /** One-shot: the component bind below is tried at most once per session. */
    private static boolean bindAttempted = false;

    static {
        ICONS.put(1, Items.EXPERIENCE_BOTTLE);
        ICONS.put(2, Items.COBWEB);
        ICONS.put(3, Items.ZOMBIE_SPAWN_EGG);
        ICONS.put(4, Items.CHEST);
        ICONS.put(5, Items.GOLDEN_APPLE);
        ICONS.put(6, Items.EMERALD);
        ICONS.put(7, Items.APPLE);
        ICONS.put(8, Items.CRAFTING_TABLE);
        ICONS.put(9, Items.MAP);
        ICONS.put(10, Items.CLOCK);
        ICONS.put(11, Items.GRASS_BLOCK);
        ICONS.put(12, Items.BUNDLE);
        ICONS.put(13, Items.ENCHANTED_BOOK);
        ICONS.put(14, Items.COBBLESTONE);
        ICONS.put(15, Items.BONE);
        ICONS.put(16, Items.BEDROCK);
        ICONS.put(17, Items.DIAMOND_BOOTS);
        ICONS.put(18, Items.IRON_SWORD);
        ICONS.put(19, Items.IRON_PICKAXE);
        ICONS.put(20, Items.KNOWLEDGE_BOOK);
        ICONS.put(21, Items.TOTEM_OF_UNDYING);
        ICONS.put(22, Items.NETHER_STAR);
        ICONS.put(23, Items.CREEPER_SPAWN_EGG);
        ICONS.put(24, Items.GLISTERING_MELON_SLICE);
        ICONS.put(25, Items.HEART_OF_THE_SEA);
        ICONS.put(26, Items.WRITABLE_BOOK);
        ICONS.put(27, Items.CHAINMAIL_CHESTPLATE);
        ICONS.put(28, Items.IRON_BOOTS);
        ICONS.put(29, Items.LAVA_BUCKET);
        ICONS.put(30, Items.ANVIL);
        ICONS.put(31, Items.STONE_PICKAXE);
        ICONS.put(32, Items.END_CRYSTAL);
        ICONS.put(33, Items.SLIME_BALL);
        ICONS.put(34, Items.PHANTOM_MEMBRANE);
        ICONS.put(35, Items.SPAWNER);
        ICONS.put(36, Items.WRITTEN_BOOK);
        ICONS.put(37, Items.REPEATER);
        ICONS.put(38, Items.GLOW_INK_SAC);
        ICONS.put(39, Items.COOKED_BEEF);
        ICONS.put(40, Items.FILLED_MAP);
        ICONS.put(41, Items.HONEY_BLOCK);
        ICONS.put(42, Items.PIG_SPAWN_EGG);
        ICONS.put(43, Items.LADDER);
        ICONS.put(44, Items.STONE);
        ICONS.put(45, Items.ITEM_FRAME);
        ICONS.put(46, net.kasax.challengecraft.item.ModItems.DICE);

        // Perks
        ICONS.put(101, Items.GOLDEN_CARROT);
        ICONS.put(102, Items.FEATHER);
        ICONS.put(103, Items.NETHERITE_CHESTPLATE);
        ICONS.put(104, Items.MAGMA_CREAM);
        ICONS.put(105, Items.DIAMOND_SWORD);
        ICONS.put(106, Items.BOOK);
        ICONS.put(107, Items.SHIELD);
        ICONS.put(108, Items.GOLDEN_SWORD);
        ICONS.put(109, net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_ITEM);
    }

    /**
     * The icon for an id, or an empty stack if item components are not bound yet. Callers draw
     * every frame, so a momentarily empty icon fills itself in a tick later rather than throwing.
     */
    public static ItemStack getIcon(int id) {
        ItemStack cached = STACK_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Item item = ICONS.getOrDefault(id, Items.BARRIER);

        // Attempt the construction and catch the failure, rather than asking
        // item.builtInRegistryHolder().areComponentsBound() first. That predicate was tried and it
        // stayed false forever in the main-menu client — the holder it answers for is not the one
        // ItemStack reads — so every icon rendered as an empty stack: the coloured tile drew, the
        // item never did. Testing the actual operation cannot be wrong about itself.
        try {
            ItemStack stack = new ItemStack(item);
            STACK_CACHE.put(id, stack);
            if (warnedUnbound) {
                warnedUnbound = false;
                net.kasax.challengecraft.ChallengeCraft.LOGGER.info("[Icons] Item components ready — icons rendering normally");
            }
            return stack;
        } catch (RuntimeException e) {
            if (!warnedUnbound) {
                warnedUnbound = true;
                net.kasax.challengecraft.ChallengeCraft.LOGGER.warn(
                        "[Icons] Item components are not bound yet ({}) — attempting a one-off bind.",
                        e.toString());
            }
            if (bindComponents()) {
                return getIcon(id);   // bound now; build the stack for real
            }
            return ItemStack.EMPTY;
        }
    }

    /**
     * Binds vanilla item data components so icons can be drawn before any world exists.
     *
     * <p>This is not a workaround for a race — the components genuinely are not bound on a freshly
     * launched client sitting at the title screen. Tracing 26.2 shows only two things ever call
     * {@code Holder.Reference.bindComponents}: {@code ReloadableServerResources} (a datapack load)
     * and {@code RegistryDataCollector} (joining a server). Neither has happened yet, so
     * {@code new ItemStack(item)} throws {@code Components not bound yet} and the Progress screen
     * had no icons until the player had opened world creation once — that flow performs a datapack
     * load — and come back.
     *
     * <p>{@code VanillaRegistries.createLookup()} builds the same provider from built-in data with
     * no world and no datapack, which is all {@code build(...)} needs. Re-binding later is harmless:
     * {@code bindComponents} is a plain field assignment, so the real load simply overwrites this
     * with the authoritative map.
     *
     * <p>Runs at most once per session, and only if an icon was actually wanted, so a player who
     * never opens the screen never pays for it.
     *
     * @return true if the bind ran without error
     */
    private static boolean bindComponents() {
        if (bindAttempted) return false;
        bindAttempted = true;
        try {
            long startedAt = System.currentTimeMillis();
            var provider = net.minecraft.data.registries.VanillaRegistries.createLookup();
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                    .build(provider)
                    .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
            net.kasax.challengecraft.ChallengeCraft.LOGGER.info(
                    "[Icons] Bound item data components in {} ms — icons available without a world",
                    System.currentTimeMillis() - startedAt);
            return true;
        } catch (Throwable t) {
            // Never fatal: without this the screen simply falls back to its coloured placeholders.
            net.kasax.challengecraft.ChallengeCraft.LOGGER.warn(
                    "[Icons] Could not bind item components; icons stay as placeholders until a world loads", t);
            return false;
        }
    }

    public static void drawIcon(GuiGraphicsExtractor context, int x, int y, int id) {
        ItemStack stack = getIcon(id);
        context.item(stack, x, y);
        if (id == 28 || id == 39) {
            context.item(getIcon(-1), x, y); // -1 is unmapped, so this resolves to the barrier
        }
    }
}
