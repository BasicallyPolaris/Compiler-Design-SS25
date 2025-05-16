package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

public class PredicateGenerator {

    public LivenessPredicate def(int lineNumber, Register register) {
        return new LivenessPredicate(LivenessPredicateType.DEF, lineNumber, register, -1 );
    }

    public LivenessPredicate use(int lineNumber, Register register) {
        return new LivenessPredicate(LivenessPredicateType.USE, lineNumber, register, -1 );
    }

    public LivenessPredicate succ(int lineNumber, int succLineNumber) {
        return new LivenessPredicate(LivenessPredicateType.SUCC, lineNumber, new VirtualRegister(-1), succLineNumber );
    }

    public LivenessPredicate live(int lineNumber, Register register) {
        return new LivenessPredicate(LivenessPredicateType.LIVE, lineNumber, register, -1 );
    }
}
