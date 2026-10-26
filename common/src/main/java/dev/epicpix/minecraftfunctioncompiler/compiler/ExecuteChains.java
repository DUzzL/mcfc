package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.params.*;
import dev.epicpix.minecraftfunctioncompiler.data.SelectorArgument;

/** Static shape queries over parsed `execute` chains. */
final class ExecuteChains {
    private ExecuteChains() {}

    private static final ClassValue<Boolean> MODIFIER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if(!type.isRecord()) return false;
            // compiled as one vanilla command (the nested command is part of the fallback text): terminals
            if(type == CommandTimeOf.class || type == CommandExecuteStoreEntity.class) return false;
            for(var component : type.getRecordComponents()) {
                if(component.getType() == CommandParams.class) return true;
            }
            return false;
        }
    };

    /** A command that wraps another command (`execute …`, `return run …`); everything else is a terminal. */
    static boolean isModifier(CommandParams command) {
        return MODIFIER.get(command.getClass());
    }

    /**
     * True when the modifier chain starting at {@code command} yields at most one source for the
     * command that ends it, so the body can be compiled inline without materializing a fork list.
     * A store modifier ends the chain (its body is compiled as a fresh chain).
     */
    static boolean isSingleSource(CommandParams command) {
        var current = command;
        while(true) {
            switch(current) {
                case CommandExecuteRun _, CommandExecuteStoreScore _, CommandExecuteStoreStorage _ -> {
                    return true;
                }
                case CommandExecuteAs(SelectorArgument targets, CommandParams inner) -> {
                    if(!isSingle(targets)) return false;
                    current = inner;
                }
                case CommandExecuteAt(SelectorArgument targets, CommandParams inner) -> {
                    if(!isSingle(targets)) return false;
                    current = inner;
                }
                case CommandExecutePositionedAs(SelectorArgument targets, CommandParams inner) -> {
                    if(!isSingle(targets)) return false;
                    current = inner;
                }
                case CommandExecuteRotatedAs(SelectorArgument targets, CommandParams inner) -> {
                    if(!isSingle(targets)) return false;
                    current = inner;
                }
                case CommandExecuteOnPassengers _ -> {
                    return false;
                }
                default -> {
                    if(!isModifier(current)) return true;
                    var inner = innerCommand(current);
                    if(inner == null) return false;
                    current = inner;
                }
            }
        }
    }

    private static boolean isSingle(SelectorArgument selector) {
        return selector.self() || selector.playerName() != null || selector.entityUuid() != null
            || (selector.limit() != null && selector.limit() <= 1);
    }

    /** True when the chain starting at {@code command} contains a {@code return} (as a modifier or its terminal),
     * including a vanilla fallback terminal that may run one. */
    static boolean containsReturn(CommandParams command) {
        for(CommandParams current = command; current != null; current = isModifier(current) ? innerCommand(current) : null) {
            if(current instanceof CommandReturnValue || current instanceof CommandReturnFail || current instanceof CommandReturnRun) return true;
            if(current instanceof CommandFallbackVanilla(String rawCommand) && CommandCompiler.mayContainReturn(rawCommand)) return true;
        }
        return false;
    }

    private static CommandParams innerCommand(CommandParams command) {
        try {
            for(var component : command.getClass().getRecordComponents()) {
                if(component.getType() == CommandParams.class) {
                    return (CommandParams) component.getAccessor().invoke(command);
                }
            }
        }catch(ReflectiveOperationException e) {
            return null;
        }
        return null;
    }
}
