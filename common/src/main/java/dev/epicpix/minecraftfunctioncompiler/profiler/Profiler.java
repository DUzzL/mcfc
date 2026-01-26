package dev.epicpix.minecraftfunctioncompiler.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.ThreadMXBean;
import dev.epicpix.minecraftfunctioncompiler.GlobalConfig;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.BiConsumer;

/// @deprecated This class will be removed, and Command Profiler will be used as a library instead
@Deprecated(forRemoval = true)
public final class Profiler {
    private Profiler() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler:profiler");

    private static final int TYPE_EXIT = 0;
    private static final int TYPE_ROOT = 1;
    private static final int TYPE_COMPILED = 2;
    private static final int TYPE_VANILLA = 3;
    private static final int TYPE_OVERHEAD = 4;

    private record Event(long timeNanos, long memoryBytes, int extra, String function) {}

    private static final ArrayList<Event> eventList = new ArrayList<>();
    private static boolean profilerEnabled;
    private static boolean resetQueued;

    public static void queueReset() {
        resetQueued = true;
    }

    public static void checkResetStatus() {
        if(resetQueued) {
            reset();
        }
    }

    public static boolean isProfilerEnabled() {
        return profilerEnabled;
    }

    public static boolean hasData() {
        return !mergedTree.children.isEmpty();
    }

    public static void setProfilerEnabled(boolean enable) {
        if(!profilerEnabled && enable) reset();
        profilerEnabled = enable;
    }

    public static void enterCompiled(String function) {
        if(!profilerEnabled) return;
        enter(function, TYPE_COMPILED);
    }

    public static void enterVanilla(String function) {
        if(!profilerEnabled) return;
        enter(function, TYPE_VANILLA);
    }

    public static void enterOverhead(String function) {
        if(!profilerEnabled) return;
        enter(function, TYPE_OVERHEAD);
    }

    public static void exit() {
        if(!profilerEnabled) return;
        eventList.add(new Event(System.nanoTime(), getMemoryBytes(), TYPE_EXIT, null));
    }

    private static void enter(String function, int extra) {
        eventList.add(new Event(System.nanoTime(), getMemoryBytes(), extra, function));
    }

    private static final ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    static {
        if(threadMxBean.isThreadAllocatedMemorySupported()) {
            threadMxBean.setThreadAllocatedMemoryEnabled(true);
        }else {
            LOGGER.warn("Thread allocated memory is not supported. Will not be able to collect memory usage.");
        }
    }

    private static long getMemoryBytes() {
        if(!GlobalConfig.profilerCollectMemoryUsage) {
            return 0;
        }
        if(threadMxBean.isThreadAllocatedMemorySupported()) {
            return threadMxBean.getCurrentThreadAllocatedBytes();
        }
        return -1;
    }

    private record CallTree(long totalDuration, long totalMemory, int extra, int execCount, String function, List<CallTree> children) {}
    private record CallTreeState(long nanosState, long bytesState, int extra, String function, ArrayList<CallTree> children) {}

    private static CallTree generateCallTree() {
        var stack = new Stack<CallTreeState>();

        var baseEntry = eventList.remove(eventList.size() - 1);
        stack.push(new CallTreeState(baseEntry.timeNanos, baseEntry.memoryBytes, baseEntry.extra, baseEntry.function, new ArrayList<>()));

        while(!eventList.isEmpty()) {
            var next = eventList.remove(eventList.size() - 1);
            if(next.function == null && next.extra == TYPE_EXIT) {
                var top = stack.pop();
                var tree = new CallTree(next.timeNanos - top.nanosState, next.memoryBytes - top.bytesState, top.extra, 1, top.function, top.children);
                if(stack.isEmpty()) return tree;
                stack.peek().children.add(tree);
            }else {
                stack.push(new CallTreeState(next.timeNanos, next.memoryBytes, next.extra, next.function, new ArrayList<>()));
            }
        }

        throw new IllegalArgumentException("Unfinished call tree");
    }

    private static void printCallTree(CallTree tree, BiConsumer<String, Boolean> writer) {
        var selfDuration = tree.totalDuration;
        var selfMemory = tree.totalMemory;
        for(var child : tree.children) {
            selfDuration -= child.totalDuration;
            selfMemory -= child.totalMemory;
        }
        if(tree.execCount != 0) selfDuration /= tree.execCount;
        if(tree.execCount != 0) selfMemory /= tree.execCount;
        if(tree.extra == TYPE_ROOT) {
            writer.accept("x" + tree.execCount + " root @ " + selfDuration + "ns self + " + selfMemory + " bytes self", true);
        }else if(tree.extra == TYPE_OVERHEAD) {
            writer.accept("x" + tree.execCount + " overhead " + tree.function + " @ " + selfDuration + "ns self + " + selfMemory + " bytes self", true);
        }else {
            writer.accept("x" + tree.execCount + " function " + tree.function + (tree.extra == TYPE_VANILLA ? " (vanilla)" : "") + " @ " + selfDuration + "ns self + " + selfMemory + " bytes self", true);
        }
        for(int i = 0; i < tree.children.size(); i++) {
            var child = tree.children.get(i);
            boolean hasNext = i + 1 < tree.children.size();
            printCallTree(child, (data, first) -> {
                if(!hasNext) {
                    writer.accept((first ? "┗━ " : "   ") + data, false);
                }else {
                    writer.accept((first ? "┣━ " : "┃  ") + data, false);
                }
            });
        }

    }

    private static CallTree previousTree = new CallTree(0, 0, TYPE_ROOT, 0, null, List.of());
    private static CallTree mergedTree = new CallTree(0, 0, TYPE_ROOT, 0, null, List.of());

    private static void mergeChildren(HashMap<IntObjectPair<String>, CallTree> squashedChildren, List<CallTree> children) {
        for(var child : children) {
            var pair = IntObjectPair.of(child.extra, child.function);
            if(!squashedChildren.containsKey(pair)) {
                squashedChildren.put(pair, mergeTrees(child, null));
            }else {
                squashedChildren.put(pair, mergeTrees(squashedChildren.get(pair), child));
            }
        }
    }

    private static CallTree mergeTrees(CallTree first, CallTree second) {
        if(first == null && second == null) return null;
        var squashedChildren = new HashMap<IntObjectPair<String>, CallTree>();
        if(first != null) mergeChildren(squashedChildren, first.children);
        if(second != null) mergeChildren(squashedChildren, second.children);
        var sortedChildren = new ArrayList<>(squashedChildren.values());
        sortedChildren.sort(Comparator.comparingLong(a -> -a.totalDuration / a.execCount));
        return new CallTree(
            (first != null ? first.totalDuration : 0) + (second != null ? second.totalDuration : 0),
            (first != null ? first.totalMemory : 0) + (second != null ? second.totalMemory : 0),
            first != null ? first.extra : second.extra,
            (first != null ? first.execCount : 0) + (second != null ? second.execCount : 0),
            first != null ? first.function : second.function,
            sortedChildren);
    }

    public static void collectEvents() {
        if(eventList.isEmpty()) return;
        Collections.reverse(eventList);
        var children = new ArrayList<CallTree>();
        var totalTime = 0L;
        var totalMemory = 0L;
        while(!eventList.isEmpty()) {
            var tree = generateCallTree();
            totalTime += tree.totalDuration;
            totalMemory += tree.totalMemory;
            children.add(tree);
        }
        previousTree = new CallTree(totalTime, totalMemory, TYPE_ROOT, 1, null, children);
        mergedTree = mergeTrees(mergedTree, previousTree);
    }

    private static JsonObject treeToJson(CallTree tree) {
        var obj = new JsonObject();
        if(tree.extra == TYPE_COMPILED) obj.addProperty("type", "compiled");
        else if(tree.extra == TYPE_VANILLA) obj.addProperty("type", "vanilla");
        else if(tree.extra == TYPE_OVERHEAD) obj.addProperty("type", "context_switch_overhead");
        else if(tree.extra == TYPE_ROOT) obj.addProperty("type", "root");
        if(tree.extra != TYPE_ROOT) obj.addProperty("function", tree.function);
        obj.addProperty("execution_count", tree.execCount);
        obj.addProperty("total_ns", tree.totalDuration);
        obj.addProperty("total_bytes", tree.totalMemory);
        var children = new JsonArray();
        for(var child : tree.children) {
            children.add(treeToJson(child));
        }
        obj.add("children", children);
        return obj;
    }

    public static JsonObject getJsonCallTree() {
        return treeToJson(mergedTree);
    }

    public static JsonObject getJsonTraceTree() {
        return treeToJson(previousTree);
    }

    public static void printTrace() {
        LOGGER.info("--- Trace Results ---");
        LOGGER.info("");
        printCallTree(previousTree, (data, ignored) -> LOGGER.info(" {}", data));
        LOGGER.info("");
        LOGGER.info("---------------------");
    }

    public static void printEvents() {
        LOGGER.info("--- Profiler Results ---");
        LOGGER.info("");
        printCallTree(mergedTree, (data, ignored) -> LOGGER.info(" {}", data));
        LOGGER.info("");
        LOGGER.info("------------------------");
    }

    public static void reset() {
        eventList.clear();
        previousTree = new CallTree(0, 0, TYPE_ROOT, 0, null, List.of());
        mergedTree = new CallTree(0, 0, TYPE_ROOT, 0, null, List.of());
        resetQueued = false;
    }

}
