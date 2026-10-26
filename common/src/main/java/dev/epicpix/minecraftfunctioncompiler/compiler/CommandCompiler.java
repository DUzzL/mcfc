package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroType;
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
import dev.epicpix.minecraftfunctioncompiler.data.SelectorSort;
import dev.epicpix.minecraftfunctioncompiler.data.SwizzleArgument;
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtValue;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextHoverEvent;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextStyle;
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
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCollectionTag;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TListTag;

import dev.epicpix.minecraftfunctioncompiler.il.locations.TLivingEntity;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TMutableComponent;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjective;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScore;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScoreHolder;
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

import dev.epicpix.minecraftfunctioncompiler.commands.ParsedCommandList;
import java.util.List;
import java.util.function.Consumer;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_STORE;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNSUPPORTED_TEXT;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED;

public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(DataLocation source, List<CommandParams> commands) {
        return compile(source, commands, null);
    }

    /** Compiles {@code commands}; {@code outliner} (null: none) may move execute-chain tails into separate methods. */
    public static List<Instruction> compile(DataLocation source, List<CommandParams> commands, @Nullable ChainOutliner outliner) {
        var context = new CompilationContext(source);
        context.setOutliner(outliner);
        var parsed = commands instanceof ParsedCommandList p ? p : null;
        for(int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
            var command = commands.get(commandIndex);
            context.setCommandSource(parsed != null ? parsed.sourceOf(commandIndex) : null);
            var commandLabel = context.current().writeLabel(context.instructionWriter);
            ResultContext root;
            try(var _ =context.enterWithoutValue(context.current().withContextLabel(commandLabel))) {
                root = ResultContext.root(context, context.current().writeLabel(context.instructionWriter));
                compile(context, null, command, root);
            }
            root.emitPendingReturn();
        }
        return context.end();
    }

    private static void compile(CompilationContext context, @Nullable ForkingContext forkingContext, CommandParams inputCommand, ResultContext result) {
        var outliner = context.getOutliner();
        if(outliner != null && forkingContext == null && !result.tracking() && !ExecuteChains.containsReturn(inputCommand)) {
            var call = outliner.outline(inputCommand);
            if(call != null) {
                context.writeInstruction(Instructions2.callOutlined(call, makeCurrentCommandSourceStack(context)));
                return;
            }
        }
        if(ExecuteChains.isModifier(inputCommand)) {
            compileCommand(context, forkingContext, inputCommand, result);
            return;
        }
        switch(inputCommand) {
            // `return <value>` / `return fail` report to the enclosing store callbacks, then leave the frame.
            case CommandReturnValue(int value) -> {
                result.emitReturn(value);
                return;
            }
            case CommandReturnFail _ -> {
                result.emitReturn(null);
                return;
            }
            default -> {}
        }
        if(!result.tracking()) {
            var terminal = result.beginTerminal();
            compileCommand(context, null, inputCommand, terminal);
            terminal.endTerminal();
            return;
        }
        // Tracked terminal: skip-jumps inside the command must still reach the success report,
        // so the command gets its own scope; failures jump to the terminal's error label.
        ResultContext terminal;
        try(var _ = context.enterLocalScope()) {
            terminal = result.beginTerminal();
            compileCommand(context, null, inputCommand, terminal);
            terminal.checkImplemented(inputCommand);
        }
        terminal.endTerminal();
    }

    private static void compileCommand(CompilationContext context, @Nullable ForkingContext forkingContext, CommandParams inputCommand, ResultContext result) {
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

                    // vanilla: nothing changed -> ERROR_MERGE_UNCHANGED (the command fails)
                    var unchangedLabel = result.generatingSuccess() ? result.markAndGetErrorLabel() : context.current().contextEndLabel();
                    context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, setCountLocal, LocationData.ofInteger(0), unchangedLabel));
                    context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
                    result.updateResultValue(setCountLocal);
                }else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }
            case CommandDifficultySet(Difficulty difficulty) -> {
                var difficultyLocation = context.<TDifficulty>createLocal();
                context.writeInstruction(Instructions.mapDifficulty(difficultyLocation, difficulty));
                context.writeInstruction(Instructions.setDifficulty(difficultyLocation));
            }
            case CommandExecuteAlign(SwizzleArgument swizzle, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var pos = context.getPosition()) {
                        var newPosition = context.<TVec3>createLocal();
                        pos.writeInstruction(Instructions.alignPosition(newPosition, pos.content(), swizzle));
                        try(var _ =context.enterWithoutValue(pos.data().withCurrentPosition(newPosition))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteAnchored(LocationAnchor anchor, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var _ =context.enterWithoutValue(context.current().withCurrentAnchor(anchor))) {
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteAs(SelectorArgument targets, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var _ =context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteAt(SelectorArgument targets, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var selector = context.forSelector(targets, result)) {
                        var world = context.<TServerLevel>createLocal();
                        var position = context.<TVec3>createLocal();
                        var rotation = context.<TVec2>createLocal();
                        selector.writeInstruction(Instructions.getEntityWorld(world, selector.content()));
                        selector.writeInstruction(Instructions.getEntityPosition(position, selector.content()));
                        selector.writeInstruction(Instructions.getEntityRotation(rotation, selector.content()));
                        try(var _ =context.enterWithoutValue(selector.data().withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteIfEntity(SelectorArgument entities, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var _ =context.forSelector(entities.limit(1), result)) {
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteIfFunction(List<DataLocation> functions, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
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
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var holder = constantHolder(target);
                    if(holder != null) {
                        context.writeInstruction(Instructions.scoreRefIfMatchesElseJump(holder, target.objective(), rangeMin(range), rangeMax(range), context.current().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                        return;
                    }
                    try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), result)) {
                        // Skip-on-null jumps to the local context end (continue iteration in
                        // `as @a` etc.), not to the function-level error label — vanilla treats
                        // a missing score entry as predicate-false, not as fatal.
                        try(var scoreData = context.getScoreData(scoreAccessor.content(), context.current().contextEndLabel())) {
                            var scoreValue = context.<Integer>createLocal();
                            scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                            context.jumpIfNotMatchesRange(scoreData.data(), scoreValue, range, scoreData.data().contextEndLabel());
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteIfScoreCompare(ScoreAccessorArgument targetArg, ScoreboardCompareOperation comparison, ScoreAccessorArgument sourceArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var targetHolder = constantHolder(targetArg);
                    var sourceHolder = constantHolder(sourceArg);
                    if(targetHolder != null && sourceHolder != null) {
                        context.writeInstruction(Instructions.scoreRefIfCompareElseJump(targetHolder, targetArg.objective(), LocationData.ofInteger(comparison.ordinal()), sourceHolder, sourceArg.objective(), context.current().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                        return;
                    }
                    try(var target = context.forScoreAccessors(targetArg.selectSingle(), result)) {
                        try(var source = context.forScoreAccessors(sourceArg.selectSingle(), result)) {
                            try(var targetScore = context.getScoreData(target.content(), context.current().contextEndLabel())) {
                                try(var sourceScore = context.getScoreData(source.content(), context.current().contextEndLabel())) {
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
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var traceableEntity = context.<TTraceableEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.tryAsTraceableEntity(traceableEntity, currentEntity.content(), currentEntity.endLabel()));
                        var origin = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityOrigin(origin, traceableEntity));
                        currentEntity.writeInstruction(Instructions.ifNullJump(origin, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(origin))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnTarget(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var targetingEntity = context.<TTargeting>createLocal();
                        currentEntity.writeInstruction(Instructions.tryAsTargetingEntity(targetingEntity, currentEntity.content(), currentEntity.endLabel()));
                        var target = context.<TLivingEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityTarget(target, targetingEntity));
                        currentEntity.writeInstruction(Instructions.ifNullJump(target, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(target))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnVehicle(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var vehicle = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityVehicle(vehicle, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(vehicle, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(vehicle))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecutePositionedAs(SelectorArgument targetsArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var targets = context.forSelector(targetsArg, result)) {
                        var position = context.<TVec3>createLocal();
                        targets.writeInstruction(Instructions.getEntityPosition(position, targets.content()));
                        try(var _ =context.enterWithoutValue(targets.data().withCurrentPosition(position))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecutePositionedPos(WorldCoordinates pos, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var position = context.applyPosition(pos)) {
                        try(var _ =context.enterWithoutValue(position.data().withCurrentPosition(position.content()).withCurrentAnchor(LocationAnchor.FEET))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteRotatedAs(SelectorArgument targetsArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var targets = context.forSelector(targetsArg, result)) {
                        var rotation = context.<TVec2>createLocal();
                        targets.writeInstruction(Instructions.getEntityRotation(rotation, targets.content()));
                        try(var _ =context.enterWithoutValue(targets.data().withCurrentRotation(rotation))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteRotatedRot(RotationCoordinates rot, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var rotation = context.applyRotation(rot)) {
                        try(var _ =context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteRun(CommandParams command) -> {
                try(var _ =context.generateForkExecCode(forkingContext)) {
                    compile(context, null, command, result);
                }
            }
            case CommandExecuteStoreScore(ResultStoreLocationType storeLocation, ScoreAccessorArgument targets, CommandParams command) -> {
                try(var _ =context.generateForkExecCode(forkingContext)) {
                    // Vanilla resolves the objective and the holders when the store modifier runs (before the
                    // body); the callback then writes once per execution of the final command.
                    try(var objective = context.getObjective(targets.objective(), null)) {
                        try(var holders = resolveScoreHolders(context, targets.scoreHolder(), null)) {
                            var sink = new ScoreStoreSink(objective.content(), targets.objective(), holders.content(), storeLocation == ResultStoreLocationType.RESULT);
                            compile(context, null, command, result.withSink(sink));
                        }
                    }
                }
            }
            case CommandExecuteStoreStorage(ResultStoreLocationType storeLocation, DataLocation storage, NbtPath path, ResultStoreType storeType, double scale, CommandParams command) -> {
                if(storeType != ResultStoreType.INT && storeType != ResultStoreType.DOUBLE && storeType != ResultStoreType.FLOAT
                        && storeType != ResultStoreType.BYTE && storeType != ResultStoreType.SHORT && storeType != ResultStoreType.LONG) {
                    throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_STORE, context.getSource(), "Storing into storage with unsupported type: " + storeType));
                }
                try(var _ =context.generateForkExecCode(forkingContext)) {
                    var sink = new StorageStoreSink(storage, path, storeType, scale, storeLocation == ResultStoreLocationType.RESULT);
                    compile(context, null, command, result.withSink(sink));
                }
            }
            case CommandExecuteUnlessEntity(SelectorArgument entities, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var contextLabel = context.current().contextEndLabel();
                    var codeLocation = context.current().writeLabel(context.instructionWriter);
                    try(var _ =context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                        try(var selector = context.forSelector(entities.limit(1), result)) {
                            selector.writeInstruction(Instructions.jump(contextLabel));
                        }
                    }
                    compile(context, fContext.content(), command, result);
                }
            }
            case CommandExecuteUnlessFunction(List<DataLocation> functions, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
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
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    // A missing objective or an empty holder selection aborts this source (vanilla throws);
                    // only a missing score makes the condition false, i.e. `unless` passes.
                    var holder = constantHolder(target);
                    if(holder != null) {
                        context.writeInstruction(Instructions.scoreRefUnlessMatchesElseJump(holder, target.objective(), rangeMin(range), rangeMax(range), context.current().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                        return;
                    }
                    try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), result)) {
                        var skipLabel = context.current().contextEndLabel();
                        try(var scope = context.enterLocalScope()) {
                            try(var scoreData = context.getScoreData(scoreAccessor.content(), scope.endLabel())) {
                                var scoreValue = context.<Integer>createLocal();
                                scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                                context.jumpIfMatchesRange(scoreData.data(), scoreValue, range, skipLabel);
                            }
                        }
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteUnlessScoreCompare(ScoreAccessorArgument targetArg, ScoreboardCompareOperation comparison, ScoreAccessorArgument sourceArg, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var targetHolder = constantHolder(targetArg);
                    var sourceHolder = constantHolder(sourceArg);
                    if(targetHolder != null && sourceHolder != null) {
                        context.writeInstruction(Instructions.scoreRefUnlessCompareElseJump(targetHolder, targetArg.objective(), LocationData.ofInteger(comparison.ordinal()), sourceHolder, sourceArg.objective(), context.current().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                        return;
                    }
                    try(var target = context.forScoreAccessors(targetArg.selectSingle(), result)) {
                        try(var source = context.forScoreAccessors(sourceArg.selectSingle(), result)) {
                            var skipLabel = context.current().contextEndLabel();
                            try(var scope = context.enterLocalScope()) {
                                try(var targetScore = context.getScoreData(target.content(), scope.endLabel())) {
                                    try(var sourceScore = context.getScoreData(source.content(), scope.endLabel())) {
                                        var targetValue = context.<Integer>createLocal();
                                        var sourceValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions.getScoreValue(targetValue, targetScore.content()));
                                        sourceScore.writeInstruction(Instructions.getScoreValue(sourceValue, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions.ifNumberCompareJump(comparison.negate().toNumberComparisonJump(), targetValue, sourceValue, skipLabel));
                                    }
                                }
                            }
                            compile(context, fContext.content(), command, result);
                        }
                    }
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
                // vanilla result: number of killed entities, failure without any; consumed results go to vanilla
                if(result.generatingSuccess() || result.generatingResult()) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                    return;
                }
                try(var selector = context.forSelector(targets, result)) {
                    try(var world = context.getWorld()) {
                        selector.writeInstruction(Instructions.killEntity(selector.content(), world.content()));
                    }
                }
            }
            case CommandReturnRun(CommandParams command) -> {
                // Skip-jumps inside the chain land at this scope's end, i.e. on the `return fail` below.
                try(var _ = context.enterLocalScope()) {
                    compile(context, null, command, result.withSink(ReturnSink.INSTANCE));
                }
                // A deferred `return <value>|fail` (store callbacks ran for every source) returns here.
                result.emitPendingReturn();
                // Reached only when the command did not execute (no source left): vanilla returns failure.
                context.writeInstruction(Instructions.returnFail());
            }
            case CommandScoreboardPlayersAdd(ScoreAccessorArgument targetsArg, MacroTemplate<Integer> score) -> {
                // vanilla result: sum of the targets' new scores; one getOrCreatePlayerScore access per target
                result.markImplemented();
                var holder = constantHolder(targetsArg);
                if(holder != null) {
                    context.writeInstruction(Instructions.scoreRefAdd(holder, targetsArg.objective(), context.getResolvedIntegerMacro(score), context.failureLabel(result)));
                    updateScoreRefResult(context, result, holder, targetsArg.objective());
                    return;
                }
                try(var targets = context.forScoreAccessors(targetsArg, result, true)) {
                    var slot = context.getOrCreateScore(targets.content());
                    var updatedScoreValue = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.valueAdd(updatedScoreValue, context.getScoreValue(slot.score()), context.getResolvedIntegerMacro(score)));
                    context.setScore(slot, updatedScoreValue);
                    result.updateResultValue(updatedScoreValue);
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
                        // vanilla enableTrigger: unlock (and sync) only a locked score
                        var unlocked = context.<Boolean>createLocal();
                        name.writeInstruction(Instructions.enableTrigger(unlocked, objective.content(), name.content()));
                    }
                }
            }
            case CommandScoreboardPlayersGet(ScoreAccessorArgument targets1) -> {
                // vanilla getScore: a single holder (getName never resolves `*`), missing score fails
                result.markImplemented();
                var holder = constantHolder(targets1);
                if(holder != null) {
                    var live = context.<TScore>createLocal();
                    context.writeInstruction(Instructions.scoreRefScore(live, holder, targets1.objective()));
                    context.writeInstruction(Instructions.ifNullJump(live, result.markAndGetErrorLabel()));
                    result.updateResultValue(context.getScoreValue(live));
                    return;
                }
                try(var targets = context.forScoreAccessors(targets1.selectSingle(), result)) {
                    try(var scoreData = context.getScoreData(targets.content(), result.markAndGetErrorLabel())) {
                        result.updateResultValue(context.getScoreValue(scoreData.content()));
                    }
                }
            }
            case CommandScoreboardPlayersOperation(ScoreAccessorArgument targetsArg, ScoreboardOperation operation, ScoreAccessorArgument sourceArg) -> {
                // vanilla ScoreboardCommand.performOperation: both holder lists are resolved up front; for each
                // target the operation is applied with every source in turn, then the target's final value is
                // added to the result.
                result.markImplemented();
                var targetHolder = constantHolder(targetsArg);
                var sourceHolder = constantHolder(sourceArg);
                if(targetHolder != null && sourceHolder != null) {
                    context.writeInstruction(Instructions.scoreRefOperation(targetHolder, targetsArg.objective(), LocationData.ofInteger(operation.ordinal()), sourceHolder, sourceArg.objective(), context.failureLabel(result)));
                    updateScoreRefResult(context, result, targetHolder, targetsArg.objective());
                    return;
                }
                try(var sourceObjective = context.getObjective(sourceArg.objective(), result)) {
                    try(var sources = resolveScoreHolders(context, sourceArg.scoreHolder(), result)) {
                        try(var targets = context.forScoreAccessors(targetsArg, result, true)) {
                            var targetSlot = context.getOrCreateScore(targets.content());
                            forEachScoreHolder(context, sources.content(), sourceObjective.content(), sourceArg.objective(), sourceAccessor ->
                                emitScoreOperation(context, result, operation, targetSlot, context.getOrCreateScore(sourceAccessor)));
                            if(result.generatingResult()) {
                                result.updateResultValue(context.getScoreValue(targetSlot.score()));
                            }
                        }
                    }
                }
            }
            case CommandScoreboardPlayersRemove(ScoreAccessorArgument targetsArg, MacroTemplate<Integer> score) -> {
                // vanilla result: sum of the targets' new scores; one getOrCreatePlayerScore access per target
                result.markImplemented();
                var holder = constantHolder(targetsArg);
                if(holder != null) {
                    context.writeInstruction(Instructions.scoreRefRemove(holder, targetsArg.objective(), context.getResolvedIntegerMacro(score), context.failureLabel(result)));
                    updateScoreRefResult(context, result, holder, targetsArg.objective());
                    return;
                }
                try(var targets = context.forScoreAccessors(targetsArg, result, true)) {
                    var slot = context.getOrCreateScore(targets.content());
                    var updatedScoreValue = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.valueSub(updatedScoreValue, context.getScoreValue(slot.score()), context.getResolvedIntegerMacro(score)));
                    context.setScore(slot, updatedScoreValue);
                    result.updateResultValue(updatedScoreValue);
                }
            }
            case CommandScoreboardPlayersResetAll(ScoreHolderArgument targetsArg) -> {
                // vanilla result: number of holders
                result.markImplemented();
                try(var name = context.forScoreHolder(targetsArg, result)) {
                    name.writeInstruction(Instructions.resetPlayerScore(name.content()));
                    result.updateResultValue(LocationData.ofInteger(1));
                }
            }
            case CommandScoreboardPlayersResetObjective(ScoreAccessorArgument targetsArg) -> {
                result.markImplemented();
                try(var objective = context.getObjective(targetsArg.objective(), result)) {
                    try(var name = context.forScoreHolder(targetsArg.scoreHolder(), result)) {
                        name.writeInstruction(Instructions.resetScore(objective.content(), name.content()));
                        result.updateResultValue(LocationData.ofInteger(1));
                    }
                }
            }
            case CommandScoreboardPlayersSet(ScoreAccessorArgument targetsArg, MacroTemplate<Integer> score) -> {
                // vanilla result: value × number of targets
                result.markImplemented();
                var value = context.getResolvedIntegerMacro(score);
                var holder = constantHolder(targetsArg);
                if(holder != null) {
                    context.writeInstruction(Instructions.scoreRefSet(holder, targetsArg.objective(), value, context.failureLabel(result)));
                    result.updateResultValue(value);
                    return;
                }
                try(var targets = context.forScoreAccessors(targetsArg, result, true)) {
                    context.setScore(context.getOrCreateScore(targets.content()), value);
                    result.updateResultValue(value);
                }
            }
            case CommandTagAdd(SelectorArgument targets, String name) -> {
                // vanilla: result = number of entities that gained the tag; fails when none did
                result.markImplemented();
                emitTagChange(context, result, targets, name, true);
            }
            case CommandTagRemove(SelectorArgument targets, String name) -> {
                result.markImplemented();
                emitTagChange(context, result, targets, name, false);
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
                // vanilla result: number of players; consumed results go to vanilla
                if(result.generatingSuccess() || result.generatingResult()) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                    return;
                }
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
                var players = context.getSelectorList(targets);
                context.writeInstruction(Instructions.giveItemsToAll(players, item, 1));
            }
            case CommandGiveCount(SelectorArgument targets, String item, int count) -> {
                var players = context.getSelectorList(targets);
                context.writeInstruction(Instructions.giveItemsToAll(players, item, count));
            }
            // playsound
            case CommandPlaysoundMinVolume(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos, double volume, double pitch, double minVolume) -> {
                var players = context.getSelectorList(targets);
                try(var world = context.getWorld(); var position = context.applyPosition(pos)) {
                    context.writeInstruction(Instructions.playSoundToAll(world.content(), players, sound.toString(), source, position.content(), LocationData.ofDouble(volume), LocationData.ofDouble(pitch), LocationData.ofDouble(minVolume)));
                }
            }
            case CommandPlaysoundPitch(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos, double volume, double pitch) -> {
                var players = context.getSelectorList(targets);
                try(var world = context.getWorld(); var position = context.applyPosition(pos)) {
                    context.writeInstruction(Instructions.playSoundToAll(world.content(), players, sound.toString(), source, position.content(), LocationData.ofDouble(volume), LocationData.ofDouble(pitch), LocationData.ofDouble(0.0)));
                }
            }
            case CommandPlaysoundVolume(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos, double volume) -> {
                var players = context.getSelectorList(targets);
                try(var world = context.getWorld(); var position = context.applyPosition(pos)) {
                    context.writeInstruction(Instructions.playSoundToAll(world.content(), players, sound.toString(), source, position.content(), LocationData.ofDouble(volume), LocationData.ofDouble(1.0), LocationData.ofDouble(0.0)));
                }
            }
            case CommandPlaysoundPos(DataLocation sound, String source, SelectorArgument targets, WorldCoordinates pos) -> {
                var players = context.getSelectorList(targets);
                try(var world = context.getWorld(); var position = context.applyPosition(pos)) {
                    context.writeInstruction(Instructions.playSoundToAll(world.content(), players, sound.toString(), source, position.content(), LocationData.ofDouble(1.0), LocationData.ofDouble(1.0), LocationData.ofDouble(0.0)));
                }
            }
            case CommandPlaysound(DataLocation sound, String source, SelectorArgument targets) -> {
                var players = context.getSelectorList(targets);
                try(var world = context.getWorld(); var position = context.getPosition()) {
                    context.writeInstruction(Instructions.playSoundToAll(world.content(), players, sound.toString(), source, position.content(), LocationData.ofDouble(1.0), LocationData.ofDouble(1.0), LocationData.ofDouble(0.0)));
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
            case CommandTimeSet(int time) -> {
                try(var world = context.getWorld()) {
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions.setTime(returnValue, world.content(), LocationData.ofInteger(time)));
                    emitEncodedResult(context, result, returnValue);
                }
            }
            case CommandTimeSetToMarker(DataLocation timeMarkerId) -> {
                try(var world = context.getWorld()) {
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions.setTimeToMarker(returnValue, world.content(), timeMarkerId.toString()));
                    emitEncodedResult(context, result, returnValue);
                }
            }
            case CommandTimeAdd(int time) -> {
                try(var world = context.getWorld()) {
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions.addTime(returnValue, world.content(), LocationData.ofInteger(time)));
                    emitEncodedResult(context, result, returnValue);
                }
            }
            case CommandTimeRate(float rate) -> {
                try(var world = context.getWorld()) {
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions.setTimeRate(returnValue, world.content(), LocationData.ofFloat(rate)));
                    emitEncodedResult(context, result, returnValue);
                }
            }
            case CommandTimePause _ -> {
                try(var world = context.getWorld()) {
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions.setTimePaused(returnValue, world.content(), true));
                    emitEncodedResult(context, result, returnValue);
                }
            }
            case CommandTimeResume _ -> {
                try(var world = context.getWorld()) {
                    var returnValue = context.<Long>createLocal();
                    context.writeInstruction(Instructions.setTimePaused(returnValue, world.content(), false));
                    emitEncodedResult(context, result, returnValue);
                }
            }
            case CommandTimeQueryTime _ -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    if (result.generatingResult() || result.generatingSuccess()) {
                        // compiled-function return encoding: fails in a dimension without a default clock
                        var returnValue = context.<Long>createLocal();
                        context.writeInstruction(Instructions.queryTime(returnValue, world.content()));
                        emitEncodedResult(context, result, returnValue);
                    }
                }
            }
            case CommandTimeQueryTimelineTicks(DataLocation timelineId) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    if (result.generatingResult() || result.generatingSuccess()) {
                        // fails without a default clock or for a timeline of another clock
                        var returnValue = context.<Long>createLocal();
                        context.writeInstruction(Instructions.queryTimelineTicks(returnValue, world.content(), timelineId.toString()));
                        emitEncodedResult(context, result, returnValue);
                    }
                }
            }
            case CommandTimeQueryTimelineRepetitions(DataLocation timelineId) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    if (result.generatingResult() || result.generatingSuccess()) {
                        // fails without a default clock or for a timeline of another clock
                        var returnValue = context.<Long>createLocal();
                        context.writeInstruction(Instructions.queryTimelineRepetitions(returnValue, world.content(), timelineId.toString()));
                        emitEncodedResult(context, result, returnValue);
                    }
                }
            }
            case CommandTimeQueryGametime _ -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    if (result.generatingResult()) {
                        var val = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.queryGameTime(val, world.content()));
                        result.updateResultValue(val);
                    }
                }
            }
            // `time of <clock> …` targets a specific world clock; the native time instructions only
            // know the default clock, so the whole command goes to vanilla (PAR-18).
            case CommandTimeOf(DataLocation clock, CommandParams command) -> {
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            }
            // schedule
            case CommandScheduleFunctionAppend(DataLocation function, int time) -> {
                // vanilla result: the trigger game time; consumed results go to vanilla
                if(result.generatingSuccess() || result.generatingResult()) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                    return;
                }
                // vanilla rejects a 0-tick delay (ERROR_SAME_TICK) without touching the schedule
                if(time != 0) context.writeInstruction(Instructions.scheduleFunction(function.toString(), LocationData.ofInteger(time), true));
            }
            case CommandScheduleFunctionReplace(DataLocation function, int time) -> {
                // vanilla result: the trigger game time; consumed results go to vanilla
                if(result.generatingSuccess() || result.generatingResult()) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                    return;
                }
                // vanilla rejects a 0-tick delay (ERROR_SAME_TICK) without touching the schedule
                if(time != 0) context.writeInstruction(Instructions.scheduleFunction(function.toString(), LocationData.ofInteger(time), false));
            }
            case CommandScheduleFunction(DataLocation function, int time) -> {
                // vanilla result: the trigger game time; consumed results go to vanilla
                if(result.generatingSuccess() || result.generatingResult()) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                    return;
                }
                // vanilla rejects a 0-tick delay (ERROR_SAME_TICK) without touching the schedule
                if(time != 0) context.writeInstruction(Instructions.scheduleFunction(function.toString(), LocationData.ofInteger(time), false));
            }
            case CommandScheduleClear(DataLocation function) -> {
                // vanilla ScheduleCommand.remove: result = removed entries, none removed = failure
                result.markImplemented();
                var removed = context.<Integer>createLocal();
                context.writeInstruction(Instructions.scheduleClearFunction(removed, function.toString()));
                emitClearTotal(context, result, removed);
            }
            // gamerule
            case CommandGameruleSet(DataLocation rule, String value) -> {
                var returnValue = context.<Long>createLocal();
                context.writeInstruction(Instructions.setGamerule(returnValue, rule.toString(), value));
                emitEncodedResult(context, result, returnValue);
            }
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
            case CommandExecuteIfPredicate(String predicate, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentNullableEntity()) {
                        try(var world = context.getWorld()) {
                            try(var position = context.getPosition()) {
                                position.writeInstruction(Instructions.checkCommandPredicate(currentEntity.content(), world.content(), position.content(), predicate, true, position.endLabel()));
                                compile(context, fContext.content(), command, result);
                            }
                        }
                    }
                }
            }
            case CommandExecuteUnlessPredicate(String predicate, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentNullableEntity()) {
                        try(var world = context.getWorld()) {
                            try(var position = context.getPosition()) {
                                position.writeInstruction(Instructions.checkCommandPredicate(currentEntity.content(), world.content(), position.content(), predicate, false, position.endLabel()));
                                compile(context, fContext.content(), command, result);
                            }
                        }
                    }
                }
            }

            // execute if/unless data (NBT check)
            case CommandExecuteIfDataEntity(SelectorArgument target, NbtPath path, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
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
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var nbtLocal = context.<TCompoundTag>createLocal();
                    context.writeInstruction(Instructions.getNbtStorage(nbtLocal, target));
                    var tagVal = getNbtPathValue(context, nbtLocal, path.parts(), false, context.current().contextEndLabel());
                    context.writeInstruction(Instructions.ifNullJump(tagVal, context.current().contextEndLabel()));
                    compile(context, fContext.content(), command, result);
                }
            }
            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: block entity NBT access),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandExecuteIfDataBlock(WorldCoordinates pos, NbtPath path, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteUnlessDataEntity(SelectorArgument target, NbtPath path, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
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
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
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
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: block region comparison + block entity serialization),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandExecuteIfBlocks(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteIfBlocksMasked(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteUnlessBlocks(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteUnlessBlocksMasked(WorldCoordinates start, WorldCoordinates end, WorldCoordinates destination, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);

            // execute if/unless без sub-command (result/success context)
            case CommandExecuteIfScoreRangeOnly(ScoreAccessorArgument target, IntRangeArgument range) -> {
                // vanilla: 1 when the score exists and matches, otherwise the command fails
                result.markImplemented();
                var holder = constantHolder(target);
                if(holder != null) {
                    context.writeInstruction(Instructions.scoreRefIfMatchesElseJump(holder, target.objective(), rangeMin(range), rangeMax(range), result.markAndGetErrorLabel()));
                    result.updateResultValue(LocationData.ofInteger(1));
                    return;
                }
                try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), result)) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), result.markAndGetErrorLabel())) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                        context.jumpIfNotMatchesRange(scoreData.data(), scoreValue, range, result.markAndGetErrorLabel());
                        result.updateResultValue(LocationData.ofInteger(1));
                    }
                }
            }
            case CommandExecuteUnlessScoreRangeOnly(ScoreAccessorArgument target, IntRangeArgument range) -> {
                // vanilla: 1 when the score is missing or does not match; a missing objective/holder fails
                result.markImplemented();
                var holder = constantHolder(target);
                if(holder != null) {
                    context.writeInstruction(Instructions.scoreRefUnlessMatchesElseJump(holder, target.objective(), rangeMin(range), rangeMax(range), result.markAndGetErrorLabel()));
                    result.updateResultValue(LocationData.ofInteger(1));
                    return;
                }
                try(var scoreAccessor = context.forScoreAccessors(target.selectSingle(), result)) {
                    try(var scope = context.enterLocalScope()) {
                        try(var scoreData = context.getScoreData(scoreAccessor.content(), scope.endLabel())) {
                            var scoreValue = context.<Integer>createLocal();
                            scoreData.writeInstruction(Instructions.getScoreValue(scoreValue, scoreData.content()));
                            context.jumpIfMatchesRange(scoreData.data(), scoreValue, range, result.markAndGetErrorLabel());
                        }
                    }
                    result.updateResultValue(LocationData.ofInteger(1));
                }
            }
            case CommandExecuteIfEntityOnly(SelectorArgument entities) -> {
                // vanilla: the match count when > 0, otherwise the command fails
                result.markImplemented();
                var entityList = context.getSelectorList(entities);
                var countLocal = context.<Integer>createLocal();
                context.writeInstruction(Instructions.getListLength(countLocal, entityList));
                context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, countLocal, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
                result.updateResultValue(countLocal);
            }
            case CommandExecuteUnlessEntityOnly(SelectorArgument entities) -> {
                // vanilla: 1 when nothing matches, otherwise the command fails
                result.markImplemented();
                var entityList = context.getSelectorList(entities.limit(1));
                var countLocal = context.<Integer>createLocal();
                context.writeInstruction(Instructions.getListLength(countLocal, entityList));
                context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, countLocal, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
                result.updateResultValue(LocationData.ofInteger(1));
            }

            // recipe give/take * wildcard — native compilation
            case CommandRecipeGiveAllStar(SelectorArgument targets) -> {
                // vanilla RecipeCommand: result = recipes awarded/reset summed over players, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        var player = context.<TServerPlayer>createLocal();
                        selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                        var changed = context.<Integer>createLocal();
                        selector.writeInstruction(Instructions.recipeGiveAll(changed, player));
                        selector.writeInstruction(Instructions.valueAdd(total, total, changed));
                    }
                }
                emitClearTotal(context, result, total);
            }
            case CommandRecipeTakeAllStar(SelectorArgument targets) -> {
                // vanilla RecipeCommand: result = recipes awarded/reset summed over players, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        var player = context.<TServerPlayer>createLocal();
                        selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                        var changed = context.<Integer>createLocal();
                        selector.writeInstruction(Instructions.recipeTakeAll(changed, player));
                        selector.writeInstruction(Instructions.valueAdd(total, total, changed));
                    }
                }
                emitClearTotal(context, result, total);
            }

            // execute summon — native compilation
            case CommandExecuteSummon(DataLocation entity, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var current = context.current();
                    var stack = context.<TCommandSourceStack>createLocal();
                    context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                    var summonedEntity = context.<TEntity>createLocal();
                    context.writeInstruction(Instructions.executeSummon(summonedEntity, stack, entity.toString()));
                    context.writeInstruction(Instructions.ifNullJump(summonedEntity, context.current().contextEndLabel()));
                    // vanilla execute summon runs inner command as the summoned entity at its position
                    var entityPos = context.<TVec3>createLocal();
                    context.writeInstruction(Instructions.getEntityPosition(entityPos, summonedEntity));
                    try(var _ =context.enterWithoutValue(context.current().withCurrentEntity(summonedEntity).withCurrentPosition(entityPos))) {
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            // execute positioned over — native compilation
            case CommandExecutePositionedOver(String heightmap, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var world = context.getWorld()) {
                        try(var currentPos = context.getPosition()) {
                            var newPos = context.<TVec3>createLocal();
                            context.writeInstruction(Instructions.positionedOver(newPos, world.content(), currentPos.content(), heightmap));
                            // null: the column's chunk is not loaded (vanilla ERROR_NOT_LOADED)
                            context.writeInstruction(Instructions.ifNullJump(newPos, context.current().contextEndLabel()));
                            try(var _ =context.enterWithoutValue(context.current().withCurrentPosition(newPos))) {
                                compile(context, fContext.content(), command, result);
                            }
                        }
                    }
                }
            }

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: slot mapping + item parsing),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandItemReplaceEntityWith(SelectorArgument targets, String slot, String item) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandItemReplaceEntityFromEntitySlot(SelectorArgument targets, String slot, SelectorArgument source, String sourceSlot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandItemReplaceBlockWith(WorldCoordinates pos, String slot, String item) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandItemReplaceBlockFromEntitySlot(WorldCoordinates pos, String slot, SelectorArgument source, String sourceSlot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandItemReplaceBlockFromBlockSlot(WorldCoordinates pos, String slot, WorldCoordinates sourcePos, String sourceSlot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));

            // scoreboard objectives setdisplay — native compilation
            case CommandScoreboardObjectivesSetdisplayClear(String slot) ->
                context.writeInstruction(Instructions.scoreboardSetdisplayClear(slot));
            case CommandScoreboardObjectivesRemove(String objective) ->
                context.writeInstruction(Instructions.removeObjective(objective));
            case CommandScoreboardObjectivesAdd(String objective, String criteria) ->
                context.writeInstruction(Instructions.addObjective(objective, criteria));
            case CommandScoreboardObjectivesAddDisplay(String objective, String criteria, Text display) -> {
                var displayLocal = context.<TComponent>createLocal();
                context.writeInstruction(Instructions.loadText(displayLocal, display));
                context.writeInstruction(Instructions.addObjectiveDisplay(objective, criteria, displayLocal));
            }
            case CommandScoreboardObjectivesAddDisplayGreedy(String objective, String criteria, String displayName) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandScoreboardPlayersDisplayName(ScoreHolderArgument targets, String objective) -> {
                try(var holder = context.forScoreHolder(targets, result)) {
                    context.writeInstruction(Instructions.scoreboardPlayersDisplayNameClear(holder.content(), objective));
                }
            }
            case CommandScoreboardPlayersDisplayNameText(ScoreHolderArgument targets, String objective, Text displayName) -> {
                try(var holder = context.forScoreHolder(targets, result)) {
                    var displayLocal = context.<TComponent>createLocal();
                    context.writeInstruction(Instructions.loadText(displayLocal, displayName));
                    context.writeInstruction(Instructions.scoreboardPlayersDisplayName(holder.content(), objective, displayLocal));
                }
            }
            case CommandScoreboardPlayersDisplayNameGreedy(ScoreHolderArgument targets, String objective, String displayName) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandScoreboardPlayersDisplayNameClear(ScoreHolderArgument targets) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandScoreboardObjectivesSetdisplay(String slot, String objective) ->
                context.writeInstruction(Instructions.scoreboardSetdisplay(slot, objective));
            case CommandScoreboardPlayersDisplayNumberformatBlank(ScoreHolderArgument targets, String objective) -> {
                try(var holder = context.forScoreHolder(targets, result)) {
                    context.writeInstruction(Instructions.scoreboardPlayersNumberformatBlank(holder.content(), objective));
                }
            }
            case CommandScoreboardPlayersDisplayNumberformatStyled(ScoreHolderArgument targets, String objective, String style) -> {
                try(var holder = context.forScoreHolder(targets, result)) {
                    context.writeInstruction(Instructions.scoreboardPlayersNumberformatStyled(holder.content(), objective, style));
                }
            }
            case CommandScoreboardPlayersDisplayNumberformatFixed(ScoreHolderArgument targets, String objective, Text text) -> {
                try(var holder = context.forScoreHolder(targets, result)) {
                    var textLocal = context.<TComponent>createLocal();
                    context.writeInstruction(Instructions.loadText(textLocal, text));
                    context.writeInstruction(Instructions.scoreboardPlayersNumberformatFixed(holder.content(), objective, textLocal));
                }
            }

            // execute in <dimension>
            case CommandExecuteIn(DataLocation dimension, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var world = context.getWorld()) {
                        try(var position = context.getPosition()) {
                            var worldLocal = context.<TServerLevel>createLocal();
                            context.writeInstruction(Instructions.getDimensionLevel(worldLocal, dimension.toString()));
                            context.writeInstruction(Instructions.ifNullJump(worldLocal, context.current().contextEndLabel()));
                            // vanilla CommandSourceStack.withLevel: X/Z scaled by the dimensions' teleportation scale
                            var scaledPosition = context.<TVec3>createLocal();
                            context.writeInstruction(Instructions.scalePositionForLevel(scaledPosition, world.content(), worldLocal, position.content()));
                            try(var _ =context.enterWithoutValue(context.current().withCurrentWorld(worldLocal).withCurrentPosition(scaledPosition))) {
                                compile(context, fContext.content(), command, result);
                            }
                        }
                    }
                }
            }
            case CommandExecuteIfDimension(DataLocation dimension, CommandParams command) ->
                compileDimensionCondition(context, forkingContext, inputCommand, dimension, command, result, true);
            case CommandExecuteUnlessDimension(DataLocation dimension, CommandParams command) ->
                compileDimensionCondition(context, forkingContext, inputCommand, dimension, command, result, false);
            case CommandExecuteIfLoaded(WorldCoordinates pos, CommandParams command) ->
                compileLoadedCondition(context, forkingContext, inputCommand, pos, command, result, true);
            case CommandExecuteUnlessLoaded(WorldCoordinates pos, CommandParams command) ->
                compileLoadedCondition(context, forkingContext, inputCommand, pos, command, result, false);
            case CommandExecuteUnlessBiome(WorldCoordinates pos, DataLocation biome, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var world = context.getWorld()) {
                        try(var posData = context.applyPosition(pos)) {
                            var cond = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.checkBiome(cond, world.content(), posData.content(), biome.toString()));
                            // 0 = other biome; 1 = match and -1 = unloaded/outside the world both fail `unless`
                            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, cond, LocationData.ofInteger(0), context.current().contextEndLabel()));
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }

            // execute if/unless block
            case CommandExecuteIfBlock(WorldCoordinates pos, MacroTemplate<String> block, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var current = context.current();
                    var worldLocal = current.currentWorld();
                    if(worldLocal == null) {
                        var w = context.<TServerLevel>createLocal();
                        context.writeInstruction(Instructions.getStackWorld(w, current.commandSourceStack()));
                        worldLocal = w;
                    }
                    try(var posData = context.applyPosition(pos)) {
                        var cond = context.<Integer>createLocal();
                        posData.writeInstruction(block.isStatic() ? Instructions.checkBlock(cond, worldLocal, posData.content(), block.getStaticValue()) : Instructions.checkBlockDynamic(cond, worldLocal, posData.content(), context.getResolvedStringMacro(block)));
                        // 1 = match; 0 = no match and -1 = vanilla error both fail `if`
                        posData.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, cond, LocationData.ofInteger(1), posData.data().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }
            case CommandExecuteUnlessBlock(WorldCoordinates pos, MacroTemplate<String> block, CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    var current = context.current();
                    var worldLocal = current.currentWorld();
                    if(worldLocal == null) {
                        var w = context.<TServerLevel>createLocal();
                        context.writeInstruction(Instructions.getStackWorld(w, current.commandSourceStack()));
                        worldLocal = w;
                    }
                    try(var posData = context.applyPosition(pos)) {
                        var cond = context.<Integer>createLocal();
                        posData.writeInstruction(block.isStatic() ? Instructions.checkBlock(cond, worldLocal, posData.content(), block.getStaticValue()) : Instructions.checkBlockDynamic(cond, worldLocal, posData.content(), context.getResolvedStringMacro(block)));
                        // 0 = no match; 1 = match and -1 = vanilla error both fail `unless`
                        posData.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, cond, LocationData.ofInteger(0), posData.data().contextEndLabel()));
                        compile(context, fContext.content(), command, result);
                    }
                }
            }

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: item predicate parsing + slot iteration),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandExecuteIfItemsEntity(SelectorArgument targets, String slot, String predicate, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteUnlessItemsEntity(SelectorArgument targets, String slot, String predicate, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteIfItemsBlock(WorldCoordinates pos, String slot, String predicate, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);
            case CommandExecuteUnlessItemsBlock(WorldCoordinates pos, String slot, String predicate, CommandParams command) ->
                compileVanillaCondition(context, forkingContext, inputCommand, command, result);

            // execute if/unless items standalone — для execute store result ... if items ...
            case CommandExecuteIfItemsEntityOnly(SelectorArgument targets, String slot, String predicate) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandExecuteUnlessItemsEntityOnly(SelectorArgument targets, String slot, String predicate) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandExecuteIfItemsBlockOnly(WorldCoordinates pos, String slot, String predicate) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandExecuteUnlessItemsBlockOnly(WorldCoordinates pos, String slot, String predicate) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: entity NBT load/save + path resolution),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandExecuteStoreEntity(ResultStoreLocationType storeLocation, SelectorArgument target, NbtPath path, ResultStoreType storeType, double scale, CommandParams command) ->
                emitVanillaFallbackWithResult(context, result, sourceTextWithoutReturn(context, inputCommand));

            // execute on leasher / attacker / controller / owner / passengers
            case CommandExecuteOnLeasher(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var holder = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityLeashHolder(holder, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(holder, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(holder))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnAttacker(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var attacker = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityLastAttacker(attacker, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(attacker, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(attacker))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnController(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var controller = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityController(controller, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(controller, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(controller))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnOwner(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
                    try(var currentEntity = context.getCurrentEntity()) {
                        var owner = context.<TEntity>createLocal();
                        currentEntity.writeInstruction(Instructions.getEntityOwner(owner, currentEntity.content()));
                        currentEntity.writeInstruction(Instructions.ifNullJump(owner, currentEntity.endLabel()));
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(owner))) {
                            compile(context, fContext.content(), command, result);
                        }
                    }
                }
            }
            case CommandExecuteOnPassengers(CommandParams command) -> {
                try(var fContext = context.withForkingContext(forkingContext, inputCommand)) {
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
                        try(var _ =context.enterWithoutValue(currentEntity.data().withCurrentEntity(passenger).withContextLabel(codeLocation))) {
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
            case CommandDataMergeEntity(SelectorArgument target, NbtValue.Compound nbt) -> {
                try(var entityData = context.forSelector(target, result)) {
                    var nbtLocal = context.<TCompoundTag>createLocal();
                    context.writeInstruction(Instructions.loadNbtValue(nbtLocal.forceCast(), nbt));
                    entityData.writeInstruction(Instructions.dataMergeEntity(entityData.content(), nbtLocal));
                }
            }

            // data remove — native compilation
            case CommandDataRemoveStorage(DataLocation target, NbtPath path) ->
                context.writeInstruction(Instructions.dataRemoveStorage(target.toString(), nbtPathToString(path)));
            case CommandDataRemoveEntity(SelectorArgument target, NbtPath path) -> {
                try(var entityData = context.forSelector(target, result)) {
                    entityData.writeInstruction(Instructions.dataRemoveEntity(entityData.content(), nbtPathToString(path)));
                }
            }

            // data modify prepend/append/insert value — Storage нативно, Entity/Block в fallback
            // TODO: Entity/Block fallback — особого смысла делать нативными нету (bottleneck в MC API: entity NBT load/save),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandDataModifyPrependValue(NbtDataLocation destination, NbtPath path, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    compileDataModifyListOpValue(context, result, storage, path, setValue, 0);
                } else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }
            case CommandDataModifyAppendValue(NbtDataLocation destination, NbtPath path, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    compileDataModifyListOpValue(context, result, storage, path, setValue, -1);
                } else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }
            case CommandDataModifyInsertValue(NbtDataLocation destination, NbtPath path, int index, NbtValue setValue) -> {
                if(destination instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    compileDataModifyListOpValue(context, result, storage, path, setValue, index);
                } else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: runtime NBT path resolution + deep copy),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandDataModifySetFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifySetFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyPrependFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyPrependFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyAppendFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyAppendFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyInsertFrom(NbtDataLocation destination, NbtPath path, int index, NbtDataLocation source) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyInsertFromPath(NbtDataLocation destination, NbtPath path, int index, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyMergeValue(NbtDataLocation destination, NbtPath path, NbtValue setValue) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyMergeFrom(NbtDataLocation destination, NbtPath path, NbtDataLocation source) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyMergeFromPath(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifySetString(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifySetStringStart(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath, int start) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifySetStringRange(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath, int start, int end) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyAppendString(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyAppendStringStart(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath, int start) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyAppendStringRange(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath, int start, int end) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyPrependString(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyPrependStringStart(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath, int start) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyPrependStringRange(NbtDataLocation destination, NbtPath path, NbtDataLocation source, NbtPath sourcePath, int start, int end) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyInsertString(NbtDataLocation destination, NbtPath path, int index, NbtDataLocation source, NbtPath sourcePath) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyInsertStringStart(NbtDataLocation destination, NbtPath path, int index, NbtDataLocation source, NbtPath sourcePath, int start) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandDataModifyInsertStringRange(NbtDataLocation destination, NbtPath path, int index, NbtDataLocation source, NbtPath sourcePath, int start, int end) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));

            // data get — native for entity and storage; block through vanilla
            case CommandDataGetPath(NbtDataLocation source, NbtPath path) -> {
                if(!(source instanceof NbtDataLocation.Block)) {
                    compileDataGet(context, result, source, path, null);
                } else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }
            case CommandDataGetPathScale(NbtDataLocation source, NbtPath path, double scale) -> {
                if(!(source instanceof NbtDataLocation.Block)) {
                    compileDataGet(context, result, source, path, scale);
                } else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }
            case CommandDataGet(NbtDataLocation source) -> {
                // vanilla prints the whole tag and returns 1
                switch(source) {
                    case NbtDataLocation.Entity(SelectorArgument selector) -> {
                        result.markImplemented();
                        try(var _ = context.forSelector(selector.limit(1), result)) {
                            result.updateResultValue(LocationData.ofInteger(1));
                        }
                    }
                    case NbtDataLocation.Storage _ -> {
                        result.markImplemented();
                        result.updateResultValue(LocationData.ofInteger(1));
                    }
                    case NbtDataLocation.Block _ -> emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: loot table resolution + item distribution),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandLootReplaceEntityLoot(SelectorArgument target, String slot, DataLocation loot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootReplaceEntityKill(SelectorArgument target, String slot, SelectorArgument kill) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootReplaceBlockLoot(WorldCoordinates pos, String slot, DataLocation loot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootReplaceBlockKill(WorldCoordinates pos, String slot, SelectorArgument kill) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootInsertLoot(WorldCoordinates pos, DataLocation loot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootInsertKill(WorldCoordinates pos, SelectorArgument kill) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootGiveLoot(SelectorArgument players, DataLocation loot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootGiveKill(SelectorArgument players, SelectorArgument kill) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootSpawnLoot(WorldCoordinates pos, DataLocation loot) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootSpawnKill(WorldCoordinates pos, SelectorArgument kill) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: loot function resolution + item modification),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandItemModifyEntity(SelectorArgument targets, String slot, String modifier) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandItemModifyBlock(WorldCoordinates pos, String slot, String modifier) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));

            // effect — native compilation
            case CommandEffectGive(SelectorArgument targets, DataLocation effect) -> {
                // vanilla EffectCommands.giveEffect: result = number of entities affected, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var src = context.getCurrentNullableEntity()) {
                            var applied = context.<Integer>createLocal();
                            selector.writeInstruction(Instructions.effectGive(applied, selector.content(), src.content(), effect.toString(), -1, 0, false));
                            selector.writeInstruction(Instructions.valueAdd(total, total, applied));
                        }
                    }
                }
                emitClearTotal(context, result, total);
            }
            case CommandEffectGiveSeconds(SelectorArgument targets, DataLocation effect, int seconds) -> {
                // vanilla EffectCommands.giveEffect: result = number of entities affected, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var src = context.getCurrentNullableEntity()) {
                            var applied = context.<Integer>createLocal();
                            selector.writeInstruction(Instructions.effectGive(applied, selector.content(), src.content(), effect.toString(), seconds, 0, false));
                            selector.writeInstruction(Instructions.valueAdd(total, total, applied));
                        }
                    }
                }
                emitClearTotal(context, result, total);
            }
            case CommandEffectGiveSecondsAmplifier(SelectorArgument targets, DataLocation effect, int seconds, int amplifier) -> {
                // vanilla EffectCommands.giveEffect: result = number of entities affected, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var src = context.getCurrentNullableEntity()) {
                            var applied = context.<Integer>createLocal();
                            selector.writeInstruction(Instructions.effectGive(applied, selector.content(), src.content(), effect.toString(), seconds, amplifier, false));
                            selector.writeInstruction(Instructions.valueAdd(total, total, applied));
                        }
                    }
                }
                emitClearTotal(context, result, total);
            }
            case CommandEffectGiveSecondsAmplifierHide(SelectorArgument targets, DataLocation effect, int seconds, int amplifier, boolean hide) -> {
                // vanilla EffectCommands.giveEffect: result = number of entities affected, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var src = context.getCurrentNullableEntity()) {
                            var applied = context.<Integer>createLocal();
                            selector.writeInstruction(Instructions.effectGive(applied, selector.content(), src.content(), effect.toString(), seconds, amplifier, hide));
                            selector.writeInstruction(Instructions.valueAdd(total, total, applied));
                        }
                    }
                }
                emitClearTotal(context, result, total);
            }
            case CommandEffectClear() -> {
                try(var selector = context.getCurrentEntity()) {
                    selector.writeInstruction(Instructions.effectClearAll(selector.content()));
                }
            }
            case CommandEffectClearTargets(SelectorArgument targets) -> {
                try(var selector = context.forSelector(targets, result)) {
                    selector.writeInstruction(Instructions.effectClearAll(selector.content()));
                }
            }
            case CommandEffectClearTargetsEffect(SelectorArgument targets, DataLocation effect) -> {
                try(var selector = context.forSelector(targets, result)) {
                    selector.writeInstruction(Instructions.effectClear(selector.content(), effect.toString()));
                }
            }

            // tp — native compilation for basic variants, vanilla fallback for facing
            case CommandTpEntity(SelectorArgument destination) -> {
                try(var current = context.getCurrentEntity()) {
                    try(var dest = context.forSelector(destination, result)) {
                        current.writeInstruction(Instructions.teleportToEntity(current.content(), dest.content()));
                    }
                }
            }
            case CommandTpTargetsEntity(SelectorArgument targets, SelectorArgument destination) -> {
                // vanilla resolves the (single) destination once, before teleporting any target
                try(var dest = context.forSelector(destination.limit(1), result)) {
                    try(var selector = context.forSelector(targets, result)) {
                        selector.writeInstruction(Instructions.teleportToEntity(selector.content(), dest.content()));
                    }
                }
            }
            case CommandTpTargetsPos(SelectorArgument targets, WorldCoordinates pos) -> {
                try(var selector = context.forSelector(targets, result)) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            selector.writeInstruction(Instructions.teleportToPos(selector.content(), world.content(), position.content(), tpRelativeFlags(pos)));
                        }
                    }
                }
            }
            case CommandTpTargetsPosRot(SelectorArgument targets, WorldCoordinates pos, RotationCoordinates rot) -> {
                try(var selector = context.forSelector(targets, result)) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            try(var rotation = context.applyRotation(rot)) {
                                var yaw = context.<Float>createLocal();
                                var pitch = context.<Float>createLocal();
                                selector.writeInstruction(Instructions.getRotationYaw(yaw, rotation.content()));
                                selector.writeInstruction(Instructions.getRotationPitch(pitch, rotation.content()));
                                selector.writeInstruction(Instructions.teleportToPosRot(selector.content(), world.content(), position.content(), yaw, pitch, tpRelativeFlags(pos) | tpRelativeFlags(rot)));
                            }
                        }
                    }
                }
            }
            case CommandTpPos(WorldCoordinates pos) -> {
                try(var current = context.getCurrentEntity()) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            current.writeInstruction(Instructions.teleportToPos(current.content(), world.content(), position.content(), tpRelativeFlags(pos)));
                        }
                    }
                }
            }
            // tp facing variants — native compilation with rotation math
            case CommandTpTargetsPosFacingEntity(SelectorArgument targets, WorldCoordinates pos, SelectorArgument facingTarget) -> {
                try(var facing = context.forSelector(facingTarget.limit(1), result)) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var world = context.getWorld()) {
                            try(var position = context.applyPosition(pos)) {
                                context.writeInstruction(Instructions.teleportFacingEntity(selector.content(), world.content(), position.content(), tpRelativeFlags(pos), context.current().commandSourceStack(), tpSourceAnchor(context), facing.content(), false));
                            }
                        }
                    }
                }
            }
            case CommandTpTargetsPosFacingEntityAnchor(SelectorArgument targets, WorldCoordinates pos, SelectorArgument facingTarget, LocationAnchor anchor) -> {
                try(var facing = context.forSelector(facingTarget.limit(1), result)) {
                    try(var selector = context.forSelector(targets, result)) {
                        try(var world = context.getWorld()) {
                            try(var position = context.applyPosition(pos)) {
                                context.writeInstruction(Instructions.teleportFacingEntity(selector.content(), world.content(), position.content(), tpRelativeFlags(pos), context.current().commandSourceStack(), tpSourceAnchor(context), facing.content(), anchor == LocationAnchor.EYES));
                            }
                        }
                    }
                }
            }
            case CommandTpTargetsPosFacingPos(SelectorArgument targets, WorldCoordinates pos, WorldCoordinates facingPos) -> {
                try(var selector = context.forSelector(targets, result)) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            try(var facingPosition = context.applyPosition(facingPos)) {
                                facingPosition.writeInstruction(Instructions.teleportFacingPos(selector.content(), world.content(), position.content(), tpRelativeFlags(pos), context.current().commandSourceStack(), tpSourceAnchor(context), facingPosition.content()));
                            }
                        }
                    }
                }
            }
            case CommandTpPosFacingEntity(WorldCoordinates pos, SelectorArgument facingTarget) -> {
                try(var current = context.getCurrentEntity()) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            try(var facing = context.forSelector(facingTarget, result)) {
                                facing.writeInstruction(Instructions.teleportFacingEntity(current.content(), world.content(), position.content(), tpRelativeFlags(pos), context.current().commandSourceStack(), tpSourceAnchor(context), facing.content(), false));
                            }
                        }
                    }
                }
            }
            case CommandTpPosFacingEntityAnchor(WorldCoordinates pos, SelectorArgument facingTarget, LocationAnchor anchor) -> {
                try(var current = context.getCurrentEntity()) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            try(var facing = context.forSelector(facingTarget, result)) {
                                facing.writeInstruction(Instructions.teleportFacingEntity(current.content(), world.content(), position.content(), tpRelativeFlags(pos), context.current().commandSourceStack(), tpSourceAnchor(context), facing.content(), anchor == LocationAnchor.EYES));
                            }
                        }
                    }
                }
            }
            case CommandTpPosFacingPos(WorldCoordinates pos, WorldCoordinates facingPos) -> {
                try(var current = context.getCurrentEntity()) {
                    try(var world = context.getWorld()) {
                        try(var position = context.applyPosition(pos)) {
                            try(var facingPosition = context.applyPosition(facingPos)) {
                                facingPosition.writeInstruction(Instructions.teleportFacingPos(current.content(), world.content(), position.content(), tpRelativeFlags(pos), context.current().commandSourceStack(), tpSourceAnchor(context), facingPosition.content()));
                            }
                        }
                    }
                }
            }

            // team — vanilla fallback
            // team — native compilation
            case CommandTeamAdd(String team) ->
                context.writeInstruction(Instructions.teamAdd(team));
            case CommandTeamAddDisplay(String team, Text display) -> {
                var displayText = loadText(context, display);
                context.writeInstruction(Instructions.teamAddDisplay(team, displayText));
            }
            case CommandTeamRemove(String team) ->
                context.writeInstruction(Instructions.teamRemove(team));
            case CommandTeamJoin(String team) -> {
                try(var current = context.getCurrentEntity()) {
                    current.writeInstruction(Instructions.teamJoin(team, current.content()));
                }
            }
            case CommandTeamJoinMembers(String team, ScoreHolderArgument members) -> {
                try(var holder = context.forScoreHolder(members, result)) {
                    context.writeInstruction(Instructions.teamJoinScoreHolder(team, holder.content()));
                }
            }
            case CommandTeamLeave(ScoreHolderArgument members) -> {
                try(var holder = context.forScoreHolder(members, result)) {
                    context.writeInstruction(Instructions.teamLeaveScoreHolder(holder.content()));
                }
            }
            case CommandTeamList() ->
                // team list outputs to chat — no perf benefit from native
                emitVanillaFallback(context, "team list");
            case CommandTeamListTeam(String team) ->
                emitVanillaFallback(context, "team list " + team);
            case CommandTeamModify(String team, String option, String value) -> {
                if(isNativeTeamModify(option, value)) {
                    context.writeInstruction(Instructions.teamModifyFull(team, option, value));
                } else {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                }
            }
            case CommandTeamEmpty(String team) ->
                context.writeInstruction(Instructions.teamEmpty(team));

            // stopsound — native compilation
            case CommandStopsound(SelectorArgument targets) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var player = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                    selector.writeInstruction(Instructions.stopsoundAll(player));
                }
            }
            case CommandStopsoundSource(SelectorArgument targets, String source) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var player = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                    selector.writeInstruction(Instructions.stopsoundSource(player, source));
                }
            }
            case CommandStopsoundSound(SelectorArgument targets, String source, DataLocation sound) -> {
                try(var selector = context.forSelector(targets, result)) {
                    var player = context.<TServerPlayer>createLocal();
                    selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                    selector.writeInstruction(Instructions.stopsoundSourceSound(player, source, sound.toString()));
                }
            }

            // setblock — native compilation via BlockStateParser
            case CommandSetblockReplace(WorldCoordinates pos, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        var setResult = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.setBlock(setResult, world.content(), position.content(), block, 0));
                        updateBlockCommandResult(context, result, setResult);
                    }
                }
            }
            case CommandSetblockKeep(WorldCoordinates pos, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        var setResult = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.setBlock(setResult, world.content(), position.content(), block, 1));
                        updateBlockCommandResult(context, result, setResult);
                    }
                }
            }
            case CommandSetblockDestroy(WorldCoordinates pos, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        var setResult = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.setBlock(setResult, world.content(), position.content(), block, 2));
                        updateBlockCommandResult(context, result, setResult);
                    }
                }
            }

            // fill — native compilation via BlockStateParser
            case CommandFill(WorldCoordinates from, WorldCoordinates to, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocks(fillResult, world.content(), fromPos.content(), toPos.content(), block, 0));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }
            case CommandFillReplace(WorldCoordinates from, WorldCoordinates to, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocks(fillResult, world.content(), fromPos.content(), toPos.content(), block, 0));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }
            case CommandFillReplaceFilter(WorldCoordinates from, WorldCoordinates to, String block, String filter) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocksFilter(fillResult, world.content(), fromPos.content(), toPos.content(), block, filter));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }
            case CommandFillKeep(WorldCoordinates from, WorldCoordinates to, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocks(fillResult, world.content(), fromPos.content(), toPos.content(), block, 2));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }
            case CommandFillDestroy(WorldCoordinates from, WorldCoordinates to, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocks(fillResult, world.content(), fromPos.content(), toPos.content(), block, 3));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }
            case CommandFillHollow(WorldCoordinates from, WorldCoordinates to, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocks(fillResult, world.content(), fromPos.content(), toPos.content(), block, 4));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }
            case CommandFillOutline(WorldCoordinates from, WorldCoordinates to, String block) -> {
                result.markImplemented();
                try(var world = context.getWorld()) {
                    try(var fromPos = context.applyPosition(from)) {
                        try(var toPos = context.applyPosition(to)) {
                            var fillResult = context.<Integer>createLocal();
                            context.writeInstruction(Instructions.fillBlocks(fillResult, world.content(), fromPos.content(), toPos.content(), block, 5));
                            updateBlockCommandResult(context, result, fillResult);
                        }
                    }
                }
            }

            // particle — native compilation
            case CommandParticleName(String name) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.getPosition()) {
                        context.writeInstruction(Instructions.spawnParticleSimple(world.content(), name.toString(), position.content()));
                    }
                }
            }
            case CommandParticle(String name, WorldCoordinates pos) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        context.writeInstruction(Instructions.spawnParticleSimple(world.content(), name.toString(), position.content()));
                    }
                }
            }
            case CommandParticleViewers(String name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        try(var deltaPos = context.applyPosition(delta)) {
                            context.writeInstruction(Instructions.spawnParticle(world.content(), name.toString(), position.content(), deltaPos.content(), LocationData.ofDouble(speed), LocationData.ofInteger(count), false));
                        }
                    }
                }
            }
            case CommandParticleViewersForce(String name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        try(var deltaPos = context.applyPosition(delta)) {
                            context.writeInstruction(Instructions.spawnParticle(world.content(), name.toString(), position.content(), deltaPos.content(), LocationData.ofDouble(speed), LocationData.ofInteger(count), true));
                        }
                    }
                }
            }
            case CommandParticleViewersNormal(String name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        try(var deltaPos = context.applyPosition(delta)) {
                            context.writeInstruction(Instructions.spawnParticle(world.content(), name.toString(), position.content(), deltaPos.content(), LocationData.ofDouble(speed), LocationData.ofInteger(count), false));
                        }
                    }
                }
            }
            case CommandParticleViewersParams(String name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count, SelectorArgument viewers) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        try(var deltaPos = context.applyPosition(delta)) {
                            var viewerList = context.getSelectorList(viewers);
                            context.writeInstruction(Instructions.spawnParticleForPlayers(world.content(), name.toString(), position.content(), deltaPos.content(), LocationData.ofDouble(speed), LocationData.ofInteger(count), false, viewerList));
                        }
                    }
                }
            }
            case CommandParticleViewersForceParams(String name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count, SelectorArgument viewers) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        try(var deltaPos = context.applyPosition(delta)) {
                            var viewerList = context.getSelectorList(viewers);
                            context.writeInstruction(Instructions.spawnParticleForPlayers(world.content(), name.toString(), position.content(), deltaPos.content(), LocationData.ofDouble(speed), LocationData.ofInteger(count), true, viewerList));
                        }
                    }
                }
            }
            case CommandParticleViewersNormalParams(String name, WorldCoordinates pos, WorldCoordinates delta, double speed, int count, SelectorArgument viewers) -> {
                try(var world = context.getWorld()) {
                    try(var position = context.applyPosition(pos)) {
                        try(var deltaPos = context.applyPosition(delta)) {
                            var viewerList = context.getSelectorList(viewers);
                            context.writeInstruction(Instructions.spawnParticleForPlayers(world.content(), name.toString(), position.content(), deltaPos.content(), LocationData.ofDouble(speed), LocationData.ofInteger(count), false, viewerList));
                        }
                    }
                }
            }

            // random — native compilation via RandomSequences API
            case CommandRandomValue(IntRangeArgument range) -> {
                int min = range.minimum() != null ? range.minimum().intValue() : Integer.MIN_VALUE;
                int max = range.maximum() != null ? range.maximum().intValue() : Integer.MAX_VALUE;
                if(!isValidRandomSpan(min, max)) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                } else {
                    result.markImplemented();
                    try(var world = context.getWorld()) {
                        var resultLocal = context.<Integer>createLocal();
                        context.writeInstruction(Instructions.randomValue(resultLocal, world.content(), min, max));
                        result.updateResultValue(resultLocal);
                    }
                }
            }
            case CommandRandomRoll(IntRangeArgument range, DataLocation sequence) -> {
                int min = range.minimum() != null ? range.minimum() : Integer.MIN_VALUE;
                int max = range.maximum() != null ? range.maximum() : Integer.MAX_VALUE;
                if(!isValidRandomSpan(min, max)) {
                    emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
                } else {
                    result.markImplemented();
                    var stack = makeCurrentCommandSourceStack(context);
                    var resultLocal = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.randomRollSequence(resultLocal, stack, min, max, sequence.toString()));
                    result.updateResultValue(resultLocal);
                }
            }
            case CommandRandomReset(DataLocation sequence) -> {
                try(var world = context.getWorld()) {
                    context.writeInstruction(Instructions.randomResetSequence(world.content(), sequence.toString()));
                }
            }
            case CommandRandomResetAll() ->
                context.writeInstruction(Instructions.randomResetAll());

            // clear — native compilation. Vanilla ClearInventoryCommands: the result is the total number of
            // items cleared and the command fails when it is 0; bare `clear` requires a player source.
            case CommandClear() -> {
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var source = context.getCurrentNullableEntity()) {
                    var player = context.<TServerPlayer>createLocal();
                    context.writeInstruction(Instructions.ifNullJump(source.content(), result.markAndGetErrorLabel()));
                    context.writeInstruction(Instructions.tryAsPlayer(player, source.content(), result.markAndGetErrorLabel()));
                    context.writeInstruction(Instructions.clearPlayerInventory(total, player));
                }
                emitClearTotal(context, result, total);
            }
            case CommandClearTargets(SelectorArgument targets) -> emitClear(context, result, targets, null, -1);
            case CommandClearTargetsItem(SelectorArgument targets, String item) -> emitClear(context, result, targets, item, -1);
            case CommandClearTargetsItemMax(SelectorArgument targets, String item, int maxCount) -> emitClear(context, result, targets, item, maxCount);

            // recipe — native compilation
            case CommandRecipeGive(SelectorArgument targets, DataLocation recipe) -> {
                // vanilla RecipeCommand: result = recipes awarded/reset summed over players, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        var player = context.<TServerPlayer>createLocal();
                        selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                        var changed = context.<Integer>createLocal();
                        selector.writeInstruction(Instructions.recipeGive(changed, player, recipe.toString()));
                        selector.writeInstruction(Instructions.valueAdd(total, total, changed));
                    }
                }
                emitClearTotal(context, result, total);
            }
            case CommandRecipeTake(SelectorArgument targets, DataLocation recipe) -> {
                // vanilla RecipeCommand: result = recipes awarded/reset summed over players, 0 = failure
                result.markImplemented();
                var total = context.<Integer>createLocal();
                context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
                try(var _ = context.enterLocalScope()) {
                    try(var selector = context.forSelector(targets, result)) {
                        var player = context.<TServerPlayer>createLocal();
                        selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                        var changed = context.<Integer>createLocal();
                        selector.writeInstruction(Instructions.recipeTake(changed, player, recipe.toString()));
                        selector.writeInstruction(Instructions.valueAdd(total, total, changed));
                    }
                }
                emitClearTotal(context, result, total);
            }

            // TODO: vanilla fallback — особого смысла делать нативными нету (bottleneck в MC API: loot table + fishing/mining context),
            //  но оставлены в fallback для того чтобы замерять нагрузку в spark
            case CommandLootSpawnFish(WorldCoordinates pos, DataLocation lootTable, WorldCoordinates pos2) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootSpawnMine(WorldCoordinates pos, WorldCoordinates pos2, String tool) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootSpawnMineMainhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootSpawnMineOffhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootInsertFish(WorldCoordinates pos, DataLocation lootTable, WorldCoordinates pos2) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootInsertMine(WorldCoordinates pos, WorldCoordinates pos2, String tool) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootInsertMineMainhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootInsertMineOffhand(WorldCoordinates pos, WorldCoordinates pos2) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootGiveFish(SelectorArgument players, DataLocation lootTable, WorldCoordinates pos) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootGiveMine(SelectorArgument players, WorldCoordinates pos, String tool) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootGiveMineMainhand(SelectorArgument players, WorldCoordinates pos) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));
            case CommandLootGiveMineOffhand(SelectorArgument players, WorldCoordinates pos) ->
                emitVanillaFallbackWithResult(context, result, sourceText(context, inputCommand));

            // vanilla fallback for partial compilation
            // If the raw fallback still carries macro placeholders (e.g. `$damage @s
            // $(damage)`), emitVanillaFallback builds the string at runtime from the
            // function's CompoundTag macro args via LOAD_MACRO + the _DYNAMIC variant.
            case CommandFallbackVanilla(String rawCommand) -> {
                if(mayContainReturn(rawCommand)) {
                    // Vanilla would swallow the `return` inside a separately dispatched command (C8).
                    throw new DiagnosticException(Diagnostic.compileError(
                        COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(),
                        "`return` inside an unparsed command would be swallowed; falling back the whole function"));
                }
                emitVanillaFallbackWithResult(context, result, rawCommand);
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
        if((resultContext.generatingResult() || resultContext.generatingSuccess()) && functions.size() > 1 && !resultContext.hasReturnSink()) {
            // vanilla FunctionCommand.queueFunctionsNoReturn with a callback: every function of the tag runs and
            // the callback gets success(sum of results) once, if any of them returned (the Accumulator).
            var anyResult = context.<Integer>createLocal();
            var sum = context.<Integer>createLocal();
            context.writeInstruction(Instructions.dup(anyResult, LocationData.ofInteger(0)));
            context.writeInstruction(Instructions.dup(sum, LocationData.ofInteger(0)));
            for(var function : functions) {
                var resultValue = context.<Long>createLocal();
                var next = context.instructionWriter.createDeferredLabel();
                context.writeInstruction(Instructions2.callFunctionsWithResult(resultValue, List.of(function), stack, arguments));
                context.writeInstruction(Instructions.jumpIfVoidReturnValue(resultValue, next));
                context.writeInstruction(Instructions.dup(anyResult, LocationData.ofInteger(1)));
                var partial = context.<Integer>createLocal();
                context.writeInstruction(Instructions.extractReturnResult(partial, resultValue));
                context.writeInstruction(Instructions.valueAdd(sum, sum, partial));
                context.writeInstruction(Instructions.label(next));
            }
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, anyResult, LocationData.ofInteger(0), resultContext.markAndGetNoResultLabel()));
            resultContext.updateResultValue(sum);
        }else if(resultContext.generatingResult() || resultContext.generatingSuccess()) {
            var resultValue = context.<Long>createLocal();
            context.writeInstruction(Instructions2.callFunctionsWithResult(resultValue, functions, stack, arguments));
            // A function that does not `return` reports nothing to the callbacks (vanilla never calls them).
            context.writeInstruction(Instructions.jumpIfVoidReturnValue(resultValue, resultContext.markAndGetNoResultLabel()));
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

    /** Resolved score holders: one holder ({@code singleName} when it is a constant name), or a materialized list
     * (entities, or names when {@code namedList}). */
    private record StoreHolders(@Nullable LocationData<? extends TScoreHolder> single, @Nullable String singleName, @Nullable LocationData<? extends List<?>> list, boolean namedList) {}

    /** Resolves a score-holder argument once, in the current source context. An empty selection fails
     * {@code failure} when it tracks success (a terminal command), otherwise skips to the context end
     * (a store modifier: vanilla aborts the chain for this source). */
    private static CompilationData<StoreHolders> resolveScoreHolders(CompilationContext context, ScoreHolderArgument holder, @Nullable ResultContext failure) {
        var emptyLabel = failure != null && failure.generatingSuccess() ? failure.markAndGetErrorLabel() : context.current().contextEndLabel();
        if(holder.all()) {
            var holders = context.<List<? extends String>>createLocal();
            context.writeInstruction(Instructions.getScoreboardHolders(holders));
            var length = context.<Integer>createLocal();
            context.writeInstruction(Instructions.getListLength(length, holders));
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), emptyLabel));
            return new CompilationData<>(context, context.current(), new StoreHolders(null, null, holders, true), 0);
        }
        if(holder.name() != null) {
            var scoreHolder = context.<TScoreHolder>createLocal();
            var constantName = CompilationContext.constantScoreHolderName(holder);
            if(constantName != null) {
                context.writeInstruction(Instructions.constScoreHolder(scoreHolder, constantName));
            }else {
                context.writeInstruction(Instructions.namedScoreHolder(scoreHolder, context.getResolvedStringMacro(holder.name())));
            }
            return new CompilationData<>(context, context.current(), new StoreHolders(scoreHolder, constantName, null, false), 0);
        }
        var selector = holder.selector();
        if(selector == null) throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(), "Unsupported score holder"));
        if(holder.single() || selector.self() || selector.playerName() != null || selector.entityUuid() != null || (selector.limit() != null && selector.limit() == 1)) {
            var entity = context.forSelector(selector.limit(1), failure);
            return new CompilationData<>(context, entity.data(), new StoreHolders(entity.content(), null, null, false), entity.stackCount());
        }
        var entities = context.getSelectorList(selector);
        var length = context.<Integer>createLocal();
        context.writeInstruction(Instructions.getListLength(length, entities));
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), emptyLabel));
        return new CompilationData<>(context, context.current(), new StoreHolders(null, null, entities, false), 0);
    }

    /** Emits {@code body} with the score of {@code objective} for one holder, or as a loop over the holder list. */
    private static void forEachScoreHolder(CompilationContext context, StoreHolders holders, LocationData<TObjective> objective, String objectiveName, Consumer<ScoreAccessor> body) {
        if(holders.single() != null) {
            body.accept(new ScoreAccessor(objective, objectiveName, holders.single(), holders.singleName()));
            return;
        }
        var list = holders.list();
        var length = context.<Integer>createLocal();
        context.writeInstruction(Instructions.getListLength(length, list));
        var index = context.<Integer>createLocal();
        context.writeInstruction(Instructions.dup(index, LocationData.ofInteger(0)));
        var loopStart = context.current().writeLabel(context.instructionWriter);
        var loopEnd = context.instructionWriter.createDeferredLabel();
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, loopEnd));
        var element = context.createLocal();
        context.writeInstruction(Instructions.getListElement(element, list, index));
        if(holders.namedList()) {
            var name = context.<String>createLocal();
            context.writeInstruction(Instructions.assumeString(name, element));
            var scoreHolder = context.<TScoreHolder>createLocal();
            context.writeInstruction(Instructions.namedScoreHolder(scoreHolder, name));
            body.accept(new ScoreAccessor(objective, objectiveName, scoreHolder, null));
        }else {
            var entity = context.<TEntity>createLocal();
            context.writeInstruction(Instructions.assumeEntity(entity, element));
            body.accept(new ScoreAccessor(objective, objectiveName, entity, null));
        }
        context.writeInstruction(Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
        context.writeInstruction(Instructions.jump(loopStart));
        context.writeInstruction(Instructions.label(loopEnd));
    }

    /** One application of `scoreboard players operation` (vanilla OperationArgument.apply on the target's and the
     * source's ScoreAccess): target = target OP source. */
    private static void emitScoreOperation(CompilationContext context, ResultContext result, ScoreboardOperation operation, CompilationContext.ScoreSlot target, CompilationContext.ScoreSlot source) {
        var targetValue = context.getScoreValue(target.score());
        var sourceValue = context.getScoreValue(source.score());
        if(operation == ScoreboardOperation.SWAP) {
            context.setScore(target, sourceValue);
            context.setScore(source, targetValue);
            return;
        }
        if(operation == ScoreboardOperation.SET) {
            context.setScore(target, sourceValue);
            return;
        }
        var updated = context.<Integer>createLocal();
        switch(operation) {
            case ADD -> context.writeInstruction(Instructions.valueAdd(updated, targetValue, sourceValue));
            case SUB -> context.writeInstruction(Instructions.valueSub(updated, targetValue, sourceValue));
            case MUL -> context.writeInstruction(Instructions.valueMul(updated, targetValue, sourceValue));
            case DIV -> {
                context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, sourceValue, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
                context.writeInstruction(Instructions.intFloorDiv(updated, targetValue, sourceValue));
            }
            case MOD -> {
                context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, sourceValue, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
                context.writeInstruction(Instructions.intFloorMod(updated, targetValue, sourceValue));
            }
            case MIN -> context.writeInstruction(Instructions.intMin(updated, targetValue, sourceValue));
            case MAX -> context.writeInstruction(Instructions.intMax(updated, targetValue, sourceValue));
            default -> throw new IllegalStateException("unhandled operation " + operation);
        }
        context.setScore(target, updated);
    }

    /** The holder of a score argument when it is a constant name: the command can use a fused ScoreRef call. */
    private static @Nullable String constantHolder(ScoreAccessorArgument accessor) {
        return CompilationContext.constantScoreHolderName(accessor.scoreHolder());
    }

    private static LocationData<Integer> rangeMin(IntRangeArgument range) {
        return LocationData.ofInteger(range.minimum() != null ? range.minimum() : Integer.MIN_VALUE);
    }

    private static LocationData<Integer> rangeMax(IntRangeArgument range) {
        return LocationData.ofInteger(range.maximum() != null ? range.maximum() : Integer.MAX_VALUE);
    }

    /** Adds the new value of the last fused ScoreRef write (set/add/remove/operation) to the result. */
    private static void updateScoreRefResult(CompilationContext context, ResultContext result, String holder, String objective) {
        if(!result.generatingResult()) return;
        var value = context.<Integer>createLocal();
        context.writeInstruction(Instructions.scoreRefLastValue(value, holder, objective));
        result.updateResultValue(value);
    }

    /** `tag … add|remove`: result = number of entities whose tags changed; vanilla fails when none changed. */
    private static void emitTagChange(CompilationContext context, ResultContext result, SelectorArgument targets, String name, boolean add) {
        boolean counting = result.generatingSuccess();
        var changedCount = context.<Integer>createLocal();
        if(counting) context.writeInstruction(Instructions.dup(changedCount, LocationData.ofInteger(0)));
        // the selector loop exits to the end of its enclosing context: scope it so the count check runs after it
        try(var _ = context.enterLocalScope()) {
            try(var entity = context.forSelector(targets, result)) {
                entity.writeInstruction(add
                    ? Instructions.addEntityTag(entity.content(), LocationData.ofString(name), entity.endLabel())
                    : Instructions.removeEntityTag(entity.content(), LocationData.ofString(name), entity.endLabel()));
                if(counting) entity.writeInstruction(Instructions.valueAdd(changedCount, changedCount, LocationData.ofInteger(1)));
            }
        }
        if(counting) {
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, changedCount, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
            result.updateResultValue(changedCount);
        }
    }

    /** `execute store result|success score`: writes every execution's value to the resolved holders
     * (vanilla storeValue callback: one getOrCreatePlayerScore access per holder and execution). */
    private record ScoreStoreSink(LocationData<TObjective> objective, String objectiveName, StoreHolders holders, boolean storeResult) implements ResultContext.Sink {
        @Override
        public boolean needsResult() {
            return storeResult;
        }

        @Override
        public void onSuccess(CompilationContext context, LocationData<Integer> result) {
            write(context, storeResult ? result : LocationData.ofInteger(1));
        }

        @Override
        public void onFailure(CompilationContext context) {
            write(context, LocationData.ofInteger(0));
        }

        private void write(CompilationContext context, LocationData<Integer> value) {
            if(holders.singleName() != null) {
                context.writeInstruction(Instructions.scoreRefStore(objective, holders.singleName(), objectiveName, value));
                return;
            }
            forEachScoreHolder(context, holders, objective, objectiveName, accessor -> context.setScore(context.getOrCreateScore(accessor), value));
        }
    }

    /** `execute store result|success storage`. */
    private record StorageStoreSink(DataLocation storage, NbtPath path, ResultStoreType storeType, double scale, boolean storeResult) implements ResultContext.Sink {
        @Override
        public boolean needsResult() {
            return storeResult;
        }

        @Override
        public void onSuccess(CompilationContext context, LocationData<Integer> result) {
            writeStoreToStorage(context, storage, path, storeType, scale, storeResult ? result : LocationData.ofInteger(1));
        }

        @Override
        public void onFailure(CompilationContext context) {
            writeStoreToStorage(context, storage, path, storeType, scale, LocationData.ofInteger(0));
        }
    }

    /** `return run`: the first execution's outcome becomes the function's return. */
    private enum ReturnSink implements ResultContext.Sink {
        INSTANCE;

        @Override
        public boolean needsResult() {
            return true;
        }

        @Override
        public boolean isReturn() {
            return true;
        }

        @Override
        public void onSuccess(CompilationContext context, LocationData<Integer> result) {
            context.writeInstruction(Instructions.returnSuccess(result));
        }

        @Override
        public void onFailure(CompilationContext context) {
            context.writeInstruction(Instructions.returnFail());
        }
    }

    private static void writeStoreToStorage(CompilationContext context, DataLocation storage, NbtPath path, ResultStoreType storeType, double scale, LocationData<Integer> localResultValue) {
        var storageLocal = context.<TCompoundTag>createLocal();
        context.writeInstruction(Instructions.getNbtStorage(storageLocal, storage));
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
                        }else if(storeType == ResultStoreType.BYTE || storeType == ResultStoreType.SHORT) {
                            if(scale == 1.0d) {
                                innerContext.writeInstruction(storeType == ResultStoreType.BYTE
                                    ? Instructions.createByteTag(tagLocal, localResultValue)
                                    : Instructions.createShortTag(tagLocal, localResultValue));
                            }else {
                                var resultDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.numberToDouble(resultDouble, localResultValue));
                                var multipliedDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(scale)));
                                var convertedInteger = context.<Integer>createLocal();
                                innerContext.writeInstruction(Instructions.numberToInteger(convertedInteger, multipliedDouble));
                                innerContext.writeInstruction(storeType == ResultStoreType.BYTE
                                    ? Instructions.createByteTag(tagLocal, convertedInteger)
                                    : Instructions.createShortTag(tagLocal, convertedInteger));
                            }
                        }else if(storeType == ResultStoreType.LONG) {
                            if(scale == 1.0d) {
                                innerContext.writeInstruction(Instructions.createLongTag(tagLocal, localResultValue));
                            }else {
                                var resultDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.numberToDouble(resultDouble, localResultValue));
                                var multipliedDouble = context.<Double>createLocal();
                                innerContext.writeInstruction(Instructions.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(scale)));
                                innerContext.writeInstruction(Instructions.createLongTagFromDouble(tagLocal, multipliedDouble));
                            }
                        }else {
                            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_STORE, context.getSource(), "Storing into storage with unsupported type: " + storeType));
                        }
            assignNbtValue(context, value, last, tagLocal, null, innerContext.endLabel());
            context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
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
                context.writeInstruction(Instructions.getNbtFromCollection(storage, listAccess.list, listAccess.index));
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
        // Counts only real changes (vanilla CompoundChildNode / IndexedElementNode.setTag return 0 for an equal value).
        var unchanged = modifiedCount != null ? context.instructionWriter.createDeferredLabel() : null;
        if(accessor instanceof NbtPathPart.Named(String name)) {
            var valueCompound = context.<TCompoundTag>createLocal();
            context.writeInstruction(Instructions.tryAsNbtCompound(valueCompound, source, failLabel));
            if(unchanged != null) {
                var previous = context.<TTag>createLocal();
                context.writeInstruction(Instructions.getNbtFromCompound(previous, valueCompound, LocationData.ofString(name)));
                context.writeInstruction(Instructions.ifObjectCompareJump(ObjectComparisonType.EQUAL, previous, value, unchanged));
            }
            context.writeInstruction(Instructions.setNbtInCompound(valueCompound, LocationData.ofString(name), value));
        }else if(accessor instanceof NbtPathPart.ArrayIndexInteger indexed) {
            var listAccess = accessNbtList(context, source, indexed, failLabel);
            if(unchanged != null) {
                var previous = context.<TTag>createLocal();
                context.writeInstruction(Instructions.getNbtFromCollection(previous, listAccess.list, listAccess.index));
                context.writeInstruction(Instructions.ifObjectCompareJump(ObjectComparisonType.EQUAL, previous, value, unchanged));
            }
            context.writeInstruction(Instructions.setNbtInCollection(listAccess.list, listAccess.index, value, failLabel));
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot set nbt value in path " + accessor));
        }
        if(unchanged != null) {
            context.writeInstruction(Instructions.valueAdd(modifiedCount, modifiedCount, LocationData.ofInteger(1)));
            context.writeInstruction(Instructions.label(unchanged));
        }
    }

    private static NbtListAccessResult accessNbtList(CompilationContext context, LocationData<? extends TTag> source, NbtPathPart.ArrayIndexInteger indexed, LabelData failLabel) {
        var list = context.<TCollectionTag>createLocal();
        context.writeInstruction(Instructions.tryAsNbtCollection(list, source, failLabel));
        var length = context.<Integer>createLocal();
        context.writeInstruction(Instructions.getCollectionLength(length, list));
        var index = context.<Integer>createLocal();

        if(indexed.index() >= 0) {
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, LocationData.ofInteger(indexed.index()), length, failLabel));
            context.writeInstruction(Instructions.dup(index, LocationData.ofInteger(indexed.index())));
        }else {
            // For negative indices: fail if abs(index) > length (e.g. list[-3] on a 2-element list)
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN, LocationData.ofInteger(-indexed.index()), length, failLabel));
            context.writeInstruction(Instructions.dup(index, length));
            context.writeInstruction(Instructions.valueSub(index, index, LocationData.ofInteger(-indexed.index())));
        }

        return new NbtListAccessResult(list, index);
    }

    private record NbtListAccessResult(LocationData<TCollectionTag> list, LocationData<Integer> index) {}

    /** setblock/fill: vanilla throws where the runtime helper returns 0 (success is always ≥ 1), so 0 fails the command. */
    private static void updateBlockCommandResult(CompilationContext context, ResultContext result, LocationData<Integer> value) {
        if(result.generatingSuccess() || result.generatingResult()) {
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, value, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
        }
        result.updateResultValue(value);
    }

    // Bits of the tp `rel_flags` instruction constant; must match ExtensionFunctions.TP_REL_*.
    private static final int TP_REL_X = 1, TP_REL_Y = 2, TP_REL_Z = 4, TP_REL_YAW = 8, TP_REL_PITCH = 16;

    /** Vanilla Coordinates.isX/Y/ZRelative: `~` components, and every component of local `^` coordinates. */
    private static int tpRelativeFlags(WorldCoordinates pos) {
        if(pos.localCoordinates()) return TP_REL_X | TP_REL_Y | TP_REL_Z;
        return (pos.isXRelative() ? TP_REL_X : 0) | (pos.isYRelative() ? TP_REL_Y : 0) | (pos.isZRelative() ? TP_REL_Z : 0);
    }

    private static int tpRelativeFlags(RotationCoordinates rot) {
        return (rot.isYawRelative() ? TP_REL_YAW : 0) | (rot.isPitchRelative() ? TP_REL_PITCH : 0);
    }

    private static final java.util.Set<String> TEAM_COLORS = java.util.Set.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "reset");
    private static final java.util.Set<String> TEAM_VISIBILITIES = java.util.Set.of("always", "never", "hideForOtherTeams", "hideForOwnTeam");
    private static final java.util.Set<String> TEAM_COLLISION_RULES = java.util.Set.of("always", "never", "pushOtherTeams", "pushOwnTeam");

    /** Whether `team modify <team> <option> <value>` has a native implementation with exactly vanilla's argument grammar. */
    private static boolean isNativeTeamModify(String option, String value) {
        return switch(option) {
            case "color" -> TEAM_COLORS.contains(value);
            case "friendlyFire", "seeFriendlyInvisibles" -> value.equals("true") || value.equals("false");
            case "nametagVisibility", "deathMessageVisibility" -> TEAM_VISIBILITIES.contains(value);
            case "collisionRule" -> TEAM_COLLISION_RULES.contains(value);
            default -> false; // displayName / prefix / suffix are components resolved against the source
        };
    }

    /**
     * Vanilla RandomCommand.randomSample: span 0 (ERROR_RANGE_TOO_SMALL) and span ≥ Integer.MAX_VALUE
     * (TOO_LARGE) fail; min > max never parses in vanilla. Invalid ranges run through the vanilla fallback.
     */
    private static boolean isValidRandomSpan(int min, int max) {
        long span = (long) max - min;
        return span > 0L && span < Integer.MAX_VALUE;
    }

    /** Vanilla ComponentUtils.DEFAULT_NO_STYLE_SEPARATOR, the NbtContents default separator. */
    private static final Text NBT_TEXT_DEFAULT_SEPARATOR = new Text(List.of(), TextStyle.empty(), new TextContent.LiteralText(", "));

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
                var selectorResult = context.getSelectorList(selector);
                var loadedSeparator = loadText(context, separator != null ? separator : TextContent.SelectorText.DEFAULT_SEPARATOR);
                var textLocation = context.<TMutableComponent>createLocal();
                context.writeInstruction(Instructions.formatEntityTextList(textLocation, selectorResult, loadedSeparator));
                applyStyleAndSiblings(context, text, textLocation);
                return textLocation;
            }
            if(text.content() instanceof TextContent.TranslatableText(String key, _, List<Object> args)) {
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
            if(text.content() instanceof TextContent.ScoreText(ScoreHolderArgument scoreHolder, String objective, String holderSource)) {
                if(scoreHolder.selector() != null) {
                    var selectorResult = context.getSelectorList(scoreHolder.selector());
                    var textLocation = context.<TComponent>createLocal();
                    context.writeInstruction(Instructions.loadScoreText(textLocation, selectorResult, objective, holderSource));
                    // null = vanilla ERROR_NOT_SINGLE_ENTITY: text resolution throws, so the command does nothing
                    context.writeInstruction(Instructions.ifNullJump(textLocation, context.current().contextEndLabel()));
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
            if(text.content() instanceof TextContent.NbtText(_, String rawPath, boolean interpret, boolean plain, Text separator, NbtDataLocation source)) {
                // Vanilla NbtContents: every matched tag of every source, joined with the separator
                // (default ", " without style); the runtime evaluates rawPath with NbtPathArgument.
                var loadedSeparator = loadText(context, separator != null ? separator : NBT_TEXT_DEFAULT_SEPARATOR);
                LocationData<TComponent> textLocation = null;
                if(source instanceof NbtDataLocation.Storage(DataLocation storage)) {
                    textLocation = context.createLocal();
                    if(interpret) {
                        context.writeInstruction(Instructions.getNbtTextInterpreted(textLocation, makeCurrentCommandSourceStack(context), storage.toString(), rawPath, loadedSeparator));
                    }else {
                        context.writeInstruction(Instructions.getNbtText(textLocation, storage.toString(), rawPath, plain, loadedSeparator));
                    }
                }else if(source instanceof NbtDataLocation.Entity(SelectorArgument selector)) {
                    var entityList = context.getSelectorList(selector);
                    textLocation = context.createLocal();
                    if(interpret) {
                        context.writeInstruction(Instructions.getEntityNbtTextInterpreted(textLocation, makeCurrentCommandSourceStack(context), entityList, rawPath, loadedSeparator));
                    }else {
                        context.writeInstruction(Instructions.getEntityNbtText(textLocation, entityList, rawPath, plain, loadedSeparator));
                    }
                }
                if(textLocation != null) {
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
                // Bake only content as a constant component (empty style, no siblings),
                // then apply style (possibly with runtime hoverEvent) and siblings at runtime.
                var innerText = new Text(List.of(), TextStyle.empty(), text.content());
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
            TextStyle baked = text.style();
            Text runtimeHover = null;
            // If the hoverEvent's Text is non-constant (e.g. contains ScoreText/NbtText),
            // we can't bake it into the Style statically. Strip it and apply at runtime.
            if(baked.hoverEvent() != null
                    && baked.hoverEvent().action() == TextHoverEvent.Action.SHOW_TEXT
                    && !((Text) baked.hoverEvent().value()).isConstant()) {
                runtimeHover = (Text) baked.hoverEvent().value();
                baked = new TextStyle(
                    baked.color(), baked.shadowColor(),
                    baked.bold(), baked.italic(), baked.underlined(), baked.strikethrough(), baked.obfuscated(),
                    baked.clickEvent(), null, baked.insertion(), baked.font()
                );
            }
            if(!baked.isEmpty()) {
                var style = context.<TStyle>createLocal();
                context.writeInstruction(Instructions.loadTextStyle(style, baked));
                context.writeInstruction(Instructions.setTextStyle(textLocation, style));
            }
            if(runtimeHover != null) {
                var hoverComp = loadText(context, runtimeHover);
                context.writeInstruction(Instructions.applyHoverText(textLocation, hoverComp));
            }
        }
        for(var sibling : text.siblings()) {
            var otherText = loadText(context, sibling);
            context.writeInstruction(Instructions.appendText(textLocation, otherText));
        }
    }

    // Split a raw fallback string like `damage @s $(damage) gbg:bullet_standard` into
    // a MacroTemplate<String> of alternating literal / MacroKind parts so LOAD_MACRO
    // can emit the string concat at runtime. Unterminated `$(` is treated as literal.
    private static MacroTemplate<String> parseVanillaMacroTemplate(String raw) {
        var parts = new java.util.ArrayList<MacroTemplate.MacroPart>();
        int i = 0;
        int literalStart = 0;
        while(i < raw.length()) {
            if(raw.charAt(i) == '$' && i + 1 < raw.length() && raw.charAt(i + 1) == '(') {
                int end = raw.indexOf(')', i + 2);
                if(end < 0) break;
                if(i > literalStart) {
                    parts.add(new MacroTemplate.LiteralKind<>(raw.substring(literalStart, i)));
                }
                parts.add(new MacroTemplate.MacroKind(raw.substring(i + 2, end), List.of()));
                i = end + 1;
                literalStart = i;
            } else {
                i++;
            }
        }
        if(literalStart < raw.length()) {
            parts.add(new MacroTemplate.LiteralKind<>(raw.substring(literalStart)));
        }
        return new MacroTemplate<>(parts, MacroType.STRING_MACRO_TYPE);
    }

    // ── Verbatim source text for vanilla fallbacks ────────────────────────────
    // Vanilla fallbacks always receive the command's original source text (from the
    // parser's CommandSourceMap side table), never a re-serialization of parsed
    // arguments: re-serializing lost `^` coordinates, selector filters, integer block
    // positions, quoting, etc. A node without a recorded span throws so the whole
    // function falls back to vanilla instead of running a wrong command.

    private static DiagnosticException missingSource(CompilationContext context, CommandParams node) {
        return new DiagnosticException(Diagnostic.compileError(
            COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(),
            "No source text recorded for " + node.getClass().getSimpleName() + " vanilla fallback; falling back the whole function"));
    }

    /** Full verbatim source text of {@code node} (including any nested `run` command). */
    private static String sourceText(CompilationContext context, CommandParams node) {
        var sources = context.getCommandSource();
        var text = sources == null ? null : sources.text(node);
        if(text == null) throw missingSource(context, node);
        return text;
    }

    /** Full verbatim source text of {@code node}; throws when the nested command contains a
     * `return`, which vanilla would swallow inside a separately dispatched command (C8). */
    private static String sourceTextWithoutReturn(CompilationContext context, CommandParams node) {
        var text = sourceText(context, node);
        if(mayContainReturn(text)) {
            throw new DiagnosticException(Diagnostic.compileError(
                COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(),
                "`return` inside a vanilla-fallback command would be swallowed; falling back the whole function"));
        }
        return text;
    }

    private static final java.util.regex.Pattern RUN_SEPARATOR = java.util.regex.Pattern.compile("\\srun\\s+");

    /** Conservative: true when a `return` (or a macro placeholder that could expand to one)
     * appears in command position. A placeholder anywhere inside an `execute` chain (before its
     * final `run`, or in a final `execute` segment) can expand to `… run return …`.
     * False positives only cost a whole-function fallback. */
    private static boolean mayContainReturn(String text) {
        var segments = RUN_SEPARATOR.split(text, -1);
        for(int i = 0; i < segments.length; i++) {
            var segment = segments[i];
            if(segment.startsWith("$(") || segment.equals("return") || segment.startsWith("return ")) return true;
            boolean executeSegment = i < segments.length - 1 || segment.startsWith("execute ");
            if(executeSegment && segment.contains("$(")) return true;
        }
        return false;
    }

    /** `$(…)` placeholders are substituted only on macro lines; elsewhere they are literal text (C25). */
    private static boolean isMacroText(CompilationContext context, String command) {
        var sources = context.getCommandSource();
        return sources != null && sources.isMacroLine() && command.indexOf("$(") >= 0;
    }

    /** `execute (if|unless) <condition> <body>` where the condition has no native implementation:
     * evaluate the terminal condition form (`execute if …` without the body) through vanilla and
     * compile the body natively, so `return`, result tracking and forking behave like native code. */
    private static void compileVanillaCondition(CompilationContext context, @Nullable ForkingContext forkingContext, CommandParams node, CommandParams body, ResultContext result) {
        var sources = context.getCommandSource();
        var condition = sources == null ? null : sources.prefix(node, body);
        if(condition == null) throw missingSource(context, node);
        try(var fContext = context.withForkingContext(forkingContext, node)) {
            var stack = makeCurrentCommandSourceStack(context);
            var returnValue = context.<Long>createLocal();
            if(isMacroText(context, condition)) {
                var resolved = context.getResolvedStringMacro(parseVanillaMacroTemplate(condition));
                context.writeInstruction(Instructions.executeVanillaCommandWithResultDynamic(returnValue, stack, resolved));
            } else {
                context.writeInstruction(Instructions.executeVanillaCommandWithResult(returnValue, stack, condition));
            }
            var failedValue = context.<Integer>createLocal();
            context.writeInstruction(Instructions.extractReturnSuccess(failedValue, returnValue));
            context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, failedValue, LocationData.ofInteger(0), context.current().contextEndLabel()));
            compile(context, fContext.content(), body, result);
        }
    }

    // Вспомогательный метод — vanilla fallback через executeVanillaCommand с текущим стеком
    private static void emitVanillaFallback(CompilationContext context, String command) {
        var stack = makeCurrentCommandSourceStack(context);
        // Same `$(...)` substitution as the CommandFallbackVanilla case. Parsers
        // that accept greedy_string/macro arguments (item modify, tellraw, data
        // modify value, etc.) funnel their raw text through here, and without
        // this branch macro placeholders survived into the vanilla dispatch and
        // broke macro functions that relied on us resolving them (e.g.
        // gbg:misc/update_model → no scope model swap).
        if(isMacroText(context, command)) {
            var template = parseVanillaMacroTemplate(command);
            var resolved = context.getResolvedStringMacro(template);
            context.writeInstruction(Instructions.executeVanillaCommandDynamic(stack, resolved));
        } else {
            context.writeInstruction(Instructions.executeVanillaCommand(stack, command));
        }
    }

    /** Vanilla fallback for a terminal command whose success/result feed the enclosing
     * {@link ResultContext} (`execute store …`, `return run …`). Vanilla failure (thrown
     * CommandSyntaxException, reported as (false, 0) through the source callback) jumps to
     * the result context's error label; success adds vanilla's result value. */
    private static void emitVanillaFallbackWithResult(CompilationContext context, ResultContext result, String command) {
        result.markImplemented();
        if(!result.generatingResult() && !result.generatingSuccess()) {
            emitVanillaFallback(context, command);
            return;
        }
        var stack = makeCurrentCommandSourceStack(context);
        var returnValue = context.<Long>createLocal();
        if(isMacroText(context, command)) {
            var template = parseVanillaMacroTemplate(command);
            var resolved = context.getResolvedStringMacro(template);
            context.writeInstruction(Instructions.executeVanillaCommandWithResultDynamic(returnValue, stack, resolved));
        } else {
            context.writeInstruction(Instructions.executeVanillaCommandWithResult(returnValue, stack, command));
        }
        var failedValue = context.<Integer>createLocal();
        context.writeInstruction(Instructions.extractReturnSuccess(failedValue, returnValue));
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, failedValue, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
        if(result.generatingResult()) {
            var resultValue = context.<Integer>createLocal();
            context.writeInstruction(Instructions.extractReturnResult(resultValue, returnValue));
            result.updateResultValue(resultValue);
        }
    }

    /** Native command whose helper returns the compiled-function return encoding (COMMAND_FAILED, or the
     * vanilla result in the low 32 bits): failure jumps to the error label, success adds the result. */
    private static void emitEncodedResult(CompilationContext context, ResultContext result, LocationData<Long> returnValue) {
        result.markImplemented();
        if(!result.generatingResult() && !result.generatingSuccess()) return;
        var failedValue = context.<Integer>createLocal();
        context.writeInstruction(Instructions.extractReturnSuccess(failedValue, returnValue));
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, failedValue, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
        if(result.generatingResult()) {
            var resultValue = context.<Integer>createLocal();
            context.writeInstruction(Instructions.extractReturnResult(resultValue, returnValue));
            result.updateResultValue(resultValue);
        }
    }

    /** ExtensionFunctions.TP_SOURCE_ANCHOR_*: the source anchor vanilla LookAt uses, when it is known at compile time. */
    private static int tpSourceAnchor(CompilationContext context) {
        var anchor = context.current().currentAnchor();
        return anchor == null ? 2 : anchor == LocationAnchor.EYES ? 1 : 0;
    }

    private static LocationData<TCommandSourceStack> makeCurrentCommandSourceStack(CompilationContext context) {
        var current = context.current();
        var stack = context.<TCommandSourceStack>createLocal();
        context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
        return stack;
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
                    sb.append(escapeNbtKey(e.getKey())).append(':').append(nbtValueToString(e.getValue()));
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

    private static final java.util.regex.Pattern NBT_SIMPLE_KEY = java.util.regex.Pattern.compile("[A-Za-z0-9._+-]+");

    private static String escapeNbtKey(String key) {
        if(NBT_SIMPLE_KEY.matcher(key).matches()) return key;
        return '"' + key.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** `data modify storage <path> append|prepend|insert <index> value <v>` (vanilla NbtPath.insert): the list at
     * the full path is created when missing; negative indices count from the end (append = -1); the result is
     * the number of lists changed and the command fails when nothing changed. */
    private static void compileDataModifyListOpValue(CompilationContext context, ResultContext result, DataLocation storage, NbtPath path, NbtValue setValue, int index) {
        result.markImplemented();
        var storageLocal = context.<TCompoundTag>createLocal();
        context.writeInstruction(Instructions.getNbtStorage(storageLocal, storage));
        var changed = context.<Integer>createLocal();
        context.writeInstruction(Instructions.dup(changed, LocationData.ofInteger(0)));
        var parts = path.parts();
        try(var inner = context.enterLocalScope()) {
            var failLabel = inner.endLabel();
            var parent = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, failLabel);
            LocationData<? extends TTag> container;
            if(parts.getLast() instanceof NbtPathPart.Named(String name)) {
                var compound = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.tryAsNbtCompound(compound, parent, failLabel));
                var created = context.<TTag>createLocal();
                context.writeInstruction(Instructions.getNbtListFromCompoundOrCreate(created, compound, LocationData.ofString(name)));
                container = created;
            }else {
                container = getNbtPathValue(context, parent, List.of(parts.getLast()), false, failLabel);
                context.writeInstruction(Instructions.ifNullJump(container, failLabel));
            }
            var listTag = context.<TListTag>createLocal();
            context.writeInstruction(Instructions.tryAsNbtList(listTag, container, failLabel));
            var tagLocal = context.<TTag>createLocal();
            var tagCopied = context.<TTag>createLocal();
            context.writeInstruction(Instructions.loadNbtValue(tagLocal, setValue));
            context.writeInstruction(Instructions.cloneNbtValue(tagCopied, tagLocal));
            if(index == -1) {
                context.writeInstruction(Instructions.nbtListAppend(listTag, tagCopied, failLabel));
            }else if(index >= 0) {
                context.writeInstruction(Instructions.nbtListInsert(listTag, LocationData.ofInteger(index), tagCopied, failLabel));
            }else {
                var length = context.<Integer>createLocal();
                context.writeInstruction(Instructions.getListLength(length, listTag));
                var actualIndex = context.<Integer>createLocal();
                context.writeInstruction(Instructions.valueAdd(actualIndex, length, LocationData.ofInteger(index + 1)));
                context.writeInstruction(Instructions.nbtListInsert(listTag, actualIndex, tagCopied, failLabel));
            }
            context.writeInstruction(Instructions.dup(changed, LocationData.ofInteger(1)));
            context.writeInstruction(Instructions.setNbtStorage(storage, storageLocal));
        }
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, changed, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
        result.updateResultValue(changed);
    }

    /** `execute if|unless dimension <dim>`: identity compare with the source level; an unknown dimension
     * aborts this source (vanilla DimensionArgument throws). */
    private static void compileDimensionCondition(CompilationContext context, @Nullable ForkingContext forkingContext, CommandParams node, DataLocation dimension, CommandParams body, ResultContext result, boolean expected) {
        try(var fContext = context.withForkingContext(forkingContext, node)) {
            try(var world = context.getWorld()) {
                var skipLabel = context.current().contextEndLabel();
                var target = context.<TServerLevel>createLocal();
                context.writeInstruction(Instructions.getDimensionLevel(target, dimension.toString()));
                context.writeInstruction(Instructions.ifNullJump(target, skipLabel));
                context.writeInstruction(Instructions.ifObjectCompareJump(expected ? ObjectComparisonType.NOT_IDENTITY_EQUAL : ObjectComparisonType.IDENTITY_EQUAL, world.content(), target, skipLabel));
                compile(context, fContext.content(), body, result);
            }
        }
    }

    /** `execute if|unless loaded <pos>` (vanilla ExecuteCommand.isChunkLoaded on the source level). */
    private static void compileLoadedCondition(CompilationContext context, @Nullable ForkingContext forkingContext, CommandParams node, WorldCoordinates pos, CommandParams body, ResultContext result, boolean expected) {
        try(var fContext = context.withForkingContext(forkingContext, node)) {
            try(var world = context.getWorld()) {
                try(var posData = context.applyPosition(pos)) {
                    var loaded = context.<Integer>createLocal();
                    context.writeInstruction(Instructions.isBlockLoaded(loaded, world.content(), posData.content()));
                    context.writeInstruction(Instructions.ifNumberCompareJump(expected ? NumberComparisonType.EQUAL : NumberComparisonType.NOT_EQUAL, loaded, LocationData.ofInteger(0), context.current().contextEndLabel()));
                    compile(context, fContext.content(), body, result);
                }
            }
        }
    }

    /** `data get entity|storage <path> [scale]` (vanilla DataCommands.getData / getNumeric); a missing path fails. */
    private static void compileDataGet(CompilationContext context, ResultContext result, NbtDataLocation source, NbtPath path, @Nullable Double scale) {
        result.markImplemented();
        // Only feedback text is produced when nobody consumes the result.
        if(!result.generatingSuccess()) return;
        switch(source) {
            case NbtDataLocation.Entity(SelectorArgument selector) -> {
                try(var entityData = context.forSelector(selector, result)) {
                    var vectorElement = entityVectorElement(path);
                    if(vectorElement != null) {
                        var tagVal = context.<TTag>createLocal();
                        entityData.writeInstruction(Instructions.getEntityVectorElement(tagVal, entityData.content(), vectorElement[0], vectorElement[1]));
                        emitDataGetNumber(context, result, tagVal, scale);
                        return;
                    }
                    var nbtLocal = context.<TCompoundTag>createLocal();
                    entityData.writeInstruction(Instructions.getEntityNbt(nbtLocal, entityData.content()));
                    emitDataGetValue(context, result, nbtLocal, path, scale);
                }
            }
            case NbtDataLocation.Storage(DataLocation storage) -> {
                var nbtLocal = context.<TCompoundTag>createLocal();
                context.writeInstruction(Instructions.getNbtStorage(nbtLocal, storage));
                emitDataGetValue(context, result, nbtLocal, path, scale);
            }
            case NbtDataLocation.Block _ -> throw new IllegalStateException("block data get is compiled as a vanilla fallback");
        }
    }

    /** Entity.saveWithoutId lists answered by GET_ENTITY_VECTOR_ELEMENT, indexed by ExtensionFunctions.ENTITY_VECTOR_*, with their sizes. */
    private static final List<String> ENTITY_VECTOR_KEYS = List.of("Pos", "Motion", "Rotation");
    private static final int[] ENTITY_VECTOR_SIZES = {3, 3, 2};

    /**
     * {vector, index} when {@code path} is exactly {@code Pos|Motion|Rotation[i]} with an index inside the list
     * (negative indices count from the end, like NbtPathArgument), else null. Every entity saves these lists with a
     * fixed size, so the element can be read without serializing the entity.
     */
    private static int @Nullable [] entityVectorElement(NbtPath path) {
        var parts = path.parts();
        if(parts.size() != 2 || !(parts.get(0) instanceof NbtPathPart.Named(String name)) || !(parts.get(1) instanceof NbtPathPart.ArrayIndexInteger(int index))) return null;
        int vector = ENTITY_VECTOR_KEYS.indexOf(name);
        if(vector < 0) return null;
        int size = ENTITY_VECTOR_SIZES[vector];
        int resolved = index < 0 ? size + index : index;
        return resolved >= 0 && resolved < size ? new int[]{vector, resolved} : null;
    }

    private static void emitDataGetValue(CompilationContext context, ResultContext result, LocationData<TCompoundTag> nbt, NbtPath path, @Nullable Double scale) {
        var tagVal = getNbtPathValue(context, nbt, path.parts(), false, result.markAndGetErrorLabel());
        context.writeInstruction(Instructions.ifNullJump(tagVal, result.markAndGetErrorLabel()));
        emitDataGetNumber(context, result, tagVal, scale);
    }

    private static void emitDataGetNumber(CompilationContext context, ResultContext result, LocationData<? extends TTag> tagVal, @Nullable Double scale) {
        if(!result.generatingResult() && scale == null) return;
        var intVal = context.<Integer>createLocal();
        if(scale == null) {
            context.writeInstruction(Instructions.nbtTagToInt(intVal, tagVal));
        }else {
            context.writeInstruction(Instructions.nbtNumericToIntScaled(intVal, tagVal, scale, result.markAndGetErrorLabel()));
        }
        result.updateResultValue(intVal);
    }

    /** `clear <targets> [item] [maxCount]`: sums the per-player counts ({@code item == null}: whole inventory). */
    private static void emitClear(CompilationContext context, ResultContext result, SelectorArgument targets, @Nullable String item, int maxCount) {
        result.markImplemented();
        var total = context.<Integer>createLocal();
        context.writeInstruction(Instructions.dup(total, LocationData.ofInteger(0)));
        try(var _ = context.enterLocalScope()) {
            try(var selector = context.forSelector(targets, result)) {
                var player = context.<TServerPlayer>createLocal();
                selector.writeInstruction(Instructions.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                var cleared = context.<Integer>createLocal();
                selector.writeInstruction(item == null
                    ? Instructions.clearPlayerInventory(cleared, player)
                    : Instructions.clearPlayerItem(cleared, player, item, maxCount));
                selector.writeInstruction(Instructions.valueAdd(total, total, cleared));
            }
        }
        emitClearTotal(context, result, total);
    }

    private static void emitClearTotal(CompilationContext context, ResultContext result, LocationData<Integer> total) {
        if(!result.generatingSuccess()) return;
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, total, LocationData.ofInteger(0), result.markAndGetErrorLabel()));
        result.updateResultValue(total);
    }

}
