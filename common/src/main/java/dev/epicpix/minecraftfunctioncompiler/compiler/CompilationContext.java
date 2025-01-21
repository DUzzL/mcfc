package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.il.*;

import java.util.*;

public class CompilationContext {

    private final Stack<CompilationContextData> stack = new Stack<>();
    private final UniqueIdTracker idTracker = new UniqueIdTracker();
    public final InstructionWriter instructionWriter = new InstructionWriter();

    public CompilationContext() {
        var commandStack = this.<CommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions2.getCommandStack(commandStack), instructionWriter.getEndLabel());
        enterWith(CompilationContextData.create(commandStack, instructionWriter.getEndLabel()));
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
            throw new IllegalStateException("end() called while not at root context");
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

    public LocationData<String> getResolvedMacro(MacroTemplate<String> macro) {
        if(macro.parts().size() == 1 && macro.parts().get(0) instanceof MacroTemplate.LiteralKind literal) {
            return LocationData.ofString(literal.literal());
        }
        var macroString = this.<String>createLocal();
        writeInstruction(Instructions.loadMacro(macro, macroString));
        return macroString;
    }

    public ResultStorage withResultStorage(boolean requireResultValue, boolean requireSuccessValue) {
        if(requireResultValue == requireSuccessValue) throw new UnsupportedOperationException("calling withResultStorage() with requireResultValue == requireSuccessValue is not supported");
        var loc = this.<Integer>createLocal();
        var res = requireResultValue ? ResultStorage.withResult(loc) : ResultStorage.withSuccess(loc);
        writeInstruction(Instructions2.valueDefine(loc, LocationData.ofInteger(0)));
        return res;
    }

    public CompilationData<LocationData<Entity>> getCurrentEntity() {
        var data = current();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<Entity> entity = createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getStackEntity(data.commandSourceStack(), entity));
            data.writeInstruction(instructionWriter, Instructions2.ifNullJump(entity.lower(), data.contextEndLabel()));
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
            data.writeInstruction(instructionWriter, Instructions2.getStackPosition(data.commandSourceStack(), position));
            return enterWithValue(data.withCurrentPosition(position), position);
        }
    }

    public CompilationData<LocationData<Vec2>> getRotation() {
        var data = current();
        if(data.currentRotation() != null) {
            return new CompilationData<>(this, data, data.currentRotation(), 0);
        }else {
            LocationData<Vec2> rotation = createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getStackRotation(data.commandSourceStack(), rotation));
            return enterWithValue(data.withCurrentRotation(rotation), rotation);
        }
    }

    public CompilationData<LocationData<World>> getWorld() {
        var data = current();
        if(data.currentWorld() != null) {
            return new CompilationData<>(this, data, data.currentWorld(), 0);
        }else {
            LocationData<World> world = createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getStackWorld(data.commandSourceStack(), world));
            return enterWithValue(data.withCurrentWorld(world), world);
        }
    }

    public CompilationData<LocationData<Vec3>> applyPosition(WorldCoordinates pos) {
        if(pos.localCoordinates()) {
            var entityData = getCurrentEntity();
            var positionData = getPosition();
            var rotationData = getRotation();

            var offset = this.<Double>createLocal();
            var position = this.<Vec3>createLocal();
            if(rotationData.data().currentAnchor() == LocationAnchor.EYES) {
                rotationData.writeInstruction(Instructions2.getEyeHeight(entityData.content(), offset));
            }else if(rotationData.data().currentAnchor() == LocationAnchor.FEET) {
                offset = LocationData.ofDouble(0.0);
            }else {
                rotationData.writeInstruction(Instructions2.getAnchorOffset(rotationData.data().commandSourceStack(), entityData.content(), offset));
            }
            rotationData.writeInstruction(Instructions.applyLocalPositionWorld(positionData.content(), rotationData.content(), offset, position, pos));
            return enterWithValue(rotationData.data(), position, entityData.stackCount() + positionData.stackCount() + rotationData.stackCount() + 1);
        }
        var positionData = getPosition();
        var position = this.<Vec3>createLocal();
        positionData.writeInstruction(Instructions.applyPositionWorld(positionData.content(), position, pos));
        return enterWithValue(positionData.data(), position, positionData.stackCount() + 1);
    }

    public CompilationData<LocationData<Vec2>> applyRotation(RotationCoordinates rot) {
        var rotationData = getRotation();
        var rotation = this.<Vec2>createLocal();
        rotationData.writeInstruction(Instructions.applyRotationWorld(rotationData.content(), rotation, rot));
        return enterWithValue(rotationData.data(), rotation, rotationData.stackCount() + 1);
    }

    public CompilationData<LocationData<Entity>> forEntity(UUID entityUuid) {
        var data = current();
        var worldIter = this.<Iterator<World>>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getWorldsIterator(worldIter.forceCast()));
        var iterStart = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions2.jumpIfIteratorHasNoNext(worldIter.forceCast(), data.contextEndLabel()));
        var worldObj = this.createLocal();
        var world = this.<World>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.iteratorNext(worldIter.forceCast(), worldObj));
        data.writeInstruction(instructionWriter, Instructions2.assumeWorld(worldObj, world));

        var entity = this.<Entity>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getEntity(world, entity, entityUuid));
        data.writeInstruction(instructionWriter, Instructions2.ifNullJump(entity.lower(), iterStart));

        var codeLocation = data.writeLabel(instructionWriter);
        data.writeInstruction(instructionWriter, Instructions2.jump(data.contextEndLabel()));

        return enterWithValue(data.withContextLabel(codeLocation), entity);
    }

    public CompilationData<LocationData<Entity>> forEachEntity(LocationData<World> inWorld) {
        var data = current();
        if(inWorld == null) {
            var worldIter = this.<Iterator<World>>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getWorldsIterator(worldIter.forceCast()));
            var iterWorldStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.jumpIfIteratorHasNoNext(worldIter.forceCast(), data.contextEndLabel()));
            var worldObj = this.createLocal();
            var world = this.<World>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.iteratorNext(worldIter.forceCast(), worldObj));
            data.writeInstruction(instructionWriter, Instructions2.assumeWorld(worldObj, world));

            var entityIter = this.<Iterator<Entity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntities(world, entityIter.forceCast()));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.jumpIfIteratorHasNoNext(entityIter.forceCast(), iterWorldStart));
            var entityObj = this.createLocal();
            var entity = this.<Entity>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.iteratorNext(entityIter.forceCast(), entityObj));
            data.writeInstruction(instructionWriter, Instructions2.assumeEntity(entityObj, entity));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }else {
            var entityIter = this.<Iterator<Entity>>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntities(inWorld, entityIter.forceCast()));
            var iterEntityStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.jumpIfIteratorHasNoNext(entityIter.forceCast(), data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<Entity>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.iteratorNext(entityIter.forceCast(), entityObj));
            data.writeInstruction(instructionWriter, Instructions2.assumeEntity(entityObj, entity));

            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.jump(iterEntityStart));
            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    public CompilationData<LocationData<Player>> forPlayer(String playerName) {
        var data = current();

        var player = this.<Player>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getPlayer(player, playerName));
        data.writeInstruction(instructionWriter, Instructions2.ifNullJump(player.lower(), data.contextEndLabel()));
        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), player);
    }

    public CompilationData<LocationData<Player>> forEachPlayer() {
        var data = current();

        var index = this.<Integer>createLocal();
        var length = this.<Integer>createLocal();
        var playerList = this.<List<Player>>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.valueDefine(index, LocationData.ofInteger(0)));
        data.writeInstruction(instructionWriter, Instructions2.getPlayers(playerList.forceCast()));
        data.writeInstruction(instructionWriter, Instructions2.getListLength(playerList.forceCast(), length));

        var iterStart = data.writeLabel(instructionWriter);

        data.writeInstruction(instructionWriter, Instructions2.ifGreaterEqualJump(index, length, data.contextEndLabel()));

        var playerObj = this.createLocal();
        var player = this.<Player>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getListElement(playerList.forceCast(), index, playerObj));
        data.writeInstruction(instructionWriter, Instructions2.assumePlayer(playerObj, player));

        var codeLocation = data.writeLabel(instructionWriter);

        data.writeInstruction(instructionWriter, Instructions2.valueAdd(index, LocationData.ofInteger(1)));
        data.writeInstruction(instructionWriter, Instructions2.jump(iterStart));

        return enterWithValue(data.withContextLabel(codeLocation), player);
    }

    public CompilationData<LocationData<Objective>> getObjective(String objectiveName) {
        var data = current();

        LocationData<Objective> objective = createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getObjective(objective, objectiveName));
        data.writeInstruction(instructionWriter, Instructions2.ifNullJump(objective.lower(), data.contextEndLabel()));

        var codeLocation = data.writeLabel(instructionWriter);

        return enterWithValue(data.withContextLabel(codeLocation), objective);
    }

    @SuppressWarnings("resource")
    public CompilationData<ScoreAccessor> forScoreAccessors(ScoreAccessorArgument accessor) {
        var objectiveName = getObjective(accessor.objective());
        var scoreHolder = forScoreHolder(accessor.scoreHolder());
        return enterWithValue(scoreHolder.data(), new ScoreAccessor(objectiveName.content(), scoreHolder.content()), objectiveName.stackCount() + scoreHolder.stackCount() + 1);
    }

    public LocationData<String> getScoreboardName(LocationData<Entity> entity) {
        var data = current();
        LocationData<String> name = createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getScoreboardName(entity, name));
        return name;
    }

    public CompilationData<LocationData<Score>> getScoreData(ScoreAccessor accessor, boolean autoCreate) {
        var data = current();
        LocationData<Score> score = createLocal();
        if(autoCreate) {
            data.writeInstruction(instructionWriter, Instructions2.scoreDataAutoCreate(accessor.objective(), accessor.scoreHolder(), score));
            return new CompilationData<>(this, data, score, 0);
        }else {
            data.writeInstruction(instructionWriter, Instructions2.scoreDataNoAutoCreate(accessor.objective(), accessor.scoreHolder(), score));
            data.writeInstruction(instructionWriter, Instructions2.ifNullJump(score.lower(), data.contextEndLabel()));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), score);
        }
    }

    public CompilationData<LocationData<String>> forEachScoreHolder(boolean single) {
        var data = current();
        var scoreHolders = this.<List<String>>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getScoreboardHolders(scoreHolders.forceCast()));
        var listLength = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getListLength(scoreHolders.forceCast(), listLength));
        if(single) {
            data.writeInstruction(instructionWriter, Instructions2.ifEqualJump(listLength, LocationData.ofInteger(0), data.contextEndLabel()));
            var scoreHolderObj = this.createLocal();
            var scoreHolder = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getListElement(scoreHolders.forceCast(), LocationData.ofInteger(0), scoreHolderObj));
            data.writeInstruction(instructionWriter, Instructions2.assumeString(scoreHolderObj, scoreHolder));
            var codeLocation = data.writeLabel(instructionWriter);
            return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
        }else {
            var index = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueDefine(index, LocationData.ofInteger(0)));
            var iterStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterEqualJump(index, listLength, data.contextEndLabel()));
            var scoreHolderObj = this.createLocal();
            var scoreHolder = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getListElement(scoreHolders.forceCast(), index, scoreHolderObj));
            data.writeInstruction(instructionWriter, Instructions2.assumeString(scoreHolderObj, scoreHolder));
            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.valueAdd(index, LocationData.ofInteger(1)));
            data.writeInstruction(instructionWriter, Instructions2.jump(iterStart));
            return enterWithValue(data.withContextLabel(codeLocation), scoreHolder);
        }
    }

    public CompilationData<LocationData<String>> forScoreHolder(ScoreHolderArgument holder) {
        if(holder.all()) {
            return forEachScoreHolder(holder.single());
        }else if(holder.name() != null) {
            var macro = getResolvedMacro(holder.name());
            return new CompilationData<>(this, current(), macro, 0);
        }else if(holder.selector() != null) {
            var data = current();
            var selector = holder.single() ? holder.selector().limit(1) : holder.selector();
            @SuppressWarnings("resource")
            var selectorData = forSelector(selector);
            var name = this.<String>createLocal();
            selectorData.writeInstruction(Instructions2.getScoreboardName(selectorData.content(), name));
            var codeLocation = instructionWriter.createLabelBefore(selectorData.data().contextEndLabel());
            return enterWithValue(selectorData.data().withContextLabel(codeLocation), name, selectorData.stackCount() + 1);
        }else {
            throw new IllegalArgumentException("ScoreHolderArgument without any valid option: " + holder);
        }
    }

    public CompilationData<LocationData<Entity>> forSelector(SelectorArgument selector) {
        var data = current();
        if(selector.requirePlayer() && selector.excludesPlayers()) {
            LocationData<Entity> entity = createLocal();
            return new CompilationData<>(this, data.withContextLabel(InstructionWriter.NULL_LABEL), entity, 0);
        }

        LocationData<List<Entity>> entityList = createLocal();

        try(var position = getPosition()) {
            LocationData<Vec3> realPosition;
            if(selector.x() != null || selector.y() != null || selector.z() != null) {
                realPosition = this.createLocal();
                position.writeInstruction(Instructions.mergePositions(position.content(), LocationData.ofDoubleOrNull(selector.x()), LocationData.ofDoubleOrNull(selector.y()), LocationData.ofDoubleOrNull(selector.z()), realPosition));
            }else {
                realPosition = position.content();
            }

            data.writeInstruction(instructionWriter, Instructions2.createMutableList(entityList.forceCast()));

            var selectorCodeLocation = data.writeLabel(instructionWriter);

            try(var ignored = enterWithoutValue(data.withContextLabel(selectorCodeLocation))) {
                if(selector.worldLimited()) {
                    try(var world = getWorld()) {
                        try(var selectorData = forSelectorSelect(selector, realPosition, world.content())) {
                            selectorData.writeInstruction(Instructions2.addListElement(entityList.forceCast(), selectorData.content().lower()));
                        }
                    }
                }else {
                    try(var selectorData = forSelectorSelect(selector, realPosition, null)) {
                        selectorData.writeInstruction(Instructions2.addListElement(entityList.forceCast(), selectorData.content().lower()));
                    }
                }
            }

            if(selector.sort() == SelectorSort.NEAREST || selector.sort() == SelectorSort.FURTHEST) {
                switch(selector.sort()) {
                    case FURTHEST -> position.writeInstruction(Instructions2.sortListEntityFurthest(realPosition, entityList.forceCast()));
                    case NEAREST -> position.writeInstruction(Instructions2.sortListEntityNearest(realPosition, entityList.forceCast()));
                }
            }else if(selector.sort() == SelectorSort.RANDOM) {
                data.writeInstruction(instructionWriter, Instructions2.shuffleList(entityList.forceCast()));
            }
        }

        var length = this.<Integer>createLocal();
        data.writeInstruction(instructionWriter, Instructions2.getListLength(entityList.forceCast(), length));
        if(selector.limit() != null) {
            if(selector.limit() == 1) {
                data.writeInstruction(instructionWriter, Instructions2.ifEqualJump(length, LocationData.ofInteger(0), data.contextEndLabel()));
                var entityObj = this.createLocal();
                var entity = this.<Entity>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.getListElement(entityList.forceCast(), LocationData.ofInteger(0), entityObj));
                data.writeInstruction(instructionWriter, Instructions2.assumeEntity(entityObj, entity));
                var codeLocation = data.writeLabel(instructionWriter);
                return enterWithValue(data.withContextLabel(codeLocation), entity);
            }else {
                var index = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.valueDefine(index, LocationData.ofInteger(0)));
                var iterStart = data.writeLabel(instructionWriter);
                data.writeInstruction(instructionWriter, Instructions2.ifGreaterEqualJump(index, length, data.contextEndLabel()));
                data.writeInstruction(instructionWriter, Instructions2.ifGreaterEqualJump(index, LocationData.ofInteger(selector.limit()), data.contextEndLabel()));
                var entityObj = this.createLocal();
                var entity = this.<Entity>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.getListElement(entityList.forceCast(), index, entityObj));
                data.writeInstruction(instructionWriter, Instructions2.assumeEntity(entityObj, entity));
                var codeLocation = data.writeLabel(instructionWriter);
                data.writeInstruction(instructionWriter, Instructions2.valueAdd(index, LocationData.ofInteger(1)));
                data.writeInstruction(instructionWriter, Instructions2.jump(iterStart));

                return enterWithValue(data.withContextLabel(codeLocation), entity);
            }
        }else {
            var index = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueDefine(index, LocationData.ofInteger(0)));
            var iterStart = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterEqualJump(index, length, data.contextEndLabel()));
            var entityObj = this.createLocal();
            var entity = this.<Entity>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getListElement(entityList.forceCast(), index, entityObj));
            data.writeInstruction(instructionWriter, Instructions2.assumeEntity(entityObj, entity));
            var codeLocation = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.valueAdd(index, LocationData.ofInteger(1)));
            data.writeInstruction(instructionWriter, Instructions2.jump(iterStart));

            return enterWithValue(data.withContextLabel(codeLocation), entity);
        }
    }

    @SuppressWarnings("resource")
    private CompilationData<LocationData<Entity>> forSelectorSelect(SelectorArgument selector, LocationData<Vec3> position, LocationData<World> world) {
        CompilationData<LocationData<Entity>> entityData;
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

    private CompilationData<LocationData<Entity>> forSelectorBase(CompilationData<LocationData<Entity>> entityData, SelectorArgument selector, LocationData<Vec3> position, LocationData<World> world) {
        var data = entityData.data();
        int stackCount = entityData.stackCount();
        int stackStart = stack.size();
        data.writeInstruction(instructionWriter, Instructions2.ifNullJump(entityData.content().lower(), data.contextEndLabel()));
        if(world != null) {
            LocationData<World> thisWorld = createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityWorld(entityData.content(), thisWorld));
            data.writeInstruction(instructionWriter, Instructions2.ifObjectNotEquivalentJump(thisWorld.lower(), world.lower(), data.contextEndLabel()));
        }
        if(selector.requireAlive()) {
            var alive = this.<Boolean>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getIsAlive(entityData.content(), alive));
            data.writeInstruction(instructionWriter, Instructions2.ifFalseJump(alive, data.contextEndLabel()));
        }
        if(selector.entityTypes() != null) {
            data.writeInstruction(instructionWriter, Instructions.checkEntityTypes(entityData.content(), new ArrayList<>(selector.entityTypes().data()), selector.entityTypes().negated(), data.contextEndLabel()));
        }
        for(var gamemode : selector.gamemodes()) {
            var playerLocal = this.<Player>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.tryAsPlayer(entityData.content(), playerLocal, data.contextEndLabel()));
            var playerGamemode = this.<GameType>createLocal();
            var mappedGamemode = this.<GameType>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.mapGamemode(gamemode.data(), mappedGamemode));
            data.writeInstruction(instructionWriter, Instructions2.getGamemode(playerLocal, playerGamemode));
            if(gamemode.negated()) {
                data.writeInstruction(instructionWriter, Instructions2.ifObjectEquivalentJump(playerGamemode.lower(), mappedGamemode.lower(), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions2.ifObjectNotEquivalentJump(playerGamemode.lower(), mappedGamemode.lower(), data.contextEndLabel()));
            }
        }
        for(var name : selector.names()) {
            var entityName = this.<String>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityName(entityData.content(), entityName));
            if(name.negated()) {
                data.writeInstruction(instructionWriter, Instructions2.ifObjectEqualJump(entityName.lower(), LocationData.ofString(name.data()).lower(), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions2.ifObjectNotEqualJump(entityName.lower(), LocationData.ofString(name.data()).lower(), data.contextEndLabel()));
            }
        }
        for(var tag : selector.tags()) {
            var tags = this.<Set<String>>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityTags(entityData.content(), tags.forceCast()));
            var resolved = getResolvedMacro(tag.data());
            if(tag.negated()) {
                data.writeInstruction(instructionWriter, Instructions2.jumpIfSetContains(tags.forceCast(), resolved.lower(), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions2.jumpIfSetNotContains(tags.forceCast(), resolved.lower(), data.contextEndLabel()));
            }
        }
        if(selector.hasTags() != null) {
            var tags = this.<Set<String>>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityTags(entityData.content(), tags.forceCast()));
            var tagsLength = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getSetLength(tags.forceCast(), tagsLength));
            if(selector.hasTags()) {
                data.writeInstruction(instructionWriter, Instructions2.ifEqualJump(tagsLength, LocationData.ofInteger(0), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions2.ifNotEqualJump(tagsLength, LocationData.ofInteger(0), data.contextEndLabel()));
            }
        }
        if(selector.level() != null) {
            var playerLocal = this.<Player>createLocal();
            var levelLocal = this.<Integer>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.tryAsPlayer(entityData.content(), playerLocal, data.contextEndLabel()));
            data.writeInstruction(instructionWriter, Instructions2.getExperienceLevel(playerLocal, levelLocal));
            jumpIfNotMatchesRange(data, levelLocal, selector.level(), data.contextEndLabel());
        }
        if(selector.yaw() != null || selector.pitch() != null) {
            var rotationLocal = this.<Vec2>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityRotation(entityData.content(), rotationLocal));
            if(selector.yaw() != null) {
                var yaw = this.<Float>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.getRotationYaw(rotationLocal, yaw));
                jumpIfNotMatchesRange(data, yaw, selector.yaw(), data.contextEndLabel());
            }
            if(selector.pitch() != null) {
                var pitch = this.<Float>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.getRotationPitch(rotationLocal, pitch));
                jumpIfNotMatchesRange(data, pitch, selector.pitch(), data.contextEndLabel());
            }
        }
        for(var team : selector.teams()) {
            var entityTeam = this.<Team>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityTeam(entityData.content(), entityTeam));
            if(team.negated()) {
                var postJump = data.writeLabel(instructionWriter);
                instructionWriter.addInstructionBefore(Instructions2.ifNullJump(entityTeam.lower(), postJump), postJump);
                var entityTeamName = this.<String>createLocal();
                instructionWriter.addInstructionBefore(Instructions2.getTeamName(entityTeam, entityTeamName), postJump);
                instructionWriter.addInstructionBefore(Instructions2.ifObjectEqualJump(entityTeamName.lower(), LocationData.ofString(team.data()).lower(), data.contextEndLabel()), postJump);
            }else {
                data.writeInstruction(instructionWriter, Instructions2.ifNullJump(entityTeam.lower(), data.contextEndLabel()));
                var entityTeamName = this.<String>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.getTeamName(entityTeam, entityTeamName));
                data.writeInstruction(instructionWriter, Instructions2.ifObjectNotEqualJump(entityTeamName.lower(), LocationData.ofString(team.data()).lower(), data.contextEndLabel()));
            }
        }
        if(selector.hasTeam() != null) {
            var entityTeam = this.<Team>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityTeam(entityData.content(), entityTeam));
            if(selector.hasTeam()) {
                data.writeInstruction(instructionWriter, Instructions2.ifNullJump(entityTeam.lower(), data.contextEndLabel()));
            }else {
                data.writeInstruction(instructionWriter, Instructions2.ifNotNullJump(entityTeam.lower(), data.contextEndLabel()));
            }
        }
        if(selector.distance() != null) {
            var thisPosition = this.<Vec3>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getEntityPosition(entityData.content(), thisPosition));
            var distance = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getDistanceSquared(position, thisPosition, distance));
            jumpIfNotMatchesRange(data, distance, selector.distance().square(), data.contextEndLabel());
        }
        if(selector.dx() != null || selector.dy() != null || selector.dz() != null) {
            var compareX = this.<Double>createLocal();
            var compareY = this.<Double>createLocal();
            var compareZ = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.getVec3X(position, compareX));
            data.writeInstruction(instructionWriter, Instructions2.getVec3Y(position, compareY));
            data.writeInstruction(instructionWriter, Instructions2.getVec3Z(position, compareZ));
            var aabb = selector.createLocalAABB();
            var transformedMinX = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueAddAssign(transformedMinX.forceCast(), compareX.forceCast(), LocationData.ofDouble(aabb.minX()).forceCast()));
            var transformedMinY = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueAddAssign(transformedMinY.forceCast(), compareY.forceCast(), LocationData.ofDouble(aabb.minY()).forceCast()));
            var transformedMinZ = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueAddAssign(transformedMinZ.forceCast(), compareZ.forceCast(), LocationData.ofDouble(aabb.minZ()).forceCast()));
            var transformedMaxX = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueAddAssign(transformedMaxX.forceCast(), compareX.forceCast(), LocationData.ofDouble(aabb.maxX()).forceCast()));
            var transformedMaxY = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueAddAssign(transformedMaxY.forceCast(), compareY.forceCast(), LocationData.ofDouble(aabb.maxY()).forceCast()));
            var transformedMaxZ = this.<Double>createLocal();
            data.writeInstruction(instructionWriter, Instructions2.valueAddAssign(transformedMaxZ.forceCast(), compareZ.forceCast(), LocationData.ofDouble(aabb.maxZ()).forceCast()));
            data.writeInstruction(instructionWriter, Instructions.checkEntityIntersection(entityData.content(), transformedMinX, transformedMinY, transformedMinZ, transformedMaxX, transformedMaxY, transformedMaxZ, data.contextEndLabel()));
        }
        if(!selector.scores().isEmpty()) {
            for(var entry : selector.scores().entrySet()) {
                var objectiveData = getObjective(entry.getKey());
                var nameData = getScoreboardName(entityData.content());
                var scoreData = getScoreData(new ScoreAccessor(objectiveData.content(), nameData), false);
                data = stack.peek();

                var scoreValue = this.<Integer>createLocal();
                data.writeInstruction(instructionWriter, Instructions2.getScoreValue(scoreData.content(), scoreValue));
                jumpIfNotMatchesRange(data, scoreValue, entry.getValue(), data.contextEndLabel());
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
        data.writeInstruction(instructionWriter, Instructions2.createMutableList(forkContexts));
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
        writeInstruction(Instructions.makeCommandSourceStack(data.commandSourceStack(), commandStack, data.currentEntity(), data.currentWorld(), data.currentPosition(), data.currentRotation(), data.currentAnchor()));
        writeInstruction(Instructions2.addListElement(forkingContext.getForkContextList(), commandStack.lower()));

        var length = this.<Integer>createLocal();
        instructionWriter.addInstructionBefore(Instructions2.getListLength(forkingContext.getForkContextList(), length), forkingContext.getForkExecLabel());
        var index = this.<Integer>createLocal();
        instructionWriter.addInstructionBefore(Instructions2.valueDefine(index, LocationData.ofInteger(0)), forkingContext.getForkExecLabel());
        var iterStart = instructionWriter.createLabelBefore(forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions2.ifGreaterEqualJump(index, length, forkingContext.getForkExecLabel()), forkingContext.getForkExecLabel());
        var commandStackObj = this.createLocal();
        var cmdStack = this.<CommandSourceStack>createLocal();
        instructionWriter.addInstructionBefore(Instructions2.getListElement(forkingContext.getForkContextList(), index, commandStackObj), forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions2.assumeCommandStack(commandStackObj, cmdStack), forkingContext.getForkExecLabel());
        var codeLocation = instructionWriter.createLabelBefore(forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions2.valueAdd(index, LocationData.ofInteger(1)), forkingContext.getForkExecLabel());
        instructionWriter.addInstructionBefore(Instructions2.jump(iterStart), forkingContext.getForkExecLabel());

        return enterWithoutValue(CompilationContextData.create(cmdStack, codeLocation));
    }


    public void jumpIfMatchesRange(CompilationContextData data, LocationData<Integer> value, IntRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            data.writeInstruction(instructionWriter, Instructions2.ifEqualJump(value, LocationData.ofInteger(range.minimum()), label));
        }else if(range.minimum() != null && range.maximum() == null) {
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterEqualJump(value, LocationData.ofInteger(range.minimum()), label));
        }else if(range.minimum() == null) {
            data.writeInstruction(instructionWriter, Instructions2.ifLessEqualJump(value, LocationData.ofInteger(range.maximum()), label));
        }else {
            var failure = data.writeLabel(instructionWriter);
            data.writeInstruction(instructionWriter, Instructions2.ifLessThanJump(value, LocationData.ofInteger(range.minimum()), failure));
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterThanJump(value, LocationData.ofInteger(range.maximum()), failure));
            data.writeInstruction(instructionWriter, Instructions2.jump(label));
        }
    }

    public void jumpIfNotMatchesRange(CompilationContextData data, LocationData<Integer> value, IntRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            data.writeInstruction(instructionWriter, Instructions2.ifNotEqualJump(value, LocationData.ofInteger(range.minimum()), label));
            return;
        }
        if(range.minimum() != null) {
            data.writeInstruction(instructionWriter, Instructions2.ifLessThanJump(value, LocationData.ofInteger(range.minimum()), label));
        }
        if(range.maximum() != null) {
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterThanJump(value, LocationData.ofInteger(range.maximum()), label));
        }
    }

    public void jumpIfNotMatchesRange(CompilationContextData data, LocationData<Float> value, FloatRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            data.writeInstruction(instructionWriter, Instructions2.ifNotEqualJump(value.forceCast(), LocationData.ofFloat(range.minimum()).forceCast(), label));
            return;
        }
        if(range.minimum() != null) {
            data.writeInstruction(instructionWriter, Instructions2.ifLessThanJump(value.forceCast(), LocationData.ofFloat(range.minimum()).forceCast(), label));
        }
        if(range.maximum() != null) {
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterThanJump(value.forceCast(), LocationData.ofFloat(range.maximum()).forceCast(), label));
        }
    }

    public void jumpIfNotMatchesRange(CompilationContextData data, LocationData<Double> value, DoubleRangeArgument range, LabelData label) {
        if(Objects.equals(range.minimum(), range.maximum())) {
            data.writeInstruction(instructionWriter, Instructions2.ifNotEqualJump(value.forceCast(), LocationData.ofDouble(range.minimum()).forceCast(), label));
            return;
        }
        if(range.minimum() != null) {
            data.writeInstruction(instructionWriter, Instructions2.ifLessThanJump(value.forceCast(), LocationData.ofDouble(range.minimum()).forceCast(), label));
        }
        if(range.maximum() != null) {
            data.writeInstruction(instructionWriter, Instructions2.ifGreaterThanJump(value.forceCast(), LocationData.ofDouble(range.maximum()).forceCast(), label));
        }
    }

}
