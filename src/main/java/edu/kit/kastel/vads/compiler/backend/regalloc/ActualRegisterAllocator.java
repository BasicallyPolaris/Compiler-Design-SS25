package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.VirtualRegister;
import org.jgrapht.Graphs;
import org.jgrapht.alg.interfaces.VertexColoringAlgorithm;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.stream.Collectors;

public class ActualRegisterAllocator {

    //1. Get the interference Graph from liveness information
    //2. Order nodes using maximum cardinality ordering
    //2.1: Identify Special Registers (DIV and RET) for precoloring in the ordering
    //3. Greedy-color the graph using the elimination ordering
    //4. Spill if more colors are used than available registers
    //5. (optional) Coalesce non-interfering move-related nodes greedily

    //frier.dich();

    public List<LivenessLine> livenessLines;
    private SimpleGraph<Register, DefaultEdge> interferenceGraph;
    private VertexColoringAlgorithm.Coloring<Register> coloring;

    public ActualRegisterAllocator(List<LivenessLine> livenessLines) {
        this.livenessLines = livenessLines;
        this.interferenceGraph = generateInterferenceGraph(livenessLines);
        maximumCardinalitySearch();
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

        nodeWeights.remove(maxWeightRegister.getKey());

        return maxWeightRegister.getKey();
    }

    private Queue<Register> maximumCardinalitySearch() {
        Queue<Register> simplicialEliminationOrdering = new LinkedList<>();
        SimpleGraph<Register, DefaultEdge> graph = generateInterferenceGraph(this.livenessLines);

        Map<Register, Integer> nodeWeights = interferenceGraph.vertexSet().stream().collect(Collectors.toMap(
                register -> register,
                register -> 0
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

}
