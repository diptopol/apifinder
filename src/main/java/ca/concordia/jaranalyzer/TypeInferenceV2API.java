package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import ca.concordia.jaranalyzer.util.artifactextraction.Artifact;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Diptopol
 * @since 9/24/2021 4:25 PM
 */
public class TypeInferenceV2API {

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           MethodInvocation methodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(methodInvocation);
        String owningClassQualifiedName = getOwingClassQualifiedName(methodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit, methodInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        methodInvocation, owningClassQualifiedName);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                methodInvocation, importStatementList, variableNameMap, owningClassQualifiedName);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);
        String owningClassQualifiedName = getOwingClassQualifiedName(superMethodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit, superMethodInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        superMethodInvocation, owningClassQualifiedName);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                superMethodInvocation, importStatementList, variableNameMap, owningClassQualifiedName);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);
        String owingClassQualifiedName = getOwingClassQualifiedName(classInstanceCreation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit, classInstanceCreation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        classInstanceCreation, owingClassQualifiedName);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                classInstanceCreation, importStatementList, variableNameMap, owingClassQualifiedName);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                    String javaVersion,
                                    ConstructorInvocation constructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);
        String owingClassQualifiedName = getOwingClassQualifiedName(constructorInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit, constructorInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        constructorInvocation, owingClassQualifiedName);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(constructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("$", ".");

        String methodName;

        if (className.contains("%.")) {
            methodName = className.substring(className.lastIndexOf("%.") + 2);
        } else if (className.contains(".")) {
            methodName = className.substring(className.lastIndexOf(".") + 1);
        } else {
            methodName = className;
        }

        methodName = methodName.replace("$", ".");

        List<Expression> argumentList = constructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owingClassQualifiedName);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owingClassQualifiedName);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                    String javaVersion,
                                    SuperConstructorInvocation superConstructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superConstructorInvocation);
        String owingClassQualifiedName = getOwingClassQualifiedName(superConstructorInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit, superConstructorInvocation);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion,
                        importStatementList, superConstructorInvocation, owingClassQualifiedName);

        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
        Type superClassType = typeDeclaration.getSuperclassType();
        String superClassName;

        if (superClassType == null) {
            superClassName = "java.lang.Object";
        } else {
            superClassName = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                    importStatementList, superClassType, owingClassQualifiedName).getQualifiedClassName();
        }

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superConstructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        List<Expression> argumentList = superConstructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owingClassQualifiedName);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, superClassName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owingClassQualifiedName)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    private static  String getOwingClassQualifiedName(ASTNode methodNode) {
        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) InferenceUtility.getAbstractTypeDeclaration(methodNode);

        return InferenceUtility.getDeclaringClassQualifiedName(abstractTypeDeclaration);
    }

}
