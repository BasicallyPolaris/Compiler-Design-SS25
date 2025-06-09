package edu.kit.kastel.vads.compiler.ir.util;

import edu.kit.kastel.vads.compiler.ir.node.JumpNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;

public final class NodeSupport {
    private NodeSupport() {

    }

    public static Node predecessorSkipProj(Node node, int predIdx) {
        Node pred = node.predecessor(predIdx);
        if (pred instanceof ProjNode) {
            return pred.predecessor(ProjNode.IN);
        }
        return pred;
    }

    public static Node predecessorSkipJump (Node node, int predIdx) {
        Node pred = node.predecessor(predIdx);
        if (pred instanceof JumpNode) {
            assert !pred.block().predecessors().isEmpty();
            return pred.block().predecessors().getFirst();
        }
        return pred;
    }
}
