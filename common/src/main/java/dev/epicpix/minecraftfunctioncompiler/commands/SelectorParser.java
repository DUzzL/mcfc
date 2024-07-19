package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators;
import dev.epicpix.minecraftfunctioncompiler.data.*;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class SelectorParser {
    private SelectorParser() {}

    public static ScoreHolderArgument parseScoreHolder(StringCommandReader scoreHolder) {
        if(scoreHolder.peek() == '@') {
            return ScoreHolderArgument.selectSelector(parseSelector(scoreHolder, true, true));
        }
        MacroTemplate<String> name = scoreHolder.readUntilSpaceWithTemplate(List.of(MacroValidators.NO_STAR));
        if(name.isLiteral("*")) {
            return ScoreHolderArgument.selectAll();
        }
        return ScoreHolderArgument.selectNamed(name);
    }

    private static void throwError(StringCommandReader reader, String message) {
        throw new CommandErrorException(new CommandError(reader.getString(), message));
    }

    public static SelectorArgument parseSelector(StringCommandReader reader, boolean allowEntities, boolean allowMultiple) {
        if(reader.peek() != '@') {
            try {
                return SelectorArgument.ofUuid(UUID.fromString(reader.readWord()));
            }catch(IllegalArgumentException e) {
                return SelectorArgument.ofPlayer(reader.readWord());
            }
        }

        reader.skip();
        var selectorType = reader.read();

        var data = new SelectorGeneratorBuilderData();
        switch (selectorType) {
            case 'e' -> {
                if(!allowEntities) throwError(reader, "Unable to use @e with a player-only selector");
                if(!allowMultiple) throwError(reader, "Unable to use @e with a single-only selector");
                data.requireAlive = true;
            }
            case 's' -> {
                data.self = true;
                data.limit = 1;
            }
            case 'a' -> {
                if(!allowMultiple) throwError(reader, "Unable to use @a with a single-only selector");
                data.requirePlayer = true;
            }
            case 'p' -> {
                data.requirePlayer = true;
                data.sort = SelectorSort.NEAREST;
                data.limit = 1;
            }
            case 'r' -> {
                data.requirePlayer = true;
                data.sort = SelectorSort.RANDOM;
                data.limit = 1;
            }
            case 'n' -> {
                if(!MinecraftVersion.isCurrentVersionInRange(MinecraftVersion.MC_1_21, null)) throwError(reader, "Selector @n is not available on versions below 1.21");
                data.requirePlayer = true;
                data.sort = SelectorSort.NEAREST;
                data.limit = 1;
            }
            default -> throwError(reader, "Unsupported selector type: @" + selectorType);
        }

        if(reader.canRead() && reader.peek() == '[') {
            reader.skip();
            while(reader.canRead() && reader.peek() != ']') {
                reader.skipWhitespace();
                var key = reader.readString();
                reader.skipWhitespace();
                if(!reader.canRead() || reader.read() != '=') throwError(reader, "Cannot parse selector, missing '='");
                reader.skipWhitespace();
                var option = selectorOptions.get(key);
                if(option == null) throwError(reader, "Cannot parse selector key: " + key);
                option.accept(reader, data);
                reader.skipWhitespace();
                if(!reader.canRead()) throwError(reader, "Cannot parse selector, ran out of data expecting ',' or ']'");
                var next = reader.peek();
                if(next == ',') {
                    reader.skip();
                    continue;
                }
                if(next == ']') break;
                throwError(reader, "Cannot parse selector, ran out of data, invalid value '" + next + "' expected ',' or ']'");
            }
            if(reader.peek() == ']') {
                reader.skip();
            }else {
                throwError(reader, "Cannot parse selector, ran out of data, invalid value '" + reader.peek() + "', expected ']'");
            }
        }

        return data.build();
    }


    private static final HashMap<String, SelectorReader> selectorOptions = new HashMap<>();
    public static void registerSelectorOption(String name, SelectorReader exec) {
        selectorOptions.put(name, exec);
    }

    public static void initBaseSelectorOptions() {
        registerSelectorOption("tag", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var tag = reader.readWord();
            if(tag.isEmpty()) {
                data.hasTags = !negated;
            }else {
                data.tags.add(new NegatableData<>(tag, negated));
            }
        });
        SelectorParser.registerSelectorOption("gamemode", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var gamemode = reader.readGamemode();
            data.gamemodes.add(new NegatableData<>(gamemode, negated));
        });
        SelectorParser.registerSelectorOption("sort", (reader, data) -> {
            data.sort = reader.readSelectorSort();
        });
        SelectorParser.registerSelectorOption("name", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var name = reader.readString();
            data.names.add(new NegatableData<>(name, negated));
        });
        registerSelectorOption("limit", (reader, data) -> {
            data.limit = reader.readInt();
        });
        registerSelectorOption("level", (reader, data) -> {
            data.level = reader.readIntRange();
            if((data.level.minimum() != null && data.level.minimum() < 0) || (data.level.maximum() != null && data.level.maximum() < 0)) {
                throwError(reader, "Minimum/maximum level cannot be negative: " + data.level);
            }
        });
        registerSelectorOption("distance", (reader, data) -> {
            data.distance = reader.readDoubleRange();
            if((data.distance.minimum() != null && data.distance.minimum() < 0) || (data.distance.maximum() != null && data.distance.maximum() < 0)) {
                throwError(reader, "Minimum/maximum distance cannot be negative: " + data.distance);
            }
        });
        registerSelectorOption("scores", (reader, data) -> {
            reader.skip();
            reader.skipWhitespace();
            while(reader.peek() != '}') {
                var objective = reader.readWord();
                reader.skipWhitespace();
                reader.skip();
                reader.skipWhitespace();
                data.scores.put(objective, reader.readIntRange());
                reader.skipWhitespace();
                if(reader.peek() == ',') reader.read();
            }
            reader.read();
        });
        registerSelectorOption("team", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var team = reader.readWord();
            if(team.isEmpty()) {
                data.hasTeam = !negated;
            }else {
                data.teams.add(new NegatableData<>(team, negated));
            }
        });
        registerSelectorOption("x_rotation", (reader, data) -> {
            var bounds = reader.readFloatRange();
            data.pitch = new FloatRangeArgument(bounds.minimum() != null ? wrapDegrees(bounds.minimum()) : null, bounds.maximum() != null ? wrapDegrees(bounds.maximum()) : null);
        });
        registerSelectorOption("y_rotation", (reader, data) -> {
            var bounds = reader.readFloatRange();
            data.yaw = new FloatRangeArgument(bounds.minimum() != null ? wrapDegrees(bounds.minimum()) : null, bounds.maximum() != null ? wrapDegrees(bounds.maximum()) : null);
        });
    }


    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if(angle >= 180.0F) angle -= 360.0F;
        if(angle < -180.0F) angle += 360.0F;
        return angle;
    }

}
