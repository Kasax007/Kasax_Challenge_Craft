package net.kasax.challengecraft.challenges.lockout;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Immutable board-goal definition shared by server validation and client rendering. */
public record LockoutBingoGoal(
        String id,
        LockoutBingoGoalCategory category,
        LockoutBingoGoalType type,
        LockoutBingoGoalDifficulty difficulty,
        List<String> targets,
        String contextTarget,
        int amount,
        String iconItemId,
        LockoutBingoGoalImplementationStatus implementationStatus,
        boolean selectableOnNormalBoard,
        String titleKey,
        String descriptionKey
) {
    public LockoutBingoGoal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(difficulty, "difficulty");
        targets = List.copyOf(targets == null ? List.of() : targets);
        if (targets.isEmpty() && type != LockoutBingoGoalType.HEALTH_CHECK) {
            throw new IllegalArgumentException("targets may not be empty for " + id);
        }
        contextTarget = contextTarget == null ? "" : contextTarget;
        amount = Math.max(1, amount);
        iconItemId = iconItemId == null ? "" : iconItemId;
        implementationStatus = implementationStatus == null ? LockoutBingoGoalImplementationStatus.TODO : implementationStatus;
        titleKey = titleKey == null ? "" : titleKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
    }

    public String primaryTarget() {
        return targets.isEmpty() ? "" : targets.getFirst();
    }

    public boolean isImplemented() {
        return implementationStatus.isImplemented();
    }

    public boolean isSelectableOnNormalBoard() {
        return selectableOnNormalBoard && isImplemented();
    }

    public Component title() {
        if (!titleKey.isBlank()) {
            return Component.translatable(titleKey);
        }

        return switch (type) {
            case ITEM, CRAFT, CONSUME, EQUIP -> itemName(primaryTarget());
            case ITEM_AMOUNT -> Component.translatable("challengecraft.lockout.goal.item_amount.title", amount, itemName(primaryTarget()));
            case ITEM_TAG -> Component.translatable("challengecraft.lockout.goal." + id + ".title");
            case KILL -> entityName(primaryTarget());
            case DIMENSION -> Component.translatable("challengecraft.lockout.dimension." + primaryTarget().replace(':', '.'));
            case BIOME -> Component.translatable("biome." + primaryTarget().replace(':', '.'));
            default -> Component.translatable("challengecraft.lockout.goal." + id + ".title");
        };
    }

    public Component description() {
        if (!descriptionKey.isBlank()) {
            return Component.translatable(descriptionKey);
        }

        return switch (type) {
            case ITEM -> Component.translatable("challengecraft.lockout.goal.obtain_item.desc", title());
            case ITEM_AMOUNT -> Component.translatable("challengecraft.lockout.goal.item_amount.desc", amount, itemName(primaryTarget()));
            case ITEM_TAG -> Component.translatable("challengecraft.lockout.goal.obtain_tag.desc", title());
            case CRAFT -> Component.translatable("challengecraft.lockout.goal.craft.desc", title());
            case KILL -> Component.translatable("challengecraft.lockout.goal.kill_entity.desc", title());
            case CONSUME -> Component.translatable("challengecraft.lockout.goal.consume.desc", title());
            case DIMENSION -> Component.translatable("challengecraft.lockout.goal.dimension.desc", title());
            case BIOME -> Component.translatable("challengecraft.lockout.goal.biome.desc", title());
            case EQUIP -> Component.translatable("challengecraft.lockout.goal.equip.desc", title());
            case INTERACT -> Component.translatable("challengecraft.lockout.goal.interact.desc", title());
            case TRADE -> Component.translatable("challengecraft.lockout.goal.trade.desc", title());
            case STRUCTURE -> Component.translatable("challengecraft.lockout.goal.structure.desc", title());
            case ADVANCEMENT -> Component.translatable("challengecraft.lockout.goal.advancement.desc", title());
            case INVENTORY_SET -> Component.translatable("challengecraft.lockout.goal.inventory_set.desc", amount, title());
            case FISHING -> Component.translatable("challengecraft.lockout.goal.fishing.desc", title());
            case ACTION -> Component.translatable("challengecraft.lockout.goal.action.desc", title());
            case BREW -> Component.translatable("challengecraft.lockout.goal.brew.desc", title());
            case ENCHANT -> Component.translatable("challengecraft.lockout.goal.enchant.desc", title());
            case PLACE_BLOCK -> Component.translatable("challengecraft.lockout.goal.place_block.desc", title());
            case DAMAGE_EVENT -> Component.translatable("challengecraft.lockout.goal.damage_event.desc", title());
            case STATUS_EFFECT -> Component.translatable("challengecraft.lockout.goal.status_effect.desc", title());
            case HEALTH_CHECK -> Component.translatable("challengecraft.lockout.goal.health_check.desc", title());
            case LOCATION -> Component.translatable("challengecraft.lockout.goal.location.desc", title());
            default -> Component.translatable("challengecraft.lockout.goal.todo.desc", title());
        };
    }

    public Component condition() {
        if (!isImplemented()) {
            return Component.translatable("challengecraft.lockout.goal.todo.condition");
        }

        return switch (type) {
            case ITEM -> Component.translatable("challengecraft.lockout.goal.obtain_item.condition");
            case ITEM_AMOUNT -> Component.translatable("challengecraft.lockout.goal.item_amount.condition");
            case ITEM_TAG -> Component.translatable("challengecraft.lockout.goal.obtain_tag.condition");
            case CRAFT -> Component.translatable("challengecraft.lockout.goal.craft.condition");
            case KILL -> Component.translatable("challengecraft.lockout.goal.kill_entity.condition");
            case CONSUME -> Component.translatable("challengecraft.lockout.goal.consume.condition");
            case DIMENSION -> Component.translatable("challengecraft.lockout.goal.dimension.condition");
            case BIOME -> Component.translatable("challengecraft.lockout.goal.biome.condition");
            case EQUIP -> Component.translatable("challengecraft.lockout.goal.equip.condition");
            case INTERACT -> Component.translatable("challengecraft.lockout.goal.interact.condition");
            case TRADE -> Component.translatable("challengecraft.lockout.goal.trade.condition");
            case STRUCTURE -> Component.translatable("challengecraft.lockout.goal.structure.condition");
            case ADVANCEMENT -> Component.translatable("challengecraft.lockout.goal.advancement.condition");
            case INVENTORY_SET -> Component.translatable("challengecraft.lockout.goal.inventory_set.condition");
            case FISHING -> Component.translatable("challengecraft.lockout.goal.fishing.condition");
            case ACTION -> Component.translatable("challengecraft.lockout.goal.action.condition");
            case BREW -> Component.translatable("challengecraft.lockout.goal.brew.condition");
            case ENCHANT -> Component.translatable("challengecraft.lockout.goal.enchant.condition");
            case PLACE_BLOCK -> Component.translatable("challengecraft.lockout.goal.place_block.condition");
            case DAMAGE_EVENT -> Component.translatable("challengecraft.lockout.goal.damage_event.condition");
            case STATUS_EFFECT -> Component.translatable("challengecraft.lockout.goal.status_effect.condition");
            case HEALTH_CHECK -> Component.translatable("challengecraft.lockout.goal.health_check.condition");
            case LOCATION -> Component.translatable("challengecraft.lockout.goal.location.condition");
            default -> Component.translatable("challengecraft.lockout.goal.todo.condition");
        };
    }

    public ItemStack createIconStack() {
        if (!iconItemId.isBlank()) {
            Item iconItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(iconItemId));
            if (iconItem != Items.AIR) {
                return new ItemStack(iconItem);
            }
        }

        return switch (type) {
            case ITEM, ITEM_AMOUNT, ITEM_TAG, CRAFT, CONSUME, EQUIP -> stackFromItemId(primaryTarget(), Items.MAP);
            case KILL -> {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(primaryTarget()));
                var egg = net.minecraft.world.item.SpawnEggItem.byId(entityType);
                yield egg.map(ItemStack::new).orElseGet(() -> new ItemStack(Items.ZOMBIE_SPAWN_EGG));
            }
            default -> new ItemStack(Items.MAP);
        };
    }

    public boolean matchesItem(ItemStack stack) {
        return switch (type) {
            case ITEM, ITEM_AMOUNT, CRAFT, CONSUME, EQUIP -> targets.stream().anyMatch(target -> stack.is(BuiltInRegistries.ITEM.getValue(Identifier.parse(target))));
            case ITEM_TAG -> matchesTagLikeGoal(stack);
            default -> false;
        };
    }

    private boolean matchesTagLikeGoal(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if ("minecraft:music_discs".equals(primaryTarget())) {
            return "minecraft".equals(itemId.getNamespace()) && itemId.getPath().startsWith("music_disc_");
        }
        return false;
    }

    private static ItemStack stackFromItemId(String itemId, Item fallback) {
        if (itemId == null || itemId.isBlank()) {
            return new ItemStack(fallback);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        return item == Items.AIR ? new ItemStack(fallback) : new ItemStack(item);
    }

    private static Component itemName(String itemId) {
        return Component.translatable(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)).getDescriptionId());
    }

    private static Component entityName(String entityId) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entityId)).getDescription();
    }
}
