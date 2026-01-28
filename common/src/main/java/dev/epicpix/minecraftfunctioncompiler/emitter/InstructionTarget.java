package dev.epicpix.minecraftfunctioncompiler.emitter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

public sealed interface InstructionTarget permits InstructionTarget.FieldTarget, InstructionTarget.FunctionTarget {
    record FunctionTarget(String className, String name, String descriptor, boolean isStatic, boolean isInterface, boolean isSpecial, boolean noReordering) implements InstructionTarget {
        public static FunctionTarget initFunction(Class<?> clazz, Class<?>... arguments) {
            String desc = clazz.descriptorString();
            String args = Arrays.stream(arguments).map(Class::descriptorString).collect(Collectors.joining(""));
            return new FunctionTarget(desc.substring(1, desc.length() - 1), "<init>", "(" + args + ")V", false, false, true, false);
        }

        public static FunctionTarget staticFunction(Class<?> clazz, String name, Class<?> returnType, Class<?>... arguments) {
            String desc = clazz.descriptorString();
            String args = Arrays.stream(arguments).map(Class::descriptorString).collect(Collectors.joining(""));
            return new FunctionTarget(desc.substring(1, desc.length() - 1), name, "(" + args + ")" + returnType.descriptorString(), true, false, false, false);
        }
    }
    record FieldTarget(String className, String name, String descriptor, boolean isStatic, boolean isWrite) implements InstructionTarget {
        public static FieldTarget fromReflection(Field field, boolean isWrite) {
            String desc = field.getDeclaringClass().descriptorString();
            return new FieldTarget(desc.substring(1, desc.length() - 1), field.getName(), field.getType().descriptorString(), (field.getModifiers() & Modifier.STATIC) != 0, isWrite);
        }
    }
}
