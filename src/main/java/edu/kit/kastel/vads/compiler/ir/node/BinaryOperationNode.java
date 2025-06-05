package edu.kit.kastel.vads.compiler.ir.node;

public sealed abstract class BinaryOperationNode extends Node permits AddNode, BitAndNode, BitOrNode, DivNode, EqualNode, ExclOrNode, LShiftNode, LeqNode, LessNode, LogicAndNode, LogicOrNode, MeqNode, ModNode, MoreNode, MulNode, NotEqualNode, RShiftNode, SubNode {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    protected BinaryOperationNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    protected BinaryOperationNode(Block block, Node left, Node right, Node sideEffect) {
        super(block, left, right, sideEffect);
    }

    protected static int commutativeHashCode(BinaryOperationNode node) {
        int h = node.block().hashCode();
        // commutative operation: we want h(op(x, y)) == h(op(y, x))
        h += 31 * (predecessorHash(node, LEFT) ^ predecessorHash(node, RIGHT));
        return h;
    }

    protected static boolean commutativeEquals(BinaryOperationNode a, Object bObj) {
        if (!(bObj instanceof BinaryOperationNode b)) {
            return false;
        }
        if (a.getClass() != b.getClass()) {
            return false;
        }
        if (a.predecessor(LEFT).hashCode() == b.predecessor(LEFT).hashCode() && a.predecessor(RIGHT).hashCode() == b.predecessor(RIGHT).hashCode()) {
            return true;
        }
        // commutative operation: op(x, y) == op(y, x)
        return a.predecessor(LEFT).hashCode() == b.predecessor(RIGHT).hashCode() && a.predecessor(RIGHT).hashCode() == b.predecessor(LEFT).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BinaryOperationNode binOp)) {
            return false;
        }
        return obj.getClass() == this.getClass()
            && this.predecessor(LEFT).hashCode() == binOp.predecessor(LEFT).hashCode()
            && this.predecessor(RIGHT).hashCode() == binOp.predecessor(RIGHT).hashCode();
    }

    @Override
    public int hashCode() {
        return (predecessorHash(this, LEFT) * 31 + predecessorHash(this, RIGHT)) ^ this.getClass().hashCode();
    }
}
