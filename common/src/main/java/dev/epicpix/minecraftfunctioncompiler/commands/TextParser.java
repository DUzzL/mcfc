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

    // `interpret` / `plain` of an nbt component. Only a real boolean is accepted here; any other
    // spelling (e.g. SNBT 1b) is rejected so the command goes through the vanilla codec instead.
    private static boolean readNbtTextFlag(JsonObject obj, String key, StringCommandReader reader) {
        if(!obj.has(key)) return false;
        var value = obj.get(key);
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
        throw createError(reader, TEXT_INVALID, "Unsupported '" + key + "' value in nbt text component: " + value);
    }

    /** TextColor.parseColor: "#hex" of any length in [0, 0xFFFFFF], or a named colour; anything else is invalid (PAR-21). */
    private static Integer stringToColor(String str, StringCommandReader reader) {
        if(str.startsWith("#")) {
            try {
                int value = Integer.parseInt(str.substring(1), 16);
                if(value >= 0 && value <= 0xFFFFFF) return value;
            }catch(NumberFormatException ignored) {}
            throw createError(reader, TEXT_INVALID_COLOR, "Invalid color: " + str);
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
            default -> throw createError(reader, TEXT_INVALID_COLOR, "Unknown color name: " + str);
        };
    }

    /** ExtraCodecs.ARGB_COLOR_CODEC: an int, or [r, g, b, a] floats packed with ARGB.colorFromFloat (PAR-11). */
    private static Integer contentToColor(JsonElement element, StringCommandReader reader) {
        try {
            if(element instanceof JsonArray array && array.size() == 4) {
                float r = parseColorChannel(array.get(0));
                float g = parseColorChannel(array.get(1));
                float b = parseColorChannel(array.get(2));
                float a = parseColorChannel(array.get(3));
                return as8BitChannel(a) << 24 | as8BitChannel(r) << 16 | as8BitChannel(g) << 8 | as8BitChannel(b);
            }else if(element instanceof JsonPrimitive prim && prim.isNumber()) {
                return prim.getAsInt();
            }
        }catch(NumberFormatException | UnsupportedOperationException | IllegalStateException ignored) {}
        throw createError(reader, TEXT_INVALID_COLOR, "Invalid color specification: " + element + " (expected [R,G,B,A] floats or integer ARGB)");
    }

    private static float parseColorChannel(JsonElement element) {
        String s = element.getAsString();
        char last = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(s.length() - 1));
        if(last == 'f' || last == 'd') s = s.substring(0, s.length() - 1);
        return Float.parseFloat(s);
    }

    private static int as8BitChannel(float value) {
        return (int) Math.floor(value * 255.0F) & 0xFF;
    }

    /** Style boolean: JSON boolean, a number (!= 0), or an SNBT byte/int token like "1b" (PAR-10). */
    private static @Nullable Boolean readStyleBoolean(JsonObject obj, String key, StringCommandReader reader) {
        if(!obj.has(key)) return null;
        var value = obj.get(key);
        if(value instanceof JsonPrimitive prim) {
            if(prim.isBoolean()) return prim.getAsBoolean();
            if(prim.isNumber()) return prim.getAsDouble() != 0;
            String s = prim.getAsString();
            if(s.equals("true")) return true;
            if(s.equals("false")) return false;
            if(s.matches("[+-]?\\d+[bBsSiIlL]?")) return Long.parseLong(s.replaceAll("[bBsSiIlL]$", "")) != 0;
        }
        throw createError(reader, TEXT_INVALID, "Invalid boolean '" + key + "' in text component: " + value);
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
                    content = new TextContent.ScoreText(holder, objective, rawHolder);
                }else if(obj.has("selector")) {
                    var selector = SelectorParser.parseSelector(new StringCommandReader(obj.get("selector").getAsString(), reader.getSource(), reader.allowsMacros()), true, true);
                    var separator = obj.has("separator") ? jsonToText(obj.get("separator"), reader) : null;
                    content = new TextContent.SelectorText(selector, separator);
                }else if(obj.has("nbt")) {
                    var separator = obj.has("separator") ? jsonToText(obj.get("separator"), reader) : null;
                    var rawPath = obj.get("nbt").getAsString();
                    var nbtReader = new StringCommandReader(rawPath, reader.getSource(), reader.allowsMacros());
                    var nbt = NbtParser.readNbtPath(nbtReader);
                    boolean interpret = readNbtTextFlag(obj, "interpret", reader);
                    boolean plain = readNbtTextFlag(obj, "plain", reader);
                    if(interpret && plain) {
                        // vanilla NbtContents codec: "'interpret' and 'plain' flags can't be both on"
                        throw createError(reader, TEXT_INVALID, "'interpret' and 'plain' flags can't be both on");
                    }
                    if(obj.has("block")) {
                        var block = new StringCommandReader(obj.get("block").getAsString(), reader.getSource(), reader.allowsMacros()).readBlockPos();
                        content = new TextContent.NbtText(nbt, rawPath, interpret, plain, separator, new NbtDataLocation.Block(block));
                    }else if(obj.has("entity")) {
                        var selector = SelectorParser.parseSelector(new StringCommandReader(obj.get("entity").getAsString(), reader.getSource(), reader.allowsMacros()), true, true);
                        content = new TextContent.NbtText(nbt, rawPath, interpret, plain, separator, new NbtDataLocation.Entity(selector));
                    }else if(obj.has("storage")) {
                        var storage = new StringCommandReader(obj.get("storage").getAsString(), reader.getSource(), reader.allowsMacros()).readDataLocation();
                        content = new TextContent.NbtText(nbt, rawPath, interpret, plain, separator, new NbtDataLocation.Storage(storage));
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

                Integer color = obj.has("color") ? stringToColor(obj.get("color").getAsString(), reader) : null;
                Integer shadowColor = obj.has("shadow_color") ? contentToColor(obj.get("shadow_color"), reader) : null;
                Boolean bold = readStyleBoolean(obj, "bold", reader);
                Boolean italic = readStyleBoolean(obj, "italic", reader);
                Boolean underlined = readStyleBoolean(obj, "underlined", reader);
                Boolean strikethrough = readStyleBoolean(obj, "strikethrough", reader);
                Boolean obfuscated = readStyleBoolean(obj, "obfuscated", reader);
                String insertion = obj.has("insertion") ? obj.get("insertion").getAsString() : null;
                DataLocation font = obj.has("font") ? new DataLocation(obj.get("font").getAsString()) : null;

                // Legacy clickEvent/hoverEvent keys are ignored by the 26.3 codec (PAR-12).
                TextClickEvent clickEvent = obj.has("click_event") ? parseClickEvent(obj.get("click_event"), reader) : null;
                TextHoverEvent<?> hoverEvent = obj.has("hover_event") ? parseHoverEvent(obj.get("hover_event"), reader) : null;

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

    // click_event (26.3 codec). Actions the compiled Text cannot represent (open_file, show_dialog,
    // custom) or malformed events throw, so the command is left to vanilla (PAR-12).
    private static TextClickEvent parseClickEvent(JsonElement element, StringCommandReader reader) {
        if(!(element instanceof JsonObject obj) || !obj.has("action")) throw createError(reader, TEXT_INVALID, "Invalid click_event: " + element);
        String action = obj.get("action").getAsString();
        String field = switch(action) {
            case "open_url" -> "url";
            case "run_command", "suggest_command" -> "command";
            case "change_page" -> "page";
            case "copy_to_clipboard" -> "value";
            default -> throw createError(reader, TEXT_INVALID, "Unsupported click_event action: " + action);
        };
        if(!obj.has(field)) throw createError(reader, TEXT_INVALID, "click_event " + action + " is missing '" + field + "'");
        var value = obj.get(field).getAsString();
        return new TextClickEvent(switch(action) {
            case "open_url" -> TextClickEvent.Action.OPEN_URL;
            case "run_command" -> TextClickEvent.Action.RUN_COMMAND;
            case "suggest_command" -> TextClickEvent.Action.SUGGEST_COMMAND;
            case "change_page" -> TextClickEvent.Action.CHANGE_PAGE;
            default -> TextClickEvent.Action.COPY_TO_CLIPBOARD;
        }, value);
    }

    // hover_event (26.3 codec): only show_text is representable; show_item/show_entity throw (PAR-12).
    private static TextHoverEvent<?> parseHoverEvent(JsonElement element, StringCommandReader reader) {
        if(!(element instanceof JsonObject obj) || !obj.has("action")) throw createError(reader, TEXT_INVALID, "Invalid hover_event: " + element);
        String action = obj.get("action").getAsString();
        if(!"show_text".equals(action)) throw createError(reader, TEXT_INVALID, "Unsupported hover_event action: " + action);
        if(!obj.has("value")) throw createError(reader, TEXT_INVALID, "hover_event show_text is missing 'value'");
        return new TextHoverEvent<>(TextHoverEvent.Action.SHOW_TEXT, jsonToText(obj.get("value"), reader));
    }

}
