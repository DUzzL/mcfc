package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.il.*;

import java.util.*;

public class CompilationContext {

    private final Stack<CompilationContextData> stack = new Stack<>();

    public CompilationContext(CompilationContextData init) {
        enterWith(init);
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

    public CompilationData<Object> enterWithoutValueUsingInstruction(Instruction instruction) {
        return enterWithValue(stack.peek().withBaseInstruction(instruction), null, 1);
    }

    public CompilationContextData current() {
        return stack.peek();
    }

    public void enterWith(CompilationContextData data) {
        stack.push(data);
    }

    public void exit() {
        stack.pop();
    }

    public CompilationData<ResultStorage> withResultStorage(boolean requireResultValue, boolean requireSuccessValue) {
        if(requireResultValue == requireSuccessValue) throw new UnsupportedOperationException("calling withResultStorage() with requireResultValue == requireSuccessValue is not supported");
        var data = stack.peek();
        var loc = LocationData.<Integer>ofLocal(data.highestId());
        var res = requireResultValue ? ResultStorage.withResult(loc) : ResultStorage.withSuccess(loc);
        var instr = Instructions2.createResultStorage(loc);
        data.addInstructionConsumer().accept(instr);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instr), res);
    }

    public CompilationData<ScopeData> forScope() {
        var data = stack.peek();
        var scope = ScopeData.ofId(data.highestId());
        var instruction = Instructions2.scope(scope);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), scope);
    }

    public CompilationData<LocationData<Entity>> getCurrentEntity() {
        var data = stack.peek();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<Entity> entity = LocationData.ofLocal(data.highestId());
            var instruction = Instructions2.getCurrentEntity(entity);
            data.addInstructionConsumer().accept(instruction);
            return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction).withCurrentEntity(entity), entity);
        }
    }

    public CompilationData<LocationData<Vec3>> getPosition() {
        if(stack.peek().currentPosition() != null) {
            var data = stack.peek();
            return new CompilationData<>(this, data, data.currentPosition(), 0);
        }else {
            var entityData = getCurrentEntity();
            LocationData<Vec3> position = LocationData.ofLocal(entityData.data().highestId());
            var instruction = Instructions2.getEntityPosition(entityData.content(), position);
            entityData.writeInstruction(Instructions2.ifNotNull(entityData.content().lower()).with(instruction));
            return enterWithValue(entityData.data().incrementedId(1).withBaseInstruction(instruction).withCurrentPosition(position), position, entityData.stackCount() + 1);
        }
    }

    public CompilationData<LocationData<Vec2>> getRotation() {
        if(stack.peek().currentRotation() != null) {
            var data = stack.peek();
            return new CompilationData<>(this, data, data.currentRotation(), 0);
        }else {
            var entityData = getCurrentEntity();
            LocationData<Vec2> rotation = LocationData.ofLocal(entityData.data().highestId());
            var instruction = Instructions2.getEntityRotation(entityData.content(), rotation);
            entityData.writeInstruction(Instructions2.ifNotNull(entityData.content().lower()).with(instruction));
            return enterWithValue(entityData.data().incrementedId(1).withBaseInstruction(instruction).withCurrentRotation(rotation), rotation, entityData.stackCount() + 1);
        }
    }

    public CompilationData<LocationData<Vec3>> applyPosition(WorldCoordinates pos) {
        var positionData = getPosition();
        var position = LocationData.<Vec3>ofLocal(positionData.data().highestId());
        var instruction = Instructions.applyPositionWorld(positionData.content(), position, pos);
        positionData.writeInstruction(instruction);
        return enterWithValue(positionData.data().incrementedId(1).withBaseInstruction(instruction), position, positionData.stackCount() + 1);
    }

    public CompilationData<LocationData<Vec2>> applyRotation(RotationCoordinates rot) {
        var rotationData = getRotation();
        var rotation = LocationData.<Vec2>ofLocal(rotationData.data().highestId());
        var instruction = Instructions.applyRotationWorld(rotationData.content(), rotation, rot);
        rotationData.writeInstruction(instruction);
        return enterWithValue(rotationData.data().incrementedId(1).withBaseInstruction(instruction), rotation, rotationData.stackCount() + 1);
    }

    public CompilationData<LocationData<Entity>> forEntity(UUID entityUuid) {
        var data = stack.peek();
        LocationData<Entity> entity = LocationData.ofLocal(data.highestId());
        var instruction = Instructions2.forEntity(entity, entityUuid);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), entity);
    }

    public CompilationData<LocationData<Entity>> forEachEntity() {
        var data = stack.peek();
        LocationData<Entity> entity = LocationData.ofLocal(data.highestId());
        var instruction = Instructions2.forEachEntity(entity);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), entity);
    }

    public CompilationData<LocationData<Player>> forPlayer(String playerName) {
        var data = stack.peek();
        LocationData<Player> player = LocationData.ofLocal(data.highestId());
        var instruction = Instructions2.forPlayer(player, playerName);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), player);
    }

    public CompilationData<LocationData<Player>> forEachPlayer() {
        var data = stack.peek();
        LocationData<Player> player = LocationData.ofLocal(data.highestId());
        var instruction = Instructions2.forEachPlayer(player);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), player);
    }

    public CompilationData<LocationData<Objective>> getObjective(String objectiveName) {
        var data = stack.peek();
        LocationData<Objective> objective = LocationData.ofLocal(data.highestId());
        var execInstruction = Instructions2.ifNotNull(objective.lower());
        var instruction = Instructions2.getObjective(objective, objectiveName).with(execInstruction);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(execInstruction), objective);
    }

    public CompilationData<LocationData<String>> getScoreboardName(LocationData<Entity> entity) {
        var data = stack.peek();
        LocationData<String> name = LocationData.ofLocal(data.highestId());
        var instruction = Instructions2.getScoreboardName(entity, name);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), name);
    }

    public CompilationData<LocationData<Score>> getScoreData(LocationData<Objective> objective, LocationData<String> name, boolean autoCreate) {
        var data = stack.peek();
        LocationData<Score> score = LocationData.ofLocal(data.highestId());
        var instruction = Instructions2.scoreData(objective, name, score, autoCreate);
        data.addInstructionConsumer().accept(instruction);
        if(autoCreate) {
            return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), score);
        }else {
            var nonNull = Instructions2.ifNotNull(score.lower());
            instruction.with(nonNull);
            return enterWithValue(data.incrementedId(1).withBaseInstruction(nonNull), score);
        }
    }

    public CompilationData<LocationData<String>> forEachScoreHolder(boolean single) {
        var data = stack.peek();
        var name = LocationData.<String>ofLocal(data.highestId());
        var instruction = Instructions2.forScoreHolders(name, single);
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.incrementedId(1).withBaseInstruction(instruction), name);
    }

    public CompilationData<LocationData<String>> forScoreHolder(ScoreHolderArgument holder) {
        if(holder.all()) {
            return forEachScoreHolder(holder.single());
        }else if(holder.name() != null) {
            return new CompilationData<>(this, stack.peek(), LocationData.ofString(holder.name()), 0);
        }else if(holder.selector() != null) {
            var selector = holder.single() ? holder.selector().limit(1) : holder.selector();
            @SuppressWarnings("resource")
            var selectorData = forSelector(selector);
            var name = LocationData.<String>ofLocal(selectorData.data().highestId());
            var instruction = Instructions2.getScoreboardName(selectorData.content(), name);
            selectorData.writeInstruction(Instructions2.ifNotNull(selectorData.content().lower()).with(instruction));
            return enterWithValue(selectorData.data().incrementedId(1).withBaseInstruction(instruction), name, selectorData.stackCount() + 1);
        }else {
            throw new IllegalArgumentException("ScoreHolderArgument without any valid option: " + holder);
        }
    }

    @SuppressWarnings("resource")
    public CompilationData<LocationData<Entity>> forSelector(SelectorArgument selector) {
        if(selector.requirePlayer() && selector.excludesPlayers()) {
            var data = stack.peek();
            LocationData<Entity> entity = LocationData.ofLocal(data.highestId());
            var instr = Instructions2.never();
            data.addInstructionConsumer().accept(instr);
            return new CompilationData<>(this, data.incrementedId(1).withBaseInstruction(instr), entity, 0);
        }
        var scope = forScope();
        if(selector.limit() != null && selector.limit() != 1) {
            var data = stack.peek();
            ScopeData limiterScope = ScopeData.ofId(data.highestId());
            LocationData<Integer> limiterCounter = LocationData.ofLocal(data.highestId() + 1);
            var instr = Instructions2.limiter(limiterScope, limiterCounter, selector.limit());
            enterWith(data.incrementedId(2).withBaseInstruction(instr));
            return forSelectorSelect(selector, limiterScope, limiterCounter, scope.content(), scope.stackCount() + 1);
        }else {
            return forSelectorSelect(selector, null, null, scope.content(), scope.stackCount());
        }
    }

    @SuppressWarnings("resource")
    private CompilationData<LocationData<Entity>> forSelectorSelect(SelectorArgument selector, ScopeData limiterScope, LocationData<Integer> limitedCounter, ScopeData breakScope, int stackCount) {
        CompilationData<LocationData<Entity>> entityData;
        if(selector.playerName() != null) {
            entityData = forPlayer(selector.playerName()).changeType();
        }else if(selector.entityUuid() != null) {
            entityData = forEntity(selector.entityUuid());
        }else if(selector.self()) {
            entityData = getCurrentEntity();
        }else if(selector.selectPlayers()) {
            entityData = forEachPlayer().changeType();
        }else {
            entityData = forEachEntity();
        }
        return forSelectorBase(entityData, selector, limiterScope, limitedCounter, breakScope, stackCount);
    }

    private CompilationData<LocationData<Entity>> forSelectorBase(CompilationData<LocationData<Entity>> entityData, SelectorArgument selector, ScopeData limiterScope, LocationData<Integer> limiterCounter, ScopeData breakScope, int inStackCount) {
        var data = entityData.data();
        int stackCount = entityData.stackCount() + inStackCount;
        var nonNull = Instructions2.ifNotNull(entityData.content().lower());
        entityData.writeInstruction(nonNull);
        var stacker = new InstructionStacker(nonNull);
        if(selector.requirePlayer()) {
            stacker.stackInstruction(Instructions.checkEntityTypes(entityData.content(), List.of(new DataLocation("minecraft", "player")), false));
        }
        if(selector.entityTypes() != null) {
            stacker.stackInstruction(Instructions.checkEntityTypes(entityData.content(), new ArrayList<>(selector.entityTypes().data()), selector.entityTypes().negated()));
        }
        if(selector.gamemode() != null) {
            stacker.stackInstruction(Instructions2.checkGamemode(entityData.content(), selector.gamemode().data(), selector.gamemode().negated()));
        }
        for(var tag : selector.tags()) {
            stacker.stackInstruction(Instructions2.checkEntityTag(entityData.content(), tag.data(), tag.negated()));
        }
        if(selector.hasTags() != null) {
            stacker.stackInstruction(Instructions2.checkEntityTags(entityData.content(), selector.hasTags()));
        }
        if(selector.level() != null) {
            var levelLocal = LocationData.<Integer>ofLocal(data.highestId());
            stacker.stackInstruction(Instructions2.getExperienceLevel(entityData.content(), levelLocal));
            stacker.stackInstruction(Instructions.checkIntRange(levelLocal, selector.level()));
            data = data.incrementedId(1);
            stackCount++;
            enterWith(data);
        }
        if(selector.yaw() != null || selector.pitch() != null) {
            var rotationLocal = LocationData.<Vec2>ofLocal(data.highestId());
            stacker.stackInstruction(Instructions2.getEntityRotation(entityData.content(), rotationLocal));
            if(selector.yaw() != null) stacker.stackInstruction(Instructions.checkRotationYaw(rotationLocal, selector.yaw()));
            if(selector.pitch() != null) stacker.stackInstruction(Instructions.checkRotationPitch(rotationLocal, selector.pitch()));
            data = data.incrementedId(1);
            stackCount++;
            enterWith(data);
        }
        for(var team : selector.teams()) {
            stacker.stackInstruction(Instructions2.checkTeam(entityData.content(), team.data(), team.negated()));
        }
        if(selector.hasTeam() != null) {
            stacker.stackInstruction(Instructions2.hasTeam(entityData.content(), selector.hasTeam()));
        }
        if(!selector.scores().isEmpty()) {
            stackCount++;
            enterWith(data.withBaseInstruction(stacker.current));
            for(var entry : selector.scores().entrySet()) {
                var objectiveData = getObjective(entry.getKey());
                stackCount += objectiveData.stackCount();
                var nameData = getScoreboardName(entityData.content());
                stackCount += nameData.stackCount();
                var scoreData = getScoreData(objectiveData.content(), nameData.content(), false);
                stackCount += scoreData.stackCount();

                var instr = Instructions.checkInScoreRange(scoreData.content(), entry.getValue());
                scoreData.writeInstruction(instr);
                stackCount++;
                enterWith(scoreData.data().withBaseInstruction(instr));
                stacker.current = instr;
            }
            data = stack.peek();
        }
        var execBase = Instructions2.always();
        stacker.current.with(execBase);
        if(Objects.equals(selector.limit(), 1)) {
            stacker.current.with(Instructions2.exitScope(breakScope));
        }else if(selector.limit() != null) {
            stacker.current.with(Instructions2.checkLimit(limiterScope, limiterCounter));
        }
        return enterWithValue(data.withBaseInstruction(execBase), entityData.content(), stackCount + 1);
    }

}
