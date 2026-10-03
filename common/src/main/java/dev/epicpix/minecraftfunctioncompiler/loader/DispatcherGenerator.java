package dev.epicpix.minecraftfunctioncompiler.loader;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the vanilla->compiled dispatch classes: one class per group of compiled functions of a batch,
 * implementing {@link CompiledFunction}. An instance holds the function's index in its group;
 * {@code run} is a {@code tableswitch} on it whose cases are a direct {@code invokestatic} of the function's
 * entry point. Measured on JDK 25 (64 batches x 100 functions, random dispatch): ~41 ns/call, against ~80 ns
 * for MethodHandleProxies (one hidden class per interface, target in a non-constant instance field, so every
 * call is an interface call plus a non-inlinable {@code invokeExact}), ~75 ns for a {@code MethodHandle}
 * field with {@code invokeExact}, and ~47 ns for LambdaMetafactory (one hidden class per function).
 *
 * Generated at link time from the placements (not cached: it is cheap and fully determined by them), in the
 * same loader as the batches.
 */
final class DispatcherGenerator {
    private DispatcherGenerator() {}

    /**
     * Functions per dispatch class. Each case costs 16 bytes of {@code run} code (12 for the call, 4 for the
     * switch entry); 256 keeps {@code run} at ~4 KB, under HotSpot's 8000-byte limit for JIT compilation.
     */
    static final int GROUP_SIZE = 256;

    private static final ClassDesc CD_COMPILED_FUNCTION = CompiledFunction.class.describeConstable().orElseThrow();
    private static final ClassDesc CD_ILLEGAL_STATE = IllegalStateException.class.describeConstable().orElseThrow();
    private static final MethodTypeDesc RUN_TYPE = MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Object, ConstantDescs.CD_Object);
    private static final MethodTypeDesc INIT_TYPE = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int);
    private static final String ID_FIELD = "id";

    static String binaryName(int batchIndex, int group) {
        return BatchCompiler.binaryName(batchIndex) + "$Dispatch" + group;
    }

    /**
     * @param entryNames entry-point method names of the group's functions, in id order
     * @param stackType  first parameter type of every entry point
     * @param argsType   second parameter type of every entry point
     */
    static byte[] generate(String binaryName, ClassDesc batchClass, List<String> entryNames, ClassDesc stackType, ClassDesc argsType) {
        var self = ClassDesc.of(binaryName);
        var entryType = MethodTypeDesc.of(ConstantDescs.CD_long, stackType, argsType);
        return ClassGenerator.classFile().build(self, cb -> {
            cb.withVersion(ClassFile.JAVA_16_VERSION, 0);
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_COMPILED_FUNCTION);
            cb.withField(ID_FIELD, ConstantDescs.CD_int, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            cb.withMethodBody(ConstantDescs.INIT_NAME, INIT_TYPE, ClassFile.ACC_PUBLIC, code -> code
                .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                .aload(0).iload(1).putfield(self, ID_FIELD, ConstantDescs.CD_int)
                .return_());
            cb.withMethodBody("run", RUN_TYPE, ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, code -> {
                var invalid = code.newLabel();
                var labels = new Label[entryNames.size()];
                var cases = new ArrayList<SwitchCase>(entryNames.size());
                for(int i = 0; i < labels.length; i++) {
                    labels[i] = code.newLabel();
                    cases.add(SwitchCase.of(i, labels[i]));
                }
                code.aload(0).getfield(self, ID_FIELD, ConstantDescs.CD_int)
                    .tableswitch(0, labels.length - 1, invalid, cases);
                for(int i = 0; i < labels.length; i++) {
                    code.labelBinding(labels[i])
                        .aload(1).checkcast(stackType)
                        .aload(2).checkcast(argsType)
                        .invokestatic(batchClass, entryNames.get(i), entryType)
                        .lreturn();
                }
                code.labelBinding(invalid)
                    .new_(CD_ILLEGAL_STATE).dup()
                    .invokespecial(CD_ILLEGAL_STATE, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                    .athrow();
            });
        });
    }
}
