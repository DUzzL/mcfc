package dev.epicpix.minecraftfunctioncompiler.data.text;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import org.jspecify.annotations.Nullable;

public record TextStyle(
    @Nullable Integer color,
    @Nullable Integer shadowColor,
    @Nullable Boolean bold,
    @Nullable Boolean italic,
    @Nullable Boolean underlined,
    @Nullable Boolean strikethrough,
    @Nullable Boolean obfuscated,
    @Nullable TextClickEvent clickEvent,
    @Nullable TextHoverEvent<?> hoverEvent,
    @Nullable String insertion,
    @Nullable DataLocation font
) {
    private static final TextStyle EMPTY = new TextStyle(null, null, null, null, null, null, null, null, null,null, null);

    public static TextStyle empty() {
        return EMPTY;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append("TextStyle{");
        if(color != null) builder.append("color=").append(color).append(", ");
        if(shadowColor != null) builder.append("shadowColor=").append(color).append(", ");
        if(bold != null) builder.append("bold=").append(bold).append(", ");
        if(italic != null) builder.append("italic=").append(italic).append(", ");
        if(underlined != null) builder.append("underlined=").append(underlined).append(", ");
        if(strikethrough != null) builder.append("strikethrough=").append(strikethrough).append(", ");
        if(obfuscated != null) builder.append("obfuscated=").append(obfuscated).append(", ");
        if(clickEvent != null) builder.append("clickEvent=").append(clickEvent).append(", ");
        if(hoverEvent != null) builder.append("hoverEvent=").append(hoverEvent).append(", ");
        if(font != null) builder.append("font=").append(font).append(", ");
        if(builder.charAt(builder.length() - 1) == ' ') {
            builder.delete(builder.length() - 2, builder.length());
        }
        return builder.append("}").toString();
    }

    public boolean isEmpty() {
        return EMPTY.equals(this);
    }

    public boolean isCompilationSupported() {
        if(hoverEvent != null && hoverEvent.action() == TextHoverEvent.Action.SHOW_TEXT) {
            // Non-constant hover text is compiled at runtime via APPLY_HOVER_TEXT,
            // so we only require the hover text itself to be compilation-supported.
            return ((Text) hoverEvent.value()).isCompilationSupported();
        }
        return true;
    }

    public boolean isConstant() {
        if(hoverEvent != null && hoverEvent.action() == TextHoverEvent.Action.SHOW_TEXT) {
            return ((Text)hoverEvent.value()).isConstant();
        }
        return true;
    }
}
