package com.intellij.plugins.drools.references;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes OOPath context to resolve types for nested property access
 */
public class OOPathContextAnalyzer {
    private static final Logger LOG = Logger.getInstance(OOPathContextAnalyzer.class);

    /**
     * Resolves the type at cursor position in OOPath expression
     *
     * Examples:
     *   /persons[<cursor>           → Person type
     *   /persons[address.<cursor>   → Address type
     *   /persons[address.city.<cursor> → String type (if city returns String)
     */
    @Nullable
    public static PsiType resolveTypeAtCursor(@NotNull PsiElement position) {
        LOG.info("=== Resolving type at cursor ===");

        try {
            // Get the full text before cursor
            String textBefore = getTextBeforeCursor(position);
            LOG.info("Text before cursor: '" + textBefore + "'");

            // Parse the OOPath expression
            OOPathInfo pathInfo = parseOOPath(textBefore);
            if (pathInfo == null) {
                LOG.warn("Failed to parse OOPath expression");
                return null;
            }

            LOG.info("Data source: " + pathInfo.dataSourceName);
            LOG.info("Property path: " + pathInfo.propertyPath);

            // Find the Rule Unit class
            PsiFile file = position.getContainingFile();
            PsiClass ruleUnit = RuleUnitResolver.findRuleUnitClass(file);
            if (ruleUnit == null) {
                LOG.warn("Rule Unit not found");
                return null;
            }
            LOG.info("Rule Unit: " + ruleUnit.getQualifiedName());

            // Get initial type from data source
            PsiType currentType = getDataSourceType(ruleUnit, pathInfo.dataSourceName);
            if (currentType == null) {
                LOG.warn("Data source type not found: " + pathInfo.dataSourceName);
                return null;
            }
            LOG.info("Initial type: " + currentType.getPresentableText());

            // Navigate through property chain
            for (int i = 0; i < pathInfo.propertyPath.size(); i++) {
                String property = pathInfo.propertyPath.get(i);
                LOG.info("Navigating to property [" + i + "]: " + property);

                currentType = getPropertyType(currentType, property);
                if (currentType == null) {
                    LOG.warn("Property not found: " + property);
                    return null;
                }
                LOG.info("  -> Type: " + currentType.getPresentableText());
            }

            LOG.info("=== Final resolved type: " + currentType.getPresentableText() + " ===");
            return currentType;

        } catch (Exception e) {
            LOG.error("Error resolving type at cursor", e);
            return null;
        }
    }

    /**
     * Gets text from start of OOPath expression to cursor
     */
    @NotNull
    private static String getTextBeforeCursor(@NotNull PsiElement position) {
        StringBuilder text = new StringBuilder();

        try {
            // Get text of current element
            text.append(position.getText());

            // Walk backwards to find the start of OOPath expression (/)
            PsiElement current = position.getPrevSibling();
            int maxSteps = 50; // Prevent infinite loops
            int steps = 0;

            while (current != null && steps < maxSteps) {
                String currentText = current.getText();
                text.insert(0, currentText);

                // Stop if we found the start of when clause or rule
                if (currentText.contains("when") || currentText.contains("rule")) {
                    break;
                }

                current = current.getPrevSibling();
                steps++;
            }

        } catch (Exception e) {
            LOG.error("Error getting text before cursor", e);
        }

        return text.toString();
    }

    /**
     * Parses OOPath expression to extract data source and property path
     *
     * Examples:
     *   "/persons[age"           → dataSource="persons", path=[]
     *   "/persons[address.city"  → dataSource="persons", path=["address"]
     *   "/persons[address.city.length" → dataSource="persons", path=["address", "city"]
     */
    @Nullable
    private static OOPathInfo parseOOPath(@NotNull String text) {
        try {
            // Find last '/' before cursor
            int lastSlash = text.lastIndexOf('/');
            if (lastSlash == -1) {
                LOG.debug("No '/' found in text");
                return null;
            }

            // Extract everything after '/'
            String afterSlash = text.substring(lastSlash + 1);
            LOG.debug("After slash: '" + afterSlash + "'");

            // Find '[' that starts constraints
            int bracketIndex = afterSlash.indexOf('[');
            if (bracketIndex == -1) {
                // Just data source name, no constraints yet
                String dataSource = afterSlash.trim();
                return new OOPathInfo(dataSource, new ArrayList<>());
            }

            // Extract data source name
            String dataSource = afterSlash.substring(0, bracketIndex).trim();
            LOG.debug("Data source: '" + dataSource + "'");

            // Extract constraints part
            String constraints = afterSlash.substring(bracketIndex + 1);
            LOG.debug("Constraints: '" + constraints + "'");

            // Parse property path from constraints
            List<String> propertyPath = parsePropertyPath(constraints);

            return new OOPathInfo(dataSource, propertyPath);

        } catch (Exception e) {
            LOG.error("Error parsing OOPath", e);
            return null;
        }
    }

    /**
     * Parses property path from constraint expression
     *
     * Examples:
     *   "age > 18"                    → []
     *   "address.city"                → ["address"]
     *   "address.city == 'NYC'"       → ["address"]
     *   "age > 18, address.city"      → ["address"]
     *   "address.city.length"         → ["address", "city"]
     */
    @NotNull
    private static List<String> parsePropertyPath(@NotNull String constraints) {
        List<String> path = new ArrayList<>();

        try {
            // Find the last comma (to handle multiple constraints)
            int lastComma = constraints.lastIndexOf(',');
            String currentConstraint = lastComma != -1
                    ? constraints.substring(lastComma + 1)
                    : constraints;

            LOG.debug("Current constraint: '" + currentConstraint + "'");

            // Remove operators and values
            // Split by common operators: ==, !=, >, <, >=, <=
            String propertyPart = currentConstraint
                    .split("==|!=|>=|<=|>|<")[0]
                    .trim();

            LOG.debug("Property part: '" + propertyPart + "'");

            // Split by dots to get property chain
            if (!propertyPart.isEmpty()) {
                String[] parts = propertyPart.split("\\.");

                // Add all parts except the last one (cursor is after last dot)
                for (int i = 0; i < parts.length - 1; i++) {
                    String part = parts[i].trim();
                    if (!part.isEmpty() && isValidPropertyName(part)) {
                        path.add(part);
                    }
                }
            }

            LOG.debug("Parsed property path: " + path);

        } catch (Exception e) {
            LOG.error("Error parsing property path", e);
        }

        return path;
    }

    /**
     * Checks if string is a valid property name
     */
    private static boolean isValidPropertyName(@NotNull String name) {
        if (name.isEmpty()) {
            return false;
        }

        // Must start with letter
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }

        // Rest must be valid identifier characters
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the type of a data source field from Rule Unit
     */
    @Nullable
    private static PsiType getDataSourceType(@NotNull PsiClass ruleUnit,
            @NotNull String fieldName) {
        try {
            // Get all data sources
            var dataSources = RuleUnitResolver.getDataSources(ruleUnit);
            PsiType type = dataSources.get(fieldName);

            if (type == null) {
                LOG.warn("Data source not found: " + fieldName);
            }

            return type;

        } catch (Exception e) {
            LOG.error("Error getting data source type", e);
            return null;
        }
    }

    /**
     * Gets the type of a property from a class
     * Handles getters, fields, and methods
     */
    @Nullable
    private static PsiType getPropertyType(@NotNull PsiType type,
            @NotNull String propertyName) {
        try {
            PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
            if (psiClass == null) {
                LOG.warn("Cannot resolve class from type: " + type.getPresentableText());
                return null;
            }

            LOG.debug("Looking for property '" + propertyName + "' in " +
                    psiClass.getQualifiedName());

            // Try getter method first (getName, isActive)
            String getterName = "get" + capitalize(propertyName);
            PsiMethod[] getMethods = psiClass.findMethodsByName(getterName, true);
            if (getMethods.length > 0) {
                PsiType returnType = getMethods[0].getReturnType();
                LOG.debug("Found getter: " + getterName + " -> " +
                        (returnType != null ? returnType.getPresentableText() : "null"));
                return returnType;
            }

            // Try boolean getter (isActive)
            String booleanGetterName = "is" + capitalize(propertyName);
            PsiMethod[] isMethods = psiClass.findMethodsByName(booleanGetterName, true);
            if (isMethods.length > 0) {
                PsiType returnType = isMethods[0].getReturnType();
                LOG.debug("Found boolean getter: " + booleanGetterName + " -> " +
                        (returnType != null ? returnType.getPresentableText() : "null"));
                return returnType;
            }

            // Try field directly
            PsiField field = psiClass.findFieldByName(propertyName, true);
            if (field != null) {
                PsiType fieldType = field.getType();
                LOG.debug("Found field: " + propertyName + " -> " +
                        fieldType.getPresentableText());
                return fieldType;
            }

            LOG.warn("Property not found: " + propertyName + " in " +
                    psiClass.getQualifiedName());
            return null;

        } catch (Exception e) {
            LOG.error("Error getting property type for: " + propertyName, e);
            return null;
        }
    }

    private static String capitalize(@NotNull String str) {
        if (str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Container for parsed OOPath information
     */
    private static class OOPathInfo {
        final String dataSourceName;
        final List<String> propertyPath;

        OOPathInfo(String dataSourceName, List<String> propertyPath) {
            this.dataSourceName = dataSourceName;
            this.propertyPath = propertyPath;
        }
    }
}
