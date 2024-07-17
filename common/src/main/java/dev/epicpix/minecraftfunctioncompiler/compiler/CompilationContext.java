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

    public void writeInstruction(Instruction instruction) {
        current().writeInstruction(instruction);
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
            var current = current();
            LocationData<Vec3> position = LocationData.ofLocal(current.highestId());
            var instruction = Instructions2.getStackPosition(position);
            writeInstruction(instruction);
            return enterWithValue(current.incrementedId(1).withBaseInstruction(instruction).withCurrentPosition(position), position);
        }
    }

    public CompilationData<LocationData<Vec2>> getRotation() {
        if(stack.peek().currentRotation() != null) {
            var data = stack.peek();
            return new CompilationData<>(this, data, data.currentRotation(), 0);
        }else {
            var current = current();
            LocationData<Vec2> rotation = LocationData.ofLocal(current.highestId());
            var instruction = Instructions2.getStackRotation(rotation);
            writeInstruction(instruction);
            return enterWithValue(current.incrementedId(1).withBaseInstruction(instruction).withCurrentRotation(rotation), rotation);
        }
    }

    public CompilationData<LocationData<Vec3>> applyPosition(WorldCoordinates pos) {
        if(pos.localCoordinates()) {
            var entityData = getCurrentEntity();
            var positionData = getPosition();
            var rotationData = getRotation();
            var offset = LocationData.<Double>ofLocal(rotationData.data().highestId());
            var position = LocationData.<Vec3>ofLocal(rotationData.data().highestId() + 1);
            var stacker = new InstructionStacker(Instructions2.always());
            if(rotationData.data().currentAnchor() == LocationAnchor.EYES) {
                stacker.stackInstruction(Instructions2.getEyeHeight(entityData.content(), offset));
            }else if(rotationData.data().currentAnchor() == LocationAnchor.FEET) {
                offset = LocationData.ofDouble(0.0);
            }else {
                stacker.stackInstruction(Instructions2.getAnchorOffset(entityData.content(), offset));
            }
            stacker.stackInstruction(Instructions.applyLocalPositionWorld(positionData.content(), rotationData.content(), offset, position, pos));
            rotationData.writeInstruction(stacker.base);
            return enterWithValue(rotationData.data().incrementedId(2).withBaseInstruction(stacker.current), position, entityData.stackCount() + positionData.stackCount() + rotationData.stackCount() + 1);
        }
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

    public CompilationData<LocationData<Entity>> forSelector(SelectorArgument selector) {
        var data = stack.peek();
        if(selector.requirePlayer() && selector.excludesPlayers()) {
            LocationData<Entity> entity = LocationData.ofLocal(data.highestId());
            var instr = Instructions2.never();
            data.addInstructionConsumer().accept(instr);
            return new CompilationData<>(this, data.incrementedId(1).withBaseInstruction(instr), entity, 0);
        }
        LocationData<List<?>> entityList = LocationData.ofLocal(data.highestId());
        var entityListInstruction = Instructions2.createMutableList(entityList);
        data.addInstructionConsumer().accept(entityListInstruction);
        enterWith(data.incrementedId(1).withBaseInstruction(entityListInstruction));
        try(var selectorData = forSelectorSelect(selector)) {
            selectorData.writeInstruction(Instructions2.addListElement(entityList, selectorData.content().lower()));
        }
        if(selector.sort() == SelectorSort.NEAREST || selector.sort() == SelectorSort.FURTHEST) {
            try(var pos = getPosition()) {
                switch(selector.sort()) {
                    case FURTHEST -> {
                        pos.writeInstruction(Instructions2.sortListEntityFurthest(pos.content(), entityList));
                    }
                    case NEAREST -> {
                        pos.writeInstruction(Instructions2.sortListEntityNearest(pos.content(), entityList));
                    }
                }
            }
        }else if(selector.sort() == SelectorSort.RANDOM) {
            entityListInstruction.with(Instructions2.shuffleList(entityList));
        }
        LocationData<Object> objectLocation = LocationData.ofLocal(data.highestId() + 1);
        LocationData<Entity> entityLocation = LocationData.ofLocal(data.highestId() + 2);
        if(selector.limit() != null) {
            if(selector.limit() == 1) {
                var execInstr = Instructions2.always();
                ScopeData earlyScope = ScopeData.ofId(data.highestId() + 3);
                var instr = Instructions2.scope(earlyScope)
                        .with(Instructions2.forEachListElement(entityList, objectLocation)
                                .with(Instructions2.tryAsEntity(objectLocation, entityLocation)
                                        .with(execInstr)
                                        .with(Instructions2.exitScope(earlyScope))));
                entityListInstruction.with(instr);
                return enterWithValue(data.incrementedId(4).withBaseInstruction(execInstr), entityLocation, 2);
            }else {
                var execInstr = Instructions2.always();
                ScopeData limiterScope = ScopeData.ofId(data.highestId() + 3);
                LocationData<Integer> limiterCounter = LocationData.ofLocal(data.highestId() + 4);
                var instr = Instructions2.limiter(limiterScope, limiterCounter, selector.limit())
                        .with(Instructions2.forEachListElement(entityList, objectLocation)
                                .with(Instructions2.tryAsEntity(objectLocation, entityLocation)
                                        .with(execInstr)
                                        .with(Instructions2.checkLimit(limiterScope, limiterCounter))));
                entityListInstruction.with(instr);
                return enterWithValue(data.incrementedId(5).withBaseInstruction(execInstr), entityLocation, 2);
            }
        }else {
            var tryEntityInstr = Instructions2.tryAsEntity(objectLocation, entityLocation);
            var instr = Instructions2.forEachListElement(entityList, objectLocation).with(tryEntityInstr);
            entityListInstruction.with(instr);
            return enterWithValue(data.incrementedId(3).withBaseInstruction(tryEntityInstr), entityLocation, 2);
        }
    }

    @SuppressWarnings("resource")
    private CompilationData<LocationData<Entity>> forSelectorSelect(SelectorArgument selector) {
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
            entityData = forEachEntity();
        }
        return forSelectorBase(entityData, selector);
    }

    private CompilationData<LocationData<Entity>> forSelectorBase(CompilationData<LocationData<Entity>> entityData, SelectorArgument selector) {
        var data = entityData.data();
        int stackCount = entityData.stackCount();
        int stackStart = stack.size();
        var nonNull = Instructions2.ifNotNull(entityData.content().lower());
        entityData.writeInstruction(nonNull);
        var stacker = new InstructionStacker(nonNull);
        if(selector.requireAlive()) {
            stacker.stackInstruction(Instructions2.checkIsAlive(entityData.content()));
        }
        if(selector.entityTypes() != null) {
            stacker.stackInstruction(Instructions.checkEntityTypes(entityData.content(), new ArrayList<>(selector.entityTypes().data()), selector.entityTypes().negated()));
        }
        for(var gamemode : selector.gamemodes()) {
            var playerLocal = LocationData.<Player>ofLocal(data.highestId());
            stacker.stackInstruction(Instructions2.tryAsPlayer(entityData.content(), playerLocal));
            stacker.stackInstruction(Instructions2.checkGamemode(playerLocal, gamemode.data(), gamemode.negated()));
            data = data.incrementedId(1);
            enterWith(data);
        }
        for(var name : selector.names()) {
            stacker.stackInstruction(Instructions2.checkEntityName(entityData.content(), name.data(), name.negated()));
        }
        for(var tag : selector.tags()) {
            stacker.stackInstruction(Instructions2.checkEntityTag(entityData.content(), tag.data(), tag.negated()));
        }
        if(selector.hasTags() != null) {
            stacker.stackInstruction(Instructions2.checkEntityTags(entityData.content(), selector.hasTags()));
        }
        if(selector.level() != null) {
            var playerLocal = LocationData.<Player>ofLocal(data.highestId());
            var levelLocal = LocationData.<Integer>ofLocal(data.highestId() + 1);
            stacker.stackInstruction(Instructions2.tryAsPlayer(entityData.content(), playerLocal));
            stacker.stackInstruction(Instructions2.getExperienceLevel(playerLocal, levelLocal));
            stacker.stackInstruction(Instructions.checkIntRange(levelLocal, selector.level()));
            data = data.incrementedId(2);
            enterWith(data);
        }
        if(selector.yaw() != null || selector.pitch() != null) {
            var rotationLocal = LocationData.<Vec2>ofLocal(data.highestId());
            stacker.stackInstruction(Instructions2.getEntityRotation(entityData.content(), rotationLocal));
            if(selector.yaw() != null) stacker.stackInstruction(Instructions.checkRotationYaw(rotationLocal, selector.yaw()));
            if(selector.pitch() != null) stacker.stackInstruction(Instructions.checkRotationPitch(rotationLocal, selector.pitch()));
            data = data.incrementedId(1);
            enterWith(data);
        }
        for(var team : selector.teams()) {
            stacker.stackInstruction(Instructions2.checkTeam(entityData.content(), team.data(), team.negated()));
        }
        if(selector.hasTeam() != null) {
            stacker.stackInstruction(Instructions2.hasTeam(entityData.content(), selector.hasTeam()));
        }
        if(selector.distance() != null) {
            enterWith(data.withBaseInstruction(stacker.current));

            var positionData = getPosition();
            var instr = Instructions2.ifNotNull(positionData.content().lower());
            positionData.writeInstruction(instr);
            stacker.current = instr;

            LocationData<Vec3> thisPosition = LocationData.ofLocal(positionData.data().highestId());
            stacker.stackInstruction(Instructions2.getEntityPosition(entityData.content(), thisPosition));
            LocationData<Double> distance = LocationData.ofLocal(positionData.data().highestId() + 1);
            stacker.stackInstruction(Instructions2.getDistanceSquared(positionData.content(), thisPosition, distance));
            stacker.stackInstruction(Instructions.checkDoubleRange(distance, selector.distance().square()));
            enterWith(positionData.data().incrementedId(2).withBaseInstruction(stacker.current));

            data = stack.peek();
        }
        if(!selector.scores().isEmpty()) {
            enterWith(data.withBaseInstruction(stacker.current));
            for(var entry : selector.scores().entrySet()) {
                var objectiveData = getObjective(entry.getKey());
                var nameData = getScoreboardName(entityData.content());
                var scoreData = getScoreData(objectiveData.content(), nameData.content(), false);

                var scoreValue = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                var execInstruction = Instructions.checkIntRange(scoreValue, entry.getValue());
                var baseInstruction = Instructions2.getScoreValue(scoreData.content(), scoreValue).with(execInstruction);
                scoreData.writeInstruction(baseInstruction);
                enterWith(scoreData.data().withBaseInstruction(execInstruction).incrementedId(1));
                stacker.current = execInstruction;
            }
            data = stack.peek();
        }
        stackCount += stack.size() - stackStart;
        return enterWithValue(data.withBaseInstruction(stacker.current), entityData.content(), stackCount + 1);
    }

}
