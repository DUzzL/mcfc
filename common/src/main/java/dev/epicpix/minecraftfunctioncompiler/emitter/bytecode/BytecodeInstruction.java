package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import org.objectweb.asm.Type;

import java.util.List;

public sealed interface BytecodeInstruction permits BytecodeInstruction.CallTarget, BytecodeInstruction.Cast, BytecodeInstruction.ConditionalJump, BytecodeInstruction.CreateArray, BytecodeInstruction.InstanceCheckJump, BytecodeInstruction.Jump, BytecodeInstruction.Label, BytecodeInstruction.New, BytecodeInstruction.RegisterValue, BytecodeInstruction.RegisterValueValue, BytecodeInstruction.Return, BytecodeInstruction.ReturnVoid, BytecodeInstruction.SetArrayValue, BytecodeInstruction.StringConcat {
    int cost();

    record RegisterValue(InstructionRVType type, BytecodeRegister result, BytecodeValue source) implements BytecodeInstruction {
        public int cost() {
            return source.cost() + result().cost();
        }
    }

    record RegisterValueValue(InstructionRVVType type, BytecodeRegister result, BytecodeValue sourceA, BytecodeValue sourceB) implements BytecodeInstruction {
        public int cost() {
            return 1 + sourceA.cost() + sourceB.cost() + result().cost();
        }
    }

    record CreateArray(BytecodeRegister result, BytecodeValue length, Type contentType) implements BytecodeInstruction {
        public int cost() {
            return 2 + result().cost() + length.cost();
        }
    }

    record SetArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value) implements BytecodeInstruction {
        public int cost() {
            return 1 + array.cost() + index.cost() + value.cost();
        }
    }

    record InstanceCheckJump(boolean ifIsInstance, BytecodeValue a, Type type, BytecodeLabel label) implements BytecodeInstruction {
        public int cost() {
            return 3 + a.cost();
        }
    }

    record ConditionalJump(JumpCondition condition, BytecodeValue a, BytecodeValue b, BytecodeLabel label) implements BytecodeInstruction {
        public int cost() {
            return 3 + a.cost() + b.cost();
        }
    }

    record Jump(BytecodeLabel label) implements BytecodeInstruction {
        public int cost() {
            return 3;
        }
    }

    record Label(BytecodeLabel label) implements BytecodeInstruction {
        public int cost() {
            return 0;
        }
    }

    record CallTarget(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) implements BytecodeInstruction {
        public int cost() {
            return 3 + result().cost() + arguments.stream().mapToInt(BytecodeValue::cost).sum();
        }
    }

    record New(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) implements BytecodeInstruction {
        public int cost() {
            return 7 + result().cost() + arguments.stream().mapToInt(BytecodeValue::cost).sum();
        }
    }

    record Cast(BytecodeRegister result, BytecodeValue source, Type type) implements BytecodeInstruction {
        public int cost() {
            return 3 + result().cost() + source.cost();
        }
    }

    record StringConcat(BytecodeRegister result, List<BytecodeValue> values) implements BytecodeInstruction {
        public int cost() {
            return 3 + result().cost() + values.stream().mapToInt(BytecodeValue::cost).sum();
        }
    }

    record Return(BytecodeValue value) implements BytecodeInstruction {
        public int cost() {
            return 1 + value.cost();
        }
    }

    record ReturnVoid() implements BytecodeInstruction {
        public int cost() {
            return 1;
        }
    }

}
