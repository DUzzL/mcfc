package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeLabel;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.objectweb.asm.*;

import java.util.*;

public class JavaStackCompiler {

    private final MethodVisitor mv;
    private final List<StackInstruction> instructions;
    private final IntArrayList toTraverse;
    private final IntArrayList toTraverseEnd;
    private final IntSet traversed;
    private final HashMap<BytecodeLabel, Stack<Type>> labelTypeStackMap;
    private final HashMap<BytecodeLabel, Label> labelMap;

    private JavaStackCompiler(MethodVisitor mv, List<StackInstruction> instructions) {
        this.mv = mv;
        this.instructions = instructions;
        this.toTraverse = new IntArrayList();
        this.toTraverseEnd = new IntArrayList();
        this.traversed = new IntOpenHashSet();
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
        if(instr instanceof StackInstruction.Load load) {
            if(load.value() instanceof BytecodeValue.NullValue) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                currentTypeStack.push(Type.getType(Object.class));
            }else if(load.value() instanceof BytecodeValue.IntegerValue va) {
                int val = va.value();
                if(val >= -1 && val <= 5) {
                    mv.visitInsn(Opcodes.ICONST_0 + val);
                }else if(val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
                    mv.visitIntInsn(Opcodes.BIPUSH, val);
                }else if(val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
                    mv.visitIntInsn(Opcodes.SIPUSH, val);
                }else {
                    mv.visitLdcInsn(va.value());
                }
                currentTypeStack.push(Type.INT_TYPE);
            }else if(load.value() instanceof BytecodeValue.LongValue va) {
                if(va.value() == 0L || va.value() == 1L) {
                    mv.visitInsn(Opcodes.LCONST_0 + (int)va.value());
                }else {
                    mv.visitLdcInsn(va.value());
                }
                currentTypeStack.push(Type.LONG_TYPE);
            }else if(load.value() instanceof BytecodeValue.FloatValue va) {
                if(va.value() == 0F || va.value() == 1F || va.value() == 2F) {
                    mv.visitInsn(Opcodes.FCONST_0 + (int)va.value());
                }else {
                    mv.visitLdcInsn(va.value());
                }
                currentTypeStack.push(Type.FLOAT_TYPE);
            }else if(load.value() instanceof BytecodeValue.DoubleValue va) {
                if(va.value() == 0D || va.value() == 1D) {
                    mv.visitInsn(Opcodes.DCONST_0 + (int)va.value());
                }else {
                    mv.visitLdcInsn(va.value());
                }
                currentTypeStack.push(Type.DOUBLE_TYPE);
            }else if(load.value() instanceof BytecodeValue.StringValue va) {
                mv.visitLdcInsn(va.value());
                currentTypeStack.push(Type.getType(String.class));
            }else if(load.value() instanceof BytecodeValue.Parameter va) {
                mv.visitVarInsn(load.type().getOpcode(Opcodes.ILOAD), va.index());
                currentTypeStack.push(load.type());
            }else if(load.value() instanceof BytecodeRegister va) {
                mv.visitVarInsn(load.type().getOpcode(Opcodes.ILOAD), va.reg());
                currentTypeStack.push(load.type());
            }else {
                throw new IllegalArgumentException("Unknown value: " + load.value());
            }
            return true;
        }else if(instr instanceof StackInstruction.Store store) {
            var type = currentTypeStack.pop();
            mv.visitVarInsn(type.getOpcode(Opcodes.ISTORE), store.register().reg());
            return true;
        }else if(instr instanceof StackInstruction.Call call) {
            if(call.target() instanceof InstructionTarget.FunctionTarget f) {
                var argCount = Type.getArgumentTypes(f.descriptor()).length + (f.isStatic() ? 0 : 1);
                for(int i = 0; i < argCount; i++) {
                    currentTypeStack.pop();
                }
                mv.visitMethodInsn(getFunctionTargetOpcode(f), f.className(), f.name(), f.descriptor(), f.isInterface());
                var returnType = Type.getReturnType(f.descriptor());
                if(returnType != Type.VOID_TYPE) currentTypeStack.push(returnType);
                return true;
            }else if(call.target() instanceof InstructionTarget.FieldTarget f) {
                if(!f.isStatic()) currentTypeStack.pop();
                if(f.isWrite()) currentTypeStack.pop();
                mv.visitFieldInsn(getFieldTargetOpcode(f), f.className(), f.name(), f.descriptor());
                if(!f.isWrite()) currentTypeStack.push(Type.getType(f.descriptor()));
                return true;
            }else {
                throw new IllegalArgumentException("Unknown target: " + call.target());
            }
        }else if(instr instanceof StackInstruction.New nw) {
            currentTypeStack.push(nw.type());
            mv.visitTypeInsn(Opcodes.NEW, nw.type().getInternalName());
            return true;
        }else if(instr instanceof StackInstruction.NewArray nw) {
            currentTypeStack.pop();
            var arrayType = Type.getType("[" + nw.type().getDescriptor());
            currentTypeStack.push(arrayType);

            if(nw.type() == Type.BOOLEAN_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
            }else if(nw.type() == Type.BYTE_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
            }else if(nw.type() == Type.CHAR_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR);
            }else if(nw.type() == Type.INT_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            }else if(nw.type() == Type.LONG_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG);
            }else if(nw.type() == Type.DOUBLE_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
            }else if(nw.type() == Type.FLOAT_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
            }else if(nw.type() == Type.SHORT_TYPE) {
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_SHORT);
            }else {
                mv.visitTypeInsn(Opcodes.ANEWARRAY, nw.type().getInternalName());
            }

            return true;
        }else if(instr instanceof StackInstruction.ArrayStore) {
            currentTypeStack.pop();
            currentTypeStack.pop();
            var array = currentTypeStack.pop();
            if(array.getSort() != Type.ARRAY) {
                throw new IllegalArgumentException("Cannot store in " + array);
            }
            mv.visitInsn(array.getElementType().getOpcode(Opcodes.IASTORE));
            return true;
        }else if(instr instanceof StackInstruction.ReturnValue) {
            var type = currentTypeStack.pop();
            mv.visitInsn(type.getOpcode(Opcodes.IRETURN));
            if(!currentTypeStack.isEmpty()) {
                throw new IllegalArgumentException("Return type stack is not empty: " + currentTypeStack);
            }
            return false;
        }else if(instr instanceof StackInstruction.Return) {
            mv.visitInsn(Opcodes.RETURN);
            if(!currentTypeStack.isEmpty()) {
                throw new IllegalArgumentException("Return type stack is not empty: " + currentTypeStack);
            }
            return false;
        }else if(instr instanceof StackInstruction.Pop) {
            var popped = currentTypeStack.pop();
            if(popped == Type.LONG_TYPE || popped == Type.DOUBLE_TYPE) {
                mv.visitInsn(Opcodes.POP2);
            }else {
                mv.visitInsn(Opcodes.POP);
            }
            return true;
        }else if(instr instanceof StackInstruction.Evaluate eval) {
            if(eval.evaluationType() == StackEvaluationType.ADD) {
                var a = currentTypeStack.pop();
                currentTypeStack.pop();
                mv.visitInsn(a.getOpcode(Opcodes.IADD));
                currentTypeStack.push(a);
                return true;
            }else if(eval.evaluationType() == StackEvaluationType.SUB) {
                var a = currentTypeStack.pop();
                currentTypeStack.pop();
                mv.visitInsn(a.getOpcode(Opcodes.ISUB));
                currentTypeStack.push(a);
                return true;
            }else if(eval.evaluationType() == StackEvaluationType.MUL) {
                var a = currentTypeStack.pop();
                currentTypeStack.pop();
                mv.visitInsn(a.getOpcode(Opcodes.IMUL));
                currentTypeStack.push(a);
                return true;
            }else if(eval.evaluationType() == StackEvaluationType.SHL) {
                currentTypeStack.pop();
                var a = currentTypeStack.pop();
                mv.visitInsn(a.getOpcode(Opcodes.ISHL));
                currentTypeStack.push(a);
                return true;
            }else if(eval.evaluationType() == StackEvaluationType.SHR) {
                currentTypeStack.pop();
                var a = currentTypeStack.pop();
                mv.visitInsn(a.getOpcode(Opcodes.ISHR));
                currentTypeStack.push(a);
                return true;
            }else {
                throw new IllegalArgumentException("Unknown evaluation type: " + eval.evaluationType());
            }
        }else if(instr instanceof StackInstruction.EvaluateString eval) {
            int dynamicCount = 0;
            StringBuilder result = new StringBuilder();
            ArrayList<String> additionalArguments = new ArrayList<>();
            for(var s : eval.template()) {
                if(s == null) {
                    dynamicCount++;
                    currentTypeStack.pop();
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
            for(int i = 0; i<additionalArguments.size(); i++) {
                res[i + 1] = additionalArguments.get(i);
            }
            var handle = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
            mv.visitInvokeDynamicInsn("makeConcatWithConstants", "(" + "Ljava/lang/String;".repeat(dynamicCount) + ")Ljava/lang/String;", handle, res);

            currentTypeStack.push(Type.getType(String.class));
            return true;
        }else if(instr instanceof StackInstruction.Cast cast) {
            var op = currentTypeStack.pop();
            if((op.getSort() != Type.OBJECT && op.getSort() != Type.ARRAY && cast.type().getSort() != Type.OBJECT && cast.type().getSort() != Type.ARRAY) && ((isIntegerLike(op) && isIntegerLike(cast.type())) || op.getSort() == cast.type().getSort())) {
                currentTypeStack.push(op);
                return true;
            }
            if(isIntegerLike(op)) {
                if(cast.type() == Type.LONG_TYPE) {
                    mv.visitInsn(Opcodes.I2L);
                    currentTypeStack.push(Type.LONG_TYPE);
                    return true;
                }else if(cast.type() == Type.FLOAT_TYPE) {
                    mv.visitInsn(Opcodes.I2F);
                    currentTypeStack.push(Type.FLOAT_TYPE);
                    return true;
                }else if(cast.type() == Type.DOUBLE_TYPE) {
                    mv.visitInsn(Opcodes.I2D);
                    currentTypeStack.push(Type.DOUBLE_TYPE);
                    return true;
                }
            }else if(op == Type.LONG_TYPE) {
                if(cast.type() == Type.INT_TYPE) {
                    mv.visitInsn(Opcodes.L2I);
                    currentTypeStack.push(Type.INT_TYPE);
                    return true;
                }else if(cast.type() == Type.FLOAT_TYPE) {
                    mv.visitInsn(Opcodes.L2F);
                    currentTypeStack.push(Type.FLOAT_TYPE);
                    return true;
                }else if(cast.type() == Type.DOUBLE_TYPE) {
                    mv.visitInsn(Opcodes.L2D);
                    currentTypeStack.push(Type.DOUBLE_TYPE);
                    return true;
                }
            }else if(op == Type.FLOAT_TYPE) {
                if(cast.type() == Type.INT_TYPE) {
                    mv.visitInsn(Opcodes.F2I);
                    currentTypeStack.push(Type.INT_TYPE);
                    return true;
                }else if(cast.type() == Type.LONG_TYPE) {
                    mv.visitInsn(Opcodes.F2L);
                    currentTypeStack.push(Type.LONG_TYPE);
                    return true;
                }else if(cast.type() == Type.DOUBLE_TYPE) {
                    mv.visitInsn(Opcodes.F2D);
                    currentTypeStack.push(Type.DOUBLE_TYPE);
                    return true;
                }
            }else if(op == Type.DOUBLE_TYPE) {
                if(cast.type() == Type.INT_TYPE) {
                    mv.visitInsn(Opcodes.D2I);
                    currentTypeStack.push(Type.INT_TYPE);
                    return true;
                }else if(cast.type() == Type.LONG_TYPE) {
                    mv.visitInsn(Opcodes.D2L);
                    currentTypeStack.push(Type.LONG_TYPE);
                    return true;
                }else if(cast.type() == Type.FLOAT_TYPE) {
                    mv.visitInsn(Opcodes.D2F);
                    currentTypeStack.push(Type.FLOAT_TYPE);
                    return true;
                }
            }
            if(op.getSort() != Type.OBJECT && op.getSort() != Type.ARRAY) {
                throw new IllegalArgumentException("Cannot cast " + op + " to " + cast.type());
            }
            mv.visitTypeInsn(Opcodes.CHECKCAST, cast.type().getInternalName());
            currentTypeStack.push(cast.type());
            return true;
        }else if(instr instanceof StackInstruction.Jump jump) {
            int labelLocation;
            for(labelLocation = 0; labelLocation < instructions.size(); labelLocation++) {
                if(instructions.get(labelLocation) instanceof StackInstruction.Label label) {
                    if(label.label().equals(jump.label())) {
                        break;
                    }
                }
            }
            while (labelLocation >= 0) {
                if(!(instructions.get(labelLocation) instanceof StackInstruction.Label)) {
                    labelLocation++;
                    break;
                }
                labelLocation--;
            }
            if(jump.jumpType() == StackJumpType.GOTO) {
                toTraverseEnd.push(labelLocation);
                mv.visitJumpInsn(Opcodes.GOTO, labelMap.get(jump.label()));
                return false;
            }
            toTraverse.push(labelLocation);
            if(jump.jumpType() == StackJumpType.IF_NULL || jump.jumpType() == StackJumpType.IF_NOT_NULL) {
                currentTypeStack.pop();
                mv.visitJumpInsn(jump.jumpType() == StackJumpType.IF_NULL ? Opcodes.IFNULL : Opcodes.IFNONNULL, labelMap.get(jump.label()));
                return true;
            }
            var a = currentTypeStack.pop();
            switch(jump.jumpType()) {
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
                    mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(jump.label()));
                    return true;
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
                    mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(jump.label()));
                    return true;
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
                    mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(jump.label()));
                    return true;
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
                    mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(jump.label()));
                    return true;
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
                    mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(jump.label()));
                    return true;
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
                    mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(jump.label()));
                    return true;
                }
            }
            var b = currentTypeStack.pop();
            switch(jump.jumpType()) {
                case IF_EQUAL -> {
                    if(isIntegerLike(a) && isIntegerLike(b)) {
                        mv.visitJumpInsn(Opcodes.IF_ICMPEQ, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.LCMP);
                        mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.FCMPL);
                        mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.DCMPL);
                        mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(jump.label()));
                        return true;
                    }
                    if((a.getSort() == Type.OBJECT || a.getSort() == Type.ARRAY) && (b.getSort() == Type.OBJECT || b.getSort() == Type.ARRAY)) {
                        mv.visitJumpInsn(Opcodes.IF_ACMPEQ, labelMap.get(jump.label()));
                        return true;
                    }
                    throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                }
                case IF_NOT_EQUAL -> {
                    if(isIntegerLike(a) && isIntegerLike(b)) {
                        mv.visitJumpInsn(Opcodes.IF_ICMPNE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.LCMP);
                        mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.FCMPL);
                        mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.DCMPL);
                        mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(jump.label()));
                        return true;
                    }
                    if((a.getSort() == Type.OBJECT || a.getSort() == Type.ARRAY) && (b.getSort() == Type.OBJECT || b.getSort() == Type.ARRAY)) {
                        mv.visitJumpInsn(Opcodes.IF_ACMPNE, labelMap.get(jump.label()));
                        return true;
                    }
                    throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                }
                case IF_LESS_THAN -> {
                    if(isIntegerLike(a) && isIntegerLike(b)) {
                        mv.visitJumpInsn(Opcodes.IF_ICMPLT, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.LCMP);
                        mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.FCMPL);
                        mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.DCMPL);
                        mv.visitJumpInsn(Opcodes.IFLT, labelMap.get(jump.label()));
                        return true;
                    }
                    throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                }
                case IF_LESS_EQUAL -> {
                    if(isIntegerLike(a) && isIntegerLike(b)) {
                        mv.visitJumpInsn(Opcodes.IF_ICMPLE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.LCMP);
                        mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.FCMPL);
                        mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.DCMPL);
                        mv.visitJumpInsn(Opcodes.IFLE, labelMap.get(jump.label()));
                        return true;
                    }
                    throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                }
                case IF_GREATER_THAN -> {
                    if(isIntegerLike(a) && isIntegerLike(b)) {
                        mv.visitJumpInsn(Opcodes.IF_ICMPGT, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.LCMP);
                        mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.FCMPG);
                        mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.DCMPG);
                        mv.visitJumpInsn(Opcodes.IFGT, labelMap.get(jump.label()));
                        return true;
                    }
                    throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                }
                case IF_GREATER_EQUAL -> {
                    if(isIntegerLike(a) && isIntegerLike(b)) {
                        mv.visitJumpInsn(Opcodes.IF_ICMPGE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.LONG_TYPE && b == Type.LONG_TYPE) {
                        mv.visitInsn(Opcodes.LCMP);
                        mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.FLOAT_TYPE && b == Type.FLOAT_TYPE) {
                        mv.visitInsn(Opcodes.FCMPG);
                        mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(jump.label()));
                        return true;
                    }
                    if(a == Type.DOUBLE_TYPE && b == Type.DOUBLE_TYPE) {
                        mv.visitInsn(Opcodes.DCMPG);
                        mv.visitJumpInsn(Opcodes.IFGE, labelMap.get(jump.label()));
                        return true;
                    }
                    throw new IllegalArgumentException("Cannot compare " + a + " and " + b);
                }
                default -> throw new IllegalArgumentException("Unknown jump type: " + jump.jumpType());
            }
        }else if(instr instanceof StackInstruction.JumpTyped jump) {
            int labelLocation;
            for(labelLocation = 0; labelLocation < instructions.size(); labelLocation++) {
                if(instructions.get(labelLocation) instanceof StackInstruction.Label label) {
                    if(label.label().equals(jump.label())) {
                        break;
                    }
                }
            }
            while (labelLocation >= 0) {
                if(!(instructions.get(labelLocation) instanceof StackInstruction.Label)) {
                    labelLocation++;
                    break;
                }
                labelLocation--;
            }
            toTraverse.push(labelLocation);
            currentTypeStack.pop();
            switch(jump.jumpType()) {
                case IF_INSTANCEOF -> {
                    mv.visitTypeInsn(Opcodes.INSTANCEOF, jump.type().getInternalName());
                    mv.visitJumpInsn(Opcodes.IFNE, labelMap.get(jump.label()));
                    return true;
                }
                case IF_NOT_INSTANCEOF -> {
                    mv.visitTypeInsn(Opcodes.INSTANCEOF, jump.type().getInternalName());
                    mv.visitJumpInsn(Opcodes.IFEQ, labelMap.get(jump.label()));
                    return true;
                }
                default -> throw new IllegalArgumentException("Unknown jump type: " + jump.jumpType());
            }
        }else if(instr instanceof StackInstruction.Label label) {
            mv.visitLabel(labelMap.get(label.label()));
            var stackClone = new Stack<Type>();
            stackClone.addAll(currentTypeStack);
            labelTypeStackMap.put(label.label(), stackClone);
            return true;
        }
        throw new IllegalArgumentException("Unknown instruction: " + instr);
    }

    // TODO: registers don't get transformed to a better location for more compact bytecode
    // ^^^^ additionally longs/doubles don't have 2 locals assigned to them, which might break if there's a register after a long/double
    // ^^^^ also, parameters might be overwritten by registers if they don't match one to one with the expected argument indices (this means they will break on long/double)
    // ^^^^ hacky way to make registers not break, is to add some constant and multiply the register number by 2, so doubles/longs always fit
    public static void compile(MethodVisitor mv, List<StackInstruction> instructions) {
        mv.visitCode();
        JavaStackCompiler state = new JavaStackCompiler(mv, instructions);
        for(var instr : instructions) {
            if(instr instanceof StackInstruction.Label label) {
                state.labelMap.put(label.label(), new Label());
            }
        }
        state.toTraverse.push(0);
        var currentStack = new Stack<Type>();
        changeTraversal: while(!state.toTraverse.isEmpty()) {
            var traverse = state.toTraverse.popInt();
            var initialInstr = instructions.get(traverse);
            if(initialInstr instanceof StackInstruction.Label label) {
                currentStack = new Stack<>();
                var mapped = state.labelTypeStackMap.get(label.label());
                if(mapped != null) {
                    currentStack.addAll(mapped);
                }
            }
            while(true) {
                if(!state.traversed.add(traverse)) {
                    if(state.toTraverse.isEmpty()) {
                        state.toTraverse.addAll(state.toTraverseEnd);
                        state.toTraverseEnd.clear();
                    }
                    continue changeTraversal;
                }
                var shouldContinue = state.traverseInstruction(traverse, currentStack);
                if(!shouldContinue) {
                    if(state.toTraverse.isEmpty()) {
                        state.toTraverse.addAll(state.toTraverseEnd);
                        state.toTraverseEnd.clear();
                    }
                    continue changeTraversal;
                }
                traverse++;
            }
        }
        mv.visitMaxs(0, 0);
    }

}
