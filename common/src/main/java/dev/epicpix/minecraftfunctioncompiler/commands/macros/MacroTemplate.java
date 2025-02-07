package dev.epicpix.minecraftfunctioncompiler.commands.macros;

import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.EMITTER_MACRO_MULTIPLE_NON_STRING_ARGUMENTS;

public record MacroTemplate<T>(List<MacroPart> parts, MacroType<T> macroType) {
    public interface MacroPart {}

    public BytecodeRegister writeBytecode(EmitterContext context) {
        if(macroType != MacroType.STRING_MACRO_TYPE) {
            if(parts.size() != 1) {
                throw new DiagnosticException(Diagnostic.emitterError(EMITTER_MACRO_MULTIPLE_NON_STRING_ARGUMENTS, context.location(), "Macro contains multiple parts while not being a string macro: \"" + this + "\" | " + macroType));
            }else {
                var part = parts.get(0);
                if(part instanceof MacroKind macro) {
                    return context.getMacro(macro.source);
                }else if(part instanceof MacroTemplate.LiteralKind<?> literal) {
                    var staticContent = context.codeWriter().createRegister();
                    if(macroType == MacroType.INT_MACRO_TYPE) {
                        context.codeWriter().setValue(staticContent, new BytecodeValue.IntegerValue((int) literal.literal));
                    }else if(macroType == MacroType.FLOAT_MACRO_TYPE) {
                        context.codeWriter().setValue(staticContent, new BytecodeValue.FloatValue((float) literal.literal));
                    }else if(macroType == MacroType.DOUBLE_MACRO_TYPE) {
                        context.codeWriter().setValue(staticContent, new BytecodeValue.DoubleValue((double) literal.literal));
                    }
                    return staticContent;
                }
            }
        }
        ArrayList<BytecodeValue> concatArgs = new ArrayList<>();
        for(var part : parts) {
            if(part instanceof MacroKind macro) {
                concatArgs.add(context.getMacro(macro.source));
            }else if(part instanceof LiteralKind<?> literal) {
                if(macroType == MacroType.STRING_MACRO_TYPE) {
                    concatArgs.add(new BytecodeValue.StringValue((String) literal.literal));
                }else if(macroType == MacroType.INT_MACRO_TYPE) {
                    concatArgs.add(new BytecodeValue.IntegerValue((int) literal.literal));
                }else if(macroType == MacroType.FLOAT_MACRO_TYPE) {
                    concatArgs.add(new BytecodeValue.FloatValue((float) literal.literal));
                }else if(macroType == MacroType.DOUBLE_MACRO_TYPE) {
                    concatArgs.add(new BytecodeValue.DoubleValue((double) literal.literal));
                }
            }
        }
        return context.codeWriter().stringConcat(concatArgs);
    }

    public record LiteralKind<T>(T literal) implements MacroPart {
        public String toString() {
            return literal.toString();
        }
    }

    public record MacroKind(String source, List<MacroValidator> validators) implements MacroPart {
        public String toString() {
            return "$(" + source + ")";
        }
    }

    public boolean isExact(T value) {
        return parts.size() == 1 && parts.get(0) instanceof LiteralKind<?> literal && literal.literal.equals(value);
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
            if(value instanceof LiteralKind<?> literal && literal.literal instanceof String strLiteral) {
                StringBuilder totalLiteral = new StringBuilder(strLiteral);
                while(i + 1 < parts.size() && parts.get(i + 1) instanceof LiteralKind<?> nextLiteral && nextLiteral.literal instanceof String nextStrLiteral) {
                    totalLiteral.append(nextStrLiteral);
                    i++;
                }
                newParts.add(new LiteralKind<>(totalLiteral.toString()));
            }else {
                newParts.add(value);
            }
        }
        return newParts;
    }

    public static MacroTemplate<String> fromStringLiteral(String value) {
        return new MacroTemplate<>(List.of(new LiteralKind<>(value)), MacroType.STRING_MACRO_TYPE);
    }

    public static MacroTemplate<Integer> fromInteger(int value) {
        return new MacroTemplate<>(List.of(new LiteralKind<>(value)), MacroType.INT_MACRO_TYPE);
    }

    public static MacroTemplate<Float> fromFloat(float value) {
        return new MacroTemplate<>(List.of(new LiteralKind<>(value)), MacroType.FLOAT_MACRO_TYPE);
    }

    public static MacroTemplate<Double> fromDouble(double value) {
        return new MacroTemplate<>(List.of(new LiteralKind<>(value)), MacroType.DOUBLE_MACRO_TYPE);
    }
}
