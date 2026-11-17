package com.elytrahunter;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

/** Shared helpers for Anchor Aura / Crystal Aura / Auto Totem. */
final class CombatUtil {

    /** Block interaction distance the server accepts is about 4.5 (+1 slack); stay inside it. */
    static final double PLACE_REACH = 4.5;

    private CombatUtil() {}

    static Vec3d eye(Entity e) {
        return new Vec3d(e.getX(), e.getEyeY(), e.getZ());
    }

    static double dist2(Entity e, Vec3d v) {
        double dx = e.getX() - v.x;
        double dy = e.getY() - v.y;
        double dz = e.getZ() - v.z;
        return dx * dx + dy * dy + dz * dz;
    }

    static int findHotbar(PlayerInventory inv, Item item) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    static AbstractClientPlayerEntity nearestTarget(MinecraftClient mc, ClientPlayerEntity p, double range) {
        AbstractClientPlayerEntity best = null;
        double bestDist = range;
        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == p) continue;
            if (!other.isAlive() || other.isSpectator()) continue;
            double d = p.distanceTo(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    /** Distance from a point to the closest point of a hitbox. */
    static double reachDistance(Vec3d from, Box box) {
        double x = MathHelper.clamp(from.x, box.minX, box.maxX);
        double y = MathHelper.clamp(from.y, box.minY, box.maxY);
        double z = MathHelper.clamp(from.z, box.minZ, box.maxZ);
        return from.distanceTo(new Vec3d(x, y, z));
    }

    static void lookAt(ClientPlayerEntity p, Vec3d to) {
        Vec3d from = eye(p);
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = MathHelper.clamp((float) (-(MathHelper.atan2(dy, horizontal) * 180.0 / Math.PI)), -90.0f, 90.0f);
        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    /**
     * Places the item in the given hotbar slot at pos by clicking an adjacent solid block.
     * Switches to the slot, clicks, switches back. Returns true when a click was sent.
     */
    static boolean placeBlock(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos, int slot, boolean rotate) {
        BlockState st = mc.world.getBlockState(pos);
        if (!st.isAir() && !st.isReplaceable()) return false;
        Vec3d eye = eye(p);
        for (Direction d : Direction.values()) {
            BlockPos nb = pos.offset(d);
            BlockState ns = mc.world.getBlockState(nb);
            if (ns.isAir() || ns.isReplaceable()) continue;
            Direction face = d.getOpposite();
            Vec3d hit = new Vec3d(nb.getX() + 0.5 + face.getOffsetX() * 0.5,
                    nb.getY() + 0.5 + face.getOffsetY() * 0.5,
                    nb.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            if (eye.distanceTo(hit) > PLACE_REACH) continue;
            PlayerInventory inv = p.getInventory();
            int previous = inv.getSelectedSlot();
            inv.setSelectedSlot(slot);
            if (rotate) lookAt(p, hit);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, nb, false));
            p.swingHand(Hand.MAIN_HAND);
            inv.setSelectedSlot(previous);
            return true;
        }
        return false;
    }

    /** Right-click a specific face of an existing block with the item in the given hotbar slot. */
    static void useOnBlock(MinecraftClient mc, ClientPlayerEntity p, BlockPos block, Direction face, int slot, boolean rotate) {
        Vec3d hit = new Vec3d(block.getX() + 0.5 + face.getOffsetX() * 0.5,
                block.getY() + 0.5 + face.getOffsetY() * 0.5,
                block.getZ() + 0.5 + face.getOffsetZ() * 0.5);
        PlayerInventory inv = p.getInventory();
        int previous = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        if (rotate) lookAt(p, hit);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, block, false));
        p.swingHand(Hand.MAIN_HAND);
        inv.setSelectedSlot(previous);
    }

    // ------------------------------------------------------------------ explosion damage estimate
    /**
     * Fraction (0..1) of the target's hitbox that an explosion at src can "see".
     * Same sampling grid as vanilla; any block in between blocks that ray.
     */
    static float exposure(MinecraftClient mc, Vec3d src, Entity e) {
        Box box = e.getBoundingBox();
        double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
        int seen = 0;
        int total = 0;
        for (double u = 0.0; u <= 1.0; u += dx) {
            for (double v = 0.0; v <= 1.0; v += dy) {
                for (double w = 0.0; w <= 1.0; w += dz) {
                    Vec3d point = new Vec3d(
                            MathHelper.lerp(u, box.minX, box.maxX) + ox,
                            MathHelper.lerp(v, box.minY, box.maxY),
                            MathHelper.lerp(w, box.minZ, box.maxZ) + oz);
                    BlockHitResult r = mc.world.raycast(new RaycastContext(point, src,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
                    if (r.getType() == HitResult.Type.MISS) seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0.0f : (float) seen / (float) total;
    }

    /**
     * Estimated damage an explosion of the given power at src does to e
     * (distance + exposure + difficulty + armor; enchantments / effects are ignored).
     * End crystal = power 6, respawn anchor = power 5.
     */
    static float explosionDamage(MinecraftClient mc, Vec3d src, LivingEntity e, float power) {
        double d = Math.sqrt(dist2(e, src)) / (power * 2.0);
        if (d > 1.0) return 0.0f;
        double impact = (1.0 - d) * exposure(mc, src, e);
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * power * 2.0 + 1.0);
        dmg = scale(mc, dmg);
        float armor = e.getArmor();
        dmg = dmg * (1.0f - MathHelper.clamp(Math.max(armor / 5.0f, armor - dmg / 2.0f), 0.0f, 20.0f) / 25.0f);
        return Math.max(0.0f, dmg);
    }

    /** Upper bound without exposure / armor (used to throw away hopeless candidates cheaply). */
    static float cheapDamage(MinecraftClient mc, double dist, float power) {
        double d = dist / (power * 2.0);
        if (d > 1.0) return 0.0f;
        double impact = 1.0 - d;
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * power * 2.0 + 1.0);
        return scale(mc, dmg);
    }

    private static float scale(MinecraftClient mc, float dmg) {
        Difficulty diff = mc.world.getDifficulty();
        if (diff == Difficulty.PEACEFUL) return 0.0f;
        if (diff == Difficulty.EASY) return Math.min(dmg / 2.0f + 1.0f, dmg);
        if (diff == Difficulty.HARD) return dmg * 1.5f;
        return dmg;
    }
}
