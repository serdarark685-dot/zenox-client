package com.elytrahunter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


/** Neon HUD: always-visible side tab + Iron-Man style rotating square on the locked target. */
final class HunterHud {

    // colours are refreshed once per frame by updateColors() (blue neon or slow RGB cycle)
    static int NEON = 0xFF00C8FF;
    static int NEON_SOFT = 0xFF9BE8FF;
    static int CORE = 0xFFE9FBFF;
    static int GLOW = 0x4000C8FF;
    static int GLOW_FAINT = 0x2000C8FF;
    static int DIM = 0xFF5AA9CC;
    static final int BG = 0xB004101E;
    static final int BAD = 0xFFFF5577;

    private HunterHud() {}

    /** Slow rainbow (one full cycle = 18 s) or the fixed neon blue. */
    static void updateColors() {
        if (HunterConfig.rgb) {
            float h = (System.currentTimeMillis() % 18000L) / 18000f;
            int c = hsv(h, 0.85f, 1.0f);
            NEON = 0xFF000000 | c;
            NEON_SOFT = 0xFF000000 | hsv(h, 0.40f, 1.0f);
            CORE = 0xFF000000 | hsv(h, 0.08f, 1.0f);
            GLOW = 0x40000000 | c;
            GLOW_FAINT = 0x20000000 | c;
            DIM = 0xFF000000 | hsv(h, 0.45f, 0.72f);
        } else {
            NEON = 0xFF00C8FF;
            NEON_SOFT = 0xFF9BE8FF;
            CORE = 0xFFE9FBFF;
            GLOW = 0x4000C8FF;
            GLOW_FAINT = 0x2000C8FF;
            DIM = 0xFF5AA9CC;
        }
    }

    private static int hsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6.0f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) (r * 255.0f + 0.5f) << 16) | ((int) (g * 255.0f + 0.5f) << 8) | (int) (b * 255.0f + 0.5f);
    }

    static void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        updateColors();
        ElytraHunterClient e = ElytraHunterClient.instance();
        if (e == null) return;
        if (HunterConfig.showPanel) drawSide(ctx, mc, -1, -1);
        if (HunterConfig.showMarker) drawMarker(ctx, mc, e);
    }

    // ------------------------------------------------------------------ side tab
    private static final int LINE_H = 12;
    // geometry of the last drawn side tab (used by the ClickGUI for hit-testing)
    private static int sideW;
    private static int sideRow0Y;

    /** Index into Modules.LISTED of the side-tab row under the mouse, or -1. */
    static int sideRowAt(int mx, int my) {
        if (mx < 0 || mx >= sideW) return -1;
        int n = Modules.LISTED.size();
        if (my < sideRow0Y || my >= sideRow0Y + n * LINE_H) return -1;
        return (my - sideRow0Y) / LINE_H;
    }

    /**
     * Always-visible tab on the left edge: client name, module list (on = bright, off = dim),
     * current target and the key hints. mx / my >= 0 turns on hover highlight (ClickGUI open).
     */
    static void drawSide(DrawContext ctx, MinecraftClient mc, int mx, int my) {
        TextRenderer tr = mc.textRenderer;
        ElytraHunterClient e = ElytraHunterClient.instance();
        java.util.List<Modules.Module> mods = Modules.LISTED;

        Text title = Fx.b("ZENOX CLIENT");
        AbstractClientPlayerEntity t = e == null ? null : e.getTarget();
        boolean hunting = HunterConfig.enabled && e != null && e.isActive() && t != null;
        Text target = Fx.t(hunting
                ? "Hedef: " + t.getName().getString() + "  " + (int) mc.player.distanceTo(t) + "m"
                : "Hedef: yok");
        Text hint = Fx.t("[R-Shift] men\u00fc  [H] a\u00e7/kapa");

        int textW = Math.max(tr.getWidth(title), Math.max(tr.getWidth(target), tr.getWidth(hint)));
        for (Modules.Module m : mods) {
            textW = Math.max(textW, tr.getWidth(Fx.t(m.name)) + 14);
        }
        int w = textW + 14;
        int n = mods.size();
        int h = 6 + 12 + 5 + n * LINE_H + 5 + LINE_H + LINE_H + 4;
        int x = 0;
        int y = mc.getWindow().getScaledHeight() / 3;

        // glow, body, border (flush with the left screen edge, like a tab)
        ctx.fill(x, y - 2, x + w + 2, y + h + 2, GLOW_FAINT);
        ctx.fill(x, y - 1, x + w + 1, y + h + 1, GLOW);
        ctx.fill(x, y, x + w, y + h, BG);
        ctx.fill(x, y, x + w, y + 1, NEON);
        ctx.fill(x, y + h - 1, x + w, y + h, NEON);
        ctx.fill(x + w - 1, y, x + w, y + h, NEON);

        int tx = x + 7;
        int ty = y + 6;
        ctx.drawText(tr, title, tx, ty, NEON, true);
        ty += 12;
        ctx.fill(x, ty, x + w - 1, ty + 1, GLOW);
        ty += 5;

        sideW = w;
        sideRow0Y = ty;
        for (int i = 0; i < n; i++) {
            Modules.Module m = mods.get(i);
            boolean on = m.on.getAsBoolean();
            int ry = ty + i * LINE_H;
            if (mx >= 0 && mx < w && my >= ry && my < ry + LINE_H) {
                ctx.fill(x + 2, ry, x + w - 3, ry + LINE_H, 0x26FFFFFF);
            }
            // state square: filled = on, hollow = off
            int sx = x + w - 11;
            int sy = ry + 3;
            if (on) {
                ctx.fill(sx, sy, sx + 5, sy + 5, NEON);
            } else {
                ctx.fill(sx, sy, sx + 5, sy + 1, DIM);
                ctx.fill(sx, sy + 4, sx + 5, sy + 5, DIM);
                ctx.fill(sx, sy, sx + 1, sy + 5, DIM);
                ctx.fill(sx + 4, sy, sx + 5, sy + 5, DIM);
            }
            ctx.drawText(tr, Fx.t(m.name), tx, ry + 2, on ? NEON_SOFT : DIM, true);
        }
        ty += n * LINE_H + 5;
        ctx.fill(x, ty - 3, x + w - 1, ty - 2, GLOW);
        ctx.drawText(tr, target, tx, ty, hunting ? NEON_SOFT : DIM, true);
        ty += LINE_H;
        ctx.drawText(tr, hint, tx, ty, DIM, true);
    }

    // ------------------------------------------------------------------ target marker
    private static void drawMarker(DrawContext ctx, MinecraftClient mc, ElytraHunterClient e) {
        AbstractClientPlayerEntity t = e.getTarget();
        if (!HunterConfig.enabled || !e.isActive() || t == null) return;
        if (e.curTarget == null || e.prevTarget == null || e.curEye == null || e.prevEye == null) return;

        // interpolate between the last two ticks (same idea as vanilla partial ticks)
        double a = MathHelper.clamp((System.nanoTime() - e.tickEndNanos) / 50_000_000.0, 0.0, 1.0);
        Vec3d eye = lerp(e.prevEye, e.curEye, a);
        Vec3d tp = lerp(e.prevTarget, e.curTarget, a);
        float yaw = e.prevYaw + MathHelper.wrapDegrees(e.curYaw - e.prevYaw) * (float) a;
        float pitch = e.prevPitch + (e.curPitch - e.prevPitch) * (float) a;

        // camera basis (Minecraft: yaw 0 = +Z, pitch > 0 = looking down)
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr);
        double fy = -Math.sin(pr);
        double fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr);
        double rz = -Math.sin(yr);
        // up = right x forward (right.y == 0)
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        double dx = tp.x - eye.x;
        double dy = tp.y - eye.y;
        double dz = tp.z - eye.z;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc < 0.5) return; // behind the camera / too close to project
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double fov = mc.options.getFov().getValue();
        double f = 1.0 / Math.tan(Math.toRadians(fov) / 2.0);
        double aspect = (double) sw / (double) sh;

        double sx = sw / 2.0 + (xc / zc) * f / aspect * (sw / 2.0);
        double sy = sh / 2.0 - (yc / zc) * f * (sh / 2.0);

        // square size follows the target's on-screen height
        double heightPx = (t.getHeight() / zc) * f * (sh / 2.0);
        double r = MathHelper.clamp(heightPx * 0.62, 11.0, 55.0);

        double ang = (System.currentTimeMillis() % 2400L) / 2400.0 * Math.PI * 2.0;
        double[] cx = new double[4];
        double[] cy = new double[4];
        for (int k = 0; k < 4; k++) {
            double ak = ang + Math.PI / 4.0 + k * Math.PI / 2.0;
            cx[k] = sx + Math.cos(ak) * r;
            cy[k] = sy + Math.sin(ak) * r;
        }
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) % 4;
            line(ctx, cx[k], cy[k], cx[n], cy[n], GLOW, 1);   // glow
        }
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) % 4;
            line(ctx, cx[k], cy[k], cx[n], cy[n], NEON, 0);   // core
        }

        // center dot
        int px = (int) Math.round(sx);
        int py = (int) Math.round(sy);
        ctx.fill(px - 3, py - 3, px + 4, py + 4, GLOW);
        ctx.fill(px - 1, py - 1, px + 2, py + 2, CORE);

        // small label under the square
        String label = t.getName().getString() + "  " + (int) mc.player.distanceTo(t) + "m";
        int lw = mc.textRenderer.getWidth(Fx.t(label));
        ctx.drawText(mc.textRenderer, Fx.t(label), px - lw / 2, (int) (sy + r + 5), NEON_SOFT, true);
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    /** Pixel line built from small fills; half = 0 -> 1px, half = 1 -> 3px wide. */
    static void line(DrawContext ctx, double x1, double y1, double x2, double y2, int color, int half) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        int n = (int) Math.max(1.0, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= n; i++) {
            int x = (int) Math.round(x1 + dx * i / n);
            int y = (int) Math.round(y1 + dy * i / n);
            ctx.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    /** Hollow neon rectangle (glow + core). */
    static void frame(DrawContext ctx, int x1, int y1, int x2, int y2) {
        ctx.fill(x1 - 2, y1 - 2, x2 + 2, y1 + 1, GLOW_FAINT);
        ctx.fill(x1 - 2, y2 - 1, x2 + 2, y2 + 2, GLOW_FAINT);
        ctx.fill(x1 - 2, y1 - 2, x1 + 1, y2 + 2, GLOW_FAINT);
        ctx.fill(x2 - 1, y1 - 2, x2 + 2, y2 + 2, GLOW_FAINT);
        ctx.fill(x1 - 1, y1 - 1, x2 + 1, y1 + 1, GLOW);
        ctx.fill(x1 - 1, y2 - 1, x2 + 1, y2 + 1, GLOW);
        ctx.fill(x1 - 1, y1 - 1, x1 + 1, y2 + 1, GLOW);
        ctx.fill(x2 - 1, y1 - 1, x2 + 1, y2 + 1, GLOW);
        ctx.fill(x1, y1, x2, y1 + 1, NEON);
        ctx.fill(x1, y2 - 1, x2, y2, NEON);
        ctx.fill(x1, y1, x1 + 1, y2, NEON);
        ctx.fill(x2 - 1, y1, x2, y2, NEON);
    }
}
