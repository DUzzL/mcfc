package dev.epicpix.minecraftfunctioncompiler.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextClickEvent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextHoverEvent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.TEXT_INVALID;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.TEXT_INVALID_COLOR;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.TEXT_INVALID_UNSUPPORTED_CLICK_EVENT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.TEXT_INVALID_UNSUPPORTED_HOVER_EVENT;

public final class TextParser {
    private TextParser() {}

    public static Text readText(StringCommandReader reader) {
        var json = reader.readJson();
        return jsonToText(json, reader);
    }

    private static DiagnosticException createError(StringCommandReader reader, String diagnosticId, String message) {
        return new DiagnosticException(Diagnostic.parseError(diagnosticId, reader.getSource(), reader.getString(), message));
    }

    private static DiagnosticException createError(StringCommandReader reader, String diagnosticId, String message, Throwable e) {
        return new DiagnosticException(Diagnostic.parseError(diagnosticId, reader.getSource(), reader.getString(), message, e));
    }

    private static @Nullable Integer stringToColor(String str) {
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
        switch(element) {
            case JsonPrimitive prim when prim.isString() -> {
                return new Text(List.of(), TextStyle.empty(), new TextContent.LiteralText(prim.getAsString()));
            }
            case JsonObject obj -> {
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
                }else if(obj.has("score")) {
                    var score = obj.getAsJsonObject("score");
                    var rawHolder = score.get("name").getAsString();
                    var objective = score.get("objective").getAsString();
                    ScoreHolderArgument holder;
                    try {
                        holder = SelectorParser.parseScoreHolder(new StringCommandReader(rawHolder, reader.getSource(), reader.allowsMacros()), false);
                    } catch(Exception e) {
                        throw createError(reader, TEXT_INVALID, "Failed to parse text score holder: " + e.getMessage() + " | " + rawHolder, e);
                    }
                    content = new TextContent.ScoreText(holder, objective);
                }else if(obj.has("selector")) {
                    var selector = SelectorParser.parseSelector(new StringCommandReader(obj.get("selector").getAsString(), reader.getSource(), reader.allowsMacros()), true, true);
                    var separator = obj.has("separator") ? jsonToText(obj.get("separator"), reader) : null;
                    content = new TextContent.SelectorText(selector, separator);
                }else if(obj.has("nbt")) {
                    var separator = obj.has("separator") ? jsonToText(obj.get("separator"), reader) : null;
                    var nbtReader = new StringCommandReader(obj.get("nbt").getAsString(), reader.getSource(), reader.allowsMacros());
                    var nbt = NbtParser.readNbtPath(nbtReader);
                    if(obj.has("block")) {
                        var block = new StringCommandReader(obj.get("block").getAsString(), reader.getSource(), reader.allowsMacros()).readVec3();
                        content = new TextContent.NbtText(nbt, separator, new NbtDataLocation.Block(block));
                    }else if(obj.has("entity")) {
                        var selector = SelectorParser.parseSelector(new StringCommandReader(obj.get("entity").getAsString(), reader.getSource(), reader.allowsMacros()), true, true);
                        content = new TextContent.NbtText(nbt, separator, new NbtDataLocation.Entity(selector));
                    }else if(obj.has("storage")) {
                        var storage = new StringCommandReader(obj.get("storage").getAsString(), reader.getSource(), reader.allowsMacros()).readDataLocation();
                        content = new TextContent.NbtText(nbt, separator, new NbtDataLocation.Storage(storage));
                    }else {
                        throw createError(reader, TEXT_INVALID, "Cannot turn \"" + element + "\" into Text");
                    }
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
                Integer shadowColor = obj.has("shadow_color") ? contentToColor(obj.get("shadow_color"), reader) : null;
                Boolean bold = obj.has("bold") ? obj.get("bold").getAsBoolean() : null;
                Boolean italic = obj.has("italic") ? obj.get("italic").getAsBoolean() : null;
                Boolean underlined = obj.has("underlined") ? obj.get("underlined").getAsBoolean() : null;
                Boolean strikethrough = obj.has("strikethrough") ? obj.get("strikethrough").getAsBoolean() : null;
                Boolean obfuscated = obj.has("obfuscated") ? obj.get("obfuscated").getAsBoolean() : null;
                String insertion = obj.has("insertion") ? obj.get("insertion").getAsString() : null;
                DataLocation font = obj.has("font") ? new DataLocation(obj.get("font").getAsString()) : null;

                TextClickEvent clickEvent = null;
                if(obj.has("click_event")) clickEvent = parseClickEvent(obj.getAsJsonObject("click_event"), reader);
                else if(obj.has("clickEvent")) clickEvent = parseClickEventLegacy(obj.getAsJsonObject("clickEvent"), reader);

                TextHoverEvent<?> hoverEvent = null;
                if(obj.has("hover_event")) hoverEvent = parseHoverEvent(obj.get("hover_event"), reader);
                else if(obj.has("hoverEvent")) hoverEvent = parseHoverEventLegacy(obj.getAsJsonObject("hoverEvent"), reader);

                return new Text(siblings, new TextStyle(color, shadowColor, bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, insertion, font), content);
            }
            case JsonArray arr -> {
                Text text = jsonToText(arr.get(0), reader);
                var siblings = new ArrayList<>(text.siblings());
                for(int i = 1; i < arr.size(); i++) {
                    siblings.add(jsonToText(arr.get(i), reader));
                }
                return new Text(siblings, text.style(), text.content());
            }
            case null, default -> throw createError(reader, TEXT_INVALID, "Cannot turn \"" + element + "\" into Text");
        }
    }

    // New format (MC 1.21.5+): click_event with action + action-specific fields
    private static @Nullable TextClickEvent parseClickEvent(JsonObject obj, StringCommandReader reader) {
        if(!obj.has("action")) return null;
        String action = obj.get("action").getAsString();
        return switch(action) {
            case "open_url" -> obj.has("url") ? new TextClickEvent(TextClickEvent.Action.OPEN_URL, obj.get("url").getAsString()) : null;
            case "run_command" -> obj.has("command") ? new TextClickEvent(TextClickEvent.Action.RUN_COMMAND, obj.get("command").getAsString()) : null;
            case "suggest_command" -> obj.has("command") ? new TextClickEvent(TextClickEvent.Action.SUGGEST_COMMAND, obj.get("command").getAsString()) : null;
            case "change_page" -> obj.has("page") ? new TextClickEvent(TextClickEvent.Action.CHANGE_PAGE, obj.get("page").getAsString()) : null;
            case "copy_to_clipboard" -> obj.has("value") ? new TextClickEvent(TextClickEvent.Action.COPY_TO_CLIPBOARD, obj.get("value").getAsString()) : null;
            default -> null;
        };
    }

    // Legacy format (pre-1.21.5): clickEvent with action + value
    private static @Nullable TextClickEvent parseClickEventLegacy(JsonObject obj, StringCommandReader reader) {
        if(!obj.has("action") || !obj.has("value")) return null;
        String action = obj.get("action").getAsString();
        String value = obj.get("value").getAsString();
        return switch(action) {
            case "open_url" -> new TextClickEvent(TextClickEvent.Action.OPEN_URL, value);
            case "run_command" -> new TextClickEvent(TextClickEvent.Action.RUN_COMMAND, value);
            case "suggest_command" -> new TextClickEvent(TextClickEvent.Action.SUGGEST_COMMAND, value);
            case "change_page" -> new TextClickEvent(TextClickEvent.Action.CHANGE_PAGE, value);
            case "copy_to_clipboard" -> new TextClickEvent(TextClickEvent.Action.COPY_TO_CLIPBOARD, value);
            default -> null;
        };
    }

    // New format (MC 1.21.5+): hover_event can be an object OR array; for show_text, value is nested Text
    private static @Nullable TextHoverEvent<?> parseHoverEvent(JsonElement element, StringCommandReader reader) {
        if(!(element instanceof JsonObject obj)) return null;
        if(!obj.has("action")) return null;
        String action = obj.get("action").getAsString();
        if("show_text".equals(action) && obj.has("value")) {
            Text text = jsonToText(obj.get("value"), reader);
            return new TextHoverEvent<>(TextHoverEvent.Action.SHOW_TEXT, text);
        }
        return null;
    }

    // Legacy format (pre-1.21.5): hoverEvent with action + value
    private static @Nullable TextHoverEvent<?> parseHoverEventLegacy(JsonObject obj, StringCommandReader reader) {
        if(!obj.has("action") || !obj.has("value")) return null;
        String action = obj.get("action").getAsString();
        if("show_text".equals(action)) {
            Text text = jsonToText(obj.get("value"), reader);
            return new TextHoverEvent<>(TextHoverEvent.Action.SHOW_TEXT, text);
        }
        return null;
    }

}
