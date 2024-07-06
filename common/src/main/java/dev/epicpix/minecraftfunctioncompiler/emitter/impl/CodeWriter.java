package dev.epicpix.minecraftfunctioncompiler.emitter.impl;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;
import java.util.stream.Collectors;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DATA_CLASS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.DEBUG;

public final class CodeWriter {
    private final MethodVisitor methodVisitor;
    private final HashMap<String, Local> locals;
    private int localsUsage = 0;
    private final HashSet<Integer> availableLocals;
    private final ArrayList<Local> generatedLocals;

    public CodeWriter(
            MethodVisitor methodVisitor,

            HashMap<String, Local> locals,
            HashSet<Integer> availableLocals,
            ArrayList<Local> generatedLocals
    ) {
        this.methodVisitor = methodVisitor;
        this.locals = locals;
        this.availableLocals = availableLocals;
        this.generatedLocals = generatedLocals;
    }

    public CodeWriter(MethodVisitor methodVisitor) {
        this(methodVisitor, new HashMap<>(), new HashSet<>(), new ArrayList<>());
    }

    private int allocateLocalIndex(boolean longKind) {
        if(!availableLocals.isEmpty()) {
            if(!longKind) {
                int localIndex = availableLocals.toArray(new Integer[0])[0];
                availableLocals.remove(localIndex);
                return localIndex;
            }else {
                Integer[] available = availableLocals.toArray(new Integer[0]);
                var definedLocals = new HashSet<>(locals.values().stream().map(Local::index).toList());
                for(Integer avl : available) {
                    if(availableLocals.contains(avl + 1) || !definedLocals.contains(avl + 1)) {
                        availableLocals.remove(avl);
                        availableLocals.remove(avl + 1);
                        return avl;
                    }
                }
            }
        }
        return localsUsage;
    }

    public Local createLocal(String local, String type) {
        var localStart = new Label();
        methodVisitor.visitLabel(localStart);
        boolean longKind = type.equals("J") || type.equals("D");
        var localValue = new Local(local, type, localStart, new Label(), allocateLocalIndex(longKind), longKind);
        locals.put(local, localValue);
        generatedLocals.add(localValue);
        localsUsage += longKind ? 2 : 1;
        return localValue;
    }

    public void destroyLocal(Local local) {
        methodVisitor.visitLabel(local.end());
        locals.remove(local.name());
        localsUsage -= local.longKind() ? 2 : 1;
        availableLocals.add(local.index());
        if(local.longKind()) {
            availableLocals.add(local.index() + 1);
        }
    }

    public void writeLocalVariables() {
        if(DEBUG) {
            for(var it : generatedLocals) {
                methodVisitor.visitLocalVariable(it.name().replace(".", "$").replace(":", "$"), it.descriptor(), null, it.start(), it.end(), it.index());
            }
        }
    }

    public void loadLocal(String local) {
        var localVar = locals.get(local);
        switch(localVar.descriptor()) {
            case "I" -> methodVisitor.visitVarInsn(Opcodes.ILOAD, localVar.index());
            case "F" -> methodVisitor.visitVarInsn(Opcodes.FLOAD, localVar.index());
            case "D" -> methodVisitor.visitVarInsn(Opcodes.DLOAD, localVar.index());
            default -> methodVisitor.visitVarInsn(Opcodes.ALOAD, localVar.index());
        }
    }

    public void storeLocal(String local) {
        var localVar = locals.get(local);
        switch(localVar.descriptor()) {
            case "I" -> methodVisitor.visitVarInsn(Opcodes.ISTORE, localVar.index());
            case "F" -> methodVisitor.visitVarInsn(Opcodes.FSTORE, localVar.index());
            case "D" -> methodVisitor.visitVarInsn(Opcodes.DSTORE, localVar.index());
            default -> methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar.index());
        }
    }

    public void writeInt(int value) {
        if (value >= -1 && value <= 5) {
            methodVisitor.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            methodVisitor.visitLdcInsn(value);
        }
    }

    public void writeLong(long value) { methodVisitor.visitLdcInsn(value); }
    public void writeFloat(float value) { methodVisitor.visitLdcInsn(value); }
    public void writeDouble(double value) { methodVisitor.visitLdcInsn(value); }
    public void writeString(String value) { methodVisitor.visitLdcInsn(value); }
    public void writeNull() { methodVisitor.visitInsn(Opcodes.ACONST_NULL); }

    public void newObjectCreate(Class<?> owner) {
        var ownerDescriptor = owner.descriptorString();
        methodVisitor.visitTypeInsn(Opcodes.NEW, ownerDescriptor.substring(1, ownerDescriptor.length() - 1));
        methodVisitor.visitInsn(Opcodes.DUP);
    }

    public void newObjectInit(Class<?> owner, Class<?>... types) {
        var ownerName = owner.descriptorString();
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, ownerName.substring(1, ownerName.length() - 1), "<init>", "(" + Arrays.stream(types).map(Class::descriptorString).collect(Collectors.joining("")) + ")V", false);
    }

    public void getDataField(String name, Class<?> type) {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, DATA_CLASS, name, type.descriptorString());
    }

    public void setDataField(String name, Class<?> type) {
        methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, DATA_CLASS, name, type.descriptorString());
    }

    public void castType(Class<?> type) {
        var desc = type.descriptorString();
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, desc.substring(1, desc.length() - 1));
    }

    private int getIfJumpOpcode(int comparison) {
        return switch (comparison) {
            case Opcodes.IFNONNULL -> Opcodes.IFNULL;
            case Opcodes.IFNULL -> Opcodes.IFNONNULL;
            case Opcodes.IFNE -> Opcodes.IFNE;
            case Opcodes.IFEQ -> Opcodes.IFEQ;
            case Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT;
            case Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE;
            default -> throw new UnsupportedOperationException("Unknown comparison opcode: " + comparison);
        };
    }

    public Label executeIfLabeled(int comparison) {
        var endLabel = new Label();
        methodVisitor.visitJumpInsn(getIfJumpOpcode(comparison), endLabel);
        return endLabel;
    }

    public String generateUniqueLocal() {
        return "L:" + generatedLocals.size();
    }

    public MethodVisitor methodVisitor() {
        return methodVisitor;
    }

}
