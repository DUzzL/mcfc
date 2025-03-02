package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.ObjectComparisonType;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNEXPECTED_CONTEXT_END;
import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_UNREACHABLE_CODE;

public class CompilationContext {

    private final DataLocation source;
    private final Stack<CompilationContextData> stack = new Stack<>();
    private final UniqueIdTracker idTracker = new UniqueIdTracker();
    public final InstructionWriter instructionWriter = new InstructionWriter();

    public CompilationContext(DataLocation source) {
        this.source = source;
        var commandStack = this.<CommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getCommandStack(commandStack), instructionWriter.getEndLabel());
        enterWith(CompilationContextData.create(commandStack, instructionWriter.getEndLabel()));
    }

    public DataLocation getSource() {
        return source;
    }

    public <T> CompilationData<T> enterWithValue(CompilationContextData data, T value, int stackCount) {
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

    public ResultStorage withResultStorage(boolean requireResultValue, boolean requireSuccessValue) {
        LocationData<Integer> rValue = null;
        if(requireResultValue) {
            rValue = this.createLocal();
            writeInstruction(Instructions.valueAssign(rValue, LocationData.ofInteger(0)));
        }
        LocationData<Integer> sValue = null;
        if(requireSuccessValue) {
            sValue = this.createLocal();
            writeInstruction(Instructions.valueAssign(sValue, LocationData.ofInteger(0)));
        }
        return new ResultStorage(source, rValue, sValue);
    }

    public CompilationData<LocationData<? extends Entity>> getCurrentEntity() {
        var data = current();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<Entity> entity = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackEntity(entity, data.commandSourceStack()));
            data.writeInstruction(instructionWriter, Instructions.ifNullJump(entity, data.contextEndLabel()));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation).withCurrentEntity(entity), entity);
        }
    }

    public CompilationData<LocationData<? extends Entity>> getCurrentNullableEntity() {
        var data = current();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<Entity> entity = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackEntity(entity, data.commandSourceStack()));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation).withCurrentEntity(entity), entity);
        }
    }

    public CompilationData<LocationData<Vec3>> getPosition() {
        var data = current();
        if(data.currentPosition() != null) {
            return new CompilationData<>(this, data, data.currentPosition(), 0);
        }else {
            LocationData<Vec3> position = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackPosition(position, data.commandSourceStack()));
            return enterWithValue(data.withCurrentPosition(position), position);
        }
    }

    public CompilationData<LocationData<Vec2>> getRotation() {
        var data = current();
        if(data.currentRotation() != null) {
            return new CompilationData<>(this, data, data.currentRotation(), 0);
        }else {
            LocationData<Vec2> rotation = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackRotation(rotation, data.commandSourceStack()));
            return enterWithValue(data.withCurrentRotation(rotation), rotation);
        }
    }

    public CompilationData<LocationData<World>> getWorld() {
        var data = current();
        if(data.currentWorld() != null) {
            return new CompilationData<>(this, data, data.currentWorld(), 0);
        }else {
            LocationData<World> world = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getStackWorld(world, data.commandSourceStack()));
            return enterWithValue(data.withCurrentWorld(world), world);
        }
    }

    public CompilationData<LocationData<Vec3>> applyPosition(WorldCoordinates pos) {
        if(pos.localCoordinates()) {
            var entityData = getCurrentNullableEntity();
            var positionData = getPosition();
            var rotationData = getRotation();

            var offset = this.<Double>createLocal();
            rotationData.writeInstruction(Instructions.valueAssign(offset, LocationData.ofDouble(0.0)));
            var position = this.<Vec3>createLocal();
            if(rotationData.data().currentAnchor() == LocationAnchor.EYES) {
                var skipCode = instructionWriter.createLabelBefore(rotationData.endLabel());
                var reassignedOffset = this.<Double>createLocal();
                instructionWriter.addInstructionBefore(Instructions.ifNullJump(entityData.content(), skipCode), skipCode);
                instructionWriter.addInstructionBefore(Instructions.getEyeHeight(reassignedOffset, entityData.content()), skipCode);
                instructionWriter.addInstructionBefore(Instructions.valueAssign(offset, reassignedOffset), skipCode);
            }else if(rotationData.data().currentAnchor() == null) {
                var skipCode = instructionWriter.createLabelBefore(rotationData.endLabel());
                var reassignedOffset = this.<Double>createLocal();
                instructionWriter.addInstructionBefore(Instructions.ifNullJump(entityData.content(), skipCode), skipCode);
                instructionWriter.addInstructionBefore(Instructions.getAnchorOffset(reassignedOffset, rotationData.data().commandSourceStack(), entityData.content()), skipCode);
                instructionWriter.addInstructionBefore(Instructions.valueAssign(offset, reassignedOffset), skipCode);
            }
            rotationData.writeInstruction(Instructions.applyLocalPositionWorld(position, positionData.content(), rotationData.content(), offset, pos));
            return enterWithValue(rotationData.data(), position, entityData.stackCount() + positionData.stackCount() + rotationData.stackCount() + 1);
        }
        var positionData = getPosition();
        var position = this.<Vec3>createLocal();
        positionData.writeInstruction(Instructions.applyPositionWorld(position, positionData.content(), pos));
        return enterWithValue(positionData.data(), position, positionData.stackCount() + 1);
    }

    public CompilationData<LocationData<Vec2>> applyRotation(RotationCoordinates rot) {
        var rotationData = getRotation();
        var rotation = this.<Vec2>createLocal();
        rotationData.writeInstruction(Instructions.applyRotationWorld(rotation, rotationData.content(), rot));
        return enterWithValue(rotationData.data(), rotation, rotationData.stackCount() + 1);
    }

    public CompilationData<LocationData<? extends Entity>> forEntity(UUID entityUuid) {
        var data = current();
        var worldIter = this.<Iterator<World>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getWorldsIterator(worldIter));
        var iterStart = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(worldIter, data.contextEndLabel()));
        var worldObj = this.createLocal();
        var world = this.<World>createLocal();
        data.writeInstruction(instructionWriter, Instructions.iteratorNext(worldObj, worldIter));
        data.writeInstruction(instructionWriter, Instructions.assumeWorld(world, worldObj));

        var entity = this.<Entity>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getEntity(entity, world, entityUuid));
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(entity, iterStart));

        var codeLocation = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions.jump(data.contextEndLabel()));

        return enterWithValue(data.withContextLabel(codeLocation), entity);
    }

    public CompilationData<LocationData<? extends Entity>> forEachEntity(LocationData<World> inWorld) {
        var data = current();
        if(inWorld == null) {
            var worldIter = this.<Iterator<World>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getWorldsIterator(worldIter));
            var iterWorldStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(worldIter, data.contextEndLabel()));
            var worldObj = this.createLocal();
            var world = this.<World>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(worldObj, worldIter));
            data.writeInstruction(instructionWriter, Instructions.assumeWorld(world, worldObj));

            var entityIter = this.<Iterator<Entity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntities(entityIter, world));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(entityIter, iterWorldStart));
            var entityObj = this.createLocal();
            var entity = this.<Entity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(entityObj, entityIter));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }else {
            var entityIter = this.<Iterator<Entity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntities(entityIter, inWorld));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jumpIfIteratorHasNoNext(entityIter, data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<Entity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.iteratorNext(entityObj, entityIter));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    public CompilationData<LocationData<Player>> forPlayer(String playerName) {
        var data = current();

        var player = this.<Player>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getPlayer(player, playerName));
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(player, data.contextEndLabel()));
        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), player);
    }

    public CompilationData<LocationData<Player>> forEachPlayer() {
        var data = current();

        var index = this.<Integer>createLocal();
        var length = this.<Integer>createLocal();
        var playerList = this.<List<Player>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.valueAssign(index, LocationData.ofInteger(0)));
        data.writeInstruction(instructionWriter, Instructions.getPlayers(playerList));
        data.writeInstruction(instructionWriter, Instructions.getListLength(length, playerList));

        var iterStart = data.writeLabel(instructionWriter);

        data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, data.contextEndLabel()));

        var playerObj = this.createLocal();
        var player = this.<Player>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListElement(playerObj, playerList, index));
        data.writeInstruction(instructionWriter, Instructions.assumePlayer(player, playerObj));

        var codeLocation = data.writeLabel(instructionWriter);

        data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
        data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

        return enterWithValue(data.withContextLabel(codeLocation), player);
    }

    public CompilationData<LocationData<Objective>> getObjective(String objectiveName) {
        var data = current();

        LocationData<Objective> objective = createLocal();
        data.writeInstruction(instructionWriter, Instructions.getObjective(objective, objectiveName));
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(objective, data.contextEndLabel()));

        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), objective);
    }

    @SuppressWarnings("resource")
    public CompilationData<ScoreAccessor> forScoreAccessors(ScoreAccessorArgument accessor) {
        var objectiveName = getObjective(accessor.objective());
        var scoreHolder = forScoreHolder(accessor.scoreHolder());
        return enterWithValue(scoreHolder.data(), new ScoreAccessor(objectiveName.content(), scoreHolder.content()), objectiveName.stackCount() + scoreHolder.stackCount() + 1);
    }

    public LocationData<String> getScoreboardName(LocationData<? extends Entity> entity) {
        var data = current();
        LocationData<String> name = createLocal();
        data.writeInstruction(instructionWriter, Instructions.getScoreboardName(name, entity));
        return name;
    }

    public CompilationData<LocationData<Score>> getScoreData(ScoreAccessor accessor, boolean autoCreate) {
        var data = current();
        LocationData<Score> score = createLocal();
        if(autoCreate) {
            data.writeInstruction(instructionWriter, Instructions.scoreDataAutoCreate(score, accessor.objective(), accessor.scoreHolder()));
            return new CompilationData<>(this, data, score, 0);
        }else {
            data.writeInstruction(instructionWriter, Instructions.scoreDataNoAutoCreate(score, accessor.objective(), accessor.scoreHolder()));
            data.writeInstruction(instructionWriter, Instructions.ifNullJump(score, data.contextEndLabel()));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), score);
        }
    }

    public CompilationData<LocationData<String>> forEachScoreHolder(boolean single) {
        var data = current();
        var scoreHolders = this.<List<String>>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getScoreboardHolders(scoreHolders));
        var listLength = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListLength(listLength, scoreHolders));
        if(single) {
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, listLength, LocationData.ofInteger(0), data.contextEndLabel()));
            var scoreHolderObj = this.createLocal();
            var scoreHolder = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(scoreHolderObj, scoreHolders, LocationData.ofInteger(0)));
            data.writeInstruction(instructionWriter, Instructions.assumeString(scoreHolder, scoreHolderObj));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
        }else {
            var index = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAssign(index, LocationData.ofInteger(0)));
            var iterStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, listLength, data.contextEndLabel()));
            var scoreHolderObj = this.createLocal();
            var scoreHolder = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(scoreHolderObj, scoreHolders, index));
            data.writeInstruction(instructionWriter, Instructions.assumeString(scoreHolder, scoreHolderObj));
            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
            data.writeInstruction(instructionWriter, Instructions.jump(iterStart));
            return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
        }
    }

    public CompilationData<LocationData<String>> forScoreHolder(ScoreHolderArgument holder) {
        if(holder.all()) {
            return forEachScoreHolder(holder.single());
        }else if(holder.name() != null) {
            var macro = getResolvedStringMacro(holder.name());
            return new CompilationData<>(this, current(), macro, 0);
        }else if(holder.selector() != null) {
            var selector = holder.single() ? holder.selector().limit(1) : holder.selector();
            @SuppressWarnings("resource")
            var selectorData = forSelector(selector);
            var name = this.<String>createLocal();
            selectorData.writeInstruction(Instructions.getScoreboardName(name, selectorData.content()));
            var codeLocation = instructionWriter.createLabelBefore(selectorData.data().contextEndLabel());
            return enterWithValue(selectorData.data().withContextLabel(codeLocation), name, selectorData.stackCount() + 1);
        }
        throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNREACHABLE_CODE, source, "Unreachable code reached"));
    }

    public CompilationData<LocationData<? extends Entity>> forSelector(SelectorArgument selector) {
        var data = current();
        if(selector.requirePlayer() && selector.excludesPlayers()) {
            return new CompilationData<>(this, data.withContextLabel(InstructionWriter.NULL_LABEL), createLocal(), 0);
        }
        for(var predicate : selector.predicates()) {
            var predicateData = DataRegistries.PREDICATE.get(predicate.data());
            if(predicateData == null) {
                return new CompilationData<>(this, data.withContextLabel(InstructionWriter.NULL_LABEL), createLocal(), 0);
            }
        }

        LocationData<List<Entity>> entityList = createLocal();

        try(var position = getPosition()) {
            LocationData<Vec3> realPosition;
            if(selector.x() != null || selector.y() != null || selector.z() != null) {
                realPosition = this.createLocal();
                position.writeInstruction(Instructions.mergePositions(realPosition, position.content(), LocationData.ofDoubleOrNull(selector.x()), LocationData.ofDoubleOrNull(selector.y()), LocationData.ofDoubleOrNull(selector.z())));
            }else {
                realPosition = position.content();
            }

            data.writeInstruction(instructionWriter, Instructions.createMutableList(entityList));

            var selectorCodeLocation = data.writeLabel(instructionWriter);

            try(var ignored = enterWithoutValue(data.withContextLabel(selectorCodeLocation))) {
                if(selector.worldLimited()) {
                    try(var world = getWorld()) {
                        try(var selectorData = forSelectorSelect(selector, realPosition, world.content())) {
                            selectorData.writeInstruction(Instructions.addListElement(entityList, selectorData.content()));
                        }
                    }
                }else {
                    try(var selectorData = forSelectorSelect(selector, realPosition, null)) {
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

        var length = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions.getListLength(length, entityList));
        if(selector.limit() != null) {
            if(selector.limit() == 1) {
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, length, LocationData.ofInteger(0), data.contextEndLabel()));
                var entityObj = this.createLocal();
                var entity = this.<Entity>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, LocationData.ofInteger(0)));
                data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
                var codeLocation = data.writeLabel(instructionWriter);
                return enterWithValue(data.withContextLabel(codeLocation), entity);
            }else {
                var index = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions.valueAssign(index, LocationData.ofInteger(0)));
                var iterStart = data.writeLabel(instructionWriter);
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, data.contextEndLabel()));
                data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, LocationData.ofInteger(selector.limit()), data.contextEndLabel()));
                var entityObj = this.createLocal();
                var entity = this.<Entity>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, index));
                data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
                var codeLocation = data.writeLabel(instructionWriter);
                data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
                data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

                return enterWithValue(data.withContextLabel(codeLocation), entity);
            }
        }else {
            var index = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAssign(index, LocationData.ofInteger(0)));
            var iterStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<Entity>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getListElement(entityObj, entityList, index));
            data.writeInstruction(instructionWriter, Instructions.assumeEntity(entity, entityObj));
            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions.valueAdd(index, index, LocationData.ofInteger(1)));
            data.writeInstruction(instructionWriter, Instructions.jump(iterStart));

            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    @SuppressWarnings("resource")
    private CompilationData<LocationData<? extends Entity>> forSelectorSelect(SelectorArgument selector, LocationData<Vec3> position, LocationData<World> world) {
        CompilationData<LocationData<? extends Entity>> entityData;
        if(selector.playerName() != null) {
            entityData = forPlayer(selector.playerName()).changeType();
        }else if(selector.entityUuid() != null) {
            entityData = forEntity(selector.entityUuid());
        }else if(selector.self()) {
            entityData = getCurrentEntity();
        }else if(selector.requirePlayer()) {
            entityData = forEachPlayer().changeType();
        }else {
            entityData = forEachEntity(world);
        }
        return forSelectorBase(entityData, selector, position, world);
    }

    private CompilationData<LocationData<? extends Entity>> forSelectorBase(CompilationData<LocationData<? extends Entity>> entityData, SelectorArgument selector, LocationData<Vec3> position, LocationData<World> world) {
        var data = entityData.data();
        int stackCount = entityData.stackCount();
        int stackStart = stack.size();
        data.writeInstruction(instructionWriter, Instructions.ifNullJump(entityData.content(), data.contextEndLabel()));
        if(world != null) {
            LocationData<World> thisWorld = createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityWorld(thisWorld, entityData.content()));
            data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.NOT_IDENTITY_EQUAL, thisWorld, world, data.contextEndLabel()));
        }
        if(selector.requireAlive()) {
            var alive = this.<Boolean>createLocal();
            data.writeInstruction(instructionWriter, Instructions.isEntityAlive(alive, entityData.content()));
            data.writeInstruction(instructionWriter, Instructions.ifFalseJump(alive, data.contextEndLabel()));
        }
        if(selector.entityTypes() != null) {
            var entityType = this.<EntityTypeValue>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityType(entityType, entityData.content()));

            if(!selector.entityTypes().negated()) {
                var jump = data.writeLabel(instructionWriter);
                for(var compareType : selector.entityTypes().data()) {
                    var compareEntityType = this.<EntityTypeValue>createLocal();
                    instructionWriter.addInstructionBefore(Instructions.mapEntityType(compareEntityType, compareType), jump);
                    instructionWriter.addInstructionBefore(Instructions.ifObjectCompareJump(ObjectComparisonType.IDENTITY_EQUAL, entityType, compareEntityType, jump), jump);
                }
                instructionWriter.addInstructionBefore(Instructions.jump(data.contextEndLabel()), jump);
            }else {
                for(var compareType : selector.entityTypes().data()) {
                    var compareEntityType = this.<EntityTypeValue>createLocal();
                    data.writeInstruction(instructionWriter, Instructions.mapEntityType(compareEntityType, compareType));
                    data.writeInstruction(instructionWriter, Instructions.ifObjectCompareJump(ObjectComparisonType.IDENTITY_EQUAL, entityType, compareEntityType, data.contextEndLabel()));
                }
            }
        }
        for(var gamemode : selector.gamemodes()) {
            var playerLocal = this.<Player>createLocal();
            data.writeInstruction(instructionWriter, Instructions.tryAsPlayer(playerLocal, entityData.content(), data.contextEndLabel()));
            var playerGamemode = this.<GameType>createLocal();
            var mappedGamemode = this.<GameType>createLocal();
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
            var tags = this.<Set<String>>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityTags(tags, entityData.content()));
            var resolved = getResolvedStringMacro(tag.data());
            if(tag.negated()) {
                data.writeInstruction(instructionWriter, Instructions.jumpIfSetContains(tags, resolved, data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.jumpIfSetNotContains(tags, resolved, data.contextEndLabel()));
            }
        }
        if(selector.hasTags() != null) {
            var tags = this.<Set<String>>createLocal();
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
            var playerLocal = this.<Player>createLocal();
            var levelLocal = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions.tryAsPlayer(playerLocal, entityData.content(), data.contextEndLabel()));
            data.writeInstruction(instructionWriter, Instructions.getExperienceLevel(levelLocal, playerLocal));
            jumpIfNotMatchesRange(data, levelLocal, selector.level(), data.contextEndLabel());
        }
        if(selector.advancements() != null && !selector.advancements().isEmpty()) {
            var possible = true;
            out: for(var check : selector.advancements().entrySet()) {
                var advancement = DataRegistries.ADVANCEMENT.get(check.getKey());
                if(advancement == null) {
                    possible = false;
                    break;
                }
                if(check.getValue() instanceof SelectorAdvancementCheck.CriteriaCompleted criteriaCompleted) {
                    for(var criterion : criteriaCompleted.criteria().keySet()) {
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
                var playerLocal = this.<Player>createLocal();
                data.writeInstruction(instructionWriter, Instructions.tryAsPlayer(playerLocal, entityData.content(), data.contextEndLabel()));
                var playerAdvancements = this.<PlayerAdvancements>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getPlayerAdvancements(playerAdvancements, playerLocal));

                for(var check : selector.advancements().entrySet()) {
                    var advancementProgressLocal = this.<AdvancementProgress>createLocal();
                    data.writeInstruction(instructionWriter, Instructions.getAdvancementProgress(advancementProgressLocal, playerAdvancements, check.getKey()));
                    if(check.getValue() instanceof SelectorAdvancementCheck.AdvancementCompleted advancementCompleted) {
                        if(advancementCompleted.done()) {
                            data.writeInstruction(instructionWriter, Instructions.jumpIfAdvancementNotDone(advancementProgressLocal, data.contextEndLabel()));
                        }else {
                            data.writeInstruction(instructionWriter, Instructions.jumpIfAdvancementDone(advancementProgressLocal, data.contextEndLabel()));
                        }
                    }else if(check.getValue() instanceof SelectorAdvancementCheck.CriteriaCompleted criteriaCompleted) {
                        for(var criteriaCheck : criteriaCompleted.criteria().entrySet()) {
                            var criterionProgressLocal = this.<CriterionProgress>createLocal();
                            data.writeInstruction(instructionWriter, Instructions.getCriterionProgress(criterionProgressLocal, advancementProgressLocal, LocationData.ofString(criteriaCheck.getKey())));
                            if(criteriaCheck.getValue()) {
                                data.writeInstruction(instructionWriter, Instructions.jumpIfAdvancementCriterionNotDone(criterionProgressLocal, data.contextEndLabel()));
                            }else {
                                data.writeInstruction(instructionWriter, Instructions.jumpIfAdvancementCriterionDone(criterionProgressLocal, data.contextEndLabel()));
                            }
                        }
                    }
                }
            }
        }
        if(selector.yaw() != null || selector.pitch() != null) {
            var rotationLocal = this.<Vec2>createLocal();
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
            var entityTeam = this.<Team>createLocal();
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
            var entityTeam = this.<Team>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityTeam(entityTeam, entityData.content()));
            if(selector.hasTeam()) {
                data.writeInstruction(instructionWriter, Instructions.ifNullJump(entityTeam, data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.ifNotNullJump(entityTeam, data.contextEndLabel()));
            }
        }
        if(selector.distance() != null) {
            var thisPosition = this.<Vec3>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityPosition(thisPosition, entityData.content()));
            var distance = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getDistanceSquared(distance, position, thisPosition));
            jumpIfNotMatchesRange(data, distance, selector.distance().square(), data.contextEndLabel());
        }
        if(selector.dx() != null || selector.dy() != null || selector.dz() != null) {
            var compareX = this.<Double>createLocal();
            var compareY = this.<Double>createLocal();
            var compareZ = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getVec3X(compareX, position));
            data.writeInstruction(instructionWriter, Instructions.getVec3Y(compareY, position));
            data.writeInstruction(instructionWriter, Instructions.getVec3Z(compareZ, position));
            var aabb = selector.createLocalAABB();
            var transformedMinX = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAdd(transformedMinX, compareX, LocationData.ofDouble(aabb.minX())));
            var transformedMinY = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAdd(transformedMinY, compareY, LocationData.ofDouble(aabb.minY())));
            var transformedMinZ = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAdd(transformedMinZ, compareZ, LocationData.ofDouble(aabb.minZ())));
            var transformedMaxX = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAdd(transformedMaxX, compareX, LocationData.ofDouble(aabb.maxX())));
            var transformedMaxY = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAdd(transformedMaxY, compareY, LocationData.ofDouble(aabb.maxY())));
            var transformedMaxZ = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions.valueAdd(transformedMaxZ, compareZ, LocationData.ofDouble(aabb.maxZ())));
            data.writeInstruction(instructionWriter, Instructions.checkEntityIntersection(entityData.content(), transformedMinX, transformedMinY, transformedMinZ, transformedMaxX, transformedMaxY, transformedMaxZ, data.contextEndLabel()));
        }
        if(!selector.scores().isEmpty()) {
            for(var entry : selector.scores().entrySet()) {
                var objectiveData = getObjective(entry.getKey());
                var nameData = getScoreboardName(entityData.content());
                var scoreData = getScoreData(new ScoreAccessor(objectiveData.content(), nameData), false);
                data = stack.peek();

                var scoreValue = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions.getScoreValue(scoreValue, scoreData.content()));
                jumpIfNotMatchesRange(data, scoreValue, entry.getValue(), data.contextEndLabel());
            }
        }
        for(var predicate : selector.predicates()) {
            var predicateData = DataRegistries.PREDICATE.get(predicate.data());
            if(predicateData == null) {
                data.writeInstruction(instructionWriter, Instructions.jump(data.contextEndLabel()));
                break;
            }
            var entityWorld = this.<World>createLocal();
            data.writeInstruction(instructionWriter, Instructions.getEntityWorld(entityWorld, entityData.content()));
            if(predicate.negated()) {
                data.writeInstruction(instructionWriter, Instructions.jumpIfPredicateSelectorSucceeds(entityWorld, entityData.content(), data.contextEndLabel(), predicate.data()));
            }else {
                data.writeInstruction(instructionWriter, Instructions.jumpIfPredicateSelectorFails(entityWorld, entityData.content(), data.contextEndLabel(), predicate.data()));
            }
        }
        stackCount += stack.size() - stackStart;
        return enterWithValue(data, entityData.content(), stackCount + 1);
    }

    public CompilationData<ForkingContext> withForkingContext(ForkingContext forkingContext) {
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

    public CompilationData<Object> generateForkExecCode(ForkingContext forkingContext) {
        if(forkingContext == null) {
            return new CompilationData<>(this, current(), null, 0);
        }
        var data = current();
        var commandStack = this.<CommandSourceStack>createLocal();
        writeInstruction(Instructions.makeCommandSourceStack(commandStack, data.commandSourceStack(), data.currentEntity(), data.currentWorld(), data.currentPosition(), data.currentRotation(), data.currentAnchor()));
        writeInstruction(Instructions.addListElement(forkingContext.getForkContextList(), commandStack));

        var length = this.<Integer>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getListLength(length, forkingContext.getForkContextList()), forkingContext.getForkExecLabel());
        var index = this.<Integer>createLocal();
        instructionWriter.addInstructionBefore(Instructions.valueAssign(index, LocationData.ofInteger(0)), forkingContext.getForkExecLabel());
        var iterStart = instructionWriter.createLabelBefore(forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, index, length, forkingContext.getForkExecLabel()), forkingContext.getForkExecLabel());
        var commandStackObj = this.createLocal();
        var cmdStack = this.<CommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions.getListElement(commandStackObj, forkingContext.getForkContextList(), index), forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.assumeCommandStack(cmdStack, commandStackObj), forkingContext.getForkExecLabel());
        var codeLocation = instructionWriter.createLabelBefore(forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.valueAdd(index, index, LocationData.ofInteger(1)), forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions.jump(iterStart), forkingContext.getForkExecLabel());

        return enterWithoutValue(CompilationContextData.create(cmdStack, codeLocation));
    }


    public void jumpIfMatchesRange(CompilationContextData data, LocationData<Integer> value, IntRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
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
