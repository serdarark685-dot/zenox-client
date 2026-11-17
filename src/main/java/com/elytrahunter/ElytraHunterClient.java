package com.elytrahunter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * Zenox Client (formerly Elytra Hunter) 1.2.2
 *
 * Modules: Elytra Hunter, Anchor Aura, Crystal Aura, Auto Totem. ClickGUI = Right Shift.
 *
 * Hunting logic = 1.0.0 logic (ported from the compiled classes, same constants).
 * 1.1.0: config (firework interval), settings screen, always-on HUD tab, target marker.
 * 1.2.0: elytra-target options (smooth rotation, prediction modes, aim point, safe mode, far firework interval).
 */
public class ElytraHunterClient implements ClientModInitializer {

    public enum State { APPROACH, RETREAT }

    // ---- constants of the original 1.0.0 ----
    private static final double SCAN_RANGE = 96.0;        // new target search radius
    private static final double KEEP_RANGE = 144.0;       // target is dropped beyond this
    private static final double ATTACK_REACH = 3.0;
    private static final double NO_BOOST_DIST = 5.0;      // no rockets while closer than this (approach)
    private static final int RETREAT_MAX_TICKS = 12;
    private static final float MIN_COOLDOWN = 0.9f;
    private static final Set<String> FRIENDS = Set.of();  // names that are never targeted

    // ---- hotkeys (polled with GLFW, only while no screen is open) ----
    private static final int KEY_MENU = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static final int KEY_TOGGLE = GLFW.GLFW_KEY_H;

    private static ElytraHunterClient instance;

    private State state = State.APPROACH;
    private AbstractClientPlayerEntity target;
    private Vec3d lastTargetPos;
    private int retreatTicks;
    private int tickCounter;
    private int rocketCooldown = 1000;
    private boolean jumpHeld;
    private boolean active;
    private boolean menuKeyWas;
    private boolean toggleKeyWas;

    // ---- snapshots used by the HUD for smooth interpolation (written at END tick) ----
    Vec3d prevEye, curEye, prevTarget, curTarget;
    float prevYaw, curYaw, prevPitch, curPitch;
    long tickEndNanos = System.nanoTime();
    private AbstractClientPlayerEntity snapTarget;
    private boolean hasSnap;

    public static ElytraHunterClient instance() { return instance; }
    public State getState() { return state; }
    public AbstractClientPlayerEntity getTarget() { return target; }
    public boolean isActive() { return active; }

    @Override
    public void onInitializeClient() {
        instance = this;
        HunterConfig.load();
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
        ClientTickEvents.END_CLIENT_TICK.register(this::endTick);
        HudElementRegistry.addLast(Identifier.of("elytrahunter", "hud"),
                (context, tickCounter) -> HunterHud.render(context, MinecraftClient.getInstance()));
    }

    // ------------------------------------------------------------------ keys
    private void pollKeys(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long window = mc.getWindow().getHandle();
        boolean menuDown = GLFW.glfwGetKey(window, KEY_MENU) == GLFW.GLFW_PRESS;
        boolean toggleDown = GLFW.glfwGetKey(window, KEY_TOGGLE) == GLFW.GLFW_PRESS;

        if (mc.currentScreen == null && mc.player != null) {
            if (menuDown && !menuKeyWas) {
                mc.setScreen(new HunterScreen());
            }
            if (toggleDown && !toggleKeyWas) {
                HunterConfig.enabled = !HunterConfig.enabled;
                HunterConfig.save();
                mc.player.sendMessage(Text.literal("Zenox Client: "
                        + (HunterConfig.enabled ? "A\u00c7IK" : "KAPALI")), true);
            }
        }
        menuKeyWas = menuDown;
        toggleKeyWas = toggleDown;
    }

    // ------------------------------------------------------------------ main tick (original logic)
    private void tick(MinecraftClient mc) {
        pollKeys(mc);

        // combat modules (each one returns true when it acted this tick)
        boolean busy = false;
        if (mc.player != null && mc.world != null && mc.interactionManager != null) {
            AutoTotem.tick(mc);
            busy |= CrystalAura.tick(mc);
            busy |= AnchorAura.tick(mc);
        }

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            reset(mc);
            return;
        }
        if (!HunterConfig.enabled
                || !player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                || !hasRocket(player)) {
            reset(mc);
            return;
        }
        if (busy) {
            // a combat module just used the hotbar / rotation: skip the hunter logic for this tick
            releaseJump(mc);
            return;
        }
        active = true;
        tickCounter++;
        if (rocketCooldown < 1000) rocketCooldown++;

        if (target == null || !valid(player, target)) {
            target = findTarget(mc, player);
            state = State.APPROACH;
            lastTargetPos = null;
            retreatTicks = 0;
        }
        if (target == null) {
            releaseJump(mc);
            return;
        }

        Vec3d targetPos = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        Vec3d targetVel = lastTargetPos == null ? Vec3d.ZERO : targetPos.subtract(lastTargetPos);
        lastTargetPos = targetPos;

        Vec3d eye = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
        double dist = eye.distanceTo(targetPos);
        double speed = Math.max(player.getVelocity().length(), 1.0);
        Vec3d aim = predictAim(targetPos, targetVel, dist, speed);
        if (HunterConfig.aimPoint == 1) {
            // aim at the eyes instead of the middle of the body
            aim = aim.add(0.0, target.getEyeY() - targetPos.y, 0.0);
        }

        if (!player.isGliding()) {
            // on the ground / falling: tap jump every other tick to open the elytra
            jumpHeld = true;
            mc.options.jumpKey.setPressed(tickCounter % 2 == 0);
            lookAt(player, eye, aim);
            return;
        }

        releaseJump(mc);
        equipSword(player);

        if (HunterConfig.safeMode
                && !mc.world.isSpaceEmpty(player, player.getBoundingBox().offset(player.getVelocity()))) {
            // about to hit something next tick: nudge upwards
            player.addVelocity(0.0, 0.1, 0.0);
        }

        if (state == State.APPROACH) {
            lookAt(player, eye, aim);
            if (dist > NO_BOOST_DIST || HunterConfig.beeMode) {
                useFirework(mc, player, dist);
            }
            if (reachDistance(eye, target) <= ATTACK_REACH
                    && player.getAttackCooldownProgress(0.0f) >= MIN_COOLDOWN) {
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                // already farther than the retreat distance (or retreat set very small): keep attacking
                state = (!HunterConfig.beeMode && dist < HunterConfig.retreatDistance) ? State.RETREAT : State.APPROACH;
                retreatTicks = 0;
            }
        } else {
            if (HunterConfig.beeMode) {
                // bee mode switched on while retreating: go straight back to attacking
                state = State.APPROACH;
                return;
            }
            retreatTicks++;
            Vec3d away = eye.subtract(targetPos);
            if (away.lengthSquared() < 1.0E-4) {
                away = new Vec3d(0.0, 1.0, 0.0);
            }
            Vec3d retreatPoint = eye.add(away.normalize().multiply(8.0)).add(0.0, 1.0, 0.0);
            lookAt(player, eye, retreatPoint);
            if (HunterConfig.retreatFireworks) {
                useFirework(mc, player, dist);
            }

            // turn back as soon as we are far enough (menu setting) or the safety cap is hit
            boolean farEnough = dist >= HunterConfig.retreatDistance;
            if (farEnough || retreatTicks >= RETREAT_MAX_TICKS) {
                state = State.APPROACH;
            }
        }
    }

    private boolean valid(ClientPlayerEntity player, AbstractClientPlayerEntity t) {
        return t.isAlive() && !t.isRemoved() && !t.isSpectator() && player.distanceTo(t) <= KEEP_RANGE;
    }

    private AbstractClientPlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity player) {
        AbstractClientPlayerEntity best = null;
        double bestDist = SCAN_RANGE;
        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == player) continue;
            if (!other.isAlive()) continue;
            if (other.isSpectator()) continue;
            if (FRIENDS.contains(other.getGameProfile().name())) continue;
            double d = player.distanceTo(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    /** Distance from a point to the closest point of the target's hitbox. */
    private double reachDistance(Vec3d from, AbstractClientPlayerEntity t) {
        Box box = t.getBoundingBox();
        double x = MathHelper.clamp(from.x, box.minX, box.maxX);
        double y = MathHelper.clamp(from.y, box.minY, box.maxY);
        double z = MathHelper.clamp(from.z, box.minZ, box.maxZ);
        return from.distanceTo(new Vec3d(x, y, z));
    }

    private void lookAt(ClientPlayerEntity player, Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = MathHelper.clamp((float) (-(MathHelper.atan2(dy, horizontal) * 180.0 / Math.PI)), -90.0f, 90.0f);

        if (!HunterConfig.smoothRotation) {
            player.setYaw(yaw);
            player.setPitch(pitch);
            return;
        }

        // speed-limited turning (menu: yaw / pitch speed in degrees per tick)
        float deltaYaw = MathHelper.wrapDegrees(yaw - player.getYaw());
        float deltaPitch = pitch - player.getPitch();
        double now = System.currentTimeMillis();
        // gentle wobble so the turn rate is not perfectly constant (1.0x .. 1.5x)
        float wobble = 1.25f + 0.25f * (float) Math.sin(now / 300.0);
        // target is behind us: turn harder
        float behind = Math.abs(deltaYaw) > 90.0f ? 1.5f : 1.0f;
        float maxYaw = HunterConfig.yawSpeed * wobble * behind;
        float maxPitch = HunterConfig.pitchSpeed * wobble;
        float moveYaw = MathHelper.clamp(deltaYaw, -maxYaw, maxYaw);
        float movePitch = MathHelper.clamp(deltaPitch, -maxPitch, maxPitch);
        // tiny drift once almost aligned
        if (Math.abs(deltaYaw) + Math.abs(deltaPitch) < 5.0f) {
            float drift = (float) (Math.sin(now / 90.0) * 0.07 + Math.cos(now / 130.0) * 0.05);
            moveYaw += drift * 0.2f;
            movePitch += drift * 0.8f;
        }
        player.setYaw(player.getYaw() + moveYaw);
        player.setPitch(MathHelper.clamp(player.getPitch() + movePitch, -90.0f, 90.0f));
    }

    /** Where to aim, depending on the prediction mode chosen in the menu. */
    private Vec3d predictAim(Vec3d targetPos, Vec3d targetVel, double dist, double speed) {
        switch (HunterConfig.predictMode) {
            case 0:
                return targetPos;
            case 2:
                return targetPos.add(targetVel.multiply(HunterConfig.predictMultiplier));
            default: {
                double lead = MathHelper.clamp(dist / speed, 0.0, 4.0);
                return targetPos.add(targetVel.multiply(lead));
            }
        }
    }

    /**
     * Fires a rocket, at most once every HunterConfig.fireworkInterval ticks.
     * Offhand mode: use the offhand rocket.
     * Hotbar mode: select the rocket slot, use it, and switch straight back - all in the same tick.
     */
    private void useFirework(MinecraftClient mc, ClientPlayerEntity player, double dist) {
        int interval = dist > HunterConfig.farDistance ? HunterConfig.farInterval : HunterConfig.fireworkInterval;
        if (rocketCooldown < Math.max(1, interval)) return;

        if (HunterConfig.hotbarMode) {
            PlayerInventory inv = player.getInventory();
            int rocketSlot = findHotbar(inv, Items.FIREWORK_ROCKET);
            if (rocketSlot < 0) return;
            int previous = inv.getSelectedSlot();
            inv.setSelectedSlot(rocketSlot);
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            inv.setSelectedSlot(previous);
        } else {
            if (!player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) return;
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
        }
        rocketCooldown = 0;
    }

    private boolean hasRocket(ClientPlayerEntity player) {
        if (!HunterConfig.hotbarMode) {
            return player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
        }
        return findHotbar(player.getInventory(), Items.FIREWORK_ROCKET) >= 0;
    }

    private int findHotbar(PlayerInventory inv, Item item) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private void equipSword(ClientPlayerEntity player) {
        if (player.getMainHandStack().isIn(ItemTags.SWORDS)) return;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isIn(ItemTags.SWORDS)) {
                inv.setSelectedSlot(i);
                return;
            }
        }
    }

    private void releaseJump(MinecraftClient mc) {
        if (jumpHeld) {
            mc.options.jumpKey.setPressed(false);
            jumpHeld = false;
        }
    }

    private void reset(MinecraftClient mc) {
        releaseJump(mc);
        target = null;
        lastTargetPos = null;
        state = State.APPROACH;
        retreatTicks = 0;
        active = false;
    }

    // ------------------------------------------------------------------ HUD snapshots
    private void endTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            hasSnap = false;
            curTarget = null;
            snapTarget = null;
            return;
        }
        Vec3d eye = new Vec3d(p.getX(), p.getEyeY(), p.getZ());
        prevEye = hasSnap && curEye != null ? curEye : eye;
        curEye = eye;
        prevYaw = hasSnap ? curYaw : p.getYaw();
        curYaw = p.getYaw();
        prevPitch = hasSnap ? curPitch : p.getPitch();
        curPitch = p.getPitch();
        hasSnap = true;

        if (target != null && active) {
            Vec3d c = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
            prevTarget = (curTarget == null || snapTarget != target) ? c : curTarget;
            curTarget = c;
            snapTarget = target;
        } else {
            curTarget = null;
            snapTarget = null;
        }
        tickEndNanos = System.nanoTime();
    }
}
