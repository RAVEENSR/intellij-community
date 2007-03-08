/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class AwaitWithoutCorrespondingSignalInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "await.without.corresponding.signal.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "await.without.corresponding.signal.problem.descriptor");
    }
    public BaseInspectionVisitor buildVisitor() {
        return new AwaitWithoutCorrespondingSignalVisitor();
    }

    private static class AwaitWithoutCorrespondingSignalVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!ThreadingUtils.isAwaitCall(expression)) {
                return;
            }

            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent = ((PsiReference) qualifier).resolve();
            if (!(referent instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) referent;
            final PsiClass fieldClass = field.getContainingClass();
            if (fieldClass == null) {
                return;
            }
            if (!PsiTreeUtil.isAncestor(fieldClass, expression, true)) {
                return;
            }
            if (containsSignalCall(fieldClass, field)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean containsSignalCall(PsiClass fieldClass,
                                                  PsiField field) {
            final ContainsSignalVisitor visitor =
                    new ContainsSignalVisitor(field);
            fieldClass.accept(visitor);
            return visitor.containsSignal();
        }
    }

    private static class ContainsSignalVisitor
            extends PsiRecursiveElementVisitor {

        private PsiField target;
        private boolean containsSignal = false;

        ContainsSignalVisitor(PsiField target) {
            super();
            this.target = target;
        }

        public void visitElement(PsiElement element) {
            if (containsSignal) {
                return;
            }
            super.visitElement(element);
        }

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!ThreadingUtils.isSignalOrSignalAllCall(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent = ((PsiReference) qualifier).resolve();
            if (referent == null) {
                return;
            }
            if (!target.equals(referent)) {
                return;
            }
            containsSignal = true;
        }

        public boolean containsSignal() {
            return containsSignal;
        }
    }
}