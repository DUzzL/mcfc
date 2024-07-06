package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.DoubleRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import dev.epicpix.minecraftfunctioncompiler.data.IntRangeArgument;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import java.util.function.Consumer;

public final class EmitterHelper {
    private EmitterHelper() {}

    public static void checkDoubleRange(EmitterContext emitter, DoubleRangeArgument range, Consumer<EmitterContext> exec) {
        var cw = emitter.codeWriter();
        var mv = cw.methodVisitor();
        if(range.minimum() != null && range.maximum() == null) {
            cw.writeDouble(range.minimum());
            mv.visitInsn(Opcodes.DCMPL);
            cw.writeInt(-1);
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() == null && range.maximum() != null) {
            cw.writeDouble(range.maximum());
            mv.visitInsn(Opcodes.DCMPG);
            cw.writeInt(1);
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() != null && range.minimum().equals(range.maximum())) {
            cw.writeDouble(range.minimum());
            mv.visitInsn(Opcodes.DCMPG);
            var endLabel = cw.executeIfLabeled(Opcodes.IFNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else {
            var uid = cw.generateUniqueLocal();
            var localValue = cw.createLocal(uid, "D");
            cw.storeLocal(uid);
            cw.loadLocal(uid);
            cw.writeDouble(range.minimum());
            mv.visitInsn(Opcodes.DCMPL);
            cw.writeInt(-1);
            var endLabel1 = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            cw.loadLocal(uid);
            cw.writeDouble(range.maximum());
            mv.visitInsn(Opcodes.DCMPG);
            cw.writeInt(1);
            var endLabel2 = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel2);
            mv.visitLabel(endLabel1);
            cw.destroyLocal(localValue);
        }
    }

    public static void checkFloatRange(EmitterContext emitter, FloatRangeArgument range, Consumer<EmitterContext> exec) {
        var cw = emitter.codeWriter();
        var mv = cw.methodVisitor();
        if(range.minimum() != null && range.maximum() == null) {
            cw.writeFloat(range.minimum());
            mv.visitInsn(Opcodes.FCMPL);
            cw.writeInt(-1);
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() == null && range.maximum() != null) {
            cw.writeFloat(range.maximum());
            mv.visitInsn(Opcodes.FCMPG);
            cw.writeInt(1);
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() != null && range.minimum().equals(range.maximum())) {
            cw.writeFloat(range.minimum());
            mv.visitInsn(Opcodes.FCMPG);
            var endLabel = cw.executeIfLabeled(Opcodes.IFNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else {
            var uid = cw.generateUniqueLocal();
            var localValue = cw.createLocal(uid, "F");
            cw.storeLocal(uid);
            cw.loadLocal(uid);
            cw.writeFloat(range.minimum());
            mv.visitInsn(Opcodes.FCMPL);
            cw.writeInt(-1);
            var endLabel1 = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            cw.loadLocal(uid);
            cw.writeFloat(range.maximum());
            mv.visitInsn(Opcodes.FCMPG);
            cw.writeInt(1);
            var endLabel2 = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel2);
            mv.visitLabel(endLabel1);
            cw.destroyLocal(localValue);
        }
    }

    public static void checkIntRange(EmitterContext emitter, IntRangeArgument range, Consumer<EmitterContext> exec) {
        var cw = emitter.codeWriter();
        var mv = cw.methodVisitor();
        if(range.minimum() != null && range.maximum() == null) {
            cw.writeInt(range.minimum());
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPGE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() == null && range.maximum() != null) {
            cw.writeInt(range.maximum());
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPLE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() != null && range.minimum().equals(range.maximum())) {
            cw.writeInt(range.minimum());
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPEQ);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() != null) {
            var uid = cw.generateUniqueLocal();
            var localValue = cw.createLocal(uid, "I");
            cw.storeLocal(uid);
            cw.loadLocal(uid);
            cw.writeInt(range.minimum());
            var endLabel1 = cw.executeIfLabeled(Opcodes.IF_ICMPGE);
            cw.loadLocal(uid);
            cw.writeInt(range.maximum());
            var endLabel2 = cw.executeIfLabeled(Opcodes.IF_ICMPLE);
            exec.accept(emitter);
            mv.visitLabel(endLabel2);
            mv.visitLabel(endLabel1);
            cw.destroyLocal(localValue);
        }
    }

    public static void checkNotIntRange(EmitterContext emitter, IntRangeArgument range, Consumer<EmitterContext> exec) {
        var cw = emitter.codeWriter();
        var mv = cw.methodVisitor();
        if(range.minimum() != null && range.maximum() == null) {
            cw.writeInt(range.minimum());
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPLT);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() == null && range.maximum() != null) {
            cw.writeInt(range.maximum());
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPGT);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() != null && range.minimum().equals(range.maximum())) {
            cw.writeInt(range.minimum());
            var endLabel = cw.executeIfLabeled(Opcodes.IF_ICMPNE);
            exec.accept(emitter);
            mv.visitLabel(endLabel);
        }else if(range.minimum() != null) {
            var skip = new Label();
            var uid = cw.generateUniqueLocal();
            var localValue = cw.createLocal(uid, "I");
            cw.storeLocal(uid);
            cw.loadLocal(uid);
            cw.writeInt(range.minimum());
            var endLabel1 = cw.executeIfLabeled(Opcodes.IF_ICMPGE);
            cw.loadLocal(uid);
            cw.writeInt(range.maximum());
            var endLabel2 = cw.executeIfLabeled(Opcodes.IF_ICMPLE);
            mv.visitJumpInsn(Opcodes.GOTO, skip);
            mv.visitLabel(endLabel2);
            mv.visitLabel(endLabel1);
            cw.destroyLocal(localValue);
            exec.accept(emitter);
            mv.visitLabel(skip);
        }
    }
}