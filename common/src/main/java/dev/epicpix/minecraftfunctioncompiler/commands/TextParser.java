package dev.epicpix.minecraftfunctioncompiler.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.*;

public final class TextParser {
    private TextParser() {}

    public static Text readText(StringCommandReader reader) {
        var json = reader.readJson();
        return jsonToText(json, reader);
    }

    private static DiagnosticException createError(StringCommandReader reader, String diagnosticId, String message) {
        return new DiagnosticException(Diagnostic.parseError(diagnosticId, reader.getSource(), reader.getString(), message));
    }

    private static Integer stringToColor(String str) {
        if(str.length() == 7 && str.charAt(0) == '#') {
            try {
                return Integer.parseInt(str.substring(1), 16);
            }catch(NumberFormatException ignored) {
                return null;
            }
        }
        return switch(str) {
            case "black" -> 0x000000;
            case "dark_blue" -> 0x0000AA;
            case "dark_green" -> 0x00AA00;
            case "dark_aqua" -> 0x00AAAA;
            case "dark_red" -> 0xAA0000;
            case "dark_purple" -> 0xAA00AA;
            case "gold" -> 0xFFAA00;
            case "gray" -> 0xAAAAAA;
            case "dark_gray" -> 0x555555;
            case "blue" -> 0x5555FF;
            case "green" -> 0x55FF55;
            case "aqua" -> 0x55FFFF;
            case "red" -> 0xFF5555;
            case "light_purple" -> 0xFF55FF;
            case "yellow" -> 0xFFFF55;
            case "white" -> 0xFFFFFF;
            default -> null;
        };
    }

    private static Integer contentToColor(JsonElement element, StringCommandReader reader) {
        if(element instanceof JsonArray array && array.size() == 4) {
            int r = array.get(0).getAsInt();
            int g = array.get(1).getAsInt();
            int b = array.get(2).getAsInt();
            int a = array.get(3).getAsInt();
            return a << 24 | r << 16 | g << 8 | b;
        }else if(element instanceof JsonPrimitive prim && prim.isNumber()) {
            return prim.getAsInt();
        }
        throw createError(reader, TEXT_INVALID_COLOR, "Invalid color specification: " + element + " (expected [R,G,B,A] or integer ARGB)");
    }

    private static Text jsonToText(JsonElement element, StringCommandReader reader) {
        if(element instanceof JsonPrimitive prim && prim.isString()) {
            return new Text(List.of(), TextStyle.empty(), new TextContent.LiteralText(prim.getAsString()));
        }else if(element instanceof JsonObject obj) {
            TextContent content;
            if(obj.has("text")) {
                var rawText = obj.get("text").getAsString();
                content = new TextContent.LiteralText(rawText);
            }else if(obj.has("keybind")) {
                var keybind = obj.get("keybind").getAsString();
                content = new TextContent.KeybindText(keybind);
            }else if(obj.has("translate")) {
                var key = obj.get("translate").getAsString();
                var fallback = obj.has("fallback") ? obj.get("fallback").getAsString() : null;
                List<Object> args = List.of();
                if(obj.has("with")) {
                    args = new ArrayList<>();
                    for(var extra : obj.getAsJsonArray("with")) {
                        if(extra instanceof JsonPrimitive prim && prim.isString()) {
                            args.add(prim.getAsString());
                        }else {
                            args.add(jsonToText(extra, reader));
                        }
                    }
                }
                content = new TextContent.TranslatableText(key, fallback, args);
            }else {
                throw createError(reader, TEXT_INVALID, "Cannot turn \"" + element + "\" into Text");
            }
            List<Text> siblings = List.of();
            if(obj.has("extra")) {
                siblings = new ArrayList<>();
                for(var extra : obj.getAsJsonArray("extra")) {
                    siblings.add(jsonToText(extra, reader));
                }
            }

            Integer color = obj.has("color") ? stringToColor(obj.get("color").getAsString()) : null;
            Integer shadowColor = MinecraftVersion.isCurrentVersionInRange(MinecraftVersion.MC_1_21_4, null) ? (obj.has("shadow_color") ? contentToColor(obj.get("shadow_color"), reader) : null) : null;
            Boolean bold = obj.has("bold") ? obj.get("bold").getAsBoolean() : null;
            Boolean italic = obj.has("italic") ? obj.get("italic").getAsBoolean() : null;
            Boolean underlined = obj.has("underlined") ? obj.get("underlined").getAsBoolean() : null;
            Boolean strikethrough = obj.has("strikethrough") ? obj.get("strikethrough").getAsBoolean() : null;
            Boolean obfuscated = obj.has("obfuscated") ? obj.get("obfuscated").getAsBoolean() : null;
            String insertion = obj.has("insertion") ? obj.get("insertion").getAsString() : null;
            DataLocation font = obj.has("font") ? new DataLocation(obj.get("font").getAsString()) : null;

            if(obj.has("clickEvent")) throw createError(reader, TEXT_INVALID_UNSUPPORTED_CLICK_EVENT, "\"clickEvent\" in text is not yet supported");
            if(obj.has("hoverEvent")) throw createError(reader, TEXT_INVALID_UNSUPPORTED_HOVER_EVENT, "\"hoverEvent\" in text is not yet supported");

            return new Text(siblings, new TextStyle(color, shadowColor, bold, italic, underlined, strikethrough, obfuscated,null, null, insertion, font), content);
        }else if(element instanceof JsonArray arr) {
            Text text = jsonToText(arr.get(0), reader);
            var siblings = new ArrayList<>(text.siblings());
            for(int i = 1; i<arr.size(); i++) {
                siblings.add(jsonToText(arr.get(i), reader));
            }
            return new Text(siblings, text.style(), text.content());
        }else {
            throw createError(reader, TEXT_INVALID, "Cannot turn \"" + element + "\" into Text");
        }
    }

}
