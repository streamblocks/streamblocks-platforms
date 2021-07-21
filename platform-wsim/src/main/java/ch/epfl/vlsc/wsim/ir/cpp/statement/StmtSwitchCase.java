package ch.epfl.vlsc.wsim.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;

import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;


public class StmtSwitchCase extends Statement {

//    public enum CaseLiteral {
//        Integer(null), Label(null), Default("default");
//        private String value;
//        CaseLiteral(String value) {
//            this.value = value;
//        }
//        public String getValue() {
//            return value;
//        }
//        public String setValue(String value) { this.value = value; }
//
//    }
    public interface CaseLiteral {
        String getValue();

        class Integer implements CaseLiteral {
            private final int value;
            public Integer(int value) { this.value = value; }
            public String getValue() { return String.valueOf(this.value); }
        }

        class Label implements CaseLiteral{
            private final String value;
            public Label(String value) { this.value = value; }
            @Override
            public String getValue() { return this.value; }
        }

        enum Default implements CaseLiteral {
            INSTANCE;
            @Override
            public String getValue() { return "default"; }
        }

    }


    public static class StmtLiteralCase extends Statement {
        private final CaseLiteral literalCase;
        private final ImmutableList<Statement> statements;
        public static StmtLiteralCase caseWithBreak(CaseLiteral literalCase,
                                                    ImmutableList<Statement> statements) {
            return new StmtLiteralCase(literalCase,
                    ImmutableList.<Statement>builder()
                            .addAll(statements)
                            .add(new StmtBreak())
                            .build());
        }
        public StmtLiteralCase(Statement original, CaseLiteral literalCase,
                               ImmutableList<Statement> statements) {
            super(original);
            this.literalCase = literalCase;
            this.statements = statements;
        }
        public StmtLiteralCase(CaseLiteral literalCase, ImmutableList<Statement> statements) {
            this(null, literalCase, statements);
        }

        public StmtLiteralCase copy(CaseLiteral literalCase, ImmutableList<Statement> statements) {
            if (Objects.equals(this.literalCase, literalCase) &&
                    Lists.sameElements(this.statements, statements)) {
                return this;
            } else {
                return new StmtLiteralCase(literalCase, statements);
            }
        }
        @Override
        public void forEachChild(Consumer<? super IRNode> action) {

            statements.forEach(action);
        }

        @Override
        public StmtLiteralCase transformChildren(Transformation transformation) {
            return copy(
                    literalCase,
                    ImmutableList.from(transformation.mapChecked(Statement.class, statements))
            );
        }

        @Override
        public Statement withAnnotations(List<Annotation> annotations) {
            return this;
        }

        public CaseLiteral getLiteralCase() {
            return literalCase;
        }
        public ImmutableList<Statement> getStatements() {
            return statements;
        }
    }

    private final Expression condition;
    private final ImmutableList<StmtLiteralCase> cases;

    public StmtSwitchCase(Statement original, Expression condition, ImmutableList<StmtLiteralCase> cases)
    {
        super(original);
        this.condition = condition;
        this.cases = cases;
    }

    public StmtSwitchCase(Expression condition, ImmutableList<StmtLiteralCase> cases) {
        this(null, condition,  cases);
    }

    public StmtSwitchCase copy(Expression condition, ImmutableList<StmtLiteralCase> cases) {
        if (Objects.equals(condition, this.condition) && Lists.sameElements(cases, this.cases)) {
            return this;
        } else {
            return new StmtSwitchCase(this, condition, cases);
        }
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.condition);
        this.cases.forEach(action);
    }

    @Override
    public StmtSwitchCase transformChildren(Transformation transformation) {
        return copy(
                transformation.applyChecked(Expression.class, this.condition),
                ImmutableList.from(transformation.mapChecked(StmtLiteralCase.class, this.cases))
        );
    }

    @Override
    public Statement withAnnotations(List<Annotation> annotations) {
        return this;
    }

    public Expression getCondition() {
        return condition;
    }

    public ImmutableList<StmtLiteralCase> getCases() {
        return cases;
    }

}
