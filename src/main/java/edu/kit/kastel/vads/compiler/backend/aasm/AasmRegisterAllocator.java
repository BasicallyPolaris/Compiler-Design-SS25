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
    private Node endBlock;

    @Override
    public Map<Node, Register> allocateRegisters(IrGraph graph) {
        Set<Node> visited = new HashSet<>();
        visited.add(graph.endBlock());
        this.endBlock = graph.endBlock();
        scan(graph.endBlock(), visited);
        return Map.copyOf(this.registers);
    }

    private void scan(Node node, Set<Node> visited) {
        Node block = node.block();
        // TODO: Is it right that size equals 1 ? What if > 1 ?
        if (node instanceof JumpNode && block.predecessors().size() == 1 && visited.add(node.block().predecessor(0))) {
            scan(node.block().predecessor(0), visited);
        }
        if (!(node instanceof Phi || (node instanceof Block && node != endBlock))) {
            for (Node predecessor : node.predecessors()) {
                if (!visited.contains(predecessor)) {
                    if (countAsVisited(node)) visited.add(predecessor);
                    scan(predecessor, visited);
                    // Even if it's a proj node, after the proj node is finished being visited all predecessors HAVE to be marked as visited
                    visited.add(predecessor);
                }
            }
            if (!visited.contains(node.block())) {
                if (countAsVisited(node)) visited.add(node.block());
                scan(node.block(), visited);
                // Even if it's a proj node, after the proj node is finished being visited all predecessors HAVE to be marked as visited
                visited.add(node.block());
            }
            if (needsRegister(node)) {
                this.registers.put(node, new VirtualRegister(this.id++));
            }
        }

        if (!(node instanceof Phi)) return;

//        if (!(node instanceof Phi)) {
//            for (Node predecessor : node.predecessors()) {
//                if (visited.add(predecessor)) {
//                    scan(predecessor, visited);
//                }
//            }
//
//            if (needsRegister(node) && shouldAddRegister) {
//                this.registers.put(node, new VirtualRegister(this.id++));
//            }
//
//            if (node instanceof JumpNode) {
//                if (node.predecessors().isEmpty()) {
//                    for (Node pred : node.block().predecessors()) {
//                        scan(pred, visited);
//                    }
//                }
//            }
//
//            return;
//        }

        boolean onlySideEffects = node
                .predecessors()
                .stream()
                .allMatch(
                        pred -> pred instanceof ProjNode && ((ProjNode) pred).projectionInfo() == ProjNode.SimpleProjectionInfo.SIDE_EFFECT
                );

        if (onlySideEffects) return;

        VirtualRegister phiRegister = new VirtualRegister(this.id++);

        // TODO: Is the order right?
        for (int i = 0; i < node.block().predecessors().size(); i++) {
            Node pred = node.predecessors().get(i);
            Node blockPred = node.block().predecessor(i);

            pred.setBlock(blockPred.block());
            blockPred.addPredecessor(pred);

            scan(blockPred, visited);

            if (needsRegister(pred)) {
                this.registers.put(pred, phiRegister);
            }
        }
        this.registers.put(node, phiRegister);
    }

    private static boolean needsRegister(Node node) {
        return !(node instanceof ProjNode || node instanceof StartNode || node instanceof Block || node instanceof ReturnNode || node instanceof JumpNode || node instanceof CondJumpNode);
    }

    private static boolean countAsVisited(Node node) {
        return !(node instanceof ProjNode);
    }
}
