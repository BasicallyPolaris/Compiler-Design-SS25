package edu.kit.kastel.vads.compiler.backend.regalloc;

import java.util.List;
import java.util.stream.Collectors;

public final class NoAssignmentLivenessLine extends LivenessLine {

    public NoAssignmentLivenessLine(int lineNumber, Operation operation, List<Register> parameters) {
        this.lineNumber = lineNumber;
        this.operation = operation;
        this.parameters = parameters;
        this.liveInVariables = List.of();
    }

    @Override
    public String toString() {
        String params = parameters.stream()
                .map(Register::toString)
                .collect(Collectors.joining(", "));
        return lineNumber + ": " + operation + "(" + params + ")";
    }
}
