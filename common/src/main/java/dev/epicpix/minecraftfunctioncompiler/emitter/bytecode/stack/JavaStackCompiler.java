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
import org.objectweb.asm.*;

import java.util.*;

public class JavaStackCompiler {

    private final MethodVisitor mv;
    private final List<StackInstruction> instructions;
    private final IntArrayList toTraverse;
    private final IntSet traversed;
    private final Int2ObjectOpenHashMap<Stack<Type>> instructionTypeStackMap;
    private final HashMap<BytecodeLabel, Stack<Type>> labelTypeStackMap;
    private final HashMap<BytecodeLabel, Label> labelMap;

    private JavaStackCompiler(MethodVisitor mv, List<StackInstruction> instructions) {
        this.mv = mv;
        this.instructions = instructions;
        this.toTraverse = new IntArrayList();
        this.traversed = new IntOpenHashSet();
        this.instructionTypeStackMap = new Int2ObjectOpenHashMap<>();
        this.labelTypeStackMap = new HashMap<>();
        this.labelMap = new HashMap<>();
    }

    private static int getFunctionTargetOpcode(InstructionTarget.FunctionTarget target) {
        if(target.isStatic()) return Opcodes.INVOKESTATIC;
        if(target.isInterface()) return Opcodes.INVOKEINTERFACE;
        if(target.isSpecial()) return Opcodes.INVOKESPECIAL;
        return Opcodes.INVOKEVIRTUAL;
    }

    private static int getFieldTargetOpcode(InstructionTarget.FieldTarget target) {
        if(target.isStatic()) {
            if(target.isWrite()) return Opcodes.PUTSTATIC;
            return Opcodes.GETSTATIC;
        }
        if(target.isWrite()) return Opcodes.PUTFIELD;
        return Opcodes.GETFIELD;
    }

    private static boolean isIntegerLike(Type t) {
        return t == Type.BOOLEAN_TYPE || t == Type.BYTE_TYPE || t == Type.CHAR_TYPE || t == Type.SHORT_TYPE || t == Type.INT_TYPE;
    }

    private boolean traverseInstruction(int index, Stack<Type> currentTypeStack) {
        var instr = instructions.get(index);
        switch(instr) {
            case StackInstruction.Load(BytecodeValue value, Type type) -> {
                switch(value) {
                    case BytecodeValue.NullValue _ -> currentTypeStack.push(Type.getType(Object.class));
                    case BytecodeValue.IntegerValue _ -> currentTypeStack.push(Type.INT_TYPE);
                    case BytecodeValue.LongValue _ -> currentTypeStack.push(Type.LONG_TYPE);
                    case BytecodeValue.FloatValue _ -> currentTypeStack.push(Type.FLOAT_TYPE);
                    case BytecodeValue.DoubleValue _ -> currentTypeStack.push(Type.DOUBLE_TYPE);
                    case BytecodeValue.StringValue _ -> currentTypeStack.push(Type.getType(String.class));
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
                    var argCount = Type.getArgumentTypes(f.descriptor()).length + (f.isStatic() ? 0 : 1);
                    for(int i = 0; i < argCount; i++) {
                        currentTypeStack.pop();
                    }
                    var returnType = Type.getReturnType(f.descriptor());
                    if(returnType != Type.VOID_TYPE) currentTypeStack.push(returnType);
                    return true;
                }else if(target instanceof InstructionTarget.FieldTarget f) {
                    if(!f.isStatic()) currentTypeStack.pop();
                    if(f.isWrite()) currentTypeStack.pop();
                    else currentTypeStack.push(Type.getType(f.descriptor()));
                    return true;
                }else {
                    throw new IllegalArgumentException("Unknown target: " + target);
                }
            }
            case StackInstruction.New(Type type) -> {
                currentTypeStack.push(type);
                return true;
            }
            case StackInstruction.NewArray(Type type) -> {
                currentTypeStack.pop();
                var arrayType = Type.getType("[" + type.getDescriptor());
                currentTypeStack.push(arrayType);
                return true;
            }
            case StackInstruction.ArrayStore _ -> {
                currentTypeStack.pop();
                currentTypeStack.pop();
                var array = currentTypeStack.pop();
                if(array.getSort() != Type.ARRAY) throw new IllegalArgumentException("Cannot store in " + array);
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

                currentTypeStack.push(Type.getType(String.class));
                return true;
            }
            case StackInstruction.Cast(Type type) -> {
                var op = currentTypeStack.pop();
                if((op.getSort() != Type.OBJECT && op.getSort() != Type.ARRAY && type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) && ((isIntegerLike(op) && isIntegerLike(type)) || op.getSort() == type.getSort())) {
                    currentTypeStack.push(op);
                    return true;
                }
                if(isIntegerLike(op)) {
                    if(type == Type.LONG_TYPE) {
                        currentTypeStack.push(Type.LONG_TYPE);
                        return true;
                    }else if(type == Type.FLOAT_TYPE) {
                        currentTypeStack.push(Type.FLOAT_TYPE);
                        return true;
                    }else if(type == Type.DOUBLE_TYPE) {
                        currentTypeStack.push(Type.DOUBLE_TYPE);
                        return true;
                    }
                }else if(op == Type.LONG_TYPE) {
                    if(type == Type.INT_TYPE) {
                        currentTypeStack.push(Type.INT_TYPE);
                        return true;
                    }else if(type == Type.FLOAT_TYPE) {
                        currentTypeStack.push(Type.FLOAT_TYPE);
                        return true;
                    }else if(type == Type.DOUBLE_TYPE) {
                        currentTypeStack.push(Type.DOUBLE_TYPE);
                        return true;
                    }
                }else if(op == Type.FLOAT_TYPE) {
                    if(type == Type.INT_TYPE) {
                        currentTypeStack.push(Type.INT_TYPE);
                        return true;
                    }else if(type == Type.LONG_TYPE) {
                        currentTypeStack.push(Type.LONG_TYPE);
                        return true;
                    }else if(type == Type.DOUBLE_TYPE) {
                        currentTypeStack.push(Type.DOUBLE_TYPE);
                        return true;
                    }
                }else if(op == Type.DOUBLE_TYPE) {
                    if(type == Type.INT_TYPE) {
                        currentTypeStack.push(Type.INT_TYPE);
                        return true;
                    }else if(type == Type.LONG_TYPE) {
                        currentTypeStack.push(Type.LONG_TYPE);
                        return true;
                    }else if(type == Type.FLOAT_TYPE) {
                        currentTypeStack.push(Type.FLOAT_TYPE);
                        return true;
                    }
                }
                if(op.getSort() != Type.OBJECT && op.getSort() != Type.ARRAY)
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
                        if(a != Type.LONG_TYPE && a != Type.FLOAT_TYPE && a != Type.DOUBLE_TYPE && !isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        return true;
                    }
                }
                var b = currentTypeStack.pop();
                switch(jumpType) {
                    case IF_EQUAL, IF_NOT_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) return true;
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) return true;
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) return true;
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) return true;
                        if((a.getSort() == Type.OBJECT || a.getSort() == Type.ARRAY) && (b.getSort() == Type.OBJECT || b.getSort() == Type.ARRAY))
                            return true;
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_LESS_THAN, IF_LESS_EQUAL, IF_GREATER_THAN, IF_GREATER_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) return true;
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) return true;
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) return true;
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) return true;
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
                var stackClone = new Stack<Type>();
                stackClone.addAll(currentTypeStack);
                labelTypeStackMap.put(label, stackClone);
                return true;
            }
            case StackCompiler.InlineEvalStore _ -> throw new IllegalArgumentException("Unknown instruction: " + instr);
        }
    }

    private static Type get(Stack<Type> currentTypeStack, int index) {
        if(index < 0) return currentTypeStack.get(currentTypeStack.size() + index);
        else return currentTypeStack.get(index);
    }

    private void writeInstruction(int index, Stack<Type> currentTypeStack) {
        var instr = instructions.get(index);
        switch(instr) {
            case StackInstruction.Load(BytecodeValue bValue, Type loadType) -> {
                switch(bValue) {
                    case BytecodeValue.NullValue _ -> mv.visitInsn(Opcodes.ACONST_NULL);
                    case BytecodeValue.IntegerValue(int val) -> {
                        if(val >= -1 && val <= 5) {
                            mv.visitInsn(Opcodes.ICONST_0 + val);
                        }else if(val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
                            mv.visitIntInsn(Opcodes.BIPUSH, val);
                        }else if(val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
                            mv.visitIntInsn(Opcodes.SIPUSH, val);
                        }else {
                            mv.visitLdcInsn(val);
                        }
                    }
                    case BytecodeValue.LongValue(long value) -> {
                        if(value == 0L || value == 1L) {
                            mv.visitInsn(Opcodes.LCONST_0 + (int) value);
                        }else {
                            mv.visitLdcInsn(value);
                        }
                    }
                    case BytecodeValue.FloatValue(float value) -> {
                        if(value == 0F || value == 1F || value == 2F) {
                            mv.visitInsn(Opcodes.FCONST_0 + (int) value);
                        }else {
                            mv.visitLdcInsn(value);
                        }
                    }
                    case BytecodeValue.DoubleValue(double value) -> {
                        if(value == 0D || value == 1D) {
                            mv.visitInsn(Opcodes.DCONST_0 + (int) value);
                        }else {
                            mv.visitLdcInsn(value);
                        }
                    }
                    case BytecodeValue.StringValue(String value) -> mv.visitLdcInsn(value);
                    case BytecodeValue.Parameter va -> mv.visitVarInsn(loadType.getOpcode(Opcodes.ILOAD), va.index());
                    case BytecodeRegister(int reg) -> mv.visitVarInsn(loadType.getOpcode(Opcodes.ILOAD), reg);
                    case null, default -> throw new IllegalArgumentException("Unknown value: " + bValue);
                }
            }
            case StackInstruction.Store(BytecodeRegister register) -> {
                var type = get(currentTypeStack, -1);
                mv.visitVarInsn(type.getOpcode(Opcodes.ISTORE), register.reg());
            }
            case StackInstruction.Call(InstructionTarget target) -> {
                switch(target) {
                    case InstructionTarget.FunctionTarget f -> mv.visitMethodInsn(getFunctionTargetOpcode(f), f.className(), f.name(), f.descriptor(), f.isInterface());
                    case InstructionTarget.FieldTarget f -> mv.visitFieldInsn(getFieldTargetOpcode(f), f.className(), f.name(), f.descriptor());
                }
            }
            case StackInstruction.New(Type type) -> mv.visitTypeInsn(Opcodes.NEW, type.getInternalName());
            case StackInstruction.NewArray(Type type) -> {
                if(type == Type.BOOLEAN_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
                }else if(type == Type.BYTE_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
                }else if(type == Type.CHAR_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR);
                }else if(type == Type.INT_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
                }else if(type == Type.LONG_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG);
                }else if(type == Type.DOUBLE_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
                }else if(type == Type.FLOAT_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
                }else if(type == Type.SHORT_TYPE) {
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_SHORT);
                }else {
                    mv.visitTypeInsn(Opcodes.ANEWARRAY, type.getInternalName());
                }
            }
            case StackInstruction.ArrayStore _ -> {
                var array = get(currentTypeStack, -3);
                if(array.getSort() != Type.ARRAY) {
                    throw new IllegalArgumentException("Cannot store in " + array);
                }
                mv.visitInsn(array.getElementType().getOpcode(Opcodes.IASTORE));
            }
            case StackInstruction.ReturnValue _ -> {
                var type = get(currentTypeStack, -1);
                mv.visitInsn(type.getOpcode(Opcodes.IRETURN));
            }
            case StackInstruction.Return _ -> mv.visitInsn(Opcodes.RETURN);
            case StackInstruction.Pop _ -> {
                var popped = get(currentTypeStack, -1);
                if(popped == Type.LONG_TYPE || popped == Type.DOUBLE_TYPE) {
                    mv.visitInsn(Opcodes.POP2);
                }else {
                    mv.visitInsn(Opcodes.POP);
                }
            }
            case StackInstruction.Evaluate(StackEvaluationType evaluationType) -> {
                if(evaluationType == StackEvaluationType.ADD) {
                    var a = get(currentTypeStack, -1);
                    mv.visitInsn(a.getOpcode(Opcodes.IADD));
                }else if(evaluationType == StackEvaluationType.SUB) {
                    var a = get(currentTypeStack, -1);
                    mv.visitInsn(a.getOpcode(Opcodes.ISUB));
                }else if(evaluationType == StackEvaluationType.MUL) {
                    var a = get(currentTypeStack, -1);
                    mv.visitInsn(a.getOpcode(Opcodes.IMUL));
                }else if(evaluationType == StackEvaluationType.DIV) {
                    var a = get(currentTypeStack, -1);
                    mv.visitInsn(a.getOpcode(Opcodes.IDIV));
                }else if(evaluationType == StackEvaluationType.MOD) {
                    var a = get(currentTypeStack, -1);
                    mv.visitInsn(a.getOpcode(Opcodes.IREM));
                }else if(evaluationType == StackEvaluationType.SHL) {
                    var a = get(currentTypeStack, -2);
                    mv.visitInsn(a.getOpcode(Opcodes.ISHL));
                }else if(evaluationType == StackEvaluationType.SHR) {
                    var a = get(currentTypeStack, -2);
                    mv.visitInsn(a.getOpcode(Opcodes.ISHR));
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
                Object[] res = new Object[1 + additionalArguments.size()];
                res[0] = result.toString();
                for(int i = 0; i < additionalArguments.size(); i++) {
                    res[i + 1] = additionalArguments.get(i);
                }
                var handle = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
                mv.visitInvokeDynamicInsn("makeConcatWithConstants", "(" + "Ljava/lang/String;".repeat(dynamicCount) + ")Ljava/lang/String;", handle, res);
            }
            case StackInstruction.Cast(Type type) -> {
                var op = get(currentTypeStack, -1);
                if((op.getSort() != Type.OBJECT && op.getSort() != Type.ARRAY && type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) && ((isIntegerLike(op) && isIntegerLike(type)) || op.getSort() == type.getSort())) {
                    return;
                }
                if(isIntegerLike(op)) {
                    if(type == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.I2L);
                        return;
                    }else if(type == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.I2F);
                        return;
                    }else if(type == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.I2D);
                        return;
                    }
                }else if(op == Type.LONG_TYPE) {
                    if(type == Type.INT_TYPE) {
                        mv.visitInsn(Opcodes.L2I);
                        return;
                    }else if(type == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.L2F);
                        return;
                    }else if(type == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.L2D);
                        return;
                    }
                }else if(op == Type.FLOAT_TYPE) {
                    if(type == Type.INT_TYPE) {
                        mv.visitInsn(Opcodes.F2I);
                        return;
                    }else if(type == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.F2L);
                        return;
                    }else if(type == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.F2D);
                        return;
                    }
                }else if(op == Type.DOUBLE_TYPE) {
                    if(type == Type.INT_TYPE) {
                        mv.visitInsn(Opcodes.D2I);
                        return;
                    }else if(type == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.D2L);
                        return;
                    }else if(type == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.D2F);
                        return;
                    }
                }
                if(op.getSort() != Type.OBJECT && op.getSort() != Type.ARRAY) {
                    throw new IllegalArgumentException("Cannot cast " + op + " to " + type);
                }
                mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
            }
            case StackInstruction.Jump(StackJumpType jumpType, BytecodeLabel label) -> {
                if(jumpType == StackJumpType.GOTO) {
                    mv.visitJumpInsn(Opcodes.GOTO, labelMap.get(label));
                    return;
                }
                if(jumpType == StackJumpType.IF_NULL || jumpType == StackJumpType.IF_NOT_NULL) {
                    mv.visitJumpInsn(jumpType == StackJumpType.IF_NULL ? Opcodes.IFNULL : Opcodes.IFNONNULL, labelMap.get(label));
                    return;
                }
                var a = get(currentTypeStack, -1);
                switch(jumpType) {
                    case IF_EQUAL_ZERO -> {
                        if(a == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCONST_0);
                            mv.visitInsn(Opcodes.LCMP);
                        }else if(a == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FCMPL);
                        }else if(a == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DCMPL);
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(label));
                        return;
                    }
                    case IF_NOT_EQUAL_ZERO -> {
                        if(a == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCONST_0);
                            mv.visitInsn(Opcodes.LCMP);
                        }else if(a == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FCMPL);
                        }else if(a == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DCMPL);
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(label));
                        return;
                    }
                    case IF_LESS_THAN_ZERO -> {
                        if(a == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCONST_0);
                            mv.visitInsn(Opcodes.LCMP);
                        }else if(a == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FCMPL);
                        }else if(a == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DCMPL);
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(label));
                        return;
                    }
                    case IF_LESS_EQUAL_ZERO -> {
                        if(a == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCONST_0);
                            mv.visitInsn(Opcodes.LCMP);
                        }else if(a == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FCMPL);
                        }else if(a == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DCMPL);
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(label));
                        return;
                    }
                    case IF_GREATER_THAN_ZERO -> {
                        if(a == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCONST_0);
                            mv.visitInsn(Opcodes.LCMP);
                        }else if(a == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FCMPG);
                        }else if(a == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DCMPG);
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(label));
                        return;
                    }
                    case IF_GREATER_EQUAL_ZERO -> {
                        if(a == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCONST_0);
                            mv.visitInsn(Opcodes.LCMP);
                        }else if(a == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FCMPG);
                        }else if(a == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DCMPG);
                        }else if(!isIntegerLike(a)) {
                            throw new IllegalArgumentException("Cannot compare " + a + " and 0");
                        }
                        mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(label));
                        return;
                    }
                }
                var b = get(currentTypeStack, -2);
                switch(jumpType) {
                    case IF_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, labelMap.get(label));
                            return;
                        }
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCMP);
                            mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(label));
                            return;
                        }
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCMPL);
                            mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(label));
                            return;
                        }
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCMPL);
                            mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(label));
                            return;
                        }
                        if((a.getSort() == Type.OBJECT || a.getSort() == Type.ARRAY) && (b.getSort() == Type.OBJECT || b.getSort() == Type.ARRAY)) {
                            mv.visitJumpInsn(Opcodes.IF_ACMPEQ, labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_NOT_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            mv.visitJumpInsn(Opcodes.IF_ICMPNE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCMP);
                            mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCMPL);
                            mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCMPL);
                            mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(label));
                            return;
                        }
                        if((a.getSort() == Type.OBJECT || a.getSort() == Type.ARRAY) && (b.getSort() == Type.OBJECT || b.getSort() == Type.ARRAY)) {
                            mv.visitJumpInsn(Opcodes.IF_ACMPNE, labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_LESS_THAN -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            mv.visitJumpInsn(Opcodes.IF_ICMPLT, labelMap.get(label));
                            return;
                        }
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCMP);
                            mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(label));
                            return;
                        }
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCMPL);
                            mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(label));
                            return;
                        }
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCMPL);
                            mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_LESS_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            mv.visitJumpInsn(Opcodes.IF_ICMPLE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCMP);
                            mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCMPL);
                            mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCMPL);
                            mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_GREATER_THAN -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            mv.visitJumpInsn(Opcodes.IF_ICMPGT, labelMap.get(label));
                            return;
                        }
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCMP);
                            mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(label));
                            return;
                        }
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCMPG);
                            mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(label));
                            return;
                        }
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCMPG);
                            mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    case IF_GREATER_EQUAL -> {
                        if(isIntegerLike(a) && isIntegerLike(b)) {
                            mv.visitJumpInsn(Opcodes.IF_ICMPGE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                            mv.visitInsn(Opcodes.LCMP);
                            mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                            mv.visitInsn(Opcodes.FCMPG);
                            mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(label));
                            return;
                        }
                        if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                            mv.visitInsn(Opcodes.DCMPG);
                            mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(label));
                            return;
                        }
                        throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                    }
                    default -> throw new IllegalArgumentException("Unknown jump type: " + jumpType);
                }
            }
            case StackInstruction.JumpTyped(StackJumpType jumpType, Type type, BytecodeLabel label) -> {
                switch(jumpType) {
                    case IF_INSTANCEOF -> {
                        mv.visitTypeInsn(Opcodes.INSTANCEOF, type.getInternalName());
                        mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(label));
                    }
                    case IF_NOT_INSTANCEOF -> {
                        mv.visitTypeInsn(Opcodes.INSTANCEOF, type.getInternalName());
                        mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(label));
                    }
                    default -> throw new IllegalArgumentException("Unknown jump type: " + jumpType);
                }
            }
            case StackInstruction.Label(BytecodeLabel label) -> mv.visitLabel(labelMap.get(label));
            case StackCompiler.InlineEvalStore _ -> throw new IllegalArgumentException("Unknown instruction: " + instr);
        }
    }

    public static void compile(MethodVisitor mv, List<StackInstruction> instructions) {
        mv.visitCode();
        JavaStackCompiler state = new JavaStackCompiler(mv, instructions);
        for(var instr : instructions) {
            if(instr instanceof StackInstruction.Label(BytecodeLabel label)) {
                state.labelMap.put(label, new Label());
            }
        }
        state.toTraverse.push(0);
        var currentStack = new Stack<Type>();
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
                var copiedStack = new Stack<Type>();
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
        mv.visitMaxs(0, 0);
    }

}
