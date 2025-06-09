package edu.kit.kastel.vads.compiler.ir;

import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfo;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfoHelper;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BinaryOperator;

/// SSA translation as described in
/// [`Simple and Efficient Construction of Static Single Assignment Form`](https://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf).
///
/// This implementation also tracks side effect edges that can be used to avoid reordering of operations that cannot be
/// reordered.
///
/// We recommend to read the paper to better understand the mechanics implemented here.
public class SsaTranslation {
    private final FunctionTree function;
    private final GraphConstructor constructor;

    public SsaTranslation(FunctionTree function, Optimizer optimizer) {
        this.function = function;
        this.constructor = new GraphConstructor(optimizer, function.name().name().asString());
    }

    public IrGraph translate() {
        var visitor = new SsaTranslationVisitor();
        this.function.accept(visitor, this);
        return this.constructor.graph();
    }

    private void writeVariable(Name variable, Block block, Node value) {
        this.constructor.writeVariable(variable, block, value);
    }

    private Node readVariable(Name variable, Block block) {
        return this.constructor.readVariable(variable, block);
    }

    private Block currentBlock() {
        return this.constructor.currentBlock();
    }

    private static class SsaTranslationVisitor implements Visitor<SsaTranslation, Optional<Node>> {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static final Optional<Node> NOT_AN_EXPRESSION = Optional.empty();

        private final Deque<DebugInfo> debugStack = new ArrayDeque<>();

        private void pushSpan(Tree tree) {
            this.debugStack.push(DebugInfoHelper.getDebugInfo());
            DebugInfoHelper.setDebugInfo(new DebugInfo.SourceInfo(tree.span()));
        }

        private void popSpan() {
            DebugInfoHelper.setDebugInfo(this.debugStack.pop());
        }

        @Override
        public Optional<Node> visit(AssignmentTree assignmentTree, SsaTranslation data) {
            pushSpan(assignmentTree);
            BinaryOperator<Node> desugar = switch (assignmentTree.operator().type()) {
                case ASSIGN_MINUS -> data.constructor::newSub;
                case ASSIGN_PLUS -> data.constructor::newAdd;
                case ASSIGN_MUL -> data.constructor::newMul;
                case ASSIGN_BIT_AND -> data.constructor::newBitAnd;
                case ASSIGN_BIT_XOR -> data.constructor::newBitXor;
                case ASSIGN_BIT_OR -> data.constructor::newBitOr;
                case ASSIGN_BIT_SHIFT_LEFT -> data.constructor::newShiftLeft;
                case ASSIGN_BIT_SHIFT_RIGHT -> data.constructor::newShiftRight;
                case ASSIGN_DIV -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case ASSIGN_MOD -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case ASSIGN -> null;
                default ->
                        throw new IllegalArgumentException("not an assignment operator " + assignmentTree.operator());
            };

            switch (assignmentTree.lValue()) {
                case LValueIdentTree(var name) -> {
                    Node rhs = assignmentTree.expression().accept(this, data).orElseThrow();
                    if (desugar != null) {
                        rhs = desugar.apply(data.readVariable(name.name(), data.currentBlock()), rhs);
                    }
                    data.writeVariable(name.name(), data.currentBlock(), rhs);
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BinaryOperationTree binaryOperationTree, SsaTranslation data) {
            pushSpan(binaryOperationTree);
            Node lhs = binaryOperationTree.lhs().accept(this, data).orElseThrow();
            Node rhs = binaryOperationTree.rhs().accept(this, data).orElseThrow();
            Node res = switch (binaryOperationTree.operatorType()) {
                case MINUS -> data.constructor.newSub(lhs, rhs);
                case PLUS -> data.constructor.newAdd(lhs, rhs);
                case MUL -> data.constructor.newMul(lhs, rhs);
                case BIT_SHIFT_LEFT -> data.constructor.newShiftLeft(lhs, rhs);
                case BIT_SHIFT_RIGHT -> data.constructor.newShiftRight(lhs, rhs);
                case LESS -> data.constructor.newLess(lhs, rhs);
                case LESS_EQUAL -> data.constructor.newLessEq(lhs, rhs);
                case MORE -> data.constructor.newMore(lhs, rhs);
                case MORE_EQUAL -> data.constructor.newMoreEq(lhs, rhs);
                case EQUAL -> data.constructor.newEq(lhs, rhs);
                case NOT_EQUAL -> data.constructor.newNotEq(lhs, rhs);
                case BIT_AND -> data.constructor.newBitAnd(lhs, rhs);
                case BIT_XOR -> data.constructor.newBitXor(lhs, rhs);
                case BIT_OR -> data.constructor.newBitOr(lhs, rhs);
                case LOG_AND -> data.constructor.newAnd(lhs, rhs);
                case LOG_OR -> data.constructor.newOr(lhs, rhs);
                case DIV -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case MOD -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                default ->
                        throw new IllegalArgumentException("not a binary expression operator " + binaryOperationTree.operatorType());
            };
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(BlockTree blockTree, SsaTranslation data) {
            pushSpan(blockTree);
            for (StatementTree statement : blockTree.statements()) {
                statement.accept(this, data);
                // skip everything after a return in a block
                if (statement instanceof ReturnTree) {
                    break;
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(DeclarationTree declarationTree, SsaTranslation data) {
            pushSpan(declarationTree);
            if (declarationTree.initializer() != null) {
                Node rhs = declarationTree.initializer().accept(this, data).orElseThrow();
                data.writeVariable(declarationTree.name().name(), data.currentBlock(), rhs);
            }
            // TODO: Check whether this results in issues, then remove general recursive parsing of DeclarationTrees
            //visit the new additional statements
            for (StatementTree statement : declarationTree.statements()) {
                statement.accept(this, data);
                // skip everything after a return in a block
                if (statement instanceof ReturnTree) {
                    break;
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(FunctionTree functionTree, SsaTranslation data) {
            pushSpan(functionTree);
            Node start = data.constructor.newStart();
            data.constructor.writeCurrentSideEffect(data.constructor.newSideEffectProj(start));
            functionTree.body().accept(this, data);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(IdentExpressionTree identExpressionTree, SsaTranslation data) {
            pushSpan(identExpressionTree);
            Node value = data.readVariable(identExpressionTree.name().name(), data.currentBlock());
            popSpan();
            return Optional.of(value);
        }

        @Override
        public Optional<Node> visit(LiteralTree literalTree, SsaTranslation data) {
            pushSpan(literalTree);
            Node node = data.constructor.newConstInt((int) literalTree.parseValue().orElseThrow());
            popSpan();
            return Optional.of(node);
        }

        @Override
        public Optional<Node> visit(BoolLiteralTree boolLiteralTree, SsaTranslation data) {
            pushSpan(boolLiteralTree);
            Node node = data.constructor.newConstBool(boolLiteralTree.parseValue());
            popSpan();
            return Optional.of(node);
        }

        @Override
        public Optional<Node> visit(LValueIdentTree lValueIdentTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(NameTree nameTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(NegateTree negateTree, SsaTranslation data) {
            pushSpan(negateTree);
            Node node = negateTree.expression().accept(this, data).orElseThrow();
            Node res = data.constructor.newSub(data.constructor.newConstInt(0), node);
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(BitNotTree bitNotTree, SsaTranslation data) {
            pushSpan(bitNotTree);
            Node node = bitNotTree.expression().accept(this, data).orElseThrow();
            Node res = data.constructor.newBitXor(data.constructor.newConstInt(~0), node);
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(LogNotTree logNotTree, SsaTranslation data) {
            pushSpan(logNotTree);
            Node node = logNotTree.expression().accept(this, data).orElseThrow();
            Node res = data.constructor.newConditional(node, data.constructor.newConstBool(false), data.constructor.newConstBool(true));
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(ProgramTree programTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(ReturnTree returnTree, SsaTranslation data) {
            pushSpan(returnTree);
            Node node = returnTree.expression().accept(this, data).orElseThrow();
            Node ret = data.constructor.newReturn(node);
            data.constructor.graph().endBlock().addPredecessor(ret);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(TypeTree typeTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }


        @Override
        public Optional<Node> visit(SequentialStatementTree sequentialStatementTree, SsaTranslation data) {
            pushSpan(sequentialStatementTree);
            sequentialStatementTree.statement().accept(this, data);
            if (sequentialStatementTree.statement() instanceof ReturnTree) {
                popSpan();
                return NOT_AN_EXPRESSION;
            }

            for (StatementTree statement : sequentialStatementTree.statements()) {
                statement.accept(this, data);
                // skip everything after a return in a block
                if (statement instanceof ReturnTree) {
                    break;
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(IfTree ifTree, SsaTranslation data) {
            pushSpan(ifTree);

            // Evaluate condition in current block
            Node condition = ifTree.condition().accept(this, data).orElseThrow();

            // Create blocks for then, else (if exists), and merge
            Block thenBlock = data.constructor.newBlock();
            Block elseBlock = ifTree.elseStatement() != null ? data.constructor.newBlock() : null;
            Block mergeBlock = data.constructor.newBlock();

            // Create conditional jump from current block
            Block falseTarget = elseBlock != null ? elseBlock : mergeBlock;
            Node condJump = data.constructor.newCondJump(condition, thenBlock, falseTarget);
            thenBlock.addPredecessor(condJump);
            falseTarget.addPredecessor(condJump);

            // Process then branch
            data.constructor.setCurrentBlock(thenBlock);
            ifTree.thenStatement().accept(this, data);


            // Jump to merge block (unless there was a return)
            if (!endsWithReturn(ifTree.thenStatement())) {
                Node jumpToMerge = data.constructor.newJump(mergeBlock);
                mergeBlock.addPredecessor(jumpToMerge);
            }

            // Process else branch if it exists
            if (ifTree.elseStatement() != null) {
                data.constructor.setCurrentBlock(elseBlock);
                ifTree.elseStatement().accept(this, data);

                // Jump to merge block (unless there was a return)
                if (!endsWithReturn(ifTree.elseStatement())) {
                    Node jumpToMerge = data.constructor.newJump(mergeBlock);
                    mergeBlock.addPredecessor(jumpToMerge);
                }
            }

            // Seal the processed blocks
            data.constructor.sealBlock(thenBlock);
            if (elseBlock != null) {
                data.constructor.sealBlock(elseBlock);
            }

            // Continue with merge block
            data.constructor.setCurrentBlock(mergeBlock);
            data.constructor.sealBlock(mergeBlock);

            popSpan();
            return Optional.of(condition);
        }

        @Override
        public Optional<Node> visit(WhileTree whileTree, SsaTranslation data) {
            pushSpan(whileTree);

            // Create blocks for loop header, body, and exit
            Block headerBlock = data.constructor.newBlock();
            Block bodyBlock = data.constructor.newBlock();
            Block exitBlock = data.constructor.newBlock();

            // Jump to header block
            Node jumpToHeader = data.constructor.newJump(headerBlock);
            headerBlock.addPredecessor(jumpToHeader);

            // Switch to header block and evaluate condition
            data.constructor.setCurrentBlock(headerBlock);
            Node condition = whileTree.condition().accept(this, data).orElseThrow();

            // Create conditional jump: true goes to body, false goes to exit
            Node condJump = data.constructor.newCondJump(condition, bodyBlock, exitBlock);
            bodyBlock.addPredecessor(condJump);
            exitBlock.addPredecessor(condJump);

            // Process loop body
            data.constructor.setCurrentBlock(bodyBlock);
            whileTree.body().accept(this, data);

            // Jump back to header (unless there was a return)
            if (!endsWithReturn(whileTree.body())) {
                Node jumpBackToHeader = data.constructor.newJump(headerBlock);
                headerBlock.addPredecessor(jumpBackToHeader);
            }

            // Seal the body block
            data.constructor.sealBlock(bodyBlock);

            // Now we can seal the header block (it has all predecessors)
            data.constructor.sealBlock(headerBlock);

            // Continue with exit block
            data.constructor.setCurrentBlock(exitBlock);
            data.constructor.sealBlock(exitBlock);

            popSpan();
            return NOT_AN_EXPRESSION;
        }

        // Helper method to check if a statement ends with a return
        private boolean endsWithReturn(StatementTree statement) {
            if (statement instanceof ReturnTree) {
                return true;
            }
            if (statement instanceof BlockTree blockTree) {
                var statements = blockTree.statements();
                if (!statements.isEmpty()) {
                    return endsWithReturn(statements.getLast());
                }
            }
            return false;
        }

        @Override
        public Optional<Node> visit(CondExprTree condExprTree, SsaTranslation data) {
            pushSpan(condExprTree);
            Node cond = condExprTree.cond().accept(this, data).orElseThrow();
            Node exp1 = condExprTree.exp1().accept(this, data).orElseThrow();
            Node exp2 = condExprTree.exp2().accept(this, data).orElseThrow();
            Node res = data.constructor.newConditional(cond, exp1, exp2);
            popSpan();
            return Optional.of(res);
        }

        private Node projResultDivMod(SsaTranslation data, Node divMod) {
            // make sure we actually have a div or a mod, as optimizations could
            // have changed it to something else already
            if (!(divMod instanceof DivNode || divMod instanceof ModNode)) {
                return divMod;
            }
            Node projSideEffect = data.constructor.newSideEffectProj(divMod);
            data.constructor.writeCurrentSideEffect(projSideEffect);
            return data.constructor.newResultProj(divMod);
        }
    }


}
