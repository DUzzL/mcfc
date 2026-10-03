package dev.epicpix.minecraftfunctioncompiler.data.text;

import dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;
import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface TextContent permits TextContent.KeybindText, TextContent.LiteralText, TextContent.NbtText, TextContent.ScoreText, TextContent.SelectorText, TextContent.TranslatableText {

    record LiteralText(String value) implements TextContent {
        public boolean isCompilationSupported() {
            return true;
        }
        public boolean isConstant() {
            return true;
        }
    }

    record TranslatableText(String key, @Nullable String fallback, List<Object> args) implements TextContent {
        public boolean isCompilationSupported() {
            for(var arg : args) {
                if(arg instanceof Text text && !text.isCompilationSupported()) return false;
            }
            return true;
        }
        public boolean isConstant() {
            for(var arg : args) {
                if(arg instanceof Text text && !text.isConstant()) return false;
            }
            return true;
        }
    }

    record KeybindText(String keybind) implements TextContent {
        public boolean isCompilationSupported() {
            return true;
        }
        public boolean isConstant() {
            return true;
        }
    }

    /** {@code holderSource}: the raw "name" text; vanilla uses it as a name-only holder when the selector matches nothing. */
    record ScoreText(ScoreHolderArgument scoreHolder, String objective, String holderSource) implements TextContent {
        public boolean isCompilationSupported() {
            return true;
        }
        public boolean isConstant() {
            return false;
        }
    }

    record SelectorText(SelectorArgument selector, @Nullable Text separator) implements TextContent {
        public static final Text DEFAULT_SEPARATOR = new Text(List.of(), new TextStyle(0xaaaaaa, null, null, null, null, null, null, null, null, null, null), new LiteralText(", "));

        public boolean isCompilationSupported() {
            return separator == null || separator.isCompilationSupported();
        }
        public boolean isConstant() {
            return false;
        }
    }

    /** {@code rawPath} is the source text of {@code path}; the runtime evaluates it with vanilla's NbtPathArgument. */
    record NbtText(NbtPath path, String rawPath, boolean interpret, boolean plain, @Nullable Text separator, NbtDataLocation source) implements TextContent {
        public boolean isCompilationSupported() {
            return (source instanceof NbtDataLocation.Storage || source instanceof NbtDataLocation.Entity)
                    && !rawPath.contains("$(")
                    && (separator == null || separator.isCompilationSupported());
        }
        public boolean isConstant() {
            return false;
        }
    }

    boolean isCompilationSupported();
    boolean isConstant();

}
