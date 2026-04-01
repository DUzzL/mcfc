package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.params.*;
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

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_MODIFY_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_STORE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_TEXT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED;

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
                    result.markImplemented();
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
                    result.updateResultValue(setCountLocal);
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
            // give
            case CommandGive(SelectorArgument targets, String item) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    selector.writeInstruction(Instructions.giveItems(playerLocation, item));
                }
            }
            // playsound
            case CommandPlaysoundMinVolume(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos, double volume, double pitch, double minVolume) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    try(var position = context.applyPosition(pos)) {
                        selector.writeInstruction(Instructions.playSound(playerLocation, sound.toString(), source, position.content(), LocationData.ofDouble(volume), LocationData.ofDouble(pitch), LocationData.ofDouble(minVolume)));
                    }
                }
            }
            case CommandPlaysoundPitch(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos, double volume, double pitch) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    try(var position = context.applyPosition(pos)) {
                        selector.writeInstruction(Instructions.playSound(playerLocation, sound.toString(), source, position.content(), LocationData.ofDouble(volume), LocationData.ofDouble(pitch), LocationData.ofDouble(0.0)));
                    }
                }
            }
            case CommandPlaysoundVolume(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos, double volume) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    try(var position = context.applyPosition(pos)) {
                        selector.writeInstruction(Instructions.playSound(playerLocation, sound.toString(), source, position.content(), LocationData.ofDouble(volume), LocationData.ofDouble(1.0), LocationData.ofDouble(0.0)));
                    }
                }
            }
            case CommandPlaysoundPos(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    try(var position = context.applyPosition(pos)) {
                        selector.writeInstruction(Instructions.playSound(playerLocation, sound.toString(), source, position.content(), LocationData.ofDouble(1.0), LocationData.ofDouble(1.0), LocationData.ofDouble(0.0)));
                    }
                }
            }
            case CommandPlaysound(DataLocation sound, String source, SelectorArgument targets) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var playerLocation = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                    try(var position = context.getPosition()) {
                        selector.writeInstruction(Instructions.playSound(playerLocation, sound.toString(), source, position.content(), LocationData.ofDouble(1.0), LocationData.ofDouble(1.0), LocationData.ofDouble(0.0)));
                    }
                }
            }
            // summon
            case CommandSummonPosNbt(DataLocation entity, WorldCoordinates pos, NbtValue.Compound nbt) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        var nbtLocal = context.<TCompoundTag>createLocal();
                        context.writeInstruction(Instructions.loadNbtValue(nbtLocal.forceCast(), nbt));
                        context.writeInstruction(Instructions.summonEntityNbt(world.content(), entity.toString(), position.content(), nbtLocal));
                    }
                }
            }
            case CommandSummonPos(DataLocation entity, WorldCoordinates pos) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        context.writeInstruction(Instructions.summonEntity(world.content(), entity.toString(), position.content()));
                    }
                }
            }
            case CommandSummon(DataLocation entity) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.getPosition()) {
                        context.writeInstruction(Instructions.summonEntity(world.content(), entity.toString(), position.content()));
                    }
                }
            }
            // time
            case CommandTimeSetDay _ -> context.writeInstruction(Instructions.setDayTime(LocationData.ofInteger(1000)));
            case CommandTimeSetNoon _ -> context.writeInstruction(Instructions.setDayTime(LocationData.ofInteger(6000)));
            case CommandTimeSetNight _ -> context.writeInstruction(Instructions.setDayTime(LocationData.ofInteger(13000)));
            case CommandTimeSetMidnight _ -> context.writeInstruction(Instructions.setDayTime(LocationData.ofInteger(18000)));
            case CommandTimeSetValue(int value) -> context.writeInstruction(Instructions.setDayTime(LocationData.ofInteger(value)));
            case CommandTimeAdd(int value) -> context.writeInstruction(Instructions.addDayTime(LocationData.ofInteger(value)));
            case CommandTimeQueryDaytime _ -> {
                if(result.generatingResult()) {
                    var val = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.queryDayTime(val));
                    result.updateResultValue(val);
                }
            }
            case CommandTimeQueryGametime _ -> {
                if(result.generatingResult()) {
                    var val = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.queryGameTime(val));
                    result.updateResultValue(val);
                }
            }
            case CommandTimeQueryDay _ -> {
                if(result.generatingResult()) {
                    var val = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.queryDay(val));
                    result.updateResultValue(val);
                }
            }
            // schedule
            case CommandScheduleFunctionAppend(DataLocation function, int time) -> context.writeInstruction(Instructions.scheduleFunction(function.toString(), LocationData.ofInteger(time), true));
            case CommandScheduleFunctionReplace(DataLocation function, int time) -> context.writeInstruction(Instructions.scheduleFunction(function.toString(), LocationData.ofInteger(time), false));
            case CommandScheduleFunction(DataLocation function, int time) -> context.writeInstruction(Instructions.scheduleFunction(function.toString(), LocationData.ofInteger(time), false));
            case CommandScheduleClear(DataLocation function) -> context.writeInstruction(Instructions.scheduleClearFunction(function.toString()));
            // gamerule
            case CommandGameruleSet(DataLocation rule, String value) -> context.writeInstruction(Instructions.setGamerule(rule.toString(), value));
            case CommandGameruleQuery(DataLocation rule) -> {
                result.markImplemented();
                var val = context.<Integer>createLocal();
                context.writeInstruction(Instructions.queryGamerule(val, rule.toString()));
                result.updateResultValue(val);
            }
            // datapack
            case CommandDatapackEnable(String name) -> context.writeInstruction(Instructions.enableDatapack(name));
            case CommandDatapackDisable(String name) -> context.writeInstruction(Instructions.disableDatapack(name));

            // execute if/unless predicate
            case CommandExecuteIfPredicate(DataLocation predicate, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        currentEntity.writeInstruction(Instructions.checkEntityPredicate(currentEntity.content(), predicate.toString(), true, currentEntity.endLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteUnlessPredicate(DataLocation predicate, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        currentEntity.writeInstruction(Instructions.checkEntityPredicate(currentEntity.content(), predicate.toString(), false, currentEntity.endLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }

            // execute if/unless data (NBT check)
            case CommandExecuteIfDataEntity(SelectorArgument target, NbtPath path, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var entityData = context.forSelector(target, result)) {
                        var nbtLocal = context.<TCompoundTag>createLocal();
                        entityData.writeInstruction(Instructions.getEntityNbt(nbtLocal, entityData.content()));
                        var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, entityData.data().contextEndLabel());
                        entityData.writeInstruction(Instructions.ifNullJump(tagVal, entityData.data().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteIfDataStorage(DataLocation target, NbtPath path, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var nbtLocal = context.<TCompoundTag>createLocal();
                    context.writeInstruction(Instructions.getNbtStorage(nbtLocal, target));
                    var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, context.current().contextEndLabel());
                    context.writeInstruction(Instructions.ifNullJump(tagVal, context.current().contextEndLabel()));
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteIfDataBlock(WorldCoordinates pos, NbtPath path, CommandParams command) ->
                emitVanillaFallback(context, "execute if data block " + worldCoordsToString(pos) + " " + nbtPathToString(path) + " run " + getRawCommand(command));
            case CommandExecuteUnlessDataEntity(SelectorArgument target, NbtPath path, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var contextLabel = context.current().contextEndLabel();
                    try(var scope = context.enterLocalScope()) {
                        try(var entityData = context.forSelector(target, result)) {
                            var nbtLocal = context.<TCompoundTag>createLocal();
                            entityData.writeInstruction(Instructions.getEntityNbt(nbtLocal, entityData.content()));
                            var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, scope.endLabel());
                            entityData.writeInstruction(Instructions.ifNotNullJump(tagVal, contextLabel));
                        }
                    }
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteUnlessDataStorage(DataLocation target, NbtPath path, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var contextLabel = context.current().contextEndLabel();
                    try(var scope = context.enterLocalScope()) {
                        var nbtLocal = context.<TCompoundTag>createLocal();
                        context.writeInstruction(Instructions.getNbtStorage(nbtLocal, target));
                        var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, scope.endLabel());
                        context.writeInstruction(Instructions.ifNotNullJump(tagVal, contextLabel));
                    }
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteUnlessDataBlock(WorldCoordinates pos, NbtPath path, CommandParams command) ->
                emitVanillaFallback(context, "execute unless data block " + worldCoordsToString(pos) + " " + nbtPathToString(path) + " run " + getRawCommand(command));

            // execute if/unless blocks — vanilla fallback
            case CommandExecuteIfBlocks(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                emitVanillaFallback(context, "execute if blocks " + worldCoordsToString(start) + " " + worldCoordsToString(end) + " " + worldCoordsToString(destination) + " all run " + getRawCommand(command));
            case CommandExecuteIfBlocksMasked(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                emitVanillaFallback(context, "execute if blocks " + worldCoordsToString(start) + " " + worldCoordsToString(end) + " " + worldCoordsToString(destination) + " masked run " + getRawCommand(command));
            case CommandExecuteUnlessBlocks(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                emitVanillaFallback(context, "execute unless blocks " + worldCoordsToString(start) + " " + worldCoordsToString(end) + " " + worldCoordsToString(destination) + " all run " + getRawCommand(command));
            case CommandExecuteUnlessBlocksMasked(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                emitVanillaFallback(context, "execute unless blocks " + worldCoordsToString(start) + " " + worldCoordsToString(end) + " " + worldCoordsToString(destination) + " masked run " + getRawCommand(command));

            // execute if/unless без sub-command (result/success context)
            case CommandExecuteIfScoreRangeOnly(ScoreAccessorArgument target, IntRangeArgument range) -> {
                result.markImplemented();
                try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), result)) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), result.markAndGetErrorLabel(), false)) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                        result.updateResultValue(scoreValue);
                        context.jumpIfNotMatchesRange(scoreData.data(), scoreValue, range, result.markAndGetErrorLabel());
                    }
                }
            }
            case CommandExecuteUnlessScoreRangeOnly(ScoreAccessorArgument target, IntRangeArgument range) -> {
                result.markImplemented();
                try(var scope = context.enterLocalScope()) {
                    var scopeResult = ResultContext.create(context, scope.endLabel());
                    try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), scopeResult)) {
                        try(var scoreData = context.getScoreData(scoreAccessor.content(), scopeResult.markAndGetErrorLabel(), false)) {
                            var scoreValue = context.<Integer>createLocal();
                            scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                            context.jumpIfMatchesRange(scoreData.data(), scoreValue, range, result.markAndGetErrorLabel());
                        }
                    }
                    scopeResult.close();
                }
            }
            case CommandExecuteIfEntityOnly(SelectorArgument entities) -> {
                result.markImplemented();
                try(var ignored = context.forSelector(entities.limit(1), result)) {}
            }
            case CommandExecuteUnlessEntityOnly(SelectorArgument entities) -> {
                result.markImplemented();
                var codeLocation = context.current().writeLabel(context.instructionWriter);
                try(var ignored = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                    try(var selector = context.forSelector(entities.limit(1), result)) {
                        selector.writeInstruction(Instructions.jump(result.markAndGetErrorLabel()));
                    }
                }
            }

            // recipe give/take * wildcard
            case CommandRecipeGiveAllStar(SelectorArgument targets, String star) ->
                emitVanillaFallback(context, "recipe give " + targets + " *");
            case CommandRecipeTakeAllStar(SelectorArgument targets, String star) ->
                emitVanillaFallback(context, "recipe take " + targets + " *");

            // execute summon / positioned over — vanilla fallback
            case CommandExecuteSummon(DataLocation entity, CommandParams command) ->
                emitVanillaFallback(context, "execute summon " + entity + " run " + getRawCommand(command));
            case CommandExecutePositionedOver(String heightmap, CommandParams command) ->
                emitVanillaFallback(context, "execute positioned over " + heightmap + " run " + getRawCommand(command));

            // item replace — vanilla fallback
            case CommandItemReplaceEntityWith(SelectorArgument targets, String slot, String item) ->
                emitVanillaFallback(context, "item replace entity " + targets + " " + slot + " with " + item);
            case CommandItemReplaceEntityFromEntity(SelectorArgument targets, String slot, SelectorArgument source) ->
                emitVanillaFallback(context, "item replace entity " + targets + " " + slot + " from entity " + source);
            case CommandItemReplaceEntityFromEntitySlot(SelectorArgument targets, String slot, SelectorArgument source, String sourceSlot) ->
                emitVanillaFallback(context, "item replace entity " + targets + " " + slot + " from entity " + source + " " + sourceSlot);
            case CommandItemReplaceBlockWith(WorldCoordinates pos, String slot, String item) ->
                emitVanillaFallback(context, "item replace block " + worldCoordsToString(pos) + " " + slot + " with " + item);
            case CommandItemReplaceBlockFromEntity(WorldCoordinates pos, String slot, SelectorArgument source) ->
                emitVanillaFallback(context, "item replace block " + worldCoordsToString(pos) + " " + slot + " from entity " + source);
            case CommandItemReplaceBlockFromEntitySlot(WorldCoordinates pos, String slot, SelectorArgument source, String sourceSlot) ->
                emitVanillaFallback(context, "item replace block " + worldCoordsToString(pos) + " " + slot + " from entity " + source + " " + sourceSlot);
            case CommandItemReplaceBlockFromBlock(WorldCoordinates pos, String slot, WorldCoordinates sourcePos) ->
                emitVanillaFallback(context, "item replace block " + worldCoordsToString(pos) + " " + slot + " from block " + worldCoordsToString(sourcePos));
            case CommandItemReplaceBlockFromBlockSlot(WorldCoordinates pos, String slot, WorldCoordinates sourcePos, String sourceSlot) ->
                emitVanillaFallback(context, "item replace block " + worldCoordsToString(pos) + " " + slot + " from block " + worldCoordsToString(sourcePos) + " " + sourceSlot);

            // scoreboard players display numberformat без sub-command (standalone)
            case CommandScoreboardObjectivesSetdisplayClear(String slot) ->
                emitVanillaFallback(context, "scoreboard objectives setdisplay " + slot);
            case CommandScoreboardObjectivesRemove(String objective) ->
                context.writeInstruction(Instructions.removeObjective(objective));
            case CommandScoreboardObjectivesAdd(String objective, DataLocation criteria) ->
                context.writeInstruction(Instructions.addObjective(objective, criteria.toString()));
            case CommandScoreboardObjectivesAddDisplay(String objective, DataLocation criteria, Text display) -> {
                var displayLocal = context.<TComponent>createLocal();
                context.writeInstruction(Instructions.loadText(displayLocal, display));
                context.writeInstruction(Instructions.addObjectiveDisplay(objective, criteria.toString(), displayLocal));
            }
            case CommandScoreboardObjectivesAddDisplayGreedy(String objective, DataLocation criteria, String displayName) ->
                emitVanillaFallback(context, "scoreboard objectives add " + objective + " " + criteria + " " + displayName);
            case CommandScoreboardPlayersDisplayName(ScoreHolderArgument targets, String objective) ->
                emitVanillaFallback(context, "scoreboard players display name " + targets + " " + objective);
            case CommandScoreboardPlayersDisplayNameText(ScoreHolderArgument targets, String objective, Text displayName) ->
                emitVanillaFallback(context, "scoreboard players display name " + targets + " " + objective + " " + nbtValueToString(new NbtValue.StringLiteral(displayName.toString())));
            case CommandScoreboardPlayersDisplayNameGreedy(ScoreHolderArgument targets, String objective, String displayName) ->
                emitVanillaFallback(context, "scoreboard players display name " + targets + " " + objective + " " + displayName);
            case CommandScoreboardPlayersDisplayNameClear(ScoreHolderArgument targets) ->
                emitVanillaFallback(context, "scoreboard players display name " + targets);
            case CommandScoreboardObjectivesSetdisplay(String slot, String objective) ->
                emitVanillaFallback(context, "scoreboard objectives setdisplay " + slot + " " + objective);
            case CommandScoreboardPlayersDisplayNumberformatBlank(ScoreHolderArgument targets, String objective) ->
                emitVanillaFallback(context, "scoreboard players display numberformat " + targets + " " + objective + " blank");
            case CommandScoreboardPlayersDisplayNumberformatStyled(ScoreHolderArgument targets, String objective, String style) ->
                emitVanillaFallback(context, "scoreboard players display numberformat " + targets + " " + objective + " styled " + style);
            case CommandScoreboardPlayersDisplayNumberformatFixed(ScoreHolderArgument targets, String objective, Text text) ->
                emitVanillaFallback(context, "scoreboard players display numberformat " + targets + " " + objective + " fixed " + nbtValueToString(new NbtValue.StringLiteral(text.toString())));

            // execute in <dimension>
            case CommandExecuteIn(DataLocation dimension, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var worldLocal = context.<TServerLevel>createLocal();
                    context.writeInstruction(Instructions.getDimensionLevel(worldLocal, dimension.toString()));
                    context.writeInstruction(Instructions.ifNullJump(worldLocal, context.current().contextEndLabel()));
                    try(var ignored = context.enterWithoutValue(context.current().withCurrentWorld(worldLocal))) {
                        compile(context, fContext.content(), command, result);
                    }
                }
            }

            // execute if/unless block
            case CommandExecuteIfBlock(WorldCoordinates pos, String block, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var current = context.current();
                    var worldLocal = current.currentWorld();
                    if(worldLocal == null) {
                        var w = context.<TServerLevel>createLocal();
                        context.writeInstruction(Instructions.getStackWorld(w, current.commandSourceStack()));
                        worldLocal = w;
                    }
                    try(var posData = context.applyPosition(pos)) {
                        var cond = context.<Boolean>createLocal();
                        posData.writeInstruction(Instructions.checkBlock(cond, worldLocal, posData.content(), block));
                        posData.writeInstruction(Instructions.ifFalseJump(cond, posData.data().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteUnlessBlock(WorldCoordinates pos, String block, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    var current = context.current();
                    var worldLocal = current.currentWorld();
                    if(worldLocal == null) {
                        var w = context.<TServerLevel>createLocal();
                        context.writeInstruction(Instructions.getStackWorld(w, current.commandSourceStack()));
                        worldLocal = w;
                    }
                    try(var posData = context.applyPosition(pos)) {
                        var cond = context.<Boolean>createLocal();
                        posData.writeInstruction(Instructions.checkBlock(cond, worldLocal, posData.content(), block));
                        posData.writeInstruction(Instructions.ifNotNullJump(cond, posData.data().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }

            // execute if/unless items — vanilla fallback (нужен ItemPredicateArgument который сложно парсить)
            case CommandExecuteIfItemsEntity(SelectorArgument targets, String slot, String predicate, CommandParams command) ->
                emitVanillaFallback(context, "execute if items entity " + targets + " " + slot + " " + predicate + " run " + getRawCommand(command));
            case CommandExecuteUnlessItemsEntity(SelectorArgument targets, String slot, String predicate, CommandParams command) ->
                emitVanillaFallback(context, "execute unless items entity " + targets + " " + slot + " " + predicate + " run " + getRawCommand(command));
            case CommandExecuteIfItemsBlock(WorldCoordinates pos, String slot, String predicate, CommandParams command) ->
                emitVanillaFallback(context, "execute if items block " + worldCoordsToString(pos) + " " + slot + " " + predicate + " run " + getRawCommand(command));
            case CommandExecuteUnlessItemsBlock(WorldCoordinates pos, String slot, String predicate, CommandParams command) ->
                emitVanillaFallback(context, "execute unless items block " + worldCoordsToString(pos) + " " + slot + " " + predicate + " run " + getRawCommand(command));

            // execute if/unless items standalone — для execute store result ... if items ...
            case CommandExecuteIfItemsEntityOnly(SelectorArgument targets, String slot, String predicate) -> {
                result.markImplemented();
                emitVanillaFallback(context, "execute if items entity " + targets + " " + slot + " " + predicate);
            }
            case CommandExecuteUnlessItemsEntityOnly(SelectorArgument targets, String slot, String predicate) -> {
                result.markImplemented();
                emitVanillaFallback(context, "execute unless items entity " + targets + " " + slot + " " + predicate);
            }
            case CommandExecuteIfItemsBlockOnly(WorldCoordinates pos, String slot, String predicate) -> {
                result.markImplemented();
                emitVanillaFallback(context, "execute if items block " + worldCoordsToString(pos) + " " + slot + " " + predicate);
            }
            case CommandExecuteUnlessItemsBlockOnly(WorldCoordinates pos, String slot, String predicate) -> {
                result.markImplemented();
                emitVanillaFallback(context, "execute unless items block " + worldCoordsToString(pos) + " " + slot + " " + predicate);
            }

            // execute store entity — vanilla fallback (entity NBT store сложный)
            case CommandExecuteStoreEntity(ResultStoreLocationType storeLocation, SelectorArgument target, NbtPath path, ResultStoreType storeType, double scale, CommandParams command) ->
                emitVanillaFallback(context, "execute store " + storeLocation.name().toLowerCase() + " entity " + target + " " + nbtPathToString(path) + " " + storeType.name().toLowerCase() + " " + scale + " run " + getRawCommand(command));

            // execute on leasher / attacker / controller / owner / passengers
            case CommandExecuteOnLeasher(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var holder = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityLeashHolder(holder, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(holder, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(holder))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnAttacker(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var attacker = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityLastAttacker(attacker, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(attacker, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(attacker))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnController(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var controller = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityController(controller, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(controller, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(controller))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnOwner(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var owner = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityOwner(owner, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(owner, currentEntity.endLabel()));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(owner))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnPassengers(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var passengerList = context.<List<? extends TEntity>>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityPassengers(passengerList, currentEntity.content()));
                        var length = context.<Integer>createLocal();
                        currentEntity.writeInstruction(Instructions.getListLength(length, passengerList));
                        var index = context.<Integer>createLocal();
                        currentEntity.writeInstruction(Instructions.dup(index, LocationData.ofInteger(0)));
                        var iterStart = currentEntity.data().writeLabel(context.instructionWriter);
                        currentEntity.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, currentEntity.endLabel()));
                        var passengerObj = context.createLocal();
                        var passenger = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getListElement(passengerObj, passengerList, index));
                        currentEntity.writeInstruction(Instructions.assumeEntity(passenger, passengerObj));
                        var codeLocation = currentEntity.data().writeLabel(context.instructionWriter);
                        currentEntity.writeInstruction(Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
                        currentEntity.writeInstruction(Instructions.jump(iterStart));
                        try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(passenger).withContextLabel(codeLocation))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }

            // data merge
            case CommandDataMergeStorage(DataLocation target, NbtValue.Compound nbt) -> {
                var nbtLocal = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.loadNbtValue(nbtLocal.forceCast(), nbt));
                context.writeInstruction(Instructions.mergeNbtStorage(target, nbtLocal));
            }
            case CommandDataMergeEntity(SelectorArgument target, NbtValue.Compound nbt) ->
                emitVanillaFallback(context, "data merge entity " + target + " " + nbtValueToString(nbt));

            // data remove
            case CommandDataRemoveStorage(DataLocation target, NbtPath path) ->
                emitVanillaFallback(context, "data remove storage " + target + " " + nbtPathToString(path));
            case CommandDataRemoveEntity(SelectorArgument target, NbtPath path) ->
                emitVanillaFallback(context, "data remove entity " + target + " " + nbtPathToString(path));

            // data modify prepend/append/insert value
            case CommandDataModifyPrependValue(NbtDataLocation destination, NbtPath path, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    compileDataModifyListOpValue(context, storage, path, setValue, true);
                } else {
                    emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " prepend value " + nbtValueToString(setValue));
                }
            }
            case CommandDataModifyAppendValue(NbtDataLocation destination, NbtPath path, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    compileDataModifyListOpValue(context, storage, path, setValue, false);
                } else {
                    emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " append value " + nbtValueToString(setValue));
                }
            }
            case CommandDataModifyInsertValue(NbtDataLocation destination, NbtPath path, int index, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    var storageLocal = context.<TCompoundTag>createLocal();
                    context.writeInstruction(Instructions.getNbtStorage(storageLocal, storage));
                    var parts = path.parts();
                    var codeLocation = context.current().writeLabel(context.instructionWriter);
                    try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                        var listContainer = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), false, innerContext.data().contextEndLabel());
                        var listTag = context.<TListTag>createLocal();
                        innerContext.writeInstruction(Instructions.tryAsNbtList(listTag, listContainer, innerContext.data().contextEndLabel()));
                        var tagLocal = context.<TTag>createLocal();
                        var tagCopied = context.<TTag>createLocal();
                        innerContext.writeInstruction(Instructions.loadNbtValue(tagLocal, setValue));
                        innerContext.writeInstruction(Instructions.cloneNbtValue(tagCopied, tagLocal));
                        innerContext.writeInstruction(Instructions.nbtListInsert(listTag, LocationData.ofInteger(index), tagCopied, innerContext.data().contextEndLabel()));
                    }
                    context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
                } else {
                    emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " insert " + index + " value " + nbtValueToString(setValue));
                }
            }

            // data modify set/prepend/append from — vanilla fallback (runtime value копирование)
            case CommandDataModifySetFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " set from " + nbtDataLocationToString(source));
            case CommandDataModifySetFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " set from " + nbtDataLocationToString(source) + " " + nbtPathToString(sourcePath));
            case CommandDataModifyPrependFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " prepend from " + nbtDataLocationToString(source));
            case CommandDataModifyPrependFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " prepend from " + nbtDataLocationToString(source) + " " + nbtPathToString(sourcePath));
            case CommandDataModifyAppendFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " append from " + nbtDataLocationToString(source));
            case CommandDataModifyAppendFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallback(context, "data modify " + nbtDataLocationToString(destination) + " " + nbtPathToString(path) + " append from " + nbtDataLocationToString(source) + " " + nbtPathToString(sourcePath));

            // data get — нативная реализация для entity (самый частый случай в датапаках)
            case CommandDataGetPath(NbtDataLocation source, NbtPath path) -> {
                if(source instanceof NbtDataLocation.Entity(SelectorArgument selector)) {
                    result.markImplemented();
                    try(var entityData = context.forSelector(selector, result)) {
                        var nbtLocal = context.<TCompoundTag>createLocal();
                        entityData.writeInstruction(Instructions.getEntityNbt(nbtLocal, entityData.content()));
                        var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, result.markAndGetErrorLabel());
                        context.writeInstruction(Instructions.ifNullJump(tagVal, result.markAndGetErrorLabel()));
                        var intVal = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.nbtTagToInt(intVal, tagVal, 1.0));
                        result.updateResultValue(intVal);
                    }
                } else {
                    emitVanillaFallback(context, "data get " + nbtDataLocationToString(source) + " " + nbtPathToString(path));
                }
            }
            case CommandDataGetPathScale(NbtDataLocation source, NbtPath path, double scale) -> {
                if(source instanceof NbtDataLocation.Entity(SelectorArgument selector)) {
                    result.markImplemented();
                    try(var entityData = context.forSelector(selector, result)) {
                        var nbtLocal = context.<TCompoundTag>createLocal();
                        entityData.writeInstruction(Instructions.getEntityNbt(nbtLocal, entityData.content()));
                        var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, result.markAndGetErrorLabel());
                        context.writeInstruction(Instructions.ifNullJump(tagVal, result.markAndGetErrorLabel()));
                        var intVal = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.nbtTagToInt(intVal, tagVal, scale));
                        result.updateResultValue(intVal);
                    }
                } else {
                    emitVanillaFallback(context, "data get " + nbtDataLocationToString(source) + " " + nbtPathToString(path) + " " + scale);
                }
            }

            // loot — vanilla fallback
            case CommandLootReplaceEntityLoot(SelectorArgument target, String slot, DataLocation loot) ->
                emitVanillaFallback(context, "loot replace entity " + target + " " + slot + " loot " + loot);
            case CommandLootReplaceEntityKill(SelectorArgument target, String slot, SelectorArgument kill) ->
                emitVanillaFallback(context, "loot replace entity " + target + " " + slot + " kill " + kill);
            case CommandLootReplaceBlockLoot(WorldCoordinates pos, String slot, DataLocation loot) ->
                emitVanillaFallback(context, "loot replace block " + worldCoordsToString(pos) + " " + slot + " loot " + loot);
            case CommandLootReplaceBlockKill(WorldCoordinates pos, String slot, SelectorArgument kill) ->
                emitVanillaFallback(context, "loot replace block " + worldCoordsToString(pos) + " " + slot + " kill " + kill);
            case CommandLootInsertLoot(WorldCoordinates pos, DataLocation loot) ->
                emitVanillaFallback(context, "loot insert " + worldCoordsToString(pos) + " loot " + loot);
            case CommandLootInsertKill(WorldCoordinates pos, SelectorArgument kill) ->
                emitVanillaFallback(context, "loot insert " + worldCoordsToString(pos) + " kill " + kill);
            case CommandLootGiveLoot(SelectorArgument players, DataLocation loot) ->
                emitVanillaFallback(context, "loot give " + players + " loot " + loot);
            case CommandLootGiveKill(SelectorArgument players, SelectorArgument kill) ->
                emitVanillaFallback(context, "loot give " + players + " kill " + kill);
            case CommandLootSpawnLoot(WorldCoordinates pos, DataLocation loot) ->
                emitVanillaFallback(context, "loot spawn " + worldCoordsToString(pos) + " loot " + loot);
            case CommandLootSpawnKill(WorldCoordinates pos, SelectorArgument kill) ->
                emitVanillaFallback(context, "loot spawn " + worldCoordsToString(pos) + " kill " + kill);

            // item modify — vanilla fallback
            case CommandItemModifyEntity(SelectorArgument targets, String slot, String modifier) ->
                emitVanillaFallback(context, "item modify entity " + targets + " " + slot + " " + modifier);
            case CommandItemModifyBlock(WorldCoordinates pos, String slot, String modifier) ->
                emitVanillaFallback(context, "item modify block " + worldCoordsToString(pos) + " " + slot + " " + modifier);

            // effect — vanilla fallback
            case CommandEffectGive(SelectorArgument targets, DataLocation effect) ->
                emitVanillaFallback(context, "effect give " + targets + " " + effect);
            case CommandEffectGiveSeconds(SelectorArgument targets, DataLocation effect, int seconds) ->
                emitVanillaFallback(context, "effect give " + targets + " " + effect + " " + seconds);
            case CommandEffectGiveSecondsAmplifier(SelectorArgument targets, DataLocation effect, int seconds, int amplifier) ->
                emitVanillaFallback(context, "effect give " + targets + " " + effect + " " + seconds + " " + amplifier);
            case CommandEffectGiveSecondsAmplifierHide(SelectorArgument targets, DataLocation effect, int seconds, int amplifier, boolean hide) ->
                emitVanillaFallback(context, "effect give " + targets + " " + effect + " " + seconds + " " + amplifier + " " + hide);
            case CommandEffectClear() ->
                emitVanillaFallback(context, "effect clear");
            case CommandEffectClearTargets(SelectorArgument targets) ->
                emitVanillaFallback(context, "effect clear " + targets);
            case CommandEffectClearTargetsEffect(SelectorArgument targets, DataLocation effect) ->
                emitVanillaFallback(context, "effect clear " + targets + " " + effect);

            // time query namespaced (minecraft:day, minecraft:daytime, etc.)
            case CommandTimeQueryNamespaced(DataLocation query) -> {
                result.markImplemented();
                var val = context.<Integer>createLocal();
                context.writeInstruction(switch(query.path()) {
                    case "day" -> Instructions.queryDay(val);
                    case "daytime" -> Instructions.queryDayTime(val);
                    case "gametime" -> Instructions.queryGameTime(val);
                    default -> Instructions.queryDayTime(val);
                });
                result.updateResultValue(val);
            }

            // tp — vanilla fallback
            case CommandTpEntity(SelectorArgument destination) ->
                emitVanillaFallback(context, "tp " + destination);
            case CommandTpTargetsEntity(SelectorArgument targets, SelectorArgument destination) ->
                emitVanillaFallback(context, "tp " + targets + " " + destination);
            case CommandTpTargetsPos(SelectorArgument targets, WorldCoordinates pos) ->
                emitVanillaFallback(context, "tp " + targets + " " + worldCoordsToString(pos));
            case CommandTpTargetsPosRot(SelectorArgument targets, WorldCoordinates pos, RotationCoordinates rot) ->
                emitVanillaFallback(context, "tp " + targets + " " + worldCoordsToString(pos) + " " + rot.yaw() + " " + rot.pitch());
            case CommandTpPos(WorldCoordinates pos) ->
                emitVanillaFallback(context, "tp " + worldCoordsToString(pos));

            // team — vanilla fallback
            case CommandTeamAdd(String team) ->
                emitVanillaFallback(context, "team add " + team);
            case CommandTeamAddDisplay(String team, Text display) ->
                emitVanillaFallback(context, "team add " + team + " " + nbtValueToString(new NbtValue.StringLiteral(display.toString())));
            case CommandTeamRemove(String team) ->
                emitVanillaFallback(context, "team remove " + team);
            case CommandTeamJoin(String team) ->
                emitVanillaFallback(context, "team join " + team);
            case CommandTeamJoinMembers(String team, ScoreHolderArgument members) ->
                emitVanillaFallback(context, "team join " + team + " " + members);
            case CommandTeamLeave(ScoreHolderArgument members) ->
                emitVanillaFallback(context, "team leave " + members);
            case CommandTeamList() ->
                emitVanillaFallback(context, "team list");
            case CommandTeamListTeam(String team) ->
                emitVanillaFallback(context, "team list " + team);
            case CommandTeamModify(String team, String option, String value) ->
                emitVanillaFallback(context, "team modify " + team + " " + option + " " + value);
            case CommandTeamEmpty(String team) ->
                emitVanillaFallback(context, "team empty " + team);

            // stopsound — vanilla fallback
            case CommandStopsound(SelectorArgument targets) ->
                emitVanillaFallback(context, "stopsound " + targets);
            case CommandStopsoundSource(SelectorArgument targets, String source) ->
                emitVanillaFallback(context, "stopsound " + targets + " " + source);
            case CommandStopsoundSound(SelectorArgument targets, String source, DataLocation sound) ->
                emitVanillaFallback(context, "stopsound " + targets + " " + source + " " + sound);

            // setblock — vanilla fallback
            case CommandSetblockReplace(WorldCoordinates pos, String block) ->
                emitVanillaFallback(context, "setblock " + worldCoordsToString(pos) + " " + block);
            case CommandSetblockKeep(WorldCoordinates pos, String block) ->
                emitVanillaFallback(context, "setblock " + worldCoordsToString(pos) + " " + block + " keep");
            case CommandSetblockDestroy(WorldCoordinates pos, String block) ->
                emitVanillaFallback(context, "setblock " + worldCoordsToString(pos) + " " + block + " destroy");

            // fill — vanilla fallback
            case CommandFill(WorldCoordinates from, WorldCoordinates to, String block) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block);
            case CommandFillReplace(WorldCoordinates from, WorldCoordinates to, String block) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block + " replace");
            case CommandFillReplaceFilter(WorldCoordinates from, WorldCoordinates to, String block, String filter) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block + " replace " + filter);
            case CommandFillKeep(WorldCoordinates from, WorldCoordinates to, String block) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block + " keep");
            case CommandFillDestroy(WorldCoordinates from, WorldCoordinates to, String block) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block + " destroy");
            case CommandFillHollow(WorldCoordinates from, WorldCoordinates to, String block) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block + " hollow");
            case CommandFillOutline(WorldCoordinates from, WorldCoordinates to, String block) ->
                emitVanillaFallback(context, "fill " + worldCoordsToString(from) + " " + worldCoordsToString(to) + " " + block + " outline");

            // particle — vanilla fallback
            case CommandParticle(DataLocation name, WorldCoordinates pos) ->
                emitVanillaFallback(context, "particle " + name + " " + worldCoordsToString(pos));
            case CommandParticleViewers(DataLocation name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count) ->
                emitVanillaFallback(context, "particle " + name + " " + worldCoordsToString(pos) + " " + worldCoordsToString(delta) + " " + speed + " " + count);
            case CommandParticleViewersParams(DataLocation name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count, SelectorArgument viewers) ->
                emitVanillaFallback(context, "particle " + name + " " + worldCoordsToString(pos) + " " + worldCoordsToString(delta) + " " + speed + " " + count + " " + viewers);

            // random — vanilla fallback
            case CommandRandomValue(IntRangeArgument range) -> {
                result.markImplemented();
                emitVanillaFallback(context, "random value " + rangeToString(range));
            }
            case CommandRandomRoll(IntRangeArgument range, DataLocation sequence) -> {
                result.markImplemented();
                emitVanillaFallback(context, "random roll " + rangeToString(range) + " " + sequence);
            }
            case CommandRandomReset(DataLocation sequence) ->
                emitVanillaFallback(context, "random reset " + sequence);
            case CommandRandomResetAll() ->
                emitVanillaFallback(context, "random reset *");

            // clear — vanilla fallback
            case CommandClear() -> {
                result.markImplemented();
                emitVanillaFallback(context, "clear");
            }
            case CommandClearTargets(SelectorArgument targets) -> {
                result.markImplemented();
                emitVanillaFallback(context, "clear " + targets);
            }
            case CommandClearTargetsItem(SelectorArgument targets, String item) -> {
                result.markImplemented();
                emitVanillaFallback(context, "clear " + targets + " " + item);
            }
            case CommandClearTargetsItemMax(SelectorArgument targets, String item, int maxCount) -> {
                result.markImplemented();
                emitVanillaFallback(context, "clear " + targets + " " + item + " " + maxCount);
            }

            // recipe — vanilla fallback
            case CommandRecipeGive(SelectorArgument targets, DataLocation recipe) ->
                emitVanillaFallback(context, "recipe give " + targets + " " + recipe);
            case CommandRecipeTake(SelectorArgument targets, DataLocation recipe) ->
                emitVanillaFallback(context, "recipe take " + targets + " " + recipe);

            // loot дополнительные источники
            case CommandLootSpawnFish(WorldCoordinates pos, DataLocation lootTable, WorldCoordinates pos2) ->
                emitVanillaFallback(context, "loot spawn " + worldCoordsToString(pos) + " fish " + lootTable + " " + worldCoordsToString(pos2));
            case CommandLootSpawnMine(WorldCoordinates pos, WorldCoordinates pos2, String tool) ->
                emitVanillaFallback(context, "loot spawn " + worldCoordsToString(pos) + " mine " + worldCoordsToString(pos2) + " " + tool);
            case CommandLootSpawnMineMainhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallback(context, "loot spawn " + worldCoordsToString(pos) + " mine " + worldCoordsToString(pos2) + " mainhand");
            case CommandLootSpawnMineOffhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallback(context, "loot spawn " + worldCoordsToString(pos) + " mine " + worldCoordsToString(pos2) + " offhand");
            case CommandLootInsertFish(WorldCoordinates pos, DataLocation lootTable, WorldCoordinates pos2) ->
                emitVanillaFallback(context, "loot insert " + worldCoordsToString(pos) + " fish " + lootTable + " " + worldCoordsToString(pos2));
            case CommandLootInsertMine(WorldCoordinates pos, WorldCoordinates pos2, String tool) ->
                emitVanillaFallback(context, "loot insert " + worldCoordsToString(pos) + " mine " + worldCoordsToString(pos2) + " " + tool);
            case CommandLootInsertMineMainhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallback(context, "loot insert " + worldCoordsToString(pos) + " mine " + worldCoordsToString(pos2) + " mainhand");
            case CommandLootInsertMineOffhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallback(context, "loot insert " + worldCoordsToString(pos) + " mine " + worldCoordsToString(pos2) + " offhand");
            case CommandLootGiveFish(SelectorArgument players, DataLocation lootTable, WorldCoordinates pos) ->
                emitVanillaFallback(context, "loot give " + players + " fish " + lootTable + " " + worldCoordsToString(pos));
            case CommandLootGiveMine(SelectorArgument players, WorldCoordinates pos, String tool) ->
                emitVanillaFallback(context, "loot give " + players + " mine " + worldCoordsToString(pos) + " " + tool);
            case CommandLootGiveMineMainhand(SelectorArgument players, WorldCoordinates pos) ->
                emitVanillaFallback(context, "loot give " + players + " mine " + worldCoordsToString(pos) + " mainhand");
            case CommandLootGiveMineOffhand(SelectorArgument players, WorldCoordinates pos) ->
                emitVanillaFallback(context, "loot give " + players + " mine " + worldCoordsToString(pos) + " offhand");

            // vanilla fallback for partial compilation
            case CommandFallbackVanilla(String rawCommand) -> {
                var current = context.current();
                var stack = context.<TCommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                context.writeInstruction(Instructions.executeVanillaCommand(stack, rawCommand));
            }
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
            default -> throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(), "Compilation not implemented for command: " + inputCommand.getClass().getSimpleName()));
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
            case NbtDataLocation.Entity(SelectorArgument selector) -> {
                try(var entityData = context.forSelector(selector, resultContext)) {
                    var nbtLocal = context.<TCompoundTag>createLocal();
                    entityData.writeInstruction(Instructions.getEntityNbt(nbtLocal, entityData.content()));
                    consumer.accept(nbtLocal);
                }
            }
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
            }else if(part instanceof NbtPathPart.ArrayIndexCompound(NbtValue.Compound filter)) {
                var list = context.<TListTag>createLocal();
                context.writeInstruction(Instructions.tryAsNbtList(list, source, failLabel));
                var filterTag = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.loadNbtValue(filterTag.forceCast(), filter));
                var found = context.<TTag>createLocal();
                context.writeInstruction(Instructions.findFirstNbtMatchingCompound(found, list, filterTag, failLabel));
                source = found;
            }else if(part instanceof NbtPathPart.CompoundCheck(NbtValue.Compound filter)) {
                // фильтр — проверяем текущий compound совпадает с фильтром, не навигируем
                var filterTag = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.loadNbtValue(filterTag.forceCast(), filter));
                context.writeInstruction(Instructions.checkNbtCompoundFilter(source, filterTag, failLabel));
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
                if(scoreHolder.name() != null) {
                    var textLocation = context.<TComponent>createLocal();
                    context.writeInstruction(Instructions.loadScoreTextNamed(textLocation, scoreHolder.name().isStatic() ? scoreHolder.name().getStaticValue() : scoreHolder.name().toString(), objective));
                    if(!text.style().isEmpty() || !text.siblings().isEmpty()) {
                        var mutableText = context.<TMutableComponent>createLocal();
                        context.writeInstruction(Instructions.copyText(mutableText, textLocation));
                        applyStyleAndSiblings(context, text, mutableText);
                        return mutableText;
                    }
                    return textLocation;
                }
            }
            if(text.content() instanceof TextContent.NbtText(NbtPath path, Text separator, NbtDataLocation source)) {
                if(source instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    var textLocation = context.<TComponent>createLocal();
                    context.writeInstruction(Instructions.getNbtText(textLocation, storage, nbtPathToString(path)));
                    if(!text.style().isEmpty() || !text.siblings().isEmpty()) {
                        var mutableText = context.<TMutableComponent>createLocal();
                        context.writeInstruction(Instructions.copyText(mutableText, textLocation));
                        applyStyleAndSiblings(context, text, mutableText);
                        return mutableText;
                    }
                    return textLocation;
                }
            }
            if(text.content().isConstant()) {
                var innerText = new Text(List.of(), text.style(), text.content());
                var textLocation = context.<TMutableComponent>createLocal();
                context.writeInstruction(Instructions.copyText(textLocation, loadText(context, innerText)));
                applyStyleAndSiblings(context, text, textLocation);
                return textLocation;
            }
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_TEXT, context.getSource(), "Compiling " + text + " is not implemented yet"));
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

    // Извлечь raw строку из CommandParams (только для CommandFallbackVanilla — остальные ≈ unreachable в if/unless items контексте)
    private static String getRawCommand(CommandParams cmd) {
        if(cmd instanceof CommandFallbackVanilla(String raw)) return raw;
        return "true"; // safe no-op
    }

    // Вспомогательный метод — vanilla fallback через executeVanillaCommand с текущим стеком
    private static void emitVanillaFallback(CompilationContext context, String command) {
        var current = context.current();
        var stack = context.<TCommandSourceStack>createLocal();
        context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
        context.writeInstruction(Instructions.executeVanillaCommand(stack, command));
    }

    // Реконструкция строки nbt_path для vanilla fallback
    private static String nbtPathToString(NbtPath path) {
        var sb = new StringBuilder();
        boolean first = true;
        for(var part : path.parts()) {
            switch(part) {
                case NbtPathPart.Named(String name) -> {
                    if(!first) sb.append('.');
                    if(name.contains(".") || name.contains(" ") || name.contains(":")) {
                        sb.append('"').append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                    } else {
                        sb.append(name);
                    }
                }
                case NbtPathPart.ArrayIndexInteger(int idx) -> sb.append('[').append(idx).append(']');
                case NbtPathPart.ArrayIndexCompound(NbtValue.Compound compound) -> sb.append('[').append(nbtValueToString(compound)).append(']');
                case NbtPathPart.CompoundCheck(NbtValue.Compound compound) -> sb.append(nbtValueToString(compound));
            }
            if(part instanceof NbtPathPart.Named) first = false;
        }
        return sb.toString();
    }

    private static String nbtValueToString(NbtValue value) {
        return switch(value) {
            case NbtValue.Compound(var entries) -> {
                var sb = new StringBuilder("{");
                var iter = entries.entrySet().iterator();
                while(iter.hasNext()) {
                    var e = iter.next();
                    sb.append(e.getKey()).append(':').append(nbtValueToString(e.getValue()));
                    if(iter.hasNext()) sb.append(',');
                }
                yield sb.append('}').toString();
            }
            case NbtValue.Array(var values) -> {
                var sb = new StringBuilder("[");
                for(int i = 0; i < values.size(); i++) {
                    if(i > 0) sb.append(',');
                    sb.append(nbtValueToString(values.get(i)));
                }
                yield sb.append(']').toString();
            }
            case NbtValue.ByteArray(var bytes) -> {
                var sb = new StringBuilder("[B;");
                for(int i = 0; i < bytes.length; i++) { if(i > 0) sb.append(','); sb.append(bytes[i]).append('b'); }
                yield sb.append(']').toString();
            }
            case NbtValue.IntegerArray(var ints) -> {
                var sb = new StringBuilder("[I;");
                for(int i = 0; i < ints.length; i++) { if(i > 0) sb.append(','); sb.append(ints[i]); }
                yield sb.append(']').toString();
            }
            case NbtValue.LongArray(var longs) -> {
                var sb = new StringBuilder("[L;");
                for(int i = 0; i < longs.length; i++) { if(i > 0) sb.append(','); sb.append(longs[i]).append('l'); }
                yield sb.append(']').toString();
            }
            case NbtValue.ByteLiteral(byte v) -> v + "b";
            case NbtValue.ShortLiteral(short v) -> v + "s";
            case NbtValue.IntegerLiteral(int v) -> String.valueOf(v);
            case NbtValue.LongLiteral(long v) -> v + "l";
            case NbtValue.FloatLiteral(float v) -> v + "f";
            case NbtValue.DoubleLiteral(double v) -> v + "d";
            case NbtValue.StringLiteral(String v) -> '"' + v.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        };
    }

    private static String nbtDataLocationToString(NbtDataLocation loc) {
        return switch(loc) {
            case NbtDataLocation.Storage(DataLocation storage) -> "storage " + storage;
            case NbtDataLocation.Entity(SelectorArgument selector) -> "entity " + selector;
            case NbtDataLocation.Block(WorldCoordinates pos) -> "block " + worldCoordsToString(pos);
        };
    }

    private static String worldCoordsToString(WorldCoordinates pos) {
        return coordToStr(pos.x(), pos.isXRelative()) + " "
             + coordToStr(pos.y(), pos.isYRelative()) + " "
             + coordToStr(pos.z(), pos.isZRelative());
    }

    private static String coordToStr(double v, boolean relative) {
        if(relative) return v == 0.0 ? "~" : "~" + v;
        return String.valueOf(v);
    }

    private static String rangeToString(IntRangeArgument range) {
        if(range.minimum() != null && range.maximum() != null && range.minimum().equals(range.maximum())) {
            return String.valueOf(range.minimum());
        }
        String min = range.minimum() != null ? String.valueOf(range.minimum()) : "";
        String max = range.maximum() != null ? String.valueOf(range.maximum()) : "";
        return min + ".." + max;
    }

    // data modify prepend/append для storage — нативная реализация
    private static void compileDataModifyListOpValue(CompilationContext context, DataLocation storage, NbtPath path, NbtValue setValue, boolean prepend) {
        var storageLocal = context.<TCompoundTag>createLocal();
        context.writeInstruction(Instructions.getNbtStorage(storageLocal, storage));
        var parts = path.parts();
        var codeLocation = context.current().writeLabel(context.instructionWriter);
        try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
            var listContainer = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), false, innerContext.data().contextEndLabel());
            var listTag = context.<TListTag>createLocal();
            innerContext.writeInstruction(Instructions.tryAsNbtList(listTag, listContainer, innerContext.data().contextEndLabel()));
            var tagLocal = context.<TTag>createLocal();
            var tagCopied = context.<TTag>createLocal();
            innerContext.writeInstruction(Instructions.loadNbtValue(tagLocal, setValue));
            innerContext.writeInstruction(Instructions.cloneNbtValue(tagCopied, tagLocal));
            if(prepend) {
                innerContext.writeInstruction(Instructions.nbtListPrepend(listTag, tagCopied, innerContext.data().contextEndLabel()));
            } else {
                innerContext.writeInstruction(Instructions.nbtListAppend(listTag, tagCopied, innerContext.data().contextEndLabel()));
            }
        }
        context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
    }

}
