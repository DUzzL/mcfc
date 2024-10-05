package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.il.*;

import java.util.*;

public class CompilationContext {

    private final Stack<CompilationContextData> stack = new Stack<>();
    private final UniqueIdTracker idTracker = new UniqueIdTracker();

    public CompilationContext(CompilationContextData init) {
        enterWith(init);
    }

    public <T> CompilationData<T> enterWithValue(CompilationContextData data, T value, int stackCount) {
        enterWith(data);
        return new CompilationData<>(this, data, value, stackCount);
    }

    public <T> CompilationData<T> enterWithInstructionAndValue(CompilationContextData data, Instruction instruction, T value) {
        data.addInstructionConsumer().accept(instruction);
        return enterWithValue(data.withBaseInstruction(instruction), value, 1);
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

    public int createId() {
        return idTracker.createId();
    }

    public <T> LocationData<T> createLocal() {
        return LocationData.ofLocal(createId());
    }

    public ScopeData createScope() {
        return ScopeData.ofId(createId());
    }

    public CompilationData<LocationData<String>> getResolvedMacro(MacroTemplate<String> macro) {
        var data = stack.peek();
        if(macro.parts().size() == 1 && macro.parts().get(0) instanceof MacroTemplate.LiteralKind literal) {
            return new CompilationData<>(this, data, LocationData.ofString(literal.literal()), 0);
        }
        var macroString = this.<String>createLocal();
        var instr = Instructions.loadMacro(macro, macroString);
        writeInstruction(instr);
        return enterWithValue(data.withBaseInstruction(instr), macroString);
    }

    public CompilationData<ResultStorage> withResultStorage(boolean requireResultValue, boolean requireSuccessValue) {
        if(requireResultValue == requireSuccessValue) throw new UnsupportedOperationException("calling withResultStorage() with requireResultValue == requireSuccessValue is not supported");
        var loc = this.<Integer>createLocal();
        var res = requireResultValue ? ResultStorage.withResult(loc) : ResultStorage.withSuccess(loc);
        var instr = Instructions2.createResultStorage(loc);
        return enterWithInstructionAndValue(current(), instr, res);
    }

    public CompilationData<ScopeData> forScope() {
        var scope = createScope();
        var instruction = Instructions2.scope(scope);
        return enterWithInstructionAndValue(current(), instruction, scope);
    }

    public CompilationData<LocationData<Entity>> getCurrentEntity() {
        var data = stack.peek();
        if(data.currentEntity() != null) {
            return new CompilationData<>(this, data, data.currentEntity(), 0);
        }else {
            LocationData<Entity> entity = createLocal();
            var instruction = Instructions2.getCurrentEntity(entity);
            return enterWithInstructionAndValue(data.withCurrentEntity(entity), instruction, entity);
        }
    }

    public CompilationData<LocationData<Vec3>> getPosition() {
        if(stack.peek().currentPosition() != null) {
            var data = stack.peek();
            return new CompilationData<>(this, data, data.currentPosition(), 0);
        }else {
            var current = current();
            LocationData<Vec3> position = createLocal();
            var instruction = Instructions2.getStackPosition(position);
            return enterWithInstructionAndValue(current.withCurrentPosition(position), instruction, position);
        }
    }

    public CompilationData<LocationData<Vec2>> getRotation() {
        if(stack.peek().currentRotation() != null) {
            var data = stack.peek();
            return new CompilationData<>(this, data, data.currentRotation(), 0);
        }else {
            var current = current();
            LocationData<Vec2> rotation = createLocal();
            var instruction = Instructions2.getStackRotation(rotation);
            return enterWithInstructionAndValue(current.withCurrentRotation(rotation), instruction, rotation);
        }
    }

    public CompilationData<LocationData<Vec3>> applyPosition(WorldCoordinates pos) {
        if(pos.localCoordinates()) {
            var entityData = getCurrentEntity();
            var positionData = getPosition();
            var rotationData = getRotation();
            var offset = this.<Double>createLocal();
            var position = this.<Vec3>createLocal();
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
            return enterWithValue(rotationData.data().withBaseInstruction(stacker.current), position, entityData.stackCount() + positionData.stackCount() + rotationData.stackCount() + 1);
        }
        var positionData = getPosition();
        var position = this.<Vec3>createLocal();
        var instruction = Instructions.applyPositionWorld(positionData.content(), position, pos);
        positionData.writeInstruction(instruction);
        return enterWithValue(positionData.data().withBaseInstruction(instruction), position, positionData.stackCount() + 1);
    }

    public CompilationData<LocationData<Vec2>> applyRotation(RotationCoordinates rot) {
        var rotationData = getRotation();
        var rotation = this.<Vec2>createLocal();
        var instruction = Instructions.applyRotationWorld(rotationData.content(), rotation, rot);
        rotationData.writeInstruction(instruction);
        return enterWithValue(rotationData.data().withBaseInstruction(instruction), rotation, rotationData.stackCount() + 1);
    }

    public CompilationData<LocationData<Entity>> forEntity(UUID entityUuid) {
        LocationData<Entity> entity = createLocal();
        var instruction = Instructions2.forEntity(entity, entityUuid);
        return enterWithInstructionAndValue(current(), instruction, entity);
    }

    public CompilationData<LocationData<Entity>> forEachEntity() {
        LocationData<Entity> entity = createLocal();
        var instruction = Instructions2.forEachEntity(entity);
        return enterWithInstructionAndValue(current(), instruction, entity);
    }

    public CompilationData<LocationData<Player>> forPlayer(String playerName) {
        LocationData<Player> player = createLocal();
        var instruction = Instructions2.forPlayer(player, playerName);
        return enterWithInstructionAndValue(current(), instruction, player);
    }

    public CompilationData<LocationData<Player>> forEachPlayer() {
        LocationData<Player> player = createLocal();
        var instruction = Instructions2.forEachPlayer(player);
        return enterWithInstructionAndValue(current(), instruction, player);
    }

    public CompilationData<LocationData<Objective>> getObjective(String objectiveName) {
        LocationData<Objective> objective = createLocal();
        var execInstruction = Instructions2.ifNotNull(objective.lower());
        var instruction = Instructions2.getObjective(objective, objectiveName).with(execInstruction);
        writeInstruction(instruction);
        return enterWithValue(current().withBaseInstruction(execInstruction), objective);
    }

    @SuppressWarnings("resource")
    public CompilationData<ScoreAccessor> forScoreAccessors(ScoreAccessorArgument accessor) {
        var objectiveName = getObjective(accessor.objective());
        var scoreHolder = forScoreHolder(accessor.scoreHolder());
        return enterWithValue(scoreHolder.data(), new ScoreAccessor(objectiveName.content(), scoreHolder.content()), objectiveName.stackCount() + scoreHolder.stackCount() + 1);
    }

    public CompilationData<LocationData<String>> getScoreboardName(LocationData<Entity> entity) {
        LocationData<String> name = createLocal();
        var instruction = Instructions2.getScoreboardName(entity, name);
        return enterWithInstructionAndValue(current(), instruction, name);
    }

    public CompilationData<LocationData<Score>> getScoreData(ScoreAccessor accessor, boolean autoCreate) {
        var data = stack.peek();
        LocationData<Score> score = createLocal();
        var instruction = Instructions2.scoreData(accessor.objective(), accessor.scoreHolder(), score, autoCreate);
        data.writeInstruction(instruction);
        if(autoCreate) {
            return enterWithValue(data.withBaseInstruction(instruction), score);
        }else {
            var nonNull = Instructions2.ifNotNull(score.lower());
            instruction.with(nonNull);
            return enterWithValue(data.withBaseInstruction(nonNull), score);
        }
    }

    public CompilationData<LocationData<String>> forEachScoreHolder(boolean single) {
        var name = this.<String>createLocal();
        var instruction = Instructions2.forScoreHolders(name, single);
        return enterWithInstructionAndValue(current(), instruction, name);
    }

    public CompilationData<LocationData<String>> forScoreHolder(ScoreHolderArgument holder) {
        if(holder.all()) {
            return forEachScoreHolder(holder.single());
        }else if(holder.name() != null) {
            return getResolvedMacro(holder.name());
        }else if(holder.selector() != null) {
            var selector = holder.single() ? holder.selector().limit(1) : holder.selector();
            @SuppressWarnings("resource")
            var selectorData = forSelector(selector);
            var name = this.<String>createLocal();
            var instruction = Instructions2.getScoreboardName(selectorData.content(), name);
            selectorData.writeInstruction(Instructions2.ifNotNull(selectorData.content().lower()).with(instruction));
            return enterWithValue(selectorData.data().withBaseInstruction(instruction), name, selectorData.stackCount() + 1);
        }else {
            throw new IllegalArgumentException("ScoreHolderArgument without any valid option: " + holder);
        }
    }

    public CompilationData<LocationData<Entity>> forSelector(SelectorArgument selector) {
        if(selector.requirePlayer() && selector.excludesPlayers()) {
            LocationData<Entity> entity = createLocal();
            var instr = Instructions2.never();
            return enterWithInstructionAndValue(current(), instr, entity);
        }
        LocationData<List<?>> entityList = createLocal();
        var entityListInstruction = Instructions2.createMutableList(entityList);
        var data = current();
        data.writeInstruction(entityListInstruction);
        enterWith(data.withBaseInstruction(entityListInstruction));
        try(var selectorData = forSelectorSelect(selector)) {
            selectorData.writeInstruction(Instructions2.addListElement(entityList, selectorData.content().lower()));
        }
        if(selector.sort() == SelectorSort.NEAREST || selector.sort() == SelectorSort.FURTHEST) {
            try(var pos = getPosition()) {
                switch(selector.sort()) {
                    case FURTHEST -> pos.writeInstruction(Instructions2.sortListEntityFurthest(pos.content(), entityList));
                    case NEAREST -> pos.writeInstruction(Instructions2.sortListEntityNearest(pos.content(), entityList));
                }
            }
        }else if(selector.sort() == SelectorSort.RANDOM) {
            entityListInstruction.with(Instructions2.shuffleList(entityList));
        }
        LocationData<Object> objectLocation = createLocal();
        LocationData<Entity> entityLocation = createLocal();
        if(selector.limit() != null) {
            var execInstr = Instructions2.always();
            if(selector.limit() == 1) {
                ScopeData earlyScope = createScope();
                var instr = Instructions2.scope(earlyScope)
                        .with(Instructions2.forEachListElement(entityList, objectLocation)
                                .with(Instructions2.tryAsEntity(objectLocation, entityLocation)
                                        .with(execInstr)
                                        .with(Instructions2.exitScope(earlyScope))));
                entityListInstruction.with(instr);
            }else {
                ScopeData limiterScope = createScope();
                LocationData<Integer> limiterCounter = createLocal();
                var instr = Instructions2.limiter(limiterScope, limiterCounter, selector.limit())
                        .with(Instructions2.forEachListElement(entityList, objectLocation)
                                .with(Instructions2.tryAsEntity(objectLocation, entityLocation)
                                        .with(execInstr)
                                        .with(Instructions2.checkLimit(limiterScope, limiterCounter))));
                entityListInstruction.with(instr);
            }
            return enterWithValue(data.withBaseInstruction(execInstr), entityLocation, 2);
        }else {
            var tryEntityInstr = Instructions2.tryAsEntity(objectLocation, entityLocation);
            var instr = Instructions2.forEachListElement(entityList, objectLocation).with(tryEntityInstr);
            entityListInstruction.with(instr);
            return enterWithValue(data.withBaseInstruction(tryEntityInstr), entityLocation, 2);
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
            var playerLocal = this.<Player>createLocal();
            stacker.stackInstruction(Instructions2.tryAsPlayer(entityData.content(), playerLocal));
            stacker.stackInstruction(Instructions2.checkGamemode(playerLocal, gamemode.data(), gamemode.negated()));
        }
        for(var name : selector.names()) {
            stacker.stackInstruction(Instructions2.checkEntityName(entityData.content(), name.data(), name.negated()));
        }
        enterWith(data = data.withBaseInstruction(stacker.current));
        for(var tag : selector.tags()) {
            var resolved = getResolvedMacro(tag.data());
            stacker.stackInstruction(Instructions2.checkEntityTag(entityData.content(), resolved.content(), tag.negated()));
            enterWith(data = resolved.data().withBaseInstruction(stacker.current));
        }
        if(selector.hasTags() != null) {
            stacker.stackInstruction(Instructions2.checkEntityTags(entityData.content(), selector.hasTags()));
        }
        if(selector.level() != null) {
            var playerLocal = this.<Player>createLocal();
            var levelLocal = this.<Integer>createLocal();
            stacker.stackInstruction(Instructions2.tryAsPlayer(entityData.content(), playerLocal));
            stacker.stackInstruction(Instructions2.getExperienceLevel(playerLocal, levelLocal));
            stacker.stackInstruction(Instructions.checkIntRange(levelLocal, selector.level()));
        }
        if(selector.yaw() != null || selector.pitch() != null) {
            var rotationLocal = this.<Vec2>createLocal();
            stacker.stackInstruction(Instructions2.getEntityRotation(entityData.content(), rotationLocal));
            if(selector.yaw() != null) stacker.stackInstruction(Instructions.checkRotationYaw(rotationLocal, selector.yaw()));
            if(selector.pitch() != null) stacker.stackInstruction(Instructions.checkRotationPitch(rotationLocal, selector.pitch()));
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

            LocationData<Vec3> thisPosition = createLocal();
            stacker.stackInstruction(Instructions2.getEntityPosition(entityData.content(), thisPosition));
            LocationData<Double> distance = createLocal();
            stacker.stackInstruction(Instructions2.getDistanceSquared(positionData.content(), thisPosition, distance));
            stacker.stackInstruction(Instructions.checkDoubleRange(distance, selector.distance().square()));
            enterWith(positionData.data().withBaseInstruction(stacker.current));

            data = stack.peek();
        }
        if(!selector.scores().isEmpty()) {
            enterWith(data.withBaseInstruction(stacker.current));
            for(var entry : selector.scores().entrySet()) {
                var objectiveData = getObjective(entry.getKey());
                var nameData = getScoreboardName(entityData.content());
                var scoreData = getScoreData(new ScoreAccessor(objectiveData.content(), nameData.content()), false);

                var scoreValue = this.<Integer>createLocal();
                var execInstruction = Instructions.checkIntRange(scoreValue, entry.getValue());
                var baseInstruction = Instructions2.getScoreValue(scoreData.content(), scoreValue).with(execInstruction);
                scoreData.writeInstruction(baseInstruction);
                enterWith(scoreData.data().withBaseInstruction(execInstruction));
                stacker.current = execInstruction;
            }
            data = stack.peek();
        }
        stackCount += stack.size() - stackStart;
        return enterWithValue(data.withBaseInstruction(stacker.current), entityData.content(), stackCount + 1);
    }

}
