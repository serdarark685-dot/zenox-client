package com.elytrahunter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Module list shown in the ClickGUI (Right Shift) and in the side tab. */
final class Modules {

    static final String[] CATEGORIES = {"ELYTRA", "COMBAT", "G\u00d6RSEL"};

    static abstract class Setting {
        final String name;

        Setting(String name) {
            this.name = name;
        }
    }

    static final class Switch extends Setting {
        final BooleanSupplier get;
        final Runnable flip;

        Switch(String name, BooleanSupplier get, Runnable flip) {
            super(name);
            this.get = get;
            this.flip = flip;
        }
    }

    static final class Cycle extends Setting {
        final Supplier<String> text;
        final Runnable next;

        Cycle(String name, Supplier<String> text, Runnable next) {
            super(name);
            this.text = text;
            this.next = next;
        }
    }

    static final class Slider extends Setting {
        final int min;
        final int max;
        final IntSupplier get;
        final IntConsumer set;
        final IntFunction<String> text;

        Slider(String name, int min, int max, IntSupplier get, IntConsumer set, IntFunction<String> text) {
            super(name);
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.text = text;
        }

        double fraction() {
            return (get.getAsInt() - min) / (double) (max - min);
        }
    }

    static final class Module {
        final String name;
        final int category;
        final BooleanSupplier on;
        final Runnable toggle;
        final List<Setting> settings = new ArrayList<>();
        boolean open;

        Module(String name, int category, BooleanSupplier on, Runnable toggle) {
            this.name = name;
            this.category = category;
            this.on = on;
            this.toggle = toggle;
        }

        Module add(Setting s) {
            settings.add(s);
            return this;
        }
    }

    static final List<Module> ALL = new ArrayList<>();
    /** Modules that appear in the side tab (and can be clicked there while the menu is open). */
    static final List<Module> LISTED = new ArrayList<>();

    private static final String[] PREDICT_NAMES = {"KAPALI", "HUNTER", "\u00c7ARPAN"};
    private static final String[] AIM_NAMES = {"G\u00d6VDE", "G\u00d6Z"};

    private Modules() {}

    private static String perSec(int v) {
        return v + "/sn";
    }

    static {
        // ---------------------------------------------------------------- ELYTRA
        Module hunter = new Module("Elytra Hunter", 0,
                () -> HunterConfig.enabled, () -> HunterConfig.enabled = !HunterConfig.enabled);
        hunter.add(new Switch("Ar\u0131 modu",
                () -> HunterConfig.beeMode, () -> HunterConfig.beeMode = !HunterConfig.beeMode));
        hunter.add(new Switch("Yumu\u015fak d\u00f6n\u00fc\u015f",
                () -> HunterConfig.smoothRotation, () -> HunterConfig.smoothRotation = !HunterConfig.smoothRotation));
        hunter.add(new Switch("G\u00fcvenli mod",
                () -> HunterConfig.safeMode, () -> HunterConfig.safeMode = !HunterConfig.safeMode));
        hunter.add(new Switch("Geri \u00e7ekilirken fi\u015fek",
                () -> HunterConfig.retreatFireworks, () -> HunterConfig.retreatFireworks = !HunterConfig.retreatFireworks));
        hunter.add(new Cycle("Fi\u015fek eli",
                () -> HunterConfig.hotbarMode ? "HOTBAR" : "SOL EL",
                () -> HunterConfig.hotbarMode = !HunterConfig.hotbarMode));
        hunter.add(new Cycle("Hedef tahmini",
                () -> PREDICT_NAMES[HunterConfig.predictMode],
                () -> HunterConfig.predictMode = (HunterConfig.predictMode + 1) % PREDICT_NAMES.length));
        hunter.add(new Cycle("Ni\u015fan noktas\u0131",
                () -> AIM_NAMES[HunterConfig.aimPoint],
                () -> HunterConfig.aimPoint = (HunterConfig.aimPoint + 1) % AIM_NAMES.length));
        hunter.add(new Slider("Fi\u015fek aral\u0131\u011f\u0131", HunterConfig.MIN_INTERVAL, HunterConfig.MAX_INTERVAL,
                () -> HunterConfig.fireworkInterval, v -> HunterConfig.fireworkInterval = v, v -> v + " tick"));
        // half-block steps: 1..60 -> 0.5..30.0 blocks
        hunter.add(new Slider("Geri \u00e7ekilme", 1, 60,
                () -> (int) Math.round(HunterConfig.retreatDistance * 2.0),
                v -> HunterConfig.retreatDistance = v / 2.0,
                v -> String.format(Locale.ROOT, "%.1f blok", v / 2.0)));
        hunter.add(new Slider("Yaw h\u0131z\u0131", HunterConfig.MIN_ROT_SPEED, HunterConfig.MAX_ROT_SPEED,
                () -> HunterConfig.yawSpeed, v -> HunterConfig.yawSpeed = v, v -> v + "\u00b0/t"));
        hunter.add(new Slider("Pitch h\u0131z\u0131", HunterConfig.MIN_ROT_SPEED, HunterConfig.MAX_ROT_SPEED,
                () -> HunterConfig.pitchSpeed, v -> HunterConfig.pitchSpeed = v, v -> v + "\u00b0/t"));
        // tenths: 5..30 -> 0.5..3.0
        hunter.add(new Slider("Tahmin \u00e7arpan\u0131", 5, 30,
                () -> (int) Math.round(HunterConfig.predictMultiplier * 10.0),
                v -> HunterConfig.predictMultiplier = v / 10.0,
                v -> String.format(Locale.ROOT, "%.1fx", v / 10.0)));
        hunter.add(new Slider("Uzak mesafe", HunterConfig.MIN_FAR, HunterConfig.MAX_FAR,
                () -> HunterConfig.farDistance, v -> HunterConfig.farDistance = v, v -> v + " blok"));
        hunter.add(new Slider("Uzak aral\u0131k", HunterConfig.MIN_INTERVAL, HunterConfig.MAX_INTERVAL,
                () -> HunterConfig.farInterval, v -> HunterConfig.farInterval = v, v -> v + " tick"));

        // ---------------------------------------------------------------- COMBAT
        Module anchor = new Module("Anchor Aura", 1,
                () -> HunterConfig.anchorEnabled, () -> HunterConfig.anchorEnabled = !HunterConfig.anchorEnabled);
        anchor.add(new Slider("H\u0131z", 1, 20,
                () -> HunterConfig.anchorSpeed, v -> HunterConfig.anchorSpeed = v, Modules::perSec));
        anchor.add(new Slider("Hedef menzili", 2, 8,
                () -> HunterConfig.anchorRange, v -> HunterConfig.anchorRange = v, v -> v + " blok"));
        anchor.add(new Switch("\u00c7ift kat kalkan",
                () -> HunterConfig.anchorDoubleShield, () -> HunterConfig.anchorDoubleShield = !HunterConfig.anchorDoubleShield));
        anchor.add(new Switch("D\u00f6nd\u00fcr",
                () -> HunterConfig.anchorRotate, () -> HunterConfig.anchorRotate = !HunterConfig.anchorRotate));

        Module crystal = new Module("Crystal Aura", 1,
                () -> HunterConfig.crystalEnabled, () -> HunterConfig.crystalEnabled = !HunterConfig.crystalEnabled);
        crystal.add(new Slider("Patlatma h\u0131z\u0131", 1, 20,
                () -> HunterConfig.crystalBreakSpeed, v -> HunterConfig.crystalBreakSpeed = v, Modules::perSec));
        crystal.add(new Slider("Koyma h\u0131z\u0131", 1, 20,
                () -> HunterConfig.crystalPlaceSpeed, v -> HunterConfig.crystalPlaceSpeed = v, Modules::perSec));
        crystal.add(new Slider("Koyma menzili", 2, 5,
                () -> HunterConfig.crystalRange, v -> HunterConfig.crystalRange = v, v -> v + " blok"));
        crystal.add(new Slider("Min hedef hasar\u0131", 1, 30,
                () -> HunterConfig.crystalMinDamage, v -> HunterConfig.crystalMinDamage = v, v -> String.format(Locale.ROOT, "%.1f kalp", v / 2.0)));
        crystal.add(new Slider("Maks kendi hasar\u0131", 0, 20,
                () -> HunterConfig.crystalMaxSelfDamage, v -> HunterConfig.crystalMaxSelfDamage = v, v -> String.format(Locale.ROOT, "%.1f kalp", v / 2.0)));
        crystal.add(new Switch("Hit crystal",
                () -> HunterConfig.hitCrystal, () -> HunterConfig.hitCrystal = !HunterConfig.hitCrystal));
        crystal.add(new Switch("D\u00f6nd\u00fcr",
                () -> HunterConfig.crystalRotate, () -> HunterConfig.crystalRotate = !HunterConfig.crystalRotate));

        Module totem = new Module("Auto Totem", 1,
                () -> HunterConfig.totemEnabled, () -> HunterConfig.totemEnabled = !HunterConfig.totemEnabled);
        totem.add(new Slider("Gecikme", 0, 10,
                () -> HunterConfig.totemDelay, v -> HunterConfig.totemDelay = v, v -> v == 0 ? "an\u0131nda" : v + " tick"));
        totem.add(new Switch("Envanter a\u00e7",
                () -> HunterConfig.totemOpenInventory, () -> HunterConfig.totemOpenInventory = !HunterConfig.totemOpenInventory));
        totem.add(new Switch("Sadece patlay\u0131nca",
                () -> HunterConfig.totemOnlyOnPop, () -> HunterConfig.totemOnlyOnPop = !HunterConfig.totemOnlyOnPop));

        // ---------------------------------------------------------------- G\u00d6RSEL
        Module marker = new Module("Hedef i\u015fareti", 2,
                () -> HunterConfig.showMarker, () -> HunterConfig.showMarker = !HunterConfig.showMarker);
        Module side = new Module("Yan sekme", 2,
                () -> HunterConfig.showPanel, () -> HunterConfig.showPanel = !HunterConfig.showPanel);
        Module rgb = new Module("RGB", 2,
                () -> HunterConfig.rgb, () -> HunterConfig.rgb = !HunterConfig.rgb);

        ALL.add(hunter);
        ALL.add(anchor);
        ALL.add(crystal);
        ALL.add(totem);
        ALL.add(marker);
        ALL.add(side);
        ALL.add(rgb);

        LISTED.add(hunter);
        LISTED.add(anchor);
        LISTED.add(crystal);
        LISTED.add(totem);
    }
}
