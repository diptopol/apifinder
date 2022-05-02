package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * @author Diptopol
 * @since 9/24/2021 4:25 PM
 */
public class TypeInferenceV2API {

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           MethodInvocation methodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(methodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getEnclosingClassList(methodInvocation));

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        methodInvocation, owningClassInfo);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                methodInvocation, importStatementList, variableNameMap, owningClassInfo);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getEnclosingClassList(superMethodInvocation));

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        superMethodInvocation, owningClassInfo);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                superMethodInvocation, importStatementList, variableNameMap, owningClassInfo);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getEnclosingClassList(classInstanceCreation));

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        classInstanceCreation, owningClassInfo);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                classInstanceCreation, importStatementList, variableNameMap, owningClassInfo);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                    String javaVersion,
                                    ConstructorInvocation constructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getEnclosingClassList(constructorInvocation));

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                        constructorInvocation, owningClassInfo);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(constructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String invokerClassName = className.replace("%", "").replace("$", ".");

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
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerClassName(invokerClassName)
                .setOwningClassInfo(owningClassInfo);

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

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getEnclosingClassList(superConstructorInvocation));

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion,
                        importStatementList, superConstructorInvocation, owningClassInfo);

        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
        Type superClassType = typeDeclaration.getSuperclassType();
        String superClassName;

        if (superClassType == null) {
            superClassName = "java.lang.Object";
        } else {
            superClassName = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                    importStatementList, superClassType, owningClassInfo).getQualifiedClassName();
        }

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superConstructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String invokerClassName = className.replace("%", "").replace("#", ".");

        List<Expression> argumentList = superConstructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, superClassName, numberOfParameters)
                .setInvokerClassName(invokerClassName)
                .setOwningClassInfo(owningClassInfo)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    private static List<String> getEnclosingClassList(ASTNode methodNode) {
        ASTNode node = methodNode;
        AbstractTypeDeclaration abstractTypeDeclaration = null;

        List<String> enclosingClassNameList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof AbstractTypeDeclaration) {
                abstractTypeDeclaration = (AbstractTypeDeclaration) node;

                String className = InferenceUtility.getDeclaringClassQualifiedName(abstractTypeDeclaration);
                enclosingClassNameList.add(className.replaceAll("\\$", "."));
            }

            node = node.getParent();
        }

        return enclosingClassNameList;
    }

    private static String getOwningClassQualifiedName(ASTNode methodNode) {
        ASTNode node = methodNode;
        AbstractTypeDeclaration abstractTypeDeclaration = null;

        while (Objects.nonNull(node)) {
            if (node instanceof AbstractTypeDeclaration) {
                abstractTypeDeclaration = (AbstractTypeDeclaration) node;
            }

            node = node.getParent();
        }

        return InferenceUtility.getDeclaringClassQualifiedName(abstractTypeDeclaration);
    }

}
