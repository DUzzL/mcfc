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
                    try(var ignored = context.enterWithoutValue(selector.data().incrementedId(3).withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    selector.writeInstruction(instruction);
                }
            }
            case EXECUTE_IF_ENTITY -> {
                try(var ignored = context.forSelector(command.<SelectorArgument>get("targets").limit(1))) {
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
            case EXECUTE_POSITIONED_POS -> {
                try(var position = context.applyPosition(command.get("position"))) {
                    try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()))) {
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
        }
    }

}
