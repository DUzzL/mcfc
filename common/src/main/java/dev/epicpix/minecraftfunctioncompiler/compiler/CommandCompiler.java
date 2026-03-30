package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementGrantEverything;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementGrantFrom;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementGrantOnly;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementGrantOnlyCriterion;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementGrantThrough;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementGrantUntil;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementRevokeEverything;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementRevokeFrom;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementRevokeOnly;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementRevokeOnlyCriterion;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementRevokeThrough;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandAdvancementRevokeUntil;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandDataModifySetValue;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandDifficultySet;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteAlign;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteAnchored;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteAs;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteAt;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteIfEntity;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteIfFunction;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteIfScoreCompare;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteIfScoreRange;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteOnOrigin;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteOnTarget;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteOnVehicle;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecutePositionedAs;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecutePositionedPos;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteRotatedAs;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteRotatedRot;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteRun;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteStoreScore;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteStoreStorage;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteUnlessEntity;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteUnlessFunction;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteUnlessScoreCompare;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExecuteUnlessScoreRange;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExperienceAdd;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExperienceAddLevels;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExperienceAddPoints;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExperienceSet;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExperienceSetLevels;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandExperienceSetPoints;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFunction;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFunctionArgs;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFunctionArgsNbt;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandFunctionArgsNbtPath;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandGamemode;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandKill;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandReturnFail;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandReturnRun;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandReturnValue;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersAdd;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersEnable;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersGet;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersOperation;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersRemove;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersResetAll;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersResetObjective;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandScoreboardPlayersSet;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTagAdd;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTagRemove;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTellraw;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTitleActionbar;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTitleClear;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTitleReset;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTitleSubtitle;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTitleTimes;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandTitleTitle;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandWeatherClear;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandWeatherClearTimed;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandWeatherRain;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandWeatherRainTimed;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandWeatherThunder;
import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandWeatherThunderTimed;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.Difficulty;
import dev.epicpix.minecraftfunctioncompiler.data.Gamemode;
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor;
import dev.epicpix.minecraftfunctioncompiler.data.NbtDataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.ResultStoreLocationType;
import dev.epicpix.minecraftfunctioncompiler.data.ResultStoreType;
import dev.epicpix.minecraftfunctioncompiler.data.RotationCoordinates;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreAccessorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardCompareOperation;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreboardOperation;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument;
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.ObjectComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.SetRandomizedWeatherData;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCommandSourceStack;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TComponent;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCompoundTag;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TDifficulty;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TEntity;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TGameType;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TListTag;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TLivingEntity;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TMutableComponent;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjectiveCriteria;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TServerLevel;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TServerPlayer;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TStyle;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TTag;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TTargeting;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TTraceableEntity;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TVec2;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TVec3;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNREACHABLE_CODE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_MODIFY_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_STORE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_TEXT;

public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(DataLocation source, List<CommandParams> commands) {
        var context = new CompilationContext(source);
        for(var command : commands) {
            var commandLabel = context.current().writeLabel(context.instructionWriter);
            try(var ignored = context.enterWithoutValue(context.current().withContextLabel(commandLabel))) {
                try(var resultContext = ResultContext.create(context, false, false)) {
                    compile(context, null, command, resultContext);
                }
            }
        }
        return context.end();
    }

    private static void compile(CompilationContext context, @Nullable ForkingContext forkingContext, CommandParams inputCommand, ResultContext result) {
        switch(inputCommand) {
            case CommandDataModifySetValue(NbtDataLocation destination, NbtPath path, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    var storageLocal = context.<TCompoundTag>createLocal();
                    var setCountLocal = context.<Integer>createLocal();

                    context.writeInstruction(Instructions.getNbtStorage(storageLocal, storage));
                    context.writeInstruction(Instructions.dup(setCountLocal, LocationData.ofInteger(0)));

                    var parts = path.parts();

                    var codeLocation = context.current().writeLabel(context.instructionWriter);
                    try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                        var value = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, innerContext.data().contextEndLabel());

                        var last = parts.getLast();
                        var tagLocal = context.<TTag>createLocal();
                        var tagCopiedLocal = context.<TTag>createLocal();
                        innerContext.writeInstruction(Instructions.loadNbtValue(tagLocal, setValue));
                        innerContext.writeInstruction(Instructions.cloneNbtValue(tagCopiedLocal, tagLocal));
                        assignNbtValue(context, value, last, tagCopiedLocal, setCountLocal, innerContext.data().contextEndLabel());
                    }

                    context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
                    context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
                }else {
                    throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_MODIFY_ACCESS, context.getSource(), "Modifying nbt in block/entity is not implemented yet"));
                }
            }
            case CommandDifficultySet(Difficulty difficulty) -> {
                var difficultyLocation = context.<TDifficulty>createLocal();
                context.writeInstruction(Instructions.mapDifficulty(difficultyLocation, difficulty));
                context.writeInstruction(Instructions.setDifficulty(difficultyLocation));
            }
            case CommandExecuteAlign(SwizzleArgument swizzle, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var pos = context.getPosition()) {
                        var newPosition = context.<TVec3>createLocal();
                        pos.writeInstruction(Instructions.alignPosition(newPosition, pos.content(), swizzle));
                        try(var ignored = context.enterWithoutValue(pos.data().withCurrentPosition(newPosition))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteAnchored(LocationAnchor anchor, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var ignored = context.enterWithoutValue(context.current().withCurrentAnchor(anchor))) {
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteAs(SelectorArgument targets, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var ignored = context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteAt(SelectorArgument targets, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var selector = context.forSelector(targets, result)) {
                        var world = context.<TServerLevel>createLocal();
                        var position = context.<TVec3>createLocal();
                        var rotation = context.<TVec2>createLocal();
                        selector.writeInstruction(Instructions.getEntityWorld(world, selector.content()));
                        selector.writeInstruction(Instructions.getEntityPosition(position, selector.content()));
                        selector.writeInstruction(Instructions.getEntityRotation(rotation, selector.content()));
                        try(var ignored = context.enterWithoutValue(selector.data().withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteIfEntity(SelectorArgument entities, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var ignored = context.forSelector(entities.limit(1), result)) {
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteIfFunction(List<DataLocation> functions, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var current = context.current();
                    var stack = context.<TCommandSourceStack>createLocal();
                    context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions2.callFunctionsWithResult(returnValue, functions, stack, null));
                    context.writeInstruction(Instructions.jumpIfNullReturnValue(returnValue, fContext.endLabel()));
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteIfScoreRange(ScoreAccessorArgument target, IntRangeArgument range, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), result)) {
                        try(var scoreData = context.getScoreData(scoreAccessor.content(), result.markAndGetErrorLabel(), false)) {
                            var scoreValue = context.<Integer>createLocal();
                            scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                            context.jumpIfNotMatchesRange(scoreData.data(), scoreValue, range, scoreData.data().contextEndLabel());
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteIfScoreCompare(ScoreAccessorArgument targetArg, ScoreboardCompareOperation comparison, ScoreAccessorArgument sourceArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var target = context.forScoreAccessors(targetArg.selectSingle(), result)) {
                        try(var source = context.forScoreAccessors(sourceArg.selectSingle(), result)) {
                            try(var targetScore = context.getScoreData(target.content(), result.markAndGetErrorLabel(), false)) {
                                try(var sourceScore = context.getScoreData(source.content(), result.markAndGetErrorLabel(), false)) {
                                    var targetValue = context.<Integer>createLocal();
                                    var sourceValue = context.<Integer>createLocal();
                                    sourceScore.writeInstruction(Instructions.getScoreValue(targetValue, targetScore.content()));
                                    sourceScore.writeInstruction(Instructions.getScoreValue(sourceValue, sourceScore.content()));
                                    sourceScore.writeInstruction(Instructions.ifNumberCompareJump(comparison.toNumberComparisonJump(), targetValue, sourceValue, sourceScore.data().contextEndLabel()));
                                    compile(context, fContext.content(), command, result);
                                }
                            }
                        }
                    }
                }
            }
            case CommandExecuteOnOrigin(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var traceableEntity = context.<TTraceableEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.tryAsTraceableEntity(traceableEntity, currentEntity.content(), currentEntity.endLabel()));
                        var origin = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityOrigin(origin, traceableEntity));
                        currentEntity.writeInstruction(Instructions.ifNullJump(origin, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(origin))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnTarget(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var targetingEntity = context.<TTargeting>createLocal();
                        currentEntity.writeInstruction(Instructions.tryAsTargetingEntity(targetingEntity, currentEntity.content(), currentEntity.endLabel()));
                        var target = context.<TLivingEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityTarget(target, targetingEntity));
                        currentEntity.writeInstruction(Instructions.ifNullJump(target, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(target))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnVehicle(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var vehicle = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityVehicle(vehicle, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(vehicle, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(vehicle))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecutePositionedAs(SelectorArgument targetsArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var targets = context.forSelector(targetsArg, result)) {
                        var position = context.<TVec3>createLocal();
                        targets.writeInstruction(Instructions.getEntityPosition(position, targets.content()));
                        try(var ignored = context.enterWithoutValue(targets.data().withCurrentPosition(position))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecutePositionedPos(WorldCoordinates pos, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var position = context.applyPosition(pos)) {
                        try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()).withCurrentAnchor(LocationAnchor.FEET))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteRotatedAs(SelectorArgument targetsArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var targets = context.forSelector(targetsArg, result)) {
                        var rotation = context.<TVec2>createLocal();
                        targets.writeInstruction(Instructions.getEntityRotation(rotation, targets.content()));
                        try(var ignored = context.enterWithoutValue(targets.data().withCurrentRotation(rotation))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteRotatedRot(RotationCoordinates rot, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var rotation = context.applyRotation(rot)) {
                        try(var ignored = context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteRun(CommandParams command) -> {
                try(var ignored = context.generateForkExecCode(forkingContext)) {
                    compile(context, forkingContext, command, result);
                }
            }
            case CommandExecuteStoreScore(ResultStoreLocationType storeLocation, ScoreAccessorArgument targets, CommandParams command) -> {
                try(var ignored = context.generateForkExecCode(forkingContext)) {
                    var localResult = ResultContext.create(context, storeLocation == ResultStoreLocationType.SUCCESS, storeLocation == ResultStoreLocationType.RESULT);
                    compile(context, null, command, localResult);
                    localResult.endGeneration();
                    localResult.checkImplemented(command);
                    var localResultValue = storeLocation == ResultStoreLocationType.SUCCESS ? localResult.getSuccess() : localResult.getResult();
                    try(var scoreAccessor = context.forScoreAccessors(targets, result)) {
                        try(var scoreData = context.getScoreDataAutoCreate(scoreAccessor.content())) {
                            scoreData.writeInstruction(Instructions.setScore(scoreData.content(), localResultValue));
                        }
                    }
                }
            }
            case CommandExecuteStoreStorage(ResultStoreLocationType storeLocation, DataLocation storage, NbtPath path, ResultStoreType storeType, double scale, CommandParams command) -> {
                try(var ignored = context.generateForkExecCode(forkingContext)) {
                    var localResult = ResultContext.create(context, storeLocation == ResultStoreLocationType.SUCCESS, storeLocation == ResultStoreLocationType.RESULT);
                    compile(context, null, command, localResult);
                    localResult.endGeneration();
                    localResult.checkImplemented(command);
                    var localResultValue = storeLocation == ResultStoreLocationType.SUCCESS ? localResult.getSuccess() : localResult.getResult();

                    var storageLocal = context.<TCompoundTag>createLocal();
                    var setCountLocal = context.<Integer>createLocal();

                    context.writeInstruction(Instructions.getNbtStorage(storageLocal, storage));
                    context.writeInstruction(Instructions.dup(setCountLocal, LocationData.ofInteger(0)));

                    var parts = path.parts();

                    try(var innerContext = context.enterLocalScope()) {
                        var value = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, innerContext.endLabel());

                        var last = parts.getLast();
                        var tagLocal = context.<TTag>createLocal();
                        if(storeType == ResultStoreType.INT) {
                            if(scale == 1.0d) {
                                innerContext.writeInstruction(Instructions.createIntTag(tagLocal, localResultValue));
                            }else {
                                var resultDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.numberToDouble(resultDouble, localResultValue));
                                var multipliedDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(scale)));
                                var convertedInteger = context.<Integer>createLocal();
                                innerContext.writeInstruction(Instructions.numberToInteger(convertedInteger, multipliedDouble));
                                innerContext.writeInstruction(Instructions.createIntTag(tagLocal, convertedInteger));
                            }
                        }else if(storeType == ResultStoreType.DOUBLE) {
                            var convertedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions.numberToDouble(convertedDouble, localResultValue));
                            if(scale == 1.0d) {
                                innerContext.writeInstruction(Instructions.createDoubleTag(tagLocal, convertedDouble));
                            }else {
                                var multipliedDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, convertedDouble, LocationData.ofDouble(scale)));
                                innerContext.writeInstruction(Instructions.createDoubleTag(tagLocal, multipliedDouble));
                            }
                        }else if(storeType == ResultStoreType.FLOAT) {
                            if(scale == 1.0d) {
                                var convertedFloat = context.<Float>createLocal();
                                innerContext.writeInstruction(Instructions.numberToFloat(convertedFloat, localResultValue));
                                innerContext.writeInstruction(Instructions.createFloatTag(tagLocal, convertedFloat));
                            }else {
                                var resultDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.numberToDouble(resultDouble, localResultValue));
                                var multipliedDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(scale)));
                                var convertedFloat = context.<Float>createLocal();
                                innerContext.writeInstruction(Instructions.numberToFloat(convertedFloat, multipliedDouble));
                                innerContext.writeInstruction(Instructions.createFloatTag(tagLocal, convertedFloat));
                            }
                        }else {
                            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_STORE, context.getSource(), "Storing into storage with a non-int/non-double/non-float type is not yet implemented"));
                        }
                        assignNbtValue(context, value, last, tagLocal, setCountLocal, innerContext.endLabel());
                    }
                    context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
                    context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
                }
            }
            case CommandExecuteUnlessEntity(SelectorArgument entities, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var contextLabel = context.current().contextEndLabel();
                    var codeLocation = context.current().writeLabel(context.instructionWriter);
                    try(var ignored = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                        try(var selector = context.forSelector(entities.limit(1), result)) {
                            selector.writeInstruction(Instructions.jump(contextLabel));
                        }
                    }
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteUnlessFunction(List<DataLocation> functions, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var current = context.current();
                    var stack = context.<TCommandSourceStack>createLocal();
                    context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions2.callFunctionsWithResult(returnValue, functions, stack, null));
                    context.writeInstruction(Instructions.jumpIfNotNullReturnValue(returnValue, fContext.endLabel()));
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteUnlessScoreRange(ScoreAccessorArgument target, IntRangeArgument range, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var contextLabel = context.current().contextEndLabel();
                    try(var scope = context.enterLocalScope()) {
                        var scopeResult = ResultContext.create(context, scope.endLabel());
                        try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), scopeResult)) {
                            try(var scoreData = context.getScoreData(scoreAccessor.content(), scopeResult.markAndGetErrorLabel(), false)) {
                                var scoreValue = context.<Integer>createLocal();
                                scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                                context.jumpIfMatchesRange(scoreData.data(), scoreValue, range, contextLabel);
                            }
                        }
                        scopeResult.close();
                    }
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteUnlessScoreCompare(ScoreAccessorArgument targetArg, ScoreboardCompareOperation comparison, ScoreAccessorArgument sourceArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var contextLabel = context.current().contextEndLabel();
                    try(var scope = context.enterLocalScope()) {
                        var scopeResult = ResultContext.create(context, scope.endLabel());
                        try(var target = context.forScoreAccessors(targetArg.selectSingle(), scopeResult)) {
                            try(var source = context.forScoreAccessors(sourceArg.selectSingle(), scopeResult)) {
                                try(var targetScore = context.getScoreData(target.content(), scopeResult.markAndGetErrorLabel(), false)) {
                                    try(var sourceScore = context.getScoreData(source.content(), scopeResult.markAndGetErrorLabel(), false)) {
                                        var targetValue = context.<Integer>createLocal();
                                        var sourceValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(targetValue, targetScore.content()));
                                        sourceScore.writeInstruction(Instructions.getScoreValue(sourceValue, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions.ifNumberCompareJump(comparison.negate().toNumberComparisonJump(), targetValue, sourceValue, contextLabel));
                                    }
                                }
                            }
                        }
                        scopeResult.close();
                    }
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExperienceAdd(SelectorArgument targetsArg, MacroTemplate<Integer> amount) -> {
                try(var targets = context.forSelector(targetsArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions.addExperiencePoints(playerLocation, context.getResolvedIntegerMacro(amount)));
                }
            }
            case CommandExperienceAddPoints(SelectorArgument targetsArg, MacroTemplate<Integer> amount) -> {
                try(var targets = context.forSelector(targetsArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions.addExperiencePoints(playerLocation, context.getResolvedIntegerMacro(amount)));
                }
            }
            case CommandExperienceAddLevels(SelectorArgument targetsArg, MacroTemplate<Integer> amount) -> {
                try(var targets = context.forSelector(targetsArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions.addExperienceLevels(playerLocation, context.getResolvedIntegerMacro(amount)));
                }
            }
            case CommandExperienceSet(SelectorArgument targets1, MacroTemplate<Integer> amount) -> {
                try(var targets = context.forSelector(targets1, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions.setExperiencePoints(playerLocation, context.getResolvedIntegerMacro(amount)));
                }
            }
            case CommandExperienceSetPoints(SelectorArgument targetsArg, MacroTemplate<Integer> amount) -> {
                try(var targets = context.forSelector(targetsArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions.setExperiencePoints(playerLocation, context.getResolvedIntegerMacro(amount)));
                }
            }
            case CommandExperienceSetLevels(SelectorArgument targetsArg, MacroTemplate<Integer> amount) -> {
                try(var targets = context.forSelector(targetsArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions.setExperienceLevels(playerLocation, context.getResolvedIntegerMacro(amount)));
                }
            }
            case CommandFunction(List<DataLocation> functions) -> {
                result.markImplemented();
                var current = context.current();
                var stack = context.<TCommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                callFunctionsAndProvideResult(context, result, functions, stack, null);
            }
            case CommandFunctionArgs(List<DataLocation> functions, NbtValue.Compound argumentsArg) -> {
                result.markImplemented();
                var current = context.current();
                var arguments = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.loadNbtValue(arguments.forceCast(), argumentsArg));
                var stack = context.<TCommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                callFunctionsAndProvideResult(context, result, functions, stack, arguments);
            }
            case CommandFunctionArgsNbt(List<DataLocation> functions, NbtDataLocation argumentsArg) -> {
                result.markImplemented();
                var current = context.current();
                forLocatedBaseNbtData(context, result, argumentsArg, arguments -> {
                    var clonedArguments = context.<TTag>createLocal();
                    var validArguments = context.<TCompoundTag>createLocal();
                    context.writeInstruction(Instructions.cloneNbtValue(clonedArguments, arguments));
                    context.writeInstruction(Instructions.tryAsNbtCompound(validArguments, clonedArguments, result.markAndGetErrorLabel()));
                    var stack = context.<TCommandSourceStack>createLocal();
                    context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                    callFunctionsAndProvideResult(context, result, functions, stack, validArguments);
                });
            }
            case CommandFunctionArgsNbtPath(List<DataLocation> functions, NbtDataLocation arguments, NbtPath path) -> {
                result.markImplemented();
                var current = context.current();
                forLocatedBaseNbtData(context, result, arguments, storageLocal -> {
                    var parts = path.parts();
                    var value = getNbtPathValue(context, storageLocal, parts, false, result.markAndGetErrorLabel());
                    context.writeInstruction(Instructions.ifNullJump(value, result.markAndGetErrorLabel()));
                    var clonedTag = context.<TTag>createLocal();
                    context.writeInstruction(Instructions.cloneNbtValue(clonedTag, value));
                    var compoundTag = context.<TCompoundTag>createLocal();
                    context.writeInstruction(Instructions.tryAsNbtCompound(compoundTag, clonedTag, result.markAndGetErrorLabel()));
                    var stack = context.<TCommandSourceStack>createLocal();
                    context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                    callFunctionsAndProvideResult(context, result, functions, stack, compoundTag);
                });
            }
            case CommandGamemode(Gamemode gamemode, SelectorArgument targets) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    var mappedGamemode = context.<TGameType>createLocal();
                    selector.writeInstruction(Instructions.mapGamemode(mappedGamemode, gamemode));
                    selector.writeInstruction(Instructions.setGamemode(playerLocation, mappedGamemode));
                }
            }
            case CommandKill(SelectorArgument targets) -> {
                try(var selector = context.forSelector(targets, result)) {
                    try(var world = context.getWorld()) {
                        selector.writeInstruction(Instructions.killEntity(selector.content(), world.content()));
                    }
                }
            }
            case CommandReturnFail _ -> context.writeInstruction(Instructions.returnFail());
            case CommandReturnRun(CommandParams command) -> {
                var current = context.current();

                var localResult = ResultContext.create(context, true, true);
                compile(context, null, command, localResult);
                localResult.endGeneration();
                localResult.checkImplemented(command);

                var jumpLabel = current.writeLabel(context.instructionWriter);
                context.instructionWriter.addInstructionBefore(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, localResult.getSuccess(), LocationData.ofInteger(0), jumpLabel), jumpLabel);
                context.instructionWriter.addInstructionBefore(Instructions.returnSuccess(localResult.getResult()), jumpLabel);
                current.writeInstruction(context.instructionWriter, Instructions.returnFail());

            }
            case CommandReturnValue(int value) -> context.writeInstruction(Instructions.returnSuccess(LocationData.ofInteger(value)));
            case CommandScoreboardPlayersAdd(ScoreAccessorArgument targetsArg, MacroTemplate<Integer> score) -> {
                try(var targets = context.forScoreAccessors(targetsArg, result)) {
                    try(var scoreData = context.getScoreDataAutoCreate(targets.content())) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                        var updatedScoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.valueAdd(updatedScoreValue, scoreValue, context.getResolvedIntegerMacro(score)));
                        scoreData.writeInstruction(Instructions.setScore(scoreData.content(), updatedScoreValue));
                    }
                }
            }
            case CommandScoreboardPlayersEnable(ScoreAccessorArgument targets1) -> {
                try(var objective = context.getObjective(targets1.objective(), result)) {
                    var criterion = context.<TObjectiveCriteria>createLocal();
                    objective.writeInstruction(Instructions.getObjectiveCriterion(criterion, objective.content()));
                    var mappedCriterion = context.<TObjectiveCriteria>createLocal();
                    objective.writeInstruction(Instructions.mapObjectiveCriterion(mappedCriterion, DataRegistries.SCOREBOARD_CRITERIA.get("trigger").orElseThrow()));
                    objective.writeInstruction(Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_IDENTITY_EQUAL, criterion, mappedCriterion, objective.data().contextEndLabel()));
                    try(var name = context.forScoreHolder(targets1.scoreHolder(), result)) {
                        try(var scoreData = context.getScoreDataAutoCreate(new ScoreAccessor(objective.content(), name.content()))) {
                            scoreData.writeInstruction(Instructions.scoreUnlock(scoreData.content()));
                        }
                    }
                }
            }
            case CommandScoreboardPlayersGet(ScoreAccessorArgument targets1) -> {
                result.markImplemented();
                try(var targets = context.forScoreAccessors(targets1, result)) {
                    try(var scoreData = context.getScoreData(targets.content(), result.markAndGetErrorLabel(), false)) {
                        var res = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.getScoreValue(res, scoreData.content()));
                        result.updateResultValue(res);
                    }
                }
            }
            case CommandScoreboardPlayersOperation(ScoreAccessorArgument targetsArg, ScoreboardOperation operation, ScoreAccessorArgument sourceArg) -> {
                result.markImplemented();
                try(var targets = context.forScoreAccessors(targetsArg, result)) {
                    try(var source = context.forScoreAccessors(sourceArg, result)) {
                        try(var targetScore = context.getScoreDataAutoCreate(targets.content())) {
                            try(var sourceScore = context.getScoreDataAutoCreate(source.content())) {
                                switch(operation) {
                                    case SET -> {
                                        var scoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValue, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), scoreValue));
                                        result.updateResultValue(scoreValue);
                                    }
                                    case ADD -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.valueAdd(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case SUB -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.valueSub(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case MUL -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.valueMul(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case DIV -> {
                                        var divisor = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(divisor, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, divisor, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
                                        var dividend = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(dividend, targetScore.content()));

                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.intFloorDiv(updatedScoreValue, dividend, divisor));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case MOD -> {
                                        var divisor = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(divisor, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, divisor, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
                                        var dividend = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(dividend, targetScore.content()));

                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.intFloorMod(updatedScoreValue, dividend, divisor));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case MIN -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.intMin(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case MAX -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.intMax(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), updatedScoreValue));
                                        result.updateResultValue(updatedScoreValue);
                                    }
                                    case SWAP -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(scoreValueB, targetScore.content()));

                                        sourceScore.writeInstruction(Instructions.setScore(targetScore.content(), scoreValueA));
                                        sourceScore.writeInstruction(Instructions.setScore(sourceScore.content(), scoreValueB));
                                        result.updateResultValue(scoreValueA);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case CommandScoreboardPlayersRemove(ScoreAccessorArgument targetsArg, MacroTemplate<Integer> score) -> {
                try(var targets = context.forScoreAccessors(targetsArg, result)) {
                    try(var scoreData = context.getScoreDataAutoCreate(targets.content())) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                        var updatedScoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.valueSub(updatedScoreValue, scoreValue, context.getResolvedIntegerMacro(score)));
                        scoreData.writeInstruction(Instructions.setScore(scoreData.content(), updatedScoreValue));
                    }
                }
            }
            case CommandScoreboardPlayersResetAll(ScoreHolderArgument targetsArg) -> {
                try(var name = context.forScoreHolder(targetsArg, result)) {
                    name.writeInstruction(Instructions.resetPlayerScore(name.content()));
                }
            }
            case CommandScoreboardPlayersResetObjective(ScoreAccessorArgument targetsArg) -> {
                try(var objective = context.getObjective(targetsArg.objective(), result)) {
                    try(var name = context.forScoreHolder(targetsArg.scoreHolder(), result)) {
                        name.writeInstruction(Instructions.resetScore(objective.content(), name.content()));
                    }
                }
            }
            case CommandScoreboardPlayersSet(ScoreAccessorArgument targetsArg, MacroTemplate<Integer> score) -> {
                try(var targets = context.forScoreAccessors(targetsArg, result)) {
                    try(var scoreData = context.getScoreDataAutoCreate(targets.content())) {
                        scoreData.writeInstruction(Instructions.setScore(scoreData.content(), context.getResolvedIntegerMacro(score)));
                    }
                }
            }
            case CommandTagAdd(SelectorArgument targets, String name) -> {
                try(var entity = context.forSelector(targets, result)) {
                    entity.writeInstruction(Instructions.addEntityTag(entity.content(), LocationData.ofString(name)));
                }
            }
            case CommandTagRemove(SelectorArgument targets, String name) -> {
                try(var entity = context.forSelector(targets, result)) {
                    entity.writeInstruction(Instructions.removeEntityTag(entity.content(), LocationData.ofString(name)));
                }
            }
            case CommandTitleActionbar(SelectorArgument playersArg, Text text) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    var textLocation = loadText(context, text);
                    players.writeInstruction(Instructions.sendActionbar(playerLocation, textLocation));
                }
            }
            case CommandTitleClear(SelectorArgument playersArg) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.sendTitlesClear(playerLocation));
                }
            }
            case CommandTitleReset(SelectorArgument playersArg) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.sendTitlesReset(playerLocation));
                }
            }
            case CommandTitleSubtitle(SelectorArgument playersArg, Text text) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    var textLocation = loadText(context, text);
                    players.writeInstruction(Instructions.sendSubtitle(playerLocation, textLocation));
                }
            }
            case CommandTitleTimes(SelectorArgument playersArg, int fadeIn, int stay, int fadeOut) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.sendTitlesTimes(playerLocation, LocationData.ofInteger(fadeIn), LocationData.ofInteger(stay), LocationData.ofInteger(fadeOut)));
                }
            }
            case CommandTitleTitle(SelectorArgument playersArg, Text text) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    var textLocation = loadText(context, text);
                    players.writeInstruction(Instructions.sendTitle(playerLocation, textLocation));
                }
            }
            case CommandTellraw(SelectorArgument playersArg, Text text) -> {
                try(var players = context.forSelector(playersArg, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    var textLocation = loadText(context, text);
                    players.writeInstruction(Instructions.sendSystemMessage(playerLocation, textLocation));
                }
            }
            case CommandWeatherClearTimed(int duration) -> context.writeInstruction(Instructions.setWeatherStatic(LocationData.ofInteger(duration), LocationData.ofInteger(0), false, false));
            case CommandWeatherClear _ -> context.writeInstruction(Instructions.setWeatherDynamic(SetRandomizedWeatherData.CLEAR));
            case CommandWeatherRainTimed(int duration) -> context.writeInstruction(Instructions.setWeatherStatic(LocationData.ofInteger(0), LocationData.ofInteger(duration), true, false));
            case CommandWeatherRain _ -> context.writeInstruction(Instructions.setWeatherDynamic(SetRandomizedWeatherData.RAIN));
            case CommandWeatherThunderTimed(int duration) -> context.writeInstruction(Instructions.setWeatherStatic(LocationData.ofInteger(0), LocationData.ofInteger(duration), true, true));
            case CommandWeatherThunder _ -> context.writeInstruction(Instructions.setWeatherDynamic(SetRandomizedWeatherData.THUNDER));
            case CommandAdvancementGrantOnly(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.grantAdvancementOnly(playerLocation, LocationData.ofString(advancement.toString())));
                }
            }
            case CommandAdvancementGrantOnlyCriterion(SelectorArgument targets, DataLocation advancement, String criterion) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.grantAdvancementOnlyCriterion(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString(criterion)));
                }
            }
            case CommandAdvancementRevokeOnly(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.revokeAdvancementOnly(playerLocation, LocationData.ofString(advancement.toString())));
                }
            }
            case CommandAdvancementRevokeOnlyCriterion(SelectorArgument targets, DataLocation advancement, String criterion) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.revokeAdvancementOnlyCriterion(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString(criterion)));
                }
            }
            case CommandAdvancementGrantEverything(SelectorArgument targets) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.grantAdvancementEverything(playerLocation));
                }
            }
            case CommandAdvancementRevokeEverything(SelectorArgument targets) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.revokeAdvancementEverything(playerLocation));
                }
            }
            case CommandAdvancementGrantFrom(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.grantAdvancementTree(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString("from")));
                }
            }
            case CommandAdvancementGrantThrough(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.grantAdvancementTree(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString("through")));
                }
            }
            case CommandAdvancementGrantUntil(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.grantAdvancementTree(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString("until")));
                }
            }
            case CommandAdvancementRevokeFrom(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.revokeAdvancementTree(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString("from")));
                }
            }
            case CommandAdvancementRevokeThrough(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.revokeAdvancementTree(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString("through")));
                }
            }
            case CommandAdvancementRevokeUntil(SelectorArgument targets, DataLocation advancement) -> {
                try(var players = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                    players.writeInstruction(Instructions.revokeAdvancementTree(playerLocation, LocationData.ofString(advancement.toString()), LocationData.ofString("until")));
                }
            }
        }
    }

    private static void callFunctionsAndProvideResult(CompilationContext context, ResultContext resultContext, List<DataLocation> functions, LocationData<? extends TCommandSourceStack> stack, @Nullable LocationData<? extends TCompoundTag> arguments) {
        if(resultContext.generatingResult() || resultContext.generatingSuccess()) {
            var resultValue = context.<Long>createLocal();
            context.writeInstruction(Instructions2.callFunctionsWithResult(resultValue, functions, stack, arguments));
            var successFunctionValue = context.<Integer>createLocal();
            context.writeInstruction(Instructions.extractReturnSuccess(successFunctionValue, resultValue));

            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, successFunctionValue, LocationData.ofInteger(0), resultContext.markAndGetErrorLabel()));
            var resultFunctionValue = context.<Integer>createLocal();
            context.writeInstruction(Instructions.extractReturnResult(resultFunctionValue, resultValue));
            resultContext.updateResultValue(resultFunctionValue);
        }else {
            context.writeInstruction(Instructions2.callFunctions(functions, stack, arguments));
        }
    }

    private static void forLocatedBaseNbtData(CompilationContext context, ResultContext resultContext, NbtDataLocation input, Consumer<LocationData<? extends TTag>> consumer) {
        switch(input) {
            case NbtDataLocation.Block _ -> throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS, context.getSource(), "Accessing block nbt is not implemented yet"));
            case NbtDataLocation.Entity _ -> throw new RuntimeException("entity nbt not implemented");
            case NbtDataLocation.Storage(DataLocation storage) -> {
                var nbtLocal = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.getNbtStorage(nbtLocal, storage));
                consumer.accept(nbtLocal);
            }
        }
    }

    private static LocationData<? extends TTag> getNbtPathValue(CompilationContext context, LocationData<? extends TTag> source, List<NbtPathPart> parts, boolean tryCreate, LabelData failLabel) {
        for(var part : parts) {
            if(part instanceof NbtPathPart.Named(String name)) {
                var compound = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.tryAsNbtCompound(compound, source, failLabel));
                var storage = context.<TTag>createLocal();
                if(tryCreate) {
                    context.writeInstruction(Instructions.getNbtFromCompoundOrCreate(storage, compound, LocationData.ofString(name)));
                }else {
                    context.writeInstruction(Instructions.getNbtFromCompound(storage, compound, LocationData.ofString(name)));
                }
                source = storage;
            }else if(part instanceof NbtPathPart.ArrayIndexInteger indexed) {
                var listAccess = accessNbtList(context, source, indexed, failLabel);
                var storage = context.<TTag>createLocal();
                context.writeInstruction(Instructions.getNbtFromList(storage, listAccess.list, listAccess.index));
                source = storage;
            }else {
                throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot get nbt path from " + part));
            }
        }
        return source;
    }

    private static void assignNbtValue(CompilationContext context, LocationData<? extends TTag> source, NbtPathPart accessor, LocationData<? extends TTag> value, @Nullable LocationData<Integer> modifiedCount, LabelData failLabel) {
        if(accessor instanceof NbtPathPart.Named(String name)) {
            var valueCompound = context.<TCompoundTag>createLocal();
            context.writeInstruction(Instructions.tryAsNbtCompound(valueCompound, source, failLabel));
            context.writeInstruction(Instructions.setNbtInCompound(valueCompound, LocationData.ofString(name), value));
        }else if(accessor instanceof NbtPathPart.ArrayIndexInteger indexed) {
            var listAccess = accessNbtList(context, source, indexed, failLabel);
            context.writeInstruction(Instructions.setNbtInList(listAccess.list, listAccess.index, value, failLabel));
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot set nbt value in path " + accessor));
        }
        if(modifiedCount != null) context.writeInstruction(Instructions.valueAdd(modifiedCount, modifiedCount, LocationData.ofInteger(1)));
    }

    private static NbtListAccessResult accessNbtList(CompilationContext context, LocationData<? extends TTag> source, NbtPathPart.ArrayIndexInteger indexed, LabelData failLabel) {
        var list = context.<TListTag>createLocal();
        context.writeInstruction(Instructions.tryAsNbtList(list, source, failLabel));
        var length = context.<Integer>createLocal();
        context.writeInstruction(Instructions.getListLength(length, list));
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, LocationData.ofInteger(indexed.index()), length, failLabel));
        var index = context.<Integer>createLocal();

        if(indexed.index() >= 0) {
            context.writeInstruction(Instructions.dup(index, LocationData.ofInteger(indexed.index())));
        }else {
            context.writeInstruction(Instructions.dup(index, length));
            context.writeInstruction(Instructions.valueSub(index, index, LocationData.ofInteger(-indexed.index())));
        }

        return new NbtListAccessResult(list, index);
    }

    private record NbtListAccessResult(LocationData<TListTag> list, LocationData<Integer> index) {}

    private static LocationData<? extends TComponent> loadText(CompilationContext context, Text text) {
        if(!text.isCompilationSupported()) {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_TEXT, context.getSource(), "Compiling " + text + " is not implemented yet"));
        }

        if(text.isConstant()) {
            var textLocation = context.<TComponent>createLocal();
            context.writeInstruction(Instructions.loadText(textLocation, text));
            return textLocation;
        }else {
            if(text.content() instanceof TextContent.SelectorText(SelectorArgument selector, Text separator)) {
                var selectorResult = context.getSelectorList(selector, null);
                var loadedSeparator = loadText(context, separator != null ? separator : TextContent.SelectorText.DEFAULT_SEPARATOR);
                var textLocation = context.<TMutableComponent>createLocal();
                context.writeInstruction(Instructions.formatEntityTextList(textLocation, selectorResult, loadedSeparator));
                applyStyleAndSiblings(context, text, textLocation);
                return textLocation;
            }
            if(text.content() instanceof TextContent.TranslatableText(String key, String fallback, List<Object> args)) {
                var argsList = context.<List<? extends TComponent>>createLocal();
                context.writeInstruction(Instructions.createMutableList(argsList));
                for(var arg : args) {
                    if(arg instanceof Text argText) {
                        var loadedArg = loadText(context, argText);
                        context.writeInstruction(Instructions.addListElement(argsList, loadedArg));
                    }
                }
                var textLocation = context.<TMutableComponent>createLocal();
                context.writeInstruction(Instructions.createTranslatableComponent(textLocation, key, argsList));
                applyStyleAndSiblings(context, text, textLocation);
                return textLocation;
            }
            if(text.content() instanceof TextContent.ScoreText(ScoreHolderArgument scoreHolder, String objective)) {
                if(scoreHolder.selector() != null) {
                    var selectorResult = context.getSelectorList(scoreHolder.selector(), null);
                    var textLocation = context.<TComponent>createLocal();
                    context.writeInstruction(Instructions.loadScoreText(textLocation, selectorResult, objective));
                    if(!text.style().isEmpty() || !text.siblings().isEmpty()) {
                        var mutableText = context.<TMutableComponent>createLocal();
                        context.writeInstruction(Instructions.copyText(mutableText, textLocation));
                        applyStyleAndSiblings(context, text, mutableText);
                        return mutableText;
                    }
                    return textLocation;
                }
            }
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNREACHABLE_CODE, context.getSource(), "Unreachable code, tried to compile unknown non-constant text: " + text));
        }
    }

    private static void applyStyleAndSiblings(CompilationContext context, Text text, LocationData<TMutableComponent> textLocation) {
        if(!text.style().isEmpty()) {
            var style = context.<TStyle>createLocal();
            context.writeInstruction(Instructions.loadTextStyle(style, text.style()));
            context.writeInstruction(Instructions.setTextStyle(textLocation, style));
        }
        for(var sibling : text.siblings()) {
            var otherText = loadText(context, sibling);
            context.writeInstruction(Instructions.appendText(textLocation, otherText));
        }
    }

}
