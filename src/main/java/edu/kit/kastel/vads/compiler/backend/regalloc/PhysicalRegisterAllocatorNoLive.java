package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.LivenessLine;
import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.Operation;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.stream.Collectors;

public class PhysicalRegisterAllocatorNoLive {

    //1. Get the interference Graph from liveness information
    //2. Order nodes using maximum cardinality ordering
    //2.1: Identify Special Registers (DIV and RET) for precoloring in the ordering
    //3. Greedy-color the graph using the elimination ordering
    //4. Spill if more colors are used than available registers
    //5. (optional) Coalesce non-interfering move-related nodes greedily
    Map<Node, Register> registers;

    public PhysicalRegisterAllocatorNoLive(Map<Node, Register> registers) {
        this.registers = registers;
    }

    public Map<Register, PhysicalRegister> allocate() {
        Map<Register, PhysicalRegister> physicalRegisters = new HashMap<>();

        int currentRegister = 0;
        for (Map.Entry<Node, Register> entry : registers.entrySet()) {
            physicalRegisters.put(entry.getValue(), X86_64PhysicalRegisters.get(currentRegister++));
        }
        return physicalRegisters;
    }
}
