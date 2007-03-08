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
package com.siyeh.ig.bugs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class NullArgumentToVariableArgMethodInspection
        extends BaseInspection {

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "null.argument.to.var.arg.method.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "null.argument.to.var.arg.method.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NullArgumentToVariableArgVisitor();
    }

    private static class NullArgumentToVariableArgVisitor
            extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);

            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
            if(languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0){
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if(args.length == 0){
                return;
            }
            final PsiExpression lastArg = args[args.length - 1];
            if(!ExpressionUtils.isNullLiteral(lastArg)){
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters.length != args.length){
                return;
            }
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if(!lastParameter.isVarArgs()){
                return;
            }
            registerError(lastArg);
        }
    }
}