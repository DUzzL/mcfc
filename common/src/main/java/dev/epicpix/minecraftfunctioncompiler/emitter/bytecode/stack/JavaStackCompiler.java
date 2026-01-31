package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class JavaStackCompiler {

    private final CodeBuilder cb;
    private final List<StackInstruction> instructions;
    private final IntArrayList toTraverse;
    private final IntSet traversed;
    private final Int2ObjectOpenHashMap<Stack<ClassDesc>> instructionTypeStackMap;
    private final HashMap<BytecodeLabel, Stack<ClassDesc>> labelTypeStackMap;
    private final HashMap<BytecodeLabel, Label> labelMap;

    private JavaStackCompiler(CodeBuilder cb, List<StackInstruction> instructions) {
        this.cb = cb;
        this.instructions = instructions;
        this.toTraverse = new IntArrayList();
        this.traversed = new IntOpenHashSet();
        this.instructionTypeStackMap = new Int2ObjectOpenHashMap<>();
        this.labelTypeStackMap = new HashMap<>();
        this.labelMap = new HashMap<>();
    }

    private static boolean isIntegerLike(ClassDesc t) {
        return t == ConstantDescs.CD_boolean || t == ConstantDescs.CD_byte || t == ConstantDescs.CD_char || t == ConstantDescs.CD_short || t == ConstantDescs.CD_int;
    }

    private boolean traverseInstruction(int index, Stack<ClassDesc> currentTypeStack) {
        var instr = instructions.get(index);
        switch(instr) {
            case StackInstruction.Load(BytecodeValue value, ClassDesc type) -> {
                switch(value) {
                    case BytecodeValue.NullValue _ -> currentTypeStack.push(ConstantDescs.CD_Object);
                    case BytecodeValue.IntegerValue _ -> currentTypeStack.push(ConstantDescs.CD_int);
                    case BytecodeValue.LongValue _ -> currentTypeStack.push(ConstantDescs.CD_long);
                    case BytecodeValue.FloatValue _ -> currentTypeStack.push(ConstantDescs.CD_float);
                    case BytecodeValue.DoubleValue _ -> currentTypeStack.push(ConstantDescs.CD_double);
                    case BytecodeValue.StringValue _ -> currentTypeStack.push(ConstantDescs.CD_String);
                    case BytecodeValue.Parameter _, BytecodeRegister _ -> currentTypeStack.push(type);
                    case null, default -> throw new IllegalArgumentException("Unknown value: " + value);
                }
                return true;
            }
            case StackInstruction.Store _ -> {
                currentTypeStack.pop();
                return true;
            }
            case StackInstruction.Call(InstructionTarget target) -> {
                if(target instanceof InstructionTarget.FunctionTarget f) {
                    var argCount = f.descriptor().parameterCount() + (f.isStatic() ? 0 : 1);
                    for(int i = 0; i < argCount; i++) {
                        currentTypeStack.pop();
                    }
                    var returnType = f.descriptor().returnType();
                    if(returnType != ConstantDescs.CD_void) currentTypeStack.push(returnType);
                    return true;
                }else if(target instanceof InstructionTarget.FieldTarget f) {
                    if(!f.isStatic()) currentTypeStack.pop();
                    if(f.isWrite()) currentTypeStack.pop();
                    else currentTypeStack.push(f.descriptor());
                    return true;
                }else {
                    throw new IllegalArgumentException("Unknown target: " + target);
                }
            }
            case StackInstruction.New(ClassDesc type) -> {
                currentTypeStack.push(type);
                return true;
            }
            case StackInstruction.NewArray(ClassDesc type) -> {
                currentTypeStack.pop();
                currentTypeStack.push(type.arrayType());
                return true;
            }
            case StackInstruction.ArrayStore _ -> {
                currentTypeStack.pop();
                currentTypeStack.pop();
                var array = currentTypeStack.pop();
                if(!array.isArray()) throw new IllegalArgumentException("Cannot store in " + array);
                return true;
            }
            case StackInstruction.ReturnValue _ -> {
                currentTypeStack.pop();
                if(!currentTypeStack.isEmpty())
                    throw new IllegalArgumentException("Return type stack is not empty: " + currentTypeStack);
                return false;
            }
            case StackInstruction.Return _ -> {
                if(!currentTypeStack.isEmpty())
                    throw new IllegalArgumentException("Return type stack is not empty: " + currentTypeStack);
                return false;
            }
            case StackInstruction.Pop _ -> {
                currentTypeStack.pop();
                return true;
            }
            case StackInstruction.Evaluate(StackEvaluationType evaluationType) -> {
                if(evaluationType == StackEvaluationType.ADD) {
                    currentTypeStack.pop();
                    return true;
                }else if(evaluationType == StackEvaluationType.SUB) {
                    currentTypeStack.pop();
                    return true;
                }else if(evaluationType == StackEvaluationType.MUL) {
                    currentTypeStack.pop();
                    return true;
                }else if(evaluationType == StackEvaluationType.DIV) {
                    currentTypeStack.pop();
                    return true;
                }else if(evaluationType == StackEvaluationType.MOD) {
                    currentTypeStack.pop();
                    return true;
                }else if(evaluationType == StackEvaluationType.SHL) {
                    currentTypeStack.pop();
                    return true;
                }else if(evaluationType == StackEvaluationType.SHR) {
                    currentTypeStack.pop();
                    return true;
                }else {
                    throw new IllegalArgumentException("Unknown evaluation type: " + evaluationType);
                }
            }
            case StackInstruction.EvaluateString(@Nullable String[] template) -> {
                for(var s : template) {
                    if(s == null) {
                        currentTypeStack.pop();
                    }
                }

                currentTypeStack.push(ConstantDescs.CD_String);
                return true;
            }
            case StackInstruction.Cast(ClassDesc type) -> {
                var op = currentTypeStack.pop();
                if((!op.isClassOrInterface() && !op.isArray() && !type.isClassOrInterface() && !type.isArray()) && ((isIntegerLike(op) && isIntegerLike(type)) || TypeKind.from(op) == TypeKind.from(type))) {
                    currentTypeStack.push(op);
                    return true;
                }
                if(isIntegerLike(op)) {
                    if(type == ConstantDescs.CD_long) {
                        currentTypeStack.push(ConstantDescs.CD_long);
                        return true;
                    }else if(type == ConstantDescs.CD_float) {
                        currentTypeStack.push(ConstantDescs.CD_float);
                        return true;
                    }else if(type == ConstantDescs.CD_double) {
                        currentTypeStack.push(ConstantDescs.CD_double);
                        return true;
                    }
                }else if(op == ConstantDescs.CD_long) {
                    if(type == ConstantDescs.CD_int) {
                        currentTypeStack.push(ConstantDescs.CD_int);
                        return true;
                    }else if(type == ConstantDescs.CD_float) {
                        currentTypeStack.push(ConstantDescs.CD_float);
                        return true;
                    }else if(type == ConstantDescs.CD_double) {
                        currentTypeStack.push(ConstantDescs.CD_double);
                        return true;
                    }
                }else if(op == ConstantDescs.CD_float) {
                    if(type == ConstantDescs.CD_int) {
                        currentTypeStack.push(ConstantDescs.CD_int);
                        return true;
                    }else if(type == ConstantDescs.CD_long) {
                        currentTypeStack.push(ConstantDescs.CD_long);
                        return true;
                    }else if(type == ConstantDescs.CD_double) {
                        currentTypeStack.push(ConstantDescs.CD_double);
                        return true;
                    }
                }else if(op == ConstantDescs.CD_double) {
                    if(type == ConstantDescs.CD_int) {
                        currentTypeStack.push(ConstantDescs.CD_int);
                        return true;
                    }else if(type == ConstantDescs.CD_long) {
                        currentTypeStack.push(ConstantDescs.CD_long);
                        return true;
                    }else if(type == ConstantDescs.CD_float) {
                        currentTypeStack.push(ConstantDescs.CD_float);
                        return true;
                    }
                }
                if(!op.isClassOrInterface() && !op.isArray())
                    throw new IllegalArgumentException("Cannot cast " + op + " to " + type);
                currentTypeStack.push(type);
                return true;
            }
            case StackInstruction.Jump(StackJumpType jumpType, BytecodeLabel jumpLabel) -> {
                int labelLocation;
                for(labelLocation = 0; labelLocation < instructions.size(); labelLocation++) {
                    if(instructions.get(labelLocation) instanceof StackInstruction.Label(BytecodeLabel label)) {
                        if(label.id() == jumpLabel.id()) {
                            break;
                        }
                    }
                }
                while(labelLocation >= 0) {
                    if(!(instructions.get(labelLocation) instanceof StackInstruction.Label)) {
                        labelLocation++;
                        break;
                    }
                    labelLocation--;
                }
                if(jumpType == StackJumpType.GOTO) {
                    toTraverse.push(labelLocation);
                    return false;
                }
                toTraverse.push(labelLocation);
                if(jumpType == StackJumpType.IF_NULL || jumpType == StackJumpType.IF_NOT_NULL) {
                    currentTypeStack.pop();
                    return true;
                }
                var a = currentTypeStack.pop();
                switch(jumpType) {
                    case IF_EQUAL_ZERO, IF_NOT_EQUAL_ZERO, IF_LESS_THAN_ZERO, IF_LESS_EQUAL_ZERO, IF_GREATER_THAN_ZERO,
                         IF_GREATER_EQUAL_ZERO -> {
                        if(a != ConstantDescs.CD_long && a != ConstantDescs.CD_float && a != ConstantDescs.CD_double && !isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        return true;
                    }
                }
                var b = currentTypeStack.pop();
                switch(jumpType) {
                    case IF_EQUAL, IF_NOT_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) return true;

                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) return true;
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) return true;
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) return true;
                        if((a.isClassOrInterface() || a.isArray()) && (b.isClassOrInterface() || b.isArray())) return true;
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_LESS_THAN, IF_LESS_EQUAL, IF_GREATER_THAN, IF_GREATER_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) return true;
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) return true;
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) return true;
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) return true;
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    default -> throw new IllegalArgumentException("Unknown jump type: " + jumpType);
                }
            }
            case StackInstruction.JumpTyped jump -> {
                int labelLocation;
                for(labelLocation = 0; labelLocation < instructions.size(); labelLocation++) {
                    if(instructions.get(labelLocation) instanceof StackInstruction.Label(BytecodeLabel label)) {
                        if(label.id() == jump.label().id()) {
                            break;
                        }
                    }
                }
                while(labelLocation >= 0) {
                    if(!(instructions.get(labelLocation) instanceof StackInstruction.Label)) {
                        labelLocation++;
                        break;
                    }
                    labelLocation--;
                }
                toTraverse.push(labelLocation);
                currentTypeStack.pop();
                switch(jump.jumpType()) {
                    case IF_INSTANCEOF, IF_NOT_INSTANCEOF -> {
                        return true;
                    }
                    default -> throw new IllegalArgumentException("Unknown jump type: " + jump.jumpType());
                }
            }
            case StackInstruction.Label(BytecodeLabel label) -> {
                var stackClone = new Stack<ClassDesc>();
                stackClone.addAll(currentTypeStack);
                labelTypeStackMap.put(label, stackClone);
                return true;
            }
            case StackCompiler.InlineEvalStore _ -> throw new IllegalArgumentException("Unknown instruction: " + instr);
        }
    }

    private static ClassDesc get(Stack<ClassDesc> currentTypeStack, int index) {
        if(index < 0) return currentTypeStack.get(currentTypeStack.size() + index);
        else return currentTypeStack.get(index);
    }

    private void writeInstruction(int index, Stack<ClassDesc> currentTypeStack) {
        var instr = instructions.get(index);
        switch(instr) {
            case StackInstruction.Load(BytecodeValue bValue, ClassDesc loadType) -> {
                switch(bValue) {
                    case BytecodeValue.NullValue _ -> cb.aconst_null();
                    case BytecodeValue.IntegerValue(int val) -> cb.loadConstant(val);
                    case BytecodeValue.LongValue(long value) -> cb.loadConstant(value);
                    case BytecodeValue.FloatValue(float value) -> cb.loadConstant(value);
                    case BytecodeValue.DoubleValue(double value) -> cb.loadConstant(value);
                    case BytecodeValue.StringValue(String value) -> cb.loadConstant(value);
                    case BytecodeValue.Parameter va -> cb.loadLocal(TypeKind.from(loadType), va.index());
                    case BytecodeRegister(int reg) -> cb.loadLocal(TypeKind.from(loadType), reg);
                    default -> throw new IllegalArgumentException("Unknown value: " + bValue);
                }
            }
            case StackInstruction.Store(BytecodeRegister register) -> {
                var type = get(currentTypeStack, -1);
                cb.storeLocal(TypeKind.from(type), register.reg());
            }
            case StackInstruction.Call(InstructionTarget target) -> {
                switch(target) {
                    case InstructionTarget.FunctionTarget f -> {
                        if(f.isStatic()) cb.invokestatic(f.classType(), f.name(), f.descriptor(), f.isInterface());
                        else if(f.isInterface()) cb.invokeinterface(f.classType(), f.name(), f.descriptor());
                        else if(f.isSpecial()) cb.invokespecial(f.classType(), f.name(), f.descriptor(), false);
                        else cb.invokevirtual(f.classType(), f.name(), f.descriptor());
                    }
                    case InstructionTarget.FieldTarget f -> {
                        if(f.isStatic() && f.isWrite()) cb.putstatic(f.classType(), f.name(), f.descriptor());
                        else if(f.isStatic()) cb.getstatic(f.classType(), f.name(), f.descriptor());
                        else if(f.isWrite()) cb.putfield(f.classType(), f.name(), f.descriptor());
                        else cb.getfield(f.classType(), f.name(), f.descriptor());
                    }
                }
            }
            case StackInstruction.New(ClassDesc type) -> cb.new_(type);
            case StackInstruction.NewArray(ClassDesc type) -> cb.newarray(TypeKind.from(type));
            case StackInstruction.ArrayStore _ -> {
                var array = get(currentTypeStack, -3);
                if(!array.isArray()) {
                    throw new IllegalArgumentException("Cannot store in " + array);
                }
                cb.arrayStore(TypeKind.from(array.componentType()));
            }
            case StackInstruction.ReturnValue _ -> {
                var type = get(currentTypeStack, -1);
                cb.return_(TypeKind.from(type));
            }
            case StackInstruction.Return _ -> cb.return_();
            case StackInstruction.Pop _ -> {
                var popped = get(currentTypeStack, -1);
                if(popped == ConstantDescs.CD_long || popped == ConstantDescs.CD_double) {
                    cb.pop2();
                }else {
                    cb.pop();
                }
            }
            case StackInstruction.Evaluate(StackEvaluationType evaluationType) -> {
                if(evaluationType == StackEvaluationType.ADD) {
                    var a = get(currentTypeStack, -1);
                    if(a == ConstantDescs.CD_int) cb.iadd();
                    else if(a == ConstantDescs.CD_long) cb.ladd();
                    else if(a == ConstantDescs.CD_float) cb.fadd();
                    else if(a == ConstantDescs.CD_double) cb.dadd();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else if(evaluationType == StackEvaluationType.SUB) {
                    var a = get(currentTypeStack, -1);
                    if(a == ConstantDescs.CD_int) cb.isub();
                    else if(a == ConstantDescs.CD_long) cb.lsub();
                    else if(a == ConstantDescs.CD_float) cb.fsub();
                    else if(a == ConstantDescs.CD_double) cb.dsub();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else if(evaluationType == StackEvaluationType.MUL) {
                    var a = get(currentTypeStack, -1);
                    if(a == ConstantDescs.CD_int) cb.imul();
                    else if(a == ConstantDescs.CD_long) cb.lmul();
                    else if(a == ConstantDescs.CD_float) cb.fmul();
                    else if(a == ConstantDescs.CD_double) cb.dmul();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else if(evaluationType == StackEvaluationType.DIV) {
                    var a = get(currentTypeStack, -1);
                    if(a == ConstantDescs.CD_int) cb.idiv();
                    else if(a == ConstantDescs.CD_long) cb.ldiv();
                    else if(a == ConstantDescs.CD_float) cb.fdiv();
                    else if(a == ConstantDescs.CD_double) cb.ddiv();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else if(evaluationType == StackEvaluationType.MOD) {
                    var a = get(currentTypeStack, -1);
                    if(a == ConstantDescs.CD_int) cb.irem();
                    else if(a == ConstantDescs.CD_long) cb.lrem();
                    else if(a == ConstantDescs.CD_float) cb.frem();
                    else if(a == ConstantDescs.CD_double) cb.drem();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else if(evaluationType == StackEvaluationType.SHL) {
                    var a = get(currentTypeStack, -2);
                    if(a == ConstantDescs.CD_int) cb.ishl();
                    else if(a == ConstantDescs.CD_long) cb.lshl();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else if(evaluationType == StackEvaluationType.SHR) {
                    var a = get(currentTypeStack, -2);
                    if(a == ConstantDescs.CD_int) cb.ishr();
                    else if(a == ConstantDescs.CD_long) cb.lshr();
                    else throw new IllegalArgumentException("invalid operation on type: " + a);
                }else {
                    throw new IllegalArgumentException("Unknown evaluation type: " + evaluationType);
                }
            }
            case StackInstruction.EvaluateString(@Nullable String[] template) -> {
                int dynamicCount = 0;
                StringBuilder result = new StringBuilder();
                ArrayList<String> additionalArguments = new ArrayList<>();
                for(var s : template) {
                    if(s == null) {
                        dynamicCount++;
                        result.append("\u0001");
                    }else {
                        if(s.contains("\u0001") || s.contains("\u0002")) {
                            additionalArguments.add(s);
                            result.append("\u0002");
                        }else {
                            result.append(s);
                        }
                    }
                }
                ConstantDesc[] res = new ConstantDesc[1 + additionalArguments.size()];
                res[0] = result.toString();
                for(int i = 0; i < additionalArguments.size(); i++) {
                    res[i + 1] = additionalArguments.get(i);
                }
                ClassDesc[] paramsDescs = new ClassDesc[dynamicCount];
                Arrays.fill(paramsDescs, ConstantDescs.CD_String);
                var handle = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, ClassDesc.of("java/lang/invoke/StringConcatFactory"), "makeConcatWithConstants", MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup, ConstantDescs.CD_String, ConstantDescs.CD_MethodType, ConstantDescs.CD_String, ConstantDescs.CD_Object.arrayType()));
                var invocationType = MethodTypeDesc.of(ConstantDescs.CD_String, paramsDescs);
                cb.invokedynamic(DynamicCallSiteDesc.of(handle, "makeConcatWithConstants", invocationType, res));
            }
            case StackInstruction.Cast(ClassDesc type) -> {
                var op = get(currentTypeStack, -1);
                if((!op.isClassOrInterface() && !op.isArray() && !op.isClassOrInterface() && !op.isArray()) && ((isIntegerLike(op) && isIntegerLike(type)) || TypeKind.from(op) == TypeKind.from(type))) {
                    return;
                }
                if(isIntegerLike(op)) {
                    if(type == ConstantDescs.CD_long) {
                        cb.i2l();
                        return;
                    }else if(type == ConstantDescs.CD_float) {
                        cb.i2f();
                        return;
                    }else if(type == ConstantDescs.CD_double) {
                        cb.i2d();
                        return;
                    }
                }else if(op == ConstantDescs.CD_long) {
                    if(type == ConstantDescs.CD_int) {
                        cb.l2i();
                        return;
                    }else if(type == ConstantDescs.CD_float) {
                        cb.l2f();
                        return;
                    }else if(type == ConstantDescs.CD_double) {
                        cb.l2d();
                        return;
                    }
                }else if(op == ConstantDescs.CD_float) {
                    if(type == ConstantDescs.CD_int) {
                        cb.f2i();
                        return;
                    }else if(type == ConstantDescs.CD_long) {
                        cb.f2l();
                        return;
                    }else if(type == ConstantDescs.CD_double) {
                        cb.f2d();
                        return;
                    }
                }else if(op == ConstantDescs.CD_double) {
                    if(type == ConstantDescs.CD_int) {
                        cb.d2i();
                        return;
                    }else if(type == ConstantDescs.CD_long) {
                        cb.d2l();
                        return;
                    }else if(type == ConstantDescs.CD_float) {
                        cb.d2f();
                        return;
                    }
                }
                if(!op.isClassOrInterface() && !op.isArray()) {
                    throw new IllegalArgumentException("Cannot cast " + op + " to " + type);
                }
                cb.checkcast(type);
            }
            case StackInstruction.Jump(StackJumpType jumpType, BytecodeLabel label) -> {
                if(jumpType == StackJumpType.GOTO) {
                    cb.goto_(labelMap.get(label));
                    return;
                }
                if(jumpType == StackJumpType.IF_NULL) {
                    cb.ifnull(labelMap.get(label));
                    return;
                }
                if(jumpType == StackJumpType.IF_NOT_NULL) {
                    cb.ifnonnull(labelMap.get(label));
                    return;
                }
                var a = get(currentTypeStack, -1);
                switch(jumpType) {
                    case IF_EQUAL_ZERO -> {
                        if(a == ConstantDescs.CD_long) {
                            cb.loadConstant(0L);
                            cb.lcmp();
                        }else if(a == ConstantDescs.CD_float) {
                            cb.loadConstant(0F);
                            cb.fcmpl();
                        }else if(a == ConstantDescs.CD_double) {
                            cb.loadConstant(0D);
                            cb.dcmpl();
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        cb.ifeq(labelMap.get(label));
                        return;
                    }
                    case IF_NOT_EQUAL_ZERO -> {
                        if(a == ConstantDescs.CD_long) {
                            cb.loadConstant(0L);
                            cb.lcmp();
                        }else if(a == ConstantDescs.CD_float) {
                            cb.loadConstant(0F);
                            cb.fcmpl();
                        }else if(a == ConstantDescs.CD_double) {
                            cb.loadConstant(0D);
                            cb.dcmpl();
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        cb.ifne(labelMap.get(label));
                        return;
                    }
                    case IF_LESS_THAN_ZERO -> {
                        if(a == ConstantDescs.CD_long) {
                            cb.loadConstant(0L);
                            cb.lcmp();
                        }else if(a == ConstantDescs.CD_float) {
                            cb.loadConstant(0F);
                            cb.fcmpl();
                        }else if(a == ConstantDescs.CD_double) {
                            cb.loadConstant(0D);
                            cb.dcmpl();
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        cb.iflt(labelMap.get(label));
                        return;
                    }
                    case IF_LESS_EQUAL_ZERO -> {
                        if(a == ConstantDescs.CD_long) {
                            cb.loadConstant(0L);
                            cb.lcmp();
                        }else if(a == ConstantDescs.CD_float) {
                            cb.loadConstant(0F);
                            cb.fcmpl();
                        }else if(a == ConstantDescs.CD_double) {
                            cb.loadConstant(0D);
                            cb.dcmpl();
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        cb.ifle(labelMap.get(label));
                        return;
                    }
                    case IF_GREATER_THAN_ZERO -> {
                        if(a == ConstantDescs.CD_long) {
                            cb.loadConstant(0L);
                            cb.lcmp();
                        }else if(a == ConstantDescs.CD_float) {
                            cb.loadConstant(0F);
                            cb.fcmpg();
                        }else if(a == ConstantDescs.CD_double) {
                            cb.loadConstant(0D);
                            cb.dcmpg();
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        cb.ifgt(labelMap.get(label));
                        return;
                    }
                    case IF_GREATER_EQUAL_ZERO -> {
                        if(a == ConstantDescs.CD_long) {
                            cb.loadConstant(0L);
                            cb.lcmp();
                        }else if(a == ConstantDescs.CD_float) {
                            cb.loadConstant(0F);
                            cb.fcmpg();
                        }else if(a == ConstantDescs.CD_double) {
                            cb.loadConstant(0D);
                            cb.dcmpg();
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        cb.ifge(labelMap.get(label));
                        return;
                    }
                }
                var b = get(currentTypeStack, -2);
                switch(jumpType) {
                    case IF_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            cb.if_icmpeq(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) {
                            cb.lcmp();
                            cb.ifeq(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) {
                            cb.fcmpl();
                            cb.ifeq(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) {
                            cb.dcmpl();
                            cb.ifeq(labelMap.get(label));
                            return;
                        }
                        if((a.isClassOrInterface() || a.isArray()) && (b.isClassOrInterface() || b.isArray())) {
                            cb.if_acmpeq(labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_NOT_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            cb.if_icmpne(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) {
                            cb.lcmp();
                            cb.ifne(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) {
                            cb.fcmpl();
                            cb.ifne(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) {
                            cb.dcmpl();
                            cb.ifne(labelMap.get(label));
                            return;
                        }
                        if((a.isClassOrInterface() || a.isArray()) && (b.isClassOrInterface() || b.isArray())) {
                            cb.if_acmpne(labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_LESS_THAN -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            cb.if_icmplt(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) {
                            cb.lcmp();
                            cb.iflt(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) {
                            cb.fcmpl();
                            cb.iflt(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) {
                            cb.dcmpl();
                            cb.iflt(labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_LESS_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            cb.if_icmple(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) {
                            cb.lcmp();
                            cb.ifle(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) {
                            cb.fcmpl();
                            cb.ifle(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) {
                            cb.dcmpl();
                            cb.ifle(labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_GREATER_THAN -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            cb.if_icmpgt(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) {
                            cb.lcmp();
                            cb.ifgt(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) {
                            cb.fcmpg();
                            cb.ifgt(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) {
                            cb.dcmpg();
                            cb.ifgt(labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_GREATER_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            cb.if_icmpge(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_long && b == ConstantDescs.CD_long) {
                            cb.lcmp();
                            cb.ifge(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_float && b == ConstantDescs.CD_float) {
                            cb.fcmpg();
                            cb.ifge(labelMap.get(label));
                            return;
                        }
                        if(a == ConstantDescs.CD_double && b == ConstantDescs.CD_double) {
                            cb.dcmpg();
                            cb.ifge(labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    default -> throw new IllegalArgumentException("Unknown jump type: " + jumpType);
                }
            }
            case StackInstruction.JumpTyped(StackJumpType jumpType, ClassDesc type, BytecodeLabel label) -> {
                switch(jumpType) {
                    case IF_INSTANCEOF -> {
                        cb.instanceOf(type);
                        cb.ifne(labelMap.get(label));
                    }
                    case IF_NOT_INSTANCEOF -> {
                        cb.instanceOf(type);
                        cb.ifeq(labelMap.get(label));
                    }
                    default -> throw new IllegalArgumentException("Unknown jump type: " + jumpType);
                }
            }
            case StackInstruction.Label(BytecodeLabel label) -> cb.labelBinding(labelMap.get(label));
            case StackCompiler.InlineEvalStore _ -> throw new IllegalArgumentException("Unknown instruction: " + instr);
        }
    }

    public static void compile(CodeBuilder cb, List<StackInstruction> instructions) {
        JavaStackCompiler state = new JavaStackCompiler(cb, instructions);
        for(var instr : instructions) {
            if(instr instanceof StackInstruction.Label(BytecodeLabel label)) {
                state.labelMap.put(label, cb.newLabel());
            }
        }
        state.toTraverse.push(0);
        var currentStack = new Stack<ClassDesc>();
        changeTraversal: while(!state.toTraverse.isEmpty()) {
            var traverse = state.toTraverse.popInt();
            var initialInstr = instructions.get(traverse);
            if(initialInstr instanceof StackInstruction.Label(BytecodeLabel label)) {
                currentStack = new Stack<>();
                var mapped = state.labelTypeStackMap.get(label);
                if(mapped != null) {
                    currentStack.addAll(mapped);
                }
            }
            while(true) {
                if(!state.traversed.add(traverse)) {
                    continue changeTraversal;
                }
                var copiedStack = new Stack<ClassDesc>();
                copiedStack.addAll(currentStack);
                state.instructionTypeStackMap.put(traverse, copiedStack);
                var shouldContinue = state.traverseInstruction(traverse, currentStack);
                if(!shouldContinue) {
                    continue changeTraversal;
                }
                traverse++;
            }
        }
        for(int i = 0; i < instructions.size(); i++) {
            if(state.traversed.contains(i)) {
                state.writeInstruction(i, state.instructionTypeStackMap.get(i));
            }
        }
    }

}
