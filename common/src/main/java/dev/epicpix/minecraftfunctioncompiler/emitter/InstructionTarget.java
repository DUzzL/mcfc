package dev.epicpix.minecraftfunctioncompiler.emitter;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;

public sealed interface InstructionTarget permits InstructionTarget.FieldTarget, InstructionTarget.FunctionTarget {
    record FunctionTarget(ClassDesc classType, String name, MethodTypeDesc descriptor, boolean isStatic, boolean isInterface, boolean isSpecial, boolean noReordering) implements InstructionTarget {
        public static FunctionTarget initFunction(ClassDesc clazz, ClassDesc... arguments) {
            return new FunctionTarget(clazz, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, arguments), false, false, true, false);
        }

        public static FunctionTarget initFunction(Class<?> clazz, Class<?>... arguments) {
            return initFunction(clazz.describeConstable().orElseThrow(), Arrays.stream(arguments).map(x -> x.describeConstable().orElseThrow()).toArray(ClassDesc[]::new));
        }

        public static FunctionTarget staticFunction(ClassDesc clazz, String name, ClassDesc returnType, ClassDesc... arguments) {
            return new FunctionTarget(clazz, name, MethodTypeDesc.of(returnType, arguments), true, false, false, false);
        }

        public static FunctionTarget staticFunction(Class<?> clazz, String name, Class<?> returnType, Class<?>... arguments) {
            return staticFunction(clazz.describeConstable().orElseThrow(), name, returnType.describeConstable().orElseThrow(), Arrays.stream(arguments).map(x -> x.describeConstable().orElseThrow()).toArray(ClassDesc[]::new));
        }
    }
    record FieldTarget(ClassDesc classType, String name, ClassDesc descriptor, boolean isStatic, boolean isWrite) implements InstructionTarget {}
}
