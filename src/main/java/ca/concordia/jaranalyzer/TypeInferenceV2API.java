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
                InferenceUtility.getAllEnclosingClassList(methodInvocation, dependentArtifactSet, javaVersion, importStatementList),
                Collections.emptyList());

        owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                javaVersion, importStatementList, owningClassInfo, methodInvocation));

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
                InferenceUtility.getAllEnclosingClassList(superMethodInvocation, dependentArtifactSet, javaVersion, importStatementList),
                Collections.emptyList());

        owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                javaVersion, importStatementList, owningClassInfo, superMethodInvocation));

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

        List<String> enclosingClassQNameList =
                InferenceUtility.getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion, importStatementList);

        List<String> nonEnclosingAccessibleClassQNameList =
                getNonEnclosingAccessibleClassListForInstantiation(classInstanceCreation, enclosingClassQNameList);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                enclosingClassQNameList, nonEnclosingAccessibleClassQNameList);

        owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                javaVersion, importStatementList, owningClassInfo, classInstanceCreation));

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
                InferenceUtility.getAllEnclosingClassList(constructorInvocation, dependentArtifactSet, javaVersion, importStatementList),
                Collections.emptyList());

        owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                javaVersion, importStatementList, owningClassInfo, constructorInvocation));

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
                InferenceUtility.getAllEnclosingClassList(superConstructorInvocation, dependentArtifactSet, javaVersion, importStatementList),
                Collections.emptyList());

        owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                javaVersion, importStatementList, owningClassInfo, superConstructorInvocation));

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

        List<Expression> argumentList = superConstructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, superClassName, numberOfParameters)
                .setOwningClassInfo(owningClassInfo)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    private static List<String> getNonEnclosingAccessibleClassListForInstantiation(ASTNode methodNode,
                                                                                   List<String> enclosingQClassNameList) {

        AbstractTypeDeclaration topMostAbstractTypeDeclaration = null;
        ASTNode node = methodNode;

        while (Objects.nonNull(node)) {
            if (node instanceof AbstractTypeDeclaration) {
                topMostAbstractTypeDeclaration = (AbstractTypeDeclaration) node;
            }

            node = node.getParent();
        }

        List<String> innerQNameList = InferenceUtility.getInnerClassQNameList(topMostAbstractTypeDeclaration);

        innerQNameList.removeIf(enclosingQClassNameList::contains);

        return innerQNameList;
    }

}
