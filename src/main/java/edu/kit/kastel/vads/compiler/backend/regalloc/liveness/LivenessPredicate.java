package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.Objects;

public class LivenessPredicate {

    public LivenessPredicateType type;
    public int lineNumber;
    public Register parameter;
    public int succLineNumber;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LivenessPredicate that = (LivenessPredicate) o;
        return lineNumber == that.lineNumber && succLineNumber == that.succLineNumber && type == that.type && Objects.equals(parameter, that.parameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lineNumber, parameter, succLineNumber);
    }

    //succLineNumber (and parameter) is -1 (or $-1) when not used by predicate
    public LivenessPredicate(LivenessPredicateType type, int lineNumber, Register parameter, int succLineNumber) {
        this.type = type;
        this.lineNumber = lineNumber;

        switch (type) {
            case LivenessPredicateType.LIVE, LivenessPredicateType.DEF, LivenessPredicateType.USE -> {
                this.parameter = parameter;
                // TODO: not elegant solution, maybe refactor
                this.succLineNumber = -1;
            }
            case LivenessPredicateType.SUCC -> {
                // TODO: not elegant solution, maybe refactor
                this.parameter = new VirtualRegister(-1);
                this.succLineNumber = succLineNumber;
            }
        }
    }
}
