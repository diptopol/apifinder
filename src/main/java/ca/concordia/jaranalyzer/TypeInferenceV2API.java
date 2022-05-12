package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.models.typeInfo.FormalTypeParameterInfo;
import ca.concordia.jaranalyzer.models.typeInfo.ParameterizedTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.QualifiedTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;

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
                getAllEnclosingClassList(methodInvocation, dependentArtifactSet, javaVersion, importStatementList));

        owningClassInfo.setAccessibleFormalTypeParameterList(getAccessibleFormalTypeParameterList(dependentArtifactSet,
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
                getAllEnclosingClassList(superMethodInvocation, dependentArtifactSet, javaVersion, importStatementList));

        owningClassInfo.setAccessibleFormalTypeParameterList(getAccessibleFormalTypeParameterList(dependentArtifactSet,
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

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion, importStatementList));

        owningClassInfo.setAccessibleFormalTypeParameterList(getAccessibleFormalTypeParameterList(dependentArtifactSet,
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
                getAllEnclosingClassList(constructorInvocation, dependentArtifactSet, javaVersion, importStatementList));

        owningClassInfo.setAccessibleFormalTypeParameterList(getAccessibleFormalTypeParameterList(dependentArtifactSet,
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
                getAllEnclosingClassList(superConstructorInvocation, dependentArtifactSet, javaVersion, importStatementList));

        owningClassInfo.setAccessibleFormalTypeParameterList(getAccessibleFormalTypeParameterList(dependentArtifactSet,
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

    private static List<FormalTypeParameterInfo> getAccessibleFormalTypeParameterList(Set<Artifact> dependentArtifactSet,
                                                                                      String javaVersion,
                                                                                      List<String> importStatementList,
                                                                                      OwningClassInfo owningClassInfo,
                                                                                      ASTNode methodNode) {
        ASTNode node = methodNode;
        List<FormalTypeParameterInfo> accessibleFormalTypeParameterList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof TypeDeclaration) {
                TypeDeclaration typeDeclaration = (TypeDeclaration) node;
                String qualifiedClassName =
                        InferenceUtility.getDeclaringClassQualifiedName(typeDeclaration).replaceAll("\\$", ".");

                TypeInfo classTypeInfo = InferenceUtility.getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                        importStatementList, qualifiedClassName, owningClassInfo);

                if (Objects.nonNull(classTypeInfo) && classTypeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) classTypeInfo;

                    List<FormalTypeParameterInfo> formalTypeArgumentList = parameterizedTypeInfo.getTypeArgumentList().stream()
                            .filter(TypeInfo::isFormalTypeParameterInfo)
                            .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                            .collect(Collectors.toList());

                    accessibleFormalTypeParameterList.addAll(formalTypeArgumentList);
                }

            } else if (node instanceof MethodDeclaration) {
                MethodDeclaration methodDeclaration = (MethodDeclaration) node;
                List<TypeParameter> typeParameterList = methodDeclaration.typeParameters();

                for (TypeParameter typeParameter: typeParameterList) {
                    List<Type> typeList = typeParameter.typeBounds();

                    if (typeList.isEmpty()) {
                        accessibleFormalTypeParameterList.add(
                                new FormalTypeParameterInfo(typeParameter.getName().getFullyQualifiedName(),
                                        new QualifiedTypeInfo("java.lang.Object")));

                    } else {
                        TypeInfo baseType = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                                typeList.get(0), owningClassInfo);

                        if (Objects.isNull(baseType) && typeList.get(0).isSimpleType()) {
                            String typeName = ((SimpleType) typeList.get(0)).getName().getFullyQualifiedName();
                            baseType = new FormalTypeParameterInfo(typeName, new QualifiedTypeInfo("java.lang.Object"));
                        }

                        accessibleFormalTypeParameterList.add(
                                new FormalTypeParameterInfo(typeParameter.getName().getFullyQualifiedName(), baseType));
                    }
                }
            }

            node = node.getParent();
        }

        return accessibleFormalTypeParameterList;
    }

    private static List<String> getEnclosingClassList(ASTNode methodNode) {
        ASTNode node = methodNode;

        List<String> enclosingClassNameList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) node;

                String className = InferenceUtility.getDeclaringClassQualifiedName(abstractTypeDeclaration);
                enclosingClassNameList.add(className.replaceAll("\\$", "."));
            }

            node = node.getParent();
        }

        return enclosingClassNameList;
    }

    private static String getClassInstanceCreationQualifiedName(ASTNode node,
                                                                Set<Artifact> dependentArtifactSet,
                                                                String javaVersion,
                                                                List<String> importStatementList) {

        ClassInstanceCreation classInstanceCreation =
                (ClassInstanceCreation) InferenceUtility.getClosestASTNode(node.getParent(), ClassInstanceCreation.class);

        if (Objects.isNull(classInstanceCreation)) {
            return null;
        }

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion, importStatementList));

        owningClassInfo.setAccessibleFormalTypeParameterList(
                getAccessibleFormalTypeParameterList(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfo, classInstanceCreation));

        Type type = classInstanceCreation.getType();
        TypeInfo classTypeInfo = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                importStatementList, type, owningClassInfo);

        assert Objects.nonNull(classTypeInfo);

        return classTypeInfo.getQualifiedClassName();
    }

    /*
     * Enclosing class list will consist of anonymous inner class and enclosing class declarations.
     */
    private static List<String> getAllEnclosingClassList(ASTNode node,
                                                         Set<Artifact> dependentArtifactSet,
                                                         String javaVersion,
                                                         List<String> importStatementList) {

        List<String> enclosingClassList = new ArrayList<>();

        String qualifiedClassName =
                getClassInstanceCreationQualifiedName(node, dependentArtifactSet, javaVersion, importStatementList);

        if (Objects.nonNull(qualifiedClassName)) {
            enclosingClassList.add(qualifiedClassName);
        }

        enclosingClassList.addAll(getEnclosingClassList(node));

        return enclosingClassList;
    }

}
