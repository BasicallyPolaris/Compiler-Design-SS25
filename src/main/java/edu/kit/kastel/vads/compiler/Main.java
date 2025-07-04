package edu.kit.kastel.vads.compiler;

import edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.SsaTranslation;
import edu.kit.kastel.vads.compiler.ir.optimize.LocalValueNumbering;
import edu.kit.kastel.vads.compiler.ir.util.GraphVizPrinter;
import edu.kit.kastel.vads.compiler.ir.util.YCompPrinter;
import edu.kit.kastel.vads.compiler.lexer.Lexer;
import edu.kit.kastel.vads.compiler.parser.ParseException;
import edu.kit.kastel.vads.compiler.parser.Parser;
import edu.kit.kastel.vads.compiler.parser.TokenSource;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static edu.kit.kastel.vads.compiler.parser.Printer.print;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            System.err.println("Invalid arguments: Expected one input file and one output file");
            System.exit(3);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        ProgramTree program = lexAndParse(input);
        try {
            new SemanticAnalysis(program).analyze();
        } catch (SemanticException e) {
            e.printStackTrace();
            System.exit(7);
            return;
        }

        // Print program tree
        System.out.println(print(program));

        List<IrGraph> graphs = new ArrayList<>();
        for (FunctionTree function : program.topLevelTrees()) {
            SsaTranslation translation = new SsaTranslation(function, new LocalValueNumbering());
            graphs.add(translation.translate());
        }

        if ("vcg".equals(System.getenv("DUMP_GRAPHS")) || "vcg".equals(System.getProperty("dumpGraphs"))) {
            Path tmp = output.toAbsolutePath().resolveSibling("graphs");
            Files.createDirectory(tmp);
            for (IrGraph graph : graphs) {
                dumpGraph(graph, tmp, "before-codegen");
            }
        }

        //TODO: Remove Graphviz for jump debugging
        String yCompOutputPath = "./test-code/run-output.vcg";
        try (FileWriter fileWriter = new FileWriter(yCompOutputPath);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {

            // Get the first graph from the list
            if (!graphs.isEmpty()) {
                // Print the output of YCompPrinter.print(graphs.getFirst()) to the file
                printWriter.println(YCompPrinter.print(graphs.getFirst()));
            } else {
                System.out.println("No graphs available to print.");
            }

        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
            e.printStackTrace();
        }

        String assemblyCode = new CodeGenerator().generateCode(graphs);

        // Second vgc print after transforming phi predecessors
        String yCompOutputPathAfter = "./test-code/run-output-after.vcg";
        try (FileWriter fileWriter = new FileWriter(yCompOutputPathAfter);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {

            // Get the first graph from the list
            if (!graphs.isEmpty()) {
                // Print the output of YCompPrinter.print(graphs.getFirst()) to the file
                printWriter.println(YCompPrinter.print(graphs.getFirst()));
            } else {
                System.out.println("No graphs available to print.");
            }

        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
            e.printStackTrace();
        }


        Path assemblyFile = Path.of(output + ".s");

        // Write assembly code to the .s file
        Files.writeString(assemblyFile, assemblyCode);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "gcc",
                assemblyFile.toString(),    // Input .s file
                "-o",
                output.toString()        // Output executable
        );
        Process process = processBuilder.start();
        process.waitFor();
    }

    private static ProgramTree lexAndParse(Path input) throws IOException {
        try {
            Lexer lexer = Lexer.forString(Files.readString(input));
            TokenSource tokenSource = new TokenSource(lexer);
            Parser parser = new Parser(tokenSource);
            return parser.parseProgram();
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(42);
            throw new AssertionError("unreachable");
        }
    }

    private static void dumpGraph(IrGraph graph, Path path, String key) throws IOException {
        Files.writeString(
                path.resolve(graph.name() + "-" + key + ".vcg"),
                YCompPrinter.print(graph)
        );
    }
}
