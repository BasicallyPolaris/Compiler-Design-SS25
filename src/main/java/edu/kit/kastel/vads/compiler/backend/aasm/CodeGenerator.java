package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.LivenessAnalyzer;
import edu.kit.kastel.vads.compiler.backend.regalloc.PhysicalRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.PhysicalRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        for (IrGraph graph : program) {
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);
            LivenessAnalyzer analyzer = new LivenessAnalyzer(graph, registers);
            analyzer.calculateLiveness();
            PhysicalRegisterAllocator pAllocator = new PhysicalRegisterAllocator(analyzer.livenessLines);
            Map<Register, PhysicalRegister> physicalRegisters = pAllocator.allocate();

            Map<Node, Register> physicalRegisterMap = new HashMap<>();
            registers.forEach((node, register) -> {
                PhysicalRegister physicalReg = physicalRegisters.get(register);
                physicalRegisterMap.put(node, physicalReg != null ? physicalReg : register);
            });
            registers = physicalRegisterMap;

            builder.append(".global main\n")
                    .append(".global _main\n")
                    .append(".text\n\n");
            builder.append("main:\n")
                    .append("call _main\n")
                    .append("movq %rax, %rdi\n").append("movq $0x3C, %rax\n")
                    .append("syscall\n\n")
                    .append("_main:\n");
            generateForGraph(graph, builder, registers);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers);
            }
        }

        switch (node) {
            case AddNode add -> binary(builder, registers, add);
            case SubNode sub -> binary(builder, registers, sub);
            case MulNode mul -> binary(builder, registers, mul);
            case DivNode div -> binary(builder, registers, div);
            case ModNode mod -> binary(builder, registers, mod);
            case ReturnNode r ->
                    builder.repeat(" ", 2).append("movl ").append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT))).append(", %eax")
                            .append("\n  ").append("ret");
            case ConstIntNode c -> builder.repeat(" ", 2)
                    .append("movl $")
                    .append(c.value())
                    .append(", ")
                    .append(registers.get(c));
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
        builder.append("\n");
    }

    private static void binary(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node
    ) {
        builder.repeat(" ", 2).append("movl ")
                .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                .append(", ")
                .append(registers.get(node))
                .append("\n");
        switch (node) {
            case AddNode _ -> builder.repeat(" ", 2).append("addl ")
                    .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                    .append(", ")
                    .append(registers.get(node));
            case SubNode _ -> builder.repeat(" ", 2).append("subl ")
                    .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                    .append(", ")
                    .append(registers.get(node));
            case MulNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                    .append(", ")
                    .append(registers.get(node));
            case DivNode _ -> {
                // First, clear EDX
                builder.repeat(" ", 2).append("xorl %edx, %edx\n");
                // Move the left operand to EAX
                builder.repeat(" ", 2).append("movl ")
                        .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                        .append(", %eax\n");
                builder.append("cltd");
                // Perform the division
                builder.repeat(" ", 2).append("idivl ")
                        .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                        .append("\n");
                // Move the result from EAX to the destination register
                builder.repeat(" ", 2).append("movl %eax, ")
                        .append(registers.get(node));
            }
            case ModNode _ -> {
                // First, clear EDX
                builder.repeat(" ", 2).append("xorl %edx, %edx\n");
                // Move the left operand to EAX
                builder.repeat(" ", 2).append("movl ")
                        .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
                        .append(", %eax\n");
                // Perform the division
                builder.repeat(" ", 2).append("idivl ")
                        .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)))
                        .append("\n");
                // Move the remainder from EDX to the destination register
                builder.repeat(" ", 2).append("movl %edx, ")
                        .append(registers.get(node));
            }
            default ->
                    throw new UnsupportedOperationException("Unsupported binary operation: " + node.getClass().getName());
        }
    }
}
