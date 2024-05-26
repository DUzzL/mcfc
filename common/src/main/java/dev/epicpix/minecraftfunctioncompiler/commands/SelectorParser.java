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

    public static ScoreHolderArgument parseScoreHolder(String scoreHolder) {
        if(scoreHolder.startsWith("@")) {
            return ScoreHolderArgument.selectSelector(parseSelector(scoreHolder));
        }
        if(scoreHolder.equals("*")) {
            return ScoreHolderArgument.selectAll();
        }
        return ScoreHolderArgument.selectNamed(scoreHolder);
    }

    public static SelectorArgument parseSelector(String selector) {
        if(!selector.startsWith("@")) {
            try {
                return SelectorArgument.ofUuid(UUID.fromString(selector));
            }catch(IllegalArgumentException e) {
                return SelectorArgument.ofPlayer(selector);
            }
        }

        var reader = new StringCommandReader(selector);
        reader.skip();
        var selectorType = reader.read();

        var isSelf = selectorType == 's';
        var data = new SelectorGeneratorBuilderData();
        if(isSelf) data.limit = 1;
        if(selectorType == 'a') data.entityTypes = new NegatableData<>(List.of(new DataLocation("minecraft", "player")), false);

        if(selectorType == 'r' || selectorType == 'p') throw new UnsupportedOperationException("Unsupported selector: " + selector);

        reader.skipWhitespace();
        if(reader.canRead() && reader.read() == '[') {
            while(reader.canRead()) {
                reader.skipWhitespace();
                var key = reader.readString();
                reader.skipWhitespace();
                if(!reader.canRead() || reader.read() != '=') throw new UnsupportedOperationException("Cannot parse selector, missing '=': " + selector);
                reader.skipWhitespace();
                var option = selectorOptions.get(key);
                if(option == null) throw new UnsupportedOperationException("Cannot parse selector key: " + key + " in " + selector);
                option.accept(reader, data);
                reader.skipWhitespace();
                if(!reader.canRead()) throw new UnsupportedOperationException("Cannot parse selector, ran out of data: " + selector);
                var next = reader.read();
                if(next == ',') continue;
                if(next == ']') break;
                throw new UnsupportedOperationException("Cannot parse selector, ran out of data: " + selector);
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
