package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.expr.ExprCase;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.expr.pattern.*;
import se.lth.cs.tycho.ir.stmt.StmtCase;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.ProductType;
import se.lth.cs.tycho.type.SumType;
import se.lth.cs.tycho.type.Type;

import java.util.Objects;
import java.util.stream.Collectors;

@Module
public interface PatternMatching {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default String evaluate(ExprCase caseExpr) {
        String expr = backend().variables().generateTemp();
        emitter().emit("%s = %s;", backend().declarations().declaration(backend().types().type(caseExpr.getExpression()), expr), backend().expressioneval().evaluate(caseExpr.getExpression()));
        String match = backend().variables().generateTemp();
        emitter().emit("%s = false;", backend().declarations().declaration(BoolType.INSTANCE, match));
        Type type = backend().types().type(caseExpr);
        String result = backend().variables().generateTemp();
        emitter().emit("%s;", backend().declarations().declaration(type, result));
        caseExpr.getAlternatives().forEach(alternative -> {
            emitter().emit("if (!%s) {", match);
            emitter().increaseIndentation();
            evaluateAlternative(alternative, expr, result, match);
            emitter().decreaseIndentation();
            emitter().emit("}");
        });
        emitter().emit("if (!%s) {", match);
        emitter().increaseIndentation();
        backend().statements().copy(backend().types().type(caseExpr), result, backend().types().type(caseExpr.getDefault()), backend().expressioneval().evaluate(caseExpr.getDefault()));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return result;
    }


    default void execute(StmtCase caseStmt) {
        String expr = backend().variables().generateTemp();
        emitter().emit("%s = %s;", backend().declarations().declaration(backend().types().type(caseStmt.getExpression()), expr), backend().expressioneval().evaluate(caseStmt.getExpression()));
        String match = backend().variables().generateTemp();
        emitter().emit("%s = false;", backend().declarations().declaration(BoolType.INSTANCE, match));
        caseStmt.getAlternatives().forEach(alternative -> {
            emitter().emit("if (!%s) {", match);
            emitter().increaseIndentation();
            executeAlternative(alternative, expr, match);
            emitter().decreaseIndentation();
            emitter().emit("}");
        });
        caseStmt.getDefault().ifPresent(default_ -> {
            emitter().emit("if (!%s) {", match);
            emitter().increaseIndentation();
            backend().statements().execute(default_);
            emitter().decreaseIndentation();
            emitter().emit("}");
        });
    }

    default void evaluateAlternative(ExprCase.Alternative alternative, String expr, String result, String match) {
        openPattern(alternative.getPattern(), expr, "", "");
        alternative.getGuards().forEach(guard -> {
            emitter().emit("if (%s) {", backend().expressioneval().evaluate(guard));
            emitter().increaseIndentation();
        });
        backend().statements().copy(backend().types().type(alternative.getExpression()), result, backend().types().type(alternative.getExpression()), backend().expressioneval().evaluate(alternative.getExpression()));
        emitter().emit("%s = true;", match);
        alternative.getGuards().forEach(guard -> {
            emitter().decreaseIndentation();
            emitter().emit("}");
        });
        closePattern(alternative.getPattern());
    }

    default void executeAlternative(StmtCase.Alternative alternative, String expr, String match) {
        openPattern(alternative.getPattern(), expr, "", "");
        alternative.getGuards().forEach(guard -> {
            emitter().emit("if (%s) {", backend().expressioneval().evaluate(guard));
            emitter().increaseIndentation();
        });
        alternative.getStatements().forEach(statement -> {
            backend().statements().execute(statement);
        });
        emitter().emit("%s = true;", match);
        alternative.getGuards().forEach(guard -> {
            emitter().decreaseIndentation();
            emitter().emit("}");
        });
        closePattern(alternative.getPattern());
    }

    void openPattern(Pattern pattern, String target, String deref, String member);

    default void openPattern(PatternDeconstruction pattern, String target, String deref, String member) {
        Type type = backend().types().type(pattern);
        if (type instanceof SumType) {
            SumType sum = (SumType) type;
            SumType.VariantType variant = sum.getVariants().stream().filter(v -> Objects.equals(v.getName(), pattern.getName())).findAny().get();
            emitter().emit("if (%s.tag == %s::Tag::%2$s___%s) {", member == "" ? target : target + deref + member, sum.getName(), variant.getName());
            emitter().increaseIndentation();
            for (int i = 0; i < variant.getFields().size(); ++i) {
                openPattern(pattern.getPatterns().get(i), (member == "" ? target : target + deref + member) + "." + pattern.getName(), ".", variant.getFields().get(i).getName());
            }
        } else if (type instanceof ProductType) {
            ProductType product = (ProductType) type;
            for (int i = 0; i < product.getFields().size(); ++i) {
                openPattern(pattern.getPatterns().get(i), member == "" ? target : target + deref + member, ".", product.getFields().get(i).getName());
            }
        }
    }

    default void openPattern(PatternExpression pattern, String target, String deref, String member) {
        Type type = backend().types().type(pattern.getExpression());
        emitter().emit("if (%s) {", backend().statements().compare(type, String.format("%s%s%s", target, deref, member), type, backend().expressioneval().evaluate(pattern.getExpression())));
        emitter().increaseIndentation();
    }


    default void openPattern(PatternBinding pattern, String target, String deref, String member) {
        emitter().emit("%s = %s%s%s;", backend().declarations().declaration(backend().types().type(pattern), backend().variables().declarationName(pattern.getDeclaration())), target, deref, member);
    }

    default void openPattern(PatternVariable pattern, String target, String deref, String member) {
        emitter().emit("%s = %s%s%s;", backend().variables().name(pattern.getVariable()), target, deref, member);
    }

    default void openPattern(PatternLiteral pattern, String target, String deref, String member) {
        Type type = backend().types().type(pattern.getLiteral());
        emitter().emit("if (%s) {", backend().statements().compare(type, String.format("%s%s%s", target, deref, member), type, backend().expressioneval().evaluate(pattern.getLiteral())));
        emitter().increaseIndentation();
    }

    default void openPattern(PatternAlias pattern, String target, String deref, String member) {
        Type type = backend().types().type(pattern.getExpression());
        String expr = backend().expressioneval().evaluate(pattern.getExpression());
        String alias;
        if (pattern.getAlias() instanceof PatternVariable) {
            alias = backend().variables().name(((PatternVariable) pattern.getAlias()).getVariable());
            backend().statements().copy(type, alias, type, expr);
        } else if (pattern.getAlias() instanceof PatternBinding) {
            alias = backend().variables().declarationName(((PatternBinding) pattern.getAlias()).getDeclaration());
            emitter().emit("%s = %s;", backend().declarations().declaration(type, alias), backend().defaultValues().defaultValue(type));
            backend().statements().copy(type, alias, type, expr);
        } else {
            alias = expr;
        }
        emitter().emit("if (%s) {", backend().statements().compare(type, String.format("%s%s%s", target, deref, member), type, alias));
        emitter().increaseIndentation();
    }

    default void openPattern(PatternAlternative pattern, String target, String deref, String member) {
        emitter().emit("if (%s) {", pattern.getPatterns().stream().map(p -> {
            Expression expr;
            if (p instanceof PatternLiteral) {
                expr = ((PatternLiteral) p).getLiteral();
            } else {
                expr = ((PatternExpression) p).getExpression();
            }
            Type type = backend().types().type(expr);
            return backend().statements().compare(type, String.format("%s%s%s", target, deref, member), type, backend().expressioneval().evaluate(expr));
        }).collect(Collectors.joining(" || ")));
        emitter().increaseIndentation();
    }

    default void openPattern(PatternList pattern, String target, String deref, String member) {
        for (int i = 0; i < pattern.getPatterns().size(); ++i) {
            openPattern(pattern.getPatterns().get(i), target, deref, String.format("%s.data[%d]", member, i));
        }
    }

    void closePattern(Pattern pattern);

    default void closePattern(PatternDeconstruction pattern) {
        Type type = backend().types().type(pattern);
        if (type instanceof SumType) {
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
        pattern.getPatterns().forEach(this::closePattern);
    }

    default void closePattern(PatternExpression pattern) {
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void closePattern(PatternDeclaration pattern) {

    }

    default void closePattern(PatternVariable pattern) {

    }

    default void closePattern(PatternLiteral pattern) {
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void closePattern(PatternAlias pattern) {
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void closePattern(PatternAlternative pattern) {
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void closePattern(PatternList pattern) {
        pattern.getPatterns().forEach(this::closePattern);
    }

}
