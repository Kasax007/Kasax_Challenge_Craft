package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Hunts mobs of the given kinds: finds the nearest one, runs up to it, hits it whenever the
 * weapon is recharged (full-strength hits, as a player would), and picks up the drops. Ends after
 * {@code kills} kills, or once the bot holds {@code count} of the wanted {@code loot}.
 */
public final class KillTask implements BotTask {
    private static final double REACH = 2.9;

    private final Set<EntityType<?>> types;
    private final Set<Item> loot;
    private final int count, kills;
    private final Set<java.util.UUID> unreachable = new java.util.HashSet<>();
    private LivingEntity target;
    private BlockPos deathSpot;
    private int killed, collectTicks, repath, explores, chaseTicks, direct, exploreTicks, lookCooldown;
    private final Explorer explorer = new Explorer(2400);
    private boolean walking;

    /** {@code loot} may be empty when only the kill matters (then {@code kills} counts). */
    public KillTask(Set<EntityType<?>> types, Set<Item> loot, int count, int kills) {
        this.types = Set.copyOf(types);
        this.loot = Set.copyOf(loot);
        this.count = count;
        this.kills = kills;
    }

    private boolean done(Bot bot) {
        if (!loot.isEmpty()) return ObtainPlanner.countAny(bot.body(), loot) >= count;
        return killed >= Math.max(1, kills);
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (collectTicks > 0) {
            collectTicks--;
            List<ItemEntity> drops = BotWorld.drops(level, deathSpot, 5, loot.isEmpty() ? null : loot);
            if (!drops.isEmpty()) {
                if (!walking || bot.navigator().status() != BotNavigator.Status.MOVING) {
                    bot.navigator().goTo(drops.get(0).blockPosition());
                    walking = true;
                }
                if (bot.navigator().tick() == BotNavigator.Status.FAILED) collectTicks = 0;
                return Result.RUNNING;
            }
            collectTicks = 0;
            walking = false;
            bot.navigator().stop();
        }
        if (done(bot)) return Result.DONE;

        if (target == null || !target.isAlive() || target.distanceTo(body) > 64) {
            if (target != null && !target.isAlive() && target.getLastHurtByMob() == body) {
                killed++;
                deathSpot = target.blockPosition();
                collectTicks = 40;
                target = null;
                return Result.RUNNING;
            }
            if (--lookCooldown > 0) return explore(bot);
            lookCooldown = 20;
            target = nearest(body, level);
            chaseTicks = 0;
            if (target == null) return explore(bot);
            explorer.pause(bot);
            if (walking) bot.navigator().stop();
            walking = false;
            equipWeapon(bot);
        }
        if (++chaseTicks > 1200) {
            // Could not get at this one (behind water, up a cliff): try another.
            unreachable.add(target.getUUID());
            target = null;
            return Result.RUNNING;
        }

        double dist = body.distanceTo(target);
        if (dist <= REACH) {
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            body.lookAt(target.getEyePosition());
            body.forward = dist > 2.0 ? 0.4f : 0f;
            if (body.getAttackStrengthScale(0.5f) >= 1f) {
                body.attack(target);
                body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                if (!target.isAlive()) {
                    killed++;
                    deathSpot = target.blockPosition();
                    collectTicks = 40;
                    target = null;
                }
            }
            return Result.RUNNING;
        }
        // Close and on about the same level: just run at it. Otherwise path there.
        if (dist < 6 && Math.abs(target.getY() - body.getY()) < 1.5 || direct > 0) {
            direct--;
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            body.lookAt(target.getEyePosition());
            body.forward = 1f;
            body.jump = body.horizontalCollision;
            return Result.RUNNING;
        }
        if (!walking || --repath <= 0) {
            bot.navigator().goNear(target.blockPosition(), 2.8);
            walking = true;
            repath = 30;
        }
        if (bot.navigator().tick() == BotNavigator.Status.FAILED) {
            walking = false;
            // No path, but close (a mob in tall grass or bamboo): go straight at it for a while.
            if (dist < 10) direct = 40;
            else {
                unreachable.add(target.getUUID());
                target = null;
            }
        }
        return Result.RUNNING;
    }

    private LivingEntity nearest(BotPlayer body, ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(48),
                        e -> e.isAlive() && types.contains(e.getType()) && e != body && !unreachable.contains(e.getUUID()))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(body))).orElse(null);
    }

    /** Best melee weapon into the hand: the item with the highest attack damage. */
    private static void equipWeapon(Bot bot) {
        var inv = bot.body().getInventory().getNonEquipmentItems();
        int best = -1;
        double bestDamage = 1;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty()) continue;
            double d = attackDamage(s);
            if (d > bestDamage) {
                bestDamage = d;
                best = i;
            }
        }
        if (best >= 0) bot.tools().select(best);
    }

    private static double attackDamage(ItemStack s) {
        var mods = s.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) return 1;
        double[] dmg = {1};
        mods.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) dmg[0] += mod.amount();
        });
        return dmg[0];
    }

    /** None in sight: walk out (up from a mine first) for up to two minutes. */
    private Result explore(Bot bot) {
        Result r = explorer.tick(bot);
        if (r == Result.FAILED) bot.say("found no " + describeTypes());
        return r;
    }

    private String describeTypes() {
        return types.size() == 1 ? BuiltInRegistries.ENTITY_TYPE.getKey(types.iterator().next()).getPath() : "mobs";
    }

    @Override
    public String describe() {
        String what = types.size() == 1 ? BuiltInRegistries.ENTITY_TYPE.getKey(types.iterator().next()).getPath() : types.size() + " kinds of mob";
        return loot.isEmpty() ? "kill " + what + (kills > 1 ? " x" + kills : "")
                : "hunt " + what + " for " + ObtainPlanner.names(loot) + " (" + count + ")";
    }
}
