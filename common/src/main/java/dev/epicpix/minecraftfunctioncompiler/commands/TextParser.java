package dev.epicpix.minecraftfunctioncompiler.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;

import java.util.ArrayList;
import java.util.List;

public final class TextParser {
    private TextParser() {}

    public static Text readText(StringCommandReader reader) {
        var json = reader.readJson();
        return jsonToText(json);
    }

    private static Integer stringToColor(String str) {
        if(str.length() == 7 && str.charAt(0) == '#') {
            try {
                return Integer.parseInt(str.substring(1));
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

    private static Text jsonToText(JsonElement element) {
        if(element instanceof JsonPrimitive prim && prim.isString()) {
            return new Text(List.of(), TextStyle.empty(), new TextContent.LiteralText(prim.getAsString()));
        }else if(element instanceof JsonObject obj) {
            TextContent content;
            if(obj.has("text")) {
                var rawText = obj.get("text").getAsString();
                content = new TextContent.LiteralText(rawText);
            }else {
                throw new CommandErrorException(new CommandError("Cannot turn \"" + element + "\" into Text"));
            }
            List<Text> siblings = List.of();
            if(obj.has("extra")) {
                siblings = new ArrayList<>();
                for(var extra : obj.getAsJsonArray("extra")) {
                    siblings.add(jsonToText(extra));
                }
            }

            Integer color = obj.has("color") ? stringToColor(obj.get("color").getAsString()) : null;
            Boolean bold = obj.has("bold") ? obj.get("bold").getAsBoolean() : null;
            Boolean italic = obj.has("italic") ? obj.get("italic").getAsBoolean() : null;
            Boolean underlined = obj.has("underlined") ? obj.get("underlined").getAsBoolean() : null;
            Boolean strikethrough = obj.has("strikethrough") ? obj.get("strikethrough").getAsBoolean() : null;
            Boolean obfuscated = obj.has("obfuscated") ? obj.get("obfuscated").getAsBoolean() : null;
            String insertion = obj.has("insertion") ? obj.get("insertion").getAsString() : null;
            DataLocation font = obj.has("font") ? new DataLocation(obj.get("font").getAsString()) : null;

            if(obj.has("clickEvent")) throw new CommandErrorException(new CommandError("\"clickEvent\" in text is not yet supported"));
            if(obj.has("hoverEvent")) throw new CommandErrorException(new CommandError("\"hoverEvent\" in text is not yet supported"));

            return new Text(siblings, new TextStyle(color, bold, italic, underlined, strikethrough, obfuscated,null, null, insertion, font), content);
        }else {
            throw new CommandErrorException(new CommandError("Cannot turn \"" + element + "\" into Text"));
        }
    }

}
