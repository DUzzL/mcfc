package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.DoubleRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.LocationAnchor;
import dev.epicpix.minecraftfunctioncompiler.data.RotationCoordinates;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreAccessorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.ScoreHolderArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorAdvancementCheck;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorSort;
import dev.epicpix.minecraftfunctioncompiler.data.WorldCoordinates;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.ObjectComparisonType;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TCommandSourceStack;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TEntity;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TEntityType;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TTag;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TGameType;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjective;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TReadOnlyScoreInfo;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScore;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScoreHolder;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TServerLevel;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TServerPlayer;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TTeam;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TVec2;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TVec3;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNEXPECTED_CONTEXT_END;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNREACHABLE_CODE;

public class CompilationContext {

    private final DataLocation source;
    private final ArrayDeque<CompilationContextData> stack = new ArrayDeque<>();
    private final UniqueIdTracker idTracker = new UniqueIdTracker();
    public final InstructionWriter instructionWriter = new InstructionWriter();

    @SuppressWarnings("this-escape")
    public CompilationContext(DataLocation source) {
        this.source = source;
        var commandStack = this.<TCommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getCommandStack(commandStack), instructionWriter.getEndLabel());
        enterWith(CompilationContextData.create(commandStack, instructionWriter.getEndLabel()));
    }

    public DataLocation getSource() {
        return source;
    }

    // Verbatim source side table of the top-level command being compiled (owned by the
    // raw-text vanilla fallback code in CommandCompiler); null when compiling commands
    // that did not come from the parser.
    private dev.epicpix.minecraftfunctioncompiler.commands.@org.jspecify.annotations.Nullable CommandSourceMap commandSource;

    public dev.epicpix.minecraftfunctioncompiler.commands.@org.jspecify.annotations.Nullable CommandSourceMap getCommandSource() {
        return commandSource;
    }

    public void setCommandSource(dev.epicpix.minecraftfunctioncompiler.commands.@org.jspecify.annotations.Nullable CommandSourceMap commandSource) {
        this.commandSource = commandSource;
    }

    // Splits oversized execute chains into separate methods; null when compiling normally.
    private @org.jspecify.annotations.Nullable ChainOutliner outliner;

    public @org.jspecify.annotations.Nullable ChainOutliner getOutliner() {
        return outliner;
    }

    public void setOutliner(@org.jspecify.annotations.Nullable ChainOutliner outliner) {
        this.outliner = outliner;
    }

    @NullUnmarked
    public <T> @NonNull CompilationData<T> enterWithValue(@NonNull CompilationContextData data, T value, int stackCount) {
        enterWith(data);
        return new CompilationData<>(this, data, value, stackCount);
    }

    public <T> CompilationData<T> enterWithValue(CompilationContextData data, T value) {
        return enterWithValue(data, value, 1);
    }

    public CompilationData<Object> enterWithoutValue(CompilationContextData data) {
        return enterWithValue(data, null, 1);
    }

    public CompilationData<Object> enterLocalScope() {
        return enterLocalScope(current());
    }

    public CompilationData<Object> enterLocalScope(CompilationContextData data) {
        var codeLocation = data.writeLabel(instructionWriter);
        return enterWithValue(data.withContextLabel(codeLocation), null, 1);
    }

    public CompilationContextData current() {
        return stack.peek();
    }

    public void enterWith(CompilationContextData data) {
        stack.push(data);
    }

    public List<Instruction> end() {
        if(stack.size() == 1) {
            return instructionWriter.getInstructions();
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNEXPECTED_CONTEXT_END, source, "end() called while not at root context"));
        }
    }

    public void exit() {
        stack.pop();
    }

    public void writeInstruction(Instruction instruction) {
        current().writeInstruction(instructionWriter, instruction);
    }

    public int createId() {
        return idTracker.createId();
    }

    public <T> LocationData<T> createLocal() {
        return LocationData.ofLocal(createId());
    }

    public LocationData<String> getResolvedStringMacro(MacroTemplate<String> macro) {
        if(macro.isStatic()) {
            return LocationData.ofString(macro.getStaticValue());
        }
        var macroString = this.<String>createLocal();
        writeInstruction(Instructions.loadMacro(macroString, macro));
        return macroString;
    }

    public LocationData<Integer> getResolvedIntegerMacro(MacroTemplate<Integer> macro) {
        if(macro.isStatic()) {
            return LocationData.ofInteger(macro.getStaticValue());
        }
        var macroInt = this.<Integer>createLocal();
        writeInstruction(Instructions.loadMacro(macroInt, macro));
        return macroInt;
    }

    public CompilationData<@Nullable LocationData<? extends TEntity>> getCurrentEntity() {
        var data = current();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<TEntity> entity = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackEntity(entity, data.commandSourceStack()));
            data.writeInstruction(instructionWriter, Instructions.ifNullJump(entity, data.contextEndLabel()));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation).withCurrentEntity(entity), entity);
        }
    }

    /** The executing entity, or null (a server/console source). Never published as {@code currentEntity}:
     * later {@link #getCurrentEntity()} calls must still null-check it. */
    public CompilationData<@Nullable LocationData<? extends TEntity>> getCurrentNullableEntity() {
        var data = current();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<TEntity> entity = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackEntity(entity, data.commandSourceStack()));
            return new CompilationData<>(this, data, entity, 0);
        }
    }

    public CompilationData<@Nullable LocationData<TVec3>> getPosition() {
        var data = current();
        if(data.currentPosition() != null) {
            return new CompilationData<>(this, data, data.currentPosition(), 0);
        }else {
            LocationData<TVec3> position = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackPosition(position, data.commandSourceStack()));
            return enterWithValue(data.withCurrentPosition(position), position);
        }
    }

    public CompilationData<@Nullable LocationData<TVec2>> getRotation() {
        var data = current();
        if(data.currentRotation() != null) {
            return new CompilationData<>(this, data, data.currentRotation(), 0);
        }else {
            LocationData<TVec2> rotation = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackRotation(rotation, data.commandSourceStack()));
            return enterWithValue(data.withCurrentRotation(rotation), rotation);
        }
    }

    public CompilationData<@Nullable LocationData<TServerLevel>> getWorld() {
        var data = current();
        if(data.currentWorld() != null) {
            return new CompilationData<>(this, data, data.currentWorld(), 0);
        }else {
            LocationData<TServerLevel> world = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackWorld(world, data.commandSourceStack()));
            return enterWithValue(data.withCurrentWorld(world), world);
        }
    }

    public CompilationData<LocationData<TVec3>> applyPosition(WorldCoordinates pos) {
        if(pos.localCoordinates()) {
            var entityData = getCurrentNullableEntity();
            var positionData = getPosition();
            var rotationData = getRotation();

            var offset = this.<Double>createLocal();
            rotationData.writeInstruction(Instructions.dup(offset, LocationData.ofDouble(0.0)));
            var position = this.<TVec3>createLocal();
            if(rotationData.data().currentAnchor() == LocationAnchor.EYES) {
                var skipCode = instructionWriter.createLabelBefore(rotationData.endLabel());
                var reassignedOffset = this.<Double>createLocal();
                instructionWriter.addInstructionBefore(Instructions.ifNullJump(entityData.content(), skipCode), skipCode);
                instructionWriter.addInstructionBefore(Instructions.getEyeHeight(reassignedOffset, entityData.content()), skipCode);
                instructionWriter.addInstructionBefore(Instructions.dup(offset, reassignedOffset), skipCode);
            }else if(rotationData.data().currentAnchor() == null) {
                var skipCode = instructionWriter.createLabelBefore(rotationData.endLabel());
                var reassignedOffset = this.<Double>createLocal();
                instructionWriter.addInstructionBefore(Instructions.ifNullJump(entityData.content(), skipCode), skipCode);
                instructionWriter.addInstructionBefore(Instructions.getAnchorOffset(reassignedOffset, rotationData.data().commandSourceStack(), entityData.content()), skipCode);
                instructionWriter.addInstructionBefore(Instructions.dup(offset, reassignedOffset), skipCode);
            }
            rotationData.writeInstruction(Instructions.applyLocalPositionWorld(position, positionData.content(), rotationData.content(), offset, pos));
            return enterWithValue(rotationData.data(), position, entityData.stackCount() + positionData.stackCount() + rotationData.stackCount() + 1);
        }
        var positionData = getPosition();
        var position = this.<TVec3>createLocal();
        positionData.writeInstruction(Instructions.applyPositionWorld(position, positionData.content(), pos));
        return enterWithValue(positionData.data(), position, positionData.stackCount() + 1);
    }

    public CompilationData<LocationData<TVec2>> applyRotation(RotationCoordinates rot) {
        var rotationData = getRotation();
        var rotation = this.<TVec2>createLocal();
        rotationData.writeInstruction(Instructions.applyRotationWorld(rotation, rotationData.content(), rot));
        return enterWithValue(rotationData.data(), rotation, rotationData.stackCount() + 1);
    }

    public CompilationData<LocationData<? extends TEntity>> forEntity(UUID entityUuid) {
        var data = current();
        var worldIter = this.<Iterator<? extends TServerLevel>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getWorldsIterator(worldIter));
        var iterStart = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(worldIter, data.contextEndLabel()));
        var worldObj = this.createLocal();
        var world = this.<TServerLevel>createLocal();
        data.writeInstruction(instructionWriter, Instructions.iteratorNext(worldObj, worldIter));
        data.writeInstruction(instructionWriter, Instructions.assumeWorld(world, worldObj));

        var entity = this.<TEntity>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getEntity(entity, world, entityUuid));
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(entity, iterStart));

        var codeLocation = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.jump(data.contextEndLabel()));

        return enterWithValue(data.withContextLabel(codeLocation), entity);
    }

    public CompilationData<LocationData<? extends TEntity>> forEachEntity(@Nullable LocationData<TServerLevel> inWorld) {
        var data = current();
        if(inWorld == null) {
            var worldIter = this.<Iterator<? extends TServerLevel>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getWorldsIterator(worldIter));
            var iterWorldStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(worldIter, data.contextEndLabel()));
            var worldObj = this.createLocal();
            var world = this.<TServerLevel>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(worldObj, worldIter));
            data.writeInstruction(instructionWriter, Instructions.assumeWorld(world, worldObj));

            var entityIter = this.<Iterator<? extends TEntity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntities(entityIter, world));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(entityIter, iterWorldStart));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(entityObj, entityIter));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }else {
            var entityIter = this.<Iterator<? extends TEntity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntities(entityIter, inWorld));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(entityIter, data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(entityObj, entityIter));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    // Fast path for unbounded `@e[type=X]` (single concrete type, not negated):
    // iterate the live EntityLookup type index for that type instead of scanning every
    // entity and filtering by getType(). forSelectorBase still applies the type filter as
    // a (now cheap) recheck over the already-narrowed set, so semantics are unchanged.
    public CompilationData<LocationData<? extends TEntity>> forEachEntityOfType(@Nullable LocationData<TServerLevel> inWorld, DataLocation entityType) {
        var data = current();
        var type = this.<TEntityType>createLocal();
        data.writeInstruction(instructionWriter, Instructions.mapEntityType(type, entityType));
        if(inWorld == null) {
            var worldIter = this.<Iterator<? extends TServerLevel>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getWorldsIterator(worldIter));
            var iterWorldStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(worldIter, data.contextEndLabel()));
            var worldObj = this.createLocal();
            var world = this.<TServerLevel>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(worldObj, worldIter));
            data.writeInstruction(instructionWriter, Instructions.assumeWorld(world, worldObj));

            var entityIter = this.<Iterator<? extends TEntity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntitiesOfType(entityIter, world, type));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(entityIter, iterWorldStart));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(entityObj, entityIter));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }else {
            var entityIter = this.<Iterator<? extends TEntity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntitiesOfType(entityIter, inWorld, type));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(entityIter, data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(entityObj, entityIter));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    private CompilationData<LocationData<? extends TEntity>> forEachEntityInside(@Nullable LocationData<TServerLevel> inWorld, SelectorBox box) {
        var data = current();
        if(inWorld == null) {
            var worldIter = this.<Iterator<? extends TServerLevel>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getWorldsIterator(worldIter));
            var iterWorldStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(worldIter, data.contextEndLabel()));
            var worldObj = this.createLocal();
            var world = this.<TServerLevel>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(worldObj, worldIter));
            data.writeInstruction(instructionWriter, Instructions.assumeWorld(world, worldObj));
            var entities = this.<List<? extends TEntity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntitiesInside(entities, world, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            var listLength = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListLength(listLength, entities));

            var index = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, listLength, iterWorldStart));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entities, index));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));

            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }else {
            var entities = this.<List<? extends TEntity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntitiesInside(entities, inWorld, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            var listLength = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListLength(listLength, entities));

            var index = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
            var iterStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, listLength, data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entities, index));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
            data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    public CompilationData<LocationData<TServerPlayer>> forPlayer(String playerName) {
        var data = current();

        var player = this.<TServerPlayer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getPlayer(player, LocationData.ofString(playerName)));
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(player, data.contextEndLabel()));
        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), player);
    }

    /** Players in PlayerList order, or in {@code inWorld.players()} order when world-limited (EntitySelector.findPlayers). */
    public CompilationData<LocationData<TServerPlayer>> forEachPlayer(@Nullable LocationData<TServerLevel> inWorld) {
        var data = current();

        var index = this.<Integer>createLocal();
        var length = this.<Integer>createLocal();
        var playerList = this.<List<? extends TServerPlayer>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
        if(inWorld != null) {
            data.writeInstruction(instructionWriter, Instructions.getLevelPlayers(playerList, inWorld));
        }else {
            data.writeInstruction(instructionWriter, Instructions.getPlayers(playerList));
        }
        data.writeInstruction(instructionWriter, Instructions.getListLength(length, playerList));

        var iterStart = data.writeLabel(instructionWriter);

        data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, data.contextEndLabel()));

        var playerObj = this.createLocal();
        var player = this.<TServerPlayer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListElement(playerObj, playerList, index));
        data.writeInstruction(instructionWriter, Instructions.assumePlayer(player, playerObj));

        var codeLocation = data.writeLabel(instructionWriter);

        data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
        data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

        return enterWithValue(data.withContextLabel(codeLocation), player);
    }

    public CompilationData<LocationData<TObjective>> getObjective(String objectiveName, ResultContext resultContext) {
        return getObjective(objectiveName, resultContext, false);
    }

    /** {@code writable}: ObjectiveArgument.getWritableObjective, a read-only criterion fails like a missing objective. */
    public CompilationData<LocationData<TObjective>> getObjective(String objectiveName, ResultContext resultContext, boolean writable) {
        var data = current();

        LocationData<TObjective> objective = createLocal();
        data.writeInstruction(instructionWriter, Instructions.getObjectiveRef(objective, objectiveName));
        // Use the LOCAL contextEndLabel, not resultContext.errorLabel: in an
        // `as @a` iteration body the local label is "continue to next player",
        // while resultContext.errorLabel is the function exit. Vanilla MC
        // treats a missing objective as predicate-false (skip this clause's
        // body), not as fatal — so we must not abort the whole function when
        // ONE iterated player happens to lack a score entry.
        // In an execute modifier (or a selector filter) a missing objective skips this source;
        // a terminal command whose result is observed fails instead (vanilla throws).
        var missingLabel = failureLabel(resultContext);
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(objective, missingLabel));
        if(writable) data.writeInstruction(instructionWriter, Instructions.ifObjectiveReadOnlyJump(objective, missingLabel));

        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), objective);
    }

    /** Where a failing argument (vanilla CommandSyntaxException) goes: the command's failure when its result is
     * observed, otherwise the end of the current source context. */
    public LabelData failureLabel(@Nullable ResultContext resultContext) {
        return resultContext != null && resultContext.generatingSuccess() ? resultContext.markAndGetErrorLabel() : current().contextEndLabel();
    }

    public CompilationData<ScoreAccessor> forScoreAccessors(ScoreAccessorArgument accessor, ResultContext resultContext) {
        return forScoreAccessors(accessor, resultContext, false);
    }

    /** {@code writable}: scoreboard players set/add/remove/operation (getWritableObjective). */
    public CompilationData<ScoreAccessor> forScoreAccessors(ScoreAccessorArgument accessor, ResultContext resultContext, boolean writable) {
        var objective = getObjective(accessor.objective(), resultContext, writable);
        var scoreHolder = forScoreHolder(accessor.scoreHolder(), resultContext);
        var scoreAccessor = new ScoreAccessor(objective.content(), accessor.objective(), scoreHolder.content(), constantScoreHolderName(accessor.scoreHolder()));
        return enterWithValue(scoreHolder.data(), scoreAccessor, objective.stackCount() + scoreHolder.stackCount() + 1);
    }

    /** The holder name when it is a compile-time constant (resolved through link-time fields), else null. */
    public static @Nullable String constantScoreHolderName(ScoreHolderArgument holder) {
        return holder.name() != null && holder.name().isStatic() ? holder.name().getStaticValue() : null;
    }

    public LocationData<String> getScoreboardName(LocationData<? extends TEntity> entity) {
        var data = current();
        LocationData<String> name = createLocal();
        data.writeInstruction(instructionWriter, Instructions.getScoreboardName(name, entity));
        return name;
    }

    /** The live score of the accessor, or null when it has none (never creates). */
    public LocationData<TScore> findScore(ScoreAccessor accessor) {
        LocationData<TScore> score = createLocal();
        if(accessor.holderName() != null) {
            writeInstruction(Instructions.findScoreRef(score, accessor.objective(), accessor.holderName(), accessor.objectiveName()));
        }else {
            writeInstruction(Instructions.findScore(score, accessor.objective(), accessor.scoreHolder()));
        }
        return score;
    }

    /** Reads an existing score; jumps to {@code failLabel} when there is none. */
    public CompilationData<LocationData<TScore>> getScoreData(ScoreAccessor accessor, LabelData failLabel) {
        var data = current();
        var score = findScore(accessor);
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(score, failLabel));
        var codeLocation = data.writeLabel(instructionWriter);
        return enterWithValue(data.withContextLabel(codeLocation), score);
    }

    /**
     * One vanilla {@code Scoreboard.getOrCreatePlayerScore} access: {@code score} is the live score (created when
     * missing), {@code found} the score before the access (null: this access created it, so its first
     * {@link #setScore} syncs the score even when the value is unchanged).
     */
    public record ScoreSlot(ScoreAccessor accessor, LocationData<TScore> found, LocationData<TScore> score) {}

    public ScoreSlot getOrCreateScore(ScoreAccessor accessor) {
        var found = findScore(accessor);
        LocationData<TScore> score = createLocal();
        writeInstruction(Instructions.ensureScore(score, accessor.objective(), accessor.scoreHolder(), found));
        return new ScoreSlot(accessor, found, score);
    }

    public LocationData<Integer> getScoreValue(LocationData<TScore> score) {
        LocationData<Integer> value = createLocal();
        writeInstruction(Instructions.getScoreValue(value, score));
        return value;
    }

    /** ScoreAccess.set through the slot's access; later sets on the same access no longer force a sync. */
    public void setScore(ScoreSlot slot, LocationData<Integer> value) {
        writeInstruction(Instructions.setScore(slot.accessor().objective(), slot.accessor().scoreHolder(), slot.score(), slot.found(), value));
        writeInstruction(Instructions.dup(slot.found(), slot.score()));
    }

    /** {@code *}: the tracked score holders (ScoreHolderArgument with getNamesWithDefaultWildcard). A single-holder
     * argument ({@code getName}) never resolves {@code *}, and an empty wildcard fails like NO_ENTITIES_FOUND. */
    public CompilationData<LocationData<? extends TScoreHolder>> forEachScoreHolder(boolean single, ResultContext resultContext) {
        var data = current();
        var emptyLabel = failureLabel(resultContext);
        if(single) {
            // Always fails at runtime (the holder is non-null); the body stays well-formed for the optimizer.
            var scoreHolder = this.<TScoreHolder>createLocal();
            data.writeInstruction(instructionWriter, Instructions.constScoreHolder(scoreHolder, "*"));
            data.writeInstruction(instructionWriter, Instructions.ifNotNullJump(scoreHolder, emptyLabel));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
        }
        var scoreHolders = this.<List<? extends String>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getScoreboardHolders(scoreHolders));
        var listLength = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListLength(listLength, scoreHolders));
        data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, listLength, LocationData.ofInteger(0), emptyLabel));
        var index = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
        var iterStart = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, listLength, data.contextEndLabel()));
        var scoreHolderObj = this.createLocal();
        var scoreHolderStr = this.<String>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListElement(scoreHolderObj, scoreHolders, index));
        data.writeInstruction(instructionWriter, Instructions.assumeString(scoreHolderStr, scoreHolderObj));
        var scoreHolder = this.<TScoreHolder>createLocal();
        data.writeInstruction(instructionWriter, Instructions.namedScoreHolder(scoreHolder, scoreHolderStr));
        var codeLocation = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
        data.writeInstruction(instructionWriter, Instructions.jump(iterStart));
        return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
    }

    public CompilationData<LocationData<? extends TScoreHolder>> forScoreHolder(ScoreHolderArgument holder, ResultContext resultContext) {
        if(holder.all()) {
            return forEachScoreHolder(holder.single(), resultContext);
        }else if(holder.name() != null) {
            var scoreHolder = this.<TScoreHolder>createLocal();
            var constantName = constantScoreHolderName(holder);
            if(constantName != null) {
                writeInstruction(Instructions.constScoreHolder(scoreHolder, constantName));
            }else {
                writeInstruction(Instructions.namedScoreHolder(scoreHolder, getResolvedStringMacro(holder.name())));
            }
            return new CompilationData<>(this, current(), scoreHolder, 0);
        }else if(holder.selector() != null) {
            var selector = holder.single() ? holder.selector().limit(1) : holder.selector();
            var selectorData = forSelector(selector, resultContext);
            var codeLocation = instructionWriter.createLabelBefore(selectorData.data().contextEndLabel());
            return enterWithValue(selectorData.data().withContextLabel(codeLocation), selectorData.content(), selectorData.stackCount() + 1);
        }
        throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNREACHABLE_CODE, source, "Unreachable code reached"));
    }

    /** Statically empty selections: players-only selectors that exclude players, or a predicate= that does not exist. */
    private static boolean selectsNothing(SelectorArgument selector) {
        if(selector.requirePlayer() && selector.excludesPlayers()) return true;
        for(var predicate : selector.predicates()) {
            if(DataRegistries.PREDICATE.getElement(predicate.data()).isEmpty()) return true;
        }
        return false;
    }

    /** EntitySelector.includesEntities() == false: @a/@p/@r, and @e/@n with type=player, gamemode=, level= or advancements=. */
    private static boolean playersOnly(SelectorArgument selector) {
        if(selector.requirePlayer()) return true;
        var types = selector.entityTypes();
        if(types != null && !types.negated() && types.data().size() == 1 && types.data().getFirst().equals(PLAYER_ENTITY_TYPE)) return true;
        return !selector.gamemodes().isEmpty() || selector.level() != null || selector.advancements() != null;
    }

    private static final DataLocation PLAYER_ENTITY_TYPE = new DataLocation("minecraft", "player");

    /** Whether candidates come from a scan (more than one possible), so an arbitrary-order limit can stop it early. */
    private static boolean scansCandidates(SelectorArgument selector) {
        return selector.playerName() == null && selector.entityUuid() == null && !selector.self();
    }

    public LocationData<List<? extends TEntity>> getSelectorList(SelectorArgument selector) {
        var data = current();

        LocationData<List<? extends TEntity>> entityList = createLocal();
        data.writeInstruction(instructionWriter, Instructions.createMutableList(entityList));

        if(selectsNothing(selector)) {
            return entityList;
        }

        try(var position = getPosition()) {

            LocationData<TVec3> realPosition;
            if(selector.x() != null || selector.y() != null || selector.z() != null) {
                realPosition = this.createLocal();
                position.writeInstruction(Instructions.mergePositions(realPosition, position.content(), LocationData.ofDoubleOrNull(selector.x()), LocationData.ofDoubleOrNull(selector.y()), LocationData.ofDoubleOrNull(selector.z())));
            }else {
                realPosition = position.content();
            }

            SelectorBox volumeBox = null;
            if(selector.dx() != null || selector.dy() != null || selector.dz() != null) {
                var compareX = this.<Double>createLocal();
                var compareY = this.<Double>createLocal();
                var compareZ = this.<Double>createLocal();
                position.writeInstruction(Instructions.getVec3X(compareX, realPosition));
                position.writeInstruction(Instructions.getVec3Y(compareY, realPosition));
                position.writeInstruction(Instructions.getVec3Z(compareZ, realPosition));
                var aabb = selector.createLocalAABB();
                var transformedMinX = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMinX, compareX, LocationData.ofDouble(aabb.minX())));
                var transformedMinY = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMinY, compareY, LocationData.ofDouble(aabb.minY())));
                var transformedMinZ = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMinZ, compareZ, LocationData.ofDouble(aabb.minZ())));
                var transformedMaxX = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMaxX, compareX, LocationData.ofDouble(aabb.maxX())));
                var transformedMaxY = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMaxY, compareY, LocationData.ofDouble(aabb.maxY())));
                var transformedMaxZ = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMaxZ, compareZ, LocationData.ofDouble(aabb.maxZ())));
                volumeBox = new SelectorBox(transformedMinX, transformedMinY, transformedMinZ, transformedMaxX, transformedMaxY, transformedMaxZ);
            }

            SelectorBox selectBox = volumeBox;
            if(selectBox == null && selector.distance() != null && selector.distance().maximum() != null) {
                var compareX = this.<Double>createLocal();
                var compareY = this.<Double>createLocal();
                var compareZ = this.<Double>createLocal();
                position.writeInstruction(Instructions.getVec3X(compareX, realPosition));
                position.writeInstruction(Instructions.getVec3Y(compareY, realPosition));
                position.writeInstruction(Instructions.getVec3Z(compareZ, realPosition));
                var transformedMinX = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueSub(transformedMinX, compareX, LocationData.ofDouble(selector.distance().maximum())));
                var transformedMinY = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueSub(transformedMinY, compareY, LocationData.ofDouble(selector.distance().maximum())));
                var transformedMinZ = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueSub(transformedMinZ, compareZ, LocationData.ofDouble(selector.distance().maximum())));
                var transformedMaxX = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMaxX, compareX, LocationData.ofDouble(selector.distance().maximum() + 1)));
                var transformedMaxY = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMaxY, compareY, LocationData.ofDouble(selector.distance().maximum() + 1)));
                var transformedMaxZ = this.<Double>createLocal();
                position.writeInstruction(Instructions.valueAdd(transformedMaxZ, compareZ, LocationData.ofDouble(selector.distance().maximum() + 1)));
                selectBox = new SelectorBox(transformedMinX, transformedMinY, transformedMinZ, transformedMaxX, transformedMaxY, transformedMaxZ);
            }

            var selectorCodeLocation = data.writeLabel(instructionWriter);
            var objectives = lookupObjectives(selector, selectorCodeLocation);

            try(var _ =enterWithoutValue(data.withContextLabel(selectorCodeLocation))) {
                if(selector.worldLimited()) {
                    try(var world = getWorld()) {
                        collectSelector(selector, entityList, realPosition, world.content(), volumeBox, selectBox, objectives, selectorCodeLocation);
                    }
                }else {
                    collectSelector(selector, entityList, realPosition, null, volumeBox, selectBox, objectives, selectorCodeLocation);
                }
            }

            // EntitySelector.sortAndLimit. Arbitrary order needs no truncation: collection stopped at the limit.
            int limit = selector.limit() != null ? selector.limit() : Integer.MAX_VALUE;
            switch(selector.sort()) {
                case NEAREST -> position.writeInstruction(Instructions.sortListEntityNearest(realPosition, entityList, limit));
                case FURTHEST -> position.writeInstruction(Instructions.sortListEntityFurthest(realPosition, entityList, limit));
                case RANDOM -> position.writeInstruction(Instructions.shuffleList(entityList, limit));
                case ARBITRARY -> {}
            }
        }
        return entityList;
    }

    private void collectSelector(SelectorArgument selector, LocationData<List<? extends TEntity>> entityList, LocationData<TVec3> position, @Nullable LocationData<TServerLevel> world, @Nullable SelectorBox volumeBox, @Nullable SelectorBox selectBox, @Nullable Map<String, LocationData<TObjective>> objectives, LabelData collectionEnd) {
        try(var selectorData = forSelectorSelect(selector, position, world, volumeBox, selectBox, objectives)) {
            selectorData.writeInstruction(Instructions.addListElement(entityList, selectorData.content()));
            // EntitySelector.addEntities: with arbitrary order the scan stops once `limit` entities matched.
            if(selector.sort() == SelectorSort.ARBITRARY && selector.limit() != null && scansCandidates(selector)) {
                if(selector.limit() == 1) {
                    selectorData.writeInstruction(Instructions.jump(collectionEnd));
                }else {
                    var size = this.<Integer>createLocal();
                    selectorData.writeInstruction(Instructions.getListLength(size, entityList));
                    selectorData.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, size, LocationData.ofInteger(selector.limit()), collectionEnd));
                }
            }
        }
    }

    /**
     * scores= objectives, looked up once before the scan (the scan cannot change objectives). A missing objective makes
     * the scores predicate false for every entity, so it skips the whole collection.
     */
    private @Nullable Map<String, LocationData<TObjective>> lookupObjectives(SelectorArgument selector, LabelData noMatch) {
        if(selector.scores().isEmpty()) return null;
        var objectives = new java.util.HashMap<String, LocationData<TObjective>>();
        for(var name : selector.scores().keySet()) {
            LocationData<TObjective> objective = createLocal();
            instructionWriter.addInstructionBefore(Instructions.getObjective(objective, LocationData.ofString(name)), noMatch);
            instructionWriter.addInstructionBefore(Instructions.ifNullJump(objective, noMatch), noMatch);
            objectives.put(name, objective);
        }
        return objectives;
    }

    public CompilationData<LocationData<? extends TEntity>> forSelector(SelectorArgument selector, @Nullable ResultContext resultContext) {
        boolean trackSuccess = resultContext != null && resultContext.generatingSuccess();
        if(selector.self() && !trackSuccess && !selectsNothing(selector) && selector.distance() == null
                && selector.x() == null && selector.y() == null && selector.z() == null
                && selector.dx() == null && selector.dy() == null && selector.dz() == null) {
            // @s: test the executing entity directly, no list (EntitySelector.findEntities currentEntity branch).
            return forSelectorBase(getCurrentEntity(), selector, null, null, null);
        }
        var data = current();
        var entityList = getSelectorList(selector);
        var length = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListLength(length, entityList));
        if(trackSuccess) data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), resultContext.markAndGetErrorLabel()));
        if(selector.limit() != null && selector.limit() == 1) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<TEntity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, LocationData.ofInteger(0)));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
        // The list already holds at most `limit` entities (getSelectorList applies it).
        var index = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
        var iterStart = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, data.contextEndLabel()));
        var entityObj = this.createLocal();
        var entity = this.<TEntity>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, index));
        data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
        var codeLocation = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
        data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

        return enterWithValue(data.withContextLabel(codeLocation), entity);
    }

    private record SelectorBox(LocationData<Double> minX, LocationData<Double> minY, LocationData<Double> minZ, LocationData<Double> maxX, LocationData<Double> maxY, LocationData<Double> maxZ) {}

    /**
     * Candidate source, mirroring EntitySelector.findEntities/findPlayers. Every scan reads one level's own structures
     * (or the player list), so no per-entity dimension check is needed; @s is never dimension-checked in vanilla.
     */
    @SuppressWarnings("resource")
    private CompilationData<LocationData<? extends TEntity>> forSelectorSelect(SelectorArgument selector, LocationData<TVec3> position, @Nullable LocationData<TServerLevel> world, @Nullable SelectorBox volumeBox, @Nullable SelectorBox selectBox, @Nullable Map<String, LocationData<TObjective>> objectives) {
        CompilationData<LocationData<? extends TEntity>> entityData;
        if(selector.playerName() != null) {
            entityData = forPlayer(selector.playerName()).changeType();
        }else if(selector.entityUuid() != null) {
            entityData = forEntity(selector.entityUuid());
        }else if(selector.self()) {
            entityData = getCurrentEntity();
        }else if(playersOnly(selector)) {
            entityData = forEachPlayer(world).changeType();
        }else if(selectBox != null) {
            entityData = forEachEntityInside(world, selectBox);
            if(selectBox.equals(volumeBox)) volumeBox = null;
        }else {
            var types = selector.entityTypes();
            if(types != null && !types.negated() && types.data().size() == 1) {
                entityData = forEachEntityOfType(world, types.data().getFirst());
            }else {
                entityData = forEachEntity(world);
            }
        }
        return forSelectorBase(entityData, selector, position, volumeBox, objectives);
    }

    /** Applies the selector's filters to one candidate; failing candidates jump to the scope end. {@code position} is only read for distance=. */
    private CompilationData<LocationData<? extends TEntity>> forSelectorBase(CompilationData<LocationData<? extends TEntity>> entityData, SelectorArgument selector, @Nullable LocationData<TVec3> position, @Nullable SelectorBox volumeBox, @Nullable Map<String, LocationData<TObjective>> objectives) {
        var data = entityData.data();
        int stackCount = entityData.stackCount();
        int stackStart = stack.size();
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(entityData.content(), data.contextEndLabel()));
        if(selector.entityTypes() != null) {
            var entityType = this.<TEntityType>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityType(entityType, entityData.content()));

            if(!selector.entityTypes().negated()) {
                var jump = data.writeLabel(instructionWriter);
                for(var compareType : selector.entityTypes().data()) {
                    var compareEntityType = this.<TEntityType>createLocal();
                    instructionWriter.addInstructionBefore(Instructions.mapEntityType(compareEntityType, compareType), jump);
                    instructionWriter.addInstructionBefore(Instructions.ifObjectCompareJump(ObjectComparisonType.IDENTITY_EQUAL, entityType, compareEntityType, jump), jump);
                }
                instructionWriter.addInstructionBefore(Instructions.jump(data.contextEndLabel()), jump);
            }else {
                for(var compareType : selector.entityTypes().data()) {
                    var compareEntityType = this.<TEntityType>createLocal();
                    data.writeInstruction(instructionWriter, Instructions.mapEntityType(compareEntityType, compareType));
                    data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.IDENTITY_EQUAL, entityType, compareEntityType, data.contextEndLabel()));
                }
            }
        }
        if(selector.requireAlive()) {
            var alive = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityAlive(alive, entityData.content()));
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, alive, LocationData.ofBoolean(false), data.contextEndLabel()));
        }
        for(var gamemode : selector.gamemodes()) {
            var playerLocal = this.<TServerPlayer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.tryAsPlayer(playerLocal, entityData.content(), data.contextEndLabel()));
            var playerGamemode = this.<TGameType>createLocal();
            var mappedGamemode = this.<TGameType>createLocal();
            data.writeInstruction(instructionWriter, Instructions.mapGamemode(mappedGamemode, gamemode.data()));
            data.writeInstruction(instructionWriter, Instructions.getGamemode(playerGamemode, playerLocal));
            if(gamemode.negated()) {
                data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.IDENTITY_EQUAL, playerGamemode, mappedGamemode, data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_IDENTITY_EQUAL, playerGamemode, mappedGamemode, data.contextEndLabel()));
            }
        }
        for(var name : selector.names()) {
            var entityName = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityName(entityName, entityData.content()));
            if(name.negated()) {
                data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.EQUAL, entityName, LocationData.ofString(name.data()), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_EQUAL, entityName, LocationData.ofString(name.data()), data.contextEndLabel()));
            }
        }
        for(var tag : selector.tags()) {
            var tags = this.<Set<? extends String>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityTags(tags, entityData.content()));
            var resolved = getResolvedStringMacro(tag.data());
            if(tag.negated()) {
                data.writeInstruction(instructionWriter, Instructions.jumpIfSetContains(tags, resolved, data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.jumpIfSetNotContains(tags, resolved, data.contextEndLabel()));
            }
        }
        if(selector.hasTags() != null) {
            var tags = this.<Set<? extends String>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityTags(tags, entityData.content()));
            var tagsLength = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getSetLength(tagsLength, tags));
            if(selector.hasTags()) {
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, tagsLength, LocationData.ofInteger(0), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, tagsLength, LocationData.ofInteger(0), data.contextEndLabel()));
            }
        }
        if(selector.level() != null) {
            var playerLocal = this.<TServerPlayer>createLocal();
            var levelLocal = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.tryAsPlayer(playerLocal, entityData.content(), data.contextEndLabel()));
            data.writeInstruction(instructionWriter, Instructions.getExperienceLevel(levelLocal, playerLocal));
            jumpIfNotMatchesRange(data, levelLocal, selector.level(), data.contextEndLabel());
        }
        if(selector.advancements() != null && !selector.advancements().isEmpty()) {
            var possible = true;
            out: for(var check : selector.advancements().entrySet()) {
                var advancementOption = DataRegistries.ADVANCEMENT.getElement(check.getKey());
                if(advancementOption.isEmpty()) {
                    possible = false;
                    break;
                }
                var advancement = advancementOption.get();
                if(check.getValue() instanceof SelectorAdvancementCheck.CriteriaCompleted(Map<String, Boolean> criteria)) {
                    for(var criterion : criteria.keySet()) {
                        if(!advancement.criteria().contains(criterion)) {
                            possible = false;
                            break out;
                        }
                    }
                }
            }
            if(!possible) {
                data.writeInstruction(instructionWriter, Instructions.jump(data.contextEndLabel()));
            }else {
                var playerLocal = this.<TServerPlayer>createLocal();
                data.writeInstruction(instructionWriter, Instructions.tryAsPlayer(playerLocal, entityData.content(), data.contextEndLabel()));
                for(var check : selector.advancements().entrySet()) {
                    var advId = check.getKey().toString();
                    if(check.getValue() instanceof SelectorAdvancementCheck.AdvancementCompleted(boolean done)) {
                        data.writeInstruction(instructionWriter, Instructions.checkAdvancementDone(playerLocal, advId, done, data.contextEndLabel()));
                    }else if(check.getValue() instanceof SelectorAdvancementCheck.CriteriaCompleted(Map<String, Boolean> criteria)) {
                        for(var criterion : criteria.entrySet()) {
                            data.writeInstruction(instructionWriter, Instructions.checkAdvancementCriterionDone(playerLocal, advId, criterion.getKey(), criterion.getValue(), data.contextEndLabel()));
                        }
                    }
                }
            }
        }
        // EntitySelectorParser.createRotationPredicate; the parser stored wrapped bounds with the 0/359 defaults.
        if(selector.pitch() != null) {
            data.writeInstruction(instructionWriter, Instructions.jumpIfXRotationNotInRange(entityData.content(), LocationData.ofFloat(Objects.requireNonNull(selector.pitch().minimum())), LocationData.ofFloat(Objects.requireNonNull(selector.pitch().maximum())), data.contextEndLabel()));
        }
        if(selector.yaw() != null) {
            data.writeInstruction(instructionWriter, Instructions.jumpIfYRotationNotInRange(entityData.content(), LocationData.ofFloat(Objects.requireNonNull(selector.yaw().minimum())), LocationData.ofFloat(Objects.requireNonNull(selector.yaw().maximum())), data.contextEndLabel()));
        }
        for(var team : selector.teams()) {
            var entityTeam = this.<TTeam>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityTeam(entityTeam, entityData.content()));
            if(team.negated()) {
                var postJump = data.writeLabel(instructionWriter);
                instructionWriter.addInstructionBefore(Instructions.ifNullJump(entityTeam, postJump), postJump);
                var entityTeamName = this.<String>createLocal();
                instructionWriter.addInstructionBefore(Instructions.getTeamName(entityTeamName, entityTeam), postJump);
                instructionWriter.addInstructionBefore(Instructions.ifObjectCompareJump(ObjectComparisonType.EQUAL, entityTeamName, LocationData.ofString(team.data()), data.contextEndLabel()), postJump);
            }else {
                data.writeInstruction(instructionWriter, Instructions.ifNullJump(entityTeam, data.contextEndLabel()));
                var entityTeamName = this.<String>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getTeamName(entityTeamName, entityTeam));
                data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_EQUAL, entityTeamName, LocationData.ofString(team.data()), data.contextEndLabel()));
            }
        }
        if(selector.hasTeam() != null) {
            var entityTeam = this.<TTeam>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityTeam(entityTeam, entityData.content()));
            if(selector.hasTeam()) {
                data.writeInstruction(instructionWriter, Instructions.ifNullJump(entityTeam, data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.ifNotNullJump(entityTeam, data.contextEndLabel()));
            }
        }
        if(selector.distance() != null) {
            var thisPosition = this.<TVec3>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityPosition(thisPosition, entityData.content()));
            var distance = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getDistanceSquared(distance, position, thisPosition));
            jumpIfNotMatchesRange(data, distance, selector.distance().square(), data.contextEndLabel());
        }
        if(volumeBox != null) {
            data.writeInstruction(instructionWriter, Instructions.checkEntityIntersection(entityData.content(), volumeBox.minX, volumeBox.minY, volumeBox.minZ, volumeBox.maxX, volumeBox.maxY, volumeBox.maxZ, data.contextEndLabel()));
        }
        if(!selector.scores().isEmpty()) {
            // EntitySelectorOptions scores: missing objective or missing score => no match; read-only lookup.
            for(var entry : selector.scores().entrySet()) {
                LocationData<TObjective> objective;
                if(objectives != null) {
                    objective = Objects.requireNonNull(objectives.get(entry.getKey()));
                }else {
                    objective = createLocal();
                    data.writeInstruction(instructionWriter, Instructions.getObjective(objective, LocationData.ofString(entry.getKey())));
                    data.writeInstruction(instructionWriter, Instructions.ifNullJump(objective, data.contextEndLabel()));
                }
                var scoreInfo = this.<TReadOnlyScoreInfo>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getSelectorScoreInfo(scoreInfo, objective, entityData.content()));
                data.writeInstruction(instructionWriter, Instructions.ifNullJump(scoreInfo, data.contextEndLabel()));
                var scoreValue = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getSelectorScoreInfoValue(scoreValue, scoreInfo));
                jumpIfNotMatchesRange(data, scoreValue, entry.getValue(), data.contextEndLabel());
            }
        }
        if(!selector.predicates().isEmpty()) {
            for(var predicate : selector.predicates()) {
                data.writeInstruction(instructionWriter, Instructions.checkEntityPredicate(entityData.content(), predicate.data().toString(), !predicate.negated(), data.contextEndLabel()));
            }
        }
        if(!selector.nbts().isEmpty()) {
            for(var nbt : selector.nbts()) {
                var nbtPattern = this.<TTag>createLocal();
                data.writeInstruction(instructionWriter, Instructions.loadNbtValue(nbtPattern, nbt.data()));
                data.writeInstruction(instructionWriter, Instructions.checkEntityNbt(entityData.content(), nbtPattern, !nbt.negated(), data.contextEndLabel()));
            }
        }
        stackCount += stack.size() - stackStart;
        return enterWithValue(data, entityData.content(), stackCount + 1);
    }

    /** Starts the fork for the execute chain beginning at {@code chain}; chains that can only yield a single
     * source compile their body inline (no fork list, no CommandSourceStack). */
    public CompilationData<@Nullable ForkingContext> withForkingContext(@Nullable ForkingContext forkingContext, CommandParams chain) {
        if(forkingContext == null && ExecuteChains.isSingleSource(chain)) {
            return new CompilationData<>(this, current(), null, 0);
        }
        return withForkingContext(forkingContext);
    }

    public CompilationData<@Nullable ForkingContext> withForkingContext(@Nullable ForkingContext forkingContext) {
        if(forkingContext != null) {
            return new CompilationData<>(this, current(), forkingContext, 0);
        }
        var data = current();
        var forkContexts = this.<List<?>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.createMutableList(forkContexts));
        var inForkCodeLocation = data.writeLabel(instructionWriter);
        var endForkLabel = data.writeLabel(instructionWriter);
        var newForkingContext = new ForkingContext(endForkLabel, forkContexts);
        return enterWithValue(data.withContextLabel(inForkCodeLocation), newForkingContext);
    }

    public CompilationData<Object> generateForkExecCode(@Nullable ForkingContext forkingContext) {
        if(forkingContext == null) {
            return new CompilationData<>(this, current(), null, 0);
        }
        var data = current();
        var commandStack = this.<TCommandSourceStack>createLocal();
        writeInstruction(Instructions.makeCommandSourceStack(commandStack, data.commandSourceStack(), data.currentEntity(), data.currentWorld(), data.currentPosition(), data.currentRotation(), data.currentAnchor()));
        writeInstruction(Instructions.addListElement(forkingContext.getForkContextList(), commandStack));

        var length = this.<Integer>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getListLength(length, forkingContext.getForkContextList()), forkingContext.getForkExecLabel());
        var index = this.<Integer>createLocal();
        instructionWriter.addInstructionBefore(Instructions.dup(index, LocationData.ofInteger(0)), forkingContext.getForkExecLabel());
        var iterStart = instructionWriter.createLabelBefore(forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, forkingContext.getForkExecLabel()), forkingContext.getForkExecLabel());
        var commandStackObj = this.createLocal();
        var cmdStack = this.<TCommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getListElement(commandStackObj, forkingContext.getForkContextList(), index), forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.assumeCommandStack(cmdStack, commandStackObj), forkingContext.getForkExecLabel());
        var codeLocation = instructionWriter.createLabelBefore(forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.valueAdd(index, index, LocationData.ofInteger(1)), forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.jump(iterStart), forkingContext.getForkExecLabel());

        return enterWithoutValue(CompilationContextData.create(cmdStack, codeLocation));
    }


    public void jumpIfMatchesRange(CompilationContextData data, LocationData<Integer> value, IntRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            if(range.minimum() == null) throw new IllegalArgumentException("range does not have minimum nor maximum");
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, value, LocationData.ofInteger(range.minimum()), label));
        }else if(range.minimum() != null && range.maximum() == null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, value, LocationData.ofInteger(range.minimum()), label));
        }else if(range.minimum() == null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.LESS_THAN_OR_EQUAL, value, LocationData.ofInteger(range.maximum()), label));
        }else {
            var failure = data.writeLabel(instructionWriter);
            instructionWriter.addInstructionBefore(Instructions.ifNumberCompareJump(NumberComparisonType.LESS_THAN, value, LocationData.ofInteger(range.minimum()), failure), failure);
            instructionWriter.addInstructionBefore(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN, value, LocationData.ofInteger(range.maximum()), failure), failure);
            instructionWriter.addInstructionBefore(Instructions.jump(label), failure);
        }
    }

    public void jumpIfNotMatchesRange(CompilationContextData data, LocationData<Integer> value, IntRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            if(range.minimum() == null) throw new IllegalArgumentException("range does not have minimum nor maximum");
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, value, LocationData.ofInteger(range.minimum()), label));
            return;
        }
        if(range.minimum() != null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.LESS_THAN, value, LocationData.ofInteger(range.minimum()), label));
        }
        if(range.maximum() != null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN, value, LocationData.ofInteger(range.maximum()), label));
        }
    }

    public void jumpIfNotMatchesRange(CompilationContextData data, LocationData<Double> value, DoubleRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            if(range.minimum() == null) throw new IllegalArgumentException("range does not have minimum nor maximum");
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, value, LocationData.ofDouble(range.minimum()), label));
            return;
        }
        if(range.minimum() != null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.LESS_THAN, value, LocationData.ofDouble(range.minimum()), label));
        }
        if(range.maximum() != null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN, value, LocationData.ofDouble(range.maximum()), label));
        }
    }

}
