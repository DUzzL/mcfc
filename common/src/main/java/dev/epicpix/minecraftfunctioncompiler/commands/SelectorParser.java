package dev.epicpix.minecraftfunctioncompiler.commands;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroValidators;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.NegatableData;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorAdvancementCheck;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorSort;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_EXPECTED_PLAYER_TARGETS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_EXPECTED_SINGULAR_TARGET;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_INVALID_ENTITY;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_INVALID_SYNTAX;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_INVALID_TARGET;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_OPTION_CANNOT_REPEAT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_OPTION_INVALID;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.SELECTOR_UNKNOWN_OPTION;

public final class SelectorParser {
    private SelectorParser() {}

    public static ScoreHolderArgument parseScoreHolder(StringCommandReader scoreHolder, boolean allowMultiple) {
        if(scoreHolder.canRead() && scoreHolder.peek() == '@') {
            return ScoreHolderArgument.selectSelector(parseSelector(scoreHolder, true, allowMultiple));
        }
        MacroTemplate<String> name = scoreHolder.readUntilSpaceWithTemplate(List.of(MacroValidators.NO_STAR, MacroValidators.IS_PLAIN_SCORE_HOLDER_NAME));
        if(name.isExact("*")) {
            return ScoreHolderArgument.selectAll();
        }
        if(name.isStatic()) {
            String text = name.getStaticValue();
            if(text.isEmpty()) throwError(scoreHolder, SELECTOR_INVALID_SYNTAX, "Expected a score holder");
            // Vanilla resolves UUID-shaped names to entities; leave those to vanilla.
            if(text.charAt(0) != '#' && looksLikeUuid(text)) throwError(scoreHolder, SELECTOR_INVALID_ENTITY, "UUID score holders are resolved by vanilla");
        }
        return ScoreHolderArgument.selectNamed(name);
    }

    private static boolean looksLikeUuid(String text) {
        if(text.indexOf('-') < 0) return false;
        try {
            UUID.fromString(text);
            return true;
        }catch(IllegalArgumentException e) {
            return false;
        }
    }

    private static void throwError(StringCommandReader reader, String diagnosticId, String message) {
        throw new DiagnosticException(Diagnostic.parseError(diagnosticId, reader.getSource(), reader.getString(), message));
    }

    public static SelectorArgument parseSelector(StringCommandReader reader, boolean allowEntities, boolean allowMultiple) {
        if(!reader.canRead()) throwError(reader, SELECTOR_INVALID_SYNTAX, "Expected a selector");
        if(reader.peek() != '@') {
            // EntitySelectorParser.parseNameOrUUID: brigadier readString (quoted or [0-9A-Za-z_.+-]*).
            var word = reader.readString();
            if(looksLikeUuid(word)) {
                // A UUID selector includes non-player entities (players-only rejects it).
                if(!allowEntities) throwError(reader, SELECTOR_EXPECTED_PLAYER_TARGETS, "Only players may be affected by this command");
                return SelectorArgument.ofUuid(UUID.fromString(word));
            }
            if(word.isEmpty() || word.length() > 16) throwError(reader, SELECTOR_INVALID_ENTITY, "Invalid name or UUID");
            return SelectorArgument.ofPlayer(word);
        }

        reader.skip();
        if(!reader.canRead()) throwError(reader, SELECTOR_INVALID_TARGET, "Missing selector type");
        var selectorType = reader.read();

        var data = new SelectorGeneratorBuilderData();
        switch (selectorType) {
            case 'e' -> data.requireAlive = true;
            case 's' -> {
                data.self = true;
                data.limit = 1;
            }
            case 'a' -> {
                data.requirePlayer = true;
                data.includesEntities = false;
                data.typeLimited = true;
            }
            case 'p' -> {
                data.requirePlayer = true;
                data.includesEntities = false;
                data.typeLimited = true;
                data.sort = SelectorSort.NEAREST;
                data.limit = 1;
            }
            case 'r' -> {
                data.requirePlayer = true;
                data.includesEntities = false;
                data.typeLimited = true;
                data.sort = SelectorSort.RANDOM;
                data.limit = 1;
            }
            case 'n' -> {
                data.requireAlive = true;
                data.sort = SelectorSort.NEAREST;
                data.limit = 1;
            }
            default -> throwError(reader, SELECTOR_INVALID_TARGET, "Unsupported selector kind: @" + selectorType);
        }

        if(reader.canRead() && reader.peek() == '[') {
            reader.skip();
            while(reader.canRead() && reader.peek() != ']') {
                reader.skipWhitespace();
                var key = reader.readString();
                reader.skipWhitespace();
                if(!reader.canRead() || reader.read() != '=') throwError(reader, SELECTOR_INVALID_SYNTAX, "Cannot parse selector, missing '='");
                reader.skipWhitespace();
                var option = selectorOptions.get(key);
                if(option == null) throwError(reader, SELECTOR_UNKNOWN_OPTION, "Cannot parse selector key: " + key);
                option.accept(reader, data);
                reader.skipWhitespace();
                if(!reader.canRead()) throwError(reader, SELECTOR_INVALID_SYNTAX, "Cannot parse selector, ran out of data expecting ',' or ']'");
                reader.checkMacro(false);
                var next = reader.peek();
                if(next == ',') {
                    reader.skip();
                    continue;
                }
                if(next == ']') break;
                throwError(reader, SELECTOR_INVALID_SYNTAX, "Cannot parse selector, ran out of data, invalid value '" + next + "' expected ',' or ']'");
            }
            reader.checkMacro(false);
            if(reader.peek() == ']') {
                reader.skip();
            }else {
                throwError(reader, SELECTOR_INVALID_SYNTAX, "Cannot parse selector, ran out of data, invalid value '" + reader.peek() + "', expected ']'");
            }
        }

        // EntityArgument: single-only rejects getMaxResults() > 1; players-only rejects
        // selectors that include entities unless it is @s (PAR-14, PAR-16).
        if(!allowMultiple && (data.limit == null || data.limit > 1)) {
            throwError(reader, SELECTOR_EXPECTED_SINGULAR_TARGET, "Only one entity is allowed, but the provided selector allows more than one");
        }
        if(!allowEntities && data.includesEntities && !data.self) {
            throwError(reader, SELECTOR_EXPECTED_PLAYER_TARGETS, "Only players may be affected by this command, but the provided selector includes entities");
        }

        return data.build();
    }


    private static final HashMap<String, SelectorReader> selectorOptions = new HashMap<>();
    public static void registerSelectorOption(String name, SelectorReader exec) {
        selectorOptions.put(name, exec);
    }

    public static void initBaseSelectorOptions() {
        registerSelectorOption("advancements", (reader, data) -> {
            if(data.advancements != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "advancements selector cannot be repeated");
            data.advancements = new HashMap<>();
            reader.skip();
            reader.skipWhitespace();
            while(reader.peek() != '}') {
                reader.skipWhitespace();
                var key = reader.readDataLocation();
                reader.skipWhitespace();
                reader.skip();
                reader.skipWhitespace();
                if(reader.peek() == '{') {
                    reader.skip();
                    reader.skipWhitespace();
                    HashMap<String, Boolean> criterionChecks = new HashMap<>();
                    while(reader.peek() != '}') {
                        reader.skipWhitespace();
                        var criterion = reader.readWord();
                        reader.skipWhitespace();
                        reader.skip();
                        reader.skipWhitespace();
                        var check = reader.readBoolean();
                        criterionChecks.put(criterion, check);
                        reader.skipWhitespace();
                        if(reader.peek() == ',') {
                            reader.skip();
                        }
                    }
                    reader.skip();
                    data.advancements.put(key, new SelectorAdvancementCheck.CriteriaCompleted(criterionChecks));
                }else {
                    var check = reader.readBoolean();
                    data.advancements.put(key, new SelectorAdvancementCheck.AdvancementCompleted(check));
                }
                reader.skipWhitespace();
                if(reader.peek() == ',') {
                    reader.skip();
                }
            }
            reader.skip();
        });
        registerSelectorOption("type", (reader, data) -> {
            // EntitySelectorOptions "type": not applicable once a positive concrete type is set (or @a/@p/@r);
            // a positive type may not follow a negated one; negations accumulate.
            if(data.typeLimited) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "type selector option isn't applicable here");
            var negated = reader.skipIfNext('!');
            if(data.typeLimitedInversely && !negated) throwError(reader, SELECTOR_OPTION_INVALID, "type selector option isn't applicable here");
            var tag = reader.skipIfNext('#');
            var type = reader.readDataLocation();
            List<DataLocation> types;
            if(!tag) {
                if(DataRegistries.ENTITY_TYPE.getElement(type).isEmpty()) throwError(reader, SELECTOR_INVALID_ENTITY, "Unsupported entity type: " + type);
                types = List.of(type);
            }else {
                types = DataRegistries.ENTITY_TYPE.getTag(type);
            }
            if(negated) {
                data.typeLimitedInversely = true;
                if(data.entityTypes == null) {
                    data.entityTypes = new NegatableData<>(types, true);
                }else if(data.entityTypes.negated()) {
                    var union = new java.util.ArrayList<>(data.entityTypes.data());
                    for(var it : types) if(!union.contains(it)) union.add(it);
                    data.entityTypes = new NegatableData<>(union, true);
                }else {
                    // positive tag followed by negations: not representable as one set; leave to vanilla
                    throwError(reader, SELECTOR_OPTION_INVALID, "Mixed positive and negated type options are resolved by vanilla");
                }
            }else if(!tag) {
                if(data.entityTypes != null) {
                    // positive tag(s) then a concrete type: intersection; leave to vanilla
                    throwError(reader, SELECTOR_OPTION_INVALID, "Combined type options are resolved by vanilla");
                }
                data.typeLimited = true;
                if(type.equals(new DataLocation("minecraft", "player"))) data.includesEntities = false;
                data.entityTypes = new NegatableData<>(types, false);
            }else {
                if(data.entityTypes != null) throwError(reader, SELECTOR_OPTION_INVALID, "Combined type options are resolved by vanilla");
                data.entityTypes = new NegatableData<>(types, false);
            }
        });
        registerSelectorOption("tag", (reader, data) -> {
            // EntitySelectorOptions: `tag=` matches entities with no tags, `tag=!` entities with any tag.
            var negated = reader.skipIfNext('!');
            var tag = reader.readMacroWord(List.of(MacroValidators.NOT_EMPTY));
            if(tag.parts().isEmpty() || tag.isExact("")) {
                if(data.hasTags != null && data.hasTags != negated) throwError(reader, SELECTOR_OPTION_INVALID, "Contradicting empty tag options are resolved by vanilla");
                data.hasTags = negated;
            }else {
                data.tags.add(new NegatableData<>(tag, negated));
            }
        });
        registerSelectorOption("gamemode", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var gamemode = reader.readGamemode();
            for(var cGamemode : data.gamemodes) {
                if(!cGamemode.negated()) {
                    throwError(reader, SELECTOR_OPTION_INVALID, "gamemode selector cannot have multiple non-negated values");
                }
                if(!negated) {
                    throwError(reader, SELECTOR_OPTION_INVALID, "gamemode selector cannot have a negated and non-negated value");
                }
            }
            data.gamemodes.add(new NegatableData<>(gamemode, negated));
        });
        registerSelectorOption("sort", (reader, data) -> {
            if(data.self || data.sortSet) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "sort selector option isn't applicable here");
            data.sortSet = true;
            data.sort = reader.readSelectorSort();
        });
        registerSelectorOption("name", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var name = reader.readString();
            // InvertableSetOptionState: one positive name, never mixed with negated ones.
            boolean anyPositive = false;
            for(var it : data.names) anyPositive |= !it.negated();
            if(anyPositive || (!negated && !data.names.isEmpty())) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "name selector option isn't applicable here");
            data.names.add(new NegatableData<>(name, negated));
        });
        registerSelectorOption("limit", (reader, data) -> {
            if(data.self || data.limitSet) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "limit selector option isn't applicable here");
            data.limitSet = true;
            int limit = reader.readInt();
            if(limit < 1) throwError(reader, SELECTOR_OPTION_INVALID, "Limit must be at least 1");
            data.limit = limit;
        });
        registerSelectorOption("level", (reader, data) -> {
            if(data.level != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "level selector cannot be repeated");
            data.includesEntities = false;
            data.level = reader.readIntRange();
            if((data.level.minimum() != null && data.level.minimum() < 0) || (data.level.maximum() != null && data.level.maximum() < 0)) {
                throwError(reader, SELECTOR_OPTION_INVALID, "Minimum/maximum level cannot be negative: " + data.level);
            }
        });
        registerSelectorOption("distance", (reader, data) -> {
            if(data.distance != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "distance selector cannot be repeated");
            data.worldLimited = true;
            data.distance = reader.readDoubleRange();
            if((data.distance.minimum() != null && data.distance.minimum() < 0) || (data.distance.maximum() != null && data.distance.maximum() < 0)) {
                throwError(reader, SELECTOR_OPTION_INVALID, "Minimum/maximum distance cannot be negative: " + data.distance);
            }
        });
        registerSelectorOption("scores", (reader, data) -> {
            if(data.scoresSet) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "scores selector cannot be repeated");
            data.scoresSet = true;
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
            // EntitySelectorOptions: `team=` matches entities without a team, `team=!` entities on any team.
            var negated = reader.skipIfNext('!');
            var team = reader.readWord();
            // InvertableSetOptionState: one positive team, never mixed with negated ones.
            boolean anyPositive = Boolean.FALSE.equals(data.hasTeam);
            for(var it : data.teams) anyPositive |= !it.negated();
            boolean any = data.hasTeam != null || !data.teams.isEmpty();
            if(anyPositive || (!negated && any)) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "team selector option isn't applicable here");
            if(team.isEmpty()) {
                if(data.hasTeam != null && data.hasTeam != negated) throwError(reader, SELECTOR_OPTION_INVALID, "Contradicting empty team options are resolved by vanilla");
                data.hasTeam = negated;
            }else {
                data.teams.add(new NegatableData<>(team, negated));
            }
        });
        registerSelectorOption("x_rotation", (reader, data) -> {
            if(data.pitch != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "x_rotation selector cannot be repeated");
            data.pitch = readRotationRange(reader);
        });
        registerSelectorOption("y_rotation", (reader, data) -> {
            if(data.yaw != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "y_rotation selector cannot be repeated");
            data.yaw = readRotationRange(reader);
        });
        registerSelectorOption("x", (reader, data) -> {
            if(data.x != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "x selector cannot be repeated");
            data.worldLimited = true;
            data.x = reader.readDouble();
        });
        registerSelectorOption("y", (reader, data) -> {
            if(data.y != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "y selector cannot be repeated");
            data.worldLimited = true;
            data.y = reader.readDouble();
        });
        registerSelectorOption("z", (reader, data) -> {
            if(data.z != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "z selector cannot be repeated");
            data.worldLimited = true;
            data.z = reader.readDouble();
        });
        registerSelectorOption("dx", (reader, data) -> {
            if(data.dx != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "dx selector cannot be repeated");
            data.worldLimited = true;
            data.dx = reader.readDouble();
        });
        registerSelectorOption("dy", (reader, data) -> {
            if(data.dy != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "dy selector cannot be repeated");
            data.worldLimited = true;
            data.dy = reader.readDouble();
        });
        registerSelectorOption("dz", (reader, data) -> {
            if(data.dz != null) throwError(reader, SELECTOR_OPTION_CANNOT_REPEAT, "dz selector cannot be repeated");
            data.worldLimited = true;
            data.dz = reader.readDouble();
        });
        registerSelectorOption("predicate", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var predicate = reader.readDataLocation();
            data.predicates.add(new NegatableData<>(predicate, negated));
        });
        registerSelectorOption("nbt", (reader, data) -> {
            var negated = reader.skipIfNext('!');
            var compound = NbtParser.readNbtCompound(reader);
            data.nbts.add(new NegatableData<>(compound, negated));
        });
    }


    /**
     * MinMaxBounds.FloatDegrees as used by EntitySelectorParser.createRotationPredicate: bounds default to 0 and 359,
     * both are wrapped to [-180, 180), and min > max (a range across ±180) is allowed.
     */
    private static FloatRangeArgument readRotationRange(StringCommandReader reader) {
        var bounds = reader.readFloatRange();
        if(bounds.minimum() == null && bounds.maximum() == null) throwError(reader, SELECTOR_OPTION_INVALID, "Expected a rotation range");
        float min = bounds.minimum() != null ? bounds.minimum() : 0.0F;
        float max = bounds.maximum() != null ? bounds.maximum() : 359.0F;
        return new FloatRangeArgument(wrapDegrees(min), wrapDegrees(max));
    }

    // Mth.wrapDegrees(float)
    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if(angle >= 180.0F) angle -= 360.0F;
        if(angle < -180.0F) angle += 360.0F;
        return angle;
    }

}
