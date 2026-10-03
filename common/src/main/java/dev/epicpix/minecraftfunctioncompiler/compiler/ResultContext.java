package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.commands.params.CommandParams;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.Diagnostic;
import dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticException;
import dev.epicpix.minecraftfunctioncompiler.emitter.types.NumberComparisonType;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.epicpix.minecraftfunctioncompiler.diagnostics.DiagnosticIds.COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED;

/**
 * Result tracking modelled on vanilla's per-source {@code CommandResultCallback}.
 *
 * <p>A <em>chain</em> context ({@link #root}, {@link #withSink}) carries the callbacks installed by
 * {@code execute store …} and {@code return run …}; execute modifiers only pass it along. Each time a
 * terminal command executes (once per forked source), {@link #beginTerminal} opens a <em>terminal</em>
 * context: the command adds to its result, jumps to {@link #markAndGetErrorLabel} when it fails
 * (vanilla: throws), or to {@link #markAndGetNoResultLabel} when vanilla reports nothing
 * (a function that does not return). {@link #endTerminal} then emits the sinks: success(result),
 * failure, or nothing. A terminal that is never reached (no sources) reports nothing, like vanilla.
 */
public final class ResultContext {

    /** A per-execution result consumer (vanilla {@code CommandResultCallback}). */
    public interface Sink {
        boolean needsResult();

        /** Return sinks leave the function, so they always run after every other sink. */
        default boolean isReturn() {
            return false;
        }

        void onSuccess(CompilationContext context, LocationData<Integer> result);

        void onFailure(CompilationContext context);
    }

    private final CompilationContext context;
    private final List<Sink> sinks;
    private final boolean terminal;
    private final @Nullable LocationData<Integer> result;
    private final LabelData errorLabel;
    private final LabelData noResultLabel;
    private final PendingReturn pendingReturn;

    private boolean requiresErrorLabelJump;
    private boolean requiresNoResultJump;
    private boolean markedImplemented;

    private ResultContext(CompilationContext context, List<Sink> sinks, boolean terminal, @Nullable LocationData<Integer> result, LabelData errorLabel, LabelData noResultLabel, PendingReturn pendingReturn) {
        this.context = context;
        this.sinks = sinks;
        this.terminal = terminal;
        this.result = result;
        this.errorLabel = errorLabel;
        this.noResultLabel = noResultLabel;
        this.pendingReturn = pendingReturn;
    }

    /**
     * Chain with no callbacks (a top-level command). {@code commandStart} is a label at the start of the
     * command's code, where a deferred {@code return} flag is initialized (see {@link #emitReturn}).
     */
    public static ResultContext root(CompilationContext context, LabelData commandStart) {
        return new ResultContext(context, List.of(), false, null, InstructionWriter.NULL_LABEL, InstructionWriter.NULL_LABEL, new PendingReturn(commandStart));
    }

    /** Chain with {@code sink} appended (vanilla {@code withCallback(sink, CommandResultCallback::chain)}). */
    public ResultContext withSink(Sink sink) {
        var newSinks = new ArrayList<Sink>(sinks.size() + 1);
        newSinks.addAll(sinks);
        newSinks.add(sink);
        return new ResultContext(context, List.copyOf(newSinks), false, null, InstructionWriter.NULL_LABEL, InstructionWriter.NULL_LABEL, pendingReturn);
    }

    /** True inside {@code return run …} (vanilla {@code ChainModifiers.isReturn()}). */
    public boolean hasReturnSink() {
        for(var sink : sinks) if(sink.isReturn()) return true;
        return false;
    }

    public boolean tracking() {
        return !sinks.isEmpty();
    }

    /** Opens the result scope of one terminal command execution; must be closed with {@link #endTerminal}. */
    public ResultContext beginTerminal() {
        LocationData<Integer> resultLocal = null;
        for(var sink : sinks) {
            if(sink.needsResult()) {
                resultLocal = context.createLocal();
                context.writeInstruction(Instructions.dup(resultLocal, LocationData.ofInteger(0)));
                break;
            }
        }
        var writer = context.instructionWriter;
        return new ResultContext(context, sinks, true, resultLocal, writer.createDeferredLabel(), writer.createDeferredLabel(), pendingReturn);
    }

    /** Emits the sinks for this execution at the current position (after the terminal's code). */
    public void endTerminal() {
        if(!terminal) throw new IllegalStateException("endTerminal on a non-terminal result context");
        if(sinks.isEmpty()) {
            if(requiresErrorLabelJump) context.writeInstruction(Instructions.label(errorLabel));
            if(requiresNoResultJump) context.writeInstruction(Instructions.label(noResultLabel));
            return;
        }
        var endLabel = context.instructionWriter.createDeferredLabel();
        emitSuccess(result != null ? result : LocationData.ofInteger(0));
        boolean needsEnd = requiresErrorLabelJump || requiresNoResultJump;
        if(needsEnd) context.writeInstruction(Instructions.jump(endLabel));
        if(requiresErrorLabelJump) {
            context.writeInstruction(Instructions.label(errorLabel));
            emitFailure();
        }
        if(requiresNoResultJump) {
            context.writeInstruction(Instructions.label(noResultLabel));
            // vanilla FallthroughTask: a `return run` function call that did not return fails the frame
            // (after the first source; later sources' calls are discarded). Store callbacks see nothing.
            for(var sink : sinks) if(sink.isReturn()) sink.onFailure(context);
        }
        if(needsEnd) context.writeInstruction(Instructions.label(endLabel));
    }

    /**
     * {@code return <value>} ({@code value != null}) / {@code return fail} as the terminal of this chain.
     * Vanilla runs the return executor for every source (BuildContexts CustomCommandExecutor loop): each one
     * reports to the store callbacks, and {@code frame.discard()} only drops queued work. With store callbacks
     * the frame return is therefore deferred until the whole command ran ({@link #emitPendingReturn}).
     */
    public void emitReturn(@Nullable Integer value) {
        boolean deferred = false;
        for(var sink : sinks) {
            if(!sink.isReturn()) {
                deferred = true;
                if(value != null) sink.onSuccess(context, LocationData.ofInteger(value));
                else sink.onFailure(context);
            }
        }
        if(!deferred) {
            writeReturn(value);
            return;
        }
        var pending = pendingReturn;
        if(pending.flag == null) {
            pending.flag = context.createLocal();
            context.instructionWriter.addInstructionBefore(Instructions.dup(pending.flag, LocationData.ofInteger(0)), pending.commandStart);
        }else if(!java.util.Objects.equals(pending.value, value)) {
            throw new IllegalStateException("conflicting deferred returns in one command");
        }
        pending.value = value;
        context.writeInstruction(Instructions.dup(pending.flag, LocationData.ofInteger(1)));
    }

    /** Performs a return deferred by {@link #emitReturn} once every source of the command ran. */
    public void emitPendingReturn() {
        var pending = pendingReturn;
        if(pending.flag == null) return;
        var skip = context.instructionWriter.createDeferredLabel();
        context.writeInstruction(Instructions.ifNumberCompareJump(NumberComparisonType.EQUAL, pending.flag, LocationData.ofInteger(0), skip));
        writeReturn(pending.value);
        context.writeInstruction(Instructions.label(skip));
        pending.flag = null;
    }

    private void writeReturn(@Nullable Integer value) {
        context.writeInstruction(value != null ? Instructions.returnSuccess(LocationData.ofInteger(value)) : Instructions.returnFail());
    }

    /** Per-command state of a deferred {@code return}. */
    private static final class PendingReturn {
        final LabelData commandStart;
        @Nullable LocationData<Integer> flag;
        @Nullable Integer value;

        PendingReturn(LabelData commandStart) {
            this.commandStart = commandStart;
        }
    }

    /** Reports success with {@code value} to every sink (terminal-independent; used by {@code return <value>}). */
    public void emitSuccess(LocationData<Integer> value) {
        for(var sink : sinks) if(!sink.isReturn()) sink.onSuccess(context, value);
        for(var sink : sinks) if(sink.isReturn()) sink.onSuccess(context, value);
    }

    public void emitFailure() {
        for(var sink : sinks) if(!sink.isReturn()) sink.onFailure(context);
        for(var sink : sinks) if(sink.isReturn()) sink.onFailure(context);
    }

    public void markImplemented() {
        markedImplemented = true;
    }

    public void checkImplemented(CommandParams command) {
        if(!markedImplemented) throw new DiagnosticException(Diagnostic.compileError(COMMAND_COMPILE_RESULT_NOT_IMPLEMENTED, context.getSource(), "ResultContext success/result not implemented: " + command));
    }

    public LabelData markAndGetErrorLabel() {
        if(!terminal) throw new IllegalStateException("error label requested from an execute-chain result context");
        requiresErrorLabelJump = true;
        return errorLabel;
    }

    /** Label for "vanilla reports no result for this execution" (e.g. a called function did not return). */
    public LabelData markAndGetNoResultLabel() {
        if(!terminal) throw new IllegalStateException("no-result label requested from an execute-chain result context");
        requiresNoResultJump = true;
        return noResultLabel;
    }

    public void updateResultValue(LocationData<Integer> value) {
        if(result != null) context.writeInstruction(Instructions.valueAdd(result, result, value));
    }

    /** True when failures of this terminal are observable (a store/return consumes them). */
    public boolean generatingSuccess() {
        return terminal && !sinks.isEmpty();
    }

    public boolean generatingResult() {
        return result != null;
    }
}
