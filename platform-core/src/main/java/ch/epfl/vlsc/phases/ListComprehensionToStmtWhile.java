package ch.epfl.vlsc.phases;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.decoration.TypeToTypeExpr;
import se.lth.cs.tycho.ir.Generator;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtForeach;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.*;

public class ListComprehensionToStmtWhile implements Phase {
    @Override
    public String getDescription() {
        return "Transform StmtAssignment with comprehension to StmtWhile";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Transformation transformation = MultiJ.from(Transformation.class)
                .bind("types").to(task.getModule(Types.key))
                .bind("unique").to(context.getUniqueNumbers())
                .instance();

        return task.transformChildren(transformation);
    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        UniqueNumbers unique();

        @Override
        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default IRNode apply(StmtBlock block) {

            StmtBlock visitedBlock = block.transformChildren(this);

            List<VarDecl> declWithComprehensions = new ArrayList<>();

            for (VarDecl decl : visitedBlock.getVarDecls()) {
                if (decl.getValue() != null) {
                    if (decl.getValue() instanceof ExprComprehension) {
                        declWithComprehensions.add(decl);
                    }
                }
            }

            if (declWithComprehensions.isEmpty()) {
                return visitedBlock;
            }

            // -- Create statements
            ImmutableList.Builder<Statement> statements = ImmutableList.builder();

            for (VarDecl decl : declWithComprehensions) {
                Type type = types().type(decl.getType());
                if (!(type instanceof ListType)) {
                    return visitedBlock;
                }
                LValueVariable lValueVariable = new LValueVariable(Variable.variable(decl.getName()));
                ExprComprehension comprehension = (ExprComprehension) decl.getValue();
                Statement stmtForeach = comprehensionToStmtForeach(type, lValueVariable, comprehension);
                statements.add(stmtForeach);
            }

            // -- Replace VarDecl values to null
            ImmutableList.Builder<LocalVarDecl> declarations = ImmutableList.builder();

            for (LocalVarDecl decl : visitedBlock.getVarDecls()) {
                if (decl.getValue() != null) {
                    if (decl.getValue() instanceof ExprComprehension) {
                        LocalVarDecl newDecl = decl.withValue(null);
                        declarations.add(newDecl);
                    } else {
                        declarations.add(decl);
                    }
                } else {
                    declarations.add(decl);
                }
            }

            // -- Add all statements of the current block
            statements.addAll(visitedBlock.getStatements());

            // -- Visit all statements

            // -- Create the StmtBlock
            StmtBlock newBlock = visitedBlock.withVarDecls(declarations.build()).withStatements(statements.build());
            return newBlock;
        }


        default IRNode apply(StmtAssignment assignment) {
            if (assignment.getExpression() instanceof ExprComprehension) {
                // -- Apply this transformation iff the assignment is done on ListType
                Type type = types().type(assignment.getLValue());
                if (!(type instanceof ListType)) {
                    return assignment;
                }

                return comprehensionToStmtForeach(type, assignment.getLValue(), (ExprComprehension) assignment.getExpression());
            }

            return assignment;
        }

        default Statement comprehensionToStmtForeach(Type type, LValue lValue, ExprComprehension comprehension) {
            List<String> indexes = new ArrayList<>();
            List<Expression> indexStarts = new ArrayList<>();
            List<Expression> indexEnds = new ArrayList<>();
            ImmutableList.Builder<Statement> statements = ImmutableList.builder();

            // -- Collect Generators
            List<Generator> generators = collectGenerators(comprehension);

            // -- Collect filters
            List<List<Expression>> filters = collectFilter(comprehension);
            Map<Generator, List<Expression>> generatorFilter = new HashMap<>();

            // -- Collect ExprList
            List<ExprList> lists = collectExprList(comprehension);

            // -- Construct generator filter map
            int i = 0;
            for (Generator generator : generators) {
                generatorFilter.put(generator, filters.get(i));
                i++;
            }

            // -- Get indexes variables
            for (Generator generator : generators) {
                for (GeneratorVarDecl decl : generator.getVarDecls()) {
                    // -- Check if operator is '..'
                    if (generator.getCollection() instanceof ExprBinaryOp) {
                        ExprBinaryOp binOp = (ExprBinaryOp) generator.getCollection();

                        if (binOp.getOperations().equals(Collections.singletonList(".."))) {
                            indexes.add(decl.getName());
                            indexStarts.add(binOp.getOperands().get(0));
                            indexEnds.add(binOp.getOperands().get(1));

                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }
            }

            // -- Reverse the indexes
            List<String> reverseIndexes = new ArrayList<>(indexes);
            Collections.reverse(reverseIndexes);

            List<Expression> reverseIndexesStart = new ArrayList<>(indexStarts);
            Collections.reverse(reverseIndexesStart);

            List<Expression> reverseIndexesEnd = new ArrayList<>(indexEnds);
            Collections.reverse(reverseIndexesEnd);

            boolean singleDimAssignment = getListDimension((ListType) type) == 1 && generators.size() > 1;
            String singleDimIndex = "idx_" + unique().next();

            // -- Create Foreach statement
            StmtForeach foreach = createStmtForeach(lValue, generators, reverseIndexesStart, singleDimIndex, generatorFilter, reverseIndexes, lists.get(lists.size() - 1), singleDimAssignment);
            statements.add(foreach);

            if (singleDimAssignment) {
                LocalVarDecl indexDecl = new LocalVarDecl(ImmutableList.empty(), TypeToTypeExpr.convert(new IntType(OptionalInt.empty(), false)), singleDimIndex, null, false);
                StmtBlock block = new StmtBlock(ImmutableList.empty(), ImmutableList.of(indexDecl), ImmutableList.of(foreach));
                return block;
            }

            return foreach;
        }



        /**
         * Create a StmtForeach
         *
         * @param lValue
         * @param generators
         * @param generatorFilters
         * @param indexes
         * @param list
         * @return
         */
        default StmtForeach createStmtForeach(LValue lValue, List<Generator> generators, List<Expression> indexesStart, String singleDimIndex, Map<Generator, List<Expression>> generatorFilters, List<String> indexes, ExprList list, boolean singleDim) {
            List<Statement> statements = new ArrayList<>();
            Generator generator = generators.get(0);
            if (generators.size() == 1) {
                int i = 0;
                for (Expression expression : list.getElements()) {
                    LValue indexer;

                    if (singleDim) {
                        indexer = new LValueIndexer(lValue, new ExprVariable(Variable.variable(singleDimIndex)));
                    } else {
                        indexer = createLValueIndexer(lValue, indexes, indexesStart, i);
                    }

                    StmtAssignment assignment = new StmtAssignment(indexer, expression.deepClone());
                    statements.add(assignment);
                    if (singleDim) {
                        ExprBinaryOp plusPlus = new ExprBinaryOp(ImmutableList.of("+"), ImmutableList.of(new ExprVariable(Variable.variable(singleDimIndex)), new ExprLiteral(ExprLiteral.Kind.Integer, "1")));
                        StmtAssignment indexPlusPlus = new StmtAssignment(new LValueVariable(Variable.variable(singleDimIndex)), plusPlus);
                        statements.add(indexPlusPlus);
                    }

                    i++;
                }
            } else {
                generators.remove(generator);
                statements.add(createStmtForeach(lValue, generators, indexesStart, singleDimIndex, generatorFilters, indexes, list, singleDim));
            }
            return new StmtForeach(generator, generatorFilters.get(generators.get(0)), statements);
        }

        default int getListDimension(ListType type) {
            int n = 1;
            if (type.getElementType() instanceof ListType) {
                n = n + getListDimension((ListType) type.getElementType());
            }
            return n;
        }

        /**
         * Create an LValue indexer, indexes should be in reversed order
         *
         * @param lvalue
         * @param indexes
         * @param increment
         * @return
         */
        default LValue createLValueIndexer(LValue lvalue, List<String> indexes, List<Expression> indexesStart, int increment) {
            Expression index;

            Expression start = indexesStart.get(0);
            if (increment > 0) {
                index = new ExprBinaryOp(ImmutableList.of("+"), ImmutableList.of(new ExprVariable(Variable.variable(indexes.get(0))), new ExprLiteral(ExprLiteral.Kind.Integer, String.valueOf(increment))));
            } else {
                if (start instanceof ExprLiteral) {
                    ExprLiteral literal = (ExprLiteral) start;
                    if (literal.asInt().isPresent()) {
                        if (literal.asInt().getAsInt() == 0) {
                            index = new ExprVariable(Variable.variable(indexes.get(0)));
                        } else {
                            index = new ExprBinaryOp(ImmutableList.of("-"), ImmutableList.of(new ExprVariable(Variable.variable(indexes.get(0))), start.deepClone()));
                        }
                    } else {
                        index = new ExprBinaryOp(ImmutableList.of("-"), ImmutableList.of(new ExprVariable(Variable.variable(indexes.get(0))), start.deepClone()));
                    }
                } else {
                    index = new ExprBinaryOp(ImmutableList.of("-"), ImmutableList.of(new ExprVariable(Variable.variable(indexes.get(0))), start));
                }
            }
            if (indexes.size() == 1) {
                return new LValueIndexer(lvalue, index);
            } else {
                String structure = indexes.get(0);
                indexes.remove(structure);
                indexesStart.remove(start);
                return new LValueIndexer(createLValueIndexer(lvalue, indexes, indexesStart, increment), index);
            }
        }


        // -- Collect Generators
        default List<Generator> collectGenerators(Expression expression) {
            return new ArrayList<>();
        }

        default List<Generator> collectGenerators(ExprComprehension comprehension) {
            List<Generator> generators = new ArrayList<>();
            generators.add(comprehension.getGenerator());
            generators.addAll(collectGenerators(comprehension.getCollection()));

            return generators;
        }

        default List<Generator> collectGenerators(ExprList list) {
            List<Generator> generators = new ArrayList<>();

            for (Expression expression : list.getElements()) {
                generators.addAll(collectGenerators(expression));
            }

            return generators;
        }


        default List<List<Expression>> collectFilter(Expression expression) {
            return new ArrayList<>();
        }

        default List<List<Expression>> collectFilter(ExprComprehension comprehension) {
            List<List<Expression>> filters = new ArrayList<>();

            filters.add(comprehension.getFilters());
            filters.addAll(collectFilter(comprehension.getCollection()));

            return filters;
        }

        default List<List<Expression>> collectFilter(ExprList list) {
            List<List<Expression>> filters = new ArrayList<>();

            for (Expression expression : list.getElements()) {
                filters.addAll(collectFilter(expression));
            }

            return filters;
        }

        default List<ExprList> collectExprList(Expression expression) {
            return new ArrayList<>();
        }

        default List<ExprList> collectExprList(ExprComprehension comprehension) {
            List<ExprList> lists = new ArrayList<>();

            if (comprehension.getCollection() instanceof ExprList) {
                lists.add((ExprList) comprehension.getCollection());
            }
            lists.addAll(collectExprList(comprehension.getCollection()));
            return lists;
        }

        default List<ExprList> collectExprList(ExprList list) {
            List<ExprList> lists = new ArrayList<>();

            for (Expression expression : list.getElements()) {
                lists.addAll(collectExprList(expression));
            }

            return lists;
        }
    }
}
