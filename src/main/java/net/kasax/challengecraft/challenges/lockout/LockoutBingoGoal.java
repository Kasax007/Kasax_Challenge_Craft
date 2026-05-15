package net.kasax.challengecraft.challenges.lockout;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;

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

    public Text title() {
        if (!titleKey.isBlank()) {
            return Text.translatable(titleKey);
        }

        return switch (type) {
            case ITEM, CRAFT, CONSUME, EQUIP -> itemName(primaryTarget());
            case ITEM_AMOUNT -> Text.translatable("challengecraft.lockout.goal.item_amount.title", amount, itemName(primaryTarget()));
            case ITEM_TAG -> Text.translatable("challengecraft.lockout.goal." + id + ".title");
            case KILL -> entityName(primaryTarget());
            case DIMENSION -> Text.translatable("challengecraft.lockout.dimension." + primaryTarget().replace(':', '.'));
            case BIOME -> Text.translatable("biome." + primaryTarget().replace(':', '.'));
            default -> Text.translatable("challengecraft.lockout.goal." + id + ".title");
        };
    }

    public Text description() {
        if (!descriptionKey.isBlank()) {
            return Text.translatable(descriptionKey);
        }

        return switch (type) {
            case ITEM -> Text.translatable("challengecraft.lockout.goal.obtain_item.desc", title());
            case ITEM_AMOUNT -> Text.translatable("challengecraft.lockout.goal.item_amount.desc", amount, itemName(primaryTarget()));
            case ITEM_TAG -> Text.translatable("challengecraft.lockout.goal.obtain_tag.desc", title());
            case CRAFT -> Text.translatable("challengecraft.lockout.goal.craft.desc", title());
            case KILL -> Text.translatable("challengecraft.lockout.goal.kill_entity.desc", title());
            case CONSUME -> Text.translatable("challengecraft.lockout.goal.consume.desc", title());
            case DIMENSION -> Text.translatable("challengecraft.lockout.goal.dimension.desc", title());
            case BIOME -> Text.translatable("challengecraft.lockout.goal.biome.desc", title());
            case EQUIP -> Text.translatable("challengecraft.lockout.goal.equip.desc", title());
            case INTERACT -> Text.translatable("challengecraft.lockout.goal.interact.desc", title());
            case TRADE -> Text.translatable("challengecraft.lockout.goal.trade.desc", title());
            case STRUCTURE -> Text.translatable("challengecraft.lockout.goal.structure.desc", title());
            case ADVANCEMENT -> Text.translatable("challengecraft.lockout.goal.advancement.desc", title());
            case INVENTORY_SET -> Text.translatable("challengecraft.lockout.goal.inventory_set.desc", amount, title());
            case FISHING -> Text.translatable("challengecraft.lockout.goal.fishing.desc", title());
            case ACTION -> Text.translatable("challengecraft.lockout.goal.action.desc", title());
            case BREW -> Text.translatable("challengecraft.lockout.goal.brew.desc", title());
            case ENCHANT -> Text.translatable("challengecraft.lockout.goal.enchant.desc", title());
            case PLACE_BLOCK -> Text.translatable("challengecraft.lockout.goal.place_block.desc", title());
            case DAMAGE_EVENT -> Text.translatable("challengecraft.lockout.goal.damage_event.desc", title());
            case STATUS_EFFECT -> Text.translatable("challengecraft.lockout.goal.status_effect.desc", title());
            case HEALTH_CHECK -> Text.translatable("challengecraft.lockout.goal.health_check.desc", title());
            case LOCATION -> Text.translatable("challengecraft.lockout.goal.location.desc", title());
            default -> Text.translatable("challengecraft.lockout.goal.todo.desc", title());
        };
    }

    public Text condition() {
        if (!isImplemented()) {
            return Text.translatable("challengecraft.lockout.goal.todo.condition");
        }

        return switch (type) {
            case ITEM -> Text.translatable("challengecraft.lockout.goal.obtain_item.condition");
            case ITEM_AMOUNT -> Text.translatable("challengecraft.lockout.goal.item_amount.condition");
            case ITEM_TAG -> Text.translatable("challengecraft.lockout.goal.obtain_tag.condition");
            case CRAFT -> Text.translatable("challengecraft.lockout.goal.craft.condition");
            case KILL -> Text.translatable("challengecraft.lockout.goal.kill_entity.condition");
            case CONSUME -> Text.translatable("challengecraft.lockout.goal.consume.condition");
            case DIMENSION -> Text.translatable("challengecraft.lockout.goal.dimension.condition");
            case BIOME -> Text.translatable("challengecraft.lockout.goal.biome.condition");
            case EQUIP -> Text.translatable("challengecraft.lockout.goal.equip.condition");
            case INTERACT -> Text.translatable("challengecraft.lockout.goal.interact.condition");
            case TRADE -> Text.translatable("challengecraft.lockout.goal.trade.condition");
            case STRUCTURE -> Text.translatable("challengecraft.lockout.goal.structure.condition");
            case ADVANCEMENT -> Text.translatable("challengecraft.lockout.goal.advancement.condition");
            case INVENTORY_SET -> Text.translatable("challengecraft.lockout.goal.inventory_set.condition");
            case FISHING -> Text.translatable("challengecraft.lockout.goal.fishing.condition");
            case ACTION -> Text.translatable("challengecraft.lockout.goal.action.condition");
            case BREW -> Text.translatable("challengecraft.lockout.goal.brew.condition");
            case ENCHANT -> Text.translatable("challengecraft.lockout.goal.enchant.condition");
            case PLACE_BLOCK -> Text.translatable("challengecraft.lockout.goal.place_block.condition");
            case DAMAGE_EVENT -> Text.translatable("challengecraft.lockout.goal.damage_event.condition");
            case STATUS_EFFECT -> Text.translatable("challengecraft.lockout.goal.status_effect.condition");
            case HEALTH_CHECK -> Text.translatable("challengecraft.lockout.goal.health_check.condition");
            case LOCATION -> Text.translatable("challengecraft.lockout.goal.location.condition");
            default -> Text.translatable("challengecraft.lockout.goal.todo.condition");
        };
    }

    public ItemStack createIconStack() {
        if (!iconItemId.isBlank()) {
            Item iconItem = Registries.ITEM.get(Identifier.of(iconItemId));
            if (iconItem != Items.AIR) {
                return new ItemStack(iconItem);
            }
        }

        return switch (type) {
            case ITEM, ITEM_AMOUNT, ITEM_TAG, CRAFT, CONSUME, EQUIP -> stackFromItemId(primaryTarget(), Items.MAP);
            case KILL -> {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(primaryTarget()));
                var egg = net.minecraft.item.SpawnEggItem.forEntity(entityType);
                yield egg != null ? new ItemStack(egg) : new ItemStack(Items.ZOMBIE_SPAWN_EGG);
            }
            default -> new ItemStack(Items.MAP);
        };
    }

    public boolean matchesItem(ItemStack stack) {
        return switch (type) {
            case ITEM, ITEM_AMOUNT, CRAFT, CONSUME, EQUIP -> targets.stream().anyMatch(target -> stack.isOf(Registries.ITEM.get(Identifier.of(target))));
            case ITEM_TAG -> matchesTagLikeGoal(stack);
            default -> false;
        };
    }

    private boolean matchesTagLikeGoal(ItemStack stack) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if ("minecraft:music_discs".equals(primaryTarget())) {
            return "minecraft".equals(itemId.getNamespace()) && itemId.getPath().startsWith("music_disc_");
        }
        return false;
    }

    private static ItemStack stackFromItemId(String itemId, Item fallback) {
        if (itemId == null || itemId.isBlank()) {
            return new ItemStack(fallback);
        }
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        return item == Items.AIR ? new ItemStack(fallback) : new ItemStack(item);
    }

    private static Text itemName(String itemId) {
        return Text.translatable(Registries.ITEM.get(Identifier.of(itemId)).getTranslationKey());
    }

    private static Text entityName(String entityId) {
        return Registries.ENTITY_TYPE.get(Identifier.of(entityId)).getName();
    }
}
