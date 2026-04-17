package dev.epicpix.minecraftfunctioncompiler.compiler;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Stores the instruction list being built during high-level compilation,
 * with O(1) label-anchored insertions.

 * Backed by a doubly-linked list; label positions are tracked in a
 * {@link HashMap} keyed by {@link LabelData}. Previously backed by an
 * {@code ArrayList} with a linear {@code indexOfLabel} scan, which made
 * {@code addInstructionBefore}/{@code createLabelBefore} O(N) per call and
 * turned quadratic on functions with many selectors / scoreHolder contexts —
 * a single pathological {@code .mcfunction} could pin a CPU core for minutes.
 */
public class InstructionWriter {

    public static final LabelData NULL_LABEL = LabelData.ofId(-1);

    private static final class Node {
        Instruction instr;
        @Nullable Node prev, next;
        Node(Instruction instr) { this.instr = instr; }
    }

    private Node head;
    private Node tail;
    private int size = 0;
    private final HashMap<LabelData, Node> labelNodes = new HashMap<>();
    private int highestLabelId = 1;
    private final LabelData endLabel;

    public InstructionWriter() {
        endLabel = LabelData.ofId(highestLabelId++);
        Node n = new Node(Instructions.label(endLabel));
        tail = n;
        head = n;
        size = 1;
        labelNodes.put(endLabel, n);
    }

    public LabelData getEndLabel() {
        return endLabel;
    }

    public List<Instruction> getInstructions() {
        var list = new ArrayList<Instruction>(size);
        for(Node n = head; n != null; n = n.next) list.add(n.instr);
        return list;
    }

    private void insertBefore(Node target, Instruction instr) {
        Node n = new Node(instr);
        n.next = target;
        n.prev = target.prev;
        if(target.prev != null) target.prev.next = n;
        else head = n;
        target.prev = n;
        size++;
        registerIfLabel(n, instr);
    }

    private void insertAfter(Node target, Instruction instr) {
        Node n = new Node(instr);
        n.prev = target;
        n.next = target.next;
        if(target.next != null) target.next.prev = n;
        else tail = n;
        target.next = n;
        size++;
        registerIfLabel(n, instr);
    }

    private void registerIfLabel(Node n, Instruction instr) {
        if(instr.type() == InstructionTypes.LABEL) {
            var label = instr.<LabelData>get(0);
            if(label != null) labelNodes.put(label, n);
        }
    }

    public void addInstructionBefore(Instruction instruction, LabelData before) {
        if(before == NULL_LABEL) return;
        Node target = labelNodes.get(before);
        if(target == null) throw new IllegalStateException("Label not registered: " + before);
        insertBefore(target, instruction);
    }

    public void addInstructionAfter(Instruction instruction, LabelData after) {
        if(after == NULL_LABEL) return;
        Node target = labelNodes.get(after);
        if(target == null) throw new IllegalStateException("Label not registered: " + after);
        insertAfter(target, instruction);
    }

    public LabelData createLabelBefore(LabelData before) {
        if(before == NULL_LABEL) return NULL_LABEL;
        LabelData label = createDeferredLabel();
        addInstructionBefore(Instructions.label(label), before);
        return label;
    }

    public LabelData createLabelAfter(LabelData after) {
        if(after == NULL_LABEL) return NULL_LABEL;
        LabelData label = createDeferredLabel();
        addInstructionAfter(Instructions.label(label), after);
        return label;
    }

    public LabelData createDeferredLabel() {
        return LabelData.ofId(highestLabelId++);
    }
}
