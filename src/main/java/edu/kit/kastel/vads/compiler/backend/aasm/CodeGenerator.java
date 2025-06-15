package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.*;
import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.LivenessAnalyzer;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {
    int labelCounter = 0;
    Stack<Node> visitedStack = new Stack<>();

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();
        for (IrGraph graph : program) {
            // System.out.println(YCompPrinter.print(graph));
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

            // Save previous Stackpointer, allocate 4 * spilled register Amount of bytes on
            // the stack
            if (spilledRegisters.get() > 0) {
                builder.append("  push %rbp\n")
                        .append("  mov %rsp, %rbp\n")
                        .append("  subq $").append((spilledRegisters.get() * 4)).append(", %rsp\n");
            }

            generateForGraph(graph, builder, physicalRegisterMap, spilledRegisterCount);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, PhysicalRegister> registers,
                                  int spilledRegisterCount) {
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), visited, builder, registers, spilledRegisterCount, graph);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, PhysicalRegister> registers,
                      int spilledRegisterCount, IrGraph graph) {
        // TODO: When is the right point of time to visit the blocks?
        if (node instanceof JumpNode && node.block().predecessors().size() == 1 && visited.add(node.block().predecessor(0))) {
            scan(node.block().predecessor(0), visited, builder, registers, spilledRegisterCount, graph);
        }
        if (!(node instanceof Phi || (node instanceof Block && node != graph.endBlock()))) {
            for (Node predecessor : node.predecessors()) {
                if (visited.add(predecessor)) {
                    visitedStack.add(predecessor);
                    scan(predecessor, visited, builder, registers, spilledRegisterCount, graph);
                }
            }
            if (visited.add(node.block())) {
                scan(node.block(), visited, builder, registers, spilledRegisterCount, graph);
            }
        }

        // We need labels for blocks, but not for start or end blocks
        // if (node instanceof Block & !(node == graph.endBlock() || node ==
        // graph.startBlock())) {
        // //Create label with blockname
        // builder.append(((Block) node).blockName() + ":");
        // builder.append("\n");
        // for (Node blockPredecessor : node.predecessors()) {
        // if (visited.add(blockPredecessor)) {
        // scan(blockPredecessor, visited, builder, registers, spilledRegisterCount,
        // graph);
        // }
        // }
        // }

        switch (node) {
            case AddNode add -> binary(builder, registers, add);
            case SubNode sub -> binary(builder, registers, sub);
            case MulNode mul -> binary(builder, registers, mul);
            case DivNode div -> binary(builder, registers, div);
            case ModNode mod -> binary(builder, registers, mod);
            case LessNode less -> comparison(builder, registers, less);
            case LeqNode leq -> comparison(builder, registers, leq);
            case MoreNode more -> comparison(builder, registers, more);
            case MeqNode meq -> comparison(builder, registers, meq);
            case EqualNode eq -> comparison(builder, registers, eq);
            case NotEqualNode neq -> comparison(builder, registers, neq);
            case LogicAndNode lAnd -> binary(builder, registers, lAnd);
            case LogicOrNode lOr -> binary(builder, registers, lOr);
            case BitAndNode and -> binary(builder, registers, and);
            case ExclOrNode exclOr -> binary(builder, registers, exclOr);
            case BitOrNode or -> binary(builder, registers, or);
            case LShiftNode lShift -> binary(builder, registers, lShift);
            case RShiftNode rShift -> binary(builder, registers, rShift);
            case ReturnNode r -> builder
                    .append("  movl ").append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)))
                    .append(", %eax\n")
                    .append("  addq $").append(spilledRegisterCount * 4).append(", %rsp\n")
                    .append("  pop %rbp\n")
                    .append("  ret");
            case ConstIntNode c -> builder.repeat(" ", 2)
                    .append("movl $")
                    .append(c.value())
                    .append(", ")
                    .append(registers.get(c));
            case ConstBoolNode b -> {
                String boolString;
                if (b.value()) {
                    boolString = "1";
                } else {
                    boolString = "0";
                }
                builder.repeat(" ", 2)
                        .append("movl $")
                        .append(boolString)
                        .append(", ")
                        .append(registers.get(b));
            }

            case Phi p -> {
                boolean onlySideEffects = p
                        .predecessors()
                        .stream()
                        .allMatch(
                                pred -> pred instanceof ProjNode && ((ProjNode) pred)
                                        .projectionInfo() == ProjNode.SimpleProjectionInfo.SIDE_EFFECT);

                if (!onlySideEffects) {
                    for (int i = 0; i < p.block().predecessors().size(); i++) {
                        Node pred = p.predecessor(i);
                        Node blockPred = p.block().predecessor(i);

                        graph.removeSuccessor(pred, p);
                        pred.setBlock(blockPred.block());
                        blockPred.addPredecessor(pred);

                        scan(blockPred, visited, builder, registers, spilledRegisterCount, graph);
                    }
                }
            }
            case ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
            case CondExprNode _ ->
                    throw new UnsupportedOperationException("No CondExprNodes should be in CodeGen step");
            case Block block -> {
                if (!(node == graph.endBlock() || node == graph.startBlock())) {
                    // Create label with blockname
                    builder.append(block.blockName()).append(":");
                    builder.append("\n");
                }
            }
            case CondJumpNode condJumpNode -> {
                // TODO: end label to avoid infinite loops
                // TODO: compare needs a register, currently as null (why??)
                // Check condition
                PhysicalRegister condition = registers.get(condJumpNode.predecessors().getFirst());
                builder.repeat(" ", 2)
                        .append("cmpl $1, ")
                        .append(condition)
                        .append("\n");
                // Jump if true
                builder.repeat(" ", 2)
                        .append("je ")
                        .append(condJumpNode.trueTarget().blockName())
                        .append("\n");
                // Jump if false
                builder.repeat(" ", 2)
                        .append("jmp ")
                        .append(condJumpNode.falseTarget().blockName())
                        .append("\n");
            }
            case JumpNode jumpNode -> {
                builder.repeat(" ", 2)
                        .append("jmp ")
                        .append(jumpNode.target().blockName())
                        .append("\n");
            }
            case UndefinedNode undefinedNode -> {
            }
        }
        builder.append("\n");
    }

    private void comparison(StringBuilder builder,
                            Map<Node, PhysicalRegister> registers,
                            BinaryOperationNode node) {
        PhysicalRegister target = registers.get(node);
        PhysicalRegister firstParameter = registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT));
        PhysicalRegister secondParameter = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
        PhysicalRegister spillRegSource = new PhysicalRegister(X86_64Register.R14);
        PhysicalRegister spillRegDest = new PhysicalRegister(X86_64Register.R15);
        boolean spillSource = false;
        boolean spillTarget = target.register == X86_64Register.SPILL;

        if (secondParameter.register == X86_64Register.SPILL) {
            spillSource = true;
        }

        // Move spilled Target in R14 and use R14 as the new second parameter
        if (spillSource) {
            builder.repeat(" ", 2).append("movl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(spillRegSource)
                    .append("\n");
            secondParameter = spillRegSource;
        }

        // Move spilled Register in R15 (not neccessary for binops) and use R15 as
        // target for binops
        if (spillTarget) {
            // builder.repeat(" ", 2).append("movl ")
            // .append(target)
            // .append(", ")
            // .append(spillRegDest)
            // .append("\n");
            target = spillRegDest;
        }

        // Move first parameter into target register for binop
        // if (!firstParameter.equals(target)) {
        // builder.repeat(" ", 2).append("movl ")
        // .append(firstParameter)
        // .append(", ")
        // .append(target)
        // .append("\n");
        // }
        switch (node) {
            case LessNode _ -> {
                writeCompareAssembly("jl ", builder, firstParameter, secondParameter, target);
            }
            case LeqNode _ -> {
                writeCompareAssembly("jle ", builder, firstParameter, secondParameter, target);
            }
            case MoreNode _ -> {
                writeCompareAssembly("jg ", builder, firstParameter, secondParameter, target);
            }
            case MeqNode _ -> {
                writeCompareAssembly("jge ", builder, firstParameter, secondParameter, target);
            }
            case EqualNode _ -> {
                writeCompareAssembly("je ", builder, firstParameter, secondParameter, target);
            }
            case NotEqualNode _ -> {
                writeCompareAssembly("jne ", builder, firstParameter, secondParameter, target);
            }
            default ->
                    throw new UnsupportedOperationException("Unsupported binary operation: " + node.getClass().getName());
        }

        if (spillTarget) {
            PhysicalRegister targetOnStack = registers.get(node);
            if (!spillRegDest.equals(targetOnStack)) {
                builder.append("\n").repeat(" ", 2).append("movl ")
                        .append(spillRegDest)
                        .append(", ")
                        .append(targetOnStack);
            }
        }
    }

    private static void binary(
            StringBuilder builder,
            Map<Node, PhysicalRegister> registers,
            BinaryOperationNode node) {
        PhysicalRegister target = registers.get(node);
        PhysicalRegister firstParameter = registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT));
        PhysicalRegister secondParameter = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
        PhysicalRegister spillRegSource = new PhysicalRegister(X86_64Register.R14);
        PhysicalRegister spillRegDest = new PhysicalRegister(X86_64Register.R15);
        boolean spillSource = false;
        boolean spillTarget = target.register == X86_64Register.SPILL;

        if (secondParameter.register == X86_64Register.SPILL) {
            spillSource = true;
        }

        // Move spilled Target in R14 and use R14 as the new second parameter
        if (spillSource) {
            builder.repeat(" ", 2).append("movl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(spillRegSource)
                    .append("\n");
            secondParameter = spillRegSource;
        }

        // Move spilled Register in R15 (not neccessary for binops) and use R15 as
        // target for binops
        if (spillTarget) {
            // builder.repeat(" ", 2).append("movl ")
            // .append(target)
            // .append(", ")
            // .append(spillRegDest)
            // .append("\n");
            target = spillRegDest;
        }

        // Move first parameter into target register for binop
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
            // Logical And is like a multiplication of bools (which are 0(false) or >0
            // (true)
            case LogicAndNode _ -> builder.repeat(" ", 2).append("imull ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            // Logical And is like an addition of bools (which are 0(false) or >0 (true)
            case LogicOrNode _ -> builder.repeat(" ", 2).append("addl ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case BitAndNode _ -> builder.repeat(" ", 2).append("and ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case ExclOrNode _ -> builder.repeat(" ", 2).append("xor ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            case BitOrNode _ -> builder.repeat(" ", 2).append("or ")
                    .append(secondParameter)
                    .append(", ")
                    .append(target);
            // TODO: Tricky shift details? See Lab 2 Notes and Hints
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
        // Write back from R15 to the Stack
        if (spillTarget) {
            PhysicalRegister targetOnStack = registers.get(node);
            if (!spillRegDest.equals(targetOnStack)) {
                builder.append("\n").repeat(" ", 2).append("movl ")
                        .append(spillRegDest)
                        .append(", ")
                        .append(targetOnStack);
            }
        }
        // Write back from R14 to Stack (Parameter is not changed so doesn't need to be
        // written back)
        // if (spillSource) {
        // PhysicalRegister secondParameterOnStack =
        // registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
        // builder.append("\n").repeat(" ", 2).append("movl ")
        // .append(spillRegSource)
        // .append(", ")
        // .append(secondParameterOnStack);
        // }
    }

    // private static void generateMoveCode(StringBuilder builder, PhysicalRegister
    // reg1, PhysicalRegister reg2) {
    // if (!reg1.equals(reg2)) {
    // builder.append("\n").repeat(" ", 2).append("movl ")
    // .append(reg1)
    // .append(", ")
    // .append(reg2);
    // }
    // }

    private void writeCompareAssembly(String comparison, StringBuilder builder, PhysicalRegister firstParameter,
                                      PhysicalRegister secondParameter, PhysicalRegister target) {
        String trueLabel = "label_" + labelCounter++;
        String falseLabel = "label_" + labelCounter++;
        builder.repeat(" ", 2).append("cmp ")
                .append(secondParameter)
                .append(", ")
                .append(firstParameter)
                .append("\n")
                .repeat(" ", 2)
                .append(comparison)
                .append(trueLabel)
                .append("\n")
                .repeat(" ", 2).append("movl ")
                .append("$0")
                .append(", ")
                .append(target)
                .append("\n")
                .repeat(" ", 2).append("jmp ")
                .append(falseLabel)
                .append("\n");
        builder.append(trueLabel).append(":").append("\n");
        builder.repeat(" ", 2).append("movl ")
                .append("$1")
                .append(", ")
                .append(target)
                .append("\n");
        builder.append(falseLabel).append(":");
    }
}
