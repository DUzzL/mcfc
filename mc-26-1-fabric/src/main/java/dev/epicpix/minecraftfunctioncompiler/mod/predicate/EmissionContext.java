package dev.epicpix.minecraftfunctioncompiler.mod.predicate;

import java.lang.classfile.Label;

/**
 * Shared per-class-build state used by {@link ConditionEmitter}s. Carries the
 * "drop-out" label they should jump to on a falsy result (early-out of the
 * outer AND chain) and a counter for unique register/field name generation.
 *
 * @param failureTarget where to jump when an emitted condition produced
 *                      {@code false}; the compiler emits an
 *                      {@code ireturn false} at this label after the chain.
 * @param uniqueId      monotonic counter — emitters call
 *                      {@link #nextUnique} to make non-colliding names for
 *                      synthesized static fields / locals.
 */
public record EmissionContext(Label failureTarget, int[] uniqueId) {

    public EmissionContext(Label failureTarget) {
        this(failureTarget, new int[]{0});
    }

    public int nextUnique() {
        return uniqueId[0]++;
    }
}
