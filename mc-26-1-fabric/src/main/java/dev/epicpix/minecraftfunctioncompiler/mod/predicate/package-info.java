/**
 * Predicate compiler — compiles vanilla {@code ContextAwarePredicate} (the
 * AND-chain of {@code LootItemCondition}s attached to advancement criteria,
 * loot tables, and {@code /execute if predicate}) into specialised JVM
 * bytecode. The compiled predicate replaces the generic
 * {@code compositePredicates} {@code Predicate<LootContext>} field on
 * {@code ContextAwarePredicate}, eliminating per-tick virtual dispatch,
 * boxing, and codec/registry indirection at the criterion-trigger hot path.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Profiling a 50-player server shows {@code SimpleCriterionTrigger.trigger},
 * {@code ContextAwarePredicate.matches}, and
 * {@code LootItemEntityPropertyCondition.test} together accounting for
 * 10–13 ms of main-thread time per tick. The vanilla evaluation path goes:
 * <pre>
 *   trigger() →
 *     allListeners.iterator() →
 *       predicate.matches(ctx) →
 *         compositePredicates.test(ctx) →     // Util.allOf — Stream-based
 *           conditions[i].test(ctx) for each   // virtual dispatch per type
 *             nested predicate evaluation     // more virtual calls
 * </pre>
 * Most of this is dispatch overhead. A predicate whose static shape is known
 * at advancement load time can be compiled to flat bytecode with inlined
 * checks and early termination — typically 5–20× faster per evaluation.
 *
 * <h2>Architecture</h2>
 *
 * <p>The compiler walks the {@code List<LootItemCondition>} from a
 * {@link net.minecraft.advancements.criterion.ContextAwarePredicate}. For
 * each condition it dispatches to a {@link ConditionEmitter} keyed by the
 * condition's runtime class. The emitter writes JVM bytecode that performs
 * the same check as the condition's {@code test()} method, but inlined and
 * specialised over the condition instance's constant fields.
 *
 * <p>The resulting class implements
 * {@code Predicate<LootContext>} with a single {@code test(LootContext)}
 * method whose body is the AND chain of all condition emissions. Loaded via
 * a child class loader, instantiated once per advancement (capturing the
 * outer condition instance fields), and stored as
 * {@code ContextAwarePredicate.compositePredicates}.
 *
 * <h2>Hook</h2>
 *
 * <p>Mixin into the {@code ContextAwarePredicate(List<LootItemCondition>)}
 * constructor at TAIL replaces the {@code compositePredicates} field with
 * a compiled version when compilation succeeds; falls back to the vanilla
 * {@code Util.allOf(conditions)} otherwise. Per-condition emitter is opt-in
 * — unsupported types short-circuit to vanilla evaluation, so the compiler
 * progressively improves as more emitters are added.
 *
 * <h2>Status</h2>
 *
 * <p>STARTER: scaffolding + one example emitter ({@link emitters.RandomChanceEmitter}).
 * Production use requires emitters for the heavy types:
 * {@code LootItemEntityPropertyCondition}, {@code LocationCheck},
 * {@code AllOfCondition}, {@code AnyOfCondition}, {@code InvertedLootItemCondition},
 * {@code DamageSourceCondition}, {@code MatchTool}, {@code TimeCheck},
 * {@code WeatherCheck}, {@code ValueCheckCondition}. See
 * {@link emitters.RandomChanceEmitter} for the implementation pattern.
 */
package dev.epicpix.minecraftfunctioncompiler.mod.predicate;
