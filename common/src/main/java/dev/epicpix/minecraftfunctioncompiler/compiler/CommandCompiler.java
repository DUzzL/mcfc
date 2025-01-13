package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.*;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.List;

public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(List<CommandParams> commands) {
        var context = new CompilationContext();
        for(var command : commands) {
            var commandLabel = context.current().writeLabel(context.instructionWriter);
            try(var ignored = context.enterWithoutValue(context.current().withContextLabel(commandLabel))) {
                compile(context, null, command, ResultStorage.empty());
            }
        }
        return context.end();
    }

    private static void compile(CompilationContext context, ForkingContext forkingContext, CommandParams command, ResultStorage result) {
        if(command instanceof CommandAttributeBaseSet cmdData) {
            try(var selector = context.forSelector(cmdData.target())) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                var attrLocation = context.<AttributeInstance>createLocal();
                selector.writeInstruction(Instructions2.tryAsLivingEntity(selector.content(), livingEntityLocation, selector.data().contextEndLabel()));
                selector.writeInstruction(Instructions2.getAttributeInstance(livingEntityLocation, attrLocation, cmdData.attribute().location()));
                selector.writeInstruction(Instructions2.ifNullJump(attrLocation.lower(), selector.data().contextEndLabel()));
                selector.writeInstruction(Instructions2.setAttributeBase(attrLocation, cmdData.value()));
            }
        }else if(command instanceof CommandDataModifyStorageSetValue cmdData) {
            var storageLocal = context.<NbtCompound>createLocal();
            var setCountLocal = context.<Integer>createLocal();

            context.writeInstruction(Instructions2.getNbtStorage(cmdData.storage(), storageLocal));
            context.writeInstruction(Instructions2.valueDefine(setCountLocal, LocationData.ofInteger(0)));

            var parts = cmdData.path().parts();

            var codeLocation = context.current().writeLabel(context.instructionWriter);
            try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                var value = getNbtPathValue(context, storageLocal.forceCast(), parts.subList(0, parts.size() - 1), true, innerContext.data().contextEndLabel());

                var last = parts.get(parts.size() - 1);
                if(last instanceof NbtPathPart.Named named) {
                    var valueCompound = context.<NbtCompound>createLocal();
                    innerContext.writeInstruction(Instructions2.checkIsCompound(value, valueCompound, innerContext.data().contextEndLabel()));

                    var tagLocal = context.<NbtTag>createLocal();
                    innerContext.writeInstruction(Instructions2.loadNbtValue(cmdData.value(), tagLocal));
                    innerContext.writeInstruction(Instructions2.setNbtInCompound(valueCompound, named.name(), tagLocal));
                    innerContext.writeInstruction(Instructions2.valueAdd(setCountLocal, LocationData.ofInteger(1)));
                }else if(last instanceof NbtPathPart.ArrayIndexInteger indexed) {
                    var list = context.<NbtList>createLocal();
                    innerContext.writeInstruction(Instructions2.checkIsList(value, list, innerContext.data().contextEndLabel()));
                    var length = context.<Integer>createLocal();
                    innerContext.writeInstruction(Instructions2.getListLength(list.forceCast(), length));
                    innerContext.writeInstruction(Instructions2.ifGreaterEqualJump(LocationData.ofInteger(indexed.index()), length, innerContext.data().contextEndLabel()));
                    var index = context.<Integer>createLocal();

                    var tagLocal = context.<NbtTag>createLocal();
                    innerContext.writeInstruction(Instructions2.loadNbtValue(cmdData.value(), tagLocal));

                    if(indexed.index() >= 0) {
                        innerContext.writeInstruction(Instructions2.valueDefine(index, LocationData.ofInteger(indexed.index())));
                    }else {
                        innerContext.writeInstruction(Instructions2.valueDefine(index, length));
                        innerContext.writeInstruction(Instructions2.valueSub(index, LocationData.ofInteger(-indexed.index())));
                    }

                    innerContext.writeInstruction(Instructions2.setNbtInList(list, index, tagLocal, innerContext.data().contextEndLabel()));
                    innerContext.writeInstruction(Instructions2.valueAdd(setCountLocal, LocationData.ofInteger(1)));
                }else {
                    throw new UnsupportedOperationException("Cannot set nbt value from path " + last);
                }
            }

            context.writeInstruction(Instructions2.ifEqualJump(setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
            context.writeInstruction(Instructions2.setNbtStorage(cmdData.storage(), storageLocal));
        }else if(command instanceof CommandDifficultySet cmdData) {
            context.writeInstruction(Instructions2.setDifficulty(cmdData.difficulty()));
        }else if(command instanceof CommandEffectClear) {
            try(var self = context.getCurrentEntity()) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                self.writeInstruction(Instructions2.ifNullJump(self.content().lower(), self.data().contextEndLabel()));
                self.writeInstruction(Instructions2.tryAsLivingEntity(self.content(), livingEntityLocation, self.data().contextEndLabel()));
                self.writeInstruction(Instructions2.clearAllEffects(livingEntityLocation));
            }
        }else if(command instanceof CommandEffectClearEntities cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.clearAllEffects(livingEntityLocation));
            }
        }else if(command instanceof CommandEffectClearEntitiesEffect cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.clearEffect(livingEntityLocation, cmdData.effect().location()));
            }
        }else if(command instanceof CommandEffectGive cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? 1 : 600, 0, true));
                }
            }
        }
        else if(command instanceof CommandEffectGiveDur cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), 0, true));
                }
            }
        }else if(command instanceof CommandEffectGiveDurAmp cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), cmdData.amplifier(), true));
                }
            }
        }else if(command instanceof CommandEffectGiveDurAmpShow cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), cmdData.amplifier(), !cmdData.hideParticles()));
                }
            }
        }else if(command instanceof CommandEffectGiveInf cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, 0, true));
                }
            }
        }else if(command instanceof CommandEffectGiveInfAmp cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, cmdData.amplifier(), true));
                }
            }
        }else if(command instanceof CommandEffectGiveInfAmpShow cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(targets.content(), livingEntityLocation, targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, cmdData.amplifier(), !cmdData.hideParticles()));
                }
            }
        }else if(command instanceof CommandExecuteAlign cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var pos = context.getPosition()) {
                    var newPosition = context.<Vec3>createLocal();
                    pos.writeInstruction(Instructions.alignPosition(pos.content(), newPosition, cmdData.swizzle()));
                    try(var ignored = context.enterWithoutValue(pos.data().withCurrentPosition(newPosition))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteAnchored cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var ignored = context.enterWithoutValue(context.current().withCurrentAnchor(cmdData.anchor()))) {
                    compile(context, fContext.content(), cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var selector = context.forSelector(cmdData.targets())) {
                    try(var ignored = context.enterWithoutValue(selector.data().withCurrentEntity(selector.content()))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteAt cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var selector = context.forSelector(cmdData.targets())) {
                    var world = context.<World>createLocal();
                    var position = context.<Vec3>createLocal();
                    var rotation = context.<Vec2>createLocal();
                    selector.writeInstruction(Instructions2.getEntityWorld(selector.content(), world));
                    selector.writeInstruction(Instructions2.getEntityPosition(selector.content(), position));
                    selector.writeInstruction(Instructions2.getEntityRotation(selector.content(), rotation));
                    try(var ignored = context.enterWithoutValue(selector.data().withCurrentPosition(position).withCurrentRotation(rotation).withCurrentWorld(world))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteIfEntity cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var ignored = context.forSelector(cmdData.entities().limit(1))) {
                    compile(context, fContext.content(), cmdData.command(), result);
                }
            }
        }else if(command instanceof CommandExecuteIfScoreRange cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), false)) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions2.getScoreValue(scoreData.content(), scoreValue));
                        context.jumpIfNotMatchesRange(scoreData.data(), scoreValue, cmdData.range(), scoreData.data().contextEndLabel());
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteIfScoreCompare cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var target = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var source = context.forScoreAccessors(cmdData.source().selectSingle())) {
                        try(var targetScore = context.getScoreData(target.content(), false)) {
                            try(var sourceScore = context.getScoreData(source.content(), false)) {
                                sourceScore.writeInstruction(Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), cmdData.comparison(), sourceScore.data().contextEndLabel()));
                                compile(context, fContext.content(), cmdData.command(), result);
                            }
                        }
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnVehicle cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var vehicle = context.<Entity>createLocal();
                    currentEntity.writeInstruction(Instructions2.getEntityVehicle(currentEntity.content(), vehicle));
                    currentEntity.writeInstruction(Instructions2.ifNullJump(vehicle.lower(), currentEntity.data().contextEndLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(vehicle))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecutePositionedAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var position = context.<Vec3>createLocal();
                    targets.writeInstruction(Instructions2.getEntityPosition(targets.content(), position));
                    try(var ignored = context.enterWithoutValue(targets.data().withCurrentPosition(position))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecutePositionedPos cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var position = context.applyPosition(cmdData.pos())) {
                    try(var ignored = context.enterWithoutValue(position.data().withCurrentPosition(position.content()).withCurrentAnchor(LocationAnchor.FEET))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteRotatedAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var rotation = context.<Vec2>createLocal();
                    targets.writeInstruction(Instructions2.getEntityRotation(targets.content(), rotation));
                    try(var ignored = context.enterWithoutValue(targets.data().withCurrentRotation(rotation))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteRotatedRot cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var rotation = context.applyRotation(cmdData.rot())) {
                    try(var ignored = context.enterWithoutValue(rotation.data().withCurrentRotation(rotation.content()))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteRun cmdData) {
            try(var ignored = context.generateForkExecCode(forkingContext)) {
                compile(context, forkingContext, cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteStoreResultScore cmdData) {
            try(var ignored = context.generateForkExecCode(forkingContext)) {
                var res = context.withResultStorage(true, false);
                compile(context, null, cmdData.command(), res);
                res.checkRequirements(cmdData.command());
                try(var scoreAccessor = context.forScoreAccessors(cmdData.targets())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), true)) {
                        scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), res.result));
                    }
                }
            }
        }else if(command instanceof CommandExecuteStoreResultStorage cmdData) {
            try(var ignored = context.generateForkExecCode(forkingContext)) {
                if(cmdData.storeType() == ResultStoreType.INT) {
                    if(cmdData.scale() == 1.0d) {
                        var res = context.withResultStorage(true, false);
                        compile(context, null, cmdData.command(), res);
                        res.checkRequirements(cmdData.command());

                        var storageLocal = context.<NbtCompound>createLocal();
                        var setCountLocal = context.<Integer>createLocal();

                        context.writeInstruction(Instructions2.getNbtStorage(cmdData.storage(), storageLocal));
                        context.writeInstruction(Instructions2.valueDefine(setCountLocal, LocationData.ofInteger(0)));

                        var parts = cmdData.path().parts();

                        var codeLocation = context.current().writeLabel(context.instructionWriter);
                        try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                            var value = getNbtPathValue(context, storageLocal.forceCast(), parts.subList(0, parts.size() - 1), true, innerContext.data().contextEndLabel());

                            var last = parts.get(parts.size() - 1);
                            if(last instanceof NbtPathPart.Named named) {
                                var valueCompound = context.<NbtCompound>createLocal();
                                innerContext.writeInstruction(Instructions2.checkIsCompound(value, valueCompound, innerContext.data().contextEndLabel()));

                                var tagLocal = context.<NbtTag>createLocal();
                                innerContext.writeInstruction(Instructions2.createIntTag(res.result, tagLocal));
                                innerContext.writeInstruction(Instructions2.setNbtInCompound(valueCompound, named.name(), tagLocal));
                                innerContext.writeInstruction(Instructions2.valueAdd(setCountLocal, LocationData.ofInteger(1)));
                            }else if(last instanceof NbtPathPart.ArrayIndexInteger indexed) {
                                var list = context.<NbtList>createLocal();
                                innerContext.writeInstruction(Instructions2.checkIsList(value, list, innerContext.data().contextEndLabel()));
                                var length = context.<Integer>createLocal();
                                innerContext.writeInstruction(Instructions2.getListLength(list.forceCast(), length));
                                innerContext.writeInstruction(Instructions2.ifGreaterEqualJump(LocationData.ofInteger(indexed.index()), length, innerContext.data().contextEndLabel()));
                                var index = context.<Integer>createLocal();

                                var tagLocal = context.<NbtTag>createLocal();
                                innerContext.writeInstruction(Instructions2.createIntTag(res.result, tagLocal));

                                if(indexed.index() >= 0) {
                                    innerContext.writeInstruction(Instructions2.valueDefine(index, LocationData.ofInteger(indexed.index())));
                                }else {
                                    innerContext.writeInstruction(Instructions2.valueDefine(index, length));
                                    innerContext.writeInstruction(Instructions2.valueSub(index, LocationData.ofInteger(-indexed.index())));
                                }

                                innerContext.writeInstruction(Instructions2.setNbtInList(list, index, tagLocal, innerContext.data().contextEndLabel()));
                                innerContext.writeInstruction(Instructions2.valueAdd(setCountLocal, LocationData.ofInteger(1)));
                            }else {
                                throw new UnsupportedOperationException("Cannot set nbt value from path " + last);
                            }
                        }
                        context.writeInstruction(Instructions2.ifEqualJump(setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
                        context.writeInstruction(Instructions2.setNbtStorage(cmdData.storage(), storageLocal));
                    }else {
                        throw new UnsupportedOperationException("Storing into storage with a non 1.0 scale is not yet implemented");
                    }
                }else {
                    throw new UnsupportedOperationException("Storing into storage with a non-int type is not yet implemented");
                }
            }
        }else if(command instanceof CommandExecuteUnlessEntity cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                var codeLocation = context.current().writeLabel(context.instructionWriter);
                try(var ignored = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                    try(var selector = context.forSelector(cmdData.entities().limit(1))) {
                        selector.writeInstruction(Instructions2.jump(contextLabel));
                    }
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessScoreRange cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), false)) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions2.getScoreValue(scoreData.content(), scoreValue));
                        context.jumpIfMatchesRange(scoreData.data(), scoreValue, cmdData.range(), contextLabel);
                    }
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessScoreCompare cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                try(var target = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var source = context.forScoreAccessors(cmdData.source().selectSingle())) {
                        try(var targetScore = context.getScoreData(target.content(), false)) {
                            try(var sourceScore = context.getScoreData(source.content(), false)) {
                                sourceScore.writeInstruction(Instructions.checkScoreCompare(targetScore.content(), sourceScore.content(), cmdData.comparison(), contextLabel));
                            }
                        }
                    }
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExperienceAdd cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(targets.content(), playerLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.addExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceAddPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(targets.content(), playerLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.addExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceAddLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(targets.content(), playerLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.addExperienceLevels(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceSet cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(targets.content(), playerLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.setExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceSetPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(targets.content(), playerLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.setExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceSetLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(targets.content(), playerLocation, targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.setExperienceLevels(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandFunction cmdData) {
            var current = context.current();
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(current.commandSourceStack(), stack, current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(cmdData.functions(), stack, null));
        }else if(command instanceof CommandFunctionArgs cmdData) {
            var current = context.current();
            var arguments = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.loadNbtValue(cmdData.arguments(), arguments.forceCast()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(current.commandSourceStack(), stack, current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(cmdData.functions(), stack, arguments));
        }else if(command instanceof CommandFunctionArgsStorage cmdData) {
            var current = context.current();
            var arguments = context.<NbtCompound>createLocal();
            var clonedArguments = context.<NbtTag>createLocal();
            var validArguments = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.getNbtStorage(cmdData.storage(), arguments));
            context.writeInstruction(Instructions2.cloneNbtValue(arguments.forceCast(), clonedArguments));
            context.writeInstruction(Instructions2.checkIsCompound(clonedArguments, validArguments, current.contextEndLabel()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(current.commandSourceStack(), stack, current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(cmdData.functions(), stack, validArguments));
        }else if(command instanceof CommandFunctionArgsStoragePath cmdData) {
            var current = context.current();
            var currentTag = context.<NbtTag>createLocal();
            var storageLocal = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.createNullTag(currentTag));
            context.writeInstruction(Instructions2.getNbtStorage(cmdData.storage(), storageLocal));

            var parts = cmdData.path().parts();
            try(var ignored = context.enterWithoutValue(context.current())) {
                var value = getNbtPathValue(context, storageLocal.forceCast(), parts, false, current.contextEndLabel());
                context.writeInstruction(Instructions2.ifNotNullJump(currentTag.lower(), current.contextEndLabel()));
                context.writeInstruction(Instructions2.objectAssign(currentTag.lower(), value.lower()));
            }
            context.writeInstruction(Instructions2.ifNullJump(currentTag.lower(), current.contextEndLabel()));
            var clonedTag = context.<NbtTag>createLocal();
            context.writeInstruction(Instructions2.cloneNbtValue(currentTag, clonedTag));
            var compoundTag = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.checkIsCompound(clonedTag, compoundTag, current.contextEndLabel()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(current.commandSourceStack(), stack, current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(cmdData.functions(), stack, compoundTag));
        }else if(command instanceof CommandGamemode cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                selector.writeInstruction(Instructions2.tryAsPlayer(selector.content(), playerLocation, selector.data().contextEndLabel()));
                selector.writeInstruction(Instructions2.setGamemode(playerLocation, cmdData.gamemode()));
            }
        }else if(command instanceof CommandKill cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                try(var world = context.getWorld()) {
                    selector.writeInstruction(Instructions2.killEntity(selector.content(), world.content()));
                }
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
                var criterion = context.<ScoreboardCriterion>createLocal();
                objective.writeInstruction(Instructions2.getObjectiveCriterion(objective.content(), criterion));
                var mappedCriterion = context.<ScoreboardCriterion>createLocal();
                objective.writeInstruction(Instructions2.mapObjectiveCriterion(DataRegistries.SCOREBOARD_CRITERIA.getOrThrow("trigger"), mappedCriterion));
                objective.writeInstruction(Instructions2.ifObjectNotEquivalentJump(criterion.lower(), mappedCriterion.lower(), objective.data().contextEndLabel()));
                try(var name = context.forScoreHolder(cmdData.targets().scoreHolder())) {
                    try(var scoreData = context.getScoreData(new ScoreAccessor(objective.content(), name.content()), true)) {
                        scoreData.writeInstruction(Instructions2.scoreUnlock(scoreData.content()));
                    }
                }
            }
        }else if(command instanceof CommandScoreboardPlayersGet cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets())) {
                try(var scoreData = context.getScoreData(targets.content(), false)) {
                    var res = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions2.getScoreValue(scoreData.content(), res));
                    result.addResult(context.instructionWriter, scoreData.data(), res);
                }
            }
        }else if(command instanceof CommandScoreboardPlayersOperation cmdData) {
            var selectorCodeLocation = context.current().writeLabel(context.instructionWriter);
            try(var ignored = context.enterWithoutValue(context.current().withContextLabel(selectorCodeLocation))) {
                try(var targets = context.forScoreAccessors(cmdData.targets())) {
                    try(var source = context.forScoreAccessors(cmdData.source())) {
                        try(var targetScore = context.getScoreData(targets.content(), true)) {
                            try(var sourceScore = context.getScoreData(source.content(), true)) {
                                switch(cmdData.operation()) {
                                    case SET -> sourceScore.writeInstruction(Instructions2.runScoreOperationSet(targetScore.content(), sourceScore.content()));
                                    case ADD -> sourceScore.writeInstruction(Instructions2.runScoreOperationPlus(targetScore.content(), sourceScore.content()));
                                    case SUB -> sourceScore.writeInstruction(Instructions2.runScoreOperationMinus(targetScore.content(), sourceScore.content()));
                                    case MUL -> sourceScore.writeInstruction(Instructions2.runScoreOperationMultiply(targetScore.content(), sourceScore.content()));
                                    case DIV -> {
                                        var divisor = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(sourceScore.content(), divisor));
                                        sourceScore.writeInstruction(Instructions2.ifEqualJump(divisor, LocationData.ofInteger(0), selectorCodeLocation));
                                        sourceScore.writeInstruction(Instructions2.runScoreOperationDivide(targetScore.content(), sourceScore.content()));
                                    }
                                    case MOD -> {
                                        var divisor = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(sourceScore.content(), divisor));
                                        sourceScore.writeInstruction(Instructions2.ifEqualJump(divisor, LocationData.ofInteger(0), selectorCodeLocation));
                                        sourceScore.writeInstruction(Instructions2.runScoreOperationModulo(targetScore.content(), sourceScore.content()));
                                    }
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
                var playerLocation = context.<Player>createLocal();
                var textLocation = context.<TextComponent>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.loadText(cmdData.text(), textLocation));
                players.writeInstruction(Instructions2.sendActionbar(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTitleClear cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.sendTitlesClear(playerLocation));
            }
        }else if(command instanceof CommandTitleReset cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.sendTitlesReset(playerLocation));
            }
        }else if(command instanceof CommandTitleSubtitle cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                var textLocation = context.<TextComponent>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.loadText(cmdData.text(), textLocation));
                players.writeInstruction(Instructions2.sendSubtitle(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTitleTimes cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.sendTitlesTimes(playerLocation, cmdData.fadeIn(), cmdData.stay(), cmdData.fadeOut()));
            }
        }else if(command instanceof CommandTitleTitle cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                var textLocation = context.<TextComponent>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.loadText(cmdData.text(), textLocation));
                players.writeInstruction(Instructions2.sendTitle(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTellraw cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                var textLocation = context.<TextComponent>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(players.content(), playerLocation, players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.loadText(cmdData.text(), textLocation));
                players.writeInstruction(Instructions2.sendSystemMessage(playerLocation, textLocation));
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

    private static LocationData<NbtTag> getNbtPathValue(CompilationContext context, LocationData<NbtTag> source, List<NbtPathPart> parts, boolean tryCreate, LabelData failLabel) {
        for(var part : parts) {
            if(part instanceof NbtPathPart.Named named) {
                var compound = context.<NbtCompound>createLocal();
                context.writeInstruction(Instructions2.checkIsCompound(source, compound, failLabel));
                var storage = context.<NbtTag>createLocal();
                if(tryCreate) {
                    context.writeInstruction(Instructions2.getNbtFromCompoundOrCreate(compound, named.name(), storage));
                }else {
                    context.writeInstruction(Instructions2.getNbtFromCompound(compound, named.name(), storage));
                }
                source = storage;
            }else if(part instanceof NbtPathPart.ArrayIndexInteger indexed) {
                var list = context.<NbtList>createLocal();
                context.writeInstruction(Instructions2.checkIsList(source, list, failLabel));
                var length = context.<Integer>createLocal();
                context.writeInstruction(Instructions2.getListLength(list.forceCast(), length));
                context.writeInstruction(Instructions2.ifGreaterEqualJump(LocationData.ofInteger(indexed.index()), length, failLabel));
                var index = context.<Integer>createLocal();
                if(indexed.index() >= 0) {
                    context.writeInstruction(Instructions2.valueDefine(index, LocationData.ofInteger(indexed.index())));
                }else {
                    context.writeInstruction(Instructions2.valueDefine(index, length));
                    context.writeInstruction(Instructions2.valueSub(index, LocationData.ofInteger(-indexed.index())));
                }
                var storage = context.<NbtTag>createLocal();
                context.writeInstruction(Instructions2.getNbtFromList(list, index, storage));
                source = storage;
            }else {
                throw new UnsupportedOperationException("Cannot get nbt path from " + part);
            }
        }
        return source;
    }

}
