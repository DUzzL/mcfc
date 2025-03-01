package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.commands.params.*;
import dev.epicpix.minecraftfunctioncompiler.data.*;
import dev.epicpix.minecraftfunctioncompiler.data.locations.*;
import dev.epicpix.minecraftfunctioncompiler.data.nbt.NbtPathPart;
import dev.epicpix.minecraftfunctioncompiler.data.text.Text;
import dev.epicpix.minecraftfunctioncompiler.data.text.TextContent;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.ObjectComparisonType;
import dev.epicpix.minecraftfunctioncompiler.il.*;
import dev.epicpix.minecraftfunctioncompiler.registry.DataRegistries;

import java.util.ArrayDeque;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.*;

public final class CommandCompiler {
    private CommandCompiler() {}

    public static List<Instruction> compile(DataLocation source, List<CommandParams> commands) {
        var context = new CompilationContext(source);
        for(var command : commands) {
            var commandLabel = context.current().writeLabel(context.instructionWriter);
            try(var ignored = context.enterWithoutValue(context.current().withContextLabel(commandLabel))) {
                compile(context, null, command, ResultStorage.empty(source));
            }
        }
        return context.end();
    }

    private static void compile(CompilationContext context, ForkingContext forkingContext, CommandParams command, ResultStorage result) {
        if(command instanceof CommandAdvancementGrantOnly cmdData) {
            var advancementData = DataRegistries.ADVANCEMENT.get(cmdData.advancement());
            if(advancementData == null) return;
            try(var selector = context.forSelector(cmdData.targets())) {
                var player = context.<Player>createLocal();
                selector.writeInstruction(Instructions2.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                var playerAdvancements = context.<PlayerAdvancements>createLocal();
                selector.writeInstruction(Instructions2.getPlayerAdvancements(playerAdvancements, player));
                for(var criterion : advancementData.criteria()) {
                    selector.writeInstruction(Instructions2.awardAdvancementCriterion(playerAdvancements, LocationData.ofString(criterion), advancementData.location()));
                }
            }
        }else if(command instanceof CommandAdvancementGrantOnlyCriterion cmdData) {
            var advancementData = DataRegistries.ADVANCEMENT.get(cmdData.advancement());
            if(advancementData == null) return;
            if(!advancementData.criteria().contains(cmdData.criterion())) return;
            try(var selector = context.forSelector(cmdData.targets())) {
                var player = context.<Player>createLocal();
                selector.writeInstruction(Instructions2.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                var playerAdvancements = context.<PlayerAdvancements>createLocal();
                selector.writeInstruction(Instructions2.getPlayerAdvancements(playerAdvancements, player));
                selector.writeInstruction(Instructions2.awardAdvancementCriterion(playerAdvancements, LocationData.ofString(cmdData.criterion()), advancementData.location()));
            }
        }else if(command instanceof CommandAdvancementRevokeOnly cmdData) {
            var advancementData = DataRegistries.ADVANCEMENT.get(cmdData.advancement());
            if(advancementData == null) return;
            try(var selector = context.forSelector(cmdData.targets())) {
                var player = context.<Player>createLocal();
                selector.writeInstruction(Instructions2.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                var playerAdvancements = context.<PlayerAdvancements>createLocal();
                selector.writeInstruction(Instructions2.getPlayerAdvancements(playerAdvancements, player));
                for(var criterion : advancementData.criteria()) {
                    selector.writeInstruction(Instructions2.revokeAdvancementCriterion(playerAdvancements, LocationData.ofString(criterion), advancementData.location()));
                }
            }
        }else if(command instanceof CommandAdvancementRevokeOnlyCriterion cmdData) {
            var advancementData = DataRegistries.ADVANCEMENT.get(cmdData.advancement());
            if(advancementData == null) return;
            if(!advancementData.criteria().contains(cmdData.criterion())) return;
            try(var selector = context.forSelector(cmdData.targets())) {
                var player = context.<Player>createLocal();
                selector.writeInstruction(Instructions2.tryAsPlayer(player, selector.content(), selector.data().contextEndLabel()));
                var playerAdvancements = context.<PlayerAdvancements>createLocal();
                selector.writeInstruction(Instructions2.getPlayerAdvancements(playerAdvancements, player));
                selector.writeInstruction(Instructions2.revokeAdvancementCriterion(playerAdvancements, LocationData.ofString(cmdData.criterion()), advancementData.location()));
            }
        }else if(command instanceof CommandAttributeBaseSet cmdData) {
            try(var selector = context.forSelector(cmdData.target())) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                var attrLocation = context.<AttributeInstance>createLocal();
                selector.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, selector.content(), selector.data().contextEndLabel()));
                selector.writeInstruction(Instructions2.getAttributeInstance(livingEntityLocation, attrLocation, cmdData.attribute().location()));
                selector.writeInstruction(Instructions2.ifNullJump(attrLocation, selector.data().contextEndLabel()));
                selector.writeInstruction(Instructions2.setAttributeBase(attrLocation, cmdData.value()));
            }
        }else if(command instanceof CommandDataModifySetValue cmdData) {
            if(cmdData.destination() instanceof NbtDataLocation.Storage loc) {
                var storageLocal = context.<NbtCompound>createLocal();
                var setCountLocal = context.<Integer>createLocal();

                context.writeInstruction(Instructions2.getNbtStorage(storageLocal, loc.storage()));
                context.writeInstruction(Instructions2.valueAssign(setCountLocal, LocationData.ofInteger(0)));

                var parts = cmdData.path().parts();

                var codeLocation = context.current().writeLabel(context.instructionWriter);
                try(var innerContext = context.enterWithoutValue(context.current().withContextLabel(codeLocation))) {
                    var value = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, innerContext.data().contextEndLabel());

                    var last = parts.get(parts.size() - 1);
                    var tagLocal = context.<NbtTag>createLocal();
                    var tagCopiedLocal = context.<NbtTag>createLocal();
                    innerContext.writeInstruction(Instructions2.loadNbtValue(tagLocal, cmdData.value()));
                    innerContext.writeInstruction(Instructions2.cloneNbtValue(tagCopiedLocal, tagLocal));
                    assignNbtValue(context, value, last, tagCopiedLocal, setCountLocal, innerContext.data().contextEndLabel());
                }

                context.writeInstruction(Instructions2.ifNumberCompareJump(NumberComparisonType.EQUAL, setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
                context.writeInstruction(Instructions2.setNbtStorage(loc.storage(), storageLocal));
            }else {
                throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_SOURCE_ACCESS, context.getSource(), "Accessing nbt from block/entity sources is not implemented yet"));
            }
        }else if(command instanceof CommandDifficultySet cmdData) {
            var difficultyLocation = context.<DifficultyValue>createLocal();
            context.writeInstruction(Instructions2.mapDifficulty(difficultyLocation, cmdData.difficulty()));
            context.writeInstruction(Instructions2.setDifficulty(difficultyLocation));
        }else if(command instanceof CommandEffectClear) {
            try(var self = context.getCurrentEntity()) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                self.writeInstruction(Instructions2.ifNullJump(self.content(), self.data().contextEndLabel()));
                self.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, self.content(), self.data().contextEndLabel()));
                self.writeInstruction(Instructions2.clearAllEffects(livingEntityLocation));
            }
        }else if(command instanceof CommandEffectClearEntities cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.clearAllEffects(livingEntityLocation));
            }
        }else if(command instanceof CommandEffectClearEntitiesEffect cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var livingEntityLocation = context.<LivingEntity>createLocal();
                targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.clearEffect(livingEntityLocation, cmdData.effect().location()));
            }
        }else if(command instanceof CommandEffectGive cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), effect.instant() ? 1 : 600, 0, true));
                }
            }
        }
        else if(command instanceof CommandEffectGiveDur cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), 0, true));
                }
            }
        }else if(command instanceof CommandEffectGiveDurAmp cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), cmdData.amplifier(), true));
                }
            }
        }else if(command instanceof CommandEffectGiveDurAmpShow cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), cmdData.duration() * (effect.instant() ? 1 : 20), cmdData.amplifier(), !cmdData.hideParticles()));
                }
            }
        }else if(command instanceof CommandEffectGiveInf cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, 0, true));
                }
            }
        }else if(command instanceof CommandEffectGiveInfAmp cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, cmdData.amplifier(), true));
                }
            }
        }else if(command instanceof CommandEffectGiveInfAmpShow cmdData) {
            MobEffectData effect = cmdData.effect();
            try(var self = context.getCurrentEntity()) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var livingEntityLocation = context.<LivingEntity>createLocal();
                    targets.writeInstruction(Instructions2.tryAsLivingEntity(livingEntityLocation, targets.content(), targets.data().contextEndLabel()));
                    targets.writeInstruction(Instructions2.giveEffect(self.content(), livingEntityLocation, effect.location(), -1, cmdData.amplifier(), !cmdData.hideParticles()));
                }
            }
        }else if(command instanceof CommandExecuteAlign cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var pos = context.getPosition()) {
                    var newPosition = context.<Vec3>createLocal();
                    pos.writeInstruction(Instructions2.alignPosition(newPosition, pos.content(), cmdData.swizzle()));
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
                    selector.writeInstruction(Instructions2.getEntityWorld(world, selector.content()));
                    selector.writeInstruction(Instructions2.getEntityPosition(position, selector.content()));
                    selector.writeInstruction(Instructions2.getEntityRotation(rotation, selector.content()));
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
        }else if(command instanceof CommandExecuteIfFunction cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var current = context.current();
                var stack = context.<CommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                var returnValue = context.<Long>createLocal();
                context.writeInstruction(Instructions.callFunctions(returnValue, cmdData.functions(), stack, null));
                context.writeInstruction(Instructions2.jumpIfNullReturnValue(returnValue, fContext.endLabel()));
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteIfPredicate cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var predicateData = DataRegistries.PREDICATE.get(cmdData.predicate());
                if(predicateData != null) {
                    try(var entity = context.getCurrentEntity()) {
                        try(var world = context.getWorld()) {
                            try(var position = context.getPosition()) {
                                entity.writeInstruction(Instructions2.jumpIfPredicateCommandFails(world.content(), entity.content(), position.content(), fContext.endLabel(), cmdData.predicate()));
                                compile(context, fContext.content(), cmdData.command(), result);
                            }
                        }
                    }
                }
            }
        }else if(command instanceof CommandExecuteIfScoreRange cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), false)) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions2.getScoreValue(scoreValue, scoreData.content()));
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
                                var targetValue = context.<Integer>createLocal();
                                var sourceValue = context.<Integer>createLocal();
                                sourceScore.writeInstruction(Instructions2.getScoreValue(targetValue, targetScore.content()));
                                sourceScore.writeInstruction(Instructions2.getScoreValue(sourceValue, sourceScore.content()));
                                sourceScore.writeInstruction(Instructions2.ifNumberCompareJump(cmdData.comparison().toNumberComparisonJump(), targetValue, sourceValue, sourceScore.data().contextEndLabel()));
                                compile(context, fContext.content(), cmdData.command(), result);
                            }
                        }
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnOrigin cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var traceableEntity = context.<TraceableEntity>createLocal();
                    currentEntity.writeInstruction(Instructions2.tryAsTraceableEntity(traceableEntity, currentEntity.content(), currentEntity.endLabel()));
                    var origin = context.<Entity>createLocal();
                    currentEntity.writeInstruction(Instructions2.getEntityOrigin(origin, traceableEntity));
                    currentEntity.writeInstruction(Instructions2.ifNullJump(origin, currentEntity.endLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(origin))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnTarget cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var targetingEntity = context.<TargetingEntity>createLocal();
                    currentEntity.writeInstruction(Instructions2.tryAsTargetingEntity(targetingEntity, currentEntity.content(), currentEntity.endLabel()));
                    var target = context.<LivingEntity>createLocal();
                    currentEntity.writeInstruction(Instructions2.getEntityTarget(target, targetingEntity));
                    currentEntity.writeInstruction(Instructions2.ifNullJump(target, currentEntity.endLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(target))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecuteOnVehicle cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var currentEntity = context.getCurrentEntity()) {
                    var vehicle = context.<Entity>createLocal();
                    currentEntity.writeInstruction(Instructions2.getEntityVehicle(vehicle, currentEntity.content()));
                    currentEntity.writeInstruction(Instructions2.ifNullJump(vehicle, currentEntity.endLabel()));
                    try(var ignored = context.enterWithoutValue(currentEntity.data().withCurrentEntity(vehicle))) {
                        compile(context, fContext.content(), cmdData.command(), result);
                    }
                }
            }
        }else if(command instanceof CommandExecutePositionedAs cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                try(var targets = context.forSelector(cmdData.targets())) {
                    var position = context.<Vec3>createLocal();
                    targets.writeInstruction(Instructions2.getEntityPosition(position, targets.content()));
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
                    targets.writeInstruction(Instructions2.getEntityRotation(rotation, targets.content()));
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
                var res = context.withResultStorage(true, false);
                compile(context, null, cmdData.command(), res);
                res.checkRequirements(cmdData.command());

                var storageLocal = context.<NbtCompound>createLocal();
                var setCountLocal = context.<Integer>createLocal();

                context.writeInstruction(Instructions2.getNbtStorage(storageLocal, cmdData.storage()));
                context.writeInstruction(Instructions2.valueAssign(setCountLocal, LocationData.ofInteger(0)));

                var parts = cmdData.path().parts();

                try(var innerContext = context.enterLocalScope()) {
                    var value = getNbtPathValue(context, storageLocal, parts.subList(0, parts.size() - 1), true, innerContext.endLabel());

                    var last = parts.get(parts.size() - 1);
                    var tagLocal = context.<NbtTag>createLocal();
                    if(cmdData.storeType() == ResultStoreType.INT) {
                        if(cmdData.scale() == 1.0d) {
                            innerContext.writeInstruction(Instructions2.createIntTag(tagLocal, res.result));
                        }else {
                            var resultDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions2.numberToDouble(resultDouble, res.result));
                            var multipliedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions2.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(cmdData.scale())));
                            var convertedInteger = context.<Integer>createLocal();
                            innerContext.writeInstruction(Instructions2.numberToInteger(convertedInteger, multipliedDouble));
                            innerContext.writeInstruction(Instructions2.createIntTag(tagLocal, convertedInteger));
                        }
                    }else if(cmdData.storeType() == ResultStoreType.DOUBLE) {
                        var convertedDouble = context.<Double>createLocal();
                        innerContext.writeInstruction(Instructions2.numberToDouble(convertedDouble, res.result));
                        if(cmdData.scale() == 1.0d) {
                            innerContext.writeInstruction(Instructions2.createDoubleTag(tagLocal, convertedDouble));
                        }else {
                            var multipliedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions2.valueMul(multipliedDouble, convertedDouble, LocationData.ofDouble(cmdData.scale())));
                            innerContext.writeInstruction(Instructions2.createDoubleTag(tagLocal, multipliedDouble));
                        }
                    }else if(cmdData.storeType() == ResultStoreType.FLOAT) {
                        if(cmdData.scale() == 1.0d) {
                            var convertedFloat = context.<Float>createLocal();
                            innerContext.writeInstruction(Instructions2.numberToFloat(convertedFloat, res.result));
                            innerContext.writeInstruction(Instructions2.createFloatTag(tagLocal, convertedFloat));
                        }else {
                            var resultDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions2.numberToDouble(resultDouble, res.result));
                            var multipliedDouble = context.<Double>createLocal();
                            innerContext.writeInstruction(Instructions2.valueMul(multipliedDouble, resultDouble, LocationData.ofDouble(cmdData.scale())));
                            var convertedFloat = context.<Float>createLocal();
                            innerContext.writeInstruction(Instructions2.numberToFloat(convertedFloat, multipliedDouble));
                            innerContext.writeInstruction(Instructions2.createFloatTag(tagLocal, convertedFloat));
                        }
                    }else {
                        throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_STORE, context.getSource(), "Storing into storage with a non-int/non-double/non-float type is not yet implemented"));
                    }
                    assignNbtValue(context, value, last, tagLocal, setCountLocal, innerContext.endLabel());
                }
                context.writeInstruction(Instructions2.ifNumberCompareJump(NumberComparisonType.EQUAL, setCountLocal, LocationData.ofInteger(0), context.current().contextEndLabel()));
                context.writeInstruction(Instructions2.setNbtStorage(cmdData.storage(), storageLocal));
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
        }else if(command instanceof CommandExecuteUnlessFunction cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var current = context.current();
                var stack = context.<CommandSourceStack>createLocal();
                context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
                var returnValue = context.<Long>createLocal();
                context.writeInstruction(Instructions.callFunctions(returnValue, cmdData.functions(), stack, null));
                context.writeInstruction(Instructions2.jumpIfNotNullReturnValue(returnValue, fContext.endLabel()));
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExecuteUnlessPredicate cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var predicateData = DataRegistries.PREDICATE.get(cmdData.predicate());
                if(predicateData != null) {
                    try(var entity = context.getCurrentEntity()) {
                        try(var world = context.getWorld()) {
                            try(var position = context.getPosition()) {
                                entity.writeInstruction(Instructions2.jumpIfPredicateCommandSucceeds(world.content(), entity.content(), position.content(), fContext.endLabel(), cmdData.predicate()));
                                compile(context, fContext.content(), cmdData.command(), result);
                            }
                        }
                    }
                }
            }
        }else if(command instanceof CommandExecuteUnlessScoreRange cmdData) {
            try(var fContext = context.withForkingContext(forkingContext)) {
                var contextLabel = context.current().contextEndLabel();
                try(var scoreAccessor = context.forScoreAccessors(cmdData.target().selectSingle())) {
                    try(var scoreData = context.getScoreData(scoreAccessor.content(), false)) {
                        var scoreValue = context.<Integer>createLocal();
                        scoreData.writeInstruction(Instructions2.getScoreValue(scoreValue, scoreData.content()));
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
                                var targetValue = context.<Integer>createLocal();
                                var sourceValue = context.<Integer>createLocal();
                                sourceScore.writeInstruction(Instructions2.getScoreValue(targetValue, targetScore.content()));
                                sourceScore.writeInstruction(Instructions2.getScoreValue(sourceValue, sourceScore.content()));
                                sourceScore.writeInstruction(Instructions2.ifNumberCompareJump(cmdData.comparison().negate().toNumberComparisonJump(), targetValue, sourceValue, contextLabel));
                            }
                        }
                    }
                }
                compile(context, fContext.content(), cmdData.command(), result);
            }
        }else if(command instanceof CommandExperienceAdd cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.addExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceAddPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.addExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceAddLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.addExperienceLevels(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceSet cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.setExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceSetPoints cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.setExperiencePoints(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandExperienceSetLevels cmdData) {
            try(var targets = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                targets.writeInstruction(Instructions2.tryAsPlayer(playerLocation, targets.content(), targets.data().contextEndLabel()));
                targets.writeInstruction(Instructions2.setExperienceLevels(playerLocation, cmdData.amount()));
            }
        }else if(command instanceof CommandFunction cmdData) {
            var current = context.current();
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(null, cmdData.functions(), stack, null));
        }else if(command instanceof CommandFunctionArgs cmdData) {
            var current = context.current();
            var arguments = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.loadNbtValue(arguments.forceCast(), cmdData.arguments()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(null, cmdData.functions(), stack, arguments));
        }else if(command instanceof CommandFunctionArgsStorage cmdData) {
            var current = context.current();
            var arguments = context.<NbtCompound>createLocal();
            var clonedArguments = context.<NbtTag>createLocal();
            var validArguments = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.getNbtStorage(arguments, cmdData.storage()));
            context.writeInstruction(Instructions2.cloneNbtValue(clonedArguments, arguments));
            context.writeInstruction(Instructions2.tryAsNbtCompound(validArguments, clonedArguments, current.contextEndLabel()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(null, cmdData.functions(), stack, validArguments));
        }else if(command instanceof CommandFunctionArgsStoragePath cmdData) {
            var current = context.current();
            var storageLocal = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.getNbtStorage(storageLocal, cmdData.storage()));

            var parts = cmdData.path().parts();
            var value = getNbtPathValue(context, storageLocal, parts, false, current.contextEndLabel());
            context.writeInstruction(Instructions2.ifNullJump(value, current.contextEndLabel()));
            var clonedTag = context.<NbtTag>createLocal();
            context.writeInstruction(Instructions2.cloneNbtValue(clonedTag, value));
            var compoundTag = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.tryAsNbtCompound(compoundTag, clonedTag, current.contextEndLabel()));
            var stack = context.<CommandSourceStack>createLocal();
            context.writeInstruction(Instructions.makeCommandSourceStack(stack, current.commandSourceStack(), current.currentEntity(), current.currentWorld(), current.currentPosition(), current.currentRotation(), current.currentAnchor()));
            context.writeInstruction(Instructions.callFunctions(null, cmdData.functions(), stack, compoundTag));
        }else if(command instanceof CommandGamemode cmdData) {
            try(var selector = context.forSelector(cmdData.targets())) {
                var playerLocation = context.<Player>createLocal();
                selector.writeInstruction(Instructions2.tryAsPlayer(playerLocation, selector.content(), selector.data().contextEndLabel()));
                var mappedGamemode = context.<GameType>createLocal();
                selector.writeInstruction(Instructions2.mapGamemode(mappedGamemode, cmdData.gamemode()));
                selector.writeInstruction(Instructions2.setGamemode(playerLocation, mappedGamemode));
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
                    var scoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions2.getScoreValue(scoreValue, scoreData.content()));
                    var updatedScoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions2.valueAdd(updatedScoreValue, scoreValue, context.getResolvedIntegerMacro(cmdData.score())));
                    scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), updatedScoreValue));
                }
            }
        }else if(command instanceof CommandScoreboardPlayersEnable cmdData) {
            try(var objective = context.getObjective(cmdData.targets().objective())) {
                var criterion = context.<ScoreboardCriterion>createLocal();
                objective.writeInstruction(Instructions2.getObjectiveCriterion(criterion, objective.content()));
                var mappedCriterion = context.<ScoreboardCriterion>createLocal();
                objective.writeInstruction(Instructions2.mapObjectiveCriterion(mappedCriterion, DataRegistries.SCOREBOARD_CRITERIA.getOrThrow("trigger")));
                objective.writeInstruction(Instructions2.ifObjectCompareJump(ObjectComparisonType.NOT_IDENTITY_EQUAL, criterion, mappedCriterion, objective.data().contextEndLabel()));
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
                    scoreData.writeInstruction(Instructions2.getScoreValue(res, scoreData.content()));
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
                                    case SET -> {
                                        var scoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValue, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), scoreValue));
                                    }
                                    case ADD -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.valueAdd(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case SUB -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.valueSub(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case MUL -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.valueMul(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case DIV -> {
                                        var divisor = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(divisor, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions2.ifNumberCompareJump(NumberComparisonType.EQUAL, divisor, LocationData.ofInteger(0), selectorCodeLocation));
                                        var dividend = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(dividend, targetScore.content()));

                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.intFloorMod(updatedScoreValue, dividend, divisor));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case MOD -> {
                                        var divisor = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(divisor, sourceScore.content()));
                                        sourceScore.writeInstruction(Instructions2.ifNumberCompareJump(NumberComparisonType.EQUAL, divisor, LocationData.ofInteger(0), selectorCodeLocation));
                                        var dividend = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(dividend, targetScore.content()));

                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.intFloorDiv(updatedScoreValue, dividend, divisor));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case MIN -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.intMin(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case MAX -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueB, targetScore.content()));
                                        var updatedScoreValue = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.intMax(updatedScoreValue, scoreValueA, scoreValueB));
                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), updatedScoreValue));
                                    }
                                    case SWAP -> {
                                        var scoreValueA = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueA, sourceScore.content()));
                                        var scoreValueB = context.<Integer>createLocal();
                                        sourceScore.writeInstruction(Instructions2.getScoreValue(scoreValueB, targetScore.content()));

                                        sourceScore.writeInstruction(Instructions2.setScore(targetScore.content(), scoreValueA));
                                        sourceScore.writeInstruction(Instructions2.setScore(sourceScore.content(), scoreValueB));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }else if(command instanceof CommandScoreboardPlayersRemove cmdData) {
            try(var targets = context.forScoreAccessors(cmdData.targets())) {
                try(var scoreData = context.getScoreData(targets.content(), true)) {
                    var scoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions2.getScoreValue(scoreValue, scoreData.content()));
                    var updatedScoreValue = context.<Integer>createLocal();
                    scoreData.writeInstruction(Instructions2.valueSub(updatedScoreValue, scoreValue, context.getResolvedIntegerMacro(cmdData.score())));
                    scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), updatedScoreValue));
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
                    scoreData.writeInstruction(Instructions2.setScore(scoreData.content(), context.getResolvedIntegerMacro(cmdData.score())));
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
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions2.sendActionbar(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTitleClear cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.sendTitlesClear(playerLocation));
            }
        }else if(command instanceof CommandTitleReset cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.sendTitlesReset(playerLocation));
            }
        }else if(command instanceof CommandTitleSubtitle cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions2.sendSubtitle(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTitleTimes cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                players.writeInstruction(Instructions2.sendTitlesTimes(playerLocation, cmdData.fadeIn(), cmdData.stay(), cmdData.fadeOut()));
            }
        }else if(command instanceof CommandTitleTitle cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
                players.writeInstruction(Instructions2.sendTitle(playerLocation, textLocation));
            }
        }else if(command instanceof CommandTellraw cmdData) {
            try(var players = context.forSelector(cmdData.players())) {
                var playerLocation = context.<Player>createLocal();
                players.writeInstruction(Instructions2.tryAsPlayer(playerLocation, players.content(), players.data().contextEndLabel()));
                var textLocation = loadText(context, cmdData.text());
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
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNKNOWN_COMMAND, context.getSource(), "Not implemented command: " + command));
        }
    }

    private static LocationData<? extends NbtTag> getNbtPathValue(CompilationContext context, LocationData<? extends NbtTag> source, List<NbtPathPart> parts, boolean tryCreate, LabelData failLabel) {
        for(var part : parts) {
            if(part instanceof NbtPathPart.Named named) {
                var compound = context.<NbtCompound>createLocal();
                context.writeInstruction(Instructions2.tryAsNbtCompound(compound, source, failLabel));
                var storage = context.<NbtTag>createLocal();
                if(tryCreate) {
                    context.writeInstruction(Instructions2.getNbtFromCompoundOrCreate(storage, compound, named.name()));
                }else {
                    context.writeInstruction(Instructions2.getNbtFromCompound(storage, compound, named.name()));
                }
                source = storage;
            }else if(part instanceof NbtPathPart.ArrayIndexInteger indexed) {
                var listAccess = accessNbtList(context, source, indexed, failLabel);
                var storage = context.<NbtTag>createLocal();
                context.writeInstruction(Instructions2.getNbtFromList(storage, listAccess.list, listAccess.index));
                source = storage;
            }else {
                throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot get nbt path from " + part));
            }
        }
        return source;
    }

    private static void assignNbtValue(CompilationContext context, LocationData<? extends NbtTag> source, NbtPathPart accessor, LocationData<? extends NbtTag> value, LocationData<Integer> modifiedCount, LabelData failLabel) {
        if(accessor instanceof NbtPathPart.Named named) {
            var valueCompound = context.<NbtCompound>createLocal();
            context.writeInstruction(Instructions2.tryAsNbtCompound(valueCompound, source, failLabel));
            context.writeInstruction(Instructions2.setNbtInCompound(valueCompound, named.name(), value));
        }else if(accessor instanceof NbtPathPart.ArrayIndexInteger indexed) {
            var listAccess = accessNbtList(context, source, indexed, failLabel);
            context.writeInstruction(Instructions2.setNbtInList(listAccess.list, listAccess.index, value, failLabel));
        }else {
            throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_NBT_ACCESS, context.getSource(), "Cannot set nbt value in path " + accessor));
        }
        if(modifiedCount != null) context.writeInstruction(Instructions2.valueAdd(modifiedCount, modifiedCount, LocationData.ofInteger(1)));
    }

    private static NbtListAccessResult accessNbtList(CompilationContext context, LocationData<? extends NbtTag> source, NbtPathPart.ArrayIndexInteger indexed, LabelData failLabel) {
        var list = context.<NbtList>createLocal();
        context.writeInstruction(Instructions2.tryAsNbtList(list, source, failLabel));
        var length = context.<Integer>createLocal();
        context.writeInstruction(Instructions2.getListLength(length, list));
        context.writeInstruction(Instructions2.ifNumberCompareJump(NumberComparisonType.GREATER_THAN_OR_EQUAL, LocationData.ofInteger(indexed.index()), length, failLabel));
        var index = context.<Integer>createLocal();

        if(indexed.index() >= 0) {
            context.writeInstruction(Instructions2.valueAssign(index, LocationData.ofInteger(indexed.index())));
        }else {
            context.writeInstruction(Instructions2.valueAssign(index, length));
            context.writeInstruction(Instructions2.valueSub(index, index, LocationData.ofInteger(-indexed.index())));
        }

        return new NbtListAccessResult(list, index);
    }

    private record NbtListAccessResult(LocationData<NbtList> list, LocationData<Integer> index) {}

    private static LocationData<TextComponent> loadText(CompilationContext context, Text text) {
        var allText = new ArrayDeque<Text>();
        allText.add(text);
        while(!allText.isEmpty()) {
            var top = allText.pop();
            if(!(top.content() instanceof TextContent.LiteralText || top.content() instanceof TextContent.KeybindText)) {
                if(top.content() instanceof TextContent.TranslatableText tr) {
                    for(var content : tr.args()) {
                        if(content instanceof Text t) allText.add(t);
                    }
                }else {
                    throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_UNSUPPORTED_TEXT, context.getSource(), "Compiling " + top.content().getClass().getSimpleName() + " is not implemented yet"));
                }
            }
            allText.addAll(top.siblings());
        }

        var textLocation = context.<TextComponent>createLocal();
        context.writeInstruction(Instructions2.loadText(textLocation, text));
        return textLocation;
    }

}
