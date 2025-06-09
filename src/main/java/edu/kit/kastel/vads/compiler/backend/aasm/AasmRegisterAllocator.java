package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.RegisterAllocator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AasmRegisterAllocator implements RegisterAllocator {
    private int id;
    private final Map<Node, Register> registers = new HashMap<>();

    @Override
    public Map<Node, Register> allocateRegisters(IrGraph graph) {
        Set<Node> visited = new HashSet<>();
        visited.add(graph.endBlock());
        scan(graph.endBlock(), visited);
        return Map.copyOf(this.registers);
    }

    private void scan(Node node, Set<Node> visited) {
        scan(node, visited, true);
    }

    private void scan(Node node, Set<Node> visited, boolean shouldAddRegister) {
        if (!(node instanceof Phi)) {
            for (Node predecessor : node.predecessors()) {
                if (visited.add(predecessor)) {
                    scan(predecessor, visited);
                }
            }


            if (needsRegister(node) && shouldAddRegister) {
                this.registers.put(node, new VirtualRegister(this.id++));
            }

            return;
        }

        boolean onlySideEffects = node
                .predecessors()
                .stream()
                .allMatch(
                        pred -> pred instanceof ProjNode && ((ProjNode) pred).projectionInfo() == ProjNode.SimpleProjectionInfo.SIDE_EFFECT
                );

        if (onlySideEffects) return;

        VirtualRegister phiRegister = new VirtualRegister(this.id++);
        for (Node pred : node.predecessors()) {
            scan(pred, visited, false);
            if (needsRegister(pred)) {
                this.registers.put(pred, phiRegister);
            }
        }
        this.registers.put(node, phiRegister);
    }

    private static boolean needsRegister(Node node) {
        return !(node instanceof ProjNode || node instanceof StartNode || node instanceof Block || node instanceof ReturnNode || node instanceof JumpNode || node instanceof CondJumpNode);
    }
}
