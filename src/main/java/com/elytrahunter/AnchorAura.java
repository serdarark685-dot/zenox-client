package com.elytrahunter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Anchor Aura ("safe anchor").
 *
 * Scans for a nearby player, then builds, one action at a time (speed = actions per second):
 *   1. glowstone block(s) right in front of you (feet, and head height if "double shield" is on)
 *   2. a respawn anchor on the far side of that glowstone, toward the target
 *   3. charges the anchor with glowstone from the hotbar
 *   4. detonates it with a non-glowstone slot
 * The glowstone sits between you and the anchor, so the blast cannot "see" you and you take (almost) no damage.
 * Needs glowstone + respawn anchor in the hotbar. Does not work in the Nether (anchors do not explode there).
 */
final class AnchorAura {

    private enum Step { IDLE, SHIELD_LOW, SHIELD_HIGH, ANCHOR, CHARGE, EXPLODE }

    private enum Result { ADVANCE, ACTED, FAIL }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private static Step step = Step.IDLE;
    private static BlockPos low;
    private static BlockPos high;
    private static BlockPos anchor;
    private static double acc;
    private static int fails;
    private static int cooldown;

    private AnchorAura() {}

    private static void reset() {
        step = Step.IDLE;
        acc = 0.0;
        fails = 0;
    }

    /** @return true when a click was sent this tick. */
    static boolean tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!HunterConfig.anchorEnabled || p == null || mc.world == null
                || mc.interactionManager == null || mc.currentScreen != null) {
            reset();
            cooldown = 0;
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        PlayerInventory inv = p.getInventory();
        int glow = CombatUtil.findHotbar(inv, Items.GLOWSTONE);
        int anchorSlot = CombatUtil.findHotbar(inv, Items.RESPAWN_ANCHOR);
        if (glow < 0 || anchorSlot < 0) {
            reset();
            return false;
        }

        acc = Math.min(acc + HunterConfig.anchorSpeed / 20.0, 1.0);
        if (acc < 1.0) return false;

        if (step == Step.IDLE) {
            AbstractClientPlayerEntity target = CombatUtil.nearestTarget(mc, p, HunterConfig.anchorRange);
            if (target == null) return false;
            if (!pickLayout(mc, p, target)) return false;
            step = Step.SHIELD_LOW;
            fails = 0;
        }

        for (int guard = 0; guard < 8; guard++) {
            Result r = stepOnce(mc, p, inv, glow, anchorSlot);
            if (r == Result.ADVANCE) continue;
            if (r == Result.ACTED) {
                acc -= 1.0;
                return true;
            }
            return false;
        }
        return false;
    }

    /** Picks the horizontal direction closest to the target where the shield and the anchor fit. */
    private static boolean pickLayout(MinecraftClient mc, ClientPlayerEntity p, AbstractClientPlayerEntity target) {
        BlockPos feet = BlockPos.ofFloored(p.getX(), p.getY(), p.getZ());
        double tx = target.getX() - p.getX();
        double tz = target.getZ() - p.getZ();
        Direction best = null;
        double bestScore = -1.0E9;
        for (Direction d : HORIZONTAL) {
            BlockPos l = feet.offset(d);
            BlockPos a = feet.offset(d, 2);
            if (!freeOr(mc, l, Blocks.GLOWSTONE) || !freeOr(mc, a, Blocks.RESPAWN_ANCHOR)) continue;
            if (blockedByEntity(mc, l) || blockedByEntity(mc, a)) continue;
            double score = tx * d.getOffsetX() + tz * d.getOffsetZ();
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best == null) return false;
        low = feet.offset(best);
        high = low.up();
        anchor = feet.offset(best, 2);
        return true;
    }

    private static boolean freeOr(MinecraftClient mc, BlockPos pos, net.minecraft.block.Block already) {
        BlockState st = mc.world.getBlockState(pos);
        return st.isAir() || st.isReplaceable() || st.isOf(already);
    }

    private static boolean blockedByEntity(MinecraftClient mc, BlockPos pos) {
        List<Entity> list = mc.world.getOtherEntities(null, new Box(pos), e -> e instanceof LivingEntity && e.isAlive());
        return !list.isEmpty();
    }

    private static Result stepOnce(MinecraftClient mc, ClientPlayerEntity p, PlayerInventory inv, int glow, int anchorSlot) {
        boolean rot = HunterConfig.anchorRotate;
        switch (step) {
            case SHIELD_LOW: {
                if (mc.world.getBlockState(low).isOf(Blocks.GLOWSTONE)) {
                    step = HunterConfig.anchorDoubleShield ? Step.SHIELD_HIGH : Step.ANCHOR;
                    return Result.ADVANCE;
                }
                if (CombatUtil.placeBlock(mc, p, low, glow, rot)) {
                    fails = 0;
                    return Result.ACTED;
                }
                return fail();
            }
            case SHIELD_HIGH: {
                if (mc.world.getBlockState(high).isOf(Blocks.GLOWSTONE)) {
                    step = Step.ANCHOR;
                    return Result.ADVANCE;
                }
                if (CombatUtil.placeBlock(mc, p, high, glow, rot)) {
                    fails = 0;
                    return Result.ACTED;
                }
                return fail();
            }
            case ANCHOR: {
                BlockState st = mc.world.getBlockState(anchor);
                if (st.isOf(Blocks.RESPAWN_ANCHOR)) {
                    step = Step.CHARGE;
                    return Result.ADVANCE;
                }
                if (CombatUtil.placeBlock(mc, p, anchor, anchorSlot, rot)) {
                    fails = 0;
                    return Result.ACTED;
                }
                return fail();
            }
            case CHARGE: {
                BlockState st = mc.world.getBlockState(anchor);
                if (!st.isOf(Blocks.RESPAWN_ANCHOR)) {
                    // the server did not accept the anchor: place it again
                    step = Step.ANCHOR;
                    return fail();
                }
                if (st.get(RespawnAnchorBlock.CHARGES) >= 1) {
                    step = Step.EXPLODE;
                    return Result.ADVANCE;
                }
                CombatUtil.useOnBlock(mc, p, anchor, Direction.UP, glow, rot);
                return Result.ACTED;
            }
            case EXPLODE: {
                BlockState st = mc.world.getBlockState(anchor);
                if (!st.isOf(Blocks.RESPAWN_ANCHOR)) {
                    reset();
                    return Result.FAIL;
                }
                int use = nonGlowstoneSlot(inv);
                CombatUtil.useOnBlock(mc, p, anchor, Direction.UP, use, rot);
                reset();
                cooldown = 8;
                return Result.ACTED;
            }
            default:
                reset();
                return Result.FAIL;
        }
    }

    private static Result fail() {
        if (++fails > 6) reset();
        return Result.FAIL;
    }

    /** Empty slot first (empty hand detonates), then a sword, then anything that is not glowstone. */
    private static int nonGlowstoneSlot(PlayerInventory inv) {
        int sword = -1;
        int other = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.GLOWSTONE)) continue;
            if (inv.getStack(i).isEmpty()) return i;
            if (sword < 0 && inv.getStack(i).isIn(ItemTags.SWORDS)) sword = i;
            if (other < 0) other = i;
        }
        if (sword >= 0) return sword;
        return other >= 0 ? other : inv.getSelectedSlot();
    }
}
