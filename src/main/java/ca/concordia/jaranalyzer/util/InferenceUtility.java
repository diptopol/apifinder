package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.TypeInferenceAPI;
import ca.concordia.jaranalyzer.TypeInferenceBase;
import ca.concordia.jaranalyzer.TypeInferenceFluentAPI;
import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.entity.FieldInfo;
import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.*;
import ca.concordia.jaranalyzer.models.typeInfo.*;
import ca.concordia.jaranalyzer.service.ClassInfoService;
import ca.concordia.jaranalyzer.service.JarInfoService;
import ca.concordia.jaranalyzer.util.signaturevisitor.FieldSignatureFormalTypeParameterExtractor;
import ca.concordia.jaranalyzer.util.signaturevisitor.GenericTypeResolutionAdapter;
import ca.concordia.jaranalyzer.util.signaturevisitor.ParameterizedSuperClassTypeInfoExtractor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vavr.Tuple2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import org.objectweb.asm.signature.SignatureReader;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtility {

    private static Cache<ASTNode, MethodInfo> methodInfoLoaderCacheFromASTNode;

    static {
        if (Objects.isNull(methodInfoLoaderCacheFromASTNode)) {
            methodInfoLoaderCacheFromASTNode  = Caffeine.newBuilder()
                    .expireAfterAccess(10, TimeUnit.MINUTES)
                    .maximumSize(100)
                    .build();
        }
    }


    public static List<String> getImportStatementList(CompilationUnit compilationUnit) {
        List<ImportDeclaration> importDeclarationList = compilationUnit.imports();

        return importDeclarationList.stream()
                .map(ImportObject::new)
                .map(ImportObject::getImportStatement)
                .collect(Collectors.toList());
    }

    public static void addSpecialImportStatements(List<String> importStatementList,
                                                   CompilationUnit compilationUnit) {
        // all java classes can access methods and classes of java.lang package without import statement
        importStatementList.add("import java.lang.*");

        // all classes under the current package can be accessed without import statement
        PackageDeclaration packageDeclaration = compilationUnit.getPackage();
        importStatementList.add("import " + packageDeclaration.getName().getFullyQualifiedName() + ".*");
    }

    /*
     * TODO: Need to think about method type argument.
     */
    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             MethodInvocation methodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {
        auditInfo.incrementMethodCount();

        String methodName = methodInvocation.getName().getIdentifier();
        int numberOfParameters = methodInvocation.arguments().size();
        List<Expression> argumentList = methodInvocation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo, auditInfo);

        List<Type> typeArgumentList = methodInvocation.typeArguments();

        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        Expression expression = methodInvocation.getExpression();

        TypeInfo invokerClassTypeInfo = null;

        if (Objects.nonNull(expression)) {
            invokerClassTypeInfo = InferenceUtility.getTypeInfoFromExpression(dependentArtifactSet, javaVersion,
                    importStatementList, variableNameMap, expression, owningClassInfo, auditInfo);
        }

        TypeInfo returnTypeInfo = getReturnParameterizedTypeInfo(methodInvocation, dependentArtifactSet, javaVersion,
                importStatementList, owningClassInfo, variableNameMap, auditInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerTypeInfo(invokerClassTypeInfo)
                .setOwningClassInfo(owningClassInfo)
                .setAuditInfo(auditInfo);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        /*
         * According to my current understanding we do not need arguments for any type inference.This may change with
         * any unanticipated scenario.
         *
         * Order of transformTypeInfoRepresentation and conversionToVarargsMethodArgument matters. since we are
         * converting last argument array type to vararg.
         */
        transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList, invokerClassTypeInfo, returnTypeInfo,
                variableNameMap, auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             SuperMethodInvocation superMethodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {
        auditInfo.incrementMethodCount();

        String methodName = superMethodInvocation.getName().getIdentifier();
        int numberOfParameters = superMethodInvocation.arguments().size();
        List<Expression> argumentList = superMethodInvocation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo, auditInfo);

        List<Type> typeArgumentList = superMethodInvocation.typeArguments();
        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setOwningClassInfo(owningClassInfo)
                .setSuperInvoker(true)
                .setAuditInfo(auditInfo);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        /*
         * According to my current understanding we do not need arguments for any type inference.This may change with
         * any unanticipated scenario.
         *
         * Order of transformTypeInfoRepresentation and conversionToVarargsMethodArgument matters. since we are
         * converting last argument array type to vararg.
         */
        transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList, null,
                null, variableNameMap, auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             ClassInstanceCreation classInstanceCreation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {
        auditInfo.incrementMethodCount();

        String methodName = classInstanceCreation.getType().toString();
        int numberOfParameters = classInstanceCreation.arguments().size();
        List<Expression> argumentList = classInstanceCreation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo, auditInfo);

        List<Type> typeArgumentList = classInstanceCreation.typeArguments();
        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        Type type = classInstanceCreation.getType();
        TypeInfo returnTypeInfo = null;

        if (type.isParameterizedType() && ((ParameterizedType) type).typeArguments().isEmpty()) {
            returnTypeInfo = getReturnParameterizedTypeInfo(classInstanceCreation, dependentArtifactSet, javaVersion,
                    importStatementList, owningClassInfo, variableNameMap, auditInfo);
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setClassInstantiation(true)
                .setOwningClassInfo(owningClassInfo)
                .setAuditInfo(auditInfo);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentTypeInfo(i, argumentTypeInfoList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList, null,
                returnTypeInfo, variableNameMap, auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             ExpressionMethodReference expressionMethodReference,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {

        auditInfo.incrementMethodReferenceCount();

        String methodName = expressionMethodReference.getName().getIdentifier();
        Expression expression = expressionMethodReference.getExpression();

        List<Type> typeArgumentList = expressionMethodReference.typeArguments();

        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        TypeInfo invokerClassTypeInfo = null;

        if (Objects.nonNull(expression)) {
            invokerClassTypeInfo = InferenceUtility.getTypeInfoFromExpression(dependentArtifactSet, javaVersion,
                    importStatementList, variableNameMap, expression, owningClassInfo, auditInfo);
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion, importStatementList, methodName)
                .setInvokerTypeInfo(invokerClassTypeInfo)
                .setOwningClassInfo(owningClassInfo)
                .setAuditInfo(auditInfo);

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        for (MethodInfo methodInfo: methodInfoList) {
            if (methodInfo.isVarargs()) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + TypeInferenceBase.VARARGS_DISTANCE);
            }
        }

        methodInfoList.sort(Comparator.comparingDouble(MethodInfo::getArgumentMatchingDistance));

        /*
         * According to my current understanding we do not need arguments for any type inference.This may change with
         * any unanticipated scenario.
         *
         * Order of transformTypeInfoRepresentation and conversionToVarargsMethodArgument matters. since we are
         * converting last argument array type to vararg.
         */
        transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                methodInfoList, Collections.emptyList(), typeArgumentTypeInfoList, invokerClassTypeInfo, null,
                variableNameMap, auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             CreationReference creationReference,
                                                             List<String> importStatementList,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {

        auditInfo.incrementMethodReferenceCount();
        String methodName = creationReference.getType().toString();

        List<Type> typeArgumentList = creationReference.typeArguments();

        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion, importStatementList, methodName)
                .setOwningClassInfo(owningClassInfo)
                .setAuditInfo(auditInfo);

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        for (MethodInfo methodInfo: methodInfoList) {
            if (methodInfo.isVarargs()) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + TypeInferenceBase.VARARGS_DISTANCE);
            }
        }

        methodInfoList.sort(Comparator.comparingDouble(MethodInfo::getArgumentMatchingDistance));

        /*
         * According to my current understanding we do not need arguments for any type inference.This may change with
         * any unanticipated scenario.
         *
         * Order of transformTypeInfoRepresentation and conversionToVarargsMethodArgument matters. since we are
         * converting last argument array type to vararg.
         */
        transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                methodInfoList, Collections.emptyList(), typeArgumentTypeInfoList, null, null,
                new LinkedHashMap<>(), auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             SuperMethodReference superMethodReference,
                                                             List<String> importStatementList,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {

        auditInfo.incrementMethodReferenceCount();
        String methodName = superMethodReference.getName().getIdentifier();

        List<Type> typeArgumentList = superMethodReference.typeArguments();

        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion, importStatementList, methodName)
                .setOwningClassInfo(owningClassInfo)
                .setSuperInvoker(true)
                .setAuditInfo(auditInfo);

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        for (MethodInfo methodInfo: methodInfoList) {
            if (methodInfo.isVarargs()) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + TypeInferenceBase.VARARGS_DISTANCE);
            }
        }

        methodInfoList.sort(Comparator.comparingDouble(MethodInfo::getArgumentMatchingDistance));

        /*
         * According to my current understanding we do not need arguments for any type inference.This may change with
         * any unanticipated scenario.
         *
         * Order of transformTypeInfoRepresentation and conversionToVarargsMethodArgument matters. since we are
         * converting last argument array type to vararg.
         */
        transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                methodInfoList, Collections.emptyList(), typeArgumentTypeInfoList, null, null,
                new LinkedHashMap<>(), auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             TypeMethodReference typeMethodReference,
                                                             List<String> importStatementList,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {

        auditInfo.incrementMethodReferenceCount();
        String methodName = typeMethodReference.getName().getIdentifier();

        List<Type> typeArgumentList = typeMethodReference.typeArguments();

        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo, auditInfo);

        TypeInfo invokerClassTypeInfo = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                importStatementList, typeMethodReference.getType(), owningClassInfo, auditInfo);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion, importStatementList, methodName)
                .setInvokerTypeInfo(invokerClassTypeInfo)
                .setOwningClassInfo(owningClassInfo)
                .setAuditInfo(auditInfo);

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        for (MethodInfo methodInfo: methodInfoList) {
            if (methodInfo.isVarargs()) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + TypeInferenceBase.VARARGS_DISTANCE);
            }
        }

        methodInfoList.sort(Comparator.comparingDouble(MethodInfo::getArgumentMatchingDistance));

        /*
         * According to my current understanding we do not need arguments for any type inference.This may change with
         * any unanticipated scenario.
         *
         * Order of transformTypeInfoRepresentation and conversionToVarargsMethodArgument matters. since we are
         * converting last argument array type to vararg.
         */
        transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                methodInfoList, Collections.emptyList(), typeArgumentTypeInfoList, invokerClassTypeInfo, null,
                new LinkedHashMap<>(), auditInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static Map<String, Set<VariableDeclarationDto>> getVariableNameMap(Set<Artifact> dependentArtifactSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              ASTNode methodExpression,
                                                                              JarInfoService jarInfoService,
                                                                              ClassInfoService classInfoService,
                                                                              AuditInfo auditInfo) {

        assert methodExpression instanceof MethodInvocation
                || methodExpression instanceof SuperMethodInvocation
                || methodExpression instanceof ClassInstanceCreation
                || methodExpression instanceof ConstructorInvocation
                || methodExpression instanceof SuperConstructorInvocation;

        Map<String, Set<VariableDeclarationDto>> variableNameMap = new HashMap<>();
        Map<String, OwningClassInfo> owningClassInfoMap = new HashMap<>();

        Set<VariableDeclarationDto> fieldVariableDeclarationSet =
                getFieldVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                        methodExpression, owningClassInfoMap, jarInfoService, classInfoService, auditInfo);

        populateVariableNameMap(variableNameMap, fieldVariableDeclarationSet);

        populateVariableNameMapForMethod(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfoMap, methodExpression, variableNameMap, jarInfoService, classInfoService, auditInfo);

        populateVariableNameMapForStaticBlock(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfoMap, methodExpression, variableNameMap, jarInfoService, classInfoService, auditInfo);

        populateVariableNameMapForLambdaExpression(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfoMap, methodExpression, variableNameMap, jarInfoService, classInfoService, auditInfo);

        return variableNameMap;
    }

    public static List<TypeInfo> getArgumentTypeInfoList(Set<Artifact> dependentArtifactSet,
                                                         String javaVersion,
                                                         List<String> importStatementList,
                                                         Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                         List<Expression> argumentList,
                                                         OwningClassInfo owningClassInfo,
                                                         AuditInfo auditInfo) {
        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();

        for (Expression argument : argumentList) {
            TypeInfo typeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList,
                    variableNameMap, argument, owningClassInfo, auditInfo);

            if (typeInfo != null) {
                argumentTypeInfoList.add(typeInfo);
            }
        }

        return argumentTypeInfoList;
    }

    public static Set<VariableDeclarationDto> getFieldVariableDeclarationDtoList(Set<Artifact> dependentArtifactSet,
                                                                                 String javaVersion,
                                                                                 List<String> importStatementList,
                                                                                 ASTNode node,
                                                                                 Map<String, OwningClassInfo> owningClassInfoMap,
                                                                                 JarInfoService jarInfoService,
                                                                                 ClassInfoService classInfoService,
                                                                                 AuditInfo auditInfo) {
        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(node);

        List<FieldDeclaration> fieldDeclarationList = new ArrayList<>();

        while (Objects.nonNull(abstractTypeDeclaration)) {
            if (abstractTypeDeclaration instanceof TypeDeclaration) {
                TypeDeclaration typeDeclaration = (TypeDeclaration) abstractTypeDeclaration;

                for (FieldDeclaration fieldDeclaration: typeDeclaration.getFields()) {
                    String firstEnclosingClassName = getFirstEnclosingClassQName(fieldDeclaration, dependentArtifactSet,
                            javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                    if (!owningClassInfoMap.containsKey(firstEnclosingClassName)) {
                        OwningClassInfo owningClassInfo = getOwningClassInfo(fieldDeclaration, dependentArtifactSet,
                                javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                        owningClassInfoMap.put(firstEnclosingClassName, owningClassInfo);
                    }

                    fieldDeclarationList.add(fieldDeclaration);
                }

            } else if (abstractTypeDeclaration instanceof AnnotationTypeDeclaration || abstractTypeDeclaration instanceof EnumDeclaration) {
                abstractTypeDeclaration.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(FieldDeclaration fieldDeclaration) {
                        String firstEnclosingClassName = getFirstEnclosingClassQName(fieldDeclaration, dependentArtifactSet,
                                javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                        if (!owningClassInfoMap.containsKey(firstEnclosingClassName)) {
                            OwningClassInfo owningClassInfo = getOwningClassInfo(fieldDeclaration, dependentArtifactSet,
                                    javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                            owningClassInfoMap.put(firstEnclosingClassName, owningClassInfo);
                        }

                        fieldDeclarationList.add(fieldDeclaration);

                        return true;
                    }
                });
            }

            abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(abstractTypeDeclaration.getParent());
        }

        return fieldDeclarationList.stream().map(fieldDeclaration -> {
                    List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

                    String firstEnclosingClassName = getFirstEnclosingClassQName(fieldDeclaration, dependentArtifactSet,
                            javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                    return getVariableDeclarationDtoList(fieldDeclaration.getType(),
                            fieldDeclaration.getParent().getStartPosition(), fragmentList,
                            owningClassInfoMap.get(firstEnclosingClassName));
                }).flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    public static MethodInfo getCachedMethodInfo(MethodInvocation methodInvocation) {
        return methodInfoLoaderCacheFromASTNode.getIfPresent(methodInvocation);
    }

    public static MethodInfo getCachedMethodInfo(SuperMethodInvocation superMethodInvocation) {
        return methodInfoLoaderCacheFromASTNode.getIfPresent(superMethodInvocation);
    }

    public static OwningClassInfo getOwningClassInfo(ASTNode astNode,
                                                     Set<Artifact> dependentArtifactSet,
                                                     String javaVersion,
                                                     List<String> importStatementList,
                                                     JarInfoService jarInfoService,
                                                     ClassInfoService classInfoService,
                                                     AuditInfo auditInfo) {

        List<String> enclosingClassQNameList = getAllEnclosingClassList(astNode, dependentArtifactSet, javaVersion,
                importStatementList, jarInfoService, classInfoService, auditInfo);
        List<String> nonEnclosingClassQNameList = getNonEnclosingAccessibleClassListForInstantiation(astNode, enclosingClassQNameList);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                enclosingClassQNameList, nonEnclosingClassQNameList, jarInfoService, classInfoService);

        owningClassInfo.setAccessibleFormalTypeParameterList(
                getAccessibleFormalTypeParameterList(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfo, astNode, auditInfo));

        return owningClassInfo;
    }

    public static ASTNode getTypeDeclaration(ASTNode node) {
        return getClosestASTNode(node, TypeDeclaration.class);
    }

    public static ASTNode getAbstractTypeDeclaration(ASTNode node) {
        return getClosestASTNode(node, AbstractTypeDeclaration.class);
    }

    public static ASTNode getCompilationUnit(ASTNode node) {
        return getClosestASTNode(node, CompilationUnit.class);
    }

    public static String getDeclaringClassQualifiedName(BodyDeclaration bodyDeclaration) {
        String declaringClassQualifiedName = "";
        ASTNode node = bodyDeclaration;

        while (node != null) {
            List<AnonymousClassDeclaration> anonymousClassDeclarationList = getAnonymousClassDeclarationList(bodyDeclaration);

            if (node instanceof CompilationUnit) {
                CompilationUnit cu = (CompilationUnit) node;
                PackageDeclaration packageDeclaration = cu.getPackage();
                String packageName = packageDeclaration != null ? packageDeclaration.getName().getFullyQualifiedName() : "";

                //TODO: Need to understand why percentage is needed
                /*if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = packageDeclaration.getName().getFullyQualifiedName() + "%";
                } else {
                    declaringClassQualifiedName = packageDeclaration.getName().getFullyQualifiedName() + "%." + declaringClassQualifiedName;
                }*/

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = packageName;
                } else {
                    declaringClassQualifiedName = (!packageName.equals("") ? packageName + "." : "") + declaringClassQualifiedName;
                }

            } else if (node instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) node;
                String typeDeclarationName = typeDeclaration.getName().getIdentifier();

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = typeDeclarationName;
                } else {
                    declaringClassQualifiedName = typeDeclarationName + "$" + declaringClassQualifiedName;
                }
            } else if (node instanceof AnonymousClassDeclaration) {
                AnonymousClassDeclaration anonymousClassDeclaration = (AnonymousClassDeclaration) node;
                String anonymousClassName = anonymousClassDeclarationList.contains(anonymousClassDeclaration)
                        ? String.valueOf(anonymousClassDeclarationList.indexOf(anonymousClassDeclaration)) : "";

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = anonymousClassName;
                } else {
                    declaringClassQualifiedName = anonymousClassName + "$" + declaringClassQualifiedName;
                }
            }

            node = node.getParent();
        }

        return declaringClassQualifiedName;
    }

    public static ASTNode getClosestASTNode(ASTNode node, Class<? extends ASTNode> nodeClazz) {
        while (Objects.nonNull(node) && !(nodeClazz.isInstance(node))) {
            node = node.getParent();
        }

        return node;
    }

    public static TypeInfo getTypeInfoFromExpression(Set<Artifact> dependentArtifactSet,
                                                     String javaVersion,
                                                     List<String> importStatementList,
                                                     Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                     Expression expression,
                                                     OwningClassInfo owningClassInfo,
                                                     AuditInfo auditInfo) {
        if (expression == null) {
            return null;
        }

        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(expression);

        if (expression instanceof NullLiteral) {
            return new NullTypeInfo();

        } else if (expression instanceof ThisExpression) {
            ThisExpression thisExpression = (ThisExpression) expression;

            if (Objects.nonNull(thisExpression.getQualifier())) {
                String className = thisExpression.getQualifier().getFullyQualifiedName();

                return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, className,
                        owningClassInfo, auditInfo);

            } else {
                String className = getDeclaringClassQualifiedName(abstractTypeDeclaration);
                className = className.replace("%", "").replace("$", ".");

                return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, className,
                        owningClassInfo, auditInfo);
            }

        } else if (expression instanceof TypeLiteral) {
            Type argumentType = ((TypeLiteral) expression).getType();
            TypeInfo argumentTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                    argumentType, owningClassInfo, auditInfo);

            if (Objects.isNull(argumentTypeInfo)) {
                argumentTypeInfo = new QualifiedTypeInfo("java.lang.Object");
            }

            ParameterizedTypeInfo parameterizedClassTypeInfo = new ParameterizedTypeInfo("java.lang.Class");
            parameterizedClassTypeInfo.setParameterized(true);
            parameterizedClassTypeInfo.setTypeArgumentList(Collections.singletonList(argumentTypeInfo));

            return parameterizedClassTypeInfo;
        } else if (expression instanceof ParenthesizedExpression) {
            ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    parenthesizedExpression.getExpression(), owningClassInfo, auditInfo);


        } else if (expression instanceof FieldAccess) {
            /*
             * There is a scope of fetching fieldAccess from variableNameMap. For that we need to keep origin of the
             * variable (e.g., field instance, local variable, method argument).
             *
             * TODO: store origin of variable
             */
            FieldAccess fieldAccess = (FieldAccess) expression;

            Expression fieldAccessExpression = fieldAccess.getExpression();
            TypeInfo fieldAccessTypeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList,
                    variableNameMap, fieldAccessExpression, owningClassInfo, auditInfo);

            String className = fieldAccessTypeInfo.getName();

            String name = fieldAccess.getName().getFullyQualifiedName();

            if (Objects.nonNull(className)) {
                name = className + "." + name;
            }

            TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                    importStatementList, name, owningClassInfo, auditInfo);

            if (Objects.isNull(fieldTypeInfo)) {
                return new NullTypeInfo();
            }

            return fieldTypeInfo;
        } else if (expression instanceof SuperFieldAccess) {
            SuperFieldAccess superFieldAccess = (SuperFieldAccess) expression;

            String name = superFieldAccess.getName().getFullyQualifiedName();

            TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                    importStatementList, name, owningClassInfo, auditInfo);

            if (Objects.isNull(fieldTypeInfo)) {
                return new NullTypeInfo();
            }

            return fieldTypeInfo;
        } else if (expression instanceof BooleanLiteral) {
            return new PrimitiveTypeInfo("boolean");

        } else if (expression instanceof StringLiteral) {
            return new QualifiedTypeInfo("java.lang.String");

        } else if (expression instanceof CharacterLiteral) {
            return new PrimitiveTypeInfo("char");

        } else if (expression instanceof ConditionalExpression) {
            ConditionalExpression conditionalExpression = (ConditionalExpression) expression;

            Expression then = conditionalExpression.getThenExpression();
            Expression elseExp = conditionalExpression.getElseExpression();

            TypeInfo thenExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    then, owningClassInfo, auditInfo);
            TypeInfo elseExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    elseExp, owningClassInfo, auditInfo);

            return !thenExpressionTypeInfo.isNullTypeInfo() ? thenExpressionTypeInfo : elseExpressionTypeInfo;
        } else if (expression instanceof CastExpression) {
            Type castedType = ((CastExpression) expression).getType();

            TypeInfo castedTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, castedType,
                    owningClassInfo, auditInfo);

            if (Objects.isNull(castedTypeInfo)) {
                return new NullTypeInfo();
            }

            return castedTypeInfo;

        } else if (expression instanceof NumberLiteral) {
            return new PrimitiveTypeInfo(getPrimitiveType((NumberLiteral) expression));

        } else if (expression instanceof ArrayCreation) {
            ArrayCreation arrayCreation = (ArrayCreation) expression;
            ArrayType arrayType = arrayCreation.getType();

            TypeInfo arrayTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, arrayType,
                    owningClassInfo, auditInfo);

            if (Objects.isNull(arrayTypeInfo)) {
                return new NullTypeInfo();
            }

            return arrayTypeInfo;

        } else if (expression instanceof ArrayAccess) {
            /*
             * In array access, we are trying to determine the type of the variable. There can be two scenarios.
             * Scenario 1: for single dimension array access (e.g., obj[i]) we will get the type of obj.
             *
             * Scenario 2: for multiple dimension array access (e.g., obj[i][j]) we will get reduced dimension array.
             */

            ArrayAccess arrayAccess = (ArrayAccess) expression;

            Expression array = arrayAccess.getArray();
            TypeInfo typeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList,
                    variableNameMap, array, owningClassInfo, auditInfo);

            if (typeInfo.isArrayTypeInfo()) {
                ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

                return arrayTypeInfo.getDimension() > 1
                        ? new ArrayTypeInfo(arrayTypeInfo.getElementTypeInfo(), arrayTypeInfo.getDimension() - 1)
                        : arrayTypeInfo.getElementTypeInfo();

            } else if (typeInfo.isVarargTypeInfo()) {
                VarargTypeInfo varargTypeInfo = (VarargTypeInfo) typeInfo;

                return varargTypeInfo.getElementTypeInfo();

            } else {
                throw new IllegalStateException();
            }

        } else if (expression instanceof InfixExpression) {
            InfixExpression infixExpression = (InfixExpression) expression;

            Expression left = infixExpression.getLeftOperand();
            Expression right = infixExpression.getRightOperand();
            InfixExpression.Operator operator = infixExpression.getOperator();

            TypeInfo leftExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion,
                    importStatementList, variableNameMap, left, owningClassInfo, auditInfo);
            TypeInfo rightExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet,
                    javaVersion, importStatementList, variableNameMap, right, owningClassInfo, auditInfo);

            if (operator.equals(InfixExpression.Operator.CONDITIONAL_AND)
                    || operator.equals(InfixExpression.Operator.CONDITIONAL_OR)
                    || operator.equals(InfixExpression.Operator.GREATER)
                    || operator.equals(InfixExpression.Operator.GREATER_EQUALS)
                    || operator.equals(InfixExpression.Operator.EQUALS)
                    || operator.equals(InfixExpression.Operator.NOT_EQUALS)
                    || operator.equals(InfixExpression.Operator.LESS)
                    || operator.equals(InfixExpression.Operator.LESS_EQUALS)) {

                return new PrimitiveTypeInfo("boolean");
            } else if (operator.equals(InfixExpression.Operator.PLUS)
                    || operator.equals(InfixExpression.Operator.MINUS)
                    || operator.equals(InfixExpression.Operator.TIMES)
                    || operator.equals(InfixExpression.Operator.DIVIDE)
                    || operator.equals(InfixExpression.Operator.REMAINDER)
                    || operator.equals(InfixExpression.Operator.XOR)
                    || operator.equals(InfixExpression.Operator.AND)
                    || operator.equals(InfixExpression.Operator.OR)) {

                if (operator.equals(InfixExpression.Operator.PLUS)
                        && ((Objects.nonNull(leftExpressionTypeInfo) && "java.lang.String".equals(leftExpressionTypeInfo.getQualifiedClassName()))
                        || (Objects.nonNull(rightExpressionTypeInfo) && "java.lang.String".equals(rightExpressionTypeInfo.getQualifiedClassName())))) {

                    return new PrimitiveTypeInfo("java.lang.String");
                }

                if (Objects.isNull(leftExpressionTypeInfo) || Objects.isNull(rightExpressionTypeInfo)) {
                    return new NullTypeInfo();
                }

                List<String> operandPrecedentList = new ArrayList<String>(Arrays.asList("byte", "short", "int", "long", "float", "double"));

                int positionOfLeft = operandPrecedentList.indexOf(leftExpressionTypeInfo.getQualifiedClassName());
                int positionOfRight = operandPrecedentList.indexOf(rightExpressionTypeInfo.getQualifiedClassName());

                return positionOfLeft > positionOfRight ? leftExpressionTypeInfo : rightExpressionTypeInfo;

            } else if (operator.equals(InfixExpression.Operator.LEFT_SHIFT)
                    || operator.equals(InfixExpression.Operator.RIGHT_SHIFT_SIGNED)
                    || operator.equals(InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED)) {

                return leftExpressionTypeInfo;
            } else {
                throw new IllegalStateException();
            }
        } else if (expression instanceof PrefixExpression) {
            PrefixExpression prefixExpression = (PrefixExpression) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    prefixExpression.getOperand(), owningClassInfo, auditInfo);

        } else if (expression instanceof PostfixExpression) {
            PostfixExpression postfixExpression = (PostfixExpression) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    postfixExpression.getOperand(), owningClassInfo, auditInfo);

        } else if (expression instanceof Name) {
            String name = ((Name) expression).getFullyQualifiedName();

            if (expression instanceof QualifiedName) {
                String firstPart = name.substring(0, name.indexOf("."));
                VariableDeclarationDto selected = getVariableDeclarationDtoFromVariableMap(firstPart, expression, variableNameMap);
                String className = Objects.nonNull(selected)
                        ? getTypeInfoFromVariableDeclarationDto(dependentArtifactSet, javaVersion,
                        importStatementList, selected, auditInfo).getQualifiedClassName()
                        : null;

                if (Objects.isNull(className)) {
                    TypeInfo typeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                            firstPart, owningClassInfo, auditInfo);

                    if (Objects.nonNull(typeInfo)) {
                        className = typeInfo.getQualifiedClassName();
                    }
                }

                String[] propertyNameList = name.substring(name.indexOf(".") + 1).split("\\.");

                TypeInfo fieldTypeInfo = null;
                TypeInfo classTypeInfo = null;
                String propertyNamePath = className;

                for (String property: propertyNameList) {
                    if (propertyNamePath != null) {
                        propertyNamePath = propertyNamePath.concat(".").concat(property);

                        fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion, importStatementList,
                                propertyNamePath, owningClassInfo, auditInfo);

                        if (Objects.nonNull(fieldTypeInfo)) {
                            propertyNamePath = fieldTypeInfo.getQualifiedClassName();
                        } else {
                            classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                                    importStatementList, propertyNamePath, owningClassInfo, auditInfo);

                            if (Objects.nonNull(classTypeInfo)) {
                                propertyNamePath = classTypeInfo.getQualifiedClassName();
                            }
                        }
                    }
                }

                if (Objects.nonNull(fieldTypeInfo)) {
                    return fieldTypeInfo;
                } else if (Objects.nonNull(classTypeInfo)) {
                    return classTypeInfo;
                } else {
                    TypeInfo typeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name,
                            owningClassInfo, auditInfo);

                    return Objects.isNull(typeInfo) ? new NullTypeInfo() : typeInfo;
                }
            } else if (expression instanceof SimpleName) {
                VariableDeclarationDto selected = getVariableDeclarationDtoFromVariableMap(name, expression, variableNameMap);
                TypeInfo classTypeInfo = Objects.nonNull(selected)
                        ? getTypeInfoFromVariableDeclarationDto(dependentArtifactSet, javaVersion, importStatementList, selected, auditInfo)
                        : null;

                if (Objects.nonNull(classTypeInfo)) {
                    return classTypeInfo;
                } else {
                    TypeInfo typeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                            name, owningClassInfo, auditInfo);

                    if (Objects.nonNull(typeInfo)) {
                        return typeInfo;
                    } else {
                        TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                                importStatementList, name, owningClassInfo, auditInfo);

                        return Objects.isNull(fieldTypeInfo) ? new NullTypeInfo() : fieldTypeInfo;
                    }
                }
            } else {
                throw new IllegalStateException();
            }

        } else if (expression instanceof ClassInstanceCreation) {
            ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;

            List<TypeInfo> typeArgumentList = getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, classInstanceCreation.typeArguments(), owningClassInfo, auditInfo);

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    classInstanceCreation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new NullTypeInfo();
            } else {
                String className = methodInfoList.get(0).getClassInfo().getQualifiedName();

                TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                        importStatementList, className, owningClassInfo, auditInfo);

                if (Objects.isNull(classTypeInfo)) {
                    return new NullTypeInfo();
                }

                if (classTypeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) classTypeInfo;

                    if (!typeArgumentList.isEmpty()) {
                        parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                        parameterizedTypeInfo.setParameterized(true);
                    }
                }

                return classTypeInfo;
            }
        } else if (expression instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) expression;

            MethodInfo methodInfo = methodInfoLoaderCacheFromASTNode.getIfPresent(methodInvocation);

            if (Objects.isNull(methodInfo)) {
                List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                        methodInvocation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

                methodInfo = methodInfoList.isEmpty() ? null : methodInfoList.get(0);

                if (Objects.nonNull(methodInfo)) {
                    methodInfoLoaderCacheFromASTNode.put(methodInvocation, methodInfo);
                }
            }

            // if the getAllMethods returns empty, the method can be a private construct.
            if (Objects.isNull(methodInfo)) {
                return new NullTypeInfo();
            } else {
                TypeInfo returnTypeInfo = SerializationUtils.clone(methodInfo.getReturnTypeInfo());

                return getParameterizedTypeWithTypeParameter(returnTypeInfo, dependentArtifactSet, javaVersion,
                        importStatementList, owningClassInfo, auditInfo);
            }
        } else if (expression instanceof SuperMethodInvocation) {
            SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) expression;

            MethodInfo methodInfo = methodInfoLoaderCacheFromASTNode.getIfPresent(superMethodInvocation);

            if (Objects.isNull(methodInfo)) {
                List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                        superMethodInvocation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

                methodInfo = methodInfoList.isEmpty() ? null : methodInfoList.get(0);

                if (Objects.nonNull(methodInfo)) {
                    methodInfoLoaderCacheFromASTNode.put(superMethodInvocation, methodInfo);
                }
            }

            // if the getAllMethods returns empty, the method can be a private construct.
            if (Objects.isNull(methodInfo)) {
                return new NullTypeInfo();
            } else {
                return SerializationUtils.clone(methodInfo.getReturnTypeInfo());
            }
        } else if (expression instanceof LambdaExpression) {
            LambdaExpression lambdaExpression = (LambdaExpression) expression;

            List<VariableDeclaration> parameterList = lambdaExpression.parameters();

            List<TypeInfo> argumentTypeInfoList = new ArrayList<>();
            for (VariableDeclaration parameter: parameterList) {
                if (parameter instanceof SingleVariableDeclaration) {
                    SingleVariableDeclaration singleVariableDeclaration = (SingleVariableDeclaration) parameter;

                    TypeInfo parameterTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                            singleVariableDeclaration.getType(), owningClassInfo, auditInfo);

                    if (Objects.isNull(parameterTypeInfo)) {
                        parameterTypeInfo = new NullTypeInfo();
                    }

                    argumentTypeInfoList.add(parameterTypeInfo);
                } else if (parameter instanceof VariableDeclarationFragment) {
                    argumentTypeInfoList.add(new QualifiedTypeInfo("java.lang.Object"));
                }
            }

            FunctionTypeInfo functionTypeInfo = new FunctionTypeInfo(Collections.singletonList(
                    new FunctionTypeInfo.FunctionDefinition(null, argumentTypeInfoList)));

            functionTypeInfo.setExpression(lambdaExpression);

            return functionTypeInfo;

        } else if (expression instanceof ExpressionMethodReference) {
            ExpressionMethodReference expressionMethodReference = (ExpressionMethodReference) expression;

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    expressionMethodReference, importStatementList, variableNameMap, owningClassInfo, auditInfo);

            List<FunctionTypeInfo.FunctionDefinition> functionDefinitionList = methodInfoList.stream()
                    .map(m -> {
                        List<TypeInfo> argumentTypeInfoList = new ArrayList<>(m.getArgumentTypeInfoList());

                        /*
                         * For non-static method expressionMethodReference, we can call method upon invoker
                         * (e.g., ImmutableList.Builder::add), in this case the first argument of functional interface
                         * is the invoker itself. So matched the string form of expression before adding invoker
                         * as first argument.
                         */
                        String expressionStr = expressionMethodReference.getExpression().toString();
                        if (!m.isStatic()
                                && (m.getQualifiedClassName().equals(expressionStr)
                                || m.getQualifiedClassName().matches((".*\\.").concat(expressionStr).concat("$")))) {
                            argumentTypeInfoList.add(0, m.getClassInfo().getTypeInfo());
                        }

                        return new FunctionTypeInfo.FunctionDefinition(m.getReturnTypeInfo(), argumentTypeInfoList);
                    })
                    .collect(Collectors.toList());

            FunctionTypeInfo functionTypeInfo = new FunctionTypeInfo(functionDefinitionList);
            functionTypeInfo.setExpression(expressionMethodReference);

            return functionTypeInfo;

        } else if (expression instanceof CreationReference) {
            CreationReference creationReference = (CreationReference) expression;

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    creationReference, importStatementList, owningClassInfo, auditInfo);

            List<FunctionTypeInfo.FunctionDefinition> functionDefinitionList = methodInfoList.stream()
                    .map(m -> new FunctionTypeInfo.FunctionDefinition(m.getReturnTypeInfo(), m.getArgumentTypeInfoList()))
                    .collect(Collectors.toList());

            boolean isInnerClassConstructor = !methodInfoList.isEmpty() && methodInfoList.get(0).isInnerClassConstructor();

            FunctionTypeInfo functionTypeInfo = new FunctionTypeInfo(isInnerClassConstructor, functionDefinitionList);
            functionTypeInfo.setExpression(creationReference);

            return functionTypeInfo;

        } else if (expression instanceof SuperMethodReference) {
            SuperMethodReference superMethodReference = (SuperMethodReference) expression;

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    superMethodReference, importStatementList, owningClassInfo, auditInfo);

            List<FunctionTypeInfo.FunctionDefinition> functionDefinitionList = methodInfoList.stream()
                    .map(m -> new FunctionTypeInfo.FunctionDefinition(m.getReturnTypeInfo(), m.getArgumentTypeInfoList()))
                    .collect(Collectors.toList());

            FunctionTypeInfo functionTypeInfo = new FunctionTypeInfo(functionDefinitionList);
            functionTypeInfo.setExpression(superMethodReference);

            return functionTypeInfo;

        } else if (expression instanceof TypeMethodReference) {
            TypeMethodReference typeMethodReference = (TypeMethodReference) expression;

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    typeMethodReference, importStatementList, owningClassInfo, auditInfo);

            List<FunctionTypeInfo.FunctionDefinition> functionDefinitionList = methodInfoList.stream()
                    .map(m -> new FunctionTypeInfo.FunctionDefinition(m.getReturnTypeInfo(), m.getArgumentTypeInfoList()))
                    .collect(Collectors.toList());

            FunctionTypeInfo functionTypeInfo = new FunctionTypeInfo(functionDefinitionList);
            functionTypeInfo.setExpression(typeMethodReference);

            return functionTypeInfo;

        } else if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList,
                    variableNameMap, assignment.getLeftHandSide(), owningClassInfo, auditInfo);

        } else {
            return new NullTypeInfo();
        }
    }

    private static TypeInfo getParameterizedTypeWithTypeParameter(TypeInfo typeInfo,
                                                                  Set<Artifact> dependentArtifactSet,
                                                                  String javaVersion,
                                                                  List<String> importStatementList,
                                                                  OwningClassInfo owningClassInfo,
                                                                  AuditInfo auditInfo) {
        if (typeInfo.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) typeInfo).isParameterized()) {
            List<TypeInfo> typeArgumentList = ((ParameterizedTypeInfo) typeInfo).getTypeArgumentList();

            List<TypeInfo> updatedTypeArgumentList = new ArrayList<>();

            for (TypeInfo typeArgument: typeArgumentList) {
                updatedTypeArgumentList.add(getParameterizedTypeWithTypeParameter(typeArgument, dependentArtifactSet, javaVersion,
                        importStatementList, owningClassInfo, auditInfo));
            }

            TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                    importStatementList, typeInfo.getQualifiedClassName(), owningClassInfo, auditInfo);

            if (Objects.nonNull(classTypeInfo) && classTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedClassTypeInfo = (ParameterizedTypeInfo) classTypeInfo;

                if (!updatedTypeArgumentList.isEmpty()) {
                    parameterizedClassTypeInfo.setTypeArgumentList(updatedTypeArgumentList);
                    parameterizedClassTypeInfo.setParameterized(true);

                    return parameterizedClassTypeInfo;
                }
            }
        }

        return typeInfo;
    }

    public static TypeInfo getTypeInfo(Set<Artifact> dependentArtifactSet,
                                       String javaVersion,
                                       List<String> importStatementList,
                                       Type type,
                                       OwningClassInfo owningClassInfo,
                                       AuditInfo auditInfo) {
        if (type == null) {
            return null;
        }

        if (type instanceof PrimitiveType) {
            return new PrimitiveTypeInfo(type.toString());

        } else if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            Type elementType = arrayType.getElementType();

            if (!elementType.isPrimitiveType()) {
                if (elementType instanceof SimpleType) {
                    TypeInfo typeInfo = getTypeInfoFromSimpleType(dependentArtifactSet, javaVersion,
                            importStatementList, ((SimpleType) elementType), owningClassInfo, auditInfo);

                    if (Objects.isNull(typeInfo)) {
                        return null;
                    }

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());

                } else if (elementType instanceof QualifiedType) {
                    TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentArtifactSet, javaVersion,
                            importStatementList, (QualifiedType) elementType, owningClassInfo, auditInfo);

                    if (Objects.isNull(typeInfo)) {
                        return null;
                    }

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());

                } else if (elementType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) elementType;

                    List<TypeInfo> typeArgumentList = getTypeInfoList(dependentArtifactSet, javaVersion,
                            importStatementList, parameterizedType.typeArguments(), owningClassInfo, auditInfo);

                    TypeInfo typeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                            parameterizedType.getType(), owningClassInfo, auditInfo);

                    if (Objects.isNull(typeInfo)) {
                        return null;
                    }

                    if (typeInfo.isParameterizedTypeInfo()) {
                        ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                        if (!typeArgumentList.isEmpty()) {
                            parameterizedTypeInfo.setParameterized(true);
                            parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                        }
                    }

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());
                } else {
                    throw new IllegalStateException();
                }

            } else {
                return new ArrayTypeInfo(new PrimitiveTypeInfo(elementType.toString()), arrayType.getDimensions());
            }
        } else if (type instanceof SimpleType) {
            TypeInfo typeInfo = getTypeInfoFromSimpleType(dependentArtifactSet, javaVersion, importStatementList,
                    (SimpleType) type, owningClassInfo, auditInfo);

            if (Objects.isNull(typeInfo)) {
                return new NullTypeInfo();
            }

            return typeInfo;

        } else if (type instanceof QualifiedType) {
            TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentArtifactSet, javaVersion, importStatementList,
                    (QualifiedType) type, owningClassInfo, auditInfo);

            if (Objects.isNull(typeInfo)) {
                return new NullTypeInfo();
            }

            return typeInfo;

        } else if (type instanceof NameQualifiedType) {
            TypeInfo typeInfo = getTypeInfoFromNameQualifiedType(dependentArtifactSet, javaVersion, importStatementList,
                    (NameQualifiedType) type, owningClassInfo, auditInfo);

            if (Objects.isNull(typeInfo)) {
                return new NullTypeInfo();
            }

            return typeInfo;

        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type internalType = parameterizedType.getType();

            List<TypeInfo> typeArgumentList = getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, parameterizedType.typeArguments(), owningClassInfo, auditInfo);

            if (internalType instanceof SimpleType) {
                TypeInfo typeInfo = getTypeInfoFromSimpleType(dependentArtifactSet, javaVersion, importStatementList,
                        (SimpleType) internalType, owningClassInfo, auditInfo);

                if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                    if (!typeArgumentList.isEmpty()) {
                        parameterizedTypeInfo.setParameterized(true);
                        parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                    }
                }

                return typeInfo;

            } else if (internalType instanceof QualifiedType) {
                TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentArtifactSet, javaVersion, importStatementList,
                        (QualifiedType) internalType, owningClassInfo, auditInfo);

                if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                    if (!typeArgumentList.isEmpty()) {
                        parameterizedTypeInfo.setParameterized(true);
                        parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                    }
                }

                return typeInfo;

            } else if (internalType instanceof NameQualifiedType) {
                TypeInfo typeInfo = getTypeInfoFromNameQualifiedType(dependentArtifactSet, javaVersion,
                        importStatementList, (NameQualifiedType) internalType, owningClassInfo, auditInfo);

                if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                    if (!typeArgumentList.isEmpty()) {
                        parameterizedTypeInfo.setParameterized(true);
                        parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                    }
                }

                return typeInfo;

            } else {
                throw new IllegalStateException();
            }
        } else if (type instanceof UnionType) {
            List<Type> typeList = ((UnionType) type).types();

            /*UnionType can be found for multicatch block exception where type is determined based on the common super
            class of all the types. For simplicity, we will use the first type as type of argument. If we can find
            scenario where this approach does not work, we will improve our approach.*/
            Type firstType = typeList.get(0);
            return getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, firstType, owningClassInfo, auditInfo);

        } else if (type instanceof WildcardType) {
            WildcardType wildCardType = (WildcardType) type;

            Type boundType = wildCardType.getBound();

            if (Objects.nonNull(boundType)) {
                TypeInfo boundTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                        boundType, owningClassInfo, auditInfo);

                //assuming that if boundTypeInfo null, means its a formal type parameter
                if (Objects.isNull(boundTypeInfo) && boundType.isSimpleType()) {
                    String typeName = ((SimpleType) boundType).getName().getFullyQualifiedName();
                    boundTypeInfo = new FormalTypeParameterInfo(typeName, new QualifiedTypeInfo("java.lang.Object"));
                }

                return boundTypeInfo;
            } else {
                return new QualifiedTypeInfo("java.lang.Object");
            }
        } else {
            return new QualifiedTypeInfo(type.toString());
        }
    }

    private static void populateVariableNameMap(Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                Set<VariableDeclarationDto> variableDeclarationDtoSet) {

        for (VariableDeclarationDto variableDeclarationDto : variableDeclarationDtoSet) {
            if (variableNameMap.containsKey(variableDeclarationDto.getName())) {
                Set<VariableDeclarationDto> variableDeclarationSet = variableNameMap.get(variableDeclarationDto.getName());
                variableDeclarationSet.add(variableDeclarationDto);

                variableNameMap.put(variableDeclarationDto.getName(), variableDeclarationSet);
            } else {
                variableNameMap.put(variableDeclarationDto.getName(), new HashSet<>(Arrays.asList(variableDeclarationDto)));
            }
        }
    }

    /*
     * We are replacing formal type parameter with concrete type info if possible.
     * I will write down the scenarios that has to be covered.
     *
     * Scenario 1: (Ljava/util/Map<TP;TR;>;TQ;)V
     * Map class has K,V type parameter
     * this method has map as argument with type parameter P, R.
     *
     * Scenario 2: (TK;TV;)TV;
     * Here method argument uses the same type argument that is defined in the class name
     *
     * Another information to be noted: We will return parameterized type with argument values (which will be concrete
     *  types)
     */
    public static void transformTypeInfoRepresentation(Set<Artifact> dependentArtifactSet,
                                                       String javaVersion,
                                                       List<String> importStatementList,
                                                       OwningClassInfo owningClassInfo,
                                                       List<MethodInfo> methodInfoList,
                                                       List<TypeInfo> argumentTypeInfoList,
                                                       List<TypeInfo> typeArgumentTypeInfoList,
                                                       TypeInfo invokerTypeInfo,
                                                       TypeInfo returnTypeInfo,
                                                       Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                       AuditInfo auditInfo) {

        for (MethodInfo methodInfo : methodInfoList) {
            convertParameterizedTypeIfRequired(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfo, auditInfo);

            Map<String, TypeInfo> replacedTypeInfoMap = new HashMap<>();
            invokerTypeInfo = getOriginalInvoker(invokerTypeInfo, methodInfo, owningClassInfo,
                    dependentArtifactSet, javaVersion, importStatementList, auditInfo);
            Map<String, TypeInfo> formalTypeParameterMap = getInferredFormalTypeParameterMap(invokerTypeInfo,
                    returnTypeInfo, methodInfo, argumentTypeInfoList, typeArgumentTypeInfoList,
                    dependentArtifactSet, javaVersion, importStatementList, owningClassInfo, variableNameMap, auditInfo);

            if (Objects.nonNull(owningClassInfo) && Objects.nonNull(invokerTypeInfo)
                    && owningClassInfo.getQualifiedClassNameSetInHierarchy().get(0).contains(invokerTypeInfo.getQualifiedClassName())) {
                populateFormalTypeParametersOfOwningClass(methodInfo, argumentTypeInfoList, formalTypeParameterMap, replacedTypeInfoMap);
            }

            transformTypeInfoRepresentationUsingInferredFormalTypeParameterMap(formalTypeParameterMap, methodInfo, replacedTypeInfoMap);
            transformTypeRepresentation(methodInfo, replacedTypeInfoMap);
        }
    }

    public static TypeInfo getTypeInfoFromClassName(Set<Artifact> dependentArtifactSet,
                                                    String javaVersion,
                                                    List<String> importStatementList,
                                                    String className,
                                                    OwningClassInfo owningClassInfo,
                                                    AuditInfo auditInfo) {
        if (Objects.isNull(className)) {
            return null;
        }

        if (PrimitiveTypeUtils.isPrimitiveType(className.replaceAll("\\[]", ""))) {
            int dimension = StringUtils.countMatches(className, "[]");

            if (dimension > 0) {
                return new ArrayTypeInfo(new PrimitiveTypeInfo(className.replaceAll("\\[]", "")), dimension);
            } else {
                return new PrimitiveTypeInfo(className);
            }
        }

        auditInfo.incrementClassCount();

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentArtifactSet, javaVersion, importStatementList,
                className, owningClassInfo);

        /*
         * Only for formal type parameter in the class, classInfoList can be empty.
         */
        if (classInfoList.isEmpty()) {
            return null;
        }

        return getTypeInfoFromClassInfo(className, classInfoList.get(0));
    }

    public static List<String> getInnerClassQNameList(AbstractTypeDeclaration abstractTypeDeclaration) {
        if (Objects.isNull(abstractTypeDeclaration)) {
            return Collections.emptyList();
        }

        AbstractTypeDeclarationVisitor abstractTypeDeclarationVisitor = new AbstractTypeDeclarationVisitor();
        abstractTypeDeclaration.accept(abstractTypeDeclarationVisitor);

        List<AbstractTypeDeclaration> abstractTypeDeclarationList =
                abstractTypeDeclarationVisitor.getAbstractTypeDeclarationList();

        List<String> innerClassQNameList = new ArrayList<>();

        for (AbstractTypeDeclaration innerAbstractTypeDeclaration: abstractTypeDeclarationList) {
            innerClassQNameList.add(getDeclaringClassQualifiedName(innerAbstractTypeDeclaration)
                    .replaceAll("\\$", "."));
        }

        return innerClassQNameList;
    }


    private static TypeInfo getReturnParameterizedTypeInfo(ASTNode methodNode,
                                                           Set<Artifact> dependentArtifactSet,
                                                           String javaVersion,
                                                           List<String> importStatementList,
                                                           OwningClassInfo owningClassInfo,
                                                           Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                           AuditInfo auditInfo) {

        if (Objects.nonNull(methodNode.getParent()) && methodNode.getParent() instanceof ReturnStatement) {
            ReturnStatement returnStatement = (ReturnStatement) methodNode.getParent();
            MethodDeclaration methodDeclaration = (MethodDeclaration) getClosestASTNode(returnStatement, MethodDeclaration.class);

            TypeInfo typeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                    methodDeclaration.getReturnType2(), owningClassInfo, auditInfo);

            if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                List<TypeParameter> typeParameterList = methodDeclaration.typeParameters();

                Map<String, Type> typeParameterMap = new HashMap<>();
                for (TypeParameter typeParameter: typeParameterList) {
                    String name = typeParameter.getName().getFullyQualifiedName();

                    if (!typeParameter.typeBounds().isEmpty()) {
                        List<Type> typeParameterBoundList = typeParameter.typeBounds();

                        typeParameterMap.put(name, typeParameterBoundList.get(0));
                    }
                }

                for (TypeInfo typeArgument: parameterizedTypeInfo.getTypeArgumentList()) {
                    if (typeArgument.isFormalTypeParameterInfo()) {
                        FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeArgument;
                        TypeInfo baseType = formalTypeParameterInfo.getBaseTypeInfo();

                        if (baseType.isFormalTypeParameterInfo()
                                && typeParameterMap.containsKey(((FormalTypeParameterInfo) baseType).getTypeParameter())) {

                            FormalTypeParameterInfo baseFormalTypeParameterInfo = (FormalTypeParameterInfo) baseType;
                            Type boundType = typeParameterMap.get(baseFormalTypeParameterInfo.getTypeParameter());

                            TypeInfo boundTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                                    boundType, owningClassInfo, auditInfo);

                            if (Objects.nonNull(boundTypeInfo)
                                    && !boundTypeInfo.getQualifiedClassName().equals(baseFormalTypeParameterInfo.getBaseTypeInfo().getQualifiedClassName())) {
                                baseFormalTypeParameterInfo.setBaseTypeInfo(boundTypeInfo);
                            }
                        }
                    }
                }
            }

            return typeInfo;
        }

        if (Objects.nonNull(methodNode.getParent()) && methodNode.getParent() instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) methodNode.getParent();
            String variableName = variableDeclarationFragment.getName().getFullyQualifiedName();

            VariableDeclarationDto variableDeclarationDto =
                    getVariableDeclarationDtoFromVariableMap(variableName, variableDeclarationFragment.getStartPosition(), variableNameMap);

            if (Objects.nonNull(variableDeclarationDto)) {
                return getTypeInfoFromVariableDeclarationDto(dependentArtifactSet, javaVersion, importStatementList,
                        variableDeclarationDto, auditInfo);
            }
        }

        return null;
    }


    /*
     * There are scenarios when parameterized type is called without type arguments in method signature.
     * (e.g., public Stack getCurrentSeriesPoints() {})
     *
     * Also, for parameterized type argument in method signature has only type arguments. They do not have formal type
     * parameter. So fetched class in order to get formal type parameter and applied type argument against them.
     */
    private static void convertParameterizedTypeIfRequired(Set<Artifact> dependentArtifactSet,
                                                           String javaVersion,
                                                           List<String> importStatementList,
                                                           OwningClassInfo owningClassInfo,
                                                           MethodInfo methodInfo,
                                                           AuditInfo auditInfo) {

        for (int i = 0; i < methodInfo.getArgumentTypeInfoList().size(); i++) {
            TypeInfo argument = methodInfo.getArgumentTypeInfoList().get(i);

            methodInfo.getArgumentTypeInfoList().set(i, convertParameterizedTypeIfRequired(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, argument, auditInfo));
        }

        methodInfo.setReturnTypeInfo(convertParameterizedTypeIfRequired(dependentArtifactSet, javaVersion,
                importStatementList, owningClassInfo, methodInfo.getReturnTypeInfo(), auditInfo));
    }

    private static TypeInfo convertParameterizedTypeIfRequired(Set<Artifact> dependentArtifactSet,
                                                               String javaVersion,
                                                               List<String> importStatementList,
                                                               OwningClassInfo owningClassInfo,
                                                               TypeInfo typeInfo,
                                                               AuditInfo auditInfo) {

        if (typeInfo.isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) typeInfo;

            TypeInfo fetchedTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                    importStatementList, qualifiedTypeInfo.getQualifiedClassName(), owningClassInfo, auditInfo);

            if (Objects.nonNull(fetchedTypeInfo) && fetchedTypeInfo.isParameterizedTypeInfo()) {
                return fetchedTypeInfo;
            }
        }

        return typeInfo;
    }

    private static TypeInfo getOriginalInvoker(TypeInfo invokerTypeInfo,
                                               MethodInfo methodInfo,
                                               OwningClassInfo owningClassInfo,
                                               Set<Artifact> dependentArtifactSet,
                                               String javaVersion,
                                               List<String> importStatementList,
                                               AuditInfo auditInfo) {

        if (Objects.isNull(invokerTypeInfo) && Objects.nonNull(owningClassInfo)
                && owningClassInfo.getQualifiedClassNameSetInHierarchy().stream()
                .anyMatch(s -> s.contains(methodInfo.getQualifiedClassName()))) {

            invokerTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo.getInnerMostClassName(), owningClassInfo, auditInfo);
        }

        /*
         * Invoker class may not be associated with method. Any super class of invoker class can be associated.
         * So added this class check before extracting formal type parameters of invoker type.
         *
         * Also, moved the type arguments to the updated invoker type for formal type resolution.
         */
        if (Objects.nonNull(invokerTypeInfo)
                && !invokerTypeInfo.getQualifiedClassName().equals(methodInfo.getQualifiedClassName())
                && !owningClassInfo.getQualifiedClassNameSetInHierarchy().get(0).contains(methodInfo.getQualifiedClassName())) {
            TypeInfo updatedInvokerTypeInfo = methodInfo.getClassInfo().getTypeInfo();

            if (updatedInvokerTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedUpdatedInvokerTypeInfo = (ParameterizedTypeInfo) updatedInvokerTypeInfo;

                updateTypeArgumentForSuperClass(dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                        invokerTypeInfo, parameterizedUpdatedInvokerTypeInfo, auditInfo);
            }

            invokerTypeInfo = updatedInvokerTypeInfo;
        }

        return invokerTypeInfo;
    }

    private static void updateTypeArgumentForSuperClass(Set<Artifact> dependentArtifactSet,
                                                        String javaVersion,
                                                        List<String> importStatementList,
                                                        OwningClassInfo owningClassInfo,
                                                        TypeInfo invokerTypeInfo,
                                                        ParameterizedTypeInfo updatedInvokerParameterizedTypeInfo,
                                                        AuditInfo auditInfo) {

        JarInfoService jarInfoService = new JarInfoService();
        ClassInfoService classInfoService = new ClassInfoService();
        List<TypeInfo> visitedInvokerTypeInfoList = new ArrayList<>();
        visitedInvokerTypeInfoList.add(invokerTypeInfo);

        List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, null);

        while (!visitedInvokerTypeInfoList.isEmpty()) {
            TypeInfo currentInvokerTypeInfo = visitedInvokerTypeInfoList.get(0);
            visitedInvokerTypeInfoList.remove(0);

            if (currentInvokerTypeInfo.getQualifiedClassName().equals(updatedInvokerParameterizedTypeInfo.getQualifiedClassName())
                    && currentInvokerTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedCurrentTypeInfo = (ParameterizedTypeInfo) currentInvokerTypeInfo;

                updatedInvokerParameterizedTypeInfo.setParameterized(parameterizedCurrentTypeInfo.isParameterized());
                updatedInvokerParameterizedTypeInfo.setTypeArgumentList(parameterizedCurrentTypeInfo.getTypeArgumentList());
                break;
            }

            List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentArtifactSet, javaVersion, importStatementList,
                    currentInvokerTypeInfo.getQualifiedClassName(), owningClassInfo);

            if (!classInfoList.isEmpty()) {
                ClassInfo classInfo = classInfoList.get(0);
                TypeInfo classTypeInfo = classInfo.getTypeInfo();

                List<TypeInfo> typeArgumentList = new ArrayList<>();

                if (Objects.isNull(classInfo.getSignature())) {
                    Set<String> classNameSet = classInfoService
                            .getSuperClassQNameSetUsingMemCache(Collections.singleton(classInfo.getQualifiedName()),
                                    jarIdList, null);

                    List<TypeInfo> superClassTypeInfoList = new ArrayList<>();

                    for (String className: classNameSet) {
                        superClassTypeInfoList.add(getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                                importStatementList, className, owningClassInfo, auditInfo));
                    }

                    visitedInvokerTypeInfoList.addAll(superClassTypeInfoList);
                } else {
                    if (classTypeInfo.isParameterizedTypeInfo()
                            && !((ParameterizedTypeInfo) classTypeInfo).isParameterized()) {
                        typeArgumentList = ((ParameterizedTypeInfo) classTypeInfo).getTypeArgumentList();
                    }

                    ParameterizedSuperClassTypeInfoExtractor parameterizedSuperClassTypeInfoExtractor =
                            new ParameterizedSuperClassTypeInfoExtractor(typeArgumentList);

                    SignatureReader signatureReader = new SignatureReader(classInfo.getSignature());
                    signatureReader.accept(parameterizedSuperClassTypeInfoExtractor);

                    List<ParameterizedTypeInfo> parameterizedSuperClassTypeInfoList = parameterizedSuperClassTypeInfoExtractor.getSuperClassTypeInfoList()
                            .stream()
                            .filter(TypeInfo::isParameterizedTypeInfo)
                            .map(t -> (ParameterizedTypeInfo) t).
                            collect(Collectors.toList());

                    if (currentInvokerTypeInfo.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) currentInvokerTypeInfo).isParameterized()) {
                        Map<String, TypeInfo> formalTypeParameterMap = new LinkedHashMap<>();

                        populateFormalTypeParameterMapForParameterizedTypeInfo((ParameterizedTypeInfo) currentInvokerTypeInfo,
                                (ParameterizedTypeInfo) classTypeInfo, formalTypeParameterMap);

                        for (ParameterizedTypeInfo superClassParameterizedTypeInfo: parameterizedSuperClassTypeInfoList) {
                            for (int i = 0; i < superClassParameterizedTypeInfo.getTypeArgumentList().size(); i++) {
                                TypeInfo typeArgumentInfo = superClassParameterizedTypeInfo.getTypeArgumentList().get(i);
                                TypeInfo updatedTypeArgumentInfo;

                                if (typeArgumentInfo.isFormalTypeParameterInfo()
                                        && formalTypeParameterMap.containsKey(((FormalTypeParameterInfo) typeArgumentInfo).getTypeParameter())) {
                                    updatedTypeArgumentInfo = formalTypeParameterMap.get(((FormalTypeParameterInfo) typeArgumentInfo).getTypeParameter());
                                } else {
                                    updatedTypeArgumentInfo =typeArgumentInfo;
                                }

                                superClassParameterizedTypeInfo.getTypeArgumentList().set(i, updatedTypeArgumentInfo);
                            }
                        }
                    }

                    visitedInvokerTypeInfoList.addAll(parameterizedSuperClassTypeInfoExtractor.getSuperClassTypeInfoList());
                }
            }
        }
    }

    private static void populateInferredTypeInfoMapForParameterizedTypeInfo(Set<Artifact> dependentArtifactSet,
                                                                            String javaVersion,
                                                                            List<String> importStatementList,
                                                                            OwningClassInfo owningClassInfo,
                                                                            ParameterizedTypeInfo parameterizedTypeInfo,
                                                                            Map<String, TypeInfo> inferredTypeInfoMap,
                                                                            AuditInfo auditInfo) {
        TypeInfo classTypeInfo = InferenceUtility.getTypeInfoFromClassName(dependentArtifactSet,
                javaVersion, importStatementList, parameterizedTypeInfo.getQualifiedClassName(),
                owningClassInfo, auditInfo);

        if (Objects.nonNull(classTypeInfo) && classTypeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo classParameterizedTypeInfo = (ParameterizedTypeInfo) classTypeInfo;

            populateFormalTypeParameterMapForParameterizedTypeInfo(parameterizedTypeInfo, classParameterizedTypeInfo, inferredTypeInfoMap);
        }
    }

    private static void populateFormalTypeParameterMapForParameterizedTypeInfo(ParameterizedTypeInfo parameterizedTypeInfo,
                                                                               ParameterizedTypeInfo classParameterizedTypeInfo,
                                                                               Map<String, TypeInfo> inferredTypeInfoMap) {
        int i = 0;
        for (TypeInfo typeArgumentInfo: classParameterizedTypeInfo.getTypeArgumentList()) {
            if (typeArgumentInfo.isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo formalTypeArgParameterInfo = (FormalTypeParameterInfo) typeArgumentInfo;

                if (!inferredTypeInfoMap.containsKey(formalTypeArgParameterInfo.getTypeParameter())) {
                    inferredTypeInfoMap.put(formalTypeArgParameterInfo.getTypeParameter(),
                            parameterizedTypeInfo.getTypeArgumentList().get(i));
                }
            }

            i++;
        }
    }

    private static Map<String, TypeInfo> getInferredFormalTypeParameterMap(TypeInfo invokerTypeInfo,
                                                                           TypeInfo returnTypeInfo,
                                                                           MethodInfo methodInfo,
                                                                           List<TypeInfo> argumentTypeInfoList,
                                                                           List<TypeInfo> typeArgumentTypeInfoList,
                                                                           Set<Artifact> dependentArtifactSet,
                                                                           String javaVersion,
                                                                           List<String> importStatementList,
                                                                           OwningClassInfo owningClassInfo,
                                                                           Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                                           AuditInfo auditInfo) {
        Map<String, TypeInfo> inferredTypeInfoMap = new HashMap<>();

        if (Objects.nonNull(invokerTypeInfo)
                && invokerTypeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo invokerParameterizedTypeInfo = (ParameterizedTypeInfo) invokerTypeInfo;

            if (invokerParameterizedTypeInfo.isParameterized()) {
                populateInferredTypeInfoMapForParameterizedTypeInfo(dependentArtifactSet, javaVersion,
                        importStatementList, owningClassInfo, invokerParameterizedTypeInfo, inferredTypeInfoMap, auditInfo);
            }
        }

        if (!typeArgumentTypeInfoList.isEmpty()) {
            List<TypeInfo> formalTypeParameterList = methodInfo.getFormalTypeParameterList();

            for (int i = 0; i < typeArgumentTypeInfoList.size(); i++) {
                TypeInfo typeArgumentInfo = typeArgumentTypeInfoList.get(i);
                TypeInfo methodTypeArgumentInfo = formalTypeParameterList.get(i);

                if (methodTypeArgumentInfo.isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodTypeArgumentInfo;
                    formalTypeParameterInfo.setBaseTypeInfo(typeArgumentInfo);
                }
            }

            inferredTypeInfoMap.putAll(
                    formalTypeParameterList.stream()
                            .filter(TypeInfo::isFormalTypeParameterInfo)
                            .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                            .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter,
                                    FormalTypeParameterInfo::getBaseTypeInfo))
            );
        } else if (isFormalTypeInferredAllowed(invokerTypeInfo, methodInfo) && !argumentTypeInfoList.isEmpty()) {
            populateInferredFormalTypeParameterMapUsingMethodArguments(methodInfo, argumentTypeInfoList,
                    inferredTypeInfoMap, dependentArtifactSet, javaVersion, importStatementList, owningClassInfo,
                    variableNameMap, auditInfo);
        }

        if (Objects.nonNull(returnTypeInfo)) {
            if (methodInfo.getReturnTypeInfo().isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo methodReturnFormalTypeParameterInfo =
                        (FormalTypeParameterInfo) methodInfo.getReturnTypeInfo();

                if (!inferredTypeInfoMap.containsKey(methodReturnFormalTypeParameterInfo.getTypeParameter())) {
                    inferredTypeInfoMap.put(methodReturnFormalTypeParameterInfo.getTypeParameter(), returnTypeInfo);
                } else {
                    TypeInfo typeInfo = inferredTypeInfoMap.get(methodReturnFormalTypeParameterInfo.getTypeParameter());

                    if (typeInfo.isParameterizedTypeInfo() && !((ParameterizedTypeInfo) typeInfo).isParameterized()
                            && returnTypeInfo.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) returnTypeInfo).isParameterized()) {

                        ParameterizedTypeInfo parameterizedReturnTypeInfo = (ParameterizedTypeInfo) returnTypeInfo;
                        ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                        parameterizedTypeInfo.setParameterized(parameterizedReturnTypeInfo.isParameterized());
                        parameterizedTypeInfo.setTypeArgumentList(parameterizedReturnTypeInfo.getTypeArgumentList());
                    }
                }
            } else if ((methodInfo.getReturnTypeInfo().isParameterizedTypeInfo() || methodInfo.isConstructor())
                    && returnTypeInfo.isParameterizedTypeInfo()
                    && ((ParameterizedTypeInfo) returnTypeInfo).isParameterized()
                    && returnTypeInfo.getQualifiedClassName().equals(methodInfo.getReturnTypeInfo().getQualifiedClassName())) {

                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) returnTypeInfo;
                ParameterizedTypeInfo methodParameterizedTypeInfo = (ParameterizedTypeInfo) methodInfo.getReturnTypeInfo();

                populateFormalTypeParameterFromParameterizedTypeInfo(parameterizedTypeInfo, methodParameterizedTypeInfo, inferredTypeInfoMap);
            }
        }

        return inferredTypeInfoMap;
    }

    private static boolean isFormalTypeInferredAllowed(TypeInfo invokerTypeInfo, MethodInfo methodInfo) {
        if (Objects.nonNull(invokerTypeInfo)) {
            if (invokerTypeInfo.isParameterizedTypeInfo() && !((ParameterizedTypeInfo) invokerTypeInfo).isParameterized()
                    && !methodInfo.isStatic()) {
                return false;
            }
        }

        return true;
    }

    private static void populateInferredFormalTypeParameterMapUsingMethodArguments(MethodInfo methodInfo,
                                                                                   List<TypeInfo> argumentTypeInfoList,
                                                                                   Map<String, TypeInfo> formalTypeInfoMap,
                                                                                   Set<Artifact> dependentArtifactSet,
                                                                                   String javaVersion,
                                                                                   List<String> importStatementList,
                                                                                   OwningClassInfo owningClassInfo,
                                                                                   Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                                                   AuditInfo auditInfo) {

        JarInfoService jarInfoService = new JarInfoService();
        ClassInfoService classInfoService = new ClassInfoService();
        List<TypeInfo> methodArgumentTypeInfoList = methodInfo.getArgumentTypeInfoList();

        for (int i = 0; i < methodArgumentTypeInfoList.size(); i++) {
            TypeInfo methodArgument = methodArgumentTypeInfoList.get(i);
            TypeInfo argument = (methodInfo.isVarargs() && methodArgument.isArrayTypeInfo() && i >= argumentTypeInfoList.size())
                    ? null
                    : argumentTypeInfoList.get(i);

            /*
             * Primitive type cannot be replaced as placeholder of formal type parameter. Replaced with wrapper object.
             */
            if (Objects.nonNull(argument) && argument.isPrimitiveTypeInfo()) {
                argument = new QualifiedTypeInfo(PrimitiveTypeUtils.getPrimitiveWrapperClassQName(argument.getQualifiedClassName()));
            }

            if (methodArgument.isFormalTypeParameterInfo()
                    && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) methodArgument).getTypeParameter())
                    && Objects.nonNull(argument) && !argument.isNullTypeInfo()) {
                FormalTypeParameterInfo formalTypeParameterMethodArgInfo = (FormalTypeParameterInfo) methodArgument;
                formalTypeInfoMap.put(formalTypeParameterMethodArgInfo.getTypeParameter(), argument);

            } else if (methodArgument.isArrayTypeInfo() && Objects.nonNull(argument)) {
                if (argument.isArrayTypeInfo()) {
                    ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;
                    ArrayTypeInfo argArray = (ArrayTypeInfo) argument;

                    if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()
                            && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) methodArgArray.getElementTypeInfo()).getTypeParameter())) {
                        FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();
                        formalTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), argArray.getElementTypeInfo());
                    }
                } else if (argument.isVarargTypeInfo()) {
                    ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;
                    VarargTypeInfo argVararg = (VarargTypeInfo) argument;

                    if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()
                            && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) methodArgArray.getElementTypeInfo()).getTypeParameter())) {
                        FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();
                        formalTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), argVararg.getElementTypeInfo());
                    }
                    //todo: ParameterizedTypeInfo, FormalTypeParameter, functionTypeInfo
                } else if (argument.isQualifiedTypeInfo() || argument.isPrimitiveTypeInfo()) {
                    ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;

                    if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()
                            && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) methodArgArray.getElementTypeInfo()).getTypeParameter())) {

                        List<Set<String>> classHierarchyList = new ArrayList<>();

                        for (int j = i; j < argumentTypeInfoList.size(); j++) {
                            Set<String> classQNameHierarchySet = new LinkedHashSet<>();

                            List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, null);
                            String classQName = argumentTypeInfoList.get(j).getQualifiedClassName();

                            if (argumentTypeInfoList.get(j).isPrimitiveTypeInfo()) {
                                classQName = PrimitiveTypeUtils.getPrimitiveWrapperClassQName(argumentTypeInfoList.get(j).getQualifiedClassName());
                            }

                            Set<String> classNameSet = Collections.singleton(classQName);
                            classQNameHierarchySet.add(classQName);

                            while (!classNameSet.isEmpty()) {
                                classNameSet = classInfoService.getSuperClassQNameSet(classNameSet, jarIdList, null);

                                classQNameHierarchySet.addAll(classNameSet);
                            }

                            classHierarchyList.add(classQNameHierarchySet);
                        }

                        List<String> commonClassQNameList = new ArrayList<>(classHierarchyList.get(0));
                        for (int j = 1; j < classHierarchyList.size(); j++) {
                            commonClassQNameList.retainAll(classHierarchyList.get(j));
                        }

                        if (!commonClassQNameList.isEmpty()) {
                            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();

                            TypeInfo typeInfo = new NullTypeInfo();
                            if (argument.isPrimitiveTypeInfo()) {
                                typeInfo = new PrimitiveTypeInfo(commonClassQNameList.get(0));
                            } else if (argument.isQualifiedTypeInfo()) {
                                typeInfo = new QualifiedTypeInfo(commonClassQNameList.get(0));
                            }

                            formalTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), typeInfo);
                        }
                    }
                }
            } else if (methodArgument.isParameterizedTypeInfo() && Objects.nonNull(argument)) {
                ParameterizedTypeInfo methodArgumentParameterizedTypeInfo = (ParameterizedTypeInfo) methodArgument;

                if (argument.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) argument).isParameterized()) {
                    ParameterizedTypeInfo parameterizedArgTypeInfo = (ParameterizedTypeInfo) argument;

                    populateFormalTypeParameterFromParameterizedTypeInfo(parameterizedArgTypeInfo, methodArgumentParameterizedTypeInfo, formalTypeInfoMap);
                } else if (argument.isFunctionTypeInfo() && Objects.nonNull(((FunctionTypeInfo) argument).getExpression())) {
                    List<TypeInfo> typeArgumentList = new ArrayList<>();
                    FunctionTypeInfo functionTypeInfo = (FunctionTypeInfo) argument;

                    for (TypeInfo typeInfo: methodArgumentParameterizedTypeInfo.getTypeArgumentList()) {
                        if (typeInfo.isFormalTypeParameterInfo()
                                && formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) typeInfo).getTypeParameter())) {
                            typeArgumentList.add(formalTypeInfoMap.get(((FormalTypeParameterInfo) typeInfo).getTypeParameter()));
                        } else {
                            typeArgumentList.add(typeInfo);
                        }
                    }

                    ParameterizedTypeInfo parameterizedArgTypeInfo = new ParameterizedTypeInfo(methodArgumentParameterizedTypeInfo.getQualifiedClassName());
                    parameterizedArgTypeInfo.setParameterized(true);
                    parameterizedArgTypeInfo.setTypeArgumentList(typeArgumentList);

                    Map<String, TypeInfo> parameterizedTypeArgumentMap
                            = getTypeArgumentMap(dependentArtifactSet, javaVersion, importStatementList,
                            owningClassInfo, parameterizedArgTypeInfo, auditInfo);

                    TypeInfo returnTypeInfo =
                            getMethodArgumentAndReturnTypeInfoTuple(dependentArtifactSet, javaVersion, parameterizedTypeArgumentMap,
                            parameterizedArgTypeInfo.getQualifiedClassName())._2();

                    if (Objects.nonNull(returnTypeInfo) && returnTypeInfo.isFormalTypeParameterInfo()) {
                        FormalTypeParameterInfo returnFormalTypeParameterInfo = (FormalTypeParameterInfo) returnTypeInfo;

                        if (formalTypeInfoMap.containsKey(returnFormalTypeParameterInfo.getTypeParameter())) {
                            continue;
                        }

                        if (functionTypeInfo.getExpression() instanceof LambdaExpression) {
                            LambdaExpression lambdaExpression = (LambdaExpression) functionTypeInfo.getExpression();
                            ASTNode body = lambdaExpression.getBody();

                            if (body instanceof MethodInvocation) {
                                MethodInvocation methodInvocation = (MethodInvocation) body;

                                Set<VariableDeclarationDto> variableDeclarationDtoSet =
                                        getVariableDeclarationDtoSet(dependentArtifactSet, javaVersion, importStatementList,
                                                owningClassInfo, lambdaExpression, typeArgumentList);

                                populateVariableNameMap(variableNameMap, variableDeclarationDtoSet);

                                List<MethodInfo> methodInfoList =
                                        getEligibleMethodInfoList(dependentArtifactSet, javaVersion, methodInvocation,
                                                importStatementList, variableNameMap, owningClassInfo, auditInfo);

                                if (!methodInfoList.isEmpty()) {
                                    MethodInfo innerLambdaMethodInfo = methodInfoList.get(0);
                                    formalTypeInfoMap.put(returnFormalTypeParameterInfo.getTypeParameter(), innerLambdaMethodInfo.getReturnTypeInfo());
                                }
                            } else if (body instanceof Block) {
                                Block bodyBlock = (Block) body;

                                List<TypeInfo> lambdaBodyReturnTypeInfoList = new ArrayList<>();

                                bodyBlock.accept(new ASTVisitor() {
                                    @Override
                                    public boolean visit(SingleVariableDeclaration singleVariableDeclaration) {
                                        OwningClassInfo owningClassInfo = getOwningClassInfo(singleVariableDeclaration, dependentArtifactSet, javaVersion,
                                                importStatementList, jarInfoService, classInfoService, auditInfo);

                                        VariableDeclarationDto variableDeclarationDto =
                                                getVariableDeclarationDto(singleVariableDeclaration, owningClassInfo);

                                        populateVariableNameMap(variableNameMap, Collections.singleton(variableDeclarationDto));

                                        return true;
                                    }

                                    @Override
                                    public void endVisit(VariableDeclarationExpression variableDeclarationExpression) {
                                        OwningClassInfo owningClassInfo = getOwningClassInfo(variableDeclarationExpression, dependentArtifactSet,
                                                javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                                        List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();

                                        List<VariableDeclarationDto> variableDeclarationDtoList =
                                                getVariableDeclarationDtoList(variableDeclarationExpression.getType(),
                                                        null, fragmentList, owningClassInfo);

                                        populateVariableNameMap(variableNameMap, new HashSet<>(variableDeclarationDtoList));
                                    }

                                    @Override
                                    public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                                        OwningClassInfo owningClassInfo = getOwningClassInfo(variableDeclarationStatement, dependentArtifactSet, javaVersion,
                                                importStatementList, jarInfoService, classInfoService, auditInfo);

                                        List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                                        List<VariableDeclarationDto> variableDeclarationDtoList =
                                                getVariableDeclarationDtoList(variableDeclarationStatement.getType(),
                                                        null, fragmentList, owningClassInfo);

                                        populateVariableNameMap(variableNameMap, new HashSet<>(variableDeclarationDtoList));
                                    }

                                    @Override
                                    public boolean visit(ReturnStatement returnStatement) {
                                        Expression expression = returnStatement.getExpression();

                                        OwningClassInfo owningClassInfo = getOwningClassInfo(expression, dependentArtifactSet, javaVersion,
                                                importStatementList, jarInfoService, classInfoService, auditInfo);

                                        lambdaBodyReturnTypeInfoList.add(getTypeInfoFromExpression(dependentArtifactSet, javaVersion,
                                                importStatementList, variableNameMap, expression, owningClassInfo, auditInfo));

                                        return false;
                                    }
                                });

                                if (!lambdaBodyReturnTypeInfoList.isEmpty()) {
                                    formalTypeInfoMap.put(returnFormalTypeParameterInfo.getTypeParameter(), lambdaBodyReturnTypeInfoList.get(0));
                                }
                            }
                        } else if (functionTypeInfo.getExpression() instanceof CreationReference) {
                            CreationReference creationReference = (CreationReference) functionTypeInfo.getExpression();

                            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                                    creationReference, importStatementList, owningClassInfo, auditInfo);

                            if (!methodInfoList.isEmpty()) {
                                MethodInfo argumentMethodInfo = methodInfoList.get(0);
                                formalTypeInfoMap.put(returnFormalTypeParameterInfo.getTypeParameter(),
                                        argumentMethodInfo.getReturnTypeInfo());
                            }
                        } else if (functionTypeInfo.getExpression() instanceof ExpressionMethodReference) {
                            ExpressionMethodReference expressionMethodReference = (ExpressionMethodReference) functionTypeInfo.getExpression();

                            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                                    expressionMethodReference, importStatementList, variableNameMap, owningClassInfo, auditInfo);

                            if (!methodInfoList.isEmpty()) {
                                MethodInfo argumentMethodInfo = methodInfoList.get(0);
                                formalTypeInfoMap.put(returnFormalTypeParameterInfo.getTypeParameter(),
                                        argumentMethodInfo.getReturnTypeInfo());
                            }
                        } else if (functionTypeInfo.getExpression() instanceof SuperMethodReference) {
                            SuperMethodReference superMethodReference = (SuperMethodReference) functionTypeInfo.getExpression();

                            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                                    superMethodReference, importStatementList, owningClassInfo, auditInfo);

                            if (!methodInfoList.isEmpty()) {
                                MethodInfo argumentMethodInfo = methodInfoList.get(0);
                                formalTypeInfoMap.put(returnFormalTypeParameterInfo.getTypeParameter(),
                                        argumentMethodInfo.getReturnTypeInfo());
                            }
                        }  else if (functionTypeInfo.getExpression() instanceof TypeMethodReference) {
                            TypeMethodReference typeMethodReference = (TypeMethodReference) functionTypeInfo.getExpression();

                            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                                    typeMethodReference, importStatementList, owningClassInfo, auditInfo);

                            if (!methodInfoList.isEmpty()) {
                                MethodInfo argumentMethodInfo = methodInfoList.get(0);
                                formalTypeInfoMap.put(returnFormalTypeParameterInfo.getTypeParameter(),
                                        argumentMethodInfo.getReturnTypeInfo());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void populateFormalTypeParameterFromParameterizedTypeInfo(ParameterizedTypeInfo parameterizedTypeInfo,
                                                                             ParameterizedTypeInfo methodParameterizedTypeInfo,
                                                                             Map<String, TypeInfo> formalTypeInfoMap) {

        if (parameterizedTypeInfo.getTypeArgumentList().size() == methodParameterizedTypeInfo.getTypeArgumentList().size()) {
            for (int j = 0; j < parameterizedTypeInfo.getTypeArgumentList().size(); j++) {
                TypeInfo argumentTypeInfo = parameterizedTypeInfo.getTypeArgumentList().get(j);
                TypeInfo methodArgumentTypeInfo = methodParameterizedTypeInfo.getTypeArgumentList().get(j);

                if (methodArgumentTypeInfo.isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo formalMethodArgumentTypeParameterInfo = (FormalTypeParameterInfo) methodArgumentTypeInfo;

                    if (!formalTypeInfoMap.containsKey(formalMethodArgumentTypeParameterInfo.getTypeParameter())) {
                        formalTypeInfoMap.put(formalMethodArgumentTypeParameterInfo.getTypeParameter(), argumentTypeInfo);
                    }
                }
            }
        }
    }

    private static void populateFormalTypeParametersOfOwningClass(MethodInfo methodInfo,
                                                                  List<TypeInfo> argumentTypeInfoList,
                                                                  Map<String, TypeInfo> formalTypeParameterMap,
                                                                  Map<String, TypeInfo> replacedTypeInfoMap) {

        for (int i = 0; i < methodInfo.getArgumentTypeInfoList().size(); i++) {
            TypeInfo methodArgumentTypeInfo = methodInfo.getArgumentTypeInfoList().get(i);
            boolean varargCondition = methodInfo.isVarargs() && methodArgumentTypeInfo.isArrayTypeInfo() && i >= argumentTypeInfoList.size();
            TypeInfo argumentTypeInfo = (argumentTypeInfoList.isEmpty() || varargCondition)
                    ? null
                    : argumentTypeInfoList.get(i);

            if (methodArgumentTypeInfo.isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo methodArgumentFormalTypeParameterInfo = (FormalTypeParameterInfo) methodArgumentTypeInfo;
                TypeInfo baseType = methodArgumentFormalTypeParameterInfo;

                if (Objects.nonNull(argumentTypeInfo) && argumentTypeInfo.isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo argumentFormalTypeParameterInfo = (FormalTypeParameterInfo) argumentTypeInfo;
                    if (!argumentFormalTypeParameterInfo.getTypeParameter().equals(methodArgumentFormalTypeParameterInfo.getTypeParameter())) {
                        if (!formalTypeParameterMap.containsKey(((FormalTypeParameterInfo) methodArgumentTypeInfo).getTypeParameter())) {
                            baseType = argumentFormalTypeParameterInfo;
                            formalTypeParameterMap.put(methodArgumentFormalTypeParameterInfo.getTypeParameter(), baseType);
                        }
                    }
                }

                replacedTypeInfoMap.put(methodArgumentFormalTypeParameterInfo.getTypeParameter(), baseType);
            }
        }

        TypeInfo returnTypeInfo = methodInfo.getReturnTypeInfo();
        if (returnTypeInfo.isFormalTypeParameterInfo()) {
            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) returnTypeInfo;

            TypeInfo baseType = formalTypeParameterMap.containsKey(formalTypeParameterInfo.getTypeParameter())
                    ? formalTypeParameterMap.get(formalTypeParameterInfo.getTypeParameter())
                    : formalTypeParameterInfo;

            replacedTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), baseType);
        }
    }

    private static void transformTypeInfoRepresentationUsingInferredFormalTypeParameterMap(Map<String, TypeInfo> formalTypeParameterMap,
                                                                                           MethodInfo methodInfo,
                                                                                           Map<String, TypeInfo> replacedTypeInfoMap) {
        if (!formalTypeParameterMap.isEmpty()) {
            /*
             * Method Arguments:
             */
            replaceFormalTypeInfoUsingInferredTypeInfoMap(methodInfo.getArgumentTypeInfoList(),
                    formalTypeParameterMap, replacedTypeInfoMap);

            /*
             * Method return type:
             */
            methodInfo.setReturnTypeInfo(replaceFormalTypeInfoUsingInferredTypeInfoMap(methodInfo.getReturnTypeInfo(),
                    formalTypeParameterMap, replacedTypeInfoMap));
        }
    }

    private static void replaceFormalTypeInfoUsingInferredTypeInfoMap(List<TypeInfo> typeInfoList,
                                                                      Map<String, TypeInfo> formalTypeParameterInfoMap,
                                                                      Map<String, TypeInfo> replacedTypeInfoMap) {

        for (int i = 0; i < typeInfoList.size(); i++) {
            TypeInfo typeInfo = typeInfoList.get(i);

            if (typeInfo.isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo formalTypeParameterTypeInfo = (FormalTypeParameterInfo) typeInfo;

                if (formalTypeParameterInfoMap.containsKey(formalTypeParameterTypeInfo.getTypeParameter())) {
                    typeInfoList.set(i, formalTypeParameterInfoMap.get(formalTypeParameterTypeInfo.getTypeParameter()));

                    replacedTypeInfoMap.put(formalTypeParameterTypeInfo.getTypeParameter(),
                            formalTypeParameterInfoMap.get(formalTypeParameterTypeInfo.getTypeParameter()));
                }
            }

            else if (typeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;
                List<TypeInfo> typeArgumentList = parameterizedTypeInfo.getTypeArgumentList();

                replaceFormalTypeInfoUsingInferredTypeInfoMap(typeArgumentList, formalTypeParameterInfoMap,
                        replacedTypeInfoMap);

            } else if (typeInfo.isArrayTypeInfo()) {
                ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

                if (arrayTypeInfo.getElementTypeInfo().isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) arrayTypeInfo.getElementTypeInfo();

                    if (formalTypeParameterInfoMap.containsKey(formalTypeParameterInfo.getTypeParameter())) {
                        arrayTypeInfo.setElementTypeInfo(formalTypeParameterInfoMap.get(formalTypeParameterInfo.getTypeParameter()));
                        replacedTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), arrayTypeInfo.getElementTypeInfo());
                    }
                }
            }
        }
    }

    private static TypeInfo replaceFormalTypeInfoUsingInferredTypeInfoMap(TypeInfo typeInfo,
                                                                          Map<String, TypeInfo> formalTypeParameterInfoMap,
                                                                          Map<String, TypeInfo> replacedTypeInfoMap) {

        List<TypeInfo> typeInfoList = new ArrayList<>(Collections.singleton(typeInfo));
        replaceFormalTypeInfoUsingInferredTypeInfoMap(typeInfoList, formalTypeParameterInfoMap,
                replacedTypeInfoMap);

        return typeInfoList.get(0);
    }

    private static void transformTypeRepresentation(MethodInfo methodInfo, Map<String, TypeInfo> formalTypeParameterMap) {
        if (!formalTypeParameterMap.isEmpty() && Objects.nonNull(methodInfo.getSignature())
                && formalTypeParameterMap.values().stream().noneMatch(TypeInfo::isNullTypeInfo)) {
            GenericTypeResolutionAdapter genericTypeResolutionAdapter =
                    new GenericTypeResolutionAdapter(formalTypeParameterMap);
            SignatureReader reader = new SignatureReader(methodInfo.getSignature());
            reader.accept(genericTypeResolutionAdapter);

            methodInfo.setArgumentTypes(genericTypeResolutionAdapter.getMethodArgumentTypes());
            methodInfo.setReturnType(genericTypeResolutionAdapter.getMethodReturnType());
        }
    }

    private static void populateVariableNameMapForMethod(Set<Artifact> dependentArtifactSet,
                                                         String javaVersion,
                                                         List<String> importStatementList,
                                                         Map<String, OwningClassInfo> owningClassInfoMap,
                                                         ASTNode node,
                                                         Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                         JarInfoService jarInfoService,
                                                         ClassInfoService classInfoService,
                                                         AuditInfo auditInfo) {

        MethodDeclaration methodDeclaration = (MethodDeclaration) getClosestASTNode(node, MethodDeclaration.class);

        if (Objects.nonNull(methodDeclaration)) {
            Set<VariableDeclarationDto> methodParameterVariableDeclarationSet =
                    getMethodParameterVariableDeclarationDtoList(dependentArtifactSet, javaVersion,
                            importStatementList, methodDeclaration, owningClassInfoMap, jarInfoService,
                            classInfoService, auditInfo);

            populateVariableNameMap(variableNameMap, methodParameterVariableDeclarationSet);

            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getLocalVariableDtoList(dependentArtifactSet, javaVersion, importStatementList,
                            methodDeclaration.getBody(), owningClassInfoMap, jarInfoService, classInfoService, auditInfo);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);

            AnonymousClassDeclaration anonymousClassDeclaration =
                    (AnonymousClassDeclaration) getClosestASTNode(methodDeclaration, AnonymousClassDeclaration.class);

            if (Objects.nonNull(anonymousClassDeclaration)) {
                populateVariableNameMapForMethod(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfoMap, anonymousClassDeclaration, variableNameMap, jarInfoService,
                        classInfoService, auditInfo);
            }

            TypeDeclaration typeDeclaration =
                    (TypeDeclaration) getClosestASTNode(methodDeclaration, TypeDeclaration.class);

            if (Objects.nonNull(typeDeclaration)) {
                populateVariableNameMapForMethod(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfoMap, typeDeclaration, variableNameMap, jarInfoService,
                        classInfoService, auditInfo);
            }
        }
    }

    private static void populateVariableNameMapForStaticBlock(Set<Artifact> dependentArtifactSet,
                                                              String javaVersion,
                                                              List<String> importStatementList,
                                                              Map<String, OwningClassInfo> owningClassInfoMap,
                                                              ASTNode node,
                                                              Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                              JarInfoService jarInfoService,
                                                              ClassInfoService classInfoService,
                                                              AuditInfo auditInfo) {

        Initializer initializer = (Initializer) getClosestASTNode(node, Initializer.class);

        if (Objects.nonNull(initializer)) {
            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getLocalVariableDtoList(dependentArtifactSet, javaVersion, importStatementList,
                            initializer.getBody(), owningClassInfoMap, jarInfoService, classInfoService, auditInfo);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);
        }
    }

    private static void populateVariableNameMapForLambdaExpression(Set<Artifact> dependentArtifactSet,
                                                                   String javaVersion,
                                                                   List<String> importStatementList,
                                                                   Map<String, OwningClassInfo> owningClassInfoMap,
                                                                   ASTNode node,
                                                                   Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                                   JarInfoService jarInfoService,
                                                                   ClassInfoService classInfoService,
                                                                   AuditInfo auditInfo) {

        LambdaExpression lambdaExpression = (LambdaExpression) getClosestASTNode(node, LambdaExpression.class);

        if (Objects.nonNull(lambdaExpression)) {
            OwningClassInfo owningClassInfo = getOwningClassInfo(lambdaExpression, dependentArtifactSet, javaVersion,
                    importStatementList, owningClassInfoMap, jarInfoService, classInfoService, auditInfo);

            List<TypeInfo> argumentTypeInfoList = getArgumentTypeInfoListForLambdaExpression(dependentArtifactSet, javaVersion,
                    importStatementList, owningClassInfoMap, variableNameMap, lambdaExpression, jarInfoService, classInfoService,
                    auditInfo);

            Set<VariableDeclarationDto> variableDeclarationDtoSet =
                    getVariableDeclarationDtoSet(dependentArtifactSet, javaVersion, importStatementList,
                            owningClassInfo, lambdaExpression, argumentTypeInfoList);

            populateVariableNameMap(variableNameMap, variableDeclarationDtoSet);
        }
    }

    /*
     * There are 3 scenarios for varargs
     * 1: There may be no argument passed
     * 2: An array can be passed
     * 3: Same type of multiple arguments can be passed
     */
    public static void conversionToVarargsMethodArgument(List<MethodInfo> methodInfoList) {

        for (MethodInfo methodInfo : methodInfoList) {
            if (methodInfo.isVarargs()) {
                int lastIndex = methodInfo.getArgumentTypeInfoList().size() - 1;
                ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) methodInfo.getArgumentTypeInfoList().get(lastIndex);
                methodInfo.getArgumentTypeInfoList().set(lastIndex,
                        new VarargTypeInfo(arrayTypeInfo.getElementTypeInfo()));
            }
        }
    }

    private static Set<VariableDeclarationDto> getMethodParameterVariableDeclarationDtoList(Set<Artifact> dependentArtifactSet,
                                                                                            String javaVersion,
                                                                                            List<String> importStatementList,
                                                                                            MethodDeclaration methodDeclaration,
                                                                                            Map<String, OwningClassInfo> owningClassInfoMap,
                                                                                            JarInfoService jarInfoService,
                                                                                            ClassInfoService classInfoService,
                                                                                            AuditInfo auditInfo) {
        if (methodDeclaration != null) {
            List<SingleVariableDeclaration> declarationList = methodDeclaration.parameters();

            String firstEnclosingQName = getFirstEnclosingClassQName(methodDeclaration, dependentArtifactSet,
                    javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);
            OwningClassInfo owningClassInfo;

            if (owningClassInfoMap.containsKey(firstEnclosingQName)) {
                owningClassInfo = owningClassInfoMap.get(firstEnclosingQName);
            } else {
                owningClassInfo = getOwningClassInfo(methodDeclaration, dependentArtifactSet, javaVersion,
                        importStatementList, jarInfoService, classInfoService, auditInfo);
                owningClassInfoMap.put(firstEnclosingQName, owningClassInfo);
            }

            return declarationList.stream()
                    .map(declaration -> getVariableDeclarationDto(declaration, owningClassInfo))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    /*
     * this method is used to get local variables from a methodDeclaration (Method) or Initializer (static block).
     */
    private static Set<VariableDeclarationDto> getLocalVariableDtoList(Set<Artifact> dependentArtifactSet,
                                                                       String javaVersion,
                                                                       List<String> importStatementList,
                                                                       Block bodyBlock,
                                                                       Map<String, OwningClassInfo> owningClassInfoMap,
                                                                       JarInfoService jarInfoService,
                                                                       ClassInfoService classInfoService,
                                                                       AuditInfo auditInfo) {
        if (Objects.isNull(bodyBlock)) {
            return Collections.emptySet();
        }

        Set<VariableDeclarationDto> localVariableDtoSet = new HashSet<>();

        bodyBlock.accept(new ASTVisitor() {
            @Override
            public boolean visit(SingleVariableDeclaration singleVariableDeclaration) {
                String firstEnclosingClassQName = getFirstEnclosingClassQName(singleVariableDeclaration,
                        dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                OwningClassInfo owningClassInfo;

                if (owningClassInfoMap.containsKey(firstEnclosingClassQName)) {
                    owningClassInfo = owningClassInfoMap.get(firstEnclosingClassQName);
                } else {
                    owningClassInfo = getOwningClassInfo(singleVariableDeclaration, dependentArtifactSet, javaVersion,
                            importStatementList, jarInfoService, classInfoService, auditInfo);

                    owningClassInfoMap.put(firstEnclosingClassQName, owningClassInfo);
                }

                VariableDeclarationDto variableDeclarationDto =
                        getVariableDeclarationDto(singleVariableDeclaration, owningClassInfo);

                localVariableDtoSet.add(variableDeclarationDto);

                return true;
            }

            @Override
            public void endVisit(VariableDeclarationExpression variableDeclarationExpression) {
                String firstEnclosingClassQName = getFirstEnclosingClassQName(variableDeclarationExpression,
                        dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                OwningClassInfo owningClassInfo;

                if (owningClassInfoMap.containsKey(firstEnclosingClassQName)) {
                    owningClassInfo = owningClassInfoMap.get(firstEnclosingClassQName);
                } else {
                    owningClassInfo = getOwningClassInfo(variableDeclarationExpression, dependentArtifactSet,
                            javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                    owningClassInfoMap.put(firstEnclosingClassQName, owningClassInfo);
                }

                List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(variableDeclarationExpression.getType(),
                                null, fragmentList, owningClassInfo);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }

            @Override
            public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                String firstEnclosingClassQName = getFirstEnclosingClassQName(variableDeclarationStatement,
                        dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

                OwningClassInfo owningClassInfo;

                if (owningClassInfoMap.containsKey(firstEnclosingClassQName)) {
                    owningClassInfo = owningClassInfoMap.get(firstEnclosingClassQName);
                } else {
                    owningClassInfo = getOwningClassInfo(variableDeclarationStatement, dependentArtifactSet, javaVersion,
                            importStatementList, jarInfoService, classInfoService, auditInfo);

                    owningClassInfoMap.put(firstEnclosingClassQName, owningClassInfo);
                }

                List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(variableDeclarationStatement.getType(), null,
                                fragmentList, owningClassInfo);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }
        });

        return localVariableDtoSet;
    }

    private static TypeInfo convertVarargsIfRequired(TypeInfo typeInfo, boolean isVarargs) {
        if (Objects.nonNull(typeInfo) && isVarargs) {
            return new VarargTypeInfo(typeInfo);
        } else {
            return typeInfo;
        }
    }

    private static VariableDeclarationDto getVariableDeclarationDto(SingleVariableDeclaration declaration,
                                                                    OwningClassInfo owningClassInfo) {
        String name = declaration.getName().getFullyQualifiedName();
        Type declarationType = declaration.getType();
        ASTNode scopedNode = getVariableDeclarationScopedNode(declaration);

        if (scopedNode != null) {
            int startOffset = scopedNode.getStartPosition();
            int endOffSet = startOffset + scopedNode.getLength();

            return new VariableDeclarationDto(name, new VariableScope(startOffset, endOffSet), declarationType,
                    owningClassInfo, declaration.isVarargs());

        } else {
            return null;
        }
    }

    private static VariableDeclarationDto getVariableDeclarationDtoFromVariableMap(String variableName,
                                                                                   Expression expression,
                                                                                   Map<String, Set<VariableDeclarationDto>> variableNameMap) {
        return getVariableDeclarationDtoFromVariableMap(variableName, expression.getParent().getStartPosition(), variableNameMap);
    }

    private static VariableDeclarationDto getVariableDeclarationDtoFromVariableMap(String variableName,
                                                                                   int position,
                                                                                   Map<String, Set<VariableDeclarationDto>> variableNameMap) {
        if (variableNameMap.containsKey(variableName)) {
            Set<VariableDeclarationDto> variableDeclarationDtoSet = variableNameMap.get(variableName);
            List<VariableDeclarationDto> selectedVariableDeclarationDto = new ArrayList<>();

            for (VariableDeclarationDto vd : variableDeclarationDtoSet) {
                VariableScope vs = vd.getScope();

                if (vs.getStartOffset() <= position && position <= vs.getEndOffset()) {
                    selectedVariableDeclarationDto.add(vd);
                }
            }

            if (selectedVariableDeclarationDto.isEmpty()) {
                return null;
            }

            selectedVariableDeclarationDto.sort(Comparator.comparingInt(o -> (position - o.getScope().getStartOffset())));

            return selectedVariableDeclarationDto.get(0);
        } else {
            return null;
        }
    }

    private static String getPrimitiveType(NumberLiteral numberLiteral) {
        String token = numberLiteral.getToken();

        if (StringUtils.isEmpty(token)) {
            return null;
        }

        if (token.contains(".")) {
            if (token.endsWith("f") || token.endsWith("F")) {
                return "float";
            } else {
                return "double";
            }
        } else {
            if (token.endsWith("l") || token.endsWith("L")) {
                return "long";
            } else {
                return "int";
            }
        }
    }

    public static List<TypeInfo> getTypeInfoList(Set<Artifact> dependentArtifactSet,
                                                   String javaVersion,
                                                   List<String> importStatementList,
                                                   List<Type> typeList,
                                                  OwningClassInfo owningClassInfo,
                                                 AuditInfo auditInfo) {
        List<TypeInfo> typeInfoList = new ArrayList<>();

        for (Type type : typeList) {
            typeInfoList.add(getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, type,
                    owningClassInfo, auditInfo));
        }

        return typeInfoList;
    }

    private static List<AnonymousClassDeclaration> getAnonymousClassDeclarationList(BodyDeclaration declaration) {
        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(declaration);

        AnonymousClassVisitor anonymousClassVisitor = new AnonymousClassVisitor();
        abstractTypeDeclaration.accept(anonymousClassVisitor);

        return anonymousClassVisitor.getAnonymousClassDeclarationList();
    }

    private static List<VariableDeclarationDto> getVariableDeclarationDtoList(Type declarationType,
                                                                              Integer fieldVariableStartOffset,
                                                                              List<VariableDeclarationFragment> fragmentList,
                                                                              OwningClassInfo owningClassInfo) {
        return fragmentList.stream().map(fragment ->
                getVariableDeclarationDto(fragment, fieldVariableStartOffset, null, declarationType, owningClassInfo))
                .collect(Collectors.toList());
    }

    private static VariableDeclarationDto getVariableDeclarationDto(VariableDeclarationFragment fragment,
                                                                    Integer fieldVariableStartOffset,
                                                                    TypeInfo declarationTypeInfo,
                                                                    Type declarationType,
                                                                    OwningClassInfo owningClassInfo) {
        ASTNode scopedNode = getVariableDeclarationScopedNode(fragment);
        String name = fragment.getName().getFullyQualifiedName();

        int startOffset = Objects.nonNull(fieldVariableStartOffset)
                ? fieldVariableStartOffset
                : fragment.getStartPosition();

        int endOffSet = startOffset + (scopedNode != null ? scopedNode.getLength() : 0);

        if (Objects.nonNull(declarationTypeInfo)) {
            return new VariableDeclarationDto(name, declarationTypeInfo, new VariableScope(startOffset, endOffSet));

        } else {
            return new VariableDeclarationDto(name, new VariableScope(startOffset, endOffSet), declarationType,
                    owningClassInfo, false);
        }
    }

    private static ASTNode getVariableDeclarationScopedNode(VariableDeclaration variableDeclaration) {
        if (variableDeclaration instanceof SingleVariableDeclaration) {
            return variableDeclaration.getParent();
        } else if (variableDeclaration instanceof VariableDeclarationFragment) {
            return variableDeclaration.getParent().getParent();
        }

        return null;
    }

    private static TypeInfo getTypeInfoFromSimpleType(Set<Artifact> dependentArtifactSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      SimpleType simpleType,
                                                      OwningClassInfo owningClassInfo,
                                                      AuditInfo auditInfo) {
        String name = simpleType.getName().getFullyQualifiedName();

        MethodDeclaration methodDeclaration = (MethodDeclaration) InferenceUtility.getClosestASTNode(simpleType, MethodDeclaration.class);

        /*
         * Checking formal type parameter of thw owning method
         */
        if (Objects.nonNull(methodDeclaration) && !methodDeclaration.typeParameters().isEmpty()) {
            List<TypeParameter> typeParameterList = methodDeclaration.typeParameters();

            for (TypeParameter typeParameter: typeParameterList) {
                String typeParameterName = typeParameter.getName().getFullyQualifiedName();

                if (name.equals(typeParameterName)) {
                    List<Type> boundTypeList = typeParameter.typeBounds();

                    if (!boundTypeList.isEmpty()) {
                        Type boundType = boundTypeList.get(0) instanceof ParameterizedType
                                ? ((ParameterizedType) boundTypeList.get(0)).getType()
                                : boundTypeList.get(0);

                        TypeInfo boundTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                                boundType, owningClassInfo, auditInfo);

                        if (Objects.isNull(boundTypeInfo)) {
                            boundTypeInfo = new QualifiedTypeInfo("java.lang.Object");
                        }

                        return new FormalTypeParameterInfo(name, new QualifiedTypeInfo(boundTypeInfo.getQualifiedClassName()));
                    } else {
                        return new FormalTypeParameterInfo(name, new QualifiedTypeInfo("java.lang.Object"));
                    }
                }
            }
        }

        /*
         * Checking formal type parameter of thw owning class
         */
        if (Objects.nonNull(owningClassInfo)
                && CollectionUtils.isNotEmpty(owningClassInfo.getAccessibleFormalTypeParameterList())
                && owningClassInfo.getAccessibleFormalTypeParameterList().stream()
                .anyMatch(ft -> ft.getTypeParameter().equals(name))) {

            return owningClassInfo.getAccessibleFormalTypeParameterList()
                    .stream()
                    .filter(ft -> ft.getTypeParameter().equals(name))
                    .collect(Collectors.toList())
                    .get(0);
        }

        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name, owningClassInfo, auditInfo);
    }

    private static TypeInfo getTypeInfoFromNameQualifiedType(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             List<String> importStatementList,
                                                             NameQualifiedType nameQualifiedType,
                                                             OwningClassInfo owningClassInfo,
                                                             AuditInfo auditInfo) {

        String qualifierName = nameQualifiedType.getQualifier().getFullyQualifiedName();
        String simpleName = nameQualifiedType.getName().getFullyQualifiedName();
        String name = qualifierName.concat(".").concat(simpleName);

        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name, owningClassInfo, auditInfo);
    }

    private static TypeInfo getTypeInfoFromQualifiedType(Set<Artifact> dependentArtifactSet,
                                                         String javaVersion,
                                                         List<String> importStatementList,
                                                         QualifiedType qualifiedType,
                                                         OwningClassInfo owningClassInfo,
                                                         AuditInfo auditInfo) {

        String name = qualifiedType.getName().getFullyQualifiedName();
        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name, owningClassInfo, auditInfo);
    }

    private static TypeInfo getTypeInfoFromFieldName(Set<Artifact> dependentArtifactSet,
                                                     String javaVersion,
                                                     List<String> importStatementList,
                                                     String fieldName,
                                                     OwningClassInfo owningClassInfo,
                                                     AuditInfo auditInfo) {

        auditInfo.incrementFieldCount();
        List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentArtifactSet,
                javaVersion, importStatementList, fieldName, owningClassInfo);

        if (fieldInfoList.isEmpty()) {
            return null;
        }

        FieldInfo fieldInfo = fieldInfoList.get(0);

        if (Objects.nonNull(fieldInfo.getSignature())) {
            FieldSignatureFormalTypeParameterExtractor formalTypeParameterExtractor
                    = new FieldSignatureFormalTypeParameterExtractor();

            SignatureReader reader = new SignatureReader(fieldInfo.getSignature());
            reader.accept(formalTypeParameterExtractor);

            TypeInfo typeInfoExtractedFromFieldSignature = formalTypeParameterExtractor.getTypeInfo();

            if (typeInfoExtractedFromFieldSignature.isFormalTypeParameterInfo()) {
                String typeParameter = ((FormalTypeParameterInfo) typeInfoExtractedFromFieldSignature).getTypeParameter();

                TypeInfo fieldClassTypeInfo = fieldInfo.getClassInfo().getTypeInfo();

                if (fieldClassTypeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) fieldClassTypeInfo;

                    return parameterizedTypeInfo.getTypeArgumentList().stream()
                            .filter(TypeInfo::isFormalTypeParameterInfo)
                            .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                            .filter(formalTypeParameterInfo -> formalTypeParameterInfo.getTypeParameter().equals(typeParameter))
                            .collect(Collectors.toList())
                            .get(0);
                } else {
                    return null;
                }

            } else {
                String fieldTypeClassName = typeInfoExtractedFromFieldSignature.getQualifiedClassName();

                TypeInfo typeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                        fieldTypeClassName, owningClassInfo, auditInfo);

                if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                    if (typeInfoExtractedFromFieldSignature.isParameterizedTypeInfo()) {
                        ParameterizedTypeInfo parameterizedTypeInfoExtractedFromSig = (ParameterizedTypeInfo) typeInfoExtractedFromFieldSignature;

                        parameterizedTypeInfo.setTypeArgumentList(parameterizedTypeInfoExtractedFromSig.getTypeArgumentList());
                        parameterizedTypeInfo.setParameterized(true);
                    }
                }

                return typeInfo;
            }
        } else {
            String fieldTypeClassName = fieldInfo.getType().getClassName();

            return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                    fieldTypeClassName, owningClassInfo, auditInfo);
        }
    }

    public static TypeInfo getTypeInfoFromClassInfo(String className, ClassInfo classInfo) {
        TypeInfo classTypeInfo = classInfo.getTypeInfo();

        if (className.contains("[]")) {
            int numberOfDimension = StringUtils.countMatches(className, "[]");
            return new ArrayTypeInfo(classTypeInfo, numberOfDimension);

        } else {
            return classTypeInfo;
        }
    }

    public static List<String> getNonEnclosingAccessibleClassListForInstantiation(ASTNode methodNode,
                                                                                  List<String> enclosingQClassNameList) {

        AbstractTypeDeclaration abstractTypeDeclaration = null;
        ASTNode node = methodNode;

        List<String> orderedInnerQNameList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof AbstractTypeDeclaration) {
                abstractTypeDeclaration = (AbstractTypeDeclaration) node;
                List<String> innerClassQNameList = getInnerClassQNameList(abstractTypeDeclaration);

                for (String innerClassQName: innerClassQNameList) {
                    if (!orderedInnerQNameList.contains(innerClassQName)) {
                        orderedInnerQNameList.add(innerClassQName);
                    }
                }
            }

            node = node.getParent();
        }

        orderedInnerQNameList.removeIf(enclosingQClassNameList::contains);

        return orderedInnerQNameList;
    }

    /*
     * Enclosing class list will consist of anonymous inner class and enclosing class declarations.
     */
    public static List<String> getAllEnclosingClassList(ASTNode node,
                                                        Set<Artifact> dependentArtifactSet,
                                                        String javaVersion,
                                                        List<String> importStatementList,
                                                        JarInfoService jarInfoService,
                                                        ClassInfoService classInfoService,
                                                        AuditInfo auditInfo) {

        List<String> enclosingClassList = new ArrayList<>();

        String qualifiedClassName =
                getClassInstanceCreationQualifiedName(node, dependentArtifactSet, javaVersion, importStatementList,
                        jarInfoService, classInfoService, auditInfo);

        if (Objects.nonNull(qualifiedClassName)) {
            enclosingClassList.add(qualifiedClassName);
        }

        enclosingClassList.addAll(getEnclosingClassList(node));

        return enclosingClassList;
    }

    public static List<FormalTypeParameterInfo> getAccessibleFormalTypeParameterList(Set<Artifact> dependentArtifactSet,
                                                                                     String javaVersion,
                                                                                     List<String> importStatementList,
                                                                                     OwningClassInfo owningClassInfo,
                                                                                     ASTNode methodOrFieldNode,
                                                                                     AuditInfo auditInfo) {
        ASTNode node = methodOrFieldNode;
        List<FormalTypeParameterInfo> accessibleFormalTypeParameterList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof TypeDeclaration) {
                TypeDeclaration typeDeclaration = (TypeDeclaration) node;
                String qualifiedClassName =
                        getDeclaringClassQualifiedName(typeDeclaration).replaceAll("\\$", ".");

                TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                        importStatementList, qualifiedClassName, owningClassInfo, auditInfo);

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
                        TypeInfo baseTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                                typeList.get(0), owningClassInfo, auditInfo);

                        if (Objects.isNull(baseTypeInfo) && typeList.get(0).isSimpleType()) {
                            String typeName = ((SimpleType) typeList.get(0)).getName().getFullyQualifiedName();
                            baseTypeInfo = new FormalTypeParameterInfo(typeName, new QualifiedTypeInfo("java.lang.Object"));
                        }

                        accessibleFormalTypeParameterList.add(
                                new FormalTypeParameterInfo(typeParameter.getName().getFullyQualifiedName(), baseTypeInfo));
                    }
                }
            }

            node = node.getParent();
        }

        return accessibleFormalTypeParameterList;
    }

    private static String getFirstEnclosingClassQName(ASTNode node,
                                                      Set<Artifact> dependentArtifactSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      JarInfoService jarInfoService,
                                                      ClassInfoService classInfoService,
                                                      AuditInfo auditInfo) {
        String classInstanceQualifiedClassName =
                getClassInstanceCreationQualifiedName(node, dependentArtifactSet, javaVersion, importStatementList,
                        jarInfoService, classInfoService, auditInfo);

        if (Objects.nonNull(classInstanceQualifiedClassName)) {
            return classInstanceQualifiedClassName;
        } else {
            AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(node);

            return getDeclaringClassQualifiedName(abstractTypeDeclaration).replaceAll("\\$", ".");
        }
    }

    private static List<String> getEnclosingClassList(ASTNode methodNode) {
        ASTNode node = methodNode;

        List<String> enclosingClassNameList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) node;

                String className = getDeclaringClassQualifiedName(abstractTypeDeclaration);
                enclosingClassNameList.add(className.replaceAll("\\$", "."));
            }

            node = node.getParent();
        }

        return enclosingClassNameList;
    }

    private static String getClassInstanceCreationQualifiedName(ASTNode node,
                                                                Set<Artifact> dependentArtifactSet,
                                                                String javaVersion,
                                                                List<String> importStatementList,
                                                                JarInfoService jarInfoService,
                                                                ClassInfoService classInfoService,
                                                                AuditInfo auditInfo) {

         AnonymousClassDeclaration anonymousClassDeclaration =
                (AnonymousClassDeclaration) getClosestASTNode(node.getParent(), AnonymousClassDeclaration.class);

        if (Objects.isNull(anonymousClassDeclaration)) {
            return null;
        }

        ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation)
                getClosestASTNode(anonymousClassDeclaration, ClassInstanceCreation.class);

        if (Objects.isNull(classInstanceCreation)) {
            return null;
        }

        List<String> enclosingClassQNameList =
                getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion,
                        importStatementList, jarInfoService, classInfoService, auditInfo);

        List<String> nonEnclosingAccessibleClassQNameList =
                getNonEnclosingAccessibleClassListForInstantiation(classInstanceCreation, enclosingClassQNameList);

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                enclosingClassQNameList, nonEnclosingAccessibleClassQNameList, jarInfoService, classInfoService);

        owningClassInfo.setAccessibleFormalTypeParameterList(
                getAccessibleFormalTypeParameterList(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfo, classInstanceCreation, auditInfo));

        Type type = classInstanceCreation.getType();
        TypeInfo classTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion,
                importStatementList, type, owningClassInfo, auditInfo);

        return Objects.nonNull(classTypeInfo) ? classTypeInfo.getQualifiedClassName() : null;
    }

    private static Integer getArgumentIndexFromMethod(MethodInvocation methodInvocation, Expression argumentExpression) {
        List<Expression> argumentList = methodInvocation.arguments();

        Integer argumentIndex = null;
        for (int i = 0; i < argumentList.size(); i++) {
            if (argumentList.get(i).equals(argumentExpression)) {
                argumentIndex = i;
            }
        }

        return argumentIndex;
    }

    private static TypeInfo getLambdaArgumentTypeInfo(Set<Artifact> dependentArtifactSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      OwningClassInfo owningClassInfo,
                                                      Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                      MethodInvocation methodInvocation,
                                                      LambdaExpression lambdaExpression,
                                                      AuditInfo auditInfo) {
        Integer argumentIndex = getArgumentIndexFromMethod(methodInvocation, lambdaExpression);

        List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                methodInvocation, importStatementList, variableNameMap, owningClassInfo, auditInfo);

        if (!methodInfoList.isEmpty() && Objects.nonNull(argumentIndex)) {
            return methodInfoList.get(0).getArgumentTypeInfoList().get(argumentIndex);
        }

        return null;
    }

    private static Map<String, TypeInfo> getTypeArgumentMap(Set<Artifact> dependentArtifactSet,
                                                            String javaVersion,
                                                            List<String> importStatementList,
                                                            OwningClassInfo owningClassInfo,
                                                            ParameterizedTypeInfo parameterizedTypeInfo,
                                                            AuditInfo auditInfo) {

        if (parameterizedTypeInfo.getTypeArgumentList().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TypeInfo> typeArgumentMap = new LinkedHashMap<>();

        List<TypeInfo> typeArgumentList = parameterizedTypeInfo.getTypeArgumentList();
        TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                importStatementList, parameterizedTypeInfo.getQualifiedClassName(), owningClassInfo, auditInfo);

        if (Objects.nonNull(classTypeInfo) && classTypeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo classParameterizedTypeInfo = (ParameterizedTypeInfo) classTypeInfo;

            int i = 0;
            for (TypeInfo classTypeArgument: classParameterizedTypeInfo.getTypeArgumentList()) {
                if (classTypeArgument.isFormalTypeParameterInfo()) {
                    typeArgumentMap.put(((FormalTypeParameterInfo) classTypeArgument).getTypeParameter(), typeArgumentList.get(i));
                    i++;
                }
            }
        }

        return typeArgumentMap;
    }

    private static Tuple2<List<TypeInfo>, TypeInfo> getMethodArgumentAndReturnTypeInfoTuple(Set<Artifact> dependentArtifactSet,
                                                                                            String javaVersion,
                                                                                            Map<String, TypeInfo> argumentTypeInfoMap,
                                                                                            String functionalInterfaceClassQName) {

        List<TypeInfo> argumentInfoList = new ArrayList<>();
        TypeInfo returnTypeInfo = new NullTypeInfo();

        if (!argumentTypeInfoMap.isEmpty()) {
            MethodInfo abstractMethod =
                    TypeInferenceAPI.getAbstractMethodInfoOfFunctionalInterface(dependentArtifactSet,
                            javaVersion, functionalInterfaceClassQName);

            if (Objects.nonNull(abstractMethod)) {
                for (TypeInfo argumentTypeInfo : abstractMethod.getArgumentTypeInfoList()) {
                    if (argumentTypeInfo.isFormalTypeParameterInfo()) {
                        argumentInfoList.add(argumentTypeInfoMap.get(((FormalTypeParameterInfo) argumentTypeInfo).getTypeParameter()));
                    } else {
                        argumentInfoList.add(argumentTypeInfo);
                    }
                }


                if (Objects.nonNull(abstractMethod.getReturnTypeInfo())) {
                    if (abstractMethod.getReturnTypeInfo().isFormalTypeParameterInfo()) {
                        returnTypeInfo = argumentTypeInfoMap.get(((FormalTypeParameterInfo) abstractMethod.getReturnTypeInfo()).getTypeParameter());
                    } else {
                        returnTypeInfo = abstractMethod.getReturnTypeInfo();
                    }
                }
            }
        }

        return new Tuple2<>(argumentInfoList, returnTypeInfo);
    }

    private static OwningClassInfo getOwningClassInfo(ASTNode astNode,
                                                      Set<Artifact> dependentArtifactSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      Map<String, OwningClassInfo> owningClassInfoMap,
                                                      JarInfoService jarInfoService,
                                                      ClassInfoService classInfoService,
                                                      AuditInfo auditInfo) {

        String firstEnclosingClassQName = getFirstEnclosingClassQName(astNode,
                dependentArtifactSet, javaVersion, importStatementList, jarInfoService, classInfoService, auditInfo);

        OwningClassInfo owningClassInfo;

        if (owningClassInfoMap.containsKey(firstEnclosingClassQName)) {
            owningClassInfo = owningClassInfoMap.get(firstEnclosingClassQName);
        } else {
            owningClassInfo = getOwningClassInfo(astNode, dependentArtifactSet, javaVersion,
                    importStatementList, jarInfoService, classInfoService, auditInfo);

            owningClassInfoMap.put(firstEnclosingClassQName, owningClassInfo);
        }

        return owningClassInfo;
    }

    private static List<TypeInfo> getArgumentTypeInfoListForLambdaExpression(Set<Artifact> dependentArtifactSet,
                                                                             String javaVersion,
                                                                             List<String> importStatementList,
                                                                             Map<String, OwningClassInfo> owningClassInfoMap,
                                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                                             LambdaExpression lambdaExpression,
                                                                             JarInfoService jarInfoService,
                                                                             ClassInfoService classInfoService,
                                                                             AuditInfo auditInfo) {
        List<TypeInfo> typeInfoList = new ArrayList<>();

        OwningClassInfo owningClassInfo = getOwningClassInfo(lambdaExpression, dependentArtifactSet, javaVersion,
                importStatementList, owningClassInfoMap, jarInfoService, classInfoService, auditInfo);

        if (Objects.nonNull(lambdaExpression.getParent())) {
            if (lambdaExpression.getParent() instanceof MethodInvocation) {
                MethodInvocation methodInvocation = (MethodInvocation) lambdaExpression.getParent();

                populateVariableNameMapForLambdaExpression(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfoMap, methodInvocation, variableNameMap, jarInfoService, classInfoService, auditInfo);

                TypeInfo funcInterfaceTypeInfo = getLambdaArgumentTypeInfo(dependentArtifactSet, javaVersion,
                        importStatementList, owningClassInfo, variableNameMap, methodInvocation, lambdaExpression, auditInfo);

                if (Objects.nonNull(funcInterfaceTypeInfo) && funcInterfaceTypeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) funcInterfaceTypeInfo;

                    Map<String, TypeInfo> typeArgumentMap =
                            getTypeArgumentMap(dependentArtifactSet, javaVersion, importStatementList,
                                    owningClassInfo, parameterizedTypeInfo, auditInfo);

                    typeInfoList = getMethodArgumentAndReturnTypeInfoTuple(dependentArtifactSet, javaVersion, typeArgumentMap,
                            funcInterfaceTypeInfo.getQualifiedClassName())._1();
                }
            }
        }

        return typeInfoList;
    }

    private static Set<VariableDeclarationDto> getVariableDeclarationDtoSet(Set<Artifact> dependentArtifactSet,
                                                                            String javaVersion,
                                                                            List<String> importStatementList,
                                                                            OwningClassInfo owningClassInfo,
                                                                            LambdaExpression lambdaExpression,
                                                                            List<TypeInfo> lambdaArgumentTypeInfoList) {
        Set<VariableDeclarationDto> variableDeclarationDtoSet = new LinkedHashSet<>();
        List<VariableDeclaration> lambdaArgumentDeclarationList = lambdaExpression.parameters();

        if (!lambdaArgumentDeclarationList.isEmpty() && lambdaArgumentDeclarationList.get(0) instanceof SingleVariableDeclaration) {
            for (VariableDeclaration lambdaArgumentDeclaration : lambdaArgumentDeclarationList) {
                SingleVariableDeclaration singleVariableDeclaration = (SingleVariableDeclaration) lambdaArgumentDeclaration;

                VariableDeclarationDto variableDeclarationDto =
                        getVariableDeclarationDto(singleVariableDeclaration, owningClassInfo);

                variableDeclarationDtoSet.add(variableDeclarationDto);
            }
        } else {
            int i = 0;
            for (VariableDeclaration lambdaArgumentDeclaration : lambdaArgumentDeclarationList) {
                VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) lambdaArgumentDeclaration;
                TypeInfo variableTypeInfo = i < lambdaArgumentTypeInfoList.size() ? lambdaArgumentTypeInfoList.get(i) : new NullTypeInfo();

                variableDeclarationDtoSet.add(getVariableDeclarationDto(variableDeclarationFragment,
                        null, variableTypeInfo, null, null));
                i++;
            }
        }

        return variableDeclarationDtoSet;
    }

    private static TypeInfo getTypeInfoFromVariableDeclarationDto(Set<Artifact> dependentArtifactSet,
                                                                  String javaVersion,
                                                                  List<String> importStatementList,
                                                                  VariableDeclarationDto variableDeclarationDto,
                                                                  AuditInfo auditInfo) {
        if (Objects.isNull(variableDeclarationDto.getTypeInfo())) {
            TypeInfo typeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                    variableDeclarationDto.getType(), variableDeclarationDto.getOwningClassInfo(), auditInfo);

            if (Objects.isNull(typeInfo)) {
                typeInfo = new NullTypeInfo();
            }

            return convertVarargsIfRequired(typeInfo, variableDeclarationDto.isVarargs());
        }

        return variableDeclarationDto.getTypeInfo();
    }

}
