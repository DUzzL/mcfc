package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.params.*;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.ObjectComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.SetRandomizedWeatherData;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.List;
import java.util.function.Consumer;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.*;

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

    private static void compile(CompilationContext context, ForkingContext forkingContext, CommandParams command, ResultContext result) {
        if(command instanceof CommandDataModifySetValue cmdData) {
            if(cmdData.destination() instanceof NbtDataLocation.Storage loc) {
                var storageLocal = context.<NbtCompound>createLocal();
                var setCountLocal = context.<Integer>createLocal();

                context.writeInstruction(Instructions.getNbtStorage(storageLocal, loc.storage()));
                context.writeInstruction(Instructions.assign(setCountLocal, LocationData.ofInteger(0)));

                var parts = cmdData.path().parts();

                var codeLocation = context.current().writeLabel(context.instructionWriter);
                try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                    var value = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, innerContext.data().contextEndLabel());

                    var last = parts.get(parts.size() - 1);
                    var tagLocal = context.<NbtTag>createLocal();
                    var tagCopiedLocal = context.<NbtTag>createLocal();
                    innerContext.writeInstruction(Instructions.loadNbtValue(tagLocal, cmdData.value()));
                    innerContext.writeInstruction(Instructions.cloneNbtValue(tagCopiedLocal, tagLocal));
                    assignNbtValue(context, value, last, tagCopiedLocal, setCountLocal, innerContext.data().contextEndLabel());
                }

                context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
                context.writeInstruction(Instructions.setNbtStorage(loc.storage(), storageLocal));
            }else {
                throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_MODIFY_ACCESS, context.getSource(), "Modifying nbt in block/entity is not implemented yet"));
            }
        }else if(command instanceof CommandDifficultySet cmdData) {
            var difficultyLocation = context.<DifficultyValue>createLocal();
            context.writeInstruction(Instructions.mapDifficulty(difficultyLocation, cmdData.difficulty()));
            context.writeInstruction(Instructions.setDifficulty(difficultyLocation));
        }else if(command instanceof CommandExecuteAlign cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var pos = context.getPosition()) {
                    var newPosition = context.<Vec3>createLocal();
                    pos.writeInstruction(Instructions.alignPosition(newPosition, pos.content(), cmdData.swizzle()));
                    try(var ignored = context.enterWithoutValue(pos.data().withCurrentPosition(newPosition))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteAnchored cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var ignored = context.enterWithoutValue(context.current().withCurrentAnchor(cmdData.anchor()))) {
                    compile(context, fContext.content(), cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var selector = context.forSelector(cmdData.targets(), result)) {
                    try(var ignored = context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteAt cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var selector = context.forSelector(cmdData.targets(), result)) {
                    var world = context.<World>createLocal();
                    var position = context.<Vec3>createLocal();
                    var rotation = context.<Vec2>createLocal();
                    selector.writeInstruction(Instructions.getEntityWorld(world, selector.content()));
                    selector.writeInstruction(Instructions.getEntityPosition(position, selector.content()));
                    selector.writeInstruction(Instructions.getEntityRotation(rotation, selector.content()));
                    try(var ignored = context.enterWithoutValue(selector.data().withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteIfEntity cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var ignored = context.forSelector(cmdData.entities().limit(1), result)) {
                    compile(context, fContext.content(), cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteIfFunction cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var current = context.current();
                var stack = context.<CommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                var returnValue = context.<Long>createLocal();
                context.writeInstruction(Instructions2.callFunctionsWithResult(returnValue, cmdData.functions(), stack, null));
                context.writeInstruction(Instructions.jumpIfNullReturnValue(returnValue, fContext.endLabel()));
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteIfScoreRange cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle(), result)) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), result.markAndGetErrorLabel(), false)) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                        context.jumpIfNotMatchesRange(scoreData.data(), scoreValue, cmdData.range(), scoreData.data().contextEndLabel());
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteIfScoreCompare cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var target = context.forScoreAccessors(cmdData.target().selectSingle(), result)) {
                    try(var source = context.forScoreAccessors(cmdData.source().selectSingle(), result)) {
                        try(var targetScore = context.getScoreData(target.content(), result.markAndGetErrorLabel(), false)) {
                            try(var sourceScore = context.getScoreData(source.content(), result.markAndGetErrorLabel(), false)) {
                                var targetValue = context.<Integer>createLocal();
                                var sourceValue = context.<Integer>createLocal();
                                sourceScore.writeInstruction(Instructions.getScoreValue(targetValue, targetScore.content()));
                                sourceScore.writeInstruction(Instructions.getScoreValue(sourceValue, sourceScore.content()));
                                sourceScore.writeInstruction(Instructions.ifNumberCompareJump(cmdData.comparison().toNumberComparisonJump(), targetValue, sourceValue, sourceScore.data().contextEndLabel()));
                                compile(context, fContext.content(), cmdData.command(), result);
                            }
                        }
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnOrigin cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var traceableEntity = context.<TraceableEntity>createLocal();
                    currentEntity.writeInstruction(Instructions.tryAsTraceableEntity(traceableEntity, currentEntity.content(), currentEntity.endLabel()));
                    var origin = context.<Entity>createLocal();
                    currentEntity.writeInstruction(Instructions.getEntityOrigin(origin, traceableEntity));
                    currentEntity.writeInstruction(Instructions.ifNullJump(origin, currentEntity.endLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(origin))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnTarget cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var targetingEntity = context.<TargetingEntity>createLocal();
                    currentEntity.writeInstruction(Instructions.tryAsTargetingEntity(targetingEntity, currentEntity.content(), currentEntity.endLabel()));
                    var target = context.<LivingEntity>createLocal();
                    currentEntity.writeInstruction(Instructions.getEntityTarget(target, targetingEntity));
                    currentEntity.writeInstruction(Instructions.ifNullJump(target, currentEntity.endLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(target))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnVehicle cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var vehicle = context.<Entity>createLocal();
                    currentEntity.writeInstruction(Instructions.getEntityVehicle(vehicle, currentEntity.content()));
                    currentEntity.writeInstruction(Instructions.ifNullJump(vehicle, currentEntity.endLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(vehicle))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecutePositionedAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var targets = context.forSelector(cmdData.targets(), result)) {
                    var position = context.<Vec3>createLocal();
                    targets.writeInstruction(Instructions.getEntityPosition(position, targets.content()));
                    try(var ignored = context.enterWithoutValue(targets.data().withCurrentPosition(position))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecutePositionedPos cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var position = context.applyPosition(cmdData.pos())) {
                    try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()).withCurrentAnchor(LocationAnchor.FEET))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteRotatedAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var targets = context.forSelector(cmdData.targets(), result)) {
                    var rotation = context.<Vec2>createLocal();
                    targets.writeInstruction(Instructions.getEntityRotation(rotation, targets.content()));
                    try(var ignored = context.enterWithoutValue(targets.data().withCurrentRotation(rotation))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteRotatedRot cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var rotation = context.applyRotation(cmdData.rot())) {
                    try(var ignored = context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteRun cmdData) {
            try(var ignored = context.generateForkExecCode(forkingContext)) {
                compile(context, forkingContext, cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteStoreScore cmdData) {
            try(var ignored = context.generateForkExecCode(forkingContext)) {
                var localResult = ResultContext.create(context, cmdData.storeLocation() == ResultStoreLocationType.SUCCESS, cmdData.storeLocation() == ResultStoreLocationType.RESULT);
                compile(context, null, cmdData.command(), localResult);
                localResult.endGeneration();
                localResult.checkImplemented(cmdData.command());
                var localResultValue = cmdData.storeLocation() == ResultStoreLocationType.SUCCESS ? localResult.getSuccess() : localResult.getResult();
                try(var scoreAccessor = context.forScoreAccessors(cmdData.targets(), result)) {
                    try(var scoreData = context.getScoreDataAutoCreate(scoreAccessor.content())) {
                        scoreData.writeInstruction(Instructions.setScore(scoreData.content(), localResultValue));
                    }
                }
            }
        }else if(command instanceof CommandExecuteStoreStorage cmdData) {
            try(var ignored = context.generateForkExecCode(forkingContext)) {
                var localResult = ResultContext.create(context, cmdData.storeLocation() == ResultStoreLocationType.SUCCESS, cmdData.storeLocation() == ResultStoreLocationType.RESULT);
                compile(context, null, cmdData.command(), localResult);
                localResult.endGeneration();
                localResult.checkImplemented(cmdData.command());
                var localResultValue = cmdData.storeLocation() == ResultStoreLocationType.SUCCESS ? localResult.getSuccess() : localResult.getResult();

                var storageLocal = context.<NbtCompound>createLocal();
                var setCountLocal = context.<Integer>createLocal();

                context.writeInstruction(Instructions.getNbtStorage(storageLocal, cmdData.storage()));
                context.writeInstruction(Instructions.assign(setCountLocal, LocationData.ofInteger(0)));

                var parts = cmdData.path().parts();

                try(var innerContext = context.enterLocalScope()) {
                    var value = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, innerContext.endLabel());

                    var last = parts.get(parts.size() - 1);
                    var tagLocal = context.<NbtTag>createLocal();
                    if(cmdData.storeType() == ResultStoreType.INT) {
                        if(cmdData.scale() == 1.0d) {
                            innerContext.writeInstruction(Instructions.createIntTag(tagLocal, localResultValue));
                        }else {
                            var resultDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions.numberToDouble(resultDouble, localResultValue));
                            var multipliedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(cmdData.scale())));
                            var convertedInteger = context.<Integer>createLocal();
                            innerContext.writeInstruction(Instructions.numberToInteger(convertedInteger, multipliedDouble));
                            innerContext.writeInstruction(Instructions.createIntTag(tagLocal, convertedInteger));
                        }
                    }else if(cmdData.storeType() == ResultStoreType.DOUBLE) {
                        var convertedDouble = context.<Double>createLocal();
                        innerContext.writeInstruction(Instructions.numberToDouble(convertedDouble, localResultValue));
                        if(cmdData.scale() == 1.0d) {
                            innerContext.writeInstruction(Instructions.createDoubleTag(tagLocal, convertedDouble));
                        }else {
                            var multipliedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, convertedDouble, LocationData.ofDouble(cmdData.scale())));
                            innerContext.writeInstruction(Instructions.createDoubleTag(tagLocal, multipliedDouble));
                        }
                    }else if(cmdData.storeType() == ResultStoreType.FLOAT) {
                        if(cmdData.scale() == 1.0d) {
                            var convertedFloat = context.<Float>createLocal();
                            innerContext.writeInstruction(Instructions.numberToFloat(convertedFloat, localResultValue));
                            innerContext.writeInstruction(Instructions.createFloatTag(tagLocal, convertedFloat));
                        }else {
                            var resultDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions.numberToDouble(resultDouble, localResultValue));
                            var multipliedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(cmdData.scale())));
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
                context.writeInstruction(Instructions.setNbtStorage(cmdData.storage(), storageLocal));
            }
        }else if(command instanceof CommandExecuteUnlessEntity cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                var codeLocation = context.current().writeLabel(context.instructionWriter);
                try(var ignored = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                    try(var selector = context.forSelector(cmdData.entities().limit(1), result)) {
                        selector.writeInstruction(Instructions.jump(contextLabel));
                    }
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessFunction cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var current = context.current();
                var stack = context.<CommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                var returnValue = context.<Long>createLocal();
                context.writeInstruction(Instructions2.callFunctionsWithResult(returnValue, cmdData.functions(), stack, null));
                context.writeInstruction(Instructions.jumpIfNotNullReturnValue(returnValue, fContext.endLabel()));
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessScoreRange cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                try(var scope = context.enterLocalScope()) {
                    var scopeResult = ResultContext.create(context, scope.endLabel());
                    try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle(), scopeResult)) {
                        try(var scoreData = context.getScoreData(scoreAccessor.content(), scopeResult.markAndGetErrorLabel(), false)) {
                            var scoreValue = context.<Integer>createLocal();
                            scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                            context.jumpIfMatchesRange(scoreData.data(), scoreValue, cmdData.range(), contextLabel);
                        }
                    }
                    scopeResult.close();
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessScoreCompare cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                try(var scope = context.enterLocalScope()) {
                    var scopeResult = ResultContext.create(context, scope.endLabel());
                    try(var target = context.forScoreAccessors(cmdData.target().selectSingle(), scopeResult)) {
                        try(var source = context.forScoreAccessors(cmdData.source().selectSingle(), scopeResult)) {
                            try(var targetScore = context.getScoreData(target.content(), scopeResult.markAndGetErrorLabel(), false)) {
                                try(var sourceScore = context.getScoreData(source.content(), scopeResult.markAndGetErrorLabel(), false)) {
                                    var targetValue = context.<Integer>createLocal();
                                    var sourceValue = context.<Integer>createLocal();
                                    sourceScore.writeInstruction(Instructions.getScoreValue(targetValue, targetScore.content()));
                                    sourceScore.writeInstruction(Instructions.getScoreValue(sourceValue, sourceScore.content()));
                                    sourceScore.writeInstruction(Instructions.ifNumberCompareJump(cmdData.comparison().negate().toNumberComparisonJump(), targetValue, sourceValue, contextLabel));
                                }
                            }
                        }
                    }
                    scopeResult.close();
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExperienceAdd cmdData) {
            try(var targets = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions.addExperiencePoints(playerLocation, context.getResolvedIntegerMacro(cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceAddPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions.addExperiencePoints(playerLocation, context.getResolvedIntegerMacro(cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceAddLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions.addExperienceLevels(playerLocation, context.getResolvedIntegerMacro(cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceSet cmdData) {
            try(var targets = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions.setExperiencePoints(playerLocation, context.getResolvedIntegerMacro(cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceSetPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions.setExperiencePoints(playerLocation, context.getResolvedIntegerMacro(cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceSetLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions.setExperienceLevels(playerLocation, context.getResolvedIntegerMacro(cmdData.amount())));
            }
        }else if(command instanceof CommandFunction cmdData) {
            result.markImplemented();
            var current = context.current();
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            callFunctionsAndProvideResult(context, result, cmdData.functions(), stack, null);
        }else if(command instanceof CommandFunctionArgs cmdData) {
            result.markImplemented();
            var current = context.current();
            var arguments = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions.loadNbtValue(arguments.forceCast(), cmdData.arguments()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            callFunctionsAndProvideResult(context, result, cmdData.functions(), stack, arguments);
        }else if(command instanceof CommandFunctionArgsNbt cmdData) {
            result.markImplemented();
            var current = context.current();
            forLocatedBaseNbtData(context, result, cmdData.arguments(), arguments -> {
                var clonedArguments = context.<NbtTag>createLocal();
                var validArguments = context.<NbtCompound>createLocal();
                context.writeInstruction(Instructions.cloneNbtValue(clonedArguments, arguments));
                context.writeInstruction(Instructions.tryAsNbtCompound(validArguments, clonedArguments, result.markAndGetErrorLabel()));
                var stack = context.<CommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                callFunctionsAndProvideResult(context, result, cmdData.functions(), stack, validArguments);
            });
        }else if(command instanceof CommandFunctionArgsNbtPath cmdData) {
            result.markImplemented();
            var current = context.current();
            forLocatedBaseNbtData(context, result, cmdData.arguments(), storageLocal -> {
                var parts = cmdData.path().parts();
                var value = getNbtPathValue(context, storageLocal, parts, false, result.markAndGetErrorLabel());
                context.writeInstruction(Instructions.ifNullJump(value, result.markAndGetErrorLabel()));
                var clonedTag = context.<NbtTag>createLocal();
                context.writeInstruction(Instructions.cloneNbtValue(clonedTag, value));
                var compoundTag = context.<NbtCompound>createLocal();
                context.writeInstruction(Instructions.tryAsNbtCompound(compoundTag, clonedTag, result.markAndGetErrorLabel()));
                var stack = context.<CommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                callFunctionsAndProvideResult(context, result, cmdData.functions(), stack, compoundTag);
            });
        }else if(command instanceof CommandGamemode cmdData) {
            try(var selector = context.forSelector(cmdData.targets(), result)) {
                var playerLocation = context.<Player>createLocal();
                selector.writeInstruction(Instructions.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                var mappedGamemode = context.<GameType>createLocal();
                selector.writeInstruction(Instructions.mapGamemode(mappedGamemode, cmdData.gamemode()));
                selector.writeInstruction(Instructions.setGamemode(playerLocation, mappedGamemode));
            }
        }else if(command instanceof CommandKill cmdData) {
            try(var selector = context.forSelector(cmdData.targets(), result)) {
                try(var world = context.getWorld()) {
                    selector.writeInstruction(Instructions.killEntity(selector.content(), world.content()));
                }
            }
        }else if(command instanceof CommandReturnFail) {
            context.writeInstruction(Instructions.returnFail());
        }else if(command instanceof CommandReturnRun cmdData) {
            var current = context.current();

            var localResult = ResultContext.create(context, true, true);
            compile(context, null, cmdData.command(), localResult);
            localResult.endGeneration();
            localResult.checkImplemented(cmdData.command());

            var jumpLabel = current.writeLabel(context.instructionWriter);
            context.instructionWriter.addInstructionBefore(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, localResult.getSuccess(), LocationData.ofInteger(0), jumpLabel), jumpLabel);
            context.instructionWriter.addInstructionBefore(Instructions.returnSuccess(localResult.getResult()), jumpLabel);
            current.writeInstruction(context.instructionWriter, Instructions.returnFail());

        }else if(command instanceof CommandReturnValue cmdData) {
            context.writeInstruction(Instructions.returnSuccess(LocationData.ofInteger(cmdData.value())));
        }else if(command instanceof CommandScoreboardPlayersAdd cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets(), result)) {
                try(var scoreData = context.getScoreDataAutoCreate(targets.content())) {
                    var scoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                    var updatedScoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions.valueAdd(updatedScoreValue, scoreValue, context.getResolvedIntegerMacro(cmdData.score())));
                    scoreData.writeInstruction(Instructions.setScore(scoreData.content(), updatedScoreValue));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersEnable cmdData) {
            try(var objective = context.getObjective(cmdData.targets().objective(), result)) {
                var criterion = context.<ScoreboardCriterion>createLocal();
                objective.writeInstruction(Instructions.getObjectiveCriterion(criterion, objective.content()));
                var mappedCriterion = context.<ScoreboardCriterion>createLocal();
                objective.writeInstruction(Instructions.mapObjectiveCriterion(mappedCriterion, DataRegistries.SCOREBOARD_CRITERIA.getOrThrow("trigger")));
                objective.writeInstruction(Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_IDENTITY_EQUAL, criterion, mappedCriterion, objective.data().contextEndLabel()));
                try(var name = context.forScoreHolder(cmdData.targets().scoreHolder(), result)) {
                    try(var scoreData = context.getScoreDataAutoCreate(new ScoreAccessor(objective.content(), name.content()))) {
                        scoreData.writeInstruction(Instructions.scoreUnlock(scoreData.content()));
                    }
                }
            }
        }else if(command instanceof CommandScoreboardPlayersGet cmdData) {
            result.markImplemented();
            try(var targets = context.forScoreAccessors(cmdData.targets(), result)) {
                try(var scoreData = context.getScoreData(targets.content(), result.markAndGetErrorLabel(), false)) {
                    var res = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions.getScoreValue(res, scoreData.content()));
                    result.updateResultValue(res);
                }
            }
        }else if(command instanceof CommandScoreboardPlayersOperation cmdData) {
            result.markImplemented();
            try(var targets = context.forScoreAccessors(cmdData.targets(), result)) {
                try(var source = context.forScoreAccessors(cmdData.source(), result)) {
                    try(var targetScore = context.getScoreDataAutoCreate(targets.content())) {
                        try(var sourceScore = context.getScoreDataAutoCreate(source.content())) {
                            switch(cmdData.operation()) {
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
        }else if(command instanceof CommandScoreboardPlayersRemove cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets(), result)) {
                try(var scoreData = context.getScoreDataAutoCreate(targets.content())) {
                    var scoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                    var updatedScoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions.valueSub(updatedScoreValue, scoreValue, context.getResolvedIntegerMacro(cmdData.score())));
                    scoreData.writeInstruction(Instructions.setScore(scoreData.content(), updatedScoreValue));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersResetAll cmdData) {
            try(var name = context.forScoreHolder(cmdData.targets(), result)) {
                name.writeInstruction(Instructions.resetPlayerScore(name.content()));
            }
        }else if(command instanceof CommandScoreboardPlayersResetObjective cmdData) {
            try(var objective = context.getObjective(cmdData.targets().objective(), result)) {
                try(var name = context.forScoreHolder(cmdData.targets().scoreHolder(), result)) {
                    name.writeInstruction(Instructions.resetScore(objective.content(), name.content()));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersSet cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets(), result)) {
                try(var scoreData = context.getScoreDataAutoCreate(targets.content())) {
                    scoreData.writeInstruction(Instructions.setScore(scoreData.content(), context.getResolvedIntegerMacro(cmdData.score())));
                }
            }
        }else if(command instanceof CommandTagAdd cmdData) {
            try(var entity = context.forSelector(cmdData.targets(), result)) {
                entity.writeInstruction(Instructions.addEntityTag(entity.content(), LocationData.ofString(cmdData.name())));
            }
        }else if(command instanceof CommandTagRemove cmdData) {
            try(var entity = context.forSelector(cmdData.targets(), result)) {
                entity.writeInstruction(Instructions.removeEntityTag(entity.content(), LocationData.ofString(cmdData.name())));
            }
        }else if(command instanceof CommandTitleActionbar cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions.sendActionbar(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTitleClear cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                players.writeInstruction(Instructions.sendTitlesClear(playerLocation));
            }
        }else if(command instanceof CommandTitleReset cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                players.writeInstruction(Instructions.sendTitlesReset(playerLocation));
            }
        }else if(command instanceof CommandTitleSubtitle cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions.sendSubtitle(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTitleTimes cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                players.writeInstruction(Instructions.sendTitlesTimes(playerLocation, LocationData.ofInteger(cmdData.fadeIn()), LocationData.ofInteger(cmdData.stay()), LocationData.ofInteger(cmdData.fadeOut())));
            }
        }else if(command instanceof CommandTitleTitle cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions.sendTitle(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTellraw cmdData) {
            try(var players = context.forSelector(cmdData.players(), result)) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions.sendSystemMessage(playerLocation, textLocation));
            }
        }else if(command instanceof CommandWeatherClearTimed cmdData) {
            context.writeInstruction(Instructions.setWeatherStatic(LocationData.ofInteger(cmdData.duration()), LocationData.ofInteger(0), false, false));
        }else if(command instanceof CommandWeatherClear) {
            context.writeInstruction(Instructions.setWeatherDynamic(SetRandomizedWeatherData.CLEAR));
        }else if(command instanceof CommandWeatherRainTimed cmdData) {
            context.writeInstruction(Instructions.setWeatherStatic(LocationData.ofInteger(0), LocationData.ofInteger(cmdData.duration()), true, false));
        }else if(command instanceof CommandWeatherRain) {
            context.writeInstruction(Instructions.setWeatherDynamic(SetRandomizedWeatherData.RAIN));
        }else if(command instanceof CommandWeatherThunderTimed cmdData) {
            context.writeInstruction(Instructions.setWeatherStatic(LocationData.ofInteger(0), LocationData.ofInteger(cmdData.duration()), true, true));
        }else if(command instanceof CommandWeatherThunder) {
            context.writeInstruction(Instructions.setWeatherDynamic(SetRandomizedWeatherData.THUNDER));
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNKNOWN_COMMAND, context.getSource(), "Not implemented command: " + command));
        }
    }

    private static void callFunctionsAndProvideResult(CompilationContext context, ResultContext resultContext, List<DataLocation> functions, LocationData<? extends CommandSourceStack> stack, LocationData<? extends NbtCompound> arguments) {
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

    private static void forLocatedBaseNbtData(CompilationContext context, ResultContext resultContext, NbtDataLocation input, Consumer<LocationData<? extends NbtTag>> consumer) {
        if(input instanceof NbtDataLocation.Entity loc) {
            throw new RuntimeException("entity nbt not implemented");
        }else if(input instanceof NbtDataLocation.Storage loc) {
            var nbtLocal = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions.getNbtStorage(nbtLocal, loc.storage()));
            consumer.accept(nbtLocal);
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS, context.getSource(), "Accessing block nbt is not implemented yet"));
        }
    }

    private static LocationData<? extends NbtTag> getNbtPathValue(CompilationContext context, LocationData<? extends NbtTag> source, List<NbtPathPart> parts, boolean tryCreate, LabelData failLabel) {
        for(var part : parts) {
            if(part instanceof NbtPathPart.Named named) {
                var compound = context.<NbtCompound>createLocal();
                context.writeInstruction(Instructions.tryAsNbtCompound(compound, source, failLabel));
                var storage = context.<NbtTag>createLocal();
                if(tryCreate) {
                    context.writeInstruction(Instructions.getNbtFromCompoundOrCreate(storage, compound, LocationData.ofString(named.name())));
                }else {
                    context.writeInstruction(Instructions.getNbtFromCompound(storage, compound, LocationData.ofString(named.name())));
                }
                source = storage;
            }else if(part instanceof NbtPathPart.ArrayIndexInteger indexed) {
                var listAccess = accessNbtList(context, source, indexed, failLabel);
                var storage = context.<NbtTag>createLocal();
                context.writeInstruction(Instructions.getNbtFromList(storage, listAccess.list, listAccess.index));
                source = storage;
            }else {
                throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot get nbt path from " + part));
            }
        }
        return source;
    }

    private static void assignNbtValue(CompilationContext context, LocationData<? extends NbtTag> source, NbtPathPart accessor, LocationData<? extends NbtTag> value, LocationData<Integer> modifiedCount, LabelData failLabel) {
        if(accessor instanceof NbtPathPart.Named named) {
            var valueCompound = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions.tryAsNbtCompound(valueCompound, source, failLabel));
            context.writeInstruction(Instructions.setNbtInCompound(valueCompound, LocationData.ofString(named.name()), value));
        }else if(accessor instanceof NbtPathPart.ArrayIndexInteger indexed) {
            var listAccess = accessNbtList(context, source, indexed, failLabel);
            context.writeInstruction(Instructions.setNbtInList(listAccess.list, listAccess.index, value, failLabel));
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot set nbt value in path " + accessor));
        }
        if(modifiedCount != null) context.writeInstruction(Instructions.valueAdd(modifiedCount, modifiedCount, LocationData.ofInteger(1)));
    }

    private static NbtListAccessResult accessNbtList(CompilationContext context, LocationData<? extends NbtTag> source, NbtPathPart.ArrayIndexInteger indexed, LabelData failLabel) {
        var list = context.<NbtList>createLocal();
        context.writeInstruction(Instructions.tryAsNbtList(list, source, failLabel));
        var length = context.<Integer>createLocal();
        context.writeInstruction(Instructions.getListLength(length, list));
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, LocationData.ofInteger(indexed.index()), length, failLabel));
        var index = context.<Integer>createLocal();

        if(indexed.index() >= 0) {
            context.writeInstruction(Instructions.assign(index, LocationData.ofInteger(indexed.index())));
        }else {
            context.writeInstruction(Instructions.assign(index, length));
            context.writeInstruction(Instructions.valueSub(index, index, LocationData.ofInteger(-indexed.index())));
        }

        return new NbtListAccessResult(list, index);
    }

    private record NbtListAccessResult(LocationData<NbtList> list, LocationData<Integer> index) {}

    private static LocationData<? extends TextComponent> loadText(CompilationContext context, Text text) {
        if(!text.isCompilationSupported()) {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_TEXT, context.getSource(), "Compiling " + text + " is not implemented yet"));
        }

        if(text.isConstant()) {
            var textLocation = context.<TextComponent>createLocal();
            context.writeInstruction(Instructions.loadText(textLocation, text));
            return textLocation;
        }else {
            if(text.content() instanceof TextContent.SelectorText selector) {
                var selectorResult = context.getSelectorList(selector.selector(), null);
                var separator = loadText(context, selector.separator() != null ? selector.separator() : TextContent.SelectorText.DEFAULT_SEPARATOR);
                var textLocation = context.<TextComponentMutable>createLocal();
                context.writeInstruction(Instructions.formatEntityTextList(textLocation, selectorResult, separator));
                if(!text.style().isEmpty()) {
                    var style = context.<TextComponentStyle>createLocal();
                    context.writeInstruction(Instructions.loadTextStyle(style, text.style()));
                    context.writeInstruction(Instructions.setTextStyle(textLocation, style));
                }
                for(var sibling : text.siblings()) {
                    var otherText = loadText(context, sibling);
                    context.writeInstruction(Instructions.appendText(textLocation, otherText));
                }
                return textLocation;
            }
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNREACHABLE_CODE, context.getSource(), "Unreachable code, tried to compile unknown non-constant text: " + text));
        }
    }

}
