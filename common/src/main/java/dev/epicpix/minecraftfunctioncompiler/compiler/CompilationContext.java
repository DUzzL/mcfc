package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.data.DoubleRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
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
import dev.epicpix.minecraftfunctioncompiler.il.locations.TGameType;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TObjective;
import dev.epicpix.minecraftfunctioncompiler.il.locations.TScoreAccess;
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNEXPECTED_CONTEXT_END;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNREACHABLE_CODE;

public class CompilationContext {

    private final DataLocation source;
    private final Stack<CompilationContextData> stack = new Stack<>();
    private final UniqueIdTracker idTracker = new UniqueIdTracker();
    public final InstructionWriter instructionWriter = new InstructionWriter();

    public CompilationContext(DataLocation source) {
        this.source = source;
        var commandStack = this.<TCommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getCommandStack(commandStack), instructionWriter.getEndLabel());
        enterWith(CompilationContextData.create(commandStack, instructionWriter.getEndLabel()));
    }

    public DataLocation getSource() {
        return source;
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

    public CompilationData<@Nullable LocationData<? extends TEntity>> getCurrentNullableEntity() {
        var data = current();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<TEntity> entity = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackEntity(entity, data.commandSourceStack()));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation).withCurrentEntity(entity), entity);
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

    private CompilationData<LocationData<? extends TEntity>> forEachEntityInside(@Nullable LocationData<TServerLevel> inWorld, SelectorBox box, int limit) {
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
            data.writeInstruction(instructionWriter, Instructions.getEntitiesInside(entities, inWorld, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, LocationData.ofInteger(limit)));
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
            data.writeInstruction(instructionWriter, Instructions.getEntitiesInside(entities, inWorld, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, LocationData.ofInteger(limit)));
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

    public CompilationData<LocationData<TServerPlayer>> forEachPlayer() {
        var data = current();

        var index = this.<Integer>createLocal();
        var length = this.<Integer>createLocal();
        var playerList = this.<List<? extends TServerPlayer>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
        data.writeInstruction(instructionWriter, Instructions.getPlayers(playerList));
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
        var data = current();

        LocationData<TObjective> objective = createLocal();
        data.writeInstruction(instructionWriter, Instructions.getObjective(objective, LocationData.ofString(objectiveName)));
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(objective, resultContext.markAndGetErrorLabel()));

        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), objective);
    }

    public CompilationData<ScoreAccessor> forScoreAccessors(ScoreAccessorArgument accessor, ResultContext resultContext) {
        var objectiveName = getObjective(accessor.objective(), resultContext);
        var scoreHolder = forScoreHolder(accessor.scoreHolder(), resultContext);
        return enterWithValue(scoreHolder.data(), new ScoreAccessor(objectiveName.content(), scoreHolder.content()), objectiveName.stackCount() + scoreHolder.stackCount() + 1);
    }

    public LocationData<String> getScoreboardName(LocationData<? extends TEntity> entity) {
        var data = current();
        LocationData<String> name = createLocal();
        data.writeInstruction(instructionWriter, Instructions.getScoreboardName(name, entity));
        return name;
    }

    public CompilationData<LocationData<TScoreAccess>> getScoreDataAutoCreate(ScoreAccessor accessor) {
        return getScoreData(accessor, null, true);
    }

    public CompilationData<LocationData<TScoreAccess>> getScoreData(ScoreAccessor accessor, @Nullable LabelData failLabel, boolean autoCreate) {
        var data = current();
        LocationData<TScoreAccess> score = createLocal();
        if(autoCreate) {
            data.writeInstruction(instructionWriter, Instructions.scoreDataAutoCreate(score, accessor.objective(), accessor.scoreHolder()));
            return new CompilationData<>(this, data, score, 0);
        }else {
            data.writeInstruction(instructionWriter, Instructions.scoreDataNoAutoCreate(score, accessor.objective(), accessor.scoreHolder()));
            data.writeInstruction(instructionWriter, Instructions.ifNullJump(score, failLabel));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), score);
        }
    }

    public CompilationData<LocationData<? extends TScoreHolder>> forEachScoreHolder(boolean single, ResultContext resultContext) {
        var data = current();
        var scoreHolders = this.<List<? extends String>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getScoreboardHolders(scoreHolders));
        var listLength = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListLength(listLength, scoreHolders));
        data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, listLength, LocationData.ofInteger(0), resultContext.markAndGetErrorLabel()));
        if(single) {
            var scoreHolderObj = this.createLocal();
            var scoreHolderStr = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(scoreHolderObj, scoreHolders, LocationData.ofInteger(0)));
            data.writeInstruction(instructionWriter, Instructions.assumeString(scoreHolderStr, scoreHolderObj));
            var scoreHolder = this.<TScoreHolder>createLocal();
            data.writeInstruction(instructionWriter, Instructions.namedScoreHolder(scoreHolder, scoreHolderStr));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
        }else {
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
    }

    public CompilationData<LocationData<? extends TScoreHolder>> forScoreHolder(ScoreHolderArgument holder, ResultContext resultContext) {
        if(holder.all()) {
            return forEachScoreHolder(holder.single(), resultContext);
        }else if(holder.name() != null) {
            var macro = getResolvedStringMacro(holder.name());
            var scoreHolder = this.<TScoreHolder>createLocal();
            writeInstruction(Instructions.namedScoreHolder(scoreHolder, macro));
            return new CompilationData<>(this, current(), scoreHolder, 0);
        }else if(holder.selector() != null) {
            var selector = holder.single() ? holder.selector().limit(1) : holder.selector();
            var selectorData = forSelector(selector, resultContext);
            var codeLocation = instructionWriter.createLabelBefore(selectorData.data().contextEndLabel());
            return enterWithValue(selectorData.data().withContextLabel(codeLocation), selectorData.content(), selectorData.stackCount() + 1);
        }
        throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNREACHABLE_CODE, source, "Unreachable code reached"));
    }

    public LocationData<List<? extends TEntity>> getSelectorList(SelectorArgument selector, @Nullable ResultContext resultContext) {
        var data = current();

        LocationData<List<? extends TEntity>> entityList = createLocal();
        data.writeInstruction(instructionWriter, Instructions.createMutableList(entityList));

        if(selector.requirePlayer() && selector.excludesPlayers()) {
            return entityList;
        }
        for(var predicate : selector.predicates()) {
            var predicateData = DataRegistries.PREDICATE.getElement(predicate.data());
            if(predicateData.isEmpty()) {
                return entityList;
            }
        }

        try(var position = getPosition()) {
            if(resultContext == null) {
                //noinspection resource
                resultContext = ResultContext.create(this, position.endLabel());
            }

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

            try(var ignored = enterWithoutValue(data.withContextLabel(selectorCodeLocation))) {
                if(selector.worldLimited()) {
                    try(var world = getWorld()) {
                        try(var selectorData = forSelectorSelect(selector, realPosition, world.content(), volumeBox, selectBox, resultContext)) {
                            selectorData.writeInstruction(Instructions.addListElement(entityList, selectorData.content()));
                        }
                    }
                }else {
                    try(var selectorData = forSelectorSelect(selector, realPosition, null, volumeBox, selectBox, resultContext)) {
                        selectorData.writeInstruction(Instructions.addListElement(entityList, selectorData.content()));
                    }
                }
            }

            if(selector.sort() == SelectorSort.NEAREST || selector.sort() == SelectorSort.FURTHEST) {
                switch(selector.sort()) {
                    case FURTHEST -> position.writeInstruction(Instructions.sortListEntityFurthest(realPosition, entityList));
                    case NEAREST -> position.writeInstruction(Instructions.sortListEntityNearest(realPosition, entityList));
                }
            }else if(selector.sort() == SelectorSort.RANDOM) {
                data.writeInstruction(instructionWriter, Instructions.shuffleList(entityList));
            }
        }
        return entityList;
    }

    public CompilationData<LocationData<? extends TEntity>> forSelector(SelectorArgument selector, ResultContext resultContext) {
        var data = current();
        var entityList = getSelectorList(selector, resultContext);
        var length = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListLength(length, entityList));
        if(resultContext.generatingSuccess()) data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), resultContext.markAndGetErrorLabel()));
        if(selector.limit() != null) {
            if(selector.limit() == 1) {
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), data.contextEndLabel()));
                var entityObj = this.createLocal();
                var entity = this.<TEntity>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, LocationData.ofInteger(0)));
                data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
                var codeLocation = data.writeLabel(instructionWriter);
                return enterWithValue(data.withContextLabel(codeLocation), entity);
            }else {
                var index = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions.dup(index, LocationData.ofInteger(0)));
                var iterStart = data.writeLabel(instructionWriter);
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, data.contextEndLabel()));
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, LocationData.ofInteger(selector.limit()), data.contextEndLabel()));
                var entityObj = this.createLocal();
                var entity = this.<TEntity>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, index));
                data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
                var codeLocation = data.writeLabel(instructionWriter);
                data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
                data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

                return enterWithValue(data.withContextLabel(codeLocation), entity);
            }
        }else {
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
    }

    private record SelectorBox(LocationData<Double> minX, LocationData<Double> minY, LocationData<Double> minZ, LocationData<Double> maxX, LocationData<Double> maxY, LocationData<Double> maxZ) {}

    @SuppressWarnings("resource")
    private CompilationData<LocationData<? extends TEntity>> forSelectorSelect(SelectorArgument selector, LocationData<TVec3> position, @Nullable LocationData<TServerLevel> world, @Nullable SelectorBox volumeBox, @Nullable SelectorBox selectBox, ResultContext resultContext) {
        CompilationData<LocationData<? extends TEntity>> entityData;
        if(selector.playerName() != null) {
            entityData = forPlayer(selector.playerName()).changeType();
        }else if(selector.entityUuid() != null) {
            entityData = forEntity(selector.entityUuid());
        }else if(selector.self()) {
            entityData = getCurrentEntity();
        }else if(selector.requirePlayer()) {
            entityData = forEachPlayer().changeType();
        }else {
            if(selectBox != null) {
                int limit = Integer.MAX_VALUE;
                if(selector.sort() == SelectorSort.ARBITRARY && selector.distance() == null && selector.limit() != null) limit = selector.limit();
                entityData = forEachEntityInside(world, selectBox, limit);
                if(selectBox.equals(volumeBox)) volumeBox = null;
            }else {
                entityData = forEachEntity(world);
            }
        }
        return forSelectorBase(entityData, selector, position, world, volumeBox, resultContext);
    }

    private CompilationData<LocationData<? extends TEntity>> forSelectorBase(CompilationData<LocationData<? extends TEntity>> entityData, SelectorArgument selector, LocationData<TVec3> position, @Nullable LocationData<TServerLevel> world, @Nullable SelectorBox volumeBox, ResultContext resultContext) {
        var data = entityData.data();
        int stackCount = entityData.stackCount();
        int stackStart = stack.size();
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(entityData.content(), data.contextEndLabel()));
        if(world != null) {
            LocationData<TServerLevel> thisWorld = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityWorld(thisWorld, entityData.content()));
            data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_IDENTITY_EQUAL, thisWorld, world, data.contextEndLabel()));
        }
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
                throw new RuntimeException("advancements are not supported");
            }
        }
        if(selector.yaw() != null || selector.pitch() != null) {
            var rotationLocal = this.<TVec2>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityRotation(rotationLocal, entityData.content()));
            if(selector.yaw() != null) {
                var yaw = this.<Float>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getRotationYaw(yaw, rotationLocal));
                jumpIfNotMatchesRange(data, yaw, selector.yaw(), data.contextEndLabel());
            }
            if(selector.pitch() != null) {
                var pitch = this.<Float>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getRotationPitch(pitch, rotationLocal));
                jumpIfNotMatchesRange(data, pitch, selector.pitch(), data.contextEndLabel());
            }
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
            for(var entry : selector.scores().entrySet()) {
                var objectiveData = getObjective(entry.getKey(), resultContext);
                var scoreData = getScoreData(new ScoreAccessor(objectiveData.content(), entityData.content()), data.contextEndLabel(), false);
                data = current();

                var scoreValue = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getScoreValue(scoreValue, scoreData.content()));
                jumpIfNotMatchesRange(data, scoreValue, entry.getValue(), data.contextEndLabel());
            }
        }
        if(!selector.predicates().isEmpty()) {
            throw new RuntimeException("predicates are not implemented");
        }
        if(!selector.nbts().isEmpty()) {
            throw new RuntimeException("entity nbt not implemented");
        }
        stackCount += stack.size() - stackStart;
        return enterWithValue(data, entityData.content(), stackCount + 1);
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

    public void jumpIfNotMatchesRange(CompilationContextData data, LocationData<Float> value, FloatRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            if(range.minimum() == null) throw new IllegalArgumentException("range does not have minimum nor maximum");
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.NOT_EQUAL, value, LocationData.ofFloat(range.minimum()), label));
            return;
        }
        if(range.minimum() != null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.LESS_THAN, value, LocationData.ofFloat(range.minimum()), label));
        }
        if(range.maximum() != null) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN, value, LocationData.ofFloat(range.maximum()), label));
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
