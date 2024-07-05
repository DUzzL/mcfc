package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.Command;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.AttributeInstance;
import dev.epicpix.minecraftfunctioncompiler.data.locations.Vec2;
import dev.epicpix.minecraftfunctioncompiler.data.locations.Vec3;
import dev.epicpix.minecraftfunctioncompiler.data.locations.World;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(List<Command> commands) {
        ArrayList<Instruction> instructions = new ArrayList<>();
        var context = new CompilationContext(CompilationContextData.create(instructions::add));
        for(var command : commands) {
            compile(context, command, ResultStorage.empty());
        }
        context.exit();
        return instructions;
    }

    private static void compile(CompilationContext context, Command command, ResultStorage result) {
        switch(command.type()) {
            case ATTRIBUTE_BASE_SET -> {
                try(var selector = context.forSelector(command.get("target"))) {
                    var attrLocation = LocationData.<AttributeInstance>ofLocal(selector.data().highestId());
                    var instruction =
                            Instructions.checkEntityTypes(selector.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                    .with(Instructions2.getAttributeInstance(selector.content(), attrLocation, command.<AttributeData>get("attribute").location())
                                            .with(Instructions2.ifNotNull(attrLocation.lower())
                                                    .with(Instructions2.setAttributeBase(attrLocation, command.get("value")))
                                            ));
                    selector.writeInstruction(instruction);

                }
            }
            case DIFFICULTY_SET -> {
                context.current().addInstructionConsumer().accept(Instructions2.setDifficulty(command.get("difficulty")));
            }
            case EFFECT_CLEAR -> {
                try(var self = context.getCurrentEntity()) {
                    self.writeInstruction(
                            Instructions2.ifNotNull(self.content().lower())
                                .with(Instructions.checkEntityTypes(self.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.clearAllEffects(self.content()))));
                }
            }
            case EFFECT_CLEAR_ENTITIES -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    targets.writeInstruction(
                            Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                .with(Instructions2.clearAllEffects(targets.content())));
                }
            }
            case EFFECT_CLEAR_ENTITIES_EFFECT -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    targets.writeInstruction(
                            Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                    .with(Instructions2.clearEffect(targets.content(), command.<MobEffectData>get("effect").location())));
                }
            }
            case EFFECT_GIVE -> {
                MobEffectData effect = command.get("effect");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), effect.instant() ? 1 : 600, 0, true)));
                    }
                }
            }
            case EFFECT_GIVE_DUR -> {
                MobEffectData effect = command.get("effect");
                int duration = command.get("duration");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), effect.instant() ? duration : duration * 20, 0, true)));
                    }
                }
            }
            case EFFECT_GIVE_DUR_AMP -> {
                MobEffectData effect = command.get("effect");
                int duration = command.get("duration");
                int amplifier = command.get("amplifier");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), effect.instant() ? duration : duration * 20, amplifier, true)));
                    }
                }
            }
            case EFFECT_GIVE_DUR_AMP_SHOW -> {
                MobEffectData effect = command.get("effect");
                int duration = command.get("duration");
                int amplifier = command.get("amplifier");
                boolean hideParticles = command.get("hide_particles");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), effect.instant() ? duration : duration * 20, amplifier, !hideParticles)));
                    }
                }
            }
            case EFFECT_GIVE_INF -> {
                MobEffectData effect = command.get("effect");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), -1, 0, true)));
                    }
                }
            }
            case EFFECT_GIVE_INF_AMP -> {
                MobEffectData effect = command.get("effect");
                int amplifier = command.get("amplifier");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), -1, amplifier, true)));
                    }
                }
            }
            case EFFECT_GIVE_INF_AMP_SHOW -> {
                MobEffectData effect = command.get("effect");
                int amplifier = command.get("amplifier");
                boolean hideParticles = command.get("hide_particles");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        targets.writeInstruction(
                                Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraftfunctioncompiler", "living_entity")), false)
                                        .with(Instructions2.giveEffect(self.content(), targets.content(), effect.location(), -1, amplifier, !hideParticles)));
                    }
                }
            }
            case EXECUTE_ALIGN -> {
                try(var pos = context.getPosition()) {
                    var newPosition = LocationData.<Vec3>ofLocal(pos.data().highestId());
                    var instruction = Instructions.alignPosition(pos.content(), newPosition, command.get("swizzle"));
                    try(var ignored = context.enterWithoutValue(pos.data().incrementedId(1).withCurrentPosition(newPosition).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    pos.writeInstruction(instruction);
                }
            }
            case EXECUTE_AS -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    try(var ignored = context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_AT -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    var world = LocationData.<World>ofLocal(selector.data().highestId());
                    var position = LocationData.<Vec3>ofLocal(selector.data().highestId() + 1);
                    var rotation = LocationData.<Vec2>ofLocal(selector.data().highestId() + 2);
                    var execInstruction = Instructions2.getEntityRotation(selector.content(), rotation);
                    var instruction = Instructions2.getEntityWorld(selector.content(), world).with(Instructions2.getEntityPosition(selector.content(), position).with(execInstruction));
                    try(var ignored = context.enterWithoutValue(selector.data().incrementedId(3).withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world).withBaseInstruction(execInstruction))) {
                        compile(context, command.get("command"), result);
                    }
                    selector.writeInstruction(instruction);
                }
            }
            case EXECUTE_IF_ENTITY -> {
                try(var ignored = context.forSelector(command.<SelectorArgument>get("entities").limit(1))) {
                    compile(context, command.get("command"), result);
                }
            }
            case EXECUTE_IF_SCORE_RANGE -> {
                try(var objective = context.getObjective(command.get("targetObjective"))) {
                    try(var name = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), false)) {
                            var instr = Instructions.checkInScoreRange(scoreData.content(), command.get("range"));
                            try(var ignored = context.enterWithoutValueUsingInstruction(instr)) {
                                compile(context, command.get("command"), result);
                            }
                            scoreData.writeInstruction(instr);
                        }
                    }
                }
            }
            case EXECUTE_IF_SCORE_COMPARE -> {
                try(var targetObjective = context.getObjective(command.get("targetObjective"))) {
                    try(var targetName = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                        try(var sourceObjective = context.getObjective(command.get("sourceObjective"))) {
                            try(var sourceName = context.forScoreHolder(command.<ScoreHolderArgument>get("source").selectSingle())) {
                                try(var targetScore = context.getScoreData(targetObjective.content(), targetName.content(), false)) {
                                    try(var sourceScore = context.getScoreData(sourceObjective.content(), sourceName.content(), false)) {
                                        var instr = Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), command.get("comparison"));
                                        try(var ignored = context.enterWithoutValueUsingInstruction(instr)) {
                                            compile(context, command.get("command"), result);
                                        }
                                        sourceScore.writeInstruction(instr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case EXECUTE_POSITIONED_AS -> {
                try(var targets = context.forSelector(command.<SelectorArgument>get("targets"))) {
                    var position = LocationData.<Vec3>ofLocal(targets.data().highestId());
                    var instruction = Instructions2.getEntityPosition(targets.content(), position);
                    try(var ignored = context.enterWithoutValue(targets.data().incrementedId(1).withCurrentPosition(position).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    targets.writeInstruction(instruction);
                }
            }
            case EXECUTE_POSITIONED_POS -> {
                try(var position = context.applyPosition(command.get("pos"))) {
                    try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()))) {
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_ROTATED_AS -> {
                try(var targets = context.forSelector(command.<SelectorArgument>get("targets"))) {
                    var rotation = LocationData.<Vec2>ofLocal(targets.data().highestId());
                    var instruction = Instructions2.getEntityRotation(targets.content(), rotation);
                    try(var ignored = context.enterWithoutValue(targets.data().incrementedId(1).withCurrentRotation(rotation).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    targets.writeInstruction(instruction);
                }
            }
            case EXECUTE_ROTATED_ROT -> {
                try(var rotation = context.applyRotation(command.get("rot"))) {
                    try(var ignored = context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_RUN -> {
                compile(context, command.get("command"), result);
            }
            case EXECUTE_STORE_RESULT_SCORE -> {
                try(var res = context.withResultStorage(true, false)) {
                    compile(context, command.get("command"), res.content());
                    res.content().checkRequirements(command.get("command"));
                    try(var objective = context.getObjective(command.get("objective"))) {
                        try(var name = context.forScoreHolder(command.get("targets"))) {
                            try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                                scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), res.content().result));
                            }
                        }
                    }
                }
            }
            case EXECUTE_UNLESS_ENTITY -> {
                try(var scope = context.forScope()) {
                    try(var selector = context.forSelector(command.<SelectorArgument>get("entities").limit(1))) {
                        selector.writeInstruction(Instructions2.exitScope(scope.content()));
                    }
                    compile(context, command.get("command"), result);
                }
            }
            case EXECUTE_UNLESS_SCORE_RANGE -> {
                try(var objective = context.getObjective(command.get("targetObjective"))) {
                    try(var name = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), false)) {
                            var instr = Instructions.checkNotInScoreRange(scoreData.content(), command.get("range"));
                            try(var ignored = context.enterWithoutValueUsingInstruction(instr)) {
                                compile(context, command.get("command"), result);
                            }
                            scoreData.writeInstruction(instr);
                        }
                    }
                }
            }
            case EXECUTE_UNLESS_SCORE_COMPARE -> {
                try(var targetObjective = context.getObjective(command.get("targetObjective"))) {
                    try(var targetName = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                        try(var sourceObjective = context.getObjective(command.get("sourceObjective"))) {
                            try(var sourceName = context.forScoreHolder(command.<ScoreHolderArgument>get("source").selectSingle())) {
                                try(var targetScore = context.getScoreData(targetObjective.content(), targetName.content(), false)) {
                                    try(var sourceScore = context.getScoreData(sourceObjective.content(), sourceName.content(), false)) {
                                        var instr = Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), command.<ScoreboardCompareOperation>get("comparison").negate());
                                        try(var ignored = context.enterWithoutValueUsingInstruction(instr)) {
                                            compile(context, command.get("command"), result);
                                        }
                                        sourceScore.writeInstruction(instr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case EXPERIENCE_ADD, EXPERIENCE_ADD_POINTS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    targets.writeInstruction(
                            Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraft", "player")), false)
                                    .with(Instructions2.addExperiencePoints(targets.content(), command.get("amount"))));
                }
            }
            case EXPERIENCE_ADD_LEVELS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    targets.writeInstruction(
                            Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraft", "player")), false)
                                    .with(Instructions2.addExperienceLevels(targets.content(), command.get("amount"))));
                }
            }
            case EXPERIENCE_SET, EXPERIENCE_SET_POINTS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    targets.writeInstruction(
                            Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraft", "player")), false)
                                    .with(Instructions2.setExperiencePoints(targets.content(), command.get("amount"))));
                }
            }
            case EXPERIENCE_SET_LEVELS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    targets.writeInstruction(
                            Instructions.checkEntityTypes(targets.content(), List.of(new DataLocation("minecraft", "player")), false)
                                    .with(Instructions2.setExperienceLevels(targets.content(), command.get("amount"))));
                }
            }
            case FUNCTION -> {
                var current = context.current();
                current.addInstructionConsumer().accept(Instructions.callFunctions(command.get("functions"), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation()));
            }
            case GAMEMODE -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    selector.writeInstruction(Instructions2.setGamemode(selector.content(), command.get("gamemode")));
                }
            }
            case KILL -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    selector.writeInstruction(Instructions2.killEntity(selector.content()));
                }
            }
            case SCOREBOARD_PLAYERS_ADD -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            scoreData.writeInstruction(Instructions2.addScore(scoreData.content(), LocationData.ofInteger(command.get("score"))));
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_GET -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            var res = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                            var instr = Instructions2.getScoreValue(scoreData.content(), res);
                            result.setResult(scoreData.data().incrementedId(1).withBaseInstruction(instr), res);
                            scoreData.writeInstruction(instr);
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_OPERATION -> {
                try(var exceptionScope = context.forScope()) {
                    try(var targetObjective = context.getObjective(command.get("targetObjective"))) {
                        try(var targetName = context.forScoreHolder(command.get("targets"))) {
                            try(var sourceObjective = context.getObjective(command.get("sourceObjective"))) {
                                try(var sourceName = context.forScoreHolder(command.get("source"))) {
                                    try(var targetScore = context.getScoreData(targetObjective.content(), targetName.content(), false)) {
                                        try(var sourceScore = context.getScoreData(sourceObjective.content(), sourceName.content(), false)) {
                                            switch(command.<ScoreboardOperation>get("operation")) {
                                                case SET -> sourceScore.writeInstruction(Instructions2.runScoreOperationSet(targetScore.content(), sourceScore.content()));
                                                case ADD -> sourceScore.writeInstruction(Instructions2.runScoreOperationPlus(targetScore.content(), sourceScore.content()));
                                                case SUB -> sourceScore.writeInstruction(Instructions2.runScoreOperationMinus(targetScore.content(), sourceScore.content()));
                                                case MUL -> sourceScore.writeInstruction(Instructions2.runScoreOperationMultiply(targetScore.content(), sourceScore.content()));
                                                case DIV -> sourceScore.writeInstruction(Instructions2.runScoreOperationDivide(targetScore.content(), sourceScore.content(), exceptionScope.content()));
                                                case MOD -> sourceScore.writeInstruction(Instructions2.runScoreOperationModulo(targetScore.content(), sourceScore.content(), exceptionScope.content()));
                                                case MIN -> sourceScore.writeInstruction(Instructions2.runScoreOperationMin(targetScore.content(), sourceScore.content()));
                                                case MAX -> sourceScore.writeInstruction(Instructions2.runScoreOperationMax(targetScore.content(), sourceScore.content()));
                                                case SWAP -> sourceScore.writeInstruction(Instructions2.runScoreOperationSwap(targetScore.content(), sourceScore.content()));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_REMOVE -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            scoreData.writeInstruction(Instructions2.removeScore(scoreData.content(), LocationData.ofInteger(command.get("score"))));
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_RESET_ALL -> {
                try(var name = context.forScoreHolder(command.get("targets"))) {
                    name.writeInstruction(Instructions2.resetPlayerScore(name.content()));
                }
            }
            case SCOREBOARD_PLAYERS_RESET_OBJECTIVE -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        name.writeInstruction(Instructions2.resetScore(objective.content(), name.content()));
                    }
                }
            }
            case SCOREBOARD_PLAYERS_SET -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), LocationData.ofInteger(command.get("score"))));
                        }
                    }
                }
            }
            case TAG_ADD -> {
                try(var entity = context.forSelector(command.get("targets"))) {
                    entity.writeInstruction(Instructions2.addEntityTag(entity.content(), command.get("name")));
                }
            }
            case TAG_REMOVE -> {
                try(var entity = context.forSelector(command.get("targets"))) {
                    entity.writeInstruction(Instructions2.removeEntityTag(entity.content(), command.get("name")));
                }
            }
            case WEATHER_CLEAR_TIMED -> {
                context.current().addInstructionConsumer().accept(Instructions2.setWeatherStatic(command.get("duration"), 0, false, false));
            }
            case WEATHER_CLEAR -> {
                context.current().addInstructionConsumer().accept(Instructions2.setWeatherDynamicClear());
            }
            case WEATHER_RAIN_TIMED -> {
                context.current().addInstructionConsumer().accept(Instructions2.setWeatherStatic(0, command.get("duration"), true, false));
            }
            case WEATHER_RAIN -> {
                context.current().addInstructionConsumer().accept(Instructions2.setWeatherDynamicRain());
            }
            case WEATHER_THUNDER_TIMED -> {
                context.current().addInstructionConsumer().accept(Instructions2.setWeatherStatic(0, command.get("duration"), true, true));
            }
            case WEATHER_THUNDER -> {
                context.current().addInstructionConsumer().accept(Instructions2.setWeatherDynamicThunder());
            }
        }
    }

}
