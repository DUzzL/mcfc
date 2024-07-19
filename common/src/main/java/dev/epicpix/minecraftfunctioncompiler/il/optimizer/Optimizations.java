package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.*;

import java.util.*;

import static dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationResult.*;
import static dev.epicpix.minecraftfunctioncompiler.il.optimizer.Optimizer.getFirstFreeLocal;

public final class Optimizations {
    private Optimizations() {}

    static final Optimization[] OPTIMIZATIONS = {
            Optimizations::removeExitScopeDeadCode,
            Optimizations::inlineListAccess,
            Optimizations::removeUnnecessaryInstructions,
            Optimizations::mergePureInstructions,
            Optimizations::removeDuplicateNullChecks,
            Optimizations::upgradeEntityToPlayerSelector,
            Optimizations::coalesceEntityCasts,
            Optimizations::combineObjectiveGetters,
            Optimizations::combineNullChecks,
    };

    private static OptimizationResult removeUnnecessaryInstructions(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.ALWAYS) return OptimizationResultTypes.INLINE_AND_REMOVE_SELF;
        if(instr.type() == InstructionTypes2.NEVER) return OptimizationResultTypes.REMOVE_SELF;
        if(instr.type() == InstructionTypes2.SCOPE) {
            var scope = instr.<ScopeData>get("scope");
            if(!input.anyChildren(it -> it.data().containsValue(scope))) return OptimizationResultTypes.INLINE_AND_REMOVE_SELF;
        }
        if(instr.type() == InstructionTypes2.IF_NOT_NULL && instr.children().isEmpty()) return OptimizationResultTypes.REMOVE_SELF;
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult removeExitScopeDeadCode(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.SCOPE) {
            var scope = instr.<ScopeData>get("scope");
            var scopeInstructions = input.collectChildren(it -> it.instruction().data().containsValue(scope));

            ArrayList<InstructionModification> modifications = new ArrayList<>();
            for(var scopeInstr : scopeInstructions) {
                if(scopeInstr.instruction().type() == InstructionTypes2.EXIT_SCOPE) {
                    var before = input.getByPath(scopeInstr.path().pathExceptLast());
                    for(int i = scopeInstr.path().lastIndex() + 1; i<before.instruction().children().size(); i++) {
                        modifications.add(new InstructionModification.RemoveInstruction(before.path().addPathNode(i)));
                    }
                }
            }
            if(!modifications.isEmpty()) {
                return OptimizationResult.modificationsWithFlags(modifications, 0);
            }
        }
        // TODO: Some check here is not correct
//        else if(instr.type() == InstructionTypes2.EXIT_SCOPE) {
//            var scope = instr.<ScopeData>get("scope");
//            var scopeDefinition = input.getInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.SCOPE && it.instruction().data().get("scope").equals(scope));
//            if(scopeDefinition != null) {
//                var isFlowSafe = !input.anyInstructionFromPath(it -> it.path().depth() > scopeDefinition.path().depth() && (it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER || it.instruction().type() == InstructionTypes2.FOR_EACH_ENTITY || it.instruction().type() == InstructionTypes2.FOR_EACH_LIST_ELEMENT));
//                var isLastElement = !input.anyInstructionFromPath(it -> it.path().depth() > scopeDefinition.path().depth() && input.thisPath().path().get(it.path().depth() - 1) == input.getByPath(it.path().pathExceptLast()).instruction().children().size());
//                if (isFlowSafe && isLastElement) {
//                    return OptimizationResultTypes.REMOVE_SELF;
//                }
//            }
//        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult inlineListAccess(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.CREATE_MUTABLE_LIST) {
            var listLocation = instr.<LocationData<List<?>>>get("list");
            var addInstructions = input.collectChildren(it -> it.instruction().type() == InstructionTypes2.ADD_LIST_ELEMENT && it.instruction().data().containsValue(listLocation));
            if(addInstructions.size() == 1) {
                var addInstruction = addInstructions.get(0);
                var elementSourceInstruction = input.getInstructionDefinitionInPath(addInstruction.path(), addInstruction.instruction().get("element"));
                var sourceInstructionType = elementSourceInstruction.instruction().type();
                var isEntity = sourceInstructionType == InstructionTypes2.GET_CURRENT_ENTITY || sourceInstructionType == InstructionTypes2.FOR_EACH_ENTITY || sourceInstructionType == InstructionTypes2.FOR_EACH_PLAYER || sourceInstructionType == InstructionTypes2.FOR_ENTITY || sourceInstructionType == InstructionTypes2.FOR_PLAYER || sourceInstructionType == InstructionTypes2.TRY_AS_LIVING_ENTITY || sourceInstructionType == InstructionTypes2.TRY_AS_PLAYER || (sourceInstructionType == InstructionTypes2.TRY_AS_ENTITY && elementSourceInstruction.instruction().data().get("entity").equals(addInstruction.instruction().get("element")));
                if(isEntity) {
                    var otherMutations = input.anyChildren(it -> it.type() != InstructionTypes2.ADD_LIST_ELEMENT && it.type() != InstructionTypes2.FOR_EACH_LIST_ELEMENT && it.data().containsValue(listLocation));
                    if(!otherMutations) {
                        var forEachInstruction = input.getChild(it -> it.instruction().type() == InstructionTypes2.FOR_EACH_LIST_ELEMENT && it.instruction().data().containsValue(listLocation));
                        if(forEachInstruction != null) {
                            var forEachDirectChild = input.getDirectChild(forEachInstruction);
                            var isSafeToInline = elementSourceInstruction.path().absolute() || sourceInstructionType == InstructionTypes2.GET_CURRENT_ENTITY || !input.anyChildrenIn(forEachInstruction, it -> it.type() == InstructionTypes.CALL_FUNCTIONS || it.type() == InstructionTypes2.KILL_ENTITY);
                            if (isSafeToInline) {
                                var commonReused = new HashSet<Integer>();
                                int nextId = getFirstFreeLocal(input.usedLocations(), instr, input.getDirectChild(addInstruction).path(), forEachDirectChild.path(), commonReused);
                                var remapData = new HashMap<Integer, Integer>();
                                for(var reused : commonReused) {
                                    remapData.putIfAbsent(reused, nextId++);
                                }
                                remapData.put(forEachInstruction.instruction().<LocationData<?>>get("element").local(), addInstruction.instruction().<LocationData<?>>get("element").local());
                                ArrayList<InstructionModification> modifications = new ArrayList<>();
                                modifications.add(new InstructionModification.RemoveInstruction(addInstruction.path(), 2));
                                if(addInstruction.path().depth() > 1) {
                                    modifications.add(new InstructionModification.MoveChildrenFromToNested(forEachInstruction.path(), addInstruction.path().pathExceptLast(), remapData, false, false, 1));
                                    if (!forEachDirectChild.path().equals(forEachInstruction.path())) {
                                        modifications.add(new InstructionModification.MoveChildrenFromToNested(forEachDirectChild.path(), InstructionPath.emptyRelative(), remapData, true, true, 0));
                                    }
                                }else {
                                    modifications.add(new InstructionModification.MoveChildrenFromToNested(addInstruction.path(), addInstruction.path(), remapData, true, false, 1));
                                }
                                return OptimizationResult.modificationsWithFlags(modifications, OptimizationResult.REMOVE_SELF_AND_INLINE_CHILDREN);
                            }
                        }
                    }
                }
            }else if(addInstructions.isEmpty()) {
                return OptimizationResultTypes.REMOVE_SELF;
            }
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult removeDuplicateNullChecks(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.IF_NOT_NULL) {
            var checkLoc = instr.<LocationData<Object>>get("local");
            var isNotNull = input.anyInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && (it.instruction().type() == InstructionTypes2.IF_NOT_NULL || it.instruction().type() == InstructionTypes2.GET_CURRENT_ENTITY || it.instruction().type() == InstructionTypes2.FOR_EACH_ENTITY || it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER || (it.instruction().type() == InstructionTypes2.TRY_AS_ENTITY && it.instruction().data().get("entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY && it.instruction().data().get("living_entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("player").equals(checkLoc))));
            if(isNotNull) {
                return OptimizationResultTypes.INLINE_AND_REMOVE_SELF;
            }
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult coalesceEntityCasts(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.TRY_AS_ENTITY) {
            var checkLoc = instr.<LocationData<Object>>get("object");
            var isCastReapplied = input.anyInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && ((it.instruction().type() == InstructionTypes2.TRY_AS_ENTITY && it.instruction().data().get("entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY && it.instruction().data().get("living_entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("player").equals(checkLoc))));
            if(isCastReapplied) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("entity").local(), checkLoc.local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }

            var castInstr = input.getInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && ((it.instruction().type() == InstructionTypes2.TRY_AS_ENTITY && it.instruction().data().get("object").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY && it.instruction().data().get("entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("entity").equals(checkLoc))));
            if(castInstr != null) {
                var remap = new HashMap<Integer, Integer>();
                if(castInstr.instruction().type() == InstructionTypes2.TRY_AS_ENTITY) remap.put(instr.<LocationData<?>>get("entity").local(), castInstr.instruction().<LocationData<?>>get("entity").local());
                if(castInstr.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY) remap.put(instr.<LocationData<?>>get("entity").local(), castInstr.instruction().<LocationData<?>>get("living_entity").local());
                if(castInstr.instruction().type() == InstructionTypes2.TRY_AS_PLAYER) remap.put(instr.<LocationData<?>>get("entity").local(), castInstr.instruction().<LocationData<?>>get("player").local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }

            var alreadyIsType = input.anyInstructionFromPath(it -> (it.instruction().type() == InstructionTypes2.GET_CURRENT_ENTITY && it.instruction().data().get("entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER && it.instruction().data().get("entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.FOR_EACH_ENTITY && it.instruction().data().get("entity").equals(checkLoc)));
            if(alreadyIsType) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("entity").local(), instr.<LocationData<?>>get("object").local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }
        }else if(instr.type() == InstructionTypes2.TRY_AS_LIVING_ENTITY) {
            var checkLoc = instr.<LocationData<Object>>get("entity");
            var isCastReapplied = input.anyInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && ((it.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY && it.instruction().data().get("living_entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("player").equals(checkLoc))));
            if(isCastReapplied) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("living_entity").local(), checkLoc.local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }

            var castInstr = input.getInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && ((it.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY && it.instruction().data().get("entity").equals(checkLoc)) || (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("entity").equals(checkLoc))));
            if(castInstr != null) {
                var remap = new HashMap<Integer, Integer>();
                if(castInstr.instruction().type() == InstructionTypes2.TRY_AS_LIVING_ENTITY) remap.put(instr.<LocationData<?>>get("entity").local(), castInstr.instruction().<LocationData<?>>get("living_entity").local());
                if(castInstr.instruction().type() == InstructionTypes2.TRY_AS_PLAYER) remap.put(instr.<LocationData<?>>get("entity").local(), castInstr.instruction().<LocationData<?>>get("player").local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }

            var alreadyIsType = input.anyInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER && it.instruction().data().get("entity").equals(checkLoc));
            if(alreadyIsType) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("living_entity").local(), instr.<LocationData<?>>get("entity").local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }
        }else if(instr.type() == InstructionTypes2.TRY_AS_PLAYER) {
            var checkLoc = instr.<LocationData<Object>>get("entity");
            var isCastReapplied = input.anyInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("player").equals(checkLoc)));
            if(isCastReapplied) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("player").local(), checkLoc.local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }

            var castInstr = input.getInstructionFromPath(it -> it.instruction().data().containsValue(checkLoc) && (it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER && it.instruction().data().get("entity").equals(checkLoc)));
            if(castInstr != null) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("entity").local(), castInstr.instruction().<LocationData<?>>get("player").local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }

            var alreadyIsType = input.anyInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER && it.instruction().data().get("entity").equals(checkLoc));
            if(alreadyIsType) {
                var remap = new HashMap<Integer, Integer>();
                remap.put(instr.<LocationData<?>>get("player").local(), instr.<LocationData<?>>get("entity").local());
                return OptimizationResult.remapWithFlags(remap, REMOVE_SELF_AND_INLINE_CHILDREN);
            }
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult upgradeEntityToPlayerSelector(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.FOR_EACH_ENTITY) {
            var entityLoc = instr.<LocationData<Object>>get("entity");
            var playerCast = input.getChild(it -> it.instruction().data().containsValue(entityLoc) && it.instruction().type() == InstructionTypes2.TRY_AS_PLAYER);
            if(playerCast != null) {
                var isLastElement = !input.anyInstructionFromPath(it -> it.path().depth() > playerCast.path().depth() && Objects.equals(input.thisPath().lastIndex(), input.getByPath(it.path().pathExceptLast()).instruction().children().size()));
                if(isLastElement) {
                    return new OptimizationResult(null, null, null, InstructionTypes2.FOR_EACH_PLAYER, null, REPLACE_SELF_TYPE);
                }
            }
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult mergePureInstructions(OptimizationInput input) {
        var instr = input.instruction();
        if(instr.type() == InstructionTypes2.GET_CURRENT_ENTITY) {
            var entityLoc = instr.<LocationData<Object>>get("entity");
            var previousCurrentEntity = input.getInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.GET_CURRENT_ENTITY);
            if(previousCurrentEntity != null) {
                return OptimizationResult.remapWithFlags(Map.of(entityLoc.local(), previousCurrentEntity.instruction().<LocationData<?>>get("entity").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
            }
        }else if(instr.type() == InstructionTypes2.GET_SCOREBOARD_NAME) {
            var entityLoc = instr.<LocationData<Object>>get("entity");
            var previousScoreboardName = input.getInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.GET_SCOREBOARD_NAME && it.instruction().get("entity").equals(entityLoc));
            if(previousScoreboardName != null) {
                var nameLoc = instr.<LocationData<Object>>get("name");
                return OptimizationResult.remapWithFlags(Map.of(nameLoc.local(), previousScoreboardName.instruction().<LocationData<?>>get("name").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
            }
        }else if(instr.type() == InstructionTypes2.GET_OBJECTIVE) {
            var nameLoc = instr.<String>get("name");
            var objectiveLoc = instr.<LocationData<Object>>get("objective");
            var previousObjectiveSetter = input.getInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.GET_OBJECTIVE && it.instruction().get("name").equals(nameLoc));
            if(previousObjectiveSetter != null) {
                var anyLoops = input.anyInstructionFromPath(it -> it.path().depth() > previousObjectiveSetter.path().depth() && (it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER || it.instruction().type() == InstructionTypes2.FOR_EACH_ENTITY || it.instruction().type() == InstructionTypes2.FOR_EACH_LIST_ELEMENT));
                if(!anyLoops) {
                    return OptimizationResult.remapWithFlags(Map.of(objectiveLoc.local(), previousObjectiveSetter.instruction().<LocationData<?>>get("objective").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
                }else {
                    var hasFunctionCalls = input.anyChildren(it -> it.type() == InstructionTypes.CALL_FUNCTIONS);
                    if(!hasFunctionCalls) {
                        return OptimizationResult.remapWithFlags(Map.of(objectiveLoc.local(), previousObjectiveSetter.instruction().<LocationData<?>>get("objective").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
                    }
                }
            }
        }else if(instr.type() == InstructionTypes2.SCORE_DATA_NO_AUTO_CREATE) {
            var nameLoc = instr.<LocationData<Object>>get("name");
            var objectiveLoc = instr.<LocationData<Object>>get("objective");
            var scoreLoc = instr.<LocationData<Object>>get("score");
            var previousScoreGetter = input.getInstructionFromPath(it -> (it.instruction().type() == InstructionTypes2.SCORE_DATA_NO_AUTO_CREATE || it.instruction().type() == InstructionTypes2.SCORE_DATA_AUTO_CREATE) && it.instruction().get("name").equals(nameLoc) && it.instruction().get("objective").equals(objectiveLoc));
            if(previousScoreGetter != null) {
                var anyLoops = input.anyInstructionFromPath(it -> it.path().depth() > previousScoreGetter.path().depth() && (it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER || it.instruction().type() == InstructionTypes2.FOR_EACH_ENTITY || it.instruction().type() == InstructionTypes2.FOR_EACH_LIST_ELEMENT));
                if(!anyLoops) {
                    return OptimizationResult.remapWithFlags(Map.of(scoreLoc.local(), previousScoreGetter.instruction().<LocationData<?>>get("score").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
                }else {
                    var hasFunctionCalls = input.anyChildren(it -> it.type() == InstructionTypes.CALL_FUNCTIONS);
                    if(!hasFunctionCalls) {
                        return OptimizationResult.remapWithFlags(Map.of(scoreLoc.local(), previousScoreGetter.instruction().<LocationData<?>>get("score").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
                    }
                }
            }
        }else if(instr.type() == InstructionTypes2.SCORE_DATA_AUTO_CREATE) {
            var nameLoc = instr.<LocationData<Object>>get("name");
            var objectiveLoc = instr.<LocationData<Object>>get("objective");
            var scoreLoc = instr.<LocationData<Object>>get("score");
            var previousScoreGetter = input.getInstructionFromPath(it -> it.instruction().type() == InstructionTypes2.SCORE_DATA_AUTO_CREATE && it.instruction().get("name").equals(nameLoc) && it.instruction().get("objective").equals(objectiveLoc));
            if(previousScoreGetter != null) {
                var anyLoops = input.anyInstructionFromPath(it -> it.path().depth() > previousScoreGetter.path().depth() && (it.instruction().type() == InstructionTypes2.FOR_EACH_PLAYER || it.instruction().type() == InstructionTypes2.FOR_EACH_ENTITY || it.instruction().type() == InstructionTypes2.FOR_EACH_LIST_ELEMENT));
                if(!anyLoops) {
                    return OptimizationResult.remapWithFlags(Map.of(scoreLoc.local(), previousScoreGetter.instruction().<LocationData<?>>get("score").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
                }else {
                    var hasFunctionCalls = input.anyChildren(it -> it.type() == InstructionTypes.CALL_FUNCTIONS);
                    if(!hasFunctionCalls) {
                        return OptimizationResult.remapWithFlags(Map.of(scoreLoc.local(), previousScoreGetter.instruction().<LocationData<?>>get("score").local()), REMOVE_SELF_AND_INLINE_CHILDREN);
                    }
                }
            }
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult combineObjectiveGetters(OptimizationInput input) {
        var instr = input.instruction();
        ArrayList<InstructionModification> modifications = new ArrayList<>();
        LocatedInstruction previous = null;
        for(int i = 0; i<instr.children().size(); i++) {
            var child = input.getByDirectIndex(i);
            if(child.instruction().type() == InstructionTypes2.GET_OBJECTIVE) {
                var canMerge = previous != null && Objects.equals(previous.instruction().data().get("name"), child.instruction().data().get("name"));
                if(canMerge) {
                    if(!Objects.equals(child.instruction().<LocationData<?>>get("objective").local(), previous.instruction().<LocationData<?>>get("objective").local())) {
                        int nextId = getFirstFreeLocal(input.usedLocations(), instr, child.path(), previous.path(), null);
                        modifications.add(new InstructionModification.MoveChildrenFromToNested(child.path(), previous.path(), Map.of(child.instruction().<LocationData<?>>get("objective").local(), previous.instruction().<LocationData<?>>get("objective").local(), previous.instruction().<LocationData<?>>get("objective").local(), nextId), false, false));
                    }else {
                        modifications.add(new InstructionModification.MoveChildrenFromToNested(child.path(), previous.path(), Map.of(child.instruction().<LocationData<?>>get("objective").local(), previous.instruction().<LocationData<?>>get("objective").local()), false, false));
                    }
                }
                if(!input.anyChildrenIn(child, it -> it.type() == InstructionTypes.CALL_FUNCTIONS)) {
                    if(!canMerge) {
                        previous = child;
                    }
                    continue;
                }
            }
            previous = null;
        }
        if(!modifications.isEmpty()) {
            return OptimizationResult.modificationsWithFlags(modifications, 0);
        }
        return OptimizationResultTypes.KEEP;
    }

    private static OptimizationResult combineNullChecks(OptimizationInput input) {
        var instr = input.instruction();
        ArrayList<InstructionModification> modifications = new ArrayList<>();
        LocatedInstruction previous = null;
        for(int i = 0; i<instr.children().size(); i++) {
            var child = input.getByDirectIndex(i);
            if(child.instruction().type() == InstructionTypes2.IF_NOT_NULL) {
                var canMerge = previous != null && Objects.equals(previous.instruction().data().get("local"), child.instruction().data().get("local"));
                if(canMerge) {
                    modifications.add(new InstructionModification.MoveChildrenFromToNested(child.path(), previous.path(), Map.of(), false, false));
                }
                if(!input.anyChildrenIn(child, it -> it.type() == InstructionTypes2.SET_NBT_VALUE && it.data().containsValue(child.instruction().data().get("local")))) {
                    if(!canMerge) {
                        previous = child;
                    }
                    continue;
                }
            }
            previous = null;
        }
        if(!modifications.isEmpty()) {
            return OptimizationResult.modificationsWithFlags(modifications, 0);
        }
        return OptimizationResultTypes.KEEP;
    }
}
