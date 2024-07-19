package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.Command;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPath;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(List<Command> commands) {
        ArrayList<Instruction> instructions = new ArrayList<>();
        var context = new CompilationContext(CompilationContextData.create(instructions::add));
        for(var command : commands) {
            compile(context, command, ResultStorage.empty());
        }
        context.exit();
        return instructions;
    }

    private static void compile(CompilationContext context, Command command, ResultStorage result) {
        switch(command.type()) {
            case ATTRIBUTE_BASE_SET -> {
                try(var selector = context.forSelector(command.get("target"))) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(selector.data().highestId());
                    var attrLocation = LocationData.<AttributeInstance>ofLocal(selector.data().highestId() + 1);
                    var instruction =
                            Instructions2.tryAsLivingEntity(selector.content(), livingEntityLocation)
                                    .with(Instructions2.getAttributeInstance(livingEntityLocation, attrLocation, command.<AttributeData>get("attribute").location())
                                            .with(Instructions2.ifNotNull(attrLocation.lower())
                                                    .with(Instructions2.setAttributeBase(attrLocation, command.get("value")))
                                            ));
                    selector.writeInstruction(instruction);

                }
            }
            case DATA_MODIFY_STORAGE_SET_VALUE -> {
                var storageLocal = LocationData.<NbtCompound>ofLocal(context.current().highestId());
                var setCountLocal = LocationData.<Integer>ofLocal(context.current().highestId() + 1);

                var stacker = new InstructionStacker(Instructions2.always());

                stacker.stackInstruction(Instructions2.getNbtStorage(command.get("storage"), storageLocal));
                var resultStorage = Instructions2.createResultStorage(setCountLocal);
                stacker.stackInstruction(resultStorage);

                var parts = command.<NbtPath>get("path").parts();
                try(var ignored = context.enterWithoutValue(context.current().incrementedId(2).withBaseInstruction(resultStorage))) {
                    getNbtPathValue(context, storageLocal.forceCast(), parts.subList(0, parts.size() - 1), true, (value) -> {
                        var last = parts.get(parts.size() - 1);
                        if(last instanceof NbtPathPart.Named named) {
                            var tagLocal = LocationData.<NbtTag>ofLocal(context.current().highestId());
                            context.writeInstruction(Instructions2.loadNbtValue(command.get("value"), tagLocal).with(Instructions2.setNbtInCompound(value, named.name(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                        }else if(last instanceof NbtPathPart.ArrayIndexInteger indexed) {
                            var tagLocal = LocationData.<NbtTag>ofLocal(context.current().highestId());
                            if(indexed.index() >= 0) {
                                context.writeInstruction(Instructions2.loadNbtValue(command.get("value"), tagLocal).with(Instructions2.setNbtInListForward(value, indexed.index(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                            }else {
                                context.writeInstruction(Instructions2.loadNbtValue(command.get("value"), tagLocal).with(Instructions2.setNbtInListBackward(value, -indexed.index(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                            }
                        }else {
                            throw new UnsupportedOperationException("Cannot set nbt value from path " + last);
                        }
                    });
                }
                stacker.stackInstruction(Instructions2.isNotResultStorage(setCountLocal, 0));
                try(var data = context.enterWithoutValue(context.current().incrementedId(2).withBaseInstruction(stacker.current))) {
                    data.writeInstruction(Instructions2.setNbtStorage(command.get("storage"), storageLocal));
                }

                context.writeInstruction(stacker.base);
            }
            case DIFFICULTY_SET -> {
                context.writeInstruction(Instructions2.setDifficulty(command.get("difficulty")));
            }
            case EFFECT_CLEAR -> {
                try(var self = context.getCurrentEntity()) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(self.data().highestId());
                    self.writeInstruction(
                            Instructions2.ifNotNull(self.content().lower())
                                .with(Instructions2.tryAsLivingEntity(self.content(), livingEntityLocation)
                                        .with(Instructions2.clearAllEffects(livingEntityLocation))));
                }
            }
            case EFFECT_CLEAR_ENTITIES -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                            Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                .with(Instructions2.clearAllEffects(livingEntityLocation)));
                }
            }
            case EFFECT_CLEAR_ENTITIES_EFFECT -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                            Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                    .with(Instructions2.clearEffect(livingEntityLocation, command.<MobEffectData>get("effect").location())));
                }
            }
            case EFFECT_GIVE -> {
                MobEffectData effect = command.get("effect");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? 1 : 600, 0, true)));
                    }
                }
            }
            case EFFECT_GIVE_DUR -> {
                MobEffectData effect = command.get("effect");
                int duration = command.get("duration");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? duration : duration * 20, 0, true)));
                    }
                }
            }
            case EFFECT_GIVE_DUR_AMP -> {
                MobEffectData effect = command.get("effect");
                int duration = command.get("duration");
                int amplifier = command.get("amplifier");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? duration : duration * 20, amplifier, true)));
                    }
                }
            }
            case EFFECT_GIVE_DUR_AMP_SHOW -> {
                MobEffectData effect = command.get("effect");
                int duration = command.get("duration");
                int amplifier = command.get("amplifier");
                boolean hideParticles = command.get("hide_particles");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? duration : duration * 20, amplifier, !hideParticles)));
                    }
                }
            }
            case EFFECT_GIVE_INF -> {
                MobEffectData effect = command.get("effect");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, 0, true)));
                    }
                }
            }
            case EFFECT_GIVE_INF_AMP -> {
                MobEffectData effect = command.get("effect");
                int amplifier = command.get("amplifier");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, amplifier, true)));
                    }
                }
            }
            case EFFECT_GIVE_INF_AMP_SHOW -> {
                MobEffectData effect = command.get("effect");
                int amplifier = command.get("amplifier");
                boolean hideParticles = command.get("hide_particles");
                try(var self = context.getCurrentEntity()) {
                    try(var targets = context.forSelector(command.get("targets"))) {
                        var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                        targets.writeInstruction(
                                Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                                        .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, amplifier, !hideParticles)));
                    }
                }
            }
            case EXECUTE_ALIGN -> {
                try(var pos = context.getPosition()) {
                    var newPosition = LocationData.<Vec3>ofLocal(pos.data().highestId());
                    var instruction = Instructions.alignPosition(pos.content(), newPosition, command.get("swizzle"));
                    try(var ignored = context.enterWithoutValue(pos.data().incrementedId(1).withCurrentPosition(newPosition).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    pos.writeInstruction(instruction);
                }
            }
            case EXECUTE_ANCHORED -> {
                try(var ignored = context.enterWithoutValue(context.current().withCurrentAnchor(command.get("anchor")))) {
                    compile(context, command.get("command"), result);
                }
            }
            case EXECUTE_AS -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    try(var ignored = context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_AT -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    var world = LocationData.<World>ofLocal(selector.data().highestId());
                    var position = LocationData.<Vec3>ofLocal(selector.data().highestId() + 1);
                    var rotation = LocationData.<Vec2>ofLocal(selector.data().highestId() + 2);
                    var execInstruction = Instructions2.getEntityRotation(selector.content(), rotation);
                    var instruction = Instructions2.getEntityWorld(selector.content(), world).with(Instructions2.getEntityPosition(selector.content(), position).with(execInstruction));
                    try(var ignored = context.enterWithoutValue(selector.data().incrementedId(3).withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world).withBaseInstruction(execInstruction))) {
                        compile(context, command.get("command"), result);
                    }
                    selector.writeInstruction(instruction);
                }
            }
            case EXECUTE_IF_ENTITY -> {
                try(var ignored = context.forSelector(command.<SelectorArgument>get("entities").limit(1))) {
                    compile(context, command.get("command"), result);
                }
            }
            case EXECUTE_IF_SCORE_RANGE -> {
                try(var objective = context.getObjective(command.get("targetObjective"))) {
                    try(var name = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), false)) {
                            var scoreValue = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                            var execInstruction = Instructions.checkIntRange(scoreValue, command.get("range"));
                            var baseInstruction = Instructions2.getScoreValue(scoreData.content(), scoreValue).with(execInstruction);
                            try(var ignored = context.enterWithoutValue(scoreData.data().incrementedId(1).withBaseInstruction(execInstruction))) {
                                compile(context, command.get("command"), result);
                            }
                            scoreData.writeInstruction(baseInstruction);
                        }
                    }
                }
            }
            case EXECUTE_IF_SCORE_COMPARE -> {
                try(var targetObjective = context.getObjective(command.get("targetObjective"))) {
                    try(var targetName = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                        try(var sourceObjective = context.getObjective(command.get("sourceObjective"))) {
                            try(var sourceName = context.forScoreHolder(command.<ScoreHolderArgument>get("source").selectSingle())) {
                                try(var targetScore = context.getScoreData(targetObjective.content(), targetName.content(), false)) {
                                    try(var sourceScore = context.getScoreData(sourceObjective.content(), sourceName.content(), false)) {
                                        var instr = Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), command.get("comparison"));
                                        try(var ignored = context.enterWithoutValueUsingInstruction(instr)) {
                                            compile(context, command.get("command"), result);
                                        }
                                        sourceScore.writeInstruction(instr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case EXECUTE_ON_VEHICLE -> {
                try(var currentEntity = context.getCurrentEntity()) {
                    var vehicle = LocationData.<Entity>ofLocal(currentEntity.data().highestId());
                    var ifNotNull = Instructions2.ifNotNull(vehicle.lower());
                    var instruction = Instructions2.getEntityVehicle(currentEntity.content(), vehicle).with(ifNotNull);
                    try(var ignored = context.enterWithoutValue(currentEntity.data().incrementedId(1).withCurrentEntity(vehicle).withBaseInstruction(ifNotNull))) {
                        compile(context, command.get("command"), result);
                    }
                    currentEntity.writeInstruction(instruction);
                }
            }
            case EXECUTE_POSITIONED_AS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var position = LocationData.<Vec3>ofLocal(targets.data().highestId());
                    var instruction = Instructions2.getEntityPosition(targets.content(), position);
                    try(var ignored = context.enterWithoutValue(targets.data().incrementedId(1).withCurrentPosition(position).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    targets.writeInstruction(instruction);
                }
            }
            case EXECUTE_POSITIONED_POS -> {
                try(var position = context.applyPosition(command.get("pos"))) {
                    try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()).withCurrentAnchor(LocationAnchor.FEET))) {
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_ROTATED_AS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var rotation = LocationData.<Vec2>ofLocal(targets.data().highestId());
                    var instruction = Instructions2.getEntityRotation(targets.content(), rotation);
                    try(var ignored = context.enterWithoutValue(targets.data().incrementedId(1).withCurrentRotation(rotation).withBaseInstruction(instruction))) {
                        compile(context, command.get("command"), result);
                    }
                    targets.writeInstruction(instruction);
                }
            }
            case EXECUTE_ROTATED_ROT -> {
                try(var rotation = context.applyRotation(command.get("rot"))) {
                    try(var ignored = context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_RUN -> {
                compile(context, command.get("command"), result);
            }
            case EXECUTE_STORE_RESULT_SCORE -> {
                try(var res = context.withResultStorage(true, false)) {
                    compile(context, command.get("command"), res.content());
                    res.content().checkRequirements(command.get("command"));
                    try(var objective = context.getObjective(command.get("objective"))) {
                        try(var name = context.forScoreHolder(command.get("targets"))) {
                            try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                                scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), res.content().result));
                            }
                        }
                    }
                }
            }
            case EXECUTE_STORE_RESULT_STORAGE -> {
                if(command.get("store_type") == ResultStoreType.INT) {
                    if(command.<Double>get("scale") == 1.0d) {
                        try(var res = context.withResultStorage(true, false)) {
                            compile(context, command.get("command"), res.content());
                            res.content().checkRequirements(command.get("command"));

                            var storageLocal = LocationData.<NbtCompound>ofLocal(context.current().highestId());
                            var setCountLocal = LocationData.<Integer>ofLocal(context.current().highestId() + 1);

                            var stacker = new InstructionStacker(Instructions2.always());

                            stacker.stackInstruction(Instructions2.getNbtStorage(command.get("storage"), storageLocal));
                            var resultStorage = Instructions2.createResultStorage(setCountLocal);
                            stacker.stackInstruction(resultStorage);

                            var parts = command.<NbtPath>get("path").parts();
                            try(var ignored = context.enterWithoutValue(context.current().incrementedId(2).withBaseInstruction(resultStorage))) {
                                getNbtPathValue(context, storageLocal.forceCast(), parts.subList(0, parts.size() - 1), true, (value) -> {
                                    var last = parts.get(parts.size() - 1);
                                    if(last instanceof NbtPathPart.Named named) {
                                        var tagLocal = LocationData.<NbtTag>ofLocal(context.current().highestId());
                                        context.writeInstruction(Instructions2.createIntTag(res.content().result, tagLocal).with(Instructions2.setNbtInCompound(value, named.name(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                                    }else if(last instanceof NbtPathPart.ArrayIndexInteger indexed) {
                                        var tagLocal = LocationData.<NbtTag>ofLocal(context.current().highestId());
                                        if(indexed.index() >= 0) {
                                            context.writeInstruction(Instructions2.createIntTag(res.content().result, tagLocal).with(Instructions2.setNbtInListForward(value, indexed.index(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                                        }else {
                                            context.writeInstruction(Instructions2.createIntTag(res.content().result, tagLocal).with(Instructions2.setNbtInListBackward(value, -indexed.index(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                                        }
                                    }else {
                                        throw new UnsupportedOperationException("Cannot set nbt value from path " + last);
                                    }
                                });
                            }
                            stacker.stackInstruction(Instructions2.isNotResultStorage(setCountLocal, 0));
                            try(var data = context.enterWithoutValue(context.current().incrementedId(2).withBaseInstruction(stacker.current))) {
                                data.writeInstruction(Instructions2.setNbtStorage(command.get("storage"), storageLocal));
                            }

                            context.writeInstruction(stacker.base);
                        }
                    }else {
                        throw new UnsupportedOperationException("Storing into storage with a non 1.0 scale is not yet implemented");
                    }
                }else {
                    throw new UnsupportedOperationException("Storing into storage with a non-int type is not yet implemented");
                }
            }
            case EXECUTE_UNLESS_ENTITY -> {
                try(var scope = context.forScope()) {
                    try(var selector = context.forSelector(command.<SelectorArgument>get("entities").limit(1))) {
                        selector.writeInstruction(Instructions2.exitScope(scope.content()));
                    }
                    compile(context, command.get("command"), result);
                }
            }
            case EXECUTE_UNLESS_SCORE_RANGE -> {
                try(var objective = context.getObjective(command.get("targetObjective"))) {
                    try(var scope = context.forScope()) {
                        try(var name = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                            try(var scoreData = context.getScoreData(objective.content(), name.content(), false)) {
                                var scoreValue = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                                scoreData.writeInstruction(Instructions2.getScoreValue(scoreData.content(), scoreValue).with(Instructions.checkIntRange(scoreValue, command.get("range")).with(Instructions2.exitScope(scope.content()))));
                            }
                        }
                        compile(context, command.get("command"), result);
                    }
                }
            }
            case EXECUTE_UNLESS_SCORE_COMPARE -> {
                try(var targetObjective = context.getObjective(command.get("targetObjective"))) {
                    try(var sourceObjective = context.getObjective(command.get("sourceObjective"))) {
                        try(var scope = context.forScope()) {
                            try(var targetName = context.forScoreHolder(command.<ScoreHolderArgument>get("target").selectSingle())) {
                                try(var sourceName = context.forScoreHolder(command.<ScoreHolderArgument>get("source").selectSingle())) {
                                    try(var targetScore = context.getScoreData(targetObjective.content(), targetName.content(), false)) {
                                        try(var sourceScore = context.getScoreData(sourceObjective.content(), sourceName.content(), false)) {
                                            sourceScore.writeInstruction(Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), command.<ScoreboardCompareOperation>get("comparison")).with(Instructions2.exitScope(scope.content())));
                                        }
                                    }
                                }
                            }
                            compile(context, command.get("command"), result);
                        }
                    }
                }
            }
            case EXPERIENCE_ADD, EXPERIENCE_ADD_POINTS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                            Instructions2.tryAsPlayer(targets.content(), playerLocation)
                                    .with(Instructions2.addExperiencePoints(playerLocation, command.get("amount"))));
                }
            }
            case EXPERIENCE_ADD_LEVELS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                            Instructions2.tryAsPlayer(targets.content(), playerLocation)
                                    .with(Instructions2.addExperienceLevels(playerLocation, command.get("amount"))));
                }
            }
            case EXPERIENCE_SET, EXPERIENCE_SET_POINTS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                            Instructions2.tryAsPlayer(targets.content(), playerLocation)
                                    .with(Instructions2.setExperiencePoints(playerLocation, command.get("amount"))));
                }
            }
            case EXPERIENCE_SET_LEVELS -> {
                try(var targets = context.forSelector(command.get("targets"))) {
                    var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                            Instructions2.tryAsPlayer(targets.content(), playerLocation)
                                    .with(Instructions2.setExperienceLevels(playerLocation, command.get("amount"))));
                }
            }
            case FUNCTION -> {
                var current = context.current();
                current.writeInstruction(Instructions.callFunctions(command.get("functions"), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), null));
            }
            case FUNCTION_ARGS -> {
                var current = context.current();
                var arguments = LocationData.<NbtCompound>ofLocal(current.highestId());
                current.writeInstruction(Instructions2.loadNbtValue(command.get("arguments"), arguments.forceCast()).with(Instructions.callFunctions(command.get("functions"), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), arguments)));
            }
            case FUNCTION_ARGS_STORAGE -> {
                var current = context.current();
                var arguments = LocationData.<NbtCompound>ofLocal(current.highestId());
                var clonedArguments = LocationData.<NbtTag>ofLocal(current.highestId() + 1);
                var validArguments = LocationData.<NbtCompound>ofLocal(current.highestId() + 2);
                current.writeInstruction(Instructions2.getNbtStorage(command.get("storage"), arguments).with(Instructions2.cloneNbtValue(arguments.forceCast(), clonedArguments).with(Instructions2.checkIsCompound(clonedArguments, validArguments).with(Instructions.callFunctions(command.get("functions"), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), validArguments)))));
            }
            case FUNCTION_ARGS_STORAGE_PATH -> {
                var current = context.current();
                var scope = ScopeData.ofId(current.highestId());
                var currentTag = LocationData.<NbtTag>ofLocal(current.highestId() + 1);
                var storageLocal = LocationData.<NbtCompound>ofLocal(context.current().highestId() + 2);
                var stacker = new InstructionStacker(Instructions2.createNullTag(currentTag));
                stacker.stackInstruction(Instructions2.scope(scope));
                stacker.stackInstruction(Instructions2.getNbtStorage(command.get("storage"), storageLocal));

                var parts = command.<NbtPath>get("path").parts();
                try(var ignored = context.enterWithoutValue(context.current().incrementedId(3).withBaseInstruction(stacker.current))) {
                    getNbtPathValue(context, storageLocal.forceCast(), parts, false, (value) -> {
                        context.writeInstruction(Instructions2.ifNotNull(currentTag.lower()).with(Instructions2.exitScope(scope)));
                        context.writeInstruction(Instructions2.setNbtValue(value, currentTag));
                    });
                }
                stacker.stackInstruction(Instructions2.ifNotNull(currentTag.lower()));
                var clonedTag = LocationData.<NbtTag>ofLocal(current.highestId() + 3);
                stacker.stackInstruction(Instructions2.cloneNbtValue(currentTag, clonedTag));
                var compoundTag = LocationData.<NbtCompound>ofLocal(current.highestId() + 4);
                stacker.stackInstruction(Instructions2.checkIsCompound(clonedTag, compoundTag));
                stacker.stackInstruction(Instructions.callFunctions(command.get("functions"), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), compoundTag));
                current.writeInstruction(stacker.base);
            }
            case GAMEMODE -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    var playerLocation = LocationData.<Player>ofLocal(selector.data().highestId());
                    selector.writeInstruction(Instructions2.tryAsPlayer(selector.content(), playerLocation).with(Instructions2.setGamemode(playerLocation, command.get("gamemode"))));
                }
            }
            case KILL -> {
                try(var selector = context.forSelector(command.get("targets"))) {
                    selector.writeInstruction(Instructions2.killEntity(selector.content()));
                }
            }
            case RETURN_FAIL -> {
                context.writeInstruction(Instructions2.returnFail());
            }
            case RETURN_VALUE -> {
                context.writeInstruction(Instructions2.returnSuccess(LocationData.ofInteger(command.get("value"))));
            }
            case SCOREBOARD_PLAYERS_ADD -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            scoreData.writeInstruction(Instructions2.addScore(scoreData.content(), LocationData.ofInteger(command.get("score"))));
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_ENABLE -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    var criterion = LocationData.<ScoreboardCriterion>ofLocal(objective.data().highestId());
                    var execInstr = Instructions2.checkIsCriterion(criterion, DataRegistries.SCOREBOARD_CRITERIA.getOrThrow("trigger"));
                    objective.writeInstruction(Instructions2.getObjectiveCriterion(objective.content(), criterion).with(execInstr));
                    try(var ignored = context.enterWithoutValue(objective.data().withBaseInstruction(execInstr))) {
                        try(var name = context.forScoreHolder(command.get("targets"))) {
                            try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                                scoreData.writeInstruction(Instructions2.scoreUnlock(scoreData.content()));
                            }
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_GET -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            var res = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                            var instr = Instructions2.getScoreValue(scoreData.content(), res);
                            result.setResult(scoreData.data().incrementedId(1).withBaseInstruction(instr), res);
                            scoreData.writeInstruction(instr);
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_OPERATION -> {
                try(var exceptionScope = context.forScope()) {
                    try(var targetObjective = context.getObjective(command.get("targetObjective"))) {
                        try(var targetName = context.forScoreHolder(command.get("targets"))) {
                            try(var sourceObjective = context.getObjective(command.get("sourceObjective"))) {
                                try(var sourceName = context.forScoreHolder(command.get("source"))) {
                                    try(var targetScore = context.getScoreData(targetObjective.content(), targetName.content(), true)) {
                                        try(var sourceScore = context.getScoreData(sourceObjective.content(), sourceName.content(), true)) {
                                            switch(command.<ScoreboardOperation>get("operation")) {
                                                case SET -> sourceScore.writeInstruction(Instructions2.runScoreOperationSet(targetScore.content(), sourceScore.content()));
                                                case ADD -> sourceScore.writeInstruction(Instructions2.runScoreOperationPlus(targetScore.content(), sourceScore.content()));
                                                case SUB -> sourceScore.writeInstruction(Instructions2.runScoreOperationMinus(targetScore.content(), sourceScore.content()));
                                                case MUL -> sourceScore.writeInstruction(Instructions2.runScoreOperationMultiply(targetScore.content(), sourceScore.content()));
                                                case DIV -> sourceScore.writeInstruction(Instructions2.runScoreOperationDivide(targetScore.content(), sourceScore.content(), exceptionScope.content()));
                                                case MOD -> sourceScore.writeInstruction(Instructions2.runScoreOperationModulo(targetScore.content(), sourceScore.content(), exceptionScope.content()));
                                                case MIN -> sourceScore.writeInstruction(Instructions2.runScoreOperationMin(targetScore.content(), sourceScore.content()));
                                                case MAX -> sourceScore.writeInstruction(Instructions2.runScoreOperationMax(targetScore.content(), sourceScore.content()));
                                                case SWAP -> sourceScore.writeInstruction(Instructions2.runScoreOperationSwap(targetScore.content(), sourceScore.content()));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_REMOVE -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            scoreData.writeInstruction(Instructions2.removeScore(scoreData.content(), LocationData.ofInteger(command.get("score"))));
                        }
                    }
                }
            }
            case SCOREBOARD_PLAYERS_RESET_ALL -> {
                try(var name = context.forScoreHolder(command.get("targets"))) {
                    name.writeInstruction(Instructions2.resetPlayerScore(name.content()));
                }
            }
            case SCOREBOARD_PLAYERS_RESET_OBJECTIVE -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        name.writeInstruction(Instructions2.resetScore(objective.content(), name.content()));
                    }
                }
            }
            case SCOREBOARD_PLAYERS_SET -> {
                try(var objective = context.getObjective(command.get("objective"))) {
                    try(var name = context.forScoreHolder(command.get("targets"))) {
                        try(var scoreData = context.getScoreData(objective.content(), name.content(), true)) {
                            scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), LocationData.ofInteger(command.get("score"))));
                        }
                    }
                }
            }
            case TAG_ADD -> {
                try(var entity = context.forSelector(command.get("targets"))) {
                    entity.writeInstruction(Instructions2.addEntityTag(entity.content(), command.get("name")));
                }
            }
            case TAG_REMOVE -> {
                try(var entity = context.forSelector(command.get("targets"))) {
                    entity.writeInstruction(Instructions2.removeEntityTag(entity.content(), command.get("name")));
                }
            }
            case WEATHER_CLEAR_TIMED -> {
                context.writeInstruction(Instructions2.setWeatherStatic(command.get("duration"), 0, false, false));
            }
            case WEATHER_CLEAR -> {
                context.writeInstruction(Instructions2.setWeatherDynamicClear());
            }
            case WEATHER_RAIN_TIMED -> {
                context.writeInstruction(Instructions2.setWeatherStatic(0, command.get("duration"), true, false));
            }
            case WEATHER_RAIN -> {
                context.writeInstruction(Instructions2.setWeatherDynamicRain());
            }
            case WEATHER_THUNDER_TIMED -> {
                context.writeInstruction(Instructions2.setWeatherStatic(0, command.get("duration"), true, true));
            }
            case WEATHER_THUNDER -> {
                context.writeInstruction(Instructions2.setWeatherDynamicThunder());
            }
            default -> throw new UnsupportedOperationException("Not implemented command: " + command.type());
        }
    }

    private static void getNbtPathValue(CompilationContext context, LocationData<NbtTag> source, List<NbtPathPart> parts, boolean tryCreate, Consumer<LocationData<NbtTag>> valueConsumer) {
        if(!parts.isEmpty()) {
            var currentPart = parts.get(0);
            var otherParts = parts.subList(1, parts.size());
            if(currentPart instanceof NbtPathPart.Named named) {
                var storage = LocationData.<NbtTag>ofLocal(context.current().highestId());
                var instr = tryCreate
                        ? Instructions2.getNbtFromCompoundOrCreate(source, named.name(), storage)
                        : Instructions2.getNbtFromCompound(source, named.name(), storage);
                context.writeInstruction(instr);
                try(var ignored = context.enterWithoutValue(context.current().incrementedId(1).withBaseInstruction(instr))) {
                    getNbtPathValue(context, storage, otherParts, tryCreate, valueConsumer);
                }
            }else if(currentPart instanceof NbtPathPart.ArrayIndexInteger indexed) {
                var storage = LocationData.<NbtTag>ofLocal(context.current().highestId());
                var instr = indexed.index() >= 0
                        ? Instructions2.getNbtFromListForward(source, indexed.index(), storage)
                        : Instructions2.getNbtFromListBackward(source, -indexed.index(), storage);
                context.writeInstruction(instr);
                try(var ignored = context.enterWithoutValue(context.current().incrementedId(1).withBaseInstruction(instr))) {
                    getNbtPathValue(context, storage, otherParts, tryCreate, valueConsumer);
                }
            }else {
                throw new UnsupportedOperationException("Cannot get nbt path from " + currentPart);
            }
        }else {
            valueConsumer.accept(source);
        }
    }

}
