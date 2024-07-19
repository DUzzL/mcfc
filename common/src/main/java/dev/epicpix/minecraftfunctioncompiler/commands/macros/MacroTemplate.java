package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record MacroTemplate<T>(List<MacroPart> parts) {
    public interface MacroPart {}

    public void writeBytecode(EmitterContext context) {
        var mv = context.codeWriter().methodVisitor();
        StringBuilder result = new StringBuilder();
        ArrayList<String> additionalArguments = new ArrayList<>();
        int macroCount = 0;
        for(var part : parts) {
            if(part instanceof MacroKind macro) {
                result.append("\u0001");
                context.codeWriter().loadLocal(context.macroLocations().get(macro.source));
                macroCount++;
            }else if(part instanceof LiteralKind literal) {
                if(literal.literal.contains("\u0001") || literal.literal.contains("\u0002")) {
                    result.append("\u0002");
                    additionalArguments.add(literal.literal);
                }else {
                    result.append(literal.literal);
                }
            }
        }
        Object[] res = new Object[1 + additionalArguments.size()];
        res[0] = result.toString();
        for(int i = 0; i<additionalArguments.size(); i++) {
            res[i + 1] = additionalArguments.get(i);
        }
        var handle = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        mv.visitInvokeDynamicInsn("makeConcatWithConstants", "(" + "Ljava/lang/String;".repeat(macroCount) + ")Ljava/lang/String;", handle, res);

    }

    public record LiteralKind(String literal) implements MacroPart {
        public String toString() {
            return literal;
        }
    }

    public record MacroKind(String source, List<MacroValidator> validators) implements MacroPart {
        public String toString() {
            return "$(" + source + ")";
        }
    }

    public boolean isLiteral(String value) {
        return parts.size() == 1 && parts.get(0) instanceof LiteralKind literal && literal.literal.equals(value);
    }

    public List<MacroKind> extractMacros() {
        ArrayList<MacroKind> macros = new ArrayList<>();
        for(var part : parts) {
            if(part instanceof MacroKind macro) {
                macros.add(macro);
            }
        }
        return macros;
    }

    public String toString() {
        return parts.stream().map(Object::toString).collect(Collectors.joining(""));
    }

    public static List<MacroPart> mergeParts(List<MacroPart> parts) {
        ArrayList<MacroPart> newParts = new ArrayList<>();
        for(int i = 0; i<parts.size(); i++) {
            var value = parts.get(i);
            if(value instanceof LiteralKind literal) {
                StringBuilder totalLiteral = new StringBuilder(literal.literal);
                while(i + 1 < parts.size() && parts.get(i + 1) instanceof LiteralKind nextLiteral) {
                    totalLiteral.append(nextLiteral.literal);
                    i++;
                }
                newParts.add(new LiteralKind(totalLiteral.toString()));
            }else {
                newParts.add(value);
            }
        }
        return newParts;
    }

    public static MacroTemplate<String> fromLiteral(String value) {
        return new MacroTemplate<>(List.of(new LiteralKind(value)));
    }
}
