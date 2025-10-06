// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.plugins.drools.lang.psi.util.processors;

import com.intellij.openapi.project.Project;
import com.intellij.plugins.drools.lang.psi.DroolsFile;
import com.intellij.plugins.drools.lang.psi.DroolsImport;
import com.intellij.plugins.drools.lang.psi.util.DroolsLightClass;
import com.intellij.plugins.drools.lang.psi.util.DroolsResolveUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class DroolsImportedClassesProcessor implements DroolsDeclarationsProcessor {
  private static DroolsImportedClassesProcessor myInstance;

  private DroolsImportedClassesProcessor() {
  }

  public static DroolsImportedClassesProcessor getInstance() {
    if (myInstance == null) {
      myInstance = new DroolsImportedClassesProcessor();
    }
    return myInstance;
  }

  @Override
  public boolean processElement(@NotNull PsiScopeProcessor processor,
                                @NotNull ResolveState state,
                                PsiElement lastParent,
                                @NotNull PsiElement place, @NotNull DroolsFile droolsFile) {
    return processImportedClasses(processor, state, droolsFile.getImports(), place.getProject(),
                                  DroolsResolveUtil.getSearchScope(droolsFile));
  }

  private static boolean processImportedClasses(PsiScopeProcessor processor, ResolveState state, DroolsImport[] imports, Project project,
                                                GlobalSearchScope scope) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    for (DroolsImport droolsImport : imports) {
      String className = droolsImport.getImportedClassName();
      if (className != null) {
        PsiClass psiClass = facade.findClass(className, scope);
        if (psiClass != null && !processor.execute(new DroolsLightClass(psiClass), state)) {
          return false;
        }
      }
    }
    return true;
  }
}
