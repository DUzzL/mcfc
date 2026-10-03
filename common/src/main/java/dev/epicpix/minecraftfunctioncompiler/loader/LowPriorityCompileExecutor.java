package dev.epicpix.minecraftfunctioncompiler.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared compilation pool that runs below the server tick.
 *
 * Compilation previously ran on Executors.newVirtualThreadPerTaskExecutor().
 * Virtual-thread carrier threads are pinned at NORM_PRIORITY (5) — same as the
 * server tick — and the carrier pool is sized to availableProcessors(), so a
 * big datapack saturated every core at equal priority and the server starved.
 *
 * Per JEP 425 a virtual thread's priority is fixed and Thread.setPriority on
 * a VT is a no-op, so lowering compile priority requires platform threads we
 * own. Three layers of priority adjustment are applied here:
 *
 *   1. {@link Thread#setPriority(int)} to {@code NORM_PRIORITY - 2} = 3.
 *      Honored on Windows. On Linux it is a no-op under the default
 *      {@code -XX:ThreadPriorityPolicy=0}; with policy=1 the JVM mapping
 *      table only assigns nice +2 to Java priority 3 — too small to matter.
 *   2. Direct {@code setpriority(PRIO_PROCESS, 0, +10)} via the Foreign
 *      Function API on Linux. {@code PRIO_PROCESS} with {@code who=0}
 *      targets the calling thread (Linux uses tid==pid for scheduling),
 *      no {@code gettid()} indirection needed. Nice +10 ≈ 1/10 CPU share
 *      vs nice 0 under CFS contention — the server tick wins easily on
 *      any contended core while compile still progresses steadily.
 *   3. {@code sched_setscheduler(0, SCHED_BATCH, &param)} marks the thread
 *      as non-interactive batch work. Kernel skips wakeup-preemption boost
 *      for these threads, so they don't kick the server tick off the CPU
 *      when they wake. Trade-off vs SCHED_IDLE: compile keeps progressing
 *      under sustained foreground load instead of stalling indefinitely,
 *      at the cost of slightly more L3-cache contention.
 *      Falls back gracefully if {@code sched_setscheduler} is restricted.
 */
public final class LowPriorityCompileExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    public static final int COMPILE_THREAD_PRIORITY = Thread.NORM_PRIORITY - 2; // 3
    public static final int COMPILE_NICE = 10;
    /**
     * Stack of every compile thread. The command compiler recurses per command-tree node (an {@code execute}
     * chain of a few dozen clauses already overflows the default 1 MiB stack, which made the whole function
     * fall back to vanilla); vanilla parses and runs such chains fine. Only touched pages are committed, so
     * the reservation costs address space, not memory.
     */
    public static final long COMPILE_STACK_SIZE = 64L * 1024 * 1024;

    private static final int PRIO_PROCESS = 0;
    // Linux scheduling policy. SCHED_BATCH (3): kernel skips wakeup-preemption
    // for these threads but still gives them their CFS weight, so compile
    // progresses steadily. Combined with nice +10 the server tick (nice 0)
    // gets the lion's share of any contended core without compile stalling
    // indefinitely. SCHED_IDLE (5) is the nuclear option for client-FPS-first
    // setups but can starve compile arbitrarily long under sustained load.
    private static final int SCHED_POLICY = 3;

    private static final MethodHandle SETPRIORITY = resolveLibcInt3("setpriority");
    private static final MethodHandle SCHED_SETSCHEDULER = resolveSchedSetscheduler();
    private static final AtomicBoolean NICE_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean BATCH_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean APPLIED_LOGGED = new AtomicBoolean();

    private static final ThreadPoolExecutor POOL = createPool();

    private LowPriorityCompileExecutor() {}

    /** Resolves a libc symbol with signature {@code int name(int,int,int)} on Linux. */
    private static MethodHandle resolveLibcInt3(String name) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if(!os.contains("linux")) return null;
        try {
            Linker linker = Linker.nativeLinker();
            return linker.defaultLookup().find(name)
                .map(addr -> linker.downcallHandle(addr, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT)))
                .orElse(null);
        } catch(Throwable t) {
            LOGGER.warn("Foreign linker unavailable; compile threads will rely on Java priority alone. " +
                "Add --enable-native-access=ALL-UNNAMED to silence and unlock nice control.", t);
            return null;
        }
    }

    /** Resolves {@code int sched_setscheduler(pid_t, int, const struct sched_param*)}.
     * The {@code sched_param} struct on Linux is just {@code int sched_priority},
     * so we pass it as a {@code JAVA_INT*} to keep the FFI layout trivial. */
    private static MethodHandle resolveSchedSetscheduler() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if(!os.contains("linux")) return null;
        try {
            Linker linker = Linker.nativeLinker();
            return linker.defaultLookup().find("sched_setscheduler")
                .map(addr -> linker.downcallHandle(addr, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)))
                .orElse(null);
        } catch(Throwable t) {
            return null;
        }
    }

    /** Lower the *current* thread's CFS nice value AND switch it to SCHED_BATCH
     * on Linux. No-op elsewhere or if the foreign linker isn't available.
     * Failures are logged once each — running at default priority is degraded
     * but functional. */
    public static void applyCompileNiceToCurrentThread() {
        boolean appliedNice = false;
        if(SETPRIORITY != null) {
            try {
                int rc = (int) SETPRIORITY.invokeExact(PRIO_PROCESS, 0, COMPILE_NICE);
                if(rc == 0) {
                    appliedNice = true;
                } else if(NICE_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.warn("setpriority(nice={}) returned {} on '{}'", COMPILE_NICE, rc, Thread.currentThread().getName());
                }
            } catch(Throwable t) {
                if(NICE_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.warn("setpriority threw on '{}'", Thread.currentThread().getName(), t);
                }
            }
        }
        boolean appliedBatch = false;
        if(SCHED_SETSCHEDULER != null) {
            try(java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
                // struct sched_param { int sched_priority; }; SCHED_BATCH requires sched_priority == 0.
                java.lang.foreign.MemorySegment param = arena.allocate(ValueLayout.JAVA_INT);
                param.set(ValueLayout.JAVA_INT, 0L, 0);
                int rc = (int) SCHED_SETSCHEDULER.invokeExact(0, SCHED_POLICY, param);
                if(rc == 0) {
                    appliedBatch = true;
                } else if(BATCH_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.warn("sched_setscheduler(policy={}) returned {} on '{}' — falling back to nice-only", SCHED_POLICY, rc, Thread.currentThread().getName());
                }
            } catch(Throwable t) {
                if(BATCH_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.warn("sched_setscheduler threw on '{}'", Thread.currentThread().getName(), t);
                }
            }
        }
        if((appliedNice || appliedBatch) && APPLIED_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("Compile threads applied nice={} (success={}) sched_policy={} (success={}) on '{}'",
                COMPILE_NICE, appliedNice, SCHED_POLICY, appliedBatch, Thread.currentThread().getName());
        }
    }

    private static ThreadPoolExecutor createPool() {
        // Leave one core to the server thread; override with -Dmcfc.compileThreads=N.
        int parallelism = Math.max(1, Integer.getInteger("mcfc.compileThreads", Runtime.getRuntime().availableProcessors() - 1));
        AtomicInteger seq = new AtomicInteger();
        // Platform threads we create ourselves: ForkJoinWorkerThread can't be given a stack size.
        var pool = new ThreadPoolExecutor(parallelism, parallelism, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            var thread = new Thread(null, () -> {
                applyCompileNiceToCurrentThread();
                task.run();
            }, "mcfc-compile-" + seq.incrementAndGet(), COMPILE_STACK_SIZE);
            thread.setDaemon(true);
            thread.setPriority(COMPILE_THREAD_PRIORITY);
            return thread;
        });
        // Idle workers exit between compiles.
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    public static int parallelism() {
        return POOL.getMaximumPoolSize();
    }

    /** Drop-in replacement for the {@code try-with-resources} virtual-thread
     * executor pattern. {@link Scope#close()} blocks until every task submitted
     * via {@link Scope#execute(Runnable)} on this scope has finished. Tasks are
     * dispatched to the shared low-priority pool; uncaught throwables are
     * logged so they don't silently disappear. */
    public static Scope newScope() {
        return new Scope();
    }

    public static final class Scope implements AutoCloseable {
        // Phaser caps parties at 65535, which the parse path blows through on
        // any datapack with 65K+ commands. AtomicLong + monitor has no such cap.
        // Assumed single-threaded use of execute()/close() by the scope owner.
        private final AtomicLong pending = new AtomicLong();
        private final Object completionLock = new Object();

        public void execute(Runnable task) {
            pending.incrementAndGet();
            POOL.execute(() -> {
                try {
                    task.run();
                } catch(Throwable t) {
                    LOGGER.error("Uncaught exception in compile task", t);
                } finally {
                    if(pending.decrementAndGet() == 0L) {
                        synchronized(completionLock) {
                            completionLock.notifyAll();
                        }
                    }
                }
            });
        }

        @Override
        public void close() {
            synchronized(completionLock) {
                while(pending.get() > 0L) {
                    try {
                        completionLock.wait();
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted awaiting compile tasks", e);
                    }
                }
            }
        }
    }
}
