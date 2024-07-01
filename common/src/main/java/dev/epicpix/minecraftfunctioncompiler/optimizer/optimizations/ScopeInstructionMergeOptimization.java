package dev.epicpix.minecraftfunctioncompiler.optimizer.optimizations;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.optimizer.Optimization;

import java.util.ArrayList;
import java.util.List;

public final class ScopeInstructionMergeOptimization implements Optimization {
    public List<Instruction> optimize(List<Instruction> instructions) {
        return runOptimizations(instructions);
    }

    private List<Instruction> runOptimizations(List<Instruction> instructions) {
        var results = new ArrayList<Instruction>();
        var checkIndex = 0;
        while(checkIndex < instructions.size()) {
            var index = checkIndex;
            var compareBase = instructions.get(checkIndex);
            var newBase = new Instruction(compareBase.type(), compareBase.data(), compareBase.children());
            index++;
            while(index < instructions.size()) {
                var compare = instructions.get(index);
                if(compareBase.type() != compare.type() || compareBase.data() != compare.data()) break;
                if (compare.type() != InstructionTypes2.GET_CURRENT_ENTITY && compare.type() != InstructionTypes2.IF_NOT_NULL && (compare.type() != InstructionTypes2.GET_SCOREBOARD_NAME && compare.type() != InstructionTypes2.GET_OBJECTIVE && compare.type() != InstructionTypes2.SCORE_DATA_AUTO_CREATE) && compare.type() != InstructionTypes2.SCORE_DATA_NO_AUTO_CREATE) {
                    break;
                }
                newBase.children().addAll(compare.children());
                index++;
            }
            results.add(newBase.withChildren(new ArrayList<>(runOptimizations(newBase.children()))));
            checkIndex = index;
        }
        return results;
    }

}