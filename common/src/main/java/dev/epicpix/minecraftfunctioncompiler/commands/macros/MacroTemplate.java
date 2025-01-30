package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record MacroTemplate<T>(List<MacroPart> parts, MacroType<T> macroType) {
    public interface MacroPart {}

    public BytecodeRegister writeBytecode(EmitterContext context) {
        ArrayList<BytecodeValue> concatArgs = new ArrayList<>();
        for(var part : parts) {
            if(part instanceof MacroKind macro) {
                concatArgs.add(context.getMacro(macro.source));
            }else if(part instanceof LiteralKind literal) {
                concatArgs.add(new BytecodeValue.StringValue(literal.literal));
            }
        }
        return context.codeWriter().stringConcat(concatArgs);
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
        return new MacroTemplate<>(List.of(new LiteralKind(value)), MacroType.STRING_MACRO_TYPE);
    }
}
