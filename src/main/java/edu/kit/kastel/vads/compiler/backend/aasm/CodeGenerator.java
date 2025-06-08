package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.*;
import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.LivenessAnalyzer;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;
import edu.kit.kastel.vads.compiler.ir.util.YCompPrinter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        for (IrGraph graph : program) {
            System.out.println(YCompPrinter.print(graph));
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);
            LivenessAnalyzer analyzer = new LivenessAnalyzer(graph, registers);
            analyzer.calculateLiveness();
            PhysicalRegisterAllocator pAllocator = new PhysicalRegisterAllocator(analyzer.livenessLines);
            Map<Register, PhysicalRegister> physicalRegisters = pAllocator.allocate();

            Map<Node, PhysicalRegister> physicalRegisterMap = new HashMap<>();
            AtomicInteger spilledRegisters = new AtomicInteger();
            registers.forEach((node, register) -> {
                PhysicalRegister physicalReg = physicalRegisters.get(register);
                physicalRegisterMap.put(node, physicalReg);
                if (physicalReg.register == X86_64Register.SPILL) {
                    spilledRegisters.getAndIncrement();
                }
            });

            int spilledRegisterCount = spilledRegisters.get();

            builder.append(".global main\n")
                    .append(".global _main\n")
                    .append(".text\n\n");
            builder.append("main:\n")
                    .append("call _main\n")
                    .append("movq %rax, %rdi\n").append("movq $0x3C, %rax\n")
                    .append("syscall\n\n")
                    .append("_main:\n");

            // Save previous Stackpointer, allocate 4 * spilled register Amount of bytes on the stack
            if (spilledRegisters.get() > 0) {
                builder.append("  push %rbp\n")
                        .append("  mov %rsp, %rbp\n")
                        .append("  subq $").append((spilledRegisters.get() * 4)).append(", %rsp\n");
            }

            generateForGraph(graph, builder, physicalRegisterMap, spilledRegisterCount);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, PhysicalRegister> registers, int spilledRegisterCount) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers, spilledRegisterCount);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, PhysicalRegister> registers, int spilledRegisterCount) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers, spilledRegisterCount);
            }
        }

        switch (node) {
            case AddNode add -> binary(builder, registers, add);
            case SubNode sub -> binary(builder, registers, sub);
            case MulNode mul -> binary(builder, registers, mul);
            case DivNode div -> binary(builder, registers, div);
            case ModNode mod -> binary(builder, registers, mod);
            case LessNode less -> binary(builder, registers, less);
            case LeqNode leq -> binary(builder, registers, leq);
            case MoreNode more -> binary(builder, registers, more);
            case MeqNode meq -> binary(builder, registers, meq);
            case EqualNode eq -> binary(builder, registers, eq);
            case NotEqualNode neq -> binary(builder, registers, neq);
            case LogicAndNode lAnd -> binary(builder, registers, lAnd);
            case LogicOrNode lOr -> binary(builder, registers, lOr);
            case BitAndNode and -> binary(builder, registers, and);
            case ExclOrNode exclOr -> binary(builder, registers, exclOr);
            case BitOrNode or -> binary(builder, registers, or);
            case LShiftNode lShift -> binary(builder, registers, lShift);
            case RShiftNode rShift -> binary(builder, registers, rShift);
            case ReturnNode r -> builder
                    .append("  movl ").append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT))).append(", %eax\n")
                    .append("  addq $").append(spilledRegisterCount * 4).append(", %rsp\n")
                    .append("  pop %rbp\n")
                    .append("  ret");
            case ConstIntNode c -> builder.repeat(" ", 2)
                    .append("movl $")
                    .append(c.value())
                    .append(", ")
                    .append(registers.get(c));
            case ConstBoolNode b -> builder.repeat(" ", 2)
                    .append("movl $")
                    .append(b.value())
                    .append(", ")
                    .append(registers.get(b));

            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
            //TODO
            case CondExprNode condExprNode -> {
            }
            case IfElseNode ifElseNode -> {
            }
            case IfNode ifNode -> {
            }
            case WhileNode whileNode -> {
            }
            case CondJumpNode condJumpNode -> {
            }
            case JumpNode jumpNode -> {
            }
            case UndefinedNode undefinedNode -> {
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
        boolean spillSource = false;
        boolean spillTarget = false;

        if (target.register == X86_64Register.SPILL) {
            spillTarget = true;
        }

        if (secondParameter.register == X86_64Register.SPILL) {
            spillSource = true;
        }

        //Move spilled Target in R14 and use R14 as the new second parameter
        if (spillSource) {
            builder.repeat(" ", 2).append("movl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(spillRegSource)
                    .append("\n");
            secondParameter = spillRegSource;
        }

        //Move spilled Register in R15 (not neccessary for binops) and use R15 as target for binops
        if (spillTarget) {
//            builder.repeat(" ", 2).append("movl ")
//                    .append(target)
//                    .append(", ")
//                    .append(spillRegDest)
//                    .append("\n");
            target = spillRegDest;
        }

        //Move first parameter into target register for binop
        if (!firstParameter.equals(target)) {
            builder.repeat(" ", 2).append("movl ")
                    .append(firstParameter)
                    .append(", ")
                    .append(target)
                    .append("\n");
        }

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
            //TODO: How to handle the boolean results for the conditional jumps? Maybe use the info from the AST
            case LessNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case LeqNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case MoreNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case MeqNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case EqualNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case NotEqualNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case LogicAndNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case LogicOrNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case BitAndNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case ExclOrNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case BitOrNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            //TODO: Tricky shift details? See Lab 2 Notes and Hints
            case LShiftNode _ -> builder.repeat(" ", 2).append("sall ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case RShiftNode _ -> builder.repeat(" ", 2).append("sarl ")
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
        if (spillTarget) {
            PhysicalRegister targetOnStack = registers.get(node);
            if (!spillRegDest.equals(targetOnStack)) {
                builder.append("\n").repeat(" ", 2).append("movl ")
                        .append(spillRegDest)
                        .append(", ")
                        .append(targetOnStack);
            }
        }
        //Write back from R14 to Stack (Parameter is not changed so doesn't need to be written back)
//        if (spillSource) {
//            PhysicalRegister secondParameterOnStack = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
//            builder.append("\n").repeat(" ", 2).append("movl ")
//                    .append(spillRegSource)
//                    .append(", ")
//                    .append(secondParameterOnStack);
//        }
    }
    private static void generateMoveCode(StringBuilder builder, PhysicalRegister reg1, PhysicalRegister reg2)  {
        if (!reg1.equals(reg2)) {
            builder.append("\n").repeat(" ", 2).append("movl ")
                    .append(reg1)
                    .append(", ")
                    .append(reg2);
        }
    }
}
