package com.intellij.plugins.drools.references;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleUnitResolver {

    private static final Pattern UNIT_PATTERN =
            Pattern.compile("unit\\s+([A-Za-z0-9_.]+)\\s*");

    private RuleUnitResolver() {
        // hide constructor
    }
    /**
     * Find the Rule Unit class from a DRL file
     */
    @Nullable
    public static PsiClass findRuleUnitClass(@NotNull PsiFile drlFile) {
        String unitName = extractUnitName(drlFile);
        if (unitName == null) return null;

        Project project = drlFile.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        // Try to find with package from DRL file
        String packageName = extractPackageName(drlFile);
        if (packageName != null) {
            PsiClass withPackage = facade.findClass(packageName + "." + unitName, scope);
            if (withPackage != null) return withPackage;
        }

        // Try without package
        return facade.findClass(unitName, scope);
    }

    @Nullable
    private static String extractUnitName(@NotNull PsiFile file) {
        String text = file.getText();
        Matcher matcher = UNIT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Nullable
    private static String extractPackageName(@NotNull PsiFile file) {
        String text = file.getText();
        Pattern pattern = Pattern.compile("package\\s+([A-Za-z0-9_.]+)\\s*");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Get all DataSource fields from Rule Unit
     */
    @NotNull
    public static Map<String, PsiType> getDataSources(@NotNull PsiClass ruleUnitClass) {
        Map<String, PsiType> result = new HashMap<>();
        PsiField[] allFields = ruleUnitClass.getAllFields();

        for (PsiField field : allFields) {

            PsiType type = field.getType();

            if (isDataSourceType(type)) {
                PsiType genericType = extractGenericType(type);
                if (genericType != null) {
                    result.put(field.getName(), genericType);
                }
            }
        }

        return result;
    }

    private static boolean isDataSourceType(PsiType type) {
        if (!(type instanceof PsiClassType)) return false;

        PsiClass psiClass = ((PsiClassType) type).resolve();
        if (psiClass == null) return false;

        String fqn = psiClass.getQualifiedName();
        return "org.drools.ruleunits.api.DataStore".equals(fqn) ||
                "org.drools.ruleunits.api.DataStream".equals(fqn);
    }

    @Nullable
    private static PsiType extractGenericType(PsiType type) {
        if (!(type instanceof PsiClassType)) return null;
        PsiType[] params = ((PsiClassType) type).getParameters();

        if (params.length > 0) {
            return params[0];
        }
        return null;
    }
}
