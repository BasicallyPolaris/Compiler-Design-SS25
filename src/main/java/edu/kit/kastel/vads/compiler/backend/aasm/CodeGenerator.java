package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.X86_64Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.LivenessAnalyzer;
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

            Map<Node, PhysicalRegister> physicalRegisterMap = new HashMap<>();
            registers.forEach((node, register) -> {
                PhysicalRegister physicalReg = physicalRegisters.get(register);
                physicalRegisterMap.put(node, physicalReg);
            });

            builder.append(".global main\n")
                    .append(".global _main\n")
                    .append(".text\n\n");
            builder.append("main:\n")
                    .append("call _main\n")
                    .append("movq %rax, %rdi\n").append("movq $0x3C, %rax\n")
                    .append("syscall\n\n")
                    .append("_main:\n");
            generateForGraph(graph, builder, physicalRegisterMap);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, PhysicalRegister> registers) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, PhysicalRegister> registers) {
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
            Map<Node, PhysicalRegister> registers,
            BinaryOperationNode node
    ) {
        PhysicalRegister target = registers.get(node);
        PhysicalRegister firstParameter = registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT));
        PhysicalRegister secondParameter = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
        PhysicalRegister spillRegSource = new PhysicalRegister(X86_64Register.R14);
        PhysicalRegister spillRegDest = new PhysicalRegister(X86_64Register.R15);
        Boolean spillSource = false;
        Boolean spillTarget = false;

        if ( target.register == X86_64Register.SPILL ) {
            spillSource = true;
        }

        if ( secondParameter.register == X86_64Register.SPILL ) {
            spillTarget = true;
        }

        //Move spilled Target in R14 and use R14 as the new second parameter
        if (spillTarget) {
            builder.repeat(" ", 2).append("movl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(spillRegSource)
                    .append("\n");
            secondParameter = spillRegSource;
        }

        //Move spilled Register in R15 and use R15 as target for binops
        if (spillSource) {
            builder.repeat(" ", 2).append("movl ")
                    .append(target)
                    .append(", ")
                    .append(spillRegDest)
                    .append("\n");
            target = spillRegDest;
        }

        //Is first operand spilled?
        if (registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)).register == X86_64Register.SPILL) {

        }

        builder.repeat(" ", 2).append("movl ")
                .append(firstParameter)
                .append(", ")
                .append(target)
                .append("\n");
        switch (node) {
            case AddNode _ -> builder.repeat(" ", 2).append("addl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case SubNode _ -> builder.repeat(" ", 2).append("subl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case MulNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case DivNode _ -> {
                // First, clear EDX
                builder.repeat(" ", 2).append("xorl %edx, %edx\n");
                // Move the left operand to EAX
                builder.repeat(" ", 2).append("movl ")
                        .append(firstParameter)
                        .append(", %eax\n");
                builder.repeat(" ", 2).append("cltd\n");
                // Perform the division
                builder.repeat(" ", 2).append("idivl ")
                        .append(secondParameter)
                        .append("\n");
                // Move the result from EAX to the destination register
                builder.repeat(" ", 2).append("movl %eax, ")
                        .append(target);
            }
            case ModNode _ -> {
                // First, clear EDX
                builder.repeat(" ", 2).append("xorl %edx, %edx\n");
                // Move the left operand to EAX
                builder.repeat(" ", 2).append("movl ")
                        .append(firstParameter)
                        .append(", %eax\n");
                builder.repeat(" ", 2).append("cltd\n");
                // Perform the division
                builder.repeat(" ", 2).append("idivl ")
                        .append(secondParameter)
                        .append("\n");
                // Move the remainder from EDX to the destination register
                builder.repeat(" ", 2).append("movl %edx, ")
                        .append(target);
            }
            default ->
                    throw new UnsupportedOperationException("Unsupported binary operation: " + node.getClass().getName());
        }
        //Write back from R15 to the Stack
        if (spillSource) {
            PhysicalRegister targetOnStack = registers.get(node);
            builder.append("\n").repeat(" ", 2).append("movl ")
                    .append(spillRegDest)
                    .append(", ")
                    .append(targetOnStack);
        }
        //Write back from R14 to Stack
        if (spillTarget) {
            PhysicalRegister secondParameterOnStack = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
            builder.append("\n").repeat(" ", 2).append("movl ")
                    .append(spillRegSource)
                    .append(", ")
                    .append(secondParameterOnStack);
        }
    }
}
