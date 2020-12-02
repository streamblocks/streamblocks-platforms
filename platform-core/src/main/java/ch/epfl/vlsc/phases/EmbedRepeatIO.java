package ch.epfl.vlsc.phases;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValuePortIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;

import java.util.*;

public class EmbedRepeatIO implements Phase {
    @Override
    public String getDescription() {
        return "Move sequential read and write to StmtWhile or StmtForEach";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Transformation transformation = MultiJ.from(Transformation.class)
                .bind("declarations").to(task.getModule(VariableDeclarations.key))
                .bind("tree").to(task.getModule(TreeShadow.key))
                .instance();

        return task.transformChildren(transformation);
    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        VariableDeclarations declarations();

        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        @Binding(BindingKind.LAZY)
        default List<LocalVarDecl> declarationsToRemove() {
            return new ArrayList<>();
        }

        @Binding(BindingKind.LAZY)
        default Map<ExprIndexer, Port> exprIndexerToReplace() {
            return new HashMap<>();
        }

        @Binding(BindingKind.LAZY)
        default Map<LValueIndexer, Port> lValueIndexerToReplace() {
            return new HashMap<>();
        }

        @Binding(BindingKind.LAZY)
        default List<StmtWrite> writesToBeRemoved() {
            return new ArrayList<>();
        }


        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default IRNode apply(Transition transition) {
            declarationsToRemove().clear();
            exprIndexerToReplace().clear();
            lValueIndexerToReplace().clear();

            // -- Collect ExprInput
            List<ExprInput> reads = new ArrayList<>();
            transition.forEachChild(child -> reads.addAll(collectExprInput(child)));

            // -- Collect StmtWrite
            List<StmtWrite> writes = new ArrayList<>();
            transition.forEachChild(child -> writes.addAll(collectStmtWrite(child)));

            // -- Get declarations for each input
            for (ExprInput read : reads) {
                IRNode parent = tree().parent(read);
                if (parent instanceof LocalVarDecl) {
                    LocalVarDecl decl = (LocalVarDecl) parent;

                    List<ExprIndexer> exprIndexerCandidates = new ArrayList<>();
                    transition.forEachChild(child -> exprIndexerCandidates.addAll(collectCandidateExprIndexer(child, decl)));

                    if (!exprIndexerCandidates.isEmpty()) {
                        for (ExprIndexer indexer : exprIndexerCandidates) {
                            exprIndexerToReplace().put(indexer, read.getPort());
                        }
                        declarationsToRemove().add(decl);
                    }

                    // -- Writing to input, authorized in RVC-CAL
                    List<LValueIndexer> lValueIndexerCandidates = new ArrayList<>();
                    transition.forEachChild(child -> lValueIndexerCandidates.addAll(collectCandidateLValueIndexer(child, decl)));
                    if (!lValueIndexerCandidates.isEmpty()) {
                        for (LValueIndexer indexer : lValueIndexerCandidates) {
                            lValueIndexerToReplace().put(indexer, read.getPort());
                        }
                    }
                }
            }

            // -- Get declaration for each write
            for (StmtWrite write : writes) {
                for (Expression expression : write.getValues()) {
                    if (write.getValues().size() == 1) {
                        if (expression instanceof ExprVariable) {
                            VarDecl decl = declarations().declaration((ExprVariable) expression);
                            if (!(tree().parent(decl) instanceof Scope)) {
                                if (decl instanceof LocalVarDecl) {
                                    List<LValueIndexer> lValueIndexerCandidates = new ArrayList<>();
                                    transition.forEachChild(child -> lValueIndexerCandidates.addAll(collectCandidateLValueIndexer(child, (LocalVarDecl) decl)));

                                    if (!lValueIndexerCandidates.isEmpty()) {
                                        for (LValueIndexer indexer : lValueIndexerCandidates) {
                                            lValueIndexerToReplace().put(indexer, write.getPort());
                                        }
                                        writesToBeRemoved().add(write);
                                        declarationsToRemove().add((LocalVarDecl) decl);
                                    }

                                    if (!(decl.getValue() instanceof ExprInput)) {
                                        // -- Reading from output linked to variable to be removed
                                        List<ExprIndexer> exprIndexerCandidates = new ArrayList<>();
                                        transition.forEachChild(child -> exprIndexerCandidates.addAll(collectCandidateExprIndexer(child, (LocalVarDecl) decl)));

                                        if (!exprIndexerCandidates.isEmpty()) {
                                            for (ExprIndexer indexer : exprIndexerCandidates) {
                                                exprIndexerToReplace().put(indexer, write.getPort());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return transition.transformChildren(this);
        }

        // -- Collectors


        // -- Collect ExprInput
        default List<ExprInput> collectExprInput(IRNode node) {
            List<ExprInput> reads = new ArrayList<>();
            node.forEachChild(child -> reads.addAll(collectExprInput(child)));
            return reads;
        }

        default List<ExprInput> collectExprInput(ExprInput input) {
            if (input.hasRepeat()) {
                return Collections.singletonList(input);
            }
            return new ArrayList<>();
        }

        default List<ExprIndexer> collectCandidateExprIndexer(IRNode node, LocalVarDecl declaration) {
            List<ExprIndexer> indexers = new ArrayList<>();
            node.forEachChild(child -> indexers.addAll(collectCandidateExprIndexer(child, declaration)));
            return indexers;
        }

        default List<ExprIndexer> collectCandidateExprIndexer(ExprIndexer indexer, LocalVarDecl declaration) {
            List<ExprIndexer> indexers = new ArrayList<>();

            indexer.forEachChild(child -> indexers.addAll(collectCandidateExprIndexer(child, declaration)));

            if (indexer.getStructure() instanceof ExprVariable) {
                // -- Check if this indexer structure contains the declaration to be searched
                if (declaration == declarations().declaration((ExprVariable) indexer.getStructure())) {
                    // -- Retrieve the parent, we are searching While or Foreach
                    boolean found = false;
                    IRNode parent = tree().parent(indexer);
                    while (!found) {
                        if (parent instanceof StmtWhile) {
                            found = true;
                        } else if (parent instanceof StmtForeach) {
                            found = true;
                        } else if (parent instanceof StmtWhile) {
                            found = true;
                        } else if (parent instanceof StmtWrite) {
                            found = true;
                        } else if (parent instanceof StmtAssignment) {
                            found = true;
                        } else if (parent instanceof StmtCall) {
                            found = true;
                        } else if (parent instanceof StmtCase) {
                            found = true;
                        } else if (parent instanceof ExprList) {
                            found = true;
                        } else if (parent instanceof StmtIf) {
                            found = true;
                        } else {
                            parent = tree().parent(parent);
                            if (parent == tree().root()) {
                                break;
                            }
                        }
                    }
                    // -- Evaluate index
                    if (found) {
                        boolean isLinear = evaluateIndex(parent, indexer.getIndex());
                        if (isLinear) {
                            return Collections.singletonList(indexer);
                        }
                    }
                }
            }
            return indexers;
        }

        default List<LValueIndexer> collectCandidateLValueIndexer(IRNode node, LocalVarDecl declaration) {
            List<LValueIndexer> indexers = new ArrayList<>();
            node.forEachChild(child -> indexers.addAll(collectCandidateLValueIndexer(child, declaration)));
            return indexers;
        }

        default List<LValueIndexer> collectCandidateLValueIndexer(LValueIndexer indexer, LocalVarDecl declaration) {
            List<LValueIndexer> indexers = new ArrayList<>();
            indexer.forEachChild(child -> indexers.addAll(collectCandidateLValueIndexer(child, declaration)));

            if (indexer.getStructure() instanceof LValueVariable) {
                // -- Check if this indexer structure contains the declaration to be searched
                if (declaration == declarations().declaration((LValueVariable) indexer.getStructure())) {
                    // -- Retrieve the parent, we are searching While or Foreach
                    boolean found = false;
                    IRNode parent = tree().parent(indexer);
                    while (!found) {
                        if (parent instanceof StmtWhile) {
                            found = true;
                        } else if (parent instanceof StmtForeach) {
                            found = true;
                        } else if (parent instanceof StmtAssignment) {
                            found = true;
                        } else if (parent instanceof StmtIf) {
                            found = true;
                        } else {
                            parent = tree().parent(parent);
                        }
                    }
                    // -- Evaluate index
                    boolean isLinear = evaluateIndex(parent, indexer.getIndex());
                    if (isLinear) {
                        return Collections.singletonList(indexer);
                    }
                }
            }

            return indexers;
        }


        default boolean evaluateIndex(IRNode node, Expression index) {
            return false;
        }

        default boolean evaluateIndex(StmtForeach stmtForeach, Expression index) {
            return true;
        }

        default boolean evaluateIndex(StmtWhile stmtWhile, Expression index) {
            return true;
        }

        default boolean evaluateIndex(StmtWrite stmtWhile, Expression index) {
            return true;
        }

        default boolean evaluateIndex(StmtAssignment stmtWhile, Expression index) {
            return true;
        }

        default boolean evaluateIndex(StmtIf stmIf, Expression index) {
            return true;
        }

        default boolean evaluateIndex(StmtCall call, Expression index) {
            return true;
        }

        default boolean evaluateIndex(StmtCase call, Expression index) {
            return true;
        }

        default boolean evaluateIndex(ExprList list, Expression index) {
            return true;
        }

        // -- Collect StmtWrites

        default List<StmtWrite> collectStmtWrite(IRNode node) {
            List<StmtWrite> writes = new ArrayList<>();
            node.forEachChild(child -> writes.addAll(collectStmtWrite(child)));
            return writes;
        }

        default List<StmtWrite> collectStmtWrite(StmtWrite write) {
            if (write.getRepeatExpression() != null) {
                return Collections.singletonList(write);
            }
            return new ArrayList<>();
        }


        // -- Transformations

        default IRNode apply(StmtBlock block) {
            block = block.transformChildren(this);

            List<LocalVarDecl> currentDecls = new ArrayList<>(block.getVarDecls());
            List<LocalVarDecl> toBeRemoved = new ArrayList<>();

            for (LocalVarDecl candidate : declarationsToRemove()) {
                if (currentDecls.contains(candidate)) {
                    toBeRemoved.add(candidate);
                }
            }

            // -- Remove the declarations
            currentDecls.removeAll(toBeRemoved);

            return block.withVarDecls(currentDecls);
        }


        // -- Replace ExprIndexer with ExprInputIndexer
        default IRNode apply(ExprIndexer indexer) {
            indexer = indexer.transformChildren(this);

            if (exprIndexerToReplace().containsKey(indexer)) {
                Port port = exprIndexerToReplace().get(indexer);
                ExprPortIndexer exprInputIndexer = new ExprPortIndexer(port.clone(), indexer.getIndex().deepClone());
                return exprInputIndexer;
            }
            return indexer;
        }

        // -- Replace LValueIndexer with LValueWriteIndexer
        default IRNode apply(LValueIndexer indexer) {
            indexer = indexer.transformChildren(this);

            if (lValueIndexerToReplace().containsKey(indexer)) {
                Port port = lValueIndexerToReplace().get(indexer);
                LValuePortIndexer writeIndexer = new LValuePortIndexer(port.clone(), indexer.getIndex().deepClone());
                return writeIndexer;
            }
            return indexer;
        }

        // -- Replace StmWrite with empty BasicBlocks
        default IRNode apply(StmtWrite write) {
            write = write.transformChildren(this);

            if (writesToBeRemoved().contains(write)) {
                StmtBlock emptyBlock = new StmtBlock(ImmutableList.empty(), ImmutableList.empty(), ImmutableList.empty());
                return emptyBlock;
            }

            return write;
        }


    }
}
