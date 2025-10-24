package com.intellij.plugins.drools.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.plugins.drools.lang.psi.*;
import com.intellij.plugins.drools.references.OOPathContextAnalyzer;
import com.intellij.plugins.drools.references.RuleUnitResolver;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DroolsOOPathCompletionContributor extends CompletionContributor {

    private static final Logger LOG = LoggerFactory.getLogger(DroolsOOPathCompletionContributor.class);

    public DroolsOOPathCompletionContributor() {
        // Complete properties in OOPath constraints (including nested)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new NestedPropertyCompletionProvider());
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

    /**
     * Completes nested properties: age, address.city, address.state.code
     */
    private static class NestedPropertyCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                @NotNull ProcessingContext context,
                @NotNull CompletionResultSet result) {

            PsiElement position = parameters.getPosition();

            // Only complete inside OOPath constraints
            if (!isInOOPathConstraint(position)) {
                return;
            }

            LOG.info("=== Nested property completion triggered ===");

            try {
                // Resolve type at cursor position
                PsiType type = OOPathContextAnalyzer.resolveTypeAtCursor(position);
                if (type == null) {
                    LOG.warn("Could not resolve type at cursor");
                    return;
                }

                LOG.info("Resolved type: " + type.getPresentableText());

                // Get class from type
                PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
                if (psiClass == null) {
                    LOG.warn("Could not get PsiClass from type");
                    return;
                }

                // Get all properties
                List<PropertyInfo> properties = getProperties(psiClass);
                LOG.info("Found " + properties.size() + " properties");

                // Add completion items
                for (PropertyInfo property : properties) {
                    LOG.debug("Adding property: " + property.name + " : " +
                            property.type.getPresentableText());

                    result.addElement(
                            LookupElementBuilder.create(property.name)
                                    .withTypeText(property.type.getPresentableText())
                                    .withIcon(AllIcons.Nodes.Property)
                    );
                }

            } catch (Exception e) {
                LOG.error("Error in nested property completion", e);
            }
        }

        /**
         * Checks if cursor is inside OOPath constraint [...]
         */
        private boolean isInOOPathConstraint(@NotNull PsiElement element) {
            try {
                String text = element.getContainingFile().getText();
                int offset = element.getTextOffset();

                // Look backwards for '/'
                int lastSlash = text.lastIndexOf('/', offset);
                if (lastSlash == -1) {
                    return false;
                }

                // Look for '[' after '/' and before cursor
                int lastBracket = text.lastIndexOf('[', offset);
                if (lastBracket <= lastSlash) {
                    return false;
                }

                // Check if there's a closing ']' before cursor
                int nextCloseBracket = text.indexOf(']', offset);

                // We're in constraint if:
                // - There's a '[' after '/' and before cursor
                // - There's no ']' between '[' and cursor, OR ']' is after cursor
                return nextCloseBracket == -1 || nextCloseBracket > offset;

            } catch (Exception e) {
                LOG.error("Error checking OOPath constraint", e);
                return false;
            }
        }

        /**
         * Gets all accessible properties from a class
         */
        @NotNull
        private List<PropertyInfo> getProperties(@NotNull PsiClass psiClass) {
            List<PropertyInfo> properties = new ArrayList<>();

            try {
                // Get properties from getter methods
                for (PsiMethod method : psiClass.getAllMethods()) {
                    if (isGetter(method)) {
                        String propertyName = getPropertyName(method);
                        PsiType returnType = method.getReturnType();

                        if (returnType != null) {
                            properties.add(new PropertyInfo(propertyName, returnType));
                        }
                    }
                }

                // Get public fields
                for (PsiField field : psiClass.getAllFields()) {
                    if (field.hasModifierProperty(PsiModifier.PUBLIC)) {
                        properties.add(new PropertyInfo(
                                field.getName(),
                                field.getType()
                        ));
                    }
                }

                // Remove duplicates and Object methods
                properties = properties.stream()
                        .filter(p -> !isObjectMethod(p.name))
                        .distinct()
                        .collect(Collectors.toList());

            } catch (Exception e) {
                LOG.error("Error getting properties", e);
            }

            return properties;
        }

        private boolean isGetter(@NotNull PsiMethod method) {
            String name = method.getName();
            return (name.startsWith("get") || name.startsWith("is")) &&
                    method.getParameterList().isEmpty() &&
                    method.getReturnType() != null &&
                    !method.hasModifierProperty(PsiModifier.STATIC);
        }

        @NotNull
        private String getPropertyName(@NotNull PsiMethod method) {
            String name = method.getName();
            String propertyName;

            if (name.startsWith("get")) {
                propertyName = name.substring(3);
            } else if (name.startsWith("is")) {
                propertyName = name.substring(2);
            } else {
                return name;
            }

            // Decapitalize first letter
            if (propertyName.length() > 0) {
                propertyName = Character.toLowerCase(propertyName.charAt(0)) +
                        propertyName.substring(1);
            }

            return propertyName;
        }

        private boolean isObjectMethod(@NotNull String name) {
            return name.equals("class") ||
                    name.equals("toString") ||
                    name.equals("hashCode") ||
                    name.equals("equals") ||
                    name.equals("wait") ||
                    name.equals("notify") ||
                    name.equals("notifyAll");
        }
    }

    /**
     * Property information container
     */
    private static class PropertyInfo {
        final String name;
        final PsiType type;

        PropertyInfo(String name, PsiType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PropertyInfo)) return false;
            PropertyInfo that = (PropertyInfo) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
