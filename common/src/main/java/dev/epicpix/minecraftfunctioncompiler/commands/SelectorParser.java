package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class SelectorParser {
    private SelectorParser() {}

    public static ScoreHolderArgument parseScoreHolder(StringCommandReader scoreHolder) {
        if(scoreHolder.peek() == '@') {
            return ScoreHolderArgument.selectSelector(parseSelector(scoreHolder, true, true));
        }
        String name = scoreHolder.readUntilSpace();
        if(name.equals("*")) {
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
            }
            case 's' -> {
                data.self = true;
                data.limit = 1;
            }
            case 'a' -> {
                if(!allowMultiple) throwError(reader, "Unable to use @a with a single-only selector");
                data.entityTypes = new NegatableData<>(List.of(new DataLocation("minecraft", "player")), false);
            }
            default -> throwError(reader, "Unsupported selector type: @" + selectorType);
        }

        if(reader.canRead() && reader.peek() == '[') {
            reader.skip();
            while(reader.canRead()) {
                reader.skipWhitespace();
                var key = reader.readString();
                reader.skipWhitespace();
                if(!reader.canRead() || reader.read() != '=') throwError(reader, "Cannot parse selector, missing '='");
                reader.skipWhitespace();
                var option = selectorOptions.get(key);
                if(option == null) throwError(reader, "Cannot parse selector key: " + key);
                option.accept(reader, data);
                reader.skipWhitespace();
                if(!reader.canRead()) throwError(reader, "Cannot parse selector, ran out of data");
                var next = reader.read();
                if(next == ',') continue;
                if(next == ']') break;
                throwError(reader, "Cannot parse selector, ran out of data");
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
        registerSelectorOption("limit", (reader, data) -> {
            data.limit = reader.readInt();
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
                data.team = new NegatableData<>(team, negated);
            }
        });
    }

}
