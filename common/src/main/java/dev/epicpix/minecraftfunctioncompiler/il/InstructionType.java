package dev.epicpix.minecraftfunctioncompiler.il;

import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public record InstructionType(String name, String[] argumentNames, int flags, int id) {

    // Types are created from several class initializers (generated tables, the optimizer's placeholder) that can run
    // concurrently on compile workers, so id allocation and the claim check below share one lock.
    private static final Object ID_LOCK = new Object();
    private static int nextId;
    private static final BitSet UNCLAIMED_IDS = new BitSet();

    public InstructionType {
        synchronized(ID_LOCK) {
            if(!UNCLAIMED_IDS.get(id)) throw new IllegalArgumentException("Don't provide the id argument manually");
            UNCLAIMED_IDS.clear(id);
        }
    }

    public InstructionType(String name, String[] argumentNames, int flags) {
        this(name, argumentNames, flags, allocateId());
    }

    public InstructionType(String name, String[] argumentNames) {
        this(name, argumentNames, 0);
    }

    private static int allocateId() {
        synchronized(ID_LOCK) {
            int id = ++nextId;
            UNCLAIMED_IDS.set(id);
            return id;
        }
    }

    public boolean equals(@Nullable Object o) {
        if(o == null || getClass() != o.getClass()) return false;
        return id == ((InstructionType) o).id;
    }

    public int hashCode() {
        return id;
    }

    public static final int PURE = 1 << 0;

    public String toString() {
        return name;
    }

    public int getArgumentIndex(String argumentName) {
        for(int i = 0; i < argumentNames.length; i++) {
            if(argumentName.equals(argumentNames[i])) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid argument name: " + argumentName);
    }

    public boolean isPure() {
        return (flags & PURE) != 0;
    }

    public boolean returnsValue() {
        return argumentNames.length >= 1 && argumentNames[0].equals("result");
    }

}
