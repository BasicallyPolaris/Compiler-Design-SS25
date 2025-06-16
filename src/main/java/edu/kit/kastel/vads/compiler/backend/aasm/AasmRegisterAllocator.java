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
        if (node instanceof JumpNode && !node.block().predecessors().isEmpty() && visited.add(node.block().predecessor(0))) {
            scan(node.block().predecessor(0), visited);
        }
        if (!(node instanceof Phi)) {
            for (Node predecessor : node.predecessors()) {
                if (visited.add(predecessor)) {
                    scan(predecessor, visited);
                }
            }

            if (needsRegister(node) && shouldAddRegister) {
                this.registers.put(node, new VirtualRegister(this.id++));
            }

            if (node instanceof JumpNode) {
                if (node.predecessors().isEmpty()) {
                    for (Node pred : node.block().predecessors()) {
                        scan(pred, visited);
                    }
                }
            }

            return;
        }

        boolean onlySideEffects = node
                .predecessors()
                .stream()
                .allMatch(
                        pred -> pred instanceof ProjNode
                                && ((ProjNode) pred).projectionInfo() == ProjNode.SimpleProjectionInfo.SIDE_EFFECT);

        if (onlySideEffects)
            return;

        VirtualRegister phiRegister = new VirtualRegister(this.id++);

        // Restructure the Phi node: move predecessors to block predecessors
        for (int i = 0; i < node.predecessors().size(); i++) {
            Node pred = node.predecessors().get(i);
            Node blockPred = node.block().predecessor(i);

            // Remove the connection from pred to phi
            // irGraph.removeSuccessor(pred, node);
            // Set the pred's block to the block predecessor's block
            pred.setBlock(blockPred.block());
            // Add pred as predecessor to block predecessor
            blockPred.addPredecessor(pred);

            scan(pred, visited, false);
            scan(blockPred, visited);

            if (needsRegister(pred) && shouldAddRegister) {
                this.registers.put(pred, phiRegister);
            }
            if (needsRegister(blockPred) && shouldAddRegister) {
                this.registers.put(blockPred, new VirtualRegister(this.id++));
            }
        }
        this.registers.put(node, phiRegister);
    }

    private static boolean needsRegister(Node node) {
        return !(node instanceof ProjNode || node instanceof StartNode || node instanceof Block
                || node instanceof ReturnNode || node instanceof JumpNode || node instanceof CondJumpNode);
    }
}
