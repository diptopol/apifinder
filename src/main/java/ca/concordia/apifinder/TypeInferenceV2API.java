package ca.concordia.apifinder;

import ca.concordia.apifinder.entity.MethodInfo;
import ca.concordia.apifinder.models.Artifact;
import ca.concordia.apifinder.models.MethodInfoResult;
import ca.concordia.apifinder.models.OwningClassInfo;
import ca.concordia.apifinder.models.VariableDeclarationDto;
import ca.concordia.apifinder.models.typeInfo.TypeInfo;
import ca.concordia.apifinder.service.ClassInfoService;
import ca.concordia.apifinder.service.JarInfoService;
import ca.concordia.apifinder.util.AuditInfo;
import ca.concordia.apifinder.util.InferenceUtility;
import org.eclipse.jdt.core.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Diptopol
 * @since 9/24/2021 4:25 PM
 */
public class TypeInferenceV2API {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceV2API.class);

    private static JarInfoService jarInfoService;
    private static ClassInfoService classInfoService;

    static {
        jarInfoService = new JarInfoService();
        classInfoService = new ClassInfoService();
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           MethodInvocation methodInvocation) {

        MethodInfoResult methodInfoResult = getMethodInfoResult(dependentArtifactSet, javaVersion, methodInvocation);

        return Objects.nonNull(methodInfoResult) ? methodInfoResult.getMethodInfo() : null;
    }

    public static MethodInfoResult getMethodInfoResult(Set<Artifact> dependentArtifactSet,
                                                       String javaVersion,
                                                       MethodInvocation methodInvocation) {
        AuditInfo auditInfo = new AuditInfo();

        try {
            MethodInfoResult cachedMethodInfoResult = InferenceUtility.getCachedMethodInfo(methodInvocation);

            if (Objects.nonNull(cachedMethodInfoResult)) {
                return cachedMethodInfoResult;
            }

            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(methodInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingQualifiedClassNameList =
                    InferenceUtility.getAllEnclosingClassList(methodInvocation, dependentArtifactSet, javaVersion,
                            importStatementList, jarInfoService, classInfoService, auditInfo);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingQualifiedClassNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, methodInvocation, auditInfo));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            methodInvocation, jarInfoService, classInfoService, auditInfo);

            List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    methodInvocation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

            return methodInfoList.isEmpty()
                    ? new MethodInfoResult(null, auditInfo)
                    : new MethodInfoResult(methodInfoList.get(0), auditInfo);

        } catch (Exception e) {
            return new MethodInfoResult(auditInfo, e);
        }
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        MethodInfoResult methodInfoResult = getMethodInfoResult(dependentArtifactSet, javaVersion, superMethodInvocation);

        return Objects.nonNull(methodInfoResult) ? methodInfoResult.getMethodInfo() : null;
    }

    public static MethodInfoResult getMethodInfoResult(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        AuditInfo auditInfo = new AuditInfo();

        try {
            MethodInfoResult cachedMethodInfoResult = InferenceUtility.getCachedMethodInfo(superMethodInvocation);

            if (Objects.nonNull(cachedMethodInfoResult)) {
                return cachedMethodInfoResult;
            }

            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingClassQNameList = InferenceUtility.getAllEnclosingClassList(superMethodInvocation,
                    dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, superMethodInvocation, auditInfo));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            superMethodInvocation, jarInfoService, classInfoService, auditInfo);

            List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    superMethodInvocation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

            return methodInfoList.isEmpty()
                    ? new MethodInfoResult(null, auditInfo)
                    : new MethodInfoResult(methodInfoList.get(0), auditInfo);

        } catch (Exception e) {
            return new MethodInfoResult(auditInfo, e);
        }
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        MethodInfoResult methodInfoResult = getMethodInfoResult(dependentArtifactSet, javaVersion, classInstanceCreation);

        return Objects.nonNull(methodInfoResult) ? methodInfoResult.getMethodInfo() : null;
    }

    public static MethodInfoResult getMethodInfoResult(Set<Artifact> dependentArtifactSet,
                                                       String javaVersion,
                                                       ClassInstanceCreation classInstanceCreation) {

        AuditInfo auditInfo = new AuditInfo();

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            List<String> enclosingClassQNameList =
                    InferenceUtility.getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion,
                            importStatementList, jarInfoService, classInfoService, auditInfo);

            List<String> nonEnclosingAccessibleClassQNameList =
                    InferenceUtility.getNonEnclosingAccessibleClassListForInstantiation(classInstanceCreation, enclosingClassQNameList);

            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, nonEnclosingAccessibleClassQNameList, jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, classInstanceCreation, auditInfo));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            classInstanceCreation, jarInfoService, classInfoService, auditInfo);

            List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    classInstanceCreation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

            return methodInfoList.isEmpty()
                    ? new MethodInfoResult(null, auditInfo)
                    : new MethodInfoResult(methodInfoList.get(0), auditInfo);

        } catch (Exception e) {
            return new MethodInfoResult(auditInfo, e);
        }
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           ConstructorInvocation constructorInvocation) {

        MethodInfoResult methodInfoResult = getMethodInfoResult(dependentArtifactSet, javaVersion, constructorInvocation);

        return Objects.nonNull(methodInfoResult) ? methodInfoResult.getMethodInfo() : null;
    }

    public static MethodInfoResult getMethodInfoResult(Set<Artifact> dependentArtifactSet,
                                                       String javaVersion,
                                                       ConstructorInvocation constructorInvocation) {

        AuditInfo auditInfo = new AuditInfo();

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            auditInfo.incrementMethodCount();

            List<String> enclosingClassQNameList = InferenceUtility.getAllEnclosingClassList(constructorInvocation,
                    dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, constructorInvocation, auditInfo));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            constructorInvocation, jarInfoService, classInfoService, auditInfo);

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

            List<Type> typeArgumentList = constructorInvocation.typeArguments();
            List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, typeArgumentList, owningClassInfo, auditInfo);

            List<Expression> argumentList = constructorInvocation.arguments();
            int numberOfParameters = argumentList.size();

            List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                    javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo, auditInfo);

            TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                    .new Criteria(dependentArtifactSet, javaVersion,
                    importStatementList, methodName, numberOfParameters)
                    .setInvokerClassName(invokerClassName)
                    .setOwningClassInfo(owningClassInfo)
                    .setAuditInfo(auditInfo);

            for (int i = 0; i < argumentTypeInfoList.size(); i++) {
                searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
            }

            List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

            InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList,
                    null, null, variableNameMap, auditInfo);
            InferenceUtility.conversionToVarargsMethodArgument(methodInfoList);

            return methodInfoList.isEmpty()
                    ? new MethodInfoResult(null, auditInfo)
                    : new MethodInfoResult(methodInfoList.get(0), auditInfo);

        } catch (Exception e) {
            return new MethodInfoResult(auditInfo, e);
        }
    }

    public static MethodInfo getMethodInfo(Set<Artifact> dependentArtifactSet,
                                           String javaVersion,
                                           SuperConstructorInvocation superConstructorInvocation) {

        MethodInfoResult methodInfoResult = getMethodInfoResult(dependentArtifactSet, javaVersion, superConstructorInvocation);

        return Objects.nonNull(methodInfoResult) ? methodInfoResult.getMethodInfo() : null;
    }

    public static MethodInfoResult getMethodInfoResult(Set<Artifact> dependentArtifactSet,
                                                       String javaVersion,
                                                       SuperConstructorInvocation superConstructorInvocation) {

        AuditInfo auditInfo = new AuditInfo();

        try {
            CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superConstructorInvocation);

            List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
            InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

            auditInfo.incrementMethodCount();

            List<String> enclosingClassQNameList = InferenceUtility.getAllEnclosingClassList(superConstructorInvocation,
                    dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);
            OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingClassQNameList, Collections.emptyList(), jarInfoService, classInfoService);

            owningClassInfo.setAccessibleFormalTypeParameterList(InferenceUtility.getAccessibleFormalTypeParameterList(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, superConstructorInvocation, auditInfo));

            Map<String, Set<VariableDeclarationDto>> variableNameMap =
                    InferenceUtility.getVariableNameMap(dependentArtifactSet, javaVersion, importStatementList,
                            superConstructorInvocation, jarInfoService, classInfoService, auditInfo);

            TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
            Type superClassType = typeDeclaration.getSuperclassType();
            String superClassName;

            if (superClassType == null) {
                superClassName = "java.lang.Object";
            } else {
                TypeInfo superClassTypeInfo = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                        importStatementList, superClassType, owningClassInfo, auditInfo);

                superClassName = Objects.nonNull(superClassTypeInfo)
                        ? superClassTypeInfo.getQualifiedClassName()
                        : null;
            }

            List<Type> typeArgumentList = superConstructorInvocation.typeArguments();
            List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, typeArgumentList, owningClassInfo, auditInfo);

            List<Expression> argumentList = superConstructorInvocation.arguments();
            int numberOfParameters = argumentList.size();

            List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                    javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo, auditInfo);

            TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                    .new Criteria(dependentArtifactSet, javaVersion,
                    importStatementList, superClassName, numberOfParameters)
                    .setOwningClassInfo(owningClassInfo)
                    .setSuperInvoker(true)
                    .setAuditInfo(auditInfo);

            for (int i = 0; i < argumentTypeInfoList.size(); i++) {
                searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
            }

            List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

            InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList,
                    null, null, variableNameMap, auditInfo);
            InferenceUtility.conversionToVarargsMethodArgument(methodInfoList);

            return methodInfoList.isEmpty()
                    ? new MethodInfoResult(null, auditInfo)
                    : new MethodInfoResult(methodInfoList.get(0), auditInfo);

        } catch (Exception e) {
            return new MethodInfoResult(auditInfo, e);
        }
    }

}
