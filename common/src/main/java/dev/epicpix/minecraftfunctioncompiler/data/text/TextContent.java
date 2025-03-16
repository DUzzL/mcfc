package dev.epicpix.minecraftfunctioncompiler.data.text;

import dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;

import java.util.List;

public interface TextContent {

    record LiteralText(String value) implements TextContent {
        public boolean isCompilationSupported() {
            return true;
        }
    }

    record TranslatableText(String key, String fallback, List<Object> args) implements TextContent {
        public boolean isCompilationSupported() {
            for(var arg : args) {
                if(arg instanceof Text text && !text.isCompilationSupported()) return false;
            }
            return true;
        }
    }

    record KeybindText(String keybind) implements TextContent {
        public boolean isCompilationSupported() {
            return true;
        }
    }

    record ScoreText(ScoreHolderArgument scoreHolder, String objective) implements TextContent {
        public boolean isCompilationSupported() {
            return false;
        }
    }

    record SelectorText(SelectorArgument selector, Text separator) implements TextContent {
        public boolean isCompilationSupported() {
            return false;
        }
    }

    record NbtText(NbtPath path, Text separator, NbtDataLocation source) implements TextContent {
        public boolean isCompilationSupported() {
            return false;
        }
    }

    boolean isCompilationSupported();

}
