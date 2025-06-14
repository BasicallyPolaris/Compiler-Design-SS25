package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.*;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class GraphConstructor {

    private final Optimizer optimizer;
    private final IrGraph graph;
    private final Map<Name, Map<Block, Node>> currentDef = new HashMap<>();
    private final Map<Block, Map<Name, Phi>> incompletePhis = new HashMap<>();
    private final Map<Block, Node> currentSideEffect = new HashMap<>();
    private final Map<Block, Phi> incompleteSideEffectPhis = new HashMap<>();
    private final Set<Block> sealedBlocks = new HashSet<>();
    private final HashSet<String> blockNames = new HashSet<>();
    private Block currentBlock;

    public GraphConstructor(Optimizer optimizer, String name) {
        this.optimizer = optimizer;
        this.graph = new IrGraph(name);
        this.currentBlock = this.graph.startBlock();
        // the start block never gets any more predecessors
        sealBlock(this.currentBlock);
    }

    public Block newBlock(FunctionTree functionTree, String name) {
        return new Block(this.graph, newBlockName(functionTree, name));
    }

    private String newBlockName(FunctionTree functionTree, String name) {
        String blockName = functionTree.name().name().asString() + "_" + name;

        if (blockNames.contains(blockName)) {
            int i = 0;
            while (blockNames.contains(blockName + "_" + i)) {
                i++;
            };
            blockName = blockName + "_" + i;
        }
        blockNames.add(blockName);
        return blockName;
    }

    public void setCurrentBlock(Block block) {
        this.currentBlock = block;
    }

    public Node newCondJump(Node condition, Block trueBlock, Block falseBlock) {
        return new CondJumpNode(currentBlock(), condition, trueBlock, falseBlock);
    }

    public Node newJump(Block target) {
        return new JumpNode(currentBlock(), target);
    }

    public Node newStart() {
        assert currentBlock() == this.graph.startBlock() : "start must be in start block";
        return new StartNode(currentBlock());
    }

    public Node newAdd(Node left, Node right) {
        return this.optimizer.transform(new AddNode(currentBlock(), left, right));
    }

    public Node newSub(Node left, Node right) {
        return this.optimizer.transform(new SubNode(currentBlock(), left, right));
    }

    public Node newMul(Node left, Node right) {
        return this.optimizer.transform(new MulNode(currentBlock(), left, right));
    }

    public Node newShiftLeft(Node left, Node right) {
        return this.optimizer.transform(new LShiftNode(currentBlock(), left, right));
    }

    public Node newShiftRight(Node left, Node right) {
        return this.optimizer.transform(new RShiftNode(currentBlock(), left, right));
    }

    public Node newLess(Node left, Node right) {
        return this.optimizer.transform(new LessNode(currentBlock(), left, right));
    }

    public Node newLessEq(Node left, Node right) {
        return this.optimizer.transform(new LeqNode(currentBlock(), left, right));
    }

    public Node newMore(Node left, Node right) {
        return this.optimizer.transform(new MoreNode(currentBlock(), left, right));
    }

    public Node newMoreEq(Node left, Node right) {
        return this.optimizer.transform(new MeqNode(currentBlock(), left, right));
    }

    public Node newEq(Node left, Node right) {
        return this.optimizer.transform(new EqualNode(currentBlock(), left, right));
    }

    public Node newNotEq(Node left, Node right) {
        return this.optimizer.transform(new NotEqualNode(currentBlock(), left, right));
    }

    public Node newBitAnd(Node left, Node right) {
        return this.optimizer.transform(new BitAndNode(currentBlock(), left, right));
    }

    public Node newBitXor(Node left, Node right) {
        return this.optimizer.transform(new ExclOrNode(currentBlock(), left, right));
    }

    public Node newConditional(Node left, Node middle, Node right) {
        return this.optimizer.transform(new CondExprNode(currentBlock(), left, middle, right));
    }

    public Node newBitOr(Node left, Node right) {
        return this.optimizer.transform(new BitOrNode(currentBlock(), left, right));
    }

    public Node newAnd(Node left, Node right) {
        return this.optimizer.transform(new LogicAndNode(currentBlock(), left, right));
    }

    public Node newOr(Node left, Node right) {
        return this.optimizer.transform(new LogicOrNode(currentBlock(), left, right));
    }

    public Node newDiv(Node left, Node right) {
        return this.optimizer.transform(new DivNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newMod(Node left, Node right) {
        return this.optimizer.transform(new ModNode(currentBlock(), left, right, readCurrentSideEffect()));
    }

    public Node newReturn(Node result) {
        return new ReturnNode(currentBlock(), readCurrentSideEffect(), result);
    }

    public Node newConstInt(int value) {
        // always move const into start block, this allows better deduplication
        // and resultingly in better value numbering
        return this.optimizer.transform(new ConstIntNode(this.graph.startBlock(), value));
    }

    public Node newUndef() {
        return this.optimizer.transform(new UndefinedNode(this.graph.startBlock()));
    }

    public Node newConstBool(boolean value) {
        return this.optimizer.transform(new ConstBoolNode(this.graph.startBlock(), value));
    }

    public Node newSideEffectProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.SIDE_EFFECT);
    }

    public Node newResultProj(Node node) {
        return new ProjNode(currentBlock(), node, ProjNode.SimpleProjectionInfo.RESULT);
    }

    public Block currentBlock() {
        return this.currentBlock;
    }

    public Phi newPhi(Block block) {
        // don't transform phi directly, it is not ready yet
        return new Phi(block);
    }

    public void cleanupTrivialPhis() {
        Set<Node> visited = new HashSet<>();
        cleanupTrivialPhisRecursive(this.graph().endBlock(), visited);
    }

    private void cleanupTrivialPhisRecursive(Node node, Set<Node> visited) {
        if (!visited.add(node)) {
            return;
        }

        // Process predecessors first (post-order traversal)
        for (Node pred : node.predecessors()) {
            cleanupTrivialPhisRecursive(pred, visited);
        }

        // Try to remove trivial phi if this is a phi node
        if (node instanceof Phi phi) {
            tryRemoveTrivialPhi(phi);
        }
    }

    public IrGraph graph() {
        return this.graph;
    }

    void writeVariable(Name variable, Block block, Node value) {
        this.currentDef.computeIfAbsent(variable, _ -> new HashMap<>()).put(block, value);
    }

    Node readVariable(Name variable, Block block) {
        Node node = this.currentDef.getOrDefault(variable, Map.of()).get(block);
        if (node != null) {
            return node;
        }
        return readVariableRecursive(variable, block);
    }

    private Node readVariableRecursive(Name variable, Block block) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = new Phi(block);
            this.incompletePhis.computeIfAbsent(block, _ -> new HashMap<>()).put(variable, (Phi) val);
        } else if (block.predecessors().size() == 1) {
            val = readVariable(variable, block.predecessors().getFirst().block());
        } else {
            val = new Phi(block);
            writeVariable(variable, block, val);
            val = addPhiOperands(variable, (Phi) val);
        }
        writeVariable(variable, block, val);
        return val;
    }

    Node addPhiOperands(Name variable, Phi phi) {
        for (Node pred : phi.block().predecessors()) {
            phi.appendOperand(readVariable(variable, pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }

    Node tryRemoveTrivialPhi(Phi phi) {
        Node same = null;
        for (Node op : phi.predecessors()) {
            if (op == same || op == phi) {
                continue; // unique value or self-reference
            }
            if (same != null) {
                return phi; // the phi merges at least two values: not trivial
            }
            same = op;
        }

        if (same == null) {
            same = this.newUndef(); // phi is unreachable or in the start block
        }

        // Remember all users except the phi itself
        Set<Node> users = new HashSet<>(phi.graph().successors(phi));
        users.remove(phi);

        // Reroute all uses of phi to same and remove phi
        for (Node use : users) {
            for (int i = 0; i < use.predecessors().size(); i++) {
                if (use.predecessor(i) == phi) {
                    use.setPredecessor(i, same);
                }
            }
        }

        // Try to recursively remove all phi users, which might have become trivial
        for (Node use : users) {
            if (use instanceof Phi) {
                tryRemoveTrivialPhi((Phi) use);
            }
        }
        return same;
    }

    void sealBlock(Block block) {
        if (this.sealedBlocks.contains(block)) {
            return;
        }
        for (Map.Entry<Name, Phi> entry : this.incompletePhis.getOrDefault(block, Map.of()).entrySet()) {
            addPhiOperands(entry.getKey(), entry.getValue());
        }
        Phi sideEffectPhi = this.incompleteSideEffectPhis.get(block);
        if (sideEffectPhi != null) {
            addPhiOperands(sideEffectPhi);
        }
        this.sealedBlocks.add(block);
    }

    public void writeCurrentSideEffect(Node node) {
        writeSideEffect(currentBlock(), node);
    }

    private void writeSideEffect(Block block, Node node) {
        this.currentSideEffect.put(block, node);
    }

    public Node readCurrentSideEffect() {
        return readSideEffect(currentBlock());
    }

    private Node readSideEffect(Block block) {
        Node node = this.currentSideEffect.get(block);
        if (node != null) {
            return node;
        }
        return readSideEffectRecursive(block);
    }

    private Node readSideEffectRecursive(Block block) {
        Node val;
        if (!this.sealedBlocks.contains(block)) {
            val = new Phi(block);
            Phi old = this.incompleteSideEffectPhis.put(block, (Phi) val);
            assert old == null : "double readSideEffectRecursive for " + block;
        } else if (block.predecessors().size() == 1) {
            val = readSideEffect(block.predecessors().getFirst().block());
        } else {
            val = new Phi(block);
            writeSideEffect(block, val);
            val = addPhiOperands((Phi) val);
        }
        writeSideEffect(block, val);
        return val;
    }

    Node addPhiOperands(Phi phi) {
        for (Node pred : phi.block().predecessors()) {
            phi.appendOperand(readSideEffect(pred.block()));
        }
        return tryRemoveTrivialPhi(phi);
    }
}