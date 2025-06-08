package edu.kit.kastel.vads.compiler;

import edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.SsaTranslation;
import edu.kit.kastel.vads.compiler.ir.optimize.LocalValueNumbering;
import edu.kit.kastel.vads.compiler.ir.util.GraphVizPrinter;
import edu.kit.kastel.vads.compiler.lexer.Lexer;
import edu.kit.kastel.vads.compiler.parser.ParseException;
import edu.kit.kastel.vads.compiler.parser.Parser;
import edu.kit.kastel.vads.compiler.parser.TokenSource;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestMain {
    public static void main(String[] args) throws IOException {
        // if (args.length != 2) {
        // System.err.println("Invalid arguments: Expected one input file and one output
        // file");
        // System.exit(3);
        // }

        System.out.println(~0);

        String inputString = "int main() {\n" +
                "  int x = 5;\n" +
                "  if (x > 0) {\n" +
                "    x = x + 1;\n" +
                "  }\n" +
                "  return x;\n" +
                "}";

        // Path input = Path.of(args[0]);
        // Path output = Path.of(args[1]);
        ProgramTree program = lexAndParse(inputString);
        try {
            new SemanticAnalysis(program).analyze();
        } catch (SemanticException e) {
            e.printStackTrace();
            System.exit(7);
            return;
        }
        List<IrGraph> graphs = new ArrayList<>();
        for (FunctionTree function : program.topLevelTrees()) {
            SsaTranslation translation = new SsaTranslation(function, new LocalValueNumbering());
            graphs.add(translation.translate());
        }

        // System.out.println(GraphVizPrinter.print(graphs.getFirst()));

        // TODO: generate assembly and invoke gcc instead of generating abstract
        // assembly
        String s = new CodeGenerator().generateCode(graphs);

        System.out.println("\n----- Input: -----\n" + inputString + "\n\n---- Output: -----\n" + s);
        // Files.writeString(output, s);
    }

    private static ProgramTree lexAndParse(String inputString) throws IOException {
        try {
            Lexer lexer = Lexer.forString(inputString);
            TokenSource tokenSource = new TokenSource(lexer);
            Parser parser = new Parser(tokenSource);
            return parser.parseProgram();
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(42);
            throw new AssertionError("unreachable");
        }
    }
}