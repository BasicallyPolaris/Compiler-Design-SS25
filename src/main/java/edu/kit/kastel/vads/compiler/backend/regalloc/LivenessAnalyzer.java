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
    List<LivenessLine> livenessLines;
    private int lineCount;

    public LivenessAnalyzer(IrGraph graph, Map<Node, Register> registers) {
        this.irGraph = graph;
        this.registers = registers;
        this.lineCount = 0;
        this.livenessLines = new ArrayList<>();
        fillLivenessInformation();
        // TODO: Remove debugging output
        livenessLines.forEach(livenessLine -> {
            System.out.println(livenessLine.toString());
        });
    }

    private void fillLivenessInformation() {
        Set<Node> visited = new HashSet<>();
        generateLivenessLines(irGraph.endBlock(), visited);
        fillLivenessLines();
    }

    private void fillLivenessLines() {
        List<LivenessLine> initialLivenessLines = List.copyOf(livenessLines);
        livenessLines.forEach(l -> {
            switch (l.operation) {
                case BINARY_OP -> {
                    // All used parameters are live in the line
                    l.liveInVariables.addAll(l.parameters);
                }
                case ASSIGN -> {

                }
            }
        })
    }

    private void generateLivenessLines(Node node, Set<Node> visited) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                generateLivenessLines(predecessor, visited);
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