package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.*;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(List<CommandParams> commands) {
        ArrayList<Instruction> instructions = new ArrayList<>();
        var context = new CompilationContext(CompilationContextData.create(instructions::add));
        for(var command : commands) {
            compile(context, command, ResultStorage.empty());
        }
        context.exit();
        return instructions;
    }

    private static void compile(CompilationContext context, CommandParams command, ResultStorage result) {
        if(command instanceof CommandAttributeBaseSet cmdData) {
            try(var selector = context.forSelector(cmdData.target())) {
                var livingEntityLocation = LocationData.<LivingEntity>ofLocal(selector.data().highestId());
                var attrLocation = LocationData.<AttributeInstance>ofLocal(selector.data().highestId() + 1);
                var instruction =
                    Instructions2.tryAsLivingEntity(selector.content(), livingEntityLocation)
                        .with(Instructions2.getAttributeInstance(livingEntityLocation, attrLocation, cmdData.attribute().location())
                            .with(Instructions2.ifNotNull(attrLocation.lower())
                                .with(Instructions2.setAttributeBase(attrLocation, cmdData.value()))
                            ));
                selector.writeInstruction(instruction);

            }
        }else if(command instanceof CommandDataModifyStorageSetValue cmdData) {
            var storageLocal = LocationData.<NbtCompound>ofLocal(context.current().highestId());
            var setCountLocal = LocationData.<Integer>ofLocal(context.current().highestId() + 1);

            var stacker = new InstructionStacker(Instructions2.always());

            stacker.stackInstruction(Instructions2.getNbtStorage(cmdData.storage(), storageLocal));
            var resultStorage = Instructions2.createResultStorage(setCountLocal);
            stacker.stackInstruction(resultStorage);

            var parts = cmdData.path().parts();
            try(var ignored = context.enterWithoutValue(context.current().incrementedId(2).withBaseInstruction(resultStorage))) {
                getNbtPathValue(context, storageLocal.forceCast(), parts.subList(0, parts.size() - 1), true, (value) -> {
                    var last = parts.get(parts.size() - 1);
                    if(last instanceof NbtPathPart.Named named) {
                        var tagLocal = LocationData.<NbtTag>ofLocal(context.current().highestId());
                        context.writeInstruction(Instructions2.loadNbtValue(cmdData.value(), tagLocal).with(Instructions2.setNbtInCompound(value, named.name(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                    }else if(last instanceof NbtPathPart.ArrayIndexInteger indexed) {
                        var tagLocal = LocationData.<NbtTag>ofLocal(context.current().highestId());
                        if(indexed.index() >= 0) {
                            context.writeInstruction(Instructions2.loadNbtValue(cmdData.value(), tagLocal).with(Instructions2.setNbtInListForward(value, indexed.index(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                        }else {
                            context.writeInstruction(Instructions2.loadNbtValue(cmdData.value(), tagLocal).with(Instructions2.setNbtInListBackward(value, -indexed.index(), tagLocal).with(Instructions2.addResultStorage(setCountLocal, LocationData.ofInteger(1)))));
                        }
                    }else {
                        throw new UnsupportedOperationException("Cannot set nbt value from path " + last);
                    }
                });
            }
            stacker.stackInstruction(Instructions2.isNotResultStorage(setCountLocal, 0));
            try(var data = context.enterWithoutValue(context.current().incrementedId(2).withBaseInstruction(stacker.current))) {
                data.writeInstruction(Instructions2.setNbtStorage(cmdData.storage(), storageLocal));
            }

            context.writeInstruction(stacker.base);
        }else if(command instanceof CommandDifficultySet cmdData) {
            context.writeInstruction(Instructions2.setDifficulty(cmdData.difficulty()));
        }else if(command instanceof CommandEffectClear) {
            try(var self = context.getCurrentEntity()) {
                var livingEntityLocation = LocationData.<LivingEntity>ofLocal(self.data().highestId());
                self.writeInstruction(
                    Instructions2.ifNotNull(self.content().lower())
                        .with(Instructions2.tryAsLivingEntity(self.content(), livingEntityLocation)
                            .with(Instructions2.clearAllEffects(livingEntityLocation))));
            }
        }else if(command instanceof CommandEffectClearEntities cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                        .with(Instructions2.clearAllEffects(livingEntityLocation)));
            }
        }else if(command instanceof CommandEffectClearEntitiesEffect cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                        .with(Instructions2.clearEffect(livingEntityLocation, cmdData.effect().location())));
            }
        }else if(command instanceof CommandEffectGive cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? 1 : 600, 0, true)));
                }
            }
        }else if(command instanceof CommandEffectGiveDur cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), 0, true)));
                }
            }
        }else if(command instanceof CommandEffectGiveDurAmp cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), cmdData.amplifier(), true)));
                }
            }
        }else if(command instanceof CommandEffectGiveDurAmpShow cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), cmdData.amplifier(), !cmdData.hideParticles())));
                }
            }
        }else if(command instanceof CommandEffectGiveInf cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, 0, true)));
                }
            }
        }else if(command instanceof CommandEffectGiveInfAmp cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, cmdData.amplifier(), true)));
                }
            }
        }else if(command instanceof CommandEffectGiveInfAmpShow cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = LocationData.<LivingEntity>ofLocal(targets.data().highestId());
                    targets.writeInstruction(
                        Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation)
                            .with(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, cmdData.amplifier(), !cmdData.hideParticles())));
                }
            }
        }else if(command instanceof CommandExecuteAlign cmdData) {
            try(var pos = context.getPosition()) {
                var newPosition = LocationData.<Vec3>ofLocal(pos.data().highestId());
                var instruction = Instructions.alignPosition(pos.content(), newPosition, cmdData.swizzle());
                try(var ignored = context.enterWithoutValue(pos.data().incrementedId(1).withCurrentPosition(newPosition).withBaseInstruction(instruction))) {
                    compile(context, cmdData.command(), result);
                }
                pos.writeInstruction(instruction);
            }
        }else if(command instanceof CommandExecuteAnchored cmdData) {
            try(var ignored = context.enterWithoutValue(context.current().withCurrentAnchor(cmdData.anchor()))) {
                compile(context, cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteAs cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                try(var ignored = context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                    compile(context, cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteAt cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                var world = LocationData.<World>ofLocal(selector.data().highestId());
                var position = LocationData.<Vec3>ofLocal(selector.data().highestId() + 1);
                var rotation = LocationData.<Vec2>ofLocal(selector.data().highestId() + 2);
                var execInstruction = Instructions2.getEntityRotation(selector.content(), rotation);
                var instruction = Instructions2.getEntityWorld(selector.content(), world).with(Instructions2.getEntityPosition(selector.content(), position).with(execInstruction));
                try(var ignored = context.enterWithoutValue(selector.data().incrementedId(3).withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world).withBaseInstruction(execInstruction))) {
                    compile(context, cmdData.command(), result);
                }
                selector.writeInstruction(instruction);
            }
        }else if(command instanceof CommandExecuteIfEntity cmdData) {
            try(var ignored = context.forSelector(cmdData.entities().limit(1))) {
                compile(context, cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteIfScoreRange cmdData) {
            try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle())) {
                try(var scoreData = context.getScoreData(scoreAccessor.content(), false)) {
                    var scoreValue = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                    var execInstruction = Instructions.checkIntRange(scoreValue, cmdData.range());
                    var baseInstruction = Instructions2.getScoreValue(scoreData.content(), scoreValue).with(execInstruction);
                    try(var ignored = context.enterWithoutValue(scoreData.data().incrementedId(1).withBaseInstruction(execInstruction))) {
                        compile(context, cmdData.command(), result);
                    }
                    scoreData.writeInstruction(baseInstruction);
                }
            }
        }else if(command instanceof CommandExecuteIfScoreCompare cmdData) {
            try(var target = context.forScoreAccessors(cmdData.target().selectSingle())) {
                try(var source = context.forScoreAccessors(cmdData.source().selectSingle())) {
                    try(var targetScore = context.getScoreData(target.content(), false)) {
                        try(var sourceScore = context.getScoreData(source.content(), false)) {
                            var instr = Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), cmdData.comparison());
                            try(var ignored = context.enterWithoutValueUsingInstruction(instr)) {
                                compile(context, cmdData.command(), result);
                            }
                            sourceScore.writeInstruction(instr);
                        }
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnVehicle cmdData) {
            try(var currentEntity = context.getCurrentEntity()) {
                var vehicle = LocationData.<Entity>ofLocal(currentEntity.data().highestId());
                var ifNotNull = Instructions2.ifNotNull(vehicle.lower());
                var instruction = Instructions2.getEntityVehicle(currentEntity.content(), vehicle).with(ifNotNull);
                try(var ignored = context.enterWithoutValue(currentEntity.data().incrementedId(1).withCurrentEntity(vehicle).withBaseInstruction(ifNotNull))) {
                    compile(context, cmdData.command(), result);
                }
                currentEntity.writeInstruction(instruction);
            }
        }else if(command instanceof CommandExecutePositionedAs cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var position = LocationData.<Vec3>ofLocal(targets.data().highestId());
                var instruction = Instructions2.getEntityPosition(targets.content(), position);
                try(var ignored = context.enterWithoutValue(targets.data().incrementedId(1).withCurrentPosition(position).withBaseInstruction(instruction))) {
                    compile(context, cmdData.command(), result);
                }
                targets.writeInstruction(instruction);
            }
        }else if(command instanceof CommandExecutePositionedPos cmdData) {
            try(var position = context.applyPosition(cmdData.pos())) {
                try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()).withCurrentAnchor(LocationAnchor.FEET))) {
                    compile(context, cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteRotatedAs cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var rotation = LocationData.<Vec2>ofLocal(targets.data().highestId());
                var instruction = Instructions2.getEntityRotation(targets.content(), rotation);
                try(var ignored = context.enterWithoutValue(targets.data().incrementedId(1).withCurrentRotation(rotation).withBaseInstruction(instruction))) {
                    compile(context, cmdData.command(), result);
                }
                targets.writeInstruction(instruction);
            }
        }else if(command instanceof CommandExecuteRotatedRot cmdData) {
            try(var rotation = context.applyRotation(cmdData.rot())) {
                try(var ignored = context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                    compile(context, cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteRun cmdData) {
            compile(context, cmdData.command(), result);
        }else if(command instanceof CommandExecuteStoreResultScore cmdData) {
            try(var res = context.withResultStorage(true, false)) {
                compile(context, cmdData.command(), res.content());
                res.content().checkRequirements(cmdData.command());
                try(var scoreAccessor = context.forScoreAccessors(cmdData.targets())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), true)) {
                        scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), res.content().result));
                    }
                }
            }
        }else if(command instanceof CommandExecuteStoreResultStorage cmdData) {
            if(cmdData.storeType() == ResultStoreType.INT) {
                if(cmdData.scale() == 1.0d) {
                    try(var res = context.withResultStorage(true, false)) {
                        compile(context, cmdData.command(), res.content());
                        res.content().checkRequirements(cmdData.command());

                        var storageLocal = LocationData.<NbtCompound>ofLocal(context.current().highestId());
                        var setCountLocal = LocationData.<Integer>ofLocal(context.current().highestId() + 1);

                        var stacker = new InstructionStacker(Instructions2.always());

                        stacker.stackInstruction(Instructions2.getNbtStorage(cmdData.storage(), storageLocal));
                        var resultStorage = Instructions2.createResultStorage(setCountLocal);
                        stacker.stackInstruction(resultStorage);

                        var parts = cmdData.path().parts();
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
                            data.writeInstruction(Instructions2.setNbtStorage(cmdData.storage(), storageLocal));
                        }

                        context.writeInstruction(stacker.base);
                    }
                }else {
                    throw new UnsupportedOperationException("Storing into storage with a non 1.0 scale is not yet implemented");
                }
            }else {
                throw new UnsupportedOperationException("Storing into storage with a non-int type is not yet implemented");
            }
        }else if(command instanceof CommandExecuteUnlessEntity cmdData) {
            try(var scope = context.forScope()) {
                try(var selector = context.forSelector(cmdData.entities().limit(1))) {
                    selector.writeInstruction(Instructions2.exitScope(scope.content()));
                }
                compile(context, cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessScoreRange cmdData) {
            try(var scope = context.forScope()) {
                try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), false)) {
                        var scoreValue = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                        scoreData.writeInstruction(Instructions2.getScoreValue(scoreData.content(), scoreValue).with(Instructions.checkIntRange(scoreValue, cmdData.range()).with(Instructions2.exitScope(scope.content()))));
                    }
                    compile(context, cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteUnlessScoreCompare cmdData) {
            try(var target = context.forScoreAccessors(cmdData.target().selectSingle())) {
                try(var source = context.forScoreAccessors(cmdData.source().selectSingle())) {
                    try(var scope = context.forScope()) {
                        try(var targetScore = context.getScoreData(target.content(), false)) {
                            try(var sourceScore = context.getScoreData(source.content(), false)) {
                                sourceScore.writeInstruction(Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), cmdData.comparison()).with(Instructions2.exitScope(scope.content())));
                            }
                        }
                        compile(context, cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExperienceAdd cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsPlayer(targets.content(), playerLocation)
                        .with(Instructions2.addExperiencePoints(playerLocation, cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceAddPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsPlayer(targets.content(), playerLocation)
                        .with(Instructions2.addExperiencePoints(playerLocation, cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceAddLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsPlayer(targets.content(), playerLocation)
                        .with(Instructions2.addExperienceLevels(playerLocation, cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceSet cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsPlayer(targets.content(), playerLocation)
                        .with(Instructions2.setExperiencePoints(playerLocation, cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceSetPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsPlayer(targets.content(), playerLocation)
                        .with(Instructions2.setExperiencePoints(playerLocation, cmdData.amount())));
            }
        }else if(command instanceof CommandExperienceSetLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(targets.data().highestId());
                targets.writeInstruction(
                    Instructions2.tryAsPlayer(targets.content(), playerLocation)
                        .with(Instructions2.setExperienceLevels(playerLocation, cmdData.amount())));
            }
        }else if(command instanceof CommandFunction cmdData) {
            var current = context.current();
            current.writeInstruction(Instructions.callFunctions(cmdData.functions(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), null));
        }else if(command instanceof CommandFunctionArgs cmdData) {
            var current = context.current();
            var arguments = LocationData.<NbtCompound>ofLocal(current.highestId());
            current.writeInstruction(Instructions2.loadNbtValue(cmdData.arguments(), arguments.forceCast()).with(Instructions.callFunctions(cmdData.functions(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), arguments)));
        }else if(command instanceof CommandFunctionArgsStorage cmdData) {
            var current = context.current();
            var arguments = LocationData.<NbtCompound>ofLocal(current.highestId());
            var clonedArguments = LocationData.<NbtTag>ofLocal(current.highestId() + 1);
            var validArguments = LocationData.<NbtCompound>ofLocal(current.highestId() + 2);
            current.writeInstruction(Instructions2.getNbtStorage(cmdData.storage(), arguments).with(Instructions2.cloneNbtValue(arguments.forceCast(), clonedArguments).with(Instructions2.checkIsCompound(clonedArguments, validArguments).with(Instructions.callFunctions(cmdData.functions(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), validArguments)))));
        }else if(command instanceof CommandFunctionArgsStoragePath cmdData) {
            var current = context.current();
            var scope = ScopeData.ofId(current.highestId());
            var currentTag = LocationData.<NbtTag>ofLocal(current.highestId() + 1);
            var storageLocal = LocationData.<NbtCompound>ofLocal(context.current().highestId() + 2);
            var stacker = new InstructionStacker(Instructions2.createNullTag(currentTag));
            stacker.stackInstruction(Instructions2.scope(scope));
            stacker.stackInstruction(Instructions2.getNbtStorage(cmdData.storage(), storageLocal));

            var parts = cmdData.path().parts();
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
            stacker.stackInstruction(Instructions.callFunctions(cmdData.functions(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor(), compoundTag));
            current.writeInstruction(stacker.base);
        }else if(command instanceof CommandGamemode cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                var playerLocation = LocationData.<Player>ofLocal(selector.data().highestId());
                selector.writeInstruction(Instructions2.tryAsPlayer(selector.content(), playerLocation).with(Instructions2.setGamemode(playerLocation, cmdData.gamemode())));
            }
        }else if(command instanceof CommandKill cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                selector.writeInstruction(Instructions2.killEntity(selector.content()));
            }
        }else if(command instanceof CommandReturnFail) {
            context.writeInstruction(Instructions2.returnFail());
        }else if(command instanceof CommandReturnValue cmdData) {
            context.writeInstruction(Instructions2.returnSuccess(LocationData.ofInteger(cmdData.value())));
        }else if(command instanceof CommandScoreboardPlayersAdd cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets())) {
                try(var scoreData = context.getScoreData(targets.content(), true)) {
                    scoreData.writeInstruction(Instructions2.addScore(scoreData.content(), LocationData.ofInteger(cmdData.score())));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersEnable cmdData) {
            try(var objective = context.getObjective(cmdData.targets().objective())) {
                var criterion = LocationData.<ScoreboardCriterion>ofLocal(objective.data().highestId());
                var execInstr = Instructions2.checkIsCriterion(criterion, DataRegistries.SCOREBOARD_CRITERIA.getOrThrow("trigger"));
                objective.writeInstruction(Instructions2.getObjectiveCriterion(objective.content(), criterion).with(execInstr));
                try(var ignored = context.enterWithoutValue(objective.data().withBaseInstruction(execInstr))) {
                    try(var name = context.forScoreHolder(cmdData.targets().scoreHolder())) {
                        try(var scoreData = context.getScoreData(new ScoreAccessor(objective.content(), name.content()), true)) {
                            scoreData.writeInstruction(Instructions2.scoreUnlock(scoreData.content()));
                        }
                    }
                }
            }
        }else if(command instanceof CommandScoreboardPlayersGet cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets())) {
                try(var scoreData = context.getScoreData(targets.content(), true)) {
                    var res = LocationData.<Integer>ofLocal(scoreData.data().highestId());
                    var instr = Instructions2.getScoreValue(scoreData.content(), res);
                    result.setResult(scoreData.data().incrementedId(1).withBaseInstruction(instr), res);
                    scoreData.writeInstruction(instr);
                }
            }
        }else if(command instanceof CommandScoreboardPlayersOperation cmdData) {
            try(var exceptionScope = context.forScope()) {
                try(var targets = context.forScoreAccessors(cmdData.targets())) {
                    try(var source = context.forScoreAccessors(cmdData.source())) {
                        try(var targetScore = context.getScoreData(targets.content(), true)) {
                            try(var sourceScore = context.getScoreData(source.content(), true)) {
                                switch(cmdData.operation()) {
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
        }else if(command instanceof CommandScoreboardPlayersRemove cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets())) {
                try(var scoreData = context.getScoreData(targets.content(), true)) {
                    scoreData.writeInstruction(Instructions2.removeScore(scoreData.content(), LocationData.ofInteger(cmdData.score())));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersResetAll cmdData) {
            try(var name = context.forScoreHolder(cmdData.targets())) {
                name.writeInstruction(Instructions2.resetPlayerScore(name.content()));
            }
        }else if(command instanceof CommandScoreboardPlayersResetObjective cmdData) {
            try(var objective = context.getObjective(cmdData.targets().objective())) {
                try(var name = context.forScoreHolder(cmdData.targets().scoreHolder())) {
                    name.writeInstruction(Instructions2.resetScore(objective.content(), name.content()));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersSet cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets())) {
                try(var scoreData = context.getScoreData(targets.content(), true)) {
                    scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), LocationData.ofInteger(cmdData.score())));
                }
            }
        }else if(command instanceof CommandTagAdd cmdData) {
            try(var entity = context.forSelector(cmdData.targets())) {
                entity.writeInstruction(Instructions2.addEntityTag(entity.content(), LocationData.ofString(cmdData.name())));
            }
        }else if(command instanceof CommandTagRemove cmdData) {
            try(var entity = context.forSelector(cmdData.targets())) {
                entity.writeInstruction(Instructions2.removeEntityTag(entity.content(), LocationData.ofString(cmdData.name())));
            }
        }else if(command instanceof CommandTitleActionbar cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                var textLocation = LocationData.<TextComponent>ofLocal(players.data().highestId() + 1);
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.loadText(cmdData.text(), textLocation)
                            .with(Instructions2.sendActionbar(playerLocation, textLocation))));
            }
        }else if(command instanceof CommandTitleClear cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.sendTitlesClear(playerLocation)));
            }
        }else if(command instanceof CommandTitleReset cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.sendTitlesReset(playerLocation)));
            }
        }else if(command instanceof CommandTitleSubtitle cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                var textLocation = LocationData.<TextComponent>ofLocal(players.data().highestId() + 1);
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.loadText(cmdData.text(), textLocation)
                            .with(Instructions2.sendSubtitle(playerLocation, textLocation))));
            }
        }else if(command instanceof CommandTitleTimes cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.sendTitlesTimes(playerLocation, cmdData.fadeIn(), cmdData.stay(), cmdData.fadeOut())));
            }
        }else if(command instanceof CommandTitleTitle cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                var textLocation = LocationData.<TextComponent>ofLocal(players.data().highestId() + 1);
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.loadText(cmdData.text(), textLocation)
                            .with(Instructions2.sendTitle(playerLocation, textLocation))));
            }
        }else if(command instanceof CommandTellraw cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = LocationData.<Player>ofLocal(players.data().highestId());
                var textLocation = LocationData.<TextComponent>ofLocal(players.data().highestId() + 1);
                players.writeInstruction(
                    Instructions2.tryAsPlayer(players.content(), playerLocation)
                        .with(Instructions2.loadText(cmdData.text(), textLocation)
                            .with(Instructions2.sendSystemMessage(playerLocation, textLocation))));
            }
        }else if(command instanceof CommandWeatherClearTimed cmdData) {
            context.writeInstruction(Instructions2.setWeatherStatic(cmdData.duration(), 0, false, false));
        }else if(command instanceof CommandWeatherClear) {
            context.writeInstruction(Instructions2.setWeatherDynamicClear());
        }else if(command instanceof CommandWeatherRainTimed cmdData) {
            context.writeInstruction(Instructions2.setWeatherStatic(0, cmdData.duration(), true, false));
        }else if(command instanceof CommandWeatherRain) {
            context.writeInstruction(Instructions2.setWeatherDynamicRain());
        }else if(command instanceof CommandWeatherThunderTimed cmdData) {
            context.writeInstruction(Instructions2.setWeatherStatic(0, cmdData.duration(), true, true));
        }else if(command instanceof CommandWeatherThunder) {
            context.writeInstruction(Instructions2.setWeatherDynamicThunder());
        }else {
            throw new UnsupportedOperationException("Not implemented command: " + command);
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
