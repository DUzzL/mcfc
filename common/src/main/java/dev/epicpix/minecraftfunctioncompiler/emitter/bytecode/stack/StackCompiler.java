package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.*;
import org.objectweb.asm.Type;

import java.util.*;

public class StackCompiler {

    private static Type getValueType(BytecodeValue value, HashMap<BytecodeRegister, Type> registerTypeMap) {
        if(value instanceof BytecodeValue.NullValue) {
            return Type.getType(Object.class);
        }else if(value instanceof BytecodeValue.IntegerValue) {
            return Type.INT_TYPE;
        }else if(value instanceof BytecodeValue.LongValue) {
            return Type.LONG_TYPE;
        }else if(value instanceof BytecodeValue.FloatValue) {
            return Type.FLOAT_TYPE;
        }else if(value instanceof BytecodeValue.DoubleValue) {
            return Type.DOUBLE_TYPE;
        }else if(value instanceof BytecodeValue.StringValue) {
            return Type.getType(String.class);
        }else if(value instanceof BytecodeValue.Parameter parameter) {
            return Type.getType(parameter.clazz());
        }else if(value instanceof BytecodeRegister register) {
            return registerTypeMap.get(register);
        }else {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }

    private static StackInstruction loadValue(BytecodeValue value, HashMap<BytecodeRegister, Type> registerTypeMap) {
        return new StackInstruction.Load(value, getValueType(value, registerTypeMap));
    }

    private static StackInstruction storeValue(BytecodeRegister value, Type type, HashMap<BytecodeRegister, Type> registerTypeMap) {
        registerTypeMap.put(value, type);
        return new StackInstruction.Store(value);
    }

    public static List<StackInstruction> compileInstructions(List<BytecodeInstruction> instructions) {
        ArrayList<StackInstruction> results = new ArrayList<>();
        HashMap<BytecodeRegister, Type> registerTypeMap = new HashMap<>();
        for(var instr : instructions) {
            if(instr instanceof BytecodeInstruction.RegisterValue v) {
                if(v.type() == InstructionRVType.SET) {
                    var sourceType = getValueType(v.source(), registerTypeMap);
                    results.add(new StackInstruction.Load(v.source(), sourceType));
                    results.add(storeValue(v.result(), sourceType, registerTypeMap));
                }else {
                    throw new IllegalArgumentException("Unknown instruction RegisterValue type: " + v.type());
                }
            }else if(instr instanceof BytecodeInstruction.RegisterValueValue v) {
                var sourceType = getValueType(v.sourceA(), registerTypeMap);
                results.add(new StackInstruction.Load(v.sourceA(), sourceType));
                results.add(new StackInstruction.Load(v.sourceB(), sourceType));
                if(v.type() == InstructionRVVType.ADD) {
                    results.add(new StackInstruction.Evaluate(StackEvaluationType.ADD));
                }else if(v.type() == InstructionRVVType.SUB) {
                    results.add(new StackInstruction.Evaluate(StackEvaluationType.SUB));
                }else if(v.type() == InstructionRVVType.MUL) {
                    results.add(new StackInstruction.Evaluate(StackEvaluationType.MUL));
                }else {
                    throw new IllegalArgumentException("Unknown instruction RegisterValueValue type: " + v.type());
                }
                results.add(storeValue(v.result(), sourceType, registerTypeMap));
            }else if(instr instanceof BytecodeInstruction.CreateArray v) {
                results.add(loadValue(v.length(), registerTypeMap));
                results.add(new StackInstruction.NewArray(Type.getType(v.contentType())));
                results.add(storeValue(v.result(), Type.getType(v.contentType().arrayType()), registerTypeMap));
            }else if(instr instanceof BytecodeInstruction.SetArrayValue v) {
                results.add(loadValue(v.array(), registerTypeMap));
                results.add(loadValue(v.index(), registerTypeMap));
                results.add(loadValue(v.value(), registerTypeMap));
                results.add(new StackInstruction.ArrayStore());
            }else if(instr instanceof BytecodeInstruction.InstanceCheckJump v) {
                results.add(loadValue(v.a(), registerTypeMap));
                results.add(new StackInstruction.JumpTyped(v.ifIsInstance() ? StackJumpType.IF_INSTANCEOF : StackJumpType.IF_NOT_INSTANCEOF, Type.getType(v.clazz()), v.label()));
            }else if(instr instanceof BytecodeInstruction.ConditionalJump v) {
                results.add(loadValue(v.a(), registerTypeMap));
                var other = v.b();
                if((other instanceof BytecodeValue.IntegerValue vbi && vbi.value() == 0) || (other instanceof BytecodeValue.LongValue vbl && vbl.value() == 0) || (other instanceof BytecodeValue.FloatValue vbf && vbf.value() == 0) || (other instanceof BytecodeValue.DoubleValue vbd && vbd.value() == 0)) {
                    results.add(new StackInstruction.Jump(switch(v.condition()) {
                        case EQ -> StackJumpType.IF_EQUAL_ZERO;
                        case NE -> StackJumpType.IF_NOT_EQUAL_ZERO;
                        case LT -> StackJumpType.IF_LESS_THAN_ZERO;
                        case LE -> StackJumpType.IF_LESS_EQUAL_ZERO;
                        case GT -> StackJumpType.IF_GREATER_THAN_ZERO;
                        case GE -> StackJumpType.IF_GREATER_EQUAL_ZERO;
                    }, v.label()));
                    continue;
                }
                if(other instanceof BytecodeValue.NullValue) {
                    results.add(new StackInstruction.Jump(switch(v.condition()) {
                        case EQ -> StackJumpType.IF_NULL;
                        case NE -> StackJumpType.IF_NOT_NULL;
                        default -> throw new IllegalArgumentException("Invalid condition for comparison with null: " + v.condition());
                    }, v.label()));
                    continue;
                }
                results.add(loadValue(other, registerTypeMap));
                results.add(new StackInstruction.Jump(switch(v.condition()) {
                    case EQ -> StackJumpType.IF_EQUAL;
                    case NE -> StackJumpType.IF_NOT_EQUAL;
                    case LT -> StackJumpType.IF_LESS_THAN;
                    case LE -> StackJumpType.IF_LESS_EQUAL;
                    case GT -> StackJumpType.IF_GREATER_THAN;
                    case GE -> StackJumpType.IF_GREATER_EQUAL;
                }, v.label()));
            }else if(instr instanceof BytecodeInstruction.Jump v) {
                results.add(new StackInstruction.Jump(StackJumpType.GOTO, v.label()));
            }else if(instr instanceof BytecodeInstruction.Label v) {
                results.add(new StackInstruction.Label(v.label()));
            }else if(instr instanceof BytecodeInstruction.CallTarget v) {
                if(v.target() instanceof InstructionTarget.FunctionTarget f) {
                    for(var arg : v.arguments()) {
                        results.add(loadValue(arg, registerTypeMap));
                    }
                    results.add(new StackInstruction.Call(f));
                    var returnType = Type.getReturnType(f.descriptor());
                    if(returnType != Type.VOID_TYPE) {
                        results.add(storeValue(v.result(), returnType, registerTypeMap));
                    }
                }else if(v.target() instanceof InstructionTarget.FieldTarget f) {
                    for(var arg : v.arguments()) {
                        results.add(loadValue(arg, registerTypeMap));
                    }
                    results.add(new StackInstruction.Call(f));
                    if(!f.isWrite()) {
                        results.add(storeValue(v.result(), Type.getType(f.descriptor()), registerTypeMap));
                    }
                }
            }else if(instr instanceof BytecodeInstruction.New v) {
                var f = (InstructionTarget.FunctionTarget) v.target();
                results.add(new StackInstruction.New(Type.getType("L" + f.className() + ";")));
                results.add(storeValue(v.result(), Type.getType("L" + f.className() + ";"), registerTypeMap));
                results.add(loadValue(v.result(), registerTypeMap));
                for(var arg : v.arguments()) {
                    results.add(loadValue(arg, registerTypeMap));
                }
                results.add(new StackInstruction.Call(f));
            }else if(instr instanceof BytecodeInstruction.Cast v) {
                results.add(loadValue(v.source(), registerTypeMap));
                results.add(new StackInstruction.Cast(Type.getType(v.clazz())));
                results.add(storeValue(v.result(), Type.getType(v.clazz()), registerTypeMap));
            }else if(instr instanceof BytecodeInstruction.StringConcat v) {
                ArrayList<String> staticStrings = new ArrayList<>();
                boolean isConstant = true;
                boolean onlyStrings = true;
                for(var value : v.values()) {
                    if(value instanceof BytecodeValue.StringValue sv) {
                        staticStrings.add(sv.value());
                        isConstant = false;
                    }else {
                        staticStrings.add(null);
                        var valueType = getValueType(value, registerTypeMap);
                        onlyStrings &= valueType.equals(Type.getType(String.class));
                        results.add(loadValue(value, registerTypeMap));
                    }
                }
                if(!isConstant) {
                    if(!onlyStrings || staticStrings.size() != 1) {
                        results.add(new StackInstruction.EvaluateString(staticStrings.toArray(new String[0])));
                    }
                }else {
                    results.add(new StackInstruction.Load(new BytecodeValue.StringValue(String.join("", staticStrings)), Type.getType(String.class)));
                }
                results.add(storeValue(v.result(), Type.getType(String.class), registerTypeMap));
            }else if(instr instanceof BytecodeInstruction.Return v) {
                results.add(loadValue(v.value(), registerTypeMap));
                results.add(new StackInstruction.ReturnValue());
            }else if(instr instanceof BytecodeInstruction.ReturnVoid) {
                results.add(new StackInstruction.Return());
            }else {
                throw new IllegalArgumentException("Unknown instruction: " + instr);
            }
        }
        var registerStoreCount = new HashMap<BytecodeRegister, Integer>();
        var registerLoadCount = new HashMap<BytecodeRegister, Integer>();
        for(var instr : results) {
            if(instr instanceof StackInstruction.Load load && load.value() instanceof BytecodeRegister register) {
                registerLoadCount.put(register, registerLoadCount.getOrDefault(register, 0) + 1);
            }
            if(instr instanceof StackInstruction.Store store) {
                registerStoreCount.put(store.register(), registerStoreCount.getOrDefault(store.register(), 0) + 1);
            }
        }

        var labelLocations = new HashMap<BytecodeLabel, Integer>();
        for(int i = 0; i<results.size(); i++) {
            if(results.get(i) instanceof StackInstruction.Label label) {
                labelLocations.put(label.label(), i);
            }
        }

        var inlineBreaks = new HashSet<Integer>();
        var singleStoreRegs = new HashMap<BytecodeRegister, Integer>();
        for(int i = 0; i<results.size(); i++) {
            var instr = results.get(i);
            if(instr instanceof StackInstruction.Jump jump) {
                inlineBreaks.add(i);
                inlineBreaks.add(labelLocations.get(jump.label()));
            }else if(instr instanceof StackInstruction.JumpTyped jump) {
                inlineBreaks.add(i);
                inlineBreaks.add(labelLocations.get(jump.label()));
            }else if(instr instanceof StackInstruction.Return || instr instanceof StackInstruction.ReturnValue) {
                inlineBreaks.add(i);
            }else if(instr instanceof StackInstruction.Store store) {
                if(registerStoreCount.get(store.register()) == 1) {
                    singleStoreRegs.put(store.register(), i);
                }
            }
        }

        var dependencyData = new HashMap<BytecodeRegister, List<Integer>>();
        DependencyGenerator gen = new DependencyGenerator(results, inlineBreaks);
        var ssrSorted = singleStoreRegs.keySet().stream().sorted(Comparator.comparingInt(BytecodeRegister::reg)).toList();
        for(var ssr : ssrSorted) {
            var ssrIndex = singleStoreRegs.get(ssr);
            gen.index = ssrIndex - 1;
            ArrayList<Integer> dependencies = new ArrayList<>();
            if(gen.findDependencies(dependencies, -1)) {
                dependencyData.put(ssr, dependencies);
            }
        }

        ArrayList<StackInstruction> tempInstructions = new ArrayList<>();

        for(StackInstruction instr : results) {
            if(instr instanceof StackInstruction.Store store) {
                var dep = dependencyData.get(store.register());
                if(dep != null && registerLoadCount.getOrDefault(store.register(), 0) == 1) {
                    for(int i = 0; i < dep.size(); i++) {
                        tempInstructions.remove(tempInstructions.size() - 1);
                    }
                    ArrayList<StackInstruction> instrs = new ArrayList<>();
                    for(var d : dep) {
                        instrs.add(results.get(d));
                    }
                    tempInstructions.add(new InlineEvalStore(store.register(), instrs));
                    continue;
                }
            }
            tempInstructions.add(instr);
        }

        for(int c = 0; c<tempInstructions.size(); c++) {
            var temp = tempInstructions.get(c);
            if(temp instanceof StackInstruction.Load load && load.value() instanceof BytecodeRegister register) {
                int w;
                InlineEvalStore found = null;
                for(w = 0; w<c; w++) {
                    if(tempInstructions.get(w) instanceof InlineEvalStore i && i.reg.equals(register)) {
                        found = i;
                        break;
                    }
                }
                if(found != null) {
                    if(isValidInline(tempInstructions.subList(w + 1, c), found)) {
                        tempInstructions.addAll(c + 1, found.instructions);
                        tempInstructions.remove(c);
                        tempInstructions.remove(w);
                        c = w;
                    }
                }
            }else if(temp instanceof InlineEvalStore store) {
                for(var in = 0; in<store.instructions.size(); in++) {
                    var t = store.instructions.get(in);
                    if(t instanceof StackInstruction.Load load && load.value() instanceof BytecodeRegister register) {
                        int w;
                        InlineEvalStore found = null;
                        for(w = 0; w<c; w++) {
                            if(tempInstructions.get(w) instanceof InlineEvalStore i && i.reg.equals(register)) {
                                found = i;
                                break;
                            }
                        }
                        if(found != null) {
                            if(isValidInline(tempInstructions.subList(w + 1, c), found)) {
                                store.instructions.addAll(in + 1, found.instructions);
                                store.instructions.remove(in);
                                tempInstructions.remove(w);
                                c = w;
                                in--;
                            }
                        }
                    }
                }
            }
        }

        ArrayList<StackInstruction> output = new ArrayList<>();
        for(var temp : tempInstructions) {
            if(temp instanceof InlineEvalStore i) {
                output.addAll(i.instructions);
                if(registerLoadCount.getOrDefault(i.reg, 0) != 0) {
                    output.add(new StackInstruction.Store(i.reg));
                }else {
                    output.add(new StackInstruction.Pop());
                }
                continue;
            }else if(temp instanceof StackInstruction.Store store) {
                if(registerLoadCount.getOrDefault(store.register(), 0) != 0) {
                    output.add(new StackInstruction.Store(store.register()));
                }else {
                    output.add(new StackInstruction.Pop());
                }
                continue;
            }
            output.add(temp);
        }

        return fixRegisterOffsets(output, registerTypeMap);
    }

    private static List<StackInstruction> fixRegisterOffsets(List<StackInstruction> instrs, HashMap<BytecodeRegister, Type> registerTypeMap) {
        var params = new ArrayList<BytecodeValue.Parameter>();
        var usedRegisters = new HashSet<BytecodeRegister>();
        for(var i : instrs) {
            if(i instanceof StackInstruction.Load load) {
                if(load.value() instanceof BytecodeValue.Parameter param) {
                    params.add(param);
                }else if(load.value() instanceof BytecodeRegister register) {
                    usedRegisters.add(register);
                }
            }else if(i instanceof StackInstruction.Store store) {
                usedRegisters.add(store.register());
            }
        }
        var baseReg = params.stream().max(Comparator.comparingInt(BytecodeValue.Parameter::index)).map(BytecodeValue.Parameter::index).orElse(-1) + 1;
        var mappedRegisters = new HashMap<BytecodeRegister, BytecodeRegister>();
        int regIndex = baseReg;
        for(var reg : usedRegisters.stream().sorted(Comparator.comparingInt(BytecodeRegister::reg)).toList()) {
            mappedRegisters.put(reg, new BytecodeRegister(regIndex));
            var t = registerTypeMap.get(reg);
            if(t != null) {
                if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE) {
                    regIndex++;
                }
            }
            regIndex++;
        }
        ArrayList<StackInstruction> output = new ArrayList<>(instrs);
        for(int i = 0; i<instrs.size(); i++) {
            var instr = instrs.get(i);
            if(instr instanceof StackInstruction.Load load && load.value() instanceof BytecodeRegister register) {
                output.set(i, new StackInstruction.Load(mappedRegisters.get(register), load.type()));
            }else if(instr instanceof StackInstruction.Store store) {
                output.set(i, new StackInstruction.Store(mappedRegisters.get(store.register())));
            }
        }
        return output;
    }

    private static boolean isValidInline(List<StackInstruction> instructionsBetween, InlineEvalStore store) {
        var uses = new HashSet<BytecodeRegister>();
        for(var i : store.instructions) {
            if(i instanceof StackInstruction.Load load && load.value() instanceof BytecodeRegister register) {
                uses.add(register);
            }else if(i instanceof StackInstruction.Call call && call.target() instanceof InstructionTarget.FunctionTarget target && target.noReordering()) {
                return false;
            }
        }
        for(var i : instructionsBetween) {
            if(i instanceof StackInstruction.Store s && uses.contains(s.register())) {
                return false;
            }else if(i instanceof StackInstruction.Label || i instanceof StackInstruction.Jump || i instanceof StackInstruction.JumpTyped || i instanceof StackInstruction.Return || i instanceof StackInstruction.ReturnValue) {
                return false;
            }
        }
        return true;
    }

    private static class DependencyGenerator {
        private final List<StackInstruction> instructions;
        private final Set<Integer> invalidInstructions;
        public int index;

        public DependencyGenerator(List<StackInstruction> instructions, Set<Integer> invalidInstructions) {
            this.instructions = instructions;
            this.invalidInstructions = invalidInstructions;
        }

        public boolean findDependencies(ArrayList<Integer> dependencies, int stackDepth) {
            while(stackDepth != 0) {
                if(invalidInstructions.contains(index)) return false;
                var instr = instructions.get(index);
                if(instr instanceof StackInstruction.Load) {
                    stackDepth++;
                }else if(instr instanceof StackInstruction.Call c) {
                    if(c.target() instanceof InstructionTarget.FunctionTarget f) {
                        var fdesc = Type.getType(f.descriptor());
                        int consumed = fdesc.getArgumentCount() + (f.isStatic() ? 0 : 1);
                        int returned = fdesc.getReturnType() == Type.VOID_TYPE ? 0 : 1;
                        stackDepth += returned - consumed;
                    }else if(c.target() instanceof InstructionTarget.FieldTarget f) {
                        if(!f.isStatic()) stackDepth--;
                        stackDepth += f.isWrite() ? -1 : 1;
                    }else {
                        return false;
                    }
                }else if(instr instanceof StackInstruction.New) {
                    stackDepth++;
                }else if(instr instanceof StackInstruction.Pop) {
                    stackDepth--;
                }else if(instr instanceof StackInstruction.Evaluate e) {
                    if(e.evaluationType() == StackEvaluationType.ADD || e.evaluationType() == StackEvaluationType.SUB || e.evaluationType() == StackEvaluationType.MUL) {
                        stackDepth--;
                    }else {
                        return false;
                    }
                }else if(!(instr instanceof StackInstruction.Cast)) {
                    return false;
                }
                dependencies.add(0, index);
                index--;
            }
            return true;
        }

    }

    record InlineEvalStore(BytecodeRegister reg, ArrayList<StackInstruction> instructions) implements StackInstruction {}

}
