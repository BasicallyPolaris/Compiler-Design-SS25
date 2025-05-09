package edu.kit.kastel.vads.compiler.backend.regalloc;

public class ActualRegisterAllocator {

    //1. Get the interference Graph from liveness information
    //2. Order nodes using maximum cardinality ordering
    //3. Greedy-color the graph using the elimination ordering
    //4. Spill if more colors are used than available registers
    //5. (optional) Coalesce non-interfering move-related nodes greedily

    //frier.dich();

}
