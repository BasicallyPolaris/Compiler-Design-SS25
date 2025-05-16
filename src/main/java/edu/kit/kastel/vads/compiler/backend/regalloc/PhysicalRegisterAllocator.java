package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.LivenessLine;
import edu.kit.kastel.vads.compiler.backend.regalloc.liveness.Operation;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.stream.Collectors;

public class PhysicalRegisterAllocator {

    //1. Get the interference Graph from liveness information
    //2. Order nodes using maximum cardinality ordering
    //2.1: Identify Special Registers (DIV and RET) for precoloring in the ordering
    //3. Greedy-color the graph using the elimination ordering
    //4. Spill if more colors are used than available registers
    //5. (optional) Coalesce non-interfering move-related nodes greedily

    //frier.dich();

    private final List<LivenessLine> livenessLines;
    private final SimpleGraph<Register, DefaultEdge> interferenceGraph;
    private final Map<Register, Integer> coloring;

    public PhysicalRegisterAllocator(List<LivenessLine> livenessLines) {
        this.livenessLines = livenessLines;
        this.interferenceGraph = generateInterferenceGraph(livenessLines);
        this.coloring = generateGraphColoring();
    }

    public Map<Register, PhysicalRegister> allocate() {
        Map<Register, PhysicalRegister> physicalRegisters = new HashMap<>();

        interferenceGraph.vertexSet().forEach(register -> physicalRegisters.put(register, X86_64PhysicalRegisters.get(coloring.get(register))));

        return physicalRegisters;
    }

    private SimpleGraph<Register, DefaultEdge> generateInterferenceGraph(List<LivenessLine> livenessLines) {
        SimpleGraph<Register, DefaultEdge> interferenceGraph = new SimpleGraph<>(DefaultEdge.class);

        // add Registers as vertices
        for (LivenessLine livenessLine : livenessLines) {
            if (livenessLine.target.isValid()) {
                interferenceGraph.addVertex(livenessLine.target);
            }
        }

        // add RegisterLivenessInterference as edges
        for (LivenessLine livenessLine : livenessLines) {
            for (Register live1 : livenessLine.liveInVariables) {
                for (Register live2 : livenessLine.liveInVariables) {
                    if (!live1.equals(live2)) interferenceGraph.addEdge(live1, live2);
                }
            }

            if (livenessLine.operation == Operation.BINARY_OP) {
                interferenceGraph.addEdge(livenessLine.target, livenessLine.parameters.getLast());
            }
        }

        return interferenceGraph;
    }

    // TODO: Check if this implementation actually also removes the node from the Map
    private Register pollMaxWeightNode(Map<Register, Integer> nodeWeights) {
        Map.Entry<Register, Integer> maxWeightRegister = null;

        for (Map.Entry<Register, Integer> entry : nodeWeights.entrySet()) {
            if (maxWeightRegister == null) {
                maxWeightRegister = entry;
            } else if (entry.getValue() > maxWeightRegister.getValue()) {
                maxWeightRegister = entry;
            }
        }

        assert maxWeightRegister != null;
        nodeWeights.remove(maxWeightRegister.getKey());

        return maxWeightRegister.getKey();
    }

    private Queue<Register> maximumCardinalitySearch() {
        Queue<Register> simplicialEliminationOrdering = new LinkedList<>();
        SimpleGraph<Register, DefaultEdge> graph = generateInterferenceGraph(this.livenessLines);

        Map<Register, Integer> nodeWeights = graph.vertexSet().stream().collect(Collectors.toMap(
                register -> register,
                _ -> 0
        ));

        //TODO: Insert special registers into Queue , Increment neighboring weights by 1 for each
        while (!nodeWeights.isEmpty()) {
            Register maxWeightVertex = pollMaxWeightNode(nodeWeights);
            simplicialEliminationOrdering.add(maxWeightVertex);

            for (Register neighbor : Graphs.neighborSetOf(graph, maxWeightVertex)) {
                nodeWeights.put(neighbor, nodeWeights.get(neighbor) + 1);
            }

            graph.removeVertex(maxWeightVertex);
        }

        return simplicialEliminationOrdering;
    }

    private int getValidColorFromNeighborhood(Set<Register> neighbors, Map<Register, Integer> coloring) {
        Set<Integer> usedColors = new HashSet<>();
        int maxColor = coloring.size();

        for (Register neighbor : neighbors) {
            usedColors.add(coloring.get(neighbor));
        }

        for (int i = 0; i < maxColor; i++) {
            if (!usedColors.contains(i)) return i;
        }

        return maxColor;
    }

    private Map<Register, Integer> generateGraphColoring() {
        Map<Register, Integer> coloring = interferenceGraph.vertexSet().stream().collect(Collectors.toMap(
                register -> register,
                _ -> -1 // Assign invalid coloring at start
        ));

        Queue<Register> simplicialEliminationOrdering = maximumCardinalitySearch();

        while (!simplicialEliminationOrdering.isEmpty()) {
            Register maxWeightVertex = simplicialEliminationOrdering.poll();

            Set<Register> neighbors = Graphs.neighborSetOf(interferenceGraph, maxWeightVertex);
            coloring.put(maxWeightVertex, getValidColorFromNeighborhood(neighbors, coloring));
            System.out.println(maxWeightVertex + " -- COLOR --> " + coloring.get(maxWeightVertex));
        }

        return coloring;
    }
}
