package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class LivenessAnalyzer {
    //1. Initialize LivenessLines Array with the information from the IR graph and AasmRegisterAllocator (temps)
    //2. Use Liveness Rules exhaustively on livenesslines array
    //3. Store liveness information in live-in field
    //4. Create Mapping with the temps to their live-in-temps for the interference graph
    private final IrGraph irGraph;
    private Map<Node, Register> registers;
    public List<LivenessLine> livenessLines;
    private int lineCount;
    private Set<LivenessPredicate> livenessPredicates;

    public LivenessAnalyzer(IrGraph graph, Map<Node, Register> registers) {
        this.irGraph = graph;
        this.registers = registers;
        this.lineCount = 0;
        this.livenessLines = new ArrayList<>();
        fillLivenessInformation();
        this.livenessPredicates = new HashSet<>();
    }

    public void calculateLiveness() {
        //Step 1: Use J-Rules Exhaustively
        generatePredicates();
        //Step 2: Use K Rules exhaustively on the generated predicates to fill livenessline live-in information
        generateLivenessPredicates();
        //Step 3: Use Liveness Predicates to fill out Liveness information on the programm lines
        useLivenessPredicates();
        livenessLines.forEach(livenessLine -> {
            System.out.println(livenessLine.toString() + " - TARGET: " + livenessLine.target);
        });
        ActualRegisterAllocator ra = new ActualRegisterAllocator(livenessLines);
    }

    private void generatePredicates() {
        int predicatesCount = 0;
        boolean stillChanging = true;
        PredicateGenerator predicateGenerator = new PredicateGenerator();

        while (stillChanging) {
            for (int k = 0; k < livenessLines.size(); k++ ) {
                LivenessLine currentLine = livenessLines.get(k);
                switch (currentLine.operation) {
                    //Rule J1
                    case Operation.BINARY_OP -> {
                        livenessPredicates.add(predicateGenerator.def(k, currentLine.target));
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getFirst()));
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getLast()));
                        livenessPredicates.add(predicateGenerator.succ(k, k+1));
                    }
                    //Rule J2
                    case  Operation.RETURN -> {
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getFirst()));
                    }
                    //Rule J3
                    case Operation.ASSIGN -> {
                        livenessPredicates.add(predicateGenerator.def(k, currentLine.target));
                        livenessPredicates.add(predicateGenerator.succ(k, k+1));
                    }
                    //Rule J4
                    case Operation.GOTO -> {
                        //TODO: Liveness Line needs second line number for jump targets that are used here for the succ predicate
                    }
                    //Rule J5
                    case Operation.CONDITIONAL_GOTO -> {
                        livenessPredicates.add(predicateGenerator.use(k, currentLine.parameters.getFirst()));
                        livenessPredicates.add(predicateGenerator.succ(k, k+1));
                        //TODO: Same as above, succ predicate for the jump target
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
                    case LivenessPredicateType.USE -> {
                        livenessPredicates2.add(predicateGenerator.live(predicate.lineNumber, predicate.parameter));
                    }
                    case LivenessPredicateType.SUCC -> {
                        for (LivenessPredicate pred : livenessPredicates) {
                            if (pred.type == LivenessPredicateType.LIVE && pred.lineNumber == predicate.succLineNumber & !(livenessPredicates.contains(predicateGenerator.def(predicate.lineNumber, pred.parameter)))) {
                                livenessPredicates2.add(predicateGenerator.live(predicate.lineNumber, pred.parameter));
                            }
                        }
                    }
                    case LivenessPredicateType.DEF, LivenessPredicateType.LIVE -> {}
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
    }

    private void scan(Node node, Set<Node> visited) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited);
            }
        }

        switch (node) {
            case BinaryOperationNode b -> {
                List<Register> params = new ArrayList<>();
                params.add(registers.get(predecessorSkipProj(b, BinaryOperationNode.LEFT)));
                params.add(registers.get(predecessorSkipProj(b, BinaryOperationNode.RIGHT)));
                livenessLines.add(new AssignmentLivenessLine(lineCount++, Operation.BINARY_OP, registers.get(b), params));
            }
            case ReturnNode r -> {
                List<Register> params = new ArrayList<>();
                params.add(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)));
                livenessLines.add(new NoAssignmentLivenessLine(lineCount++, Operation.RETURN, params));
            }
            case ConstIntNode c -> {
                livenessLines.add(new AssignmentLivenessLine(lineCount++, Operation.ASSIGN, registers.get(c), List.of()));
            }
//          TODO: Implement Jump & CondJump Nodes :)
//            case JumpNode j -> {
//                livenessLines.add(new AssignmentLivenessLine(lineCount++, Operation.ASSIGN, registers.get(c), List.of()));
//            }
//            case CondJumpNode cj -> {
//                livenessLines.add(new AssignmentLivenessLine(lineCount++, Operation.ASSIGN, registers.get(c), List.of()));
//            }

            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
    }
}