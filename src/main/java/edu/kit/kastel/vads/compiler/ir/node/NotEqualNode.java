package edu.kit.kastel.vads.compiler.ir.node;

public final class NotEqualNode extends BinaryOperationNode {

    public NotEqualNode(Block block, Node left, Node right) {
        super(block, left, right);
    }

    @SuppressWarnings("EqualsDoesntCheckParameterClass") // we do, but not here
    @Override
    public boolean equals(Object obj) {
        return commutativeEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return commutativeHashCode(this);
    }
}
