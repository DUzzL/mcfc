package dev.epicpix.minecraftfunctioncompiler.mod.predicate;

import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.lang.classfile.CodeBuilder;

/**
 * Emits bytecode for a single {@link LootItemCondition} as part of the
 * AND-chain inside a compiled {@code ContextAwarePredicate}.
 *
 * <p>The emitter contract: on entry to {@link #emit}, the local slot 1 of the
 * outer method holds the {@code LootContext}. The emitter must produce a
 * boolean on the stack representing the condition's result, then immediately
 * jump to the supplied {@code failureTarget} if the result is false (early
 * termination of the AND chain). On successful emission the next condition
 * follows; after all conditions emit, the method returns {@code true}.
 *
 * <p>Each emitter is specific to one runtime condition class. Implementations
 * close over the condition instance's fields by storing them in static fields
 * on the generated class (the loader rebuilds the static state at
 * instantiation). For very simple conditions like
 * {@code LootItemRandomChanceCondition} with a constant probability, the
 * field value can be inlined as a constant.
 *
 * @param <T> the specific condition type this emitter handles
 */
public interface ConditionEmitter<T extends LootItemCondition> {

    /**
     * The condition class this emitter handles. Used by
     * {@link PredicateCompiler} to dispatch over the conditions list.
     */
    Class<T> conditionType();

    /**
     * Emit the AND-chain step for {@code condition} into {@code code}. On the
     * supplied {@link EmissionContext}, the method has signature
     * {@code (Ljava/util/function/Predicate;Lnet/minecraft/world/level/storage/loot/LootContext;)Z}
     * (note: the implementing class is itself the Predicate; slot 0 is this,
     * slot 1 is the LootContext).
     *
     * <p>The emitter must leave the operand stack balanced and the locals
     * unchanged outside the slots it explicitly allocates.
     */
    void emit(T condition, CodeBuilder code, EmissionContext ctx);
}
