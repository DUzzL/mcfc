package dev.epicpix.minecraftfunctioncompiler.mod.predicate;

import dev.epicpix.minecraftfunctioncompiler.mod.predicate.emitters.RandomChanceEmitter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Entry point for compiling a list of {@link LootItemCondition}s (the contents
 * of a {@code ContextAwarePredicate}) into a single
 * {@code Predicate<LootContext>} implemented in tight inlined bytecode.
 *
 * <p>This class is the public surface; emitters do the per-condition work.
 * The two interesting methods are:
 * <ul>
 *   <li>{@link #compile(List)} — main entry. Returns a compiled predicate or
 *       {@code null} if any condition has no emitter (caller must fall back
 *       to vanilla evaluation).</li>
 *   <li>{@link #register(ConditionEmitter)} — register an emitter for a
 *       condition type. Called from a static initialiser; safe to add new
 *       emitters at runtime.</li>
 * </ul>
 */
public final class PredicateCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger("McfcPredicateCompiler");

    private static final Map<Class<? extends LootItemCondition>, ConditionEmitter<?>> EMITTERS = new ConcurrentHashMap<>();
    private static final ClassDesc CD_PREDICATE = ClassDesc.of("java.util.function.Predicate");
    private static final ClassDesc CD_OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc CD_LOOT_CONTEXT = ClassDesc.of("net.minecraft.world.level.storage.loot.LootContext");

    static {
        register(new RandomChanceEmitter());
        // TODO: more emitters here as they are implemented:
        // register(new EntityPropertiesEmitter());
        // register(new LocationCheckEmitter());
        // register(new InvertedConditionEmitter());
        // register(new AllOfConditionEmitter());
        // register(new AnyOfConditionEmitter());
        // ... etc
    }

    private PredicateCompiler() {}

    public static <T extends LootItemCondition> void register(ConditionEmitter<T> emitter) {
        EMITTERS.put(emitter.conditionType(), emitter);
    }

    /**
     * Compile the AND-chain of {@code conditions} into a {@code Predicate<LootContext>}.
     * Returns {@code null} if any condition has no registered emitter — the caller
     * must fall back to {@code Util.allOf(conditions)} so behaviour is preserved.
     *
     * <p>Trivial cases are short-circuited: empty list returns the
     * always-true predicate (and saves the codegen round-trip).
     */
    public static @Nullable Predicate<LootContext> compile(List<LootItemCondition> conditions) {
        if (conditions.isEmpty()) {
            return ctx -> true;
        }
        // Verify every condition has a registered emitter; bail otherwise.
        for (LootItemCondition c : conditions) {
            if (!EMITTERS.containsKey(c.getClass())) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("No emitter for {} — falling back to vanilla", c.getClass().getName());
                }
                return null;
            }
        }
        try {
            return buildAndLoad(conditions);
        } catch (Throwable t) {
            LOGGER.warn("Predicate compilation failed; falling back to vanilla", t);
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate<LootContext> buildAndLoad(List<LootItemCondition> conditions) throws Throwable {
        String className = "dev/epicpix/mcfc/CompiledPredicate$" + Math.abs(System.identityHashCode(conditions));
        ClassDesc thisCd = ClassDesc.ofInternalName(className);

        byte[] classBytes = ClassFile.of().build(thisCd, classBuilder -> {
            classBuilder.withSuperclass(CD_OBJECT);
            classBuilder.withInterfaceSymbols(CD_PREDICATE);

            // No-arg constructor
            classBuilder.withMethod("<init>", MethodTypeDesc.of(ClassDesc.ofDescriptor("V")), 0x0001 /*PUBLIC*/, mb ->
                    mb.withCode(code -> {
                        code.aload(0);
                        code.invokespecial(CD_OBJECT, "<init>",
                                MethodTypeDesc.of(ClassDesc.ofDescriptor("V")));
                        code.return_();
                    }));

            // test(Object) bridge — Predicate.test signature
            classBuilder.withMethod("test", MethodTypeDesc.of(ClassDesc.ofDescriptor("Z"), CD_OBJECT),
                    0x1041 /*PUBLIC + BRIDGE + SYNTHETIC*/, mb ->
                    mb.withCode(code -> {
                        code.aload(0);
                        code.aload(1);
                        code.checkcast(CD_LOOT_CONTEXT);
                        code.invokevirtual(thisCd, "test", MethodTypeDesc.of(
                                ClassDesc.ofDescriptor("Z"), CD_LOOT_CONTEXT));
                        code.ireturn();
                    }));

            // The real test(LootContext) — the AND chain
            classBuilder.withMethod("test", MethodTypeDesc.of(ClassDesc.ofDescriptor("Z"), CD_LOOT_CONTEXT),
                    0x0001, mb ->
                    mb.withCode(code -> {
                        Label fail = code.newLabel();
                        EmissionContext ctx = new EmissionContext(fail);
                        for (LootItemCondition condition : conditions) {
                            ConditionEmitter emitter = EMITTERS.get(condition.getClass());
                            emitter.emit(condition, code, ctx);
                        }
                        // All passed — return true.
                        code.iconst_1();
                        code.ireturn();
                        // Failure target — return false.
                        code.labelBinding(fail);
                        code.iconst_0();
                        code.ireturn();
                    }));
        });

        // Load via a child classloader so it can be GCed when the predicate is dropped.
        // TODO: integrate with FunctionCompilerClassLoader for unified lifecycle / dumping.
        Class<?> cls = MethodHandles.lookup().defineClass(classBytes);
        Constructor<?> ctor = cls.getDeclaredConstructor();
        return (Predicate<LootContext>) ctor.newInstance();
    }
}
