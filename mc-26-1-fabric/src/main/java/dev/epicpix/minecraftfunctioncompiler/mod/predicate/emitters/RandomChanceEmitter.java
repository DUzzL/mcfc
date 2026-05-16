package dev.epicpix.minecraftfunctioncompiler.mod.predicate.emitters;

import dev.epicpix.minecraftfunctioncompiler.mod.predicate.ConditionEmitter;
import dev.epicpix.minecraftfunctioncompiler.mod.predicate.EmissionContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * Reference {@link ConditionEmitter} for the simplest condition shape — a
 * single random-chance roll. Two emission paths:
 *
 * <ol>
 *   <li><b>Constant probability</b> ({@code chance} is a
 *       {@code ConstantValue}): inlines the float as a {@code ldc} and emits
 *       {@code context.getRandom().nextFloat() < CONST} directly. ~4 bytecode
 *       instructions, no field loads, no virtual dispatch.</li>
 *   <li><b>Dynamic probability</b>: falls back to calling the original
 *       {@code chance.getFloat(context)} via a captured field. Still saves
 *       one layer of dispatch (the condition itself) but not the
 *       NumberProvider lookup.</li>
 * </ol>
 *
 * <p>This pattern — peek at the condition's constant fields, inline what you
 * can, fall back to a captured reference otherwise — is the model the other
 * emitters should follow.
 */
public final class RandomChanceEmitter implements ConditionEmitter<LootItemRandomChanceCondition> {

    private static final ClassDesc CD_LOOT_CONTEXT = ClassDesc.of("net.minecraft.world.level.storage.loot.LootContext");
    private static final ClassDesc CD_RANDOM_SOURCE = ClassDesc.of("net.minecraft.util.RandomSource");
    private static final ClassDesc CD_NUMBER_PROVIDER = ClassDesc.of("net.minecraft.world.level.storage.loot.providers.number.NumberProvider");

    @Override
    public Class<LootItemRandomChanceCondition> conditionType() {
        return LootItemRandomChanceCondition.class;
    }

    @Override
    public void emit(LootItemRandomChanceCondition condition, CodeBuilder code, EmissionContext ctx) {
        NumberProvider chance = condition.chance();

        // Push: context.getRandom().nextFloat()
        code.aload(1);  // LootContext
        code.invokeinterface(CD_LOOT_CONTEXT, "getRandom", MethodTypeDesc.of(CD_RANDOM_SOURCE));
        code.invokeinterface(CD_RANDOM_SOURCE, "nextFloat", MethodTypeDesc.of(ClassDesc.ofDescriptor("F")));

        // Push: probability  (constant fast-path or dynamic fallback)
        if (chance instanceof ConstantValue cv) {
            code.ldc(cv.value());  // inline as constant
        } else {
            // For non-constant providers: store the NumberProvider as a static field
            // on the generated class and call chance.getFloat(context).
            // The field name is unique per emission to allow multiple dynamic providers.
            String fieldName = "chance$" + ctx.nextUnique();
            // NOTE: the calling PredicateCompiler is expected to populate this static
            // field during class init via reflection. See PredicateCompiler#instantiate.
            code.getstatic(ClassDesc.of("PLACEHOLDER_CLASS_NAME"), fieldName, CD_NUMBER_PROVIDER);
            code.aload(1);
            code.invokevirtual(CD_NUMBER_PROVIDER, "getFloat",
                    MethodTypeDesc.of(ClassDesc.ofDescriptor("F"), CD_LOOT_CONTEXT));
        }

        // Compare: if nextFloat >= probability, jump to failure target (FCMPG + IFGE).
        // nextFloat < probability means "this roll passed".
        code.fcmpg();
        code.ifge(ctx.failureTarget());
    }
}
