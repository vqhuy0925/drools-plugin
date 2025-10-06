// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.plugins.drools.lang.psi.util.processors;

import com.intellij.plugins.drools.lang.psi.DroolsFile;
import com.intellij.plugins.drools.lang.psi.util.DroolsResolveUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

public final class DroolsImportedPackagesProcessor implements DroolsDeclarationsProcessor {

  private static DroolsImportedPackagesProcessor myInstance;

  private DroolsImportedPackagesProcessor() {
  }

  public static DroolsImportedPackagesProcessor getInstance() {
    if (myInstance == null) {
      myInstance = new DroolsImportedPackagesProcessor();
    }
    return myInstance;
  }

  @Override
  public boolean processElement(@NotNull PsiScopeProcessor processor,
                                @NotNull ResolveState state,
                                PsiElement lastParent,
                                @NotNull PsiElement place, @NotNull DroolsFile droolsFile) {
    final PsiPackage currentPackage = DroolsResolveUtil.getCurrentPsiPackage(droolsFile);
    if (currentPackage != null) {
      if (!currentPackage.processDeclarations(processor, state, lastParent, place)) return false;
    }
    final PsiPackage topPackage = DroolsResolveUtil.getTopPackage(droolsFile.getProject());
    if (topPackage != null) {
      if (!topPackage.processDeclarations(processor, state, lastParent, place)) return false;
    }
    final PsiPackage javaLangPackage = DroolsResolveUtil.getJavaLangPackage(droolsFile.getProject());
    if (javaLangPackage != null) {
      if (!javaLangPackage.processDeclarations(processor, state, lastParent, place)) return false;
    }
    for (PsiPackage psiPackage : DroolsResolveUtil.getExplicitlyImportedPackages(droolsFile)) {
      if (psiPackage != null) {
        if (!psiPackage.processDeclarations(processor, state, lastParent, place)) return false;
        for (PsiClass aClass : psiPackage.getClasses(DroolsResolveUtil.getSearchScope(droolsFile))) {
          if (!aClass.processDeclarations(processor, state, lastParent, place)) return false;
        }
      }
    }
    return true;
  }
}
