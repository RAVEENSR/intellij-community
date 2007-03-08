/*
 * Copyright 2006-2007 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManualArrayToCollectionCopyInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "manual.array.to.collection.copy.display.name");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "manual.array.to.collection.copy.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ManualArrayToCollectionCopyVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ManualArrayToCollectionCopyFix();
    }

    private static class ManualArrayToCollectionCopyFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "manual.array.to.collection.copy.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement forElement = descriptor.getPsiElement();
            final PsiForStatement forStatement =
                    (PsiForStatement)forElement.getParent();
            final String newExpression = getCollectionsAddAllText(forStatement);
            if (newExpression == null) {
                return;
            }
            replaceStatementAndShortenClassNames(forStatement, newExpression);
        }

        @Nullable
        private static String getCollectionsAddAllText(
                PsiForStatement forStatement)
                throws IncorrectOperationException {
            final PsiExpression expression = forStatement.getCondition();
            final PsiBinaryExpression condition =
                    (PsiBinaryExpression)PsiUtil.deparenthesizeExpression(
                            expression);
            if (condition == null) {
                return null;
            }
            final PsiStatement initialization =
                    forStatement.getInitialization();
            if (initialization == null) {
                return null;
            }
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return null;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            if (declaration.getDeclaredElements().length != 1) {
                return null;
            }
            final PsiLocalVariable variable = (PsiLocalVariable)
                    declaration.getDeclaredElements()[0];
            final PsiExpressionStatement body = getBody(forStatement);
            if (body == null) {
                return null;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)body.getExpression();
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiElement collection = methodExpression.getQualifier();
            if (collection == null) {
                // fixme for when the array is added to 'this'
                return null;
            }
            final String collectionText = collection.getText();
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression argument = argumentList.getExpressions()[0];
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)
                            PsiUtil.deparenthesizeExpression(argument);
            if (arrayAccessExpression == null) {
                return null;
            }
            final PsiExpression arrayExpression =
                    arrayAccessExpression.getArrayExpression();
            final String arrayText = arrayExpression.getText();
            final PsiExpression indexExpression =
                    arrayAccessExpression.getIndexExpression();
            final String fromOffsetText =
                    getStartOffsetText(indexExpression, variable);
            if (fromOffsetText == null) {
                return null;
            }
            PsiExpression limit;
            if (condition.getOperationTokenType() == JavaTokenType.LT)  {
                limit = condition.getROperand();
            } else {
                limit = condition.getLOperand();
            }
            limit = PsiUtil.deparenthesizeExpression(limit);
            if (limit == null) {
                return null;
            }
            @NonNls final String toOffsetText = limit.getText();
            if (toOffsetText == null) {
                return null;
            }
            @NonNls final StringBuilder buffer = new StringBuilder(60);
            buffer.append(collectionText);
            buffer.append(".addAll(java.util.Arrays.asList(");
            buffer.append(arrayText);
            buffer.append(')');
            if (!fromOffsetText.equals("0") &&
                    !toOffsetText.equals(arrayText + ".length")) {
                buffer.append(".subList(");
                buffer.append(fromOffsetText);
                buffer.append(", ");
                buffer.append(toOffsetText);
                buffer.append(')');
            }
            buffer.append(");");
            return buffer.toString();
        }

        @Nullable
        private static String getStartOffsetText(PsiExpression expression,
                                                 PsiLocalVariable variable)
                throws IncorrectOperationException {
            expression =
                    PsiUtil.deparenthesizeExpression(expression);
            if (expression == null) {
                return null;
            }
            final String expressionText = expression.getText();
            final String variableName = variable.getName();
            if (expressionText.equals(variableName)) {
                final PsiExpression initialValue = variable.getInitializer();
                if (initialValue == null) {
                    return null;
                }
                return initialValue.getText();
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                final String rhsText = getStartOffsetText(rhs, variable);
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (ExpressionUtils.isZero(lhs)) {
                    if (tokenType.equals(JavaTokenType.MINUS)) {
                        return '-' + rhsText;
                    }
                    return rhsText;
                }
                final String lhsText = getStartOffsetText(lhs, variable);
                if (ExpressionUtils.isZero(rhs)) {
                    return lhsText;
                }
                return collapseConstant(lhsText + sign.getText() + rhsText,
                        variable);
            }
            return collapseConstant(expression.getText(), variable);
        }

        private static String collapseConstant(String expressionText,
                                               PsiElement context)
                throws IncorrectOperationException {
            final PsiManager manager = context.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiConstantEvaluationHelper evaluationHelper =
                    manager.getConstantEvaluationHelper();
            final PsiExpression fromOffsetExpression =
                    factory.createExpressionFromText(expressionText, context);
            final Object fromOffsetConstant =
                    evaluationHelper.computeConstantExpression(
                            fromOffsetExpression);
            if (fromOffsetConstant != null) {
                return fromOffsetConstant.toString();
            } else {
                return expressionText;
            }
        }

        @Nullable
        private static PsiExpressionStatement getBody(
                PsiLoopStatement forStatement) {
            PsiStatement body = forStatement.getBody();
            while (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                body = statements[0];
            }
            return (PsiExpressionStatement)body;
        }
    }

    private static class ManualArrayToCollectionCopyVisitor
            extends BaseInspectionVisitor {

        public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement initialization =
                    statement.getInitialization();
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            if (declaration.getDeclaredElements().length != 1) {
                return;
            }
            final PsiLocalVariable variable = (PsiLocalVariable)
                    declaration.getDeclaredElements()[0];
            final PsiExpression initialValue = variable.getInitializer();
            if (initialValue == null) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            if (!ExpressionUtils.isComparison(condition, variable)) {
                return;
            }
            final PsiStatement update = statement.getUpdate();
            if (!VariableAccessUtils.variableIsIncremented(variable, update)) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (!bodyIsArrayToCollectionCopy(body, variable)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean bodyIsArrayToCollectionCopy(PsiStatement body,
                                               PsiLocalVariable variable) {
            if (body instanceof PsiExpressionStatement) {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement)body;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                return expressionIsArrayToCollectionCopy(expression, variable);
            } else if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                return statements.length == 1 &&
                        bodyIsArrayToCollectionCopy(statements[0], variable);
            }
            return false;
        }

        private static boolean expressionIsArrayToCollectionCopy(
                PsiExpression expression, PsiLocalVariable variable) {
            final PsiExpression strippedExpression =
                    PsiUtil.deparenthesizeExpression(expression);
            if (strippedExpression == null) {
                return false;
            }
            if (!(strippedExpression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)strippedExpression;
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return false;
            }
            final PsiExpression argument = arguments[0];
            final PsiType argumentType = argument.getType();
            if (argumentType instanceof PsiPrimitiveType) {
                return false;
            }
            if (SideEffectChecker.mayHaveSideEffects(argument)) {
                return false;
            }
            if (!ExpressionUtils.isOffsetArrayAccess(argument, variable)) {
                return false;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return false;
            }
            @NonNls final String name = method.getName();
            if (!name.equals("add")) {
                return false;
            }
            final PsiClass containingClass = method.getContainingClass();
            return ClassUtils.isSubclass(containingClass,
                    "java.util.Collection");
        }
    }
}