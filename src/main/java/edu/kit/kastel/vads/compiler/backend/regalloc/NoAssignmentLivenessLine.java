package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class NoAssignmentLivenessLine extends LivenessLine {

    public NoAssignmentLivenessLine(int lineNumber, Operation operation, List<Register> parameters) {
        this.lineNumber = lineNumber;
        this.operation = operation;
        this.parameters = parameters;
        this.liveInVariables = Set.of();

        // TODO: not elegant solution, maybe refactor
        this.target =  new VirtualRegister(-1);
    }

    @Override
    public String toString() {
        String params = parameters.stream()
                .map(Register::toString)
                .collect(Collectors.joining(", "));
        return lineNumber + ": " + operation + "(" + params + ")";
    }
}
