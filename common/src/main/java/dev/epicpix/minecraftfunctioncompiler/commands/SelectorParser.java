package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.MinecraftVersion;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

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
            var word = reader.readWord();
            try {
                return SelectorArgument.ofUuid(UUID.fromString(word));
            }catch(IllegalArgumentException e) {
                return SelectorArgument.ofPlayer(word);
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
                reader.checkMacro(false);
                var next = reader.peek();
                if(next == ',') {
                    reader.skip();
                    continue;
                }
                if(next == ']') break;
                throwError(reader, "Cannot parse selector, ran out of data, invalid value '" + next + "' expected ',' or ']'");
            }
            reader.checkMacro(false);
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
        registerSelectorOption("type", (reader, data) -> {
            if(data.entityTypes != null) throwError(reader, "type selector cannot be repeated");
            var negated = reader.skipIfNext('!');
            var tag = reader.skipIfNext('#');
            var type = reader.readDataLocation();
            List<DataLocation> types;
            if(!tag) {
                types = List.of(type);
            }else {
                types = DataRegistries.ENTITY_TAGS.get(type);
                if(types == null) types = List.of();
            }
            for(var it : types) {
                if(DataRegistries.ENTITY_TYPE.get(it) == null) throw new UnsupportedOperationException("Unsupported entity type: " + it);
            }
            data.entityTypes = new NegatableData<>(types, negated);
        });
        registerSelectorOption("tag", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var tag = reader.readMacroWord(List.of(MacroValidators.NOT_EMPTY));
            if(tag.parts().isEmpty()) {
                data.hasTags = !negated;
            }else {
                data.tags.add(new NegatableData<>(tag, negated));
            }
        });
        registerSelectorOption("gamemode", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var gamemode = reader.readGamemode();
            for(var cGamemode : data.gamemodes) {
                if(!cGamemode.negated()) {
                    throwError(reader, "gamemode selector cannot have multiple non-negated values");
                }
                if(!negated) {
                    throwError(reader, "gamemode selector cannot have a negated and non-negated value");
                }
            }
            data.gamemodes.add(new NegatableData<>(gamemode, negated));
        });
        registerSelectorOption("sort", (reader, data) -> {
            data.sort = reader.readSelectorSort();
        });
        registerSelectorOption("name", (reader, data) -> {
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
            if(data.distance != null) throwError(reader, "distance selector cannot be repeated");
            data.worldLimited = true;
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
        registerSelectorOption("x", (reader, data) -> {
            if(data.x != null) throwError(reader, "x selector cannot be repeated");
            data.worldLimited = true;
            data.x = reader.readDouble();
        });
        registerSelectorOption("y", (reader, data) -> {
            if(data.y != null) throwError(reader, "y selector cannot be repeated");
            data.worldLimited = true;
            data.y = reader.readDouble();
        });
        registerSelectorOption("z", (reader, data) -> {
            if(data.z != null) throwError(reader, "z selector cannot be repeated");
            data.worldLimited = true;
            data.z = reader.readDouble();
        });
        registerSelectorOption("dx", (reader, data) -> {
            if(data.dx != null) throwError(reader, "dx selector cannot be repeated");
            data.worldLimited = true;
            data.dx = reader.readDouble();
        });
        registerSelectorOption("dy", (reader, data) -> {
            if(data.dy != null) throwError(reader, "dy selector cannot be repeated");
            data.worldLimited = true;
            data.dy = reader.readDouble();
        });
        registerSelectorOption("dz", (reader, data) -> {
            if(data.dz != null) throwError(reader, "dz selector cannot be repeated");
            data.worldLimited = true;
            data.dz = reader.readDouble();
        });
    }


    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if(angle >= 180.0F) angle -= 360.0F;
        if(angle < -180.0F) angle += 360.0F;
        return angle;
    }

}
