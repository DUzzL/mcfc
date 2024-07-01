package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommonSubexpressionEliminationOptimization implements Optimization {

    private record CommonSubexpressionEliminationData(
            Map<Integer, Integer> localMappings,
            Map<String, Integer> objectives,
            Map<Integer, Integer> scoreboardNames
    ) {
        public CommonSubexpressionEliminationData withLocalMapping(int oldMapping, int newMapping) {
            var newLocalMapping = new HashMap<>(localMappings);
            newLocalMapping.put(oldMapping, newMapping);
            return new CommonSubexpressionEliminationData(newLocalMapping, objectives, scoreboardNames);
        }
        public CommonSubexpressionEliminationData withObjectiveMapping(String name, int mapping) {
            var newObjectives = new HashMap<>(objectives);
            newObjectives.put(name, mapping);
            return new CommonSubexpressionEliminationData(localMappings, newObjectives, scoreboardNames);
        }
        public CommonSubexpressionEliminationData withScoreboardMapping(int scoreboard, int mapping) {
            var newScoreboardNames = new HashMap<>(scoreboardNames);
            newScoreboardNames.put(scoreboard, mapping);
            return new CommonSubexpressionEliminationData(localMappings, objectives, newScoreboardNames);
        }
    }

    public List<Instruction> optimize(List<Instruction> instructions) {
        return runOptimizations(instructions, new CommonSubexpressionEliminationData(Map.of(), Map.of(), Map.of()));
    }

    private List<Instruction> runOptimizations(List<Instruction> instructions, CommonSubexpressionEliminationData cse) {
        var results = new ArrayList<Instruction>();
        for(var instruction : instructions) {
            results.addAll(runOptimizations(instruction, cse));
        }
        return results;
    }

    private List<Instruction> runOptimizations(Instruction instruction, CommonSubexpressionEliminationData cse) {
        var data = Instruction.remapInstructionData(instruction.data(), cse.localMappings);

        if(instruction.type().equals(InstructionTypes2.GET_OBJECTIVE)) {
            var name = instruction.<String>get("name");
            var objective = instruction.<LocationData<?>>get("objective");
            if (cse.objectives.get(name) == null) {
                cse = cse.withObjectiveMapping(name, objective.local());
            } else {
                return runOptimizations(instruction.children(), cse.withLocalMapping(objective.local(), cse.objectives.get(name)));
            }
        }else if(instruction.type().equals(InstructionTypes2.GET_SCOREBOARD_NAME)) {
            var entity = instruction.<LocationData<?>>get("entity");
            var name = instruction.<LocationData<?>>get("name");
            if (cse.scoreboardNames.get(entity.local()) == null) {
                cse = cse.withScoreboardMapping(entity.local(), name.local());
            } else {
                return runOptimizations(instruction.children(), cse.withLocalMapping(name.local(), cse.scoreboardNames.get(entity.local())));
            }
        }

        return List.of(new Instruction(instruction.type(), data, runOptimizations(instruction.children(), cse)));
    }

}
