package com.elytrahunter;

import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** UI fonts: Outfit Regular / Bold bundled in assets/elytrahunter/font (ui.json, ui_bold.json). */
final class Fx {
    private static final Style FONT = Style.EMPTY
            .withFont(new StyleSpriteSource.Font(Identifier.of("elytrahunter", "ui")));
    private static final Style FONT_BOLD = Style.EMPTY
            .withFont(new StyleSpriteSource.Font(Identifier.of("elytrahunter", "ui_bold")));

    private Fx() {}

    static Text t(String s) {
        return Text.literal(s).setStyle(FONT);
    }

    static Text b(String s) {
        return Text.literal(s).setStyle(FONT_BOLD);
    }
}
