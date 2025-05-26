package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class JumpLivenessLine extends LivenessLine {

    public JumpLivenessLine(int lineNumber, Operation operation, List<Register> parameters, int jumpTarget) {
        this.lineNumber = lineNumber;
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
        return lineNumber + ": " + operation + "(" + params + ")" + "Jump Target: " + jumpTarget +  ", Liveness Variables: " + liveInVariables;
    }
}
