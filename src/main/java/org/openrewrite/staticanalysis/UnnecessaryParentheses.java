/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.UnwrapParentheses;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.UnnecessaryParenthesesStyle;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnnecessaryParentheses extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove unnecessary parentheses";
    }

    @Override
    public String getDescription() {
        return "Removes unnecessary parentheses from code where extra parentheses pairs are redundant.";
    }

    @Override
    public Set<String> getTags() {
        return new LinkedHashSet<>(Arrays.asList("RSPEC-1110", "RSPEC-1611"));
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
                // Causes problems on other languages like JavaScript
                return sourceFile instanceof J.CompilationUnit;
            }

            private static final String UNNECESSARY_PARENTHESES_MESSAGE = "unnecessaryParenthesesUnwrapTarget";

            transient UnnecessaryParenthesesStyle style;

            private UnnecessaryParenthesesStyle getStyle() {
                if (style == null) {
                    JavaSourceFile cu = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                    style = ((SourceFile) cu).getStyle(UnnecessaryParenthesesStyle.class, Checkstyle.unnecessaryParentheses());
                }
                return style;
            }

            @Override
            public <T extends J> J visitParentheses(J.Parentheses<T> parens, ExecutionContext ctx) {
                J par = super.visitParentheses(parens, ctx);
                Cursor c = getCursor().pollNearestMessage(UNNECESSARY_PARENTHESES_MESSAGE);
                if (c != null && (c.getValue() instanceof J.Literal || c.getValue() instanceof J.Identifier)) {
                    par = new UnwrapParentheses<>((J.Parentheses<?>) par).visit(par, ctx, getCursor().getParentOrThrow());
                }

                assert par != null;
                if (par instanceof J.Parentheses parentheses) {
                    if (getCursor().getParentTreeCursor().getValue() instanceof J.Parentheses) {
                        return parentheses.getTree().withPrefix(Space.EMPTY);
                    }
                }
                return par;
            }

            @Override
            public J visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
                J.Identifier i = (J.Identifier) super.visitIdentifier(ident, ctx);
                if (getStyle().getIdent() && getCursor().getParentTreeCursor().getValue() instanceof J.Parentheses) {
                    getCursor().putMessageOnFirstEnclosing(J.Parentheses.class, UNNECESSARY_PARENTHESES_MESSAGE, getCursor());
                }
                return i;
            }

            @Override
            public J visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = (J.Literal) super.visitLiteral(literal, ctx);
                JavaType.Primitive type = l.getType();
                if ((getStyle().getNumInt() && type == JavaType.Primitive.Int) ||
                    (getStyle().getNumDouble() && type == JavaType.Primitive.Double) ||
                    (getStyle().getNumLong() && type == JavaType.Primitive.Long) ||
                    (getStyle().getNumFloat() && type == JavaType.Primitive.Float) ||
                    (getStyle().getStringLiteral() && type == JavaType.Primitive.String) ||
                    (getStyle().getLiteralNull() && type == JavaType.Primitive.Null) ||
                    (getStyle().getLiteralFalse() && type == JavaType.Primitive.Boolean && l.getValue() == Boolean.valueOf(false)) ||
                    (getStyle().getLiteralTrue() && type == JavaType.Primitive.Boolean && l.getValue() == Boolean.valueOf(true))) {
                    if (getCursor().getParentTreeCursor().getValue() instanceof J.Parentheses) {
                        getCursor().putMessageOnFirstEnclosing(J.Parentheses.class, UNNECESSARY_PARENTHESES_MESSAGE, getCursor());
                    }
                }
                return l;
            }

            @Override
            public J visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
                J.AssignmentOperation a = (J.AssignmentOperation) super.visitAssignmentOperation(assignOp, ctx);
                J.AssignmentOperation.Type op = a.getOperator();
                if (a.getAssignment() instanceof J.Parentheses && ((getStyle().getBitAndAssign() && op == J.AssignmentOperation.Type.BitAnd) ||
                                                                   (getStyle().getBitOrAssign() && op == J.AssignmentOperation.Type.BitOr) ||
                                                                   (getStyle().getBitShiftRightAssign() && op == J.AssignmentOperation.Type.UnsignedRightShift) ||
                                                                   (getStyle().getBitXorAssign() && op == J.AssignmentOperation.Type.BitXor) ||
                                                                   (getStyle().getShiftRightAssign() && op == J.AssignmentOperation.Type.RightShift) ||
                                                                   (getStyle().getShiftLeftAssign() && op == J.AssignmentOperation.Type.LeftShift) ||
                                                                   (getStyle().getMinusAssign() && op == J.AssignmentOperation.Type.Subtraction) ||
                                                                   (getStyle().getDivAssign() && op == J.AssignmentOperation.Type.Division) ||
                                                                   (getStyle().getPlusAssign() && op == J.AssignmentOperation.Type.Addition) ||
                                                                   (getStyle().getStarAssign() && op == J.AssignmentOperation.Type.Multiplication) ||
                                                                   (getStyle().getModAssign() && op == J.AssignmentOperation.Type.Modulo))) {
                    a = (J.AssignmentOperation) new UnwrapParentheses<>((J.Parentheses<?>) a.getAssignment()).visitNonNull(a, ctx, getCursor().getParentOrThrow());
                }
                return a;
            }

            @Override
            public J visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment a = visitAndCast(assignment, ctx, super::visitAssignment);
                if (getStyle().getAssign() && a.getAssignment() instanceof J.Parentheses) {
                    a = (J.Assignment) new UnwrapParentheses<>((J.Parentheses<?>) a.getAssignment()).visitNonNull(a, ctx, getCursor().getParentOrThrow());
                }
                return a;
            }

            @Override
            public J visitReturn(J.Return return_, ExecutionContext ctx) {
                J.Return rtn = (J.Return) super.visitReturn(return_, ctx);
                if (getStyle().getExpr() && rtn.getExpression() instanceof J.Parentheses) {
                    rtn = (J.Return) new UnwrapParentheses<>((J.Parentheses<?>) rtn.getExpression()).visitNonNull(rtn, ctx, getCursor().getParentOrThrow());
                }
                return rtn;
            }

            @Override
            public J visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable v = (J.VariableDeclarations.NamedVariable) super.visitVariable(variable, ctx);
                if (getStyle().getAssign() && v.getInitializer() != null && v.getInitializer() instanceof J.Parentheses) {
                    v = (J.VariableDeclarations.NamedVariable) new UnwrapParentheses<>((J.Parentheses<?>) v.getInitializer()).visitNonNull(v, ctx, getCursor().getParentOrThrow());
                }
                return v;
            }

            @Override
            public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                J.Lambda l = (J.Lambda) super.visitLambda(lambda, ctx);
                if (l.getParameters().getParameters().size() == 1 &&
                    l.getParameters().isParenthesized() &&
                    l.getParameters().getParameters().get(0) instanceof J.VariableDeclarations &&
                    ((J.VariableDeclarations) l.getParameters().getParameters().get(0)).getTypeExpression() == null) {
                    l = l.withParameters(l.getParameters().withParenthesized(false));
                }
                return l;
            }

            @Override
            public J visitIf(J.If iff, ExecutionContext ctx) {
                J.If i = (J.If) super.visitIf(iff, ctx);
                // Unwrap when if condition is a single parenthesized expression
                Expression expression = i.getIfCondition().getTree();
                if (expression instanceof J.Parentheses parentheses) {
                    i = (J.If) new UnwrapParentheses<>(parentheses).visitNonNull(i, ctx, getCursor().getParentOrThrow());
                }
                return i;
            }

            @Override
            public J visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
                J.WhileLoop w = (J.WhileLoop) super.visitWhileLoop(whileLoop, ctx);
                // Unwrap when while condition is a single parenthesized expression
                Expression expression = w.getCondition().getTree();
                if (expression instanceof J.Parentheses parentheses) {
                    w = (J.WhileLoop) new UnwrapParentheses<>(parentheses).visitNonNull(w, ctx, getCursor().getParentOrThrow());
                }
                return w;
            }

            @Override
            public J visitDoWhileLoop(J.DoWhileLoop doWhileLoop, ExecutionContext ctx) {
                J.DoWhileLoop dw = (J.DoWhileLoop) super.visitDoWhileLoop(doWhileLoop, ctx);
                // Unwrap when while condition is a single parenthesized expression
                Expression expression = dw.getWhileCondition().getTree();
                if (expression instanceof J.Parentheses parentheses) {
                    dw = (J.DoWhileLoop) new UnwrapParentheses<>(parentheses).visitNonNull(dw, ctx, getCursor().getParentOrThrow());
                }
                return dw;
            }

            @Override
            public J visitForControl(J.ForLoop.Control control, ExecutionContext ctx) {
                J.ForLoop.Control fc = (J.ForLoop.Control) super.visitForControl(control, ctx);
                Expression condition = fc.getCondition();
                if (condition instanceof J.Parentheses parentheses) {
                    fc = (J.ForLoop.Control) new UnwrapParentheses<>(parentheses).visitNonNull(fc, ctx, getCursor().getParentOrThrow());
                }
                return fc;
            }
        };
    }

    @Override
    public boolean causesAnotherCycle() {
        return true;
    }
}
