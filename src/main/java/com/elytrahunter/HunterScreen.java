package com.elytrahunter;

import com.elytrahunter.Modules.Cycle;
import com.elytrahunter.Modules.Module;
import com.elytrahunter.Modules.Setting;
import com.elytrahunter.Modules.Slider;
import com.elytrahunter.Modules.Switch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI (Right Shift).
 *  - one small panel per category (ELYTRA / COMBAT / G\u00d6RSEL); drag a panel by its header to move it left / right / anywhere
 *  - left click a module = on / off, right click = open / close its settings
 *  - settings: click switches and cycles, drag sliders, mouse wheel scrolls a tall panel
 *  - the side tab on the left edge is clickable too (left click toggles that module)
 *
 * Everything is drawn in render() and mouse buttons are polled with GLFW (same trick as the key polling),
 * so no vanilla widgets are needed. Saved when the screen closes.
 */
public class HunterScreen extends Screen {

    private static final int PW = 104;          // panel width
    private static final int HEAD_H = 15;
    private static final int MOD_H = 13;
    private static final int SET_H = 11;
    private static final int GAP = 8;

    private static final int BG = 0xF2101728;
    private static final int OUTLINE = 0xFF1C2744;
    private static final int ACCENT = 0xFF2F6DF2;
    private static final int TEXT_ON = 0xFFFFFFFF;
    private static final int TEXT_OFF = 0xFFCDD3E4;
    private static final int TEXT_VALUE = 0xFF8E98B3;
    private static final int TEXT_VALUE_ON = 0xFFDCE6FF;

    private final int count = Modules.CATEGORIES.length;
    private final int[] px = new int[count];
    private final int[] py = new int[count];
    private final int[] scroll = new int[count];
    private final List<Integer> order = new ArrayList<>();

    private boolean lastL;
    private boolean lastR;
    private boolean lastShift = true;   // the opening key press is still down
    private boolean closeRequested;
    private int dragPanel = -1;
    private int dragDX;
    private int dragDY;
    private Slider activeSlider;
    private int sliderX;
    private int sliderW;

    public HunterScreen() {
        super(Text.literal("Zenox Client"));
    }

    // ------------------------------------------------------------------ layout
    @Override
    protected void init() {
        order.clear();
        int total = count * PW + (count - 1) * GAP;
        int startX = Math.max(128, this.width / 2 - total / 2);   // stay clear of the side tab
        if (startX + total > this.width - 4) {
            startX = Math.max(4, this.width - total - 4);
        }
        for (int i = 0; i < count; i++) {
            order.add(i);
            px[i] = HunterConfig.panelX[i] >= 0 ? HunterConfig.panelX[i] : startX + i * (PW + GAP);
            py[i] = HunterConfig.panelY[i] >= 0 ? HunterConfig.panelY[i] : 14;
            px[i] = Math.max(0, Math.min(px[i], this.width - PW));
            py[i] = Math.max(0, Math.min(py[i], this.height - HEAD_H));
        }
    }

    private int contentHeight(int cat) {
        int h = 0;
        for (Module m : Modules.ALL) {
            if (m.category != cat) continue;
            h += MOD_H;
            if (m.open) h += m.settings.size() * SET_H;
        }
        return h;
    }

    private int panelHeight(int cat, int y) {
        int full = HEAD_H + contentHeight(cat) + 3;
        int maxH = Math.max(HEAD_H + 50, this.height - y - 4);
        return Math.min(full, maxH);
    }

    // ------------------------------------------------------------------ frame
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta); // dimmed background only
        MinecraftClient mc = MinecraftClient.getInstance();
        HunterHud.updateColors();
        int accent = HunterConfig.rgb ? HunterHud.NEON : ACCENT;

        long win = mc.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean lEdge = left && !lastL;
        boolean rEdge = right && !lastR;
        if (shift && !lastShift) closeRequested = true;
        lastL = left;
        lastR = right;
        lastShift = shift;

        // side tab (clickable)
        int sideRow = -1;
        if (HunterConfig.showPanel) {
            HunterHud.drawSide(ctx, mc, mx, my);
            sideRow = HunterHud.sideRowAt(mx, my);
            if (lEdge && sideRow >= 0) {
                Modules.LISTED.get(sideRow).toggle.run();
            }
        }

        // topmost panel under the mouse
        int hot = -1;
        for (int k = order.size() - 1; k >= 0; k--) {
            int i = order.get(k);
            int h = panelHeight(i, py[i]);
            if (mx >= px[i] && mx < px[i] + PW && my >= py[i] && my < py[i] + h) {
                hot = i;
                break;
            }
        }

        // drag a panel by its header
        if (lEdge && hot >= 0 && sideRow < 0 && my < py[hot] + HEAD_H) {
            dragPanel = hot;
            dragDX = mx - px[hot];
            dragDY = my - py[hot];
            order.remove(Integer.valueOf(hot));
            order.add(hot);
        }
        if (dragPanel >= 0) {
            if (left) {
                px[dragPanel] = Math.max(0, Math.min(mx - dragDX, this.width - PW));
                py[dragPanel] = Math.max(0, Math.min(my - dragDY, this.height - HEAD_H));
            } else {
                HunterConfig.panelX[dragPanel] = px[dragPanel];
                HunterConfig.panelY[dragPanel] = py[dragPanel];
                dragPanel = -1;
            }
        }

        // slider being dragged (keeps following the mouse outside its row)
        if (activeSlider != null) {
            if (left) {
                applySlider(mx);
            } else {
                activeSlider = null;
            }
        }

        boolean allowClicks = dragPanel < 0;
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            drawPanel(ctx, i, mx, my, allowClicks && hot == i && sideRow < 0, lEdge, rEdge, accent);
        }
    }

    @Override
    public void tick() {
        if (closeRequested) {
            closeRequested = false;
            this.close();
        }
    }

    private void applySlider(int mx) {
        double f = Math.max(0.0, Math.min(1.0, (mx - sliderX) / (double) sliderW));
        int v = activeSlider.min + (int) Math.round(f * (activeSlider.max - activeSlider.min));
        if (v != activeSlider.get.getAsInt()) {
            activeSlider.set.accept(v);
        }
    }

    // ------------------------------------------------------------------ drawing
    private void drawPanel(DrawContext ctx, int cat, int mx, int my, boolean active,
                           boolean lEdge, boolean rEdge, int accent) {
        int x = px[cat];
        int y = py[cat];
        int content = contentHeight(cat);
        int h = panelHeight(cat, y);
        int viewH = h - HEAD_H - 3;
        scroll[cat] = Math.max(0, Math.min(scroll[cat], Math.max(0, content - viewH)));

        roundRect(ctx, x - 1, y - 1, x + PW + 1, y + h + 1, 6, OUTLINE);
        roundRect(ctx, x, y, x + PW, y + h, 5, BG);
        ctx.drawText(this.textRenderer, Fx.b(Modules.CATEGORIES[cat]), x + 7, y + 4, TEXT_ON, false);
        ctx.fill(x + 4, y + HEAD_H - 1, x + PW - 4, y + HEAD_H, (accent & 0x00FFFFFF) | 0x80000000);

        int bodyTop = y + HEAD_H;
        int bodyBot = y + h - 2;
        boolean inBody = active && mx >= x && mx < x + PW && my >= bodyTop && my < bodyBot;

        ctx.enableScissor(x, bodyTop, x + PW, bodyBot);
        int yy = bodyTop + 2 - scroll[cat];
        for (Module m : Modules.ALL) {
            if (m.category != cat) continue;
            boolean on = m.on.getAsBoolean();
            boolean hover = inBody && my >= yy && my < yy + MOD_H;
            int rx = x + 3;
            int rw = PW - 6;
            int rh = MOD_H - 1;
            if (on) {
                roundRect(ctx, rx, yy, rx + rw, yy + rh, 3, hover ? lighten(accent) : accent);
            } else if (hover) {
                roundRect(ctx, rx, yy, rx + rw, yy + rh, 3, 0x22FFFFFF);
            }
            ctx.drawText(this.textRenderer, Fx.t(m.name), rx + 5, yy + (rh - 8) / 2, on ? TEXT_ON : TEXT_OFF, false);
            if (!m.settings.isEmpty()) {
                drawChevron(ctx, rx + rw - 8, yy + rh / 2, m.open, on ? 0xFFFFFFFF : 0xFF8E98B3);
            }
            if (hover) {
                if (lEdge) {
                    m.toggle.run();
                } else if (rEdge && !m.settings.isEmpty()) {
                    m.open = !m.open;
                }
            }
            yy += MOD_H;

            if (!m.open) continue;
            for (Setting s : m.settings) {
                boolean sh = inBody && my >= yy && my < yy + SET_H;
                drawSetting(ctx, s, x + 8, yy, PW - 14, SET_H - 1, sh, lEdge, accent, mx);
                yy += SET_H;
            }
        }
        ctx.disableScissor();
    }

    private void drawSetting(DrawContext ctx, Setting s, int rx, int ry, int rw, int rh,
                             boolean hover, boolean lEdge, int accent, int mx) {
        int ty = ry + (rh - 8) / 2;
        if (s instanceof Switch) {
            Switch sw = (Switch) s;
            boolean on = sw.get.getAsBoolean();
            if (hover) roundRect(ctx, rx, ry, rx + rw, ry + rh, 3, 0x22FFFFFF);
            ctx.drawText(this.textRenderer, Fx.t(s.name), rx + 4, ty, on ? TEXT_ON : TEXT_OFF, false);
            int bx = rx + rw - 10;
            int by = ry + (rh - 5) / 2;
            if (on) {
                ctx.fill(bx, by, bx + 5, by + 5, accent);
            } else {
                ctx.fill(bx, by, bx + 5, by + 1, TEXT_VALUE);
                ctx.fill(bx, by + 4, bx + 5, by + 5, TEXT_VALUE);
                ctx.fill(bx, by, bx + 1, by + 5, TEXT_VALUE);
                ctx.fill(bx + 4, by, bx + 5, by + 5, TEXT_VALUE);
            }
            if (hover && lEdge) sw.flip.run();
        } else if (s instanceof Cycle) {
            Cycle cy = (Cycle) s;
            if (hover) roundRect(ctx, rx, ry, rx + rw, ry + rh, 3, 0x22FFFFFF);
            ctx.drawText(this.textRenderer, Fx.t(s.name), rx + 4, ty, TEXT_OFF, false);
            Text v = Fx.t(cy.text.get());
            int vw = this.textRenderer.getWidth(v);
            ctx.drawText(this.textRenderer, v, rx + rw - 4 - vw, ty, TEXT_VALUE_ON, false);
            if (hover && lEdge) cy.next.run();
        } else if (s instanceof Slider) {
            Slider sl = (Slider) s;
            roundRect(ctx, rx, ry, rx + rw, ry + rh, 3, hover || activeSlider == sl ? 0x26FFFFFF : 0x16FFFFFF);
            int fw = (int) Math.round(sl.fraction() * rw);
            if (fw > 0) {
                roundRect(ctx, rx, ry, rx + Math.max(fw, 6), ry + rh, 3, (accent & 0x00FFFFFF) | 0xA0000000);
            }
            ctx.drawText(this.textRenderer, Fx.t(s.name), rx + 4, ty, TEXT_ON, false);
            Text v = Fx.t(sl.text.apply(sl.get.getAsInt()));
            int vw = this.textRenderer.getWidth(v);
            ctx.drawText(this.textRenderer, v, rx + rw - 4 - vw, ty, TEXT_VALUE_ON, false);
            if (hover && lEdge) {
                activeSlider = sl;
                sliderX = rx;
                sliderW = rw;
                applySlider(mx);
            }
        }
    }

    /** Small chevron: down when open, right when closed. */
    private static void drawChevron(DrawContext ctx, int cx, int cy, boolean open, int color) {
        for (int i = 0; i < 4; i++) {
            if (open) {
                ctx.fill(cx - 3 + i, cy - 1 + i, cx - 2 + i, cy + i, color);
                ctx.fill(cx + 3 - i, cy - 1 + i, cx + 4 - i, cy + i, color);
            } else {
                ctx.fill(cx - 1 + i, cy - 3 + i, cx + i, cy - 2 + i, color);
                ctx.fill(cx - 1 + i, cy + 3 - i, cx + i, cy + 4 - i, color);
            }
        }
    }

    /** Filled rectangle with rounded corners. */
    private static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        r += (255 - r) / 6;
        g += (255 - g) / 6;
        b += (255 - b) / 6;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ input / screen basics
    /** Mouse wheel scrolls the panel under the cursor (no @Override: the call is simply ignored if the signature differs). */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int k = order.size() - 1; k >= 0; k--) {
            int i = order.get(k);
            int h = panelHeight(i, py[i]);
            if (mouseX >= px[i] && mouseX < px[i] + PW && mouseY >= py[i] && mouseY < py[i] + h) {
                scroll[i] -= (int) Math.round(verticalAmount * 12.0);
                return true;
            }
        }
        return false;
    }

    @Override
    public void removed() {
        for (int i = 0; i < count; i++) {
            HunterConfig.panelX[i] = px[i];
            HunterConfig.panelY[i] = py[i];
        }
        HunterConfig.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
