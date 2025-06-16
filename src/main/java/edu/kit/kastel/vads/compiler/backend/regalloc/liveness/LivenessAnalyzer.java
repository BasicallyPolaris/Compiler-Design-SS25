package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.regalloc.*;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class LivenessAnalyzer {
    //1. Initialize LivenessLines Array with the information from the IR graph and AasmRegisterAllocator (temps)
    //2. Use Liveness Rules exhaustively on livenesslines array
    //3. Store liveness information in live-in field
    //4. Create Mapping with the temps to their live-in-temps for the interference graph
    private final IrGraph irGraph;
    private final Map<Node, Register> registers;
    public List<LivenessLine> livenessLines;
    public Map<Node, Integer> nodeLineNumbers;
    private int lineCount;
    private final Set<LivenessPredicate> livenessPredicates;
    private Map<Node, Block> blocks = new HashMap<>();
    Stack<Node> visitingOrder = new Stack<>();


    public LivenessAnalyzer(IrGraph graph, Map<Node, Register> registers) {
        this.irGraph = graph;
        this.registers = registers;
        this.lineCount = 0;
        this.nodeLineNumbers = new IdentityHashMap<>();
        this.livenessLines = new ArrayList<>();
        this.livenessPredicates = new HashSet<>();
    }

    public void calculateLiveness() {
        //Step 0: Accumulate liveness data
        fillLivenessInformation();
        //Step 1: Use J-Rules Exhaustively
        generatePredicates();
        //Step 2: Use K Rules exhaustively on the generated predicates to fill livenessline live-in information
        generateLivenessPredicates();
        //Step 3: Use Liveness Predicates to fill out Liveness information on the programm lines
        useLivenessPredicates();

        debugPrintLivenessLines();
    }

    private void generatePredicates() {
        int predicatesCount = 0;
        boolean stillChanging = true;
        PredicateGenerator predicateGenerator = new PredicateGenerator();

        while (stillChanging) {
            for (int k = 0; k < livenessLines.size(); k++) {
                LivenessLine currentLine = livenessLines.get(k);
                switch (currentLine.operation) {
                    //Rule J1
                    case Operation.BINARY_OP -> {
                        livenessPredicates.add(predicateGenerator.def(k, currentLine.target));
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getFirst()));
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getLast()));
                        livenessPredicates.add(predicateGenerator.succ(k, k + 1));
                    }
                    //Rule J2
                    case Operation.RETURN ->
                            livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getFirst()));

                    //Rule J3
                    case Operation.ASSIGN -> {
                        livenessPredicates.add(predicateGenerator.def(k, currentLine.target));
                        livenessPredicates.add(predicateGenerator.succ(k, k + 1));
                    }
                    case Operation.PHI_ASSIGN -> {
                        for (Register p : currentLine.parameters) {
                            livenessPredicates.add(predicateGenerator.use(k, p));
                            livenessPredicates.add(predicateGenerator.def(k, currentLine.target));
                            livenessPredicates.add(predicateGenerator.succ(k, k + 1));
                        }
                    }
                    //Rule J4
                    case Operation.GOTO -> {
                        Node target = currentLine.jumpTarget;
                        int lineNumber = nodeLineNumbers.get(target);
                        livenessPredicates.add(predicateGenerator.succ(k, lineNumber));
                    }
                    //Rule J5
                    case Operation.CONDITIONAL_GOTO -> {
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getFirst()));
                        livenessPredicates.add(predicateGenerator.succ(k, k + 1));
                        livenessPredicates.add(predicateGenerator.succ(k, nodeLineNumbers.get(currentLine.jumpTarget)));
                    }
                }
            }
            //If no predicates are added after iterating all lines, stop looking for predicates
            if (predicatesCount == livenessPredicates.size()) {
                stillChanging = false;
            }
            //Else we set the new predicatescount for the next run
            else {
                predicatesCount = livenessPredicates.size();
            }
        }
    }

    private void generateLivenessPredicates() {
        int predicatesCount = livenessPredicates.size();
        boolean stillChanging = true;
        PredicateGenerator predicateGenerator = new PredicateGenerator();

        while (stillChanging) {
            Set<LivenessPredicate> livenessPredicates2 = new HashSet<>();
            for (LivenessPredicate predicate : livenessPredicates) {

                switch (predicate.type) {
                    //Rule J1
                    case LivenessPredicateType.USE ->
                            livenessPredicates2.add(predicateGenerator.live(predicate.lineNumber, predicate.parameter));
                    case LivenessPredicateType.SUCC -> {
                        for (LivenessPredicate pred : livenessPredicates) {
                            if (pred.type == LivenessPredicateType.LIVE && pred.lineNumber == predicate.succLineNumber & !(livenessPredicates.contains(predicateGenerator.def(predicate.lineNumber, pred.parameter)))) {
                                livenessPredicates2.add(predicateGenerator.live(predicate.lineNumber, pred.parameter));
                            }
                        }
                    }
                    case LivenessPredicateType.DEF, LivenessPredicateType.LIVE -> {
                    }
                }
            }
            //Add new Predicates to predicates set
            livenessPredicates.addAll(livenessPredicates2);

            //If no predicates are added after iterating all lines, stop looking for predicates
            if (predicatesCount == livenessPredicates.size()) {
                stillChanging = false;
            }
            //Else we set the new predicatescount for the next run
            else {
                predicatesCount = livenessPredicates.size();
            }
        }
    }

    private void useLivenessPredicates() {
        for (LivenessPredicate predicate : livenessPredicates) {
            if (predicate.type == LivenessPredicateType.LIVE) {
                livenessLines.get(predicate.lineNumber).liveInVariables.add(predicate.parameter);
            }
        }
    }

    private void fillLivenessInformation() {
        Set<Node> visited = new HashSet<>();
        scan(irGraph.endBlock(), visited);
        System.out.println("Swag");
    }

    private void scan(Node node, Set<Node> visited) {
        //For debugging
        visitingOrder.push(node);
        // TODO: Is it right that size equals 1 ? What if > 1 ?
        if (node instanceof JumpNode && getNodeBlock(node).predecessors().size() == 1 && visited.add(getNodeBlock(node).predecessor(0))) {
            scan(getNodeBlock(node).predecessor(0), visited);
        }
        if (!(node instanceof Phi)) {
            for (Node predecessor : node.predecessors()) {
                if (!visited.contains(predecessor)) {
                    if (countAsVisited(node)) visited.add(predecessor);
                    scan(predecessor, visited);
                    // Even if it's a proj node, after the proj node is finished being visited all predecessors HAVE to be marked as visited
                    visited.add(predecessor);
                }
            }
            if (!visited.contains(getNodeBlock(node))) {
                if (countAsVisited(node)) visited.add(getNodeBlock(node));
                scan(getNodeBlock(node), visited);
                // Even if it's a proj node, after the proj node is finished being visited all predecessors HAVE to be marked as visited
                visited.add(getNodeBlock(node));
            }
        }
//
//        if (!(node instanceof Phi)) {
//            for (Node predecessor : node.predecessors()) {
//                if (visited.add(predecessor)) {
//                    scan(predecessor, visited);
//                }
//            }
//        }
//        if (node instanceof JumpNode) {
//            if (node.predecessors().isEmpty()) {
//                for (Node pred : node.block().predecessors()) {
//                    scan(pred, visited);
//                }
//            }
//        }

        switch (node) {
            case BinaryOperationNode b -> {
                List<Register> params = new ArrayList<>();
                params.add(registers.get(predecessorSkipProj(b, BinaryOperationNode.LEFT)));
                params.add(registers.get(predecessorSkipProj(b, BinaryOperationNode.RIGHT)));
                setNodeLineNumber(b);
                livenessLines.add(new AssignmentLivenessLine(b, Operation.BINARY_OP, registers.get(b), params));
            }
            case ReturnNode r -> {
                List<Register> params = new ArrayList<>();
                params.add(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)));
                int lineNumber = lineCount++;
                setNodeLineNumber(r);
                livenessLines.add(new NoAssignmentLivenessLine(Operation.RETURN, params));
            }
            case ConstIntNode c -> {
                setNodeLineNumber(c);
                livenessLines.add(new AssignmentLivenessLine(c, Operation.ASSIGN, registers.get(c), List.of()));
            }
            case ConstBoolNode b -> {
                setNodeLineNumber(b);
                livenessLines.add(new AssignmentLivenessLine(b, Operation.ASSIGN, registers.get(b), List.of()));
            }
            case JumpNode j -> {
                setNodeLineNumber(j);
                Collection<Node> successors = irGraph.successors(j);
                if (successors.isEmpty()) {
                    // Handle the case where there are no successors
                    // This might be a terminal jump or an error in graph construction
                    throw new IllegalStateException("JumpNode has no successors: " + j);
                }
                Node successor = successors.iterator().next();
                livenessLines.add(new JumpLivenessLine(j, Operation.GOTO, List.of(), successor));
            }
            case CondJumpNode cj -> {
                List<Register> params = new ArrayList<>();
                params.add(registers.get(cj.condition()));
                setNodeLineNumber(cj);
                livenessLines.add(new JumpLivenessLine(cj, Operation.CONDITIONAL_GOTO, params, irGraph.successors(cj).iterator().next()));
            }
            case Phi p -> {
//              assert p.block().predecessors().size() = p.predecessors();
                boolean onlySideEffects = p
                        .predecessors()
                        .stream()
                        .allMatch(
                                pred -> pred instanceof ProjNode &&
                                        ((ProjNode) pred).projectionInfo() == ProjNode.SimpleProjectionInfo.SIDE_EFFECT
                        );

                if (onlySideEffects) break;

                List<Register> params = new ArrayList<>();
                //TODO: Maybe traverse block predecessors only if the phi has no other predecessors to solve case with mutliple phis in single block??

                for (int i = 0; i < getNodeBlock(p).predecessors().size(); i++) {
                    Node pred = p.predecessors().get(i);
                    Node blockPred = getNodeBlock(p).predecessor(i);

                    blocks.put(pred, getNodeBlock(blockPred));

                    scan(blockPred, visited);
                    if (visited.add(pred)) {
                        scan(pred, visited);
                    }
                    params.add(registers.get(p.predecessors().get(i)));
                }

                setNodeLineNumber(p);
                livenessLines.add(new AssignmentLivenessLine(p, Operation.PHI_ASSIGN, registers.get(p), params));
            }
            case Block _, ProjNode _, StartNode _, UndefinedNode _ -> {
                // do nothing, skip line break
            }
            case CondExprNode _ ->
                    throw new UnsupportedOperationException("Cond Expression Nodes should not exist in liveness analysis step anymore");
        }
    }

    private void setNodeLineNumber(Node node) {
        int nodeLineNumber = lineCount++;
        nodeLineNumbers.put(node, nodeLineNumber);

        if (!nodeLineNumbers.containsKey(node.block())) {
            nodeLineNumbers.put(node.block(), nodeLineNumber);
            return;
        }

        int blockLineNumber = nodeLineNumbers.get(node.block());
        if (blockLineNumber > nodeLineNumber) {
            nodeLineNumbers.put(node.block(), nodeLineNumber);
        }
    }

    private void debugPrintLivenessLines() {
        System.out.println("\n Liveness lines:");
        for (LivenessLine line : livenessLines) {
            System.out.println(line);
        }
    }

    // Change Node Block
    private Block getNodeBlock(Node node) {
        if (blocks.containsKey(node)) return blocks.get(node);

        return node.block();
    }

    private static boolean countAsVisited(Node node) {
        return !(node instanceof ProjNode);
    }
}