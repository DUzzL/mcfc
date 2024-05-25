package dev.epicpix.minecraftfunctioncompiler.emitter;

import dev.epicpix.minecraftfunctioncompiler.data.FloatRangeArgument;
import org.objectweb.asm.Opcodes;
import java.util.function.Consumer;

public final class EmitterHelper {
    private EmitterHelper() {}

    public static void checkFloatRange(EmitterContext emitter, FloatRangeArgument range, Consumer<EmitterContext> exec) {
        var cw = emitter.getCodeWriter();
        var mv = cw.getMethodVisitor();
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
}