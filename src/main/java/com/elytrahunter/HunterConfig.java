package com.elytrahunter;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny properties-file config: .minecraft/config/elytrahunter.properties (file name kept so old settings survive the rename to Zenox Client) */
public final class HunterConfig {
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 40;

    /** Master switch (H key / menu). */
    public static boolean enabled = true;
    /** Ticks between two firework uses (20 ticks = 1 second). Original behaviour = 2. */
    public static int fireworkInterval = 2;
    /** After a hit: turn back toward the target once this far away (blocks). */
    public static double retreatDistance = 8.0;
    public static final double MIN_RETREAT = 0.5;
    public static final double MAX_RETREAT = 30.0;
    /** Keep firing rockets while retreating (original behaviour = true). */
    public static boolean retreatFireworks = false;
    /** true = rockets come from the hotbar (select rocket, use, back to sword). false = offhand. */
    public static boolean hotbarMode = false;
    /** Slowly cycling rainbow GUI colours instead of fixed neon blue. */
    public static boolean rgb = true;
    /** Rotating square + dot over the locked target. */
    public static boolean showMarker = true;
    /** Always-visible mini tab on the left edge. */
    public static boolean showPanel = true;

    /** Bee mode: never retreat after a hit, keep circling the target and hitting; rockets at any distance. */
    public static boolean beeMode = false;

    // ---- 1.2.0: elytra-target style options (all default to the old 1.1.x behaviour) ----
    /** Speed-limited turning instead of snapping straight onto the target. */
    public static boolean smoothRotation = false;
    public static final int MIN_ROT_SPEED = 5;
    public static final int MAX_ROT_SPEED = 180;
    /** Max yaw / pitch change per tick (degrees) while smoothing is on. */
    public static int yawSpeed = 54;
    public static int pitchSpeed = 42;
    /** 0 = no lead, 1 = hunter (distance based lead, original), 2 = fixed multiplier. */
    public static int predictMode = 1;
    public static final int PREDICT_MODES = 3;
    public static final double MIN_PREDICT = 0.5;
    public static final double MAX_PREDICT = 3.0;
    /** Lead in ticks of target movement for predictMode 2. */
    public static double predictMultiplier = 1.9;
    /** 0 = center of the body (original), 1 = eyes. */
    public static int aimPoint = 0;
    /** Nudge upwards when the next tick would hit a block. */
    public static boolean safeMode = false;
    public static final int MIN_FAR = 10;
    public static final int MAX_FAR = 100;
    /** Beyond this distance the "far" firework interval is used. */
    public static int farDistance = 50;
    public static int farInterval = 2;

    // ---- Zenox: combat modules ----
    /** Anchor Aura: glowstone shield + respawn anchor, "safe anchor". */
    public static boolean anchorEnabled = false;
    /** Actions per second (1..20, 20 = every tick). */
    public static int anchorSpeed = 10;
    /** Players closer than this (blocks) get targeted. */
    public static int anchorRange = 5;
    /** Shield is 2 blocks high (feet + head) instead of 1. */
    public static boolean anchorDoubleShield = true;
    public static boolean anchorRotate = true;

    /** Crystal Aura. */
    public static boolean crystalEnabled = false;
    public static int crystalBreakSpeed = 20;
    public static int crystalPlaceSpeed = 10;
    /** Placement range in blocks. */
    public static int crystalRange = 4;
    /** Only break / place when the estimated damage to the target is at least this much (half hearts). */
    public static int crystalMinDamage = 4;
    /** Never break / place when the estimated damage to you is above this (half hearts). */
    public static int crystalMaxSelfDamage = 5;
    /** Hit crystal: sword-hit the target first (knocks it up), then crystals. */
    public static boolean hitCrystal = false;
    public static boolean crystalRotate = true;

    /** Auto Inventory Totem. */
    public static boolean totemEnabled = false;
    /** Ticks to wait after a pop before swapping (0 = same tick it is noticed). */
    public static int totemDelay = 0;
    public static boolean totemOpenInventory = true;
    public static boolean totemOnlyOnPop = true;

    /** ClickGUI panel positions (-1 = default). */
    public static final int[] panelX = {-1, -1, -1};
    public static final int[] panelY = {-1, -1, -1};

    private HunterConfig() {}

    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(def)));
    }

    private static int num(Properties p, String key, int def, int min, int max) {
        return clampInt(p.getProperty(key, Integer.toString(def)), def, min, max);
    }

    private static int clampInt(String raw, int fallback, int min, int max) {
        int v;
        try {
            v = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            v = fallback;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("elytrahunter.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        showMarker = Boolean.parseBoolean(p.getProperty("showMarker", "true"));
        showPanel = Boolean.parseBoolean(p.getProperty("showPanel", "true"));
        retreatFireworks = Boolean.parseBoolean(p.getProperty("retreatFireworks", "false"));
        hotbarMode = Boolean.parseBoolean(p.getProperty("hotbarMode", "false"));
        rgb = Boolean.parseBoolean(p.getProperty("rgb", "true"));
        beeMode = Boolean.parseBoolean(p.getProperty("beeMode", "false"));
        smoothRotation = Boolean.parseBoolean(p.getProperty("smoothRotation", "false"));
        safeMode = Boolean.parseBoolean(p.getProperty("safeMode", "false"));
        yawSpeed = clampInt(p.getProperty("yawSpeed", "54"), 54, MIN_ROT_SPEED, MAX_ROT_SPEED);
        pitchSpeed = clampInt(p.getProperty("pitchSpeed", "42"), 42, MIN_ROT_SPEED, MAX_ROT_SPEED);
        predictMode = clampInt(p.getProperty("predictMode", "1"), 1, 0, PREDICT_MODES - 1);
        aimPoint = clampInt(p.getProperty("aimPoint", "0"), 0, 0, 1);
        farDistance = clampInt(p.getProperty("farDistance", "50"), 50, MIN_FAR, MAX_FAR);
        farInterval = clampInt(p.getProperty("farInterval", "2"), 2, MIN_INTERVAL, MAX_INTERVAL);
        anchorEnabled = bool(p, "anchorEnabled", false);
        anchorSpeed = num(p, "anchorSpeed", 10, 1, 20);
        anchorRange = num(p, "anchorRange", 5, 2, 8);
        anchorDoubleShield = bool(p, "anchorDoubleShield", true);
        anchorRotate = bool(p, "anchorRotate", true);
        crystalEnabled = bool(p, "crystalEnabled", false);
        crystalBreakSpeed = num(p, "crystalBreakSpeed", 20, 1, 20);
        crystalPlaceSpeed = num(p, "crystalPlaceSpeed", 10, 1, 20);
        crystalRange = num(p, "crystalRange", 4, 2, 5);
        crystalMinDamage = num(p, "crystalMinDamage", 4, 1, 30);
        crystalMaxSelfDamage = num(p, "crystalMaxSelfDamage", 5, 0, 20);
        hitCrystal = bool(p, "hitCrystal", false);
        crystalRotate = bool(p, "crystalRotate", true);
        totemEnabled = bool(p, "totemEnabled", false);
        totemDelay = num(p, "totemDelay", 0, 0, 10);
        totemOpenInventory = bool(p, "totemOpenInventory", true);
        totemOnlyOnPop = bool(p, "totemOnlyOnPop", true);
        for (int i = 0; i < panelX.length; i++) {
            panelX[i] = num(p, "panelX" + i, -1, -1, 5000);
            panelY[i] = num(p, "panelY" + i, -1, -1, 5000);
        }
        try {
            predictMultiplier = Double.parseDouble(p.getProperty("predictMultiplier", "1.9").trim());
        } catch (NumberFormatException e) {
            predictMultiplier = 1.9;
        }
        predictMultiplier = Math.max(MIN_PREDICT, Math.min(MAX_PREDICT, predictMultiplier));
        try {
            retreatDistance = Double.parseDouble(p.getProperty("retreatDistance", "8").trim());
        } catch (NumberFormatException e) {
            retreatDistance = 8.0;
        }
        retreatDistance = Math.max(MIN_RETREAT, Math.min(MAX_RETREAT, retreatDistance));
        try {
            fireworkInterval = Integer.parseInt(p.getProperty("fireworkInterval", "2").trim());
        } catch (NumberFormatException e) {
            fireworkInterval = 2;
        }
        fireworkInterval = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, fireworkInterval));
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("fireworkInterval", Integer.toString(fireworkInterval));
        p.setProperty("showMarker", Boolean.toString(showMarker));
        p.setProperty("showPanel", Boolean.toString(showPanel));
        p.setProperty("retreatDistance", Double.toString(retreatDistance));
        p.setProperty("retreatFireworks", Boolean.toString(retreatFireworks));
        p.setProperty("hotbarMode", Boolean.toString(hotbarMode));
        p.setProperty("rgb", Boolean.toString(rgb));
        p.setProperty("beeMode", Boolean.toString(beeMode));
        p.setProperty("smoothRotation", Boolean.toString(smoothRotation));
        p.setProperty("safeMode", Boolean.toString(safeMode));
        p.setProperty("yawSpeed", Integer.toString(yawSpeed));
        p.setProperty("pitchSpeed", Integer.toString(pitchSpeed));
        p.setProperty("predictMode", Integer.toString(predictMode));
        p.setProperty("predictMultiplier", Double.toString(predictMultiplier));
        p.setProperty("aimPoint", Integer.toString(aimPoint));
        p.setProperty("farDistance", Integer.toString(farDistance));
        p.setProperty("farInterval", Integer.toString(farInterval));
        p.setProperty("anchorEnabled", Boolean.toString(anchorEnabled));
        p.setProperty("anchorSpeed", Integer.toString(anchorSpeed));
        p.setProperty("anchorRange", Integer.toString(anchorRange));
        p.setProperty("anchorDoubleShield", Boolean.toString(anchorDoubleShield));
        p.setProperty("anchorRotate", Boolean.toString(anchorRotate));
        p.setProperty("crystalEnabled", Boolean.toString(crystalEnabled));
        p.setProperty("crystalBreakSpeed", Integer.toString(crystalBreakSpeed));
        p.setProperty("crystalPlaceSpeed", Integer.toString(crystalPlaceSpeed));
        p.setProperty("crystalRange", Integer.toString(crystalRange));
        p.setProperty("crystalMinDamage", Integer.toString(crystalMinDamage));
        p.setProperty("crystalMaxSelfDamage", Integer.toString(crystalMaxSelfDamage));
        p.setProperty("hitCrystal", Boolean.toString(hitCrystal));
        p.setProperty("crystalRotate", Boolean.toString(crystalRotate));
        p.setProperty("totemEnabled", Boolean.toString(totemEnabled));
        p.setProperty("totemDelay", Integer.toString(totemDelay));
        p.setProperty("totemOpenInventory", Boolean.toString(totemOpenInventory));
        p.setProperty("totemOnlyOnPop", Boolean.toString(totemOnlyOnPop));
        for (int i = 0; i < panelX.length; i++) {
            p.setProperty("panelX" + i, Integer.toString(panelX[i]));
            p.setProperty("panelY" + i, Integer.toString(panelY[i]));
        }
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Zenox Client");
        } catch (IOException ignored) {
        }
    }
}
