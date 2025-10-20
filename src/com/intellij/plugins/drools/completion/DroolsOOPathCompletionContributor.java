package com.intellij.plugins.drools.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.plugins.drools.lang.psi.*;
import com.intellij.plugins.drools.references.RuleUnitResolver;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DroolsOOPathCompletionContributor extends CompletionContributor {

    public DroolsOOPathCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().afterLeaf("/"),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                            @NotNull ProcessingContext context,
                            @NotNull CompletionResultSet result) {
                        PsiFile file = parameters.getOriginalFile();

                        // Find Rule Unit class
                        PsiClass ruleUnit = RuleUnitResolver.findRuleUnitClass(file);
                        if (ruleUnit == null) return;

                        // Get data sources
                        Map<String, PsiType> dataSources =
                                RuleUnitResolver.getDataSources(ruleUnit);

                        // Add completions
                        for (Map.Entry<String, PsiType> entry : dataSources.entrySet()) {
                            result.addElement(
                                    LookupElementBuilder.create(entry.getKey())
                                            .withTypeText(entry.getValue().getPresentableText())
                            );
                        }
                    }
                });
    }
}
