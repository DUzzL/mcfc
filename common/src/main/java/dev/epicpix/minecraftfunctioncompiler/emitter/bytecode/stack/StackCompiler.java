package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack;

import dev.epicpix.minecraftfunctioncompiler.CommonConstants;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.InstructionRVType;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.InstructionRVVType;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.JumpCondition;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StackCompiler {

    private static ClassDesc getValueType(BytecodeValue value, Int2ObjectMap<ClassDesc> registerTypeMap) {
        return switch(value) {
            case BytecodeValue.NullValue _ -> ConstantDescs.CD_Object;
            case BytecodeValue.IntegerValue _ -> ConstantDescs.CD_int;
            case BytecodeValue.LongValue _ -> ConstantDescs.CD_long;
            case BytecodeValue.FloatValue _ -> ConstantDescs.CD_float;
            case BytecodeValue.DoubleValue _ -> ConstantDescs.CD_double;
            case BytecodeValue.StringValue _ -> ConstantDescs.CD_String;
            case BytecodeValue.Parameter parameter -> parameter.type();
            case BytecodeRegister(int reg) -> registerTypeMap.get(reg);
            case null -> throw new IllegalArgumentException("Unknown value: " + value);
        };
    }

    private static StackInstruction loadValue(BytecodeValue value, Int2ObjectMap<ClassDesc> registerTypeMap) {
        return new StackInstruction.Load(value, getValueType(value, registerTypeMap));
    }

    private static StackInstruction storeValue(BytecodeRegister value, ClassDesc type, Int2ObjectMap<ClassDesc> registerTypeMap) {
        registerTypeMap.put(value.reg(), type);
        return new StackInstruction.Store(value);
    }

    public static List<StackInstruction> compileInstructions(List<BytecodeInstruction> instructions) {
        ArrayList<StackInstruction> results = new ArrayList<>();
        var registerTypeMap = new Int2ObjectOpenHashMap<ClassDesc>();
        for(var instr : instructions) {
            switch(instr) {
                case BytecodeInstruction.RegisterValue(InstructionRVType type, BytecodeRegister result, BytecodeValue source) -> {
                    if(type == InstructionRVType.SET) {
                        var sourceType = getValueType(source, registerTypeMap);
                        results.add(new StackInstruction.Load(source, sourceType));
                        results.add(storeValue(result, sourceType, registerTypeMap));
                    }else {
                        throw new IllegalArgumentException("Unknown instruction RegisterValue type: " + type);
                    }
                }
                case BytecodeInstruction.RegisterValueValue(InstructionRVVType type, BytecodeRegister result, BytecodeValue sourceA, BytecodeValue sourceB) -> {
                    var sourceType = getValueType(sourceA, registerTypeMap);
                    results.add(new StackInstruction.Load(sourceA, sourceType));
                    results.add(new StackInstruction.Load(sourceB, sourceType));
                    if(type == InstructionRVVType.ADD) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.ADD));
                    }else if(type == InstructionRVVType.SUB) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.SUB));
                    }else if(type == InstructionRVVType.MUL) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.MUL));
                    }else if(type == InstructionRVVType.DIV) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.DIV));
                    }else if(type == InstructionRVVType.MOD) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.MOD));
                    }else if(type == InstructionRVVType.SHL) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.SHL));
                    }else if(type == InstructionRVVType.SHR) {
                        results.add(new StackInstruction.Evaluate(StackEvaluationType.SHR));
                    }else {
                        throw new IllegalArgumentException("Unknown instruction RegisterValueValue type: " + type);
                    }
                    results.add(storeValue(result, sourceType, registerTypeMap));
                }
                case BytecodeInstruction.CreateArray(BytecodeRegister result, BytecodeValue length, ClassDesc contentType) -> {
                    results.add(loadValue(length, registerTypeMap));
                    results.add(new StackInstruction.NewArray(contentType));
                    results.add(storeValue(result, contentType.arrayType(), registerTypeMap));
                }
                case BytecodeInstruction.SetArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value1) -> {
                    results.add(loadValue(array, registerTypeMap));
                    results.add(loadValue(index, registerTypeMap));
                    results.add(loadValue(value1, registerTypeMap));
                    results.add(new StackInstruction.ArrayStore());
                }
                case BytecodeInstruction.InstanceCheckJump(boolean ifIsInstance, BytecodeValue a, ClassDesc type, BytecodeLabel label) -> {
                    results.add(loadValue(a, registerTypeMap));
                    results.add(new StackInstruction.JumpTyped(ifIsInstance ? StackJumpType.IF_INSTANCEOF : StackJumpType.IF_NOT_INSTANCEOF, type, label));
                }
                case BytecodeInstruction.ConditionalJump(JumpCondition condition, BytecodeValue a, BytecodeValue other, BytecodeLabel label) -> {
                    results.add(loadValue(a, registerTypeMap));
                    if((other instanceof BytecodeValue.IntegerValue bvi && bvi.value() == 0) || (other instanceof BytecodeValue.LongValue bvl && bvl.value() == 0) || (other instanceof BytecodeValue.FloatValue bvf && bvf.value() == 0) || (other instanceof BytecodeValue.DoubleValue bvd && bvd.value() == 0)) {
                        results.add(new StackInstruction.Jump(switch(condition) {
                            case EQ -> StackJumpType.IF_EQUAL_ZERO;
                            case NE -> StackJumpType.IF_NOT_EQUAL_ZERO;
                            case LT -> StackJumpType.IF_LESS_THAN_ZERO;
                            case LE -> StackJumpType.IF_LESS_EQUAL_ZERO;
                            case GT -> StackJumpType.IF_GREATER_THAN_ZERO;
                            case GE -> StackJumpType.IF_GREATER_EQUAL_ZERO;
                        }, label));
                        continue;
                    }
                    if(other instanceof BytecodeValue.NullValue) {
                        var aType = getValueType(a, registerTypeMap);
                        // ifnull/ifnonnull require a reference type on the stack.
                        // If 'a' is a primitive (can happen when the optimizer constant-folds
                        // an object register into an integer value), fall back to zero comparison.
                        if(aType == ConstantDescs.CD_int || aType == ConstantDescs.CD_long ||
                           aType == ConstantDescs.CD_float || aType == ConstantDescs.CD_double ||
                           aType == ConstantDescs.CD_boolean || aType == ConstantDescs.CD_byte ||
                           aType == ConstantDescs.CD_short || aType == ConstantDescs.CD_char) {
                            results.add(new StackInstruction.Jump(switch(condition) {
                                case EQ -> StackJumpType.IF_EQUAL_ZERO;
                                case NE -> StackJumpType.IF_NOT_EQUAL_ZERO;
                                default -> throw new IllegalArgumentException("Invalid condition for primitive comparison with null: " + condition);
                            }, label));
                        } else {
                            results.add(new StackInstruction.Jump(switch(condition) {
                                case EQ -> StackJumpType.IF_NULL;
                                case NE -> StackJumpType.IF_NOT_NULL;
                                default -> throw new IllegalArgumentException("Invalid condition for comparison with null: " + condition);
                            }, label));
                        }
                        continue;
                    }
                    results.add(loadValue(other, registerTypeMap));
                    results.add(new StackInstruction.Jump(switch(condition) {
                        case EQ -> StackJumpType.IF_EQUAL;
                        case NE -> StackJumpType.IF_NOT_EQUAL;
                        case LT -> StackJumpType.IF_LESS_THAN;
                        case LE -> StackJumpType.IF_LESS_EQUAL;
                        case GT -> StackJumpType.IF_GREATER_THAN;
                        case GE -> StackJumpType.IF_GREATER_EQUAL;
                    }, label));
                }
                case BytecodeInstruction.Jump(BytecodeLabel label) -> results.add(new StackInstruction.Jump(StackJumpType.GOTO, label));
                case BytecodeInstruction.Label(BytecodeLabel label) -> results.add(new StackInstruction.Label(label));
                case BytecodeInstruction.CallTarget(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                    if(target instanceof InstructionTarget.FunctionTarget f) {
                        for(var arg : arguments) {
                            results.add(loadValue(arg, registerTypeMap));
                        }
                        results.add(new StackInstruction.Call(f));
                        var returnType = f.descriptor().returnType();
                        if(returnType != ConstantDescs.CD_void) {
                            results.add(storeValue(result, returnType, registerTypeMap));
                        }
                    }else if(target instanceof InstructionTarget.FieldTarget f) {
                        for(var arg : arguments) {
                            results.add(loadValue(arg, registerTypeMap));
                        }
                        results.add(new StackInstruction.Call(f));
                        if(!f.isWrite()) {
                            results.add(storeValue(result, f.descriptor(), registerTypeMap));
                        }
                    }
                }
                case BytecodeInstruction.New(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                    var f = (InstructionTarget.FunctionTarget) target;
                    results.add(new StackInstruction.New(f.classType()));
                    results.add(storeValue(result, f.classType(), registerTypeMap));
                    results.add(loadValue(result, registerTypeMap));
                    for(var arg : arguments) {
                        results.add(loadValue(arg, registerTypeMap));
                    }
                    results.add(new StackInstruction.Call(f));
                }
                case BytecodeInstruction.Cast(BytecodeRegister result, BytecodeValue source, ClassDesc type) -> {
                    results.add(loadValue(source, registerTypeMap));
                    results.add(new StackInstruction.Cast(type));
                    results.add(storeValue(result, type, registerTypeMap));
                }
                case BytecodeInstruction.StringConcat(BytecodeRegister result, List<BytecodeValue> values) -> {
                    ArrayList<@Nullable String> staticStrings = new ArrayList<>();
                    boolean isConstant = true;
                    boolean onlyStrings = true;
                    for(var value : values) {
                        if(value instanceof BytecodeValue.StringValue(String staticString)) {
                            staticStrings.add(staticString);
                        }else {
                            isConstant = false;
                            staticStrings.add(null);
                            var valueType = getValueType(value, registerTypeMap);
                            onlyStrings &= valueType.equals(ConstantDescs.CD_String);
                            results.add(loadValue(value, registerTypeMap));
                        }
                    }
                    if(!isConstant) {
                        if(!onlyStrings || staticStrings.size() != 1) {
                            results.add(new StackInstruction.EvaluateString(staticStrings.toArray(new @Nullable String[0])));
                        }
                    }else {
                        results.add(new StackInstruction.Load(new BytecodeValue.StringValue(String.join("", staticStrings)), ConstantDescs.CD_String));
                    }
                    results.add(storeValue(result, ConstantDescs.CD_String, registerTypeMap));
                }
                case BytecodeInstruction.Return(BytecodeValue value) -> {
                    results.add(loadValue(value, registerTypeMap));
                    results.add(new StackInstruction.ReturnValue());
                }
                case BytecodeInstruction.ReturnVoid _ -> results.add(new StackInstruction.Return());
                case null, default -> throw new IllegalArgumentException("Unknown instruction: " + instr);
            }
        }
        var registerStoreCount = new Int2IntOpenHashMap();
        var registerLoadCount = new Int2IntOpenHashMap();
        for(var instr : results) {
            if(instr instanceof StackInstruction.Load load && load.value() instanceof BytecodeRegister(int reg)) {
                registerLoadCount.put(reg, registerLoadCount.getOrDefault(reg, 0) + 1);
            }
            if(instr instanceof StackInstruction.Store(BytecodeRegister register)) {
                registerStoreCount.put(register.reg(), registerStoreCount.getOrDefault(register.reg(), 0) + 1);
            }
        }

        var labelLocations = new Int2IntOpenHashMap();
        var inlineBreaks = new IntOpenHashSet();
        var singleStoreRegs = new Int2IntOpenHashMap();
        for(int i = 0; i<results.size(); i++) {
            var instr = results.get(i);
            if(instr instanceof StackInstruction.Label(BytecodeLabel label)) {
                labelLocations.put(label.id(), i);
            }else if(instr instanceof StackInstruction.Jump jump) {
                inlineBreaks.add(i);
                inlineBreaks.add(labelLocations.get(jump.label().id()));
            }else if(instr instanceof StackInstruction.JumpTyped jump) {
                inlineBreaks.add(i);
                inlineBreaks.add(labelLocations.get(jump.label().id()));
            }else if(instr instanceof StackInstruction.Return || instr instanceof StackInstruction.ReturnValue) {
                inlineBreaks.add(i);
            }else if(instr instanceof StackInstruction.Store(BytecodeRegister register)) {
                if(registerStoreCount.get(register.reg()) == 1) {
                    singleStoreRegs.put(register.reg(), i);
                }
            }
        }

        var dependencyData = new Int2ObjectOpenHashMap<@Nullable IntList>();
        DependencyGenerator gen = new DependencyGenerator(results, inlineBreaks);
        var ssrSorted = singleStoreRegs.keySet().intStream().sorted();
        for(var it = ssrSorted.iterator(); it.hasNext(); ) {
            var ssr = it.nextInt();
            var ssrIndex = singleStoreRegs.get(ssr);
            gen.index = ssrIndex - 1;
            var dependencies = new IntArrayList();
            if(gen.findDependencies(dependencies, -1)) {
                dependencyData.put(ssr, dependencies);
            }
        }

        ArrayList<StackInstruction> tempInstructions = new ArrayList<>();

        for(StackInstruction instr : results) {
            if(instr instanceof StackInstruction.Store(BytecodeRegister register)) {
                var dep = dependencyData.get(register.reg());
                if(dep != null && registerLoadCount.getOrDefault(register.reg(), 0) == 1) {
                    for(int i = 0; i < dep.size(); i++) {
                        tempInstructions.removeLast();
                    }
                    ArrayList<StackInstruction> instrs = new ArrayList<>();
                    for(var d : dep) {
                        instrs.add(results.get(d));
                    }
                    tempInstructions.add(new InlineEvalStore(register, instrs));
                    continue;
                }
            }
            tempInstructions.add(instr);
        }

        tempInstructions = new Inliner().run(tempInstructions);

        ArrayList<StackInstruction> output = new ArrayList<>();
        for(var temp : tempInstructions) {
            if(temp instanceof InlineEvalStore(BytecodeRegister reg, ArrayList<StackInstruction> instructions1)) {
                output.addAll(instructions1);
                if(registerLoadCount.getOrDefault(reg.reg(), 0) != 0) {
                    output.add(new StackInstruction.Store(reg));
                }else {
                    output.add(new StackInstruction.Pop());
                }
                continue;
            }else if(temp instanceof StackInstruction.Store(BytecodeRegister register)) {
                if(registerLoadCount.getOrDefault(register.reg(), 0) != 0) {
                    output.add(new StackInstruction.Store(register));
                }else {
                    output.add(new StackInstruction.Pop());
                }
                continue;
            }
            output.add(temp);
        }

        return fixRegisterOffsets(output, registerTypeMap);
    }

    private static List<StackInstruction> fixRegisterOffsets(List<StackInstruction> instrs, Int2ObjectMap<@Nullable ClassDesc> registerTypeMap) {
        var params = new ArrayList<BytecodeValue.Parameter>();
        var usedRegisters = new IntOpenHashSet();
        for(var i : instrs) {
            if(i instanceof StackInstruction.Load load) {
                if(load.value() instanceof BytecodeValue.Parameter param) {
                    params.add(param);
                }else if(load.value() instanceof BytecodeRegister(int reg)) {
                    usedRegisters.add(reg);
                }
            }else if(i instanceof StackInstruction.Store(BytecodeRegister register)) {
                usedRegisters.add(register.reg());
            }
        }
        // Parameter.index is the JVM slot; long/double parameters take two slots.
        int baseReg = 0;
        for(var param : params) {
            baseReg = Math.max(baseReg, param.index() + TypeKind.from(param.type()).slotSize());
        }
        var mappedRegisters = new Int2IntOpenHashMap();
        int regIndex = baseReg;
        for(var it = usedRegisters.intStream().sorted().iterator(); it.hasNext(); ) {
            var reg = it.nextInt();
            mappedRegisters.put(reg, regIndex);
            var t = registerTypeMap.get(reg);
            if(t != null) {
                if(t == ConstantDescs.CD_long || t == ConstantDescs.CD_double) {
                    regIndex++;
                }
            }
            regIndex++;
        }
        ArrayList<StackInstruction> output = new ArrayList<>(instrs);
        for(int i = 0; i<instrs.size(); i++) {
            var instr = instrs.get(i);
            if(instr instanceof StackInstruction.Load(BytecodeValue value, ClassDesc type) && value instanceof BytecodeRegister(int reg)) {
                output.set(i, new StackInstruction.Load(new BytecodeRegister(mappedRegisters.get(reg)), type));
            }else if(instr instanceof StackInstruction.Store(BytecodeRegister register)) {
                output.set(i, new StackInstruction.Store(new BytecodeRegister(mappedRegisters.get(register.reg()))));
            }
        }
        return output;
    }

    /** Longest stretch an expression may be moved over; keeps inlining linear in the method size. */
    private static final int MAX_INLINE_DISTANCE = 256;
    /** How many recent failed loads are retried when a later inline removes an expression in between. */
    private static final int MAX_RETRIED_LOADS = 32;

    /**
     * Moves each single-use expression ({@link InlineEvalStore}) to its only load, keeping the evaluation order of
     * order-sensitive operations. Works in one forward pass; a load that could not take its expression because
     * another pending expression sat in between is retried once that one has been moved away (the typical case is
     * call arguments: {@code a = f(); b = g(); h(a, b)} becomes {@code h(f(), g())}).
     *
     * <p>Sequence entries are a {@link StackInstruction} (including a pending {@link InlineEvalStore}), a spliced
     * {@code List<StackInstruction>}, or {@code null} for an expression that was moved away.
     */
    private static final class Inliner {
        private final ArrayList<@Nullable Object> top = new ArrayList<>();
        private final Int2IntOpenHashMap pending = new Int2IntOpenHashMap(); // register -> index in top
        private final IntArrayList topFailed = new IntArrayList(); // indices in top of loads that failed
        private @Nullable ArrayList<@Nullable Object> body; // body of the InlineEvalStore being built
        private final IntArrayList bodyFailed = new IntArrayList();

        Inliner() {
            pending.defaultReturnValue(-1);
        }

        ArrayList<StackInstruction> run(List<StackInstruction> instructions) {
            for(var instr : instructions) {
                if(instr instanceof InlineEvalStore store) {
                    body = new ArrayList<>(store.instructions.size());
                    bodyFailed.clear();
                    for(var t : store.instructions) {
                        if(t instanceof StackInstruction.Load) appendLoad(body, bodyFailed, true, t);
                        else body.add(t);
                    }
                    var closed = new InlineEvalStore(store.reg, flatten(body));
                    body = null;
                    pending.put(store.reg.reg(), top.size());
                    top.add(closed);
                }else if(instr instanceof StackInstruction.Load) {
                    appendLoad(top, topFailed, false, instr);
                }else {
                    top.add(instr);
                }
            }
            return flatten(top);
        }

        private void appendLoad(ArrayList<@Nullable Object> seq, IntArrayList failed, boolean isBody, StackInstruction load) {
            int position = seq.size();
            if(tryInline(seq, position, isBody, load)) return;
            seq.add(load);
            if(pendingIndex(load) >= 0) {
                failed.add(position);
                if(failed.size() > MAX_RETRIED_LOADS) failed.removeInt(0);
            }
        }

        private int pendingIndex(Object load) {
            if(load instanceof StackInstruction.Load(BytecodeValue value, ClassDesc _) && value instanceof BytecodeRegister(int reg)) {
                return pending.get(reg);
            }
            return -1;
        }

        /** Inlines the pending expression of {@code load} at {@code seq[position]} (appending if position == size). */
        private boolean tryInline(ArrayList<@Nullable Object> seq, int position, boolean isBody, Object load) {
            int w = pendingIndex(load);
            if(w < 0 || (!isBody && w >= position)) return false; // never move an expression before its own position
            var store = (InlineEvalStore) Objects.requireNonNull(top.get(w));
            int topEnd = isBody ? top.size() : position;
            int distance = topEnd - w - 1 + (isBody ? position : 0);
            if(distance > MAX_INLINE_DISTANCE) return false;
            var uses = new IntOpenHashSet();
            boolean storeImpure = false;
            for(var i : store.instructions) {
                if(i instanceof StackInstruction.Load l && l.value() instanceof BytecodeRegister(int reg)) {
                    uses.add(reg);
                }else if(i instanceof StackInstruction.Call(InstructionTarget target) && target instanceof InstructionTarget.FunctionTarget functionTarget && functionTarget.noReordering()) {
                    return false;
                }
                storeImpure |= isOrderSensitive(i);
            }
            for(int k = w + 1; k < topEnd; k++) {
                if(!canMovePast(top.get(k), uses, storeImpure)) return false;
            }
            if(isBody) {
                for(int k = 0; k < position; k++) {
                    if(!canMovePast(seq.get(k), uses, storeImpure)) return false;
                }
            }
            top.set(w, null);
            pending.remove(store.reg.reg());
            var spliced = store.instructions;
            if(position == seq.size()) seq.add(spliced);
            else seq.set(position, spliced);
            retryFailed(w);
            return true;
        }

        /** An expression at top[w] moved away: loads after it that failed may succeed now. */
        private void retryFailed(int w) {
            boolean progress = true;
            while(progress) {
                progress = false;
                for(int k = 0; k < topFailed.size(); k++) {
                    int position = topFailed.getInt(k);
                    if(position <= w) continue;
                    var load = top.get(position);
                    if(!(load instanceof StackInstruction) || pendingIndex(load) < 0) {
                        topFailed.removeInt(k--);
                        continue;
                    }
                    if(tryInline(top, position, false, load)) {
                        topFailed.rem(position);
                        progress = true;
                        break;
                    }
                }
                if(progress || body == null) continue;
                for(int k = 0; k < bodyFailed.size(); k++) {
                    int position = bodyFailed.getInt(k);
                    var load = body.get(position);
                    if(!(load instanceof StackInstruction) || pendingIndex(load) < 0) {
                        bodyFailed.removeInt(k--);
                        continue;
                    }
                    if(tryInline(body, position, true, load)) {
                        bodyFailed.rem(position);
                        progress = true;
                        break;
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static ArrayList<StackInstruction> flatten(List<@Nullable Object> seq) {
            var out = new ArrayList<StackInstruction>(seq.size());
            for(var entry : seq) {
                if(entry instanceof StackInstruction instr) out.add(instr);
                else if(entry instanceof List<?> list) out.addAll((List<StackInstruction>) list);
            }
            return out;
        }

        @SuppressWarnings("unchecked")
        private static boolean canMovePast(@Nullable Object entry, IntSet uses, boolean storeImpure) {
            if(entry == null) return true;
            if(entry instanceof List<?> list) {
                for(var inner : (List<StackInstruction>) list) {
                    if(!canMovePast(inner, uses, storeImpure)) return false;
                }
                return true;
            }
            var i = (StackInstruction) entry;
            if(i instanceof InlineEvalStore other) {
                // Not moved (yet): it executes here, then stores its register.
                if(uses.contains(other.reg.reg())) return false;
                for(var inner : other.instructions) {
                    if(!canMovePast(inner, uses, storeImpure)) return false;
                }
                return true;
            }
            if(i instanceof StackInstruction.Store(BytecodeRegister register) && uses.contains(register.reg())) return false;
            if(i instanceof StackInstruction.Load l && l.value() instanceof BytecodeRegister(int reg) && uses.contains(reg)) return false;
            if(i instanceof StackInstruction.Label || i instanceof StackInstruction.Jump || i instanceof StackInstruction.JumpTyped || i instanceof StackInstruction.Return || i instanceof StackInstruction.ReturnValue) return false;
            return !(storeImpure && isOrderSensitive(i));
        }
    }

    /**
     * Whether the instruction reads or writes mutable state or may throw, so it must keep its order relative to
     * other such instructions. Casts are treated as order-free: they only ever narrow values the IL already knows
     * the type of. Reads of data-class constants (static final, written once before any function runs) are free.
     */
    private static boolean isOrderSensitive(StackInstruction i) {
        return switch(i) {
            case StackInstruction.Call(InstructionTarget target) -> switch(target) {
                case InstructionTarget.FunctionTarget _ -> true;
                case InstructionTarget.FieldTarget f -> f.isWrite() || !isDataClassConstant(f);
            };
            case StackInstruction.New _, StackInstruction.ArrayStore _, StackInstruction.EvaluateString _ -> true;
            case StackInstruction.Evaluate(StackEvaluationType type) -> type == StackEvaluationType.DIV || type == StackEvaluationType.MOD;
            default -> false;
        };
    }

    private static boolean isDataClassConstant(InstructionTarget.FieldTarget f) {
        return f.isStatic() && dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage.isDataClass(f.classType());
    }

    private static class DependencyGenerator {
        private final List<StackInstruction> instructions;
        private final IntSet invalidInstructions;
        public int index;

        public DependencyGenerator(List<StackInstruction> instructions, IntSet invalidInstructions) {
            this.instructions = instructions;
            this.invalidInstructions = invalidInstructions;
        }

        public boolean findDependencies(IntArrayList dependencies, int stackDepth) {
            while(stackDepth != 0) {
                if(invalidInstructions.contains(index)) return false;
                var instr = instructions.get(index);
                if(instr instanceof StackInstruction.Load) {
                    stackDepth++;
                }else if(instr instanceof StackInstruction.Call(InstructionTarget target)) {
                    if(target instanceof InstructionTarget.FunctionTarget f) {
                        var fdesc = f.descriptor();
                        int consumed = fdesc.parameterCount() + (f.isStatic() ? 0 : 1);
                        int returned = fdesc.returnType() == ConstantDescs.CD_void ? 0 : 1;
                        stackDepth += returned - consumed;
                    }else if(target instanceof InstructionTarget.FieldTarget f) {
                        if(!f.isStatic()) stackDepth--;
                        stackDepth += f.isWrite() ? -1 : 1;
                    }else {
                        return false;
                    }
                }else if(instr instanceof StackInstruction.New) {
                    stackDepth++;
                }else if(instr instanceof StackInstruction.Pop) {
                    stackDepth--;
                }else if(instr instanceof StackInstruction.Evaluate(StackEvaluationType evaluationType)) {
                    if(evaluationType == StackEvaluationType.ADD || evaluationType == StackEvaluationType.SUB || evaluationType == StackEvaluationType.MUL || evaluationType == StackEvaluationType.DIV || evaluationType == StackEvaluationType.MOD || evaluationType == StackEvaluationType.SHL || evaluationType == StackEvaluationType.SHR) {
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
