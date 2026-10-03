package dev.epicpix.minecraftfunctioncompiler.mod.predicate.emitters;

import dev.epicpix.minecraftfunctioncompiler.mod.predicate.ConditionEmitter;
import dev.epicpix.minecraftfunctioncompiler.mod.predicate.EmissionContext;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.floats.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProvider;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * Reference {@link ConditionEmitter} for the simplest condition shape — a
 * single random-chance roll. Two emission paths:
 *
 * <ol>
 *   <li><b>Constant probability</b> ({@code chance} resolves to a
 *       {@code ConstantValue}): inlines the float as a {@code ldc} and emits
 *       {@code context.getRandom().nextFloat() < CONST} directly. ~4 bytecode
 *       instructions, no field loads, no virtual dispatch. Non-finite constants
 *       are folded to {@code 0.0F}, matching {@code ContextFloatProvider.getFloat}.</li>
 *   <li><b>Dynamic probability</b>: falls back to calling the original
 *       {@code chance.value().getFloat(context)} via a captured field. Still saves
 *       one layer of dispatch (the condition itself) but not the
 *       ContextFloatProvider lookup.</li>
 * </ol>
 *
 * <p>This pattern — peek at the condition's constant fields, inline what you
 * can, fall back to a captured reference otherwise — is the model the other
 * emitters should follow.
 */
public final class RandomChanceEmitter implements ConditionEmitter<LootItemRandomChanceCondition> {

    private static final ClassDesc CD_LOOT_CONTEXT = ClassDesc.of("net.minecraft.world.level.storage.loot.LootContext");
    private static final ClassDesc CD_RANDOM_SOURCE = ClassDesc.of("net.minecraft.util.RandomSource");
    private static final ClassDesc CD_HOLDER = ClassDesc.of("net.minecraft.core.Holder");
    private static final ClassDesc CD_CONTEXT_FLOAT_PROVIDER = ClassDesc.of("net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProvider");

    @Override
    public Class<LootItemRandomChanceCondition> conditionType() {
        return LootItemRandomChanceCondition.class;
    }

    @Override
    public void emit(LootItemRandomChanceCondition condition, CodeBuilder code, EmissionContext ctx) {
        Holder<ContextFloatProvider> chance = condition.chance();

        // Vanilla LootItemRandomChanceCondition.test evaluates the probability FIRST and only
        // then draws context.getRandom().nextFloat(); keep that order so providers that consume
        // the same RandomSource see the vanilla sequence.
        // Push: probability  (constant fast-path or dynamic fallback)
        if (chance.value() instanceof ConstantValue cv) {
            // Vanilla ContextFloatProvider.getFloat returns 0.0F for non-finite results.
            float value = cv.value();
            code.ldc(Float.isFinite(value) ? value : 0.0F);  // inline as constant
        } else {
            // For non-constant providers: store the Holder<ContextFloatProvider> as a static
            // field on the generated class and call chance.value().getFloat(context),
            // exactly as vanilla LootItemRandomChanceCondition.test does.
            // The field name is unique per emission to allow multiple dynamic providers.
            String fieldName = "chance$" + ctx.nextUnique();
            // NOTE: the calling PredicateCompiler is expected to populate this static
            // field during class init via reflection. See PredicateCompiler#instantiate.
            code.getstatic(ClassDesc.of("PLACEHOLDER_CLASS_NAME"), fieldName, CD_HOLDER);
            code.invokeinterface(CD_HOLDER, "value", MethodTypeDesc.of(ConstantDescs.CD_Object));
            code.checkcast(CD_CONTEXT_FLOAT_PROVIDER);
            code.aload(1);
            code.invokeinterface(CD_CONTEXT_FLOAT_PROVIDER, "getFloat",
                    MethodTypeDesc.of(ClassDesc.ofDescriptor("F"), CD_LOOT_CONTEXT));
        }
        // Push: context.getRandom().nextFloat()
        code.aload(1);  // LootContext
        code.invokeinterface(CD_LOOT_CONTEXT, "getRandom", MethodTypeDesc.of(CD_RANDOM_SOURCE));
        code.invokeinterface(CD_RANDOM_SOURCE, "nextFloat", MethodTypeDesc.of(ClassDesc.ofDescriptor("F")));

        // Stack: probability, roll. The roll passes iff roll < probability, i.e.
        // fcmpl(probability, roll) > 0; otherwise (including NaN via fcmpl = -1) fail.
        code.fcmpl();
        code.ifle(ctx.failureTarget());
    }
}
