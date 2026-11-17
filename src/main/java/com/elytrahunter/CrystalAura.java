package com.elytrahunter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Crystal Aura.
 *
 * Damage rules used (vanilla): an end crystal explodes with power 6, so everything within 12 blocks is hit.
 * damage = ((i*i + i) / 2 * 84 + 1) with i = (1 - distance/12) * exposure; exposure is the share of the
 * victim's hitbox the blast can "see", so ANY block between the crystal and you cuts your damage, and the
 * farther you are from the crystal the less you take. The aura computes this for you and for the target
 * and only acts when target damage >= min and self damage <= max (and you would not die).
 */
final class CrystalAura {

    private static final double BREAK_REACH = 3.9;
    private static final float POWER = 6.0f;

    private static double breakAcc;
    private static double placeAcc;

    private CrystalAura() {}

    private static final class Cand {
        final BlockPos pos;
        final double cheap;

        Cand(BlockPos pos, double cheap) {
            this.pos = pos;
            this.cheap = cheap;
        }
    }

    /** @return true when something was done this tick (so other logic leaves rotation / hotbar alone). */
    static boolean tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!HunterConfig.crystalEnabled || p == null || mc.world == null
                || mc.interactionManager == null || mc.currentScreen != null) {
            breakAcc = 0.0;
            placeAcc = 0.0;
            return false;
        }
        breakAcc = Math.min(breakAcc + HunterConfig.crystalBreakSpeed / 20.0, 1.0);
        placeAcc = Math.min(placeAcc + HunterConfig.crystalPlaceSpeed / 20.0, 1.0);

        AbstractClientPlayerEntity target = CombatUtil.nearestTarget(mc, p, 14.0);
        if (target == null) return false;

        Vec3d eye = CombatUtil.eye(p);
        boolean acted = false;

        if (HunterConfig.hitCrystal && swordHit(mc, p, target, eye)) {
            acted = true;
        }
        if (breakAcc >= 1.0 && breakCrystal(mc, p, eye)) {
            breakAcc -= 1.0;
            acted = true;
        }
        if (placeAcc >= 1.0 && placeCrystal(mc, p, eye, target)) {
            placeAcc -= 1.0;
            acted = true;
        }
        return acted;
    }

    // ------------------------------------------------------------------ hit crystal
    private static boolean swordHit(MinecraftClient mc, ClientPlayerEntity p, AbstractClientPlayerEntity target, Vec3d eye) {
        if (CombatUtil.reachDistance(eye, target.getBoundingBox()) > 3.0) return false;
        if (p.getAttackCooldownProgress(0.0f) < 0.9f) return false;
        PlayerInventory inv = p.getInventory();
        if (!p.getMainHandStack().isIn(ItemTags.SWORDS)) {
            int sw = -1;
            for (int i = 0; i < 9; i++) {
                if (inv.getStack(i).isIn(ItemTags.SWORDS)) {
                    sw = i;
                    break;
                }
            }
            if (sw < 0) return false;
            inv.setSelectedSlot(sw);
        }
        if (HunterConfig.crystalRotate) {
            CombatUtil.lookAt(p, new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ()));
        }
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        return true;
    }

    // ------------------------------------------------------------------ break
    private static boolean breakCrystal(MinecraftClient mc, ClientPlayerEntity p, Vec3d eye) {
        Box area = new Box(eye.x - 6.0, eye.y - 6.0, eye.z - 6.0, eye.x + 6.0, eye.y + 6.0, eye.z + 6.0);
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, area,
                c -> c.isAlive() && !c.isRemoved());
        if (crystals.isEmpty()) return false;

        float selfHp = p.getHealth() + p.getAbsorptionAmount();
        EndCrystalEntity best = null;
        float bestScore = -1.0E9f;
        for (EndCrystalEntity c : crystals) {
            if (CombatUtil.reachDistance(eye, c.getBoundingBox()) > BREAK_REACH) continue;
            Vec3d src = new Vec3d(c.getX(), c.getY(), c.getZ());
            float self = CombatUtil.explosionDamage(mc, src, p, POWER);
            if (self >= selfHp - 1.0f) continue;
            if (self > HunterConfig.crystalMaxSelfDamage) continue;
            float tgt = bestTargetDamage(mc, p, src);
            if (tgt < HunterConfig.crystalMinDamage) continue;
            float score = tgt - self;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null) return false;

        if (HunterConfig.crystalRotate) {
            CombatUtil.lookAt(p, new Vec3d(best.getX(), best.getY() + 1.0, best.getZ()));
        }
        mc.interactionManager.attackEntity(p, best);
        p.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private static float bestTargetDamage(MinecraftClient mc, ClientPlayerEntity p, Vec3d src) {
        float best = 0.0f;
        for (AbstractClientPlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            if (CombatUtil.dist2(o, src) > 144.0) continue;
            best = Math.max(best, CombatUtil.explosionDamage(mc, src, o, POWER));
        }
        return best;
    }

    // ------------------------------------------------------------------ place
    private static boolean placeCrystal(MinecraftClient mc, ClientPlayerEntity p, Vec3d eye, AbstractClientPlayerEntity target) {
        boolean offhand = p.getOffHandStack().isOf(Items.END_CRYSTAL);
        int slot = offhand ? -1 : CombatUtil.findHotbar(p.getInventory(), Items.END_CRYSTAL);
        if (!offhand && slot < 0) return false;

        int r = HunterConfig.crystalRange;
        double maxSq = (double) r * r;
        BlockPos base = BlockPos.ofFloored(eye.x, eye.y, eye.z);
        List<Cand> cands = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    double cx = pos.getX() + 0.5;
                    double cy = pos.getY() + 1.0;
                    double cz = pos.getZ() + 0.5;
                    double ex = cx - eye.x;
                    double ey = cy - eye.y;
                    double ez = cz - eye.z;
                    if (ex * ex + ey * ey + ez * ez > maxSq) continue;
                    BlockState st = mc.world.getBlockState(pos);
                    if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    Vec3d src = new Vec3d(cx, cy, cz);
                    double dist = Math.sqrt(CombatUtil.dist2(target, src));
                    if (dist > 12.0) continue;
                    float cheap = CombatUtil.cheapDamage(mc, dist, POWER);
                    if (cheap < HunterConfig.crystalMinDamage) continue;
                    cands.add(new Cand(pos, cheap));
                }
            }
        }
        if (cands.isEmpty()) return false;
        cands.sort(Comparator.comparingDouble((Cand c) -> -c.cheap));

        float selfHp = p.getHealth() + p.getAbsorptionAmount();
        BlockPos bestPos = null;
        float bestScore = -1.0E9f;
        int limit = Math.min(cands.size(), 10);
        for (int i = 0; i < limit; i++) {
            BlockPos pos = cands.get(i).pos;
            Box spawn = new Box(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 3.0, pos.getZ() + 1.0);
            List<Entity> blocking = mc.world.getOtherEntities(null, spawn);
            if (!blocking.isEmpty()) continue;
            Vec3d src = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            float self = CombatUtil.explosionDamage(mc, src, p, POWER);
            if (self >= selfHp - 1.0f || self > HunterConfig.crystalMaxSelfDamage) continue;
            float tgt = CombatUtil.explosionDamage(mc, src, target, POWER);
            if (tgt < HunterConfig.crystalMinDamage) continue;
            float score = tgt - self;
            if (score > bestScore) {
                bestScore = score;
                bestPos = pos;
            }
        }
        if (bestPos == null) return false;

        Vec3d hit = new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 1.0, bestPos.getZ() + 0.5);
        PlayerInventory inv = p.getInventory();
        int previous = inv.getSelectedSlot();
        if (!offhand) inv.setSelectedSlot(slot);
        if (HunterConfig.crystalRotate) CombatUtil.lookAt(p, hit);
        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        mc.interactionManager.interactBlock(p, hand, new BlockHitResult(hit, Direction.UP, bestPos, false));
        p.swingHand(hand);
        if (!offhand) inv.setSelectedSlot(previous);
        return true;
    }
}
