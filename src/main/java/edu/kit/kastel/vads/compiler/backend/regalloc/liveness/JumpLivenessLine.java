package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class JumpLivenessLine extends LivenessLine {
    public JumpLivenessLine(Node root, Operation operation, List<Register> parameters, Node jumpTarget) {
        this.rootNode = root;
        this.operation = operation;
        this.parameters = parameters;
        this.liveInVariables = new HashSet<Register>();
        this.jumpTarget = jumpTarget;

        // TODO: not elegant solution, maybe refactor
        this.target =  new VirtualRegister(-1);
    }

    @Override
    public String toString() {
        String params = parameters.stream()
                .map(Register::toString)
                .collect(Collectors.joining(", "));
        return operation + "(" + params + ")" + "Jump Target: " + jumpTarget +  ", Liveness Variables: " + liveInVariables;
    }
}
