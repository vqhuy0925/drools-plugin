// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.plugins.drools.lang.psi.util.processors;

import com.intellij.plugins.drools.lang.psi.DroolsFile;
import com.intellij.plugins.drools.lang.psi.DroolsJavaRhsStatement;
import com.intellij.plugins.drools.lang.psi.DroolsSimpleRhsStatement;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class DroolsLocalVariablesProcessor implements DroolsDeclarationsProcessor {
  private static DroolsLocalVariablesProcessor myInstance;

  private DroolsLocalVariablesProcessor() {
  }

  public static DroolsLocalVariablesProcessor getInstance() {
    if (myInstance == null) {
      myInstance = new DroolsLocalVariablesProcessor();
    }
    return myInstance;
  }
  @Override
  public boolean processElement(@NotNull PsiScopeProcessor processor,
                                @NotNull ResolveState state,
                                PsiElement lastParent,
                                @NotNull PsiElement place, @NotNull DroolsFile droolsFile) {
    for (PsiVariable psiVariable : getLocalVariables(place)) {
      if (!processor.execute(psiVariable, state)) return false;
    }
    return true;
  }

  public static @NotNull Set<PsiVariable> getLocalVariables(PsiElement place) {
   final Set<PsiVariable> psiVariables = new HashSet<>();
    DroolsSimpleRhsStatement rhsStatement = PsiTreeUtil.getParentOfType(place, DroolsSimpleRhsStatement.class, false);
    if (rhsStatement != null) {
      PsiElement statement = PsiTreeUtil.getPrevSiblingOfType(rhsStatement, DroolsJavaRhsStatement.class);
      while (statement != null) {
        statement.acceptChildren(new PsiElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            if (element instanceof PsiVariable) {
              psiVariables.add((PsiVariable)element);
            }
            element.acceptChildren(this);
          }
        });

        statement = PsiTreeUtil.getPrevSiblingOfType(statement, DroolsJavaRhsStatement.class);
      }

      rhsStatement.acceptChildren(new PsiElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element instanceof PsiVariable) {
            psiVariables.add((PsiVariable)element);
          }
          if (element instanceof PsiReferenceExpression) return;

          element.acceptChildren(this);
        }
      });
    }
    return psiVariables;
  }
}
