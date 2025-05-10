# Starter Code: Java

This project contains starter code written in Java 24.
It contains:

- A lexer for L1
- A parser for L1
- Semantic analysis for L1
- SSA translation and IR
- Code generation for an abstract assembly

Furthermore, the starter code also provides working `build.sh` and `run.sh` files.

## Code Overview

The starter code is meant to spare you some initial work on things that are not covered
by the lecture at the time of the first lab.
You will most likely need to touch large parts of the existing code sooner or later,
so we recommend going through it for a basic understanding of what is going on.

Remember that you are free to modify any code.

### Lexer & Tokens

The lexer lazily produces tokens from an input string.
Invalid input parts will generate `ErrorToken`s.

### Parser & AST

The parser is a handwritten, recursive-descent parser.
You can choose other technologies (e.g., ANTLR), but expanding this parser as needed
might be a good exercise to deepen your understanding.

The parser does not implement any kind of error recovery.
Instead, it just throws an exception as soon as the first problem is encountered.
You can implement error recovery, but it is not mandatory.

### Semantic Analysis

The semantic analysis in Lab 1 is just very basic.
You will need to expand it in future labs.
Similar to the parser, error handling is only very basic.

### SSA translation & IR

The SSA IR is inspired by [libFirm](https://libfirm.github.io/) and [Sea-of-Nodes](https://github.com/SeaOfNodes/).
It might be helpful to study these to get a better understanding of what is going on.
The implementation also showcases how SSA translation can directly apply optimizations.

In the first lab, you don't need to understand SSA in full detail.
However, register allocation on chordal graphs depends on SSA.
For Lab 1, register allocation can also be done just using the AST,
but that means you'll likely have to rewrite more code in future labs.
It can still make sense to start with simple, naive implementations to have something working early on.

### Code generation

This is more or less just a placeholder.
You most likely just want to fully replace it with your register allocation and instruction selection.

## Debugging Utilities

There is a chance something won't work on the first try.
To figure out the cause, we provide utilities that ease debugging.

- `edu.kit.kastel.vads.compiler.parser.Printer` allows printing the AST.
  As it inserts many parentheses, it can be helpful when debugging precedence problems.
- `edu.kit.kastel.vads.compiler.ir.util.GraphVizPrinter` can generate output in
  the DOT format. There are online tools (e.g.,
  <https://magjac.com/graphviz-visual-editor/>, which can display tooltips and
  subgraphs, or https://www.yworks.com/yed-live/, which is relatively good at
  neighbourhoods and larger layouts) that can visualize that output.
  It allows debugging anything related to the IR.
- `edu.kit.kastel.vads.compiler.ir.util.YCompPrinter` can generate output for [yComp](https://pp.ipd.kit.edu/firm/yComp.html).
  This tool is more sophisticated than GraphViz. See below for further information.

We also try to keep track of source positions as much as possible through the compiler.
You can get rid of all that, but it can be helpful to track down where something comes from.

### yComp

To use yComp, you need to patch the provided start script to make it work with modern Java versions.
You can copy-paste the following script:
```sh
#!/bin/sh
set -e

YCOMP="$0"
while [ -L "$YCOMP" ]; do
    LINK="$(readlink "$YCOMP")"
    case "$LINK" in
        /*) YCOMP="$LINK";;
        *)  YCOMP="${YCOMP%/*}/$LINK";;
    esac
done

# 1.5.0_22, 1.8, 9, 11.0.2...
# We only match on the first field
JAVA_VERSION="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)"

ADDITIONAL_ARGUMENTS=""

if [ "$JAVA_VERSION" -gt 9 ]; then
    echo "Java 9+ detected, opening potentially required packages"
    ADDITIONAL_ARGUMENTS='--add-opens java.desktop/sun.swing=ALL-UNNAMED'
fi

echo "Commandline is: 'java -Xmx512m $ADDITIONAL_ARGUMENTS -jar "${YCOMP%/*}/yComp.jar" "$@"'"
java -Xmx512m $ADDITIONAL_ARGUMENTS -jar "${YCOMP%/*}/yComp.jar" "$@"
```

You can directly dump graphs by setting the `DUMP_GRAPHS` environment variable to `vcg` or by passing `-DdumpGraphs=vcg`
to the compiler as a JVM argument (not as a program argument!).
The graphs will be dumped to the `graphs` directory relative to the output file.

## Miscellaneous

### Nullability

This project uses [jspecify](https://jspecify.dev/).
The `module-info.java` is annotated with `@NullMarked`,
meaning uses of `null` must be annotated, and not-null is assumed otherwise.

### Gradle

This project provides the wrapper for Gradle 8.14.
Additionally, the `application` plugin is used to easily specify the main class and build ready-to-use executables.
To ease setup ceremony,
the `foojay-resolver-convention` is used to automatically download a JDK matching the toolchain configuration.
