package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.TypeInferenceAPI;
import ca.concordia.jaranalyzer.TypeInferenceFluentAPI;
import ca.concordia.jaranalyzer.models.*;
import ca.concordia.jaranalyzer.models.typeInfo.*;
import ca.concordia.jaranalyzer.util.signaturevisitor.ClassSignatureFormalTypeParameterExtractor;
import ca.concordia.jaranalyzer.util.signaturevisitor.FieldSignatureFormalTypeParameterExtractor;
import ca.concordia.jaranalyzer.util.signaturevisitor.GenericTypeResolutionAdapter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import org.objectweb.asm.signature.SignatureReader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtility {

    public static final List<String> PRIMITIVE_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean"));

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
                                                             OwningClassInfo owningClassInfo) {

        String methodName = methodInvocation.getName().getIdentifier();
        int numberOfParameters = methodInvocation.arguments().size();
        List<Expression> argumentList = methodInvocation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

        Expression expression = methodInvocation.getExpression();

        TypeInfo invokerClassTypeInfo = null;
        String invokerClassName = null;

        if (Objects.nonNull(expression)) {
            invokerClassTypeInfo = InferenceUtility.getTypeInfoFromExpression(dependentArtifactSet, javaVersion,
                    importStatementList, variableNameMap, expression, owningClassInfo);

            invokerClassName = invokerClassTypeInfo.getQualifiedClassName();
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerClassName(invokerClassName)
                .setOwningClassInfo(owningClassInfo);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
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
                owningClassInfo, methodInfoList, argumentTypeInfoList, invokerClassTypeInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             SuperMethodInvocation superMethodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             OwningClassInfo owningClassInfo) {

        String methodName = superMethodInvocation.getName().getIdentifier();
        int numberOfParameters = superMethodInvocation.arguments().size();
        List<Expression> argumentList = superMethodInvocation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

        BodyDeclaration bodyDeclaration =
                (BodyDeclaration) InferenceUtility.getClosestASTNode(superMethodInvocation, BodyDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(bodyDeclaration);
        String invokerClassName = className.replace("%", "").replace("$", ".");

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerClassName(invokerClassName)
                .setOwningClassInfo(owningClassInfo)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
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
                owningClassInfo, methodInfoList, argumentTypeInfoList, null);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             ClassInstanceCreation classInstanceCreation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             OwningClassInfo owningClassInfo) {

        String methodName = classInstanceCreation.getType().toString();
        int numberOfParameters = classInstanceCreation.arguments().size();
        List<Expression> argumentList = classInstanceCreation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentArtifactSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassInfo);

        Type type = classInstanceCreation.getType();
        TypeInfo invokerClassTypeInfo = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                importStatementList, type, owningClassInfo);
        String invokerClassName = Objects.nonNull(invokerClassTypeInfo)
                ? invokerClassTypeInfo.getQualifiedClassName()
                : null;

        if (type.isParameterizedType() && ((ParameterizedType) type).typeArguments().isEmpty()) {
            VariableDeclarationStatement variableDeclarationStatement
                    = (VariableDeclarationStatement) InferenceUtility.getClosestASTNode(classInstanceCreation, VariableDeclarationStatement.class);

            if (Objects.nonNull(variableDeclarationStatement)) {
                Type returnType = variableDeclarationStatement.getType();

                if (returnType.isParameterizedType()) {
                    ParameterizedType parameterizedReturnType = (ParameterizedType) returnType;

                    if (!parameterizedReturnType.typeArguments().isEmpty()) {
                        List<Type> returnTypeArgumentList = parameterizedReturnType.typeArguments();

                        List<TypeInfo> returnTypeInfoArgumentList =
                                getTypeInfoList(dependentArtifactSet, javaVersion, importStatementList,
                                        returnTypeArgumentList, owningClassInfo);

                        assert invokerClassTypeInfo.isParameterizedTypeInfo();

                        ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) invokerClassTypeInfo;
                        parameterizedTypeInfo.setParameterized(true);
                        parameterizedTypeInfo.setTypeArgumentList(returnTypeInfoArgumentList);
                    }
                }
            }
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentArtifactSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerClassName(invokerClassName)
                .setOwningClassInfo(owningClassInfo);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfo, methodInfoList, argumentTypeInfoList, invokerClassTypeInfo);

        return methodInfoList;
    }

    public static Map<String, Set<VariableDeclarationDto>> getVariableNameMap(Set<Artifact> dependentArtifactSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              ASTNode methodExpression,
                                                                              OwningClassInfo owningClassInfo) {

        assert methodExpression instanceof MethodInvocation
                || methodExpression instanceof SuperMethodInvocation
                || methodExpression instanceof ClassInstanceCreation
                || methodExpression instanceof ConstructorInvocation
                || methodExpression instanceof SuperConstructorInvocation;

        Map<String, Set<VariableDeclarationDto>> variableNameMap = new HashMap<>();

        Set<VariableDeclarationDto> fieldVariableDeclarationSet =
                getFieldVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                        methodExpression, owningClassInfo);

        populateVariableNameMap(variableNameMap, fieldVariableDeclarationSet);

        populateVariableNameMapForMethod(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfo, methodExpression, variableNameMap);

        populateVariableNameMapForStaticBlock(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfo, methodExpression, variableNameMap);

        return variableNameMap;
    }

    public static List<TypeInfo> getArgumentTypeInfoList(Set<Artifact> dependentArtifactSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                          List<Expression> argumentList,
                                                          OwningClassInfo owningClassInfo) {
        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();

        for (Expression argument : argumentList) {
            TypeInfo typeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList,
                    variableNameMap, argument, owningClassInfo);

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
                                                                                 OwningClassInfo owningClassInfo) {

        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(node);

        if (abstractTypeDeclaration instanceof TypeDeclaration) {
            TypeDeclaration typeDeclaration = (TypeDeclaration) abstractTypeDeclaration;

            FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();

            return Arrays.stream(fieldDeclarations).map(fieldDeclaration -> {
                List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

                return getVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                        fieldDeclaration.getType(), fragmentList, owningClassInfo);
            }).flatMap(Collection::stream).collect(Collectors.toSet());

        } else {
            return Collections.emptySet();
        }

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
                                                      OwningClassInfo owningClassInfo) {
        if (expression == null) {
            return null;
        }

        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(expression);

        if (expression instanceof NullLiteral) {
            return new NullTypeInfo();

        } else if (expression instanceof ThisExpression) {
            ThisExpression thisExpression = (ThisExpression) expression;
            String className = thisExpression.getQualifier() != null ? thisExpression.getQualifier().getFullyQualifiedName()
                    : getDeclaringClassQualifiedName(typeDeclaration);

            className = className.replace("%", "").replace("$", ".");

            return new QualifiedTypeInfo(className);

        } else if (expression instanceof TypeLiteral) {
            Type argumentType = ((TypeLiteral) expression).getType();
            TypeInfo argumentTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                    argumentType, owningClassInfo);

            ParameterizedTypeInfo parameterizedClassTypeInfo = new ParameterizedTypeInfo("java.lang.Class");
            parameterizedClassTypeInfo.setParameterized(true);
            parameterizedClassTypeInfo.setTypeArgumentList(Collections.singletonList(
                    new FormalTypeParameterInfo("T", argumentTypeInfo)));

            return parameterizedClassTypeInfo;
        } else if (expression instanceof ParenthesizedExpression) {
            ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    parenthesizedExpression.getExpression(), owningClassInfo);


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
                    variableNameMap, fieldAccessExpression, owningClassInfo);

            String className = fieldAccessTypeInfo.getName();

            String name = fieldAccess.getName().getFullyQualifiedName();

            if (Objects.nonNull(className)) {
                name = className + "." + name;
            }

            TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                    importStatementList, name, owningClassInfo);

            assert Objects.nonNull(fieldTypeInfo);

            return fieldTypeInfo;
        } else if (expression instanceof SuperFieldAccess) {
            SuperFieldAccess superFieldAccess = (SuperFieldAccess) expression;

            String name = superFieldAccess.getName().getFullyQualifiedName();

            TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                    importStatementList, name, owningClassInfo);

            assert Objects.nonNull(fieldTypeInfo);

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
                    then, owningClassInfo);
            TypeInfo elseExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    elseExp, owningClassInfo);

            return !thenExpressionTypeInfo.isNullTypeInfo() ? thenExpressionTypeInfo : elseExpressionTypeInfo;
        } else if (expression instanceof CastExpression) {
            Type castedType = ((CastExpression) expression).getType();

            return getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, castedType, owningClassInfo);

        } else if (expression instanceof NumberLiteral) {
            return new PrimitiveTypeInfo(getPrimitiveType((NumberLiteral) expression));

        } else if (expression instanceof ArrayCreation) {
            ArrayCreation arrayCreation = (ArrayCreation) expression;

            ArrayType arrayType = arrayCreation.getType();

            return getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, arrayType, owningClassInfo);
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
                    variableNameMap, array, owningClassInfo);

            assert typeInfo.isArrayTypeInfo();

            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            return arrayTypeInfo.getDimension() > 1
                    ? new ArrayTypeInfo(arrayTypeInfo.getElementTypeInfo(), arrayTypeInfo.getDimension() - 1)
                    : arrayTypeInfo.getElementTypeInfo();

        } else if (expression instanceof InfixExpression) {
            InfixExpression infixExpression = (InfixExpression) expression;

            Expression left = infixExpression.getLeftOperand();
            Expression right = infixExpression.getRightOperand();
            InfixExpression.Operator operator = infixExpression.getOperator();

            TypeInfo leftExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet, javaVersion,
                    importStatementList, variableNameMap, left, owningClassInfo);
            TypeInfo rightExpressionTypeInfo = getTypeInfoFromExpression(dependentArtifactSet,
                    javaVersion, importStatementList, variableNameMap, right, owningClassInfo);

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
                        && ("java.lang.String".equals(leftExpressionTypeInfo.getQualifiedClassName())
                        || "java.lang.String".equals(rightExpressionTypeInfo.getQualifiedClassName()))) {

                    return new PrimitiveTypeInfo("java.lang.String");
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
                    prefixExpression.getOperand(), owningClassInfo);

        } else if (expression instanceof PostfixExpression) {
            PostfixExpression postfixExpression = (PostfixExpression) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                    postfixExpression.getOperand(), owningClassInfo);

        } else if (expression instanceof Name) {
            String name = ((Name) expression).getFullyQualifiedName();

            if (expression instanceof QualifiedName) {
                String firstPart = name.substring(0, name.indexOf("."));
                VariableDeclarationDto selected = getClassNameFromVariableMap(firstPart, expression, variableNameMap);
                String className = selected != null ? selected.getTypeInfo().getQualifiedClassName() : null;

                if (className != null) {
                    name = className + name.substring(name.indexOf("."));
                }

                TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                        importStatementList, name, owningClassInfo);

                if (Objects.nonNull(fieldTypeInfo)) {
                    return fieldTypeInfo;
                } else {
                    return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name,
                            owningClassInfo);
                }
            } else if (expression instanceof SimpleName) {
                VariableDeclarationDto selected = getClassNameFromVariableMap(name, expression, variableNameMap);
                TypeInfo classTypeInfo = selected != null ? selected.getTypeInfo() : null;

                if (Objects.nonNull(classTypeInfo)) {
                    return classTypeInfo;
                } else {
                    TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentArtifactSet, javaVersion,
                            importStatementList, name, owningClassInfo);

                    if (Objects.nonNull(fieldTypeInfo)) {
                        return fieldTypeInfo;
                    } else {
                        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                                name, owningClassInfo);
                    }
                }
            } else {
                throw new IllegalStateException();
            }

        } else if (expression instanceof ClassInstanceCreation) {
            ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;

            List<TypeInfo> typeArgumentList = getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, classInstanceCreation.typeArguments(), owningClassInfo);

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    classInstanceCreation, importStatementList, variableNameMap, owningClassInfo);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new NullTypeInfo();
            } else {
                String className = methodInfoList.get(0).getClassInfo().getQualifiedName();

                TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                        importStatementList, className, owningClassInfo);

                if (Objects.nonNull(classTypeInfo) && classTypeInfo.isParameterizedTypeInfo()) {
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

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    methodInvocation, importStatementList, variableNameMap, owningClassInfo);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new NullTypeInfo();
            } else {
                return methodInfoList.get(0).getReturnTypeInfo();
            }
        } else if (expression instanceof SuperMethodInvocation) {
            SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) expression;

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentArtifactSet, javaVersion,
                    superMethodInvocation, importStatementList, variableNameMap, owningClassInfo);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new NullTypeInfo();
            } else {
                return methodInfoList.get(0).getReturnTypeInfo();
            }
        } else if (expression instanceof LambdaExpression) {
            LambdaExpression lambdaExpression = (LambdaExpression) expression;

            ASTNode body = lambdaExpression.getBody();

            if (body instanceof Expression) {
                Expression bodyExpression = (Expression) body;

                return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList, variableNameMap,
                        bodyExpression, owningClassInfo);
            } else {
                return new NullTypeInfo();
            }

        } else if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;

            return getTypeInfoFromExpression(dependentArtifactSet, javaVersion, importStatementList,
                    variableNameMap, assignment.getLeftHandSide(), owningClassInfo);

        } else {
            return null;
        }
    }

    public static TypeInfo getTypeInfo(Set<Artifact> dependentArtifactSet,
                                        String javaVersion,
                                        List<String> importStatementList,
                                        Type type,
                                        OwningClassInfo owningClassInfo) {
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
                            importStatementList, ((SimpleType) elementType), owningClassInfo);

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());

                } else if (elementType instanceof QualifiedType) {
                    TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentArtifactSet, javaVersion,
                            importStatementList, (QualifiedType) elementType, owningClassInfo);

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());

                } else if (elementType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) elementType;

                    List<TypeInfo> typeArgumentList = getTypeInfoList(dependentArtifactSet, javaVersion,
                            importStatementList, parameterizedType.typeArguments(), owningClassInfo);

                    TypeInfo typeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                            parameterizedType.getType(), owningClassInfo);

                    assert typeInfo.isParameterizedTypeInfo();

                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                    if (!typeArgumentList.isEmpty()) {
                        parameterizedTypeInfo.setParameterized(true);
                        parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                    }

                    return new ArrayTypeInfo(parameterizedTypeInfo, arrayType.getDimensions());
                } else {
                    throw new IllegalStateException();
                }

            } else {
                return new ArrayTypeInfo(new PrimitiveTypeInfo(elementType.toString()), arrayType.getDimensions());
            }
        } else if (type instanceof SimpleType) {
            return getTypeInfoFromSimpleType(dependentArtifactSet, javaVersion, importStatementList,
                    (SimpleType) type, owningClassInfo);

        } else if (type instanceof QualifiedType) {
            return getTypeInfoFromQualifiedType(dependentArtifactSet, javaVersion, importStatementList,
                    (QualifiedType) type, owningClassInfo);


        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type internalType = parameterizedType.getType();

            List<TypeInfo> typeArgumentList = getTypeInfoList(dependentArtifactSet, javaVersion,
                    importStatementList, parameterizedType.typeArguments(), owningClassInfo);

            if (internalType instanceof SimpleType) {
                TypeInfo typeInfo = getTypeInfoFromSimpleType(dependentArtifactSet, javaVersion, importStatementList,
                        (SimpleType) internalType, owningClassInfo);

                assert typeInfo.isParameterizedTypeInfo();

                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                if (!typeArgumentList.isEmpty()) {
                    parameterizedTypeInfo.setParameterized(true);
                    parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                }

                return parameterizedTypeInfo;

            } else if (internalType instanceof QualifiedType) {
                TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentArtifactSet, javaVersion, importStatementList,
                        (QualifiedType) internalType, owningClassInfo);

                assert typeInfo.isParameterizedTypeInfo();

                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                if (!typeArgumentList.isEmpty()) {
                    parameterizedTypeInfo.setParameterized(true);
                    parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                }

                return parameterizedTypeInfo;

            } else {
                throw new IllegalStateException();
            }
        } else if (type instanceof UnionType) {
            List<Type> typeList = ((UnionType) type).types();

            /*UnionType can be found for multicatch block exception where type is determined based on the common super
            class of all the types. For simplicity, we will use the first type as type of argument. If we can find
            scenario where this approach does not work, we will improve our approach.*/
            Type firstType = typeList.get(0);
            return getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, firstType, owningClassInfo);

        } else if (type instanceof WildcardType) {
            WildcardType wildCardType = (WildcardType) type;

            Type boundType = wildCardType.getBound();

            if (Objects.nonNull(boundType)) {
                return getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, boundType, owningClassInfo);
            } else {
                return new QualifiedTypeInfo("java.lang.Object");
            }
        } else {
            return new QualifiedTypeInfo(type.toString());
        }
    }

    public static boolean isPrimitiveType(String argumentTypeClassName) {
        return PRIMITIVE_TYPE_LIST.contains(argumentTypeClassName);
    }

    private static void populateVariableNameMap(Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                Set<VariableDeclarationDto> variableDeclarationDtoList) {

        for (VariableDeclarationDto declarationDto : variableDeclarationDtoList) {
            if (variableNameMap.containsKey(declarationDto.getName())) {
                Set<VariableDeclarationDto> variableDeclarationSet = variableNameMap.get(declarationDto.getName());
                variableDeclarationSet.add(declarationDto);

                variableNameMap.put(declarationDto.getName(), variableDeclarationSet);
            } else {
                variableNameMap.put(declarationDto.getName(), new HashSet<>(Arrays.asList(declarationDto)));
            }
        }
    }

    private static TypeInfo replaceFormalTypeInfoUsingInvokerTypeParameterMap(TypeInfo typeInfo,
                                                                          Map<String, FormalTypeParameterInfo> formalTypeParameterInfoMap,
                                                                          Map<String, TypeInfo> replacedTypeInfoMap) {

        List<TypeInfo> typeInfoList = new ArrayList<>(Collections.singleton(typeInfo));
        replaceFormalTypeInfoUsingInvokerTypeParameterMap(typeInfoList, formalTypeParameterInfoMap, replacedTypeInfoMap);

        return typeInfoList.get(0);
    }

    private static void replaceFormalTypeInfoUsingInvokerTypeParameterMap(List<TypeInfo> typeInfoList,
                                                                          Map<String, FormalTypeParameterInfo> formalTypeParameterInfoMap,
                                                                          Map<String, TypeInfo> replacedTypeInfoMap) {

        for (int i = 0; i < typeInfoList.size(); i++) {
            TypeInfo typeInfo = typeInfoList.get(i);

            if (typeInfo.isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo formalTypeParameterTypeInfo = (FormalTypeParameterInfo) typeInfo;

                if (formalTypeParameterInfoMap.containsKey(formalTypeParameterTypeInfo.getTypeParameter())) {
                    FormalTypeParameterInfo invokerFormalTypeParameterInfo =
                            formalTypeParameterInfoMap.get(formalTypeParameterTypeInfo.getTypeParameter());

                    typeInfoList.set(i, invokerFormalTypeParameterInfo.getBaseTypeInfo());

                    replacedTypeInfoMap.put(invokerFormalTypeParameterInfo.getTypeParameter(), invokerFormalTypeParameterInfo.getBaseTypeInfo());
                }

            } else if (typeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;
                List<TypeInfo> typeArgumentList = parameterizedTypeInfo.getTypeArgumentList();

                replaceFormalTypeInfoUsingInvokerTypeParameterMap(typeArgumentList, formalTypeParameterInfoMap, replacedTypeInfoMap);
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
     */
    public static void transformTypeInfoRepresentation(Set<Artifact> dependentArtifactSet,
                                                       String javaVersion,
                                                       List<String> importStatementList,
                                                       OwningClassInfo owningClassInfo,
                                                       List<MethodInfo> methodInfoList,
                                                       List<TypeInfo> argumentTypeInfoList,
                                                       TypeInfo invokerTypeInfo) {

        for (MethodInfo methodInfo : methodInfoList) {
            convertParameterizedTypeIfRequired(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfo);

            if (Objects.isNull(invokerTypeInfo) && Objects.nonNull(owningClassInfo)
                    && owningClassInfo.getQualifiedClassNameSetInHierarchy().get(0).contains(methodInfo.getQualifiedClassName())) {

                invokerTypeInfo = methodInfo.getClassInfo().getTypeInfo();
            }

            Map<String, TypeInfo> replacedTypeInfoMap = new HashMap<>();

            /*
             * Invoker class may not be associated with method. Any super class of invoker class can be associated.
             * So added this class check before extracting formal type parameters of invoker type.
             *
             * Also, moved the type arguments to the updated invoker type for formal type resolution.
             */
            if (Objects.nonNull(invokerTypeInfo)
                    && !invokerTypeInfo.getQualifiedClassName().equals(methodInfo.getQualifiedClassName())) {

                TypeInfo updatedInvokerTypeInfo = methodInfo.getClassInfo().getTypeInfo();

                if (invokerTypeInfo.isParameterizedTypeInfo()
                        && updatedInvokerTypeInfo.isParameterizedTypeInfo()
                        && ((ParameterizedTypeInfo) invokerTypeInfo).isParameterized()) {

                    ParameterizedTypeInfo parameterizedInvokerTypeInfo = (ParameterizedTypeInfo) invokerTypeInfo;

                    ParameterizedTypeInfo parameterizedUpdatedInvokerTypeInfo = (ParameterizedTypeInfo) updatedInvokerTypeInfo;
                    parameterizedUpdatedInvokerTypeInfo.setParameterized(parameterizedInvokerTypeInfo.isParameterized());

                    Map<String, FormalTypeParameterInfo> invokerFormalTypeParameterMap =
                            parameterizedInvokerTypeInfo.getTypeArgumentList()
                                    .stream()
                                    .filter(TypeInfo::isFormalTypeParameterInfo)
                                    .map(t -> (FormalTypeParameterInfo) t)
                                    .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter, f -> f));

                    for (TypeInfo typeInfo : parameterizedUpdatedInvokerTypeInfo.getTypeArgumentList()) {
                        if (typeInfo.isFormalTypeParameterInfo()
                                && invokerFormalTypeParameterMap.containsKey(((FormalTypeParameterInfo) typeInfo).getTypeParameter())) {

                            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeInfo;
                            formalTypeParameterInfo.setBaseTypeInfo(invokerFormalTypeParameterMap.get(
                                            formalTypeParameterInfo.getTypeParameter())
                                    .getBaseTypeInfo());
                        }
                    }
                }

                invokerTypeInfo = updatedInvokerTypeInfo;
            }

            if (Objects.nonNull(invokerTypeInfo) && invokerTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo invokerParameterizedTypeInfo = (ParameterizedTypeInfo) invokerTypeInfo;

                /*
                 * If invoker is parameterized with type arguments:
                 * Here we are doing scenario 2 where we are replacing the formalTypeParameter with typeArgument Info of
                 * the parameterized type.
                 */
                if (invokerParameterizedTypeInfo.isParameterized()) {
                    Map<String, FormalTypeParameterInfo> invokerFormalTypeParameterMap = invokerParameterizedTypeInfo.getTypeArgumentList()
                            .stream()
                            .filter(TypeInfo::isFormalTypeParameterInfo)
                            .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                            .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter,
                                    formalTypeParameterInfo -> formalTypeParameterInfo));

                    /*
                     * Method Arguments:
                     */
                    replaceFormalTypeInfoUsingInvokerTypeParameterMap(methodInfo.getArgumentTypeInfoList(), invokerFormalTypeParameterMap, replacedTypeInfoMap);

                    /*
                     * Method return type:
                     */
                    methodInfo.setReturnTypeInfo(replaceFormalTypeInfoUsingInvokerTypeParameterMap(methodInfo.getReturnTypeInfo(),
                            invokerFormalTypeParameterMap, replacedTypeInfoMap));
                } else {
                    /*
                     * If invokerClass is ParameterizedTypeInfo but parameterized=false, then there can be two scenarios.
                     *
                     * Scenario 1. the invokerClass can be owning class, then we should show the formalTypeParameterInfo
                     * as it is.
                     *
                     * Scenario 2. the invokerClass can be another class, then we have to show the base type of formalTypeParameterInfo
                     */
                    if (methodInfo.getClassInfo().getTypeInfo().isParameterizedTypeInfo()) {
                        ParameterizedTypeInfo classParameterizedType = (ParameterizedTypeInfo) methodInfo.getClassInfo().getTypeInfo();
                        Map<String, FormalTypeParameterInfo> invokerFormalTypeParameterMap = classParameterizedType.getTypeArgumentList()
                                .stream()
                                .filter(TypeInfo::isFormalTypeParameterInfo)
                                .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                                .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter,
                                        formalTypeParameterInfo -> formalTypeParameterInfo));

                        /*
                         * Method Arguments:
                         */
                        for (int i = 0; i < methodInfo.getArgumentTypeInfoList().size(); i++) {
                            TypeInfo methodArgument = methodInfo.getArgumentTypeInfoList().get(i);

                            if (methodArgument.isFormalTypeParameterInfo()) {
                                FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgument;

                                if (invokerFormalTypeParameterMap.containsKey(formalTypeParameterInfo.getTypeParameter())) {
                                    FormalTypeParameterInfo invokerFormalTypeParameter =
                                            invokerFormalTypeParameterMap.get(formalTypeParameterInfo.getTypeParameter());

                                    TypeInfo typeInfo = Objects.nonNull(owningClassInfo)
                                            && owningClassInfo.getOwningQualifiedClassName().equals(methodInfo.getClassInfo().getQualifiedName())
                                            ? invokerFormalTypeParameter
                                            : invokerFormalTypeParameter.getBaseTypeInfo();

                                    methodInfo.getArgumentTypeInfoList().set(i, typeInfo);

                                    replacedTypeInfoMap.put(invokerFormalTypeParameter.getTypeParameter(), typeInfo);
                                }
                            }
                        }

                        /*
                         * Method return type:
                         */
                        if (methodInfo.getReturnTypeInfo().isFormalTypeParameterInfo()) {
                            FormalTypeParameterInfo formalTypeParameterMethodReturnTypeInfo = (FormalTypeParameterInfo) methodInfo.getReturnTypeInfo();

                            if (invokerFormalTypeParameterMap.containsKey(formalTypeParameterMethodReturnTypeInfo.getTypeParameter())) {
                                FormalTypeParameterInfo invokerFormalTypeParameterInfo =
                                        invokerFormalTypeParameterMap.get(formalTypeParameterMethodReturnTypeInfo.getTypeParameter());

                                TypeInfo typeInfo = Objects.nonNull(owningClassInfo)
                                        && owningClassInfo.getOwningQualifiedClassName().equals(methodInfo.getClassInfo().getQualifiedName())
                                        ? invokerFormalTypeParameterInfo
                                        : invokerFormalTypeParameterInfo.getBaseTypeInfo();

                                methodInfo.setReturnTypeInfo(typeInfo);

                                replacedTypeInfoMap.put(invokerFormalTypeParameterInfo.getTypeParameter(), typeInfo);
                            }
                        }
                    }
                }
                /*
                 * When invoker is Qualified type but method has formal type parameter.
                 *
                 * We can infer from argument type or use base type
                 *
                 */
            } else if (Objects.nonNull(invokerTypeInfo) && invokerTypeInfo.isQualifiedTypeInfo()) {
                List<TypeInfo> methodArgumentTypeInfoList = methodInfo.getArgumentTypeInfoList();

                Map<String, TypeInfo> inferredFormalTypeParameterValueMap = getInferredFormalTypeParameterMap(methodInfo, argumentTypeInfoList);

                for (int i = 0; i < methodArgumentTypeInfoList.size(); i++) {
                    TypeInfo methodArgument = methodArgumentTypeInfoList.get(i);

                    if (methodArgument.isFormalTypeParameterInfo()) {
                        FormalTypeParameterInfo formalTypeParameterMethodArgInfo = (FormalTypeParameterInfo) methodArgument;

                        TypeInfo baseTypeInfo = inferredFormalTypeParameterValueMap.containsKey(formalTypeParameterMethodArgInfo.getTypeParameter())
                                ? inferredFormalTypeParameterValueMap.get(formalTypeParameterMethodArgInfo.getTypeParameter())
                                : formalTypeParameterMethodArgInfo.getBaseTypeInfo();
                        methodArgumentTypeInfoList.set(i, baseTypeInfo);

                        replacedTypeInfoMap.put(formalTypeParameterMethodArgInfo.getTypeParameter(), baseTypeInfo);
                    } else if (methodArgument.isParameterizedTypeInfo()) {
                        ParameterizedTypeInfo parameterizedMethodArgument = (ParameterizedTypeInfo) methodArgument;
                        List<TypeInfo> typeArgumentList = parameterizedMethodArgument.getTypeArgumentList();

                        for (int j = 0; j < typeArgumentList.size(); j++) {
                            TypeInfo typeArgument = typeArgumentList.get(j);

                            if (typeArgument.isFormalTypeParameterInfo()) {
                                FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeArgument;

                                TypeInfo baseTypeInfo = inferredFormalTypeParameterValueMap.containsKey(formalTypeParameterInfo.getTypeParameter())
                                        ? inferredFormalTypeParameterValueMap.get(formalTypeParameterInfo.getTypeParameter())
                                        : (formalTypeParameterInfo.getBaseTypeInfo().isFormalTypeParameterInfo()
                                        ? ((FormalTypeParameterInfo) formalTypeParameterInfo.getBaseTypeInfo()).getBaseTypeInfo()
                                        : formalTypeParameterInfo.getBaseTypeInfo());

                                typeArgumentList.set(j, baseTypeInfo);

                                replacedTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), baseTypeInfo);
                            }
                        }
                    } else if (methodArgument.isArrayTypeInfo()) {
                        ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;

                        if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()) {
                            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();

                            if (inferredFormalTypeParameterValueMap.containsKey(formalTypeParameterInfo.getTypeParameter())) {
                                methodArgArray.setElementTypeInfo(inferredFormalTypeParameterValueMap.get(formalTypeParameterInfo.getTypeParameter()));

                                replacedTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), methodArgArray.getElementTypeInfo());
                            }

                        }
                    }
                }

                if (methodInfo.getReturnTypeInfo().isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo formalTypeParameterMethodReturnTypeInfo = (FormalTypeParameterInfo) methodInfo.getReturnTypeInfo();

                    TypeInfo baseTypeInfo = inferredFormalTypeParameterValueMap.containsKey(formalTypeParameterMethodReturnTypeInfo.getTypeParameter())
                            ? inferredFormalTypeParameterValueMap.get(formalTypeParameterMethodReturnTypeInfo.getTypeParameter())
                            : formalTypeParameterMethodReturnTypeInfo.getBaseTypeInfo();

                    methodInfo.setReturnTypeInfo(baseTypeInfo);
                    replacedTypeInfoMap.put(formalTypeParameterMethodReturnTypeInfo.getTypeParameter(), baseTypeInfo);
                }
            } else if (Objects.nonNull(invokerTypeInfo) && invokerTypeInfo.isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo invokerFormalTypeParameterInfo = (FormalTypeParameterInfo) invokerTypeInfo;
                processFormalTypeParameter(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfo, methodInfo, replacedTypeInfoMap, invokerFormalTypeParameterInfo);
            }

            transformTypeRepresentation(methodInfo, replacedTypeInfoMap);
        }
    }

    private static void populateVariableNameMapForMethod(Set<Artifact> dependentArtifactSet,
                                                         String javaVersion,
                                                         List<String> importStatementList,
                                                         OwningClassInfo owningClassInfo,
                                                         ASTNode node,
                                                         Map<String, Set<VariableDeclarationDto>> variableNameMap) {

        MethodDeclaration methodDeclaration = (MethodDeclaration) getClosestASTNode(node, MethodDeclaration.class);

        if (methodDeclaration != null) {
            Set<VariableDeclarationDto> methodParameterVariableDeclarationSet =
                    getMethodParameterVariableDeclarationDtoList(dependentArtifactSet, javaVersion,
                            importStatementList, methodDeclaration, owningClassInfo);

            populateVariableNameMap(variableNameMap, methodParameterVariableDeclarationSet);

            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getLocalVariableDtoList(dependentArtifactSet, javaVersion, importStatementList,
                            methodDeclaration.getBody(), owningClassInfo);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);

            AnonymousClassDeclaration anonymousClassDeclaration =
                    (AnonymousClassDeclaration) getClosestASTNode(methodDeclaration, AnonymousClassDeclaration.class);

            if (Objects.nonNull(anonymousClassDeclaration)) {
                populateVariableNameMapForMethod(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfo, anonymousClassDeclaration, variableNameMap);
            }
        }
    }

    private static void populateVariableNameMapForStaticBlock(Set<Artifact> dependentArtifactSet,
                                                              String javaVersion,
                                                              List<String> importStatementList,
                                                              OwningClassInfo owningClassInfo,
                                                              ASTNode node,
                                                              Map<String, Set<VariableDeclarationDto>> variableNameMap) {

        Initializer initializer = (Initializer) getClosestASTNode(node, Initializer.class);

        if (Objects.nonNull(initializer)) {
            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getLocalVariableDtoList(dependentArtifactSet, javaVersion, importStatementList,
                            initializer.getBody(), owningClassInfo);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);
        }
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
                                                           MethodInfo methodInfo) {

        for (int i = 0; i < methodInfo.getArgumentTypeInfoList().size(); i++) {
            TypeInfo argument = methodInfo.getArgumentTypeInfoList().get(i);

            methodInfo.getArgumentTypeInfoList().set(i, convertParameterizedTypeIfRequired(dependentArtifactSet,
                    javaVersion, importStatementList, owningClassInfo, argument));
        }

        methodInfo.setReturnTypeInfo(convertParameterizedTypeIfRequired(dependentArtifactSet, javaVersion,
                importStatementList, owningClassInfo, methodInfo.getReturnTypeInfo()));
    }

    private static TypeInfo convertParameterizedTypeIfRequired(Set<Artifact> dependentArtifactSet,
                                                               String javaVersion,
                                                               List<String> importStatementList,
                                                               OwningClassInfo owningClassInfo,
                                                               TypeInfo typeInfo) {

        if (typeInfo.isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) typeInfo;

            TypeInfo fetchedTypeInfo = InferenceUtility.getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                    importStatementList, qualifiedTypeInfo.getQualifiedClassName(), owningClassInfo);

            if (Objects.nonNull(fetchedTypeInfo) && fetchedTypeInfo.isParameterizedTypeInfo()) {
                return fetchedTypeInfo;
            }

        } else if (typeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo parameterizedArgTypeInfo = (ParameterizedTypeInfo) typeInfo;

            if (parameterizedArgTypeInfo.isParameterized()) {
                TypeInfo updatedArgTypeInfo = InferenceUtility.getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                        importStatementList, parameterizedArgTypeInfo.getQualifiedClassName(), owningClassInfo);

                if (Objects.nonNull(updatedArgTypeInfo) && updatedArgTypeInfo.isParameterizedTypeInfo()) {
                    ParameterizedTypeInfo parameterizedUpdatedArgTypeInfo = (ParameterizedTypeInfo) updatedArgTypeInfo;

                    parameterizedUpdatedArgTypeInfo.setParameterized(true);
                    parameterizedUpdatedArgTypeInfo.setTypeArgumentList(parameterizedArgTypeInfo.getTypeArgumentList());

                    return parameterizedUpdatedArgTypeInfo;
                }
            }
        }

        return typeInfo;
    }

    private static void processFormalTypeParameter(Set<Artifact> dependentArtifactSet,
                                                   String javaVersion, List<String> importStatementList,
                                                   OwningClassInfo owningClassInfo,
                                                   MethodInfo methodInfo,
                                                   Map<String, TypeInfo> replacedTypeInfoMap,
                                                   FormalTypeParameterInfo invokerFormalTypeParameterInfo) {
        TypeInfo baseType = invokerFormalTypeParameterInfo.getBaseTypeInfo();

        TypeInfo invokerBaseTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                baseType.getQualifiedClassName(), owningClassInfo);

        if (Objects.nonNull(invokerBaseTypeInfo) && invokerBaseTypeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo invokerParameterizedTypeInfo = (ParameterizedTypeInfo) invokerBaseTypeInfo;
            invokerParameterizedTypeInfo.setParameterized(true);
            invokerParameterizedTypeInfo.setTypeArgumentList(Collections.singletonList(invokerFormalTypeParameterInfo));

            Map<String, FormalTypeParameterInfo> invokerFormalTypeParameterMap = invokerParameterizedTypeInfo.getTypeArgumentList()
                    .stream()
                    .filter(TypeInfo::isFormalTypeParameterInfo)
                    .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                    .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter,
                            formalTypeParameterInfo -> formalTypeParameterInfo));

            /*
             * Method Arguments:
             */
            replaceFormalTypeInfoUsingInvokerTypeParameterMap(methodInfo.getArgumentTypeInfoList(),
                    invokerFormalTypeParameterMap, replacedTypeInfoMap);
        }
    }

    private static void transformTypeRepresentation(MethodInfo methodInfo, Map<String, TypeInfo> formalTypeParameterMap) {
        if (!formalTypeParameterMap.isEmpty() && Objects.nonNull(methodInfo.getSignature())) {
            GenericTypeResolutionAdapter genericTypeResolutionAdapter =
                    new GenericTypeResolutionAdapter(formalTypeParameterMap);
            SignatureReader reader = new SignatureReader(methodInfo.getSignature());
            reader.accept(genericTypeResolutionAdapter);

            methodInfo.setArgumentTypes(genericTypeResolutionAdapter.getMethodArgumentTypes());
            methodInfo.setReturnType(genericTypeResolutionAdapter.getMethodReturnType());
        }
    }

    /*
     * There are 3 scenarios for varargs
     * 1: There may be no argument passed
     * 2: An array can be passed
     * 3: Same type of multiple arguments can be passed
     */
    private static void conversionToVarargsMethodArgument(List<MethodInfo> methodInfoList) {

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
                                                                                            OwningClassInfo owningClassInfo) {
        if (methodDeclaration != null) {
            List<SingleVariableDeclaration> declarationList = methodDeclaration.parameters();

            return declarationList.stream()
                    .map(declaration -> getVariableDeclarationDto(dependentArtifactSet, javaVersion,
                            importStatementList, declaration, owningClassInfo))
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
                                                                       OwningClassInfo owningClassInfo) {
        if (Objects.isNull(bodyBlock)) {
            return Collections.emptySet();
        }

        Set<VariableDeclarationDto> localVariableDtoSet = new HashSet<>();

        bodyBlock.accept(new ASTVisitor() {
            @Override
            public boolean visit(SingleVariableDeclaration singleVariableDeclaration) {
                VariableDeclarationDto variableDeclarationDto =
                        getVariableDeclarationDto(dependentArtifactSet, javaVersion, importStatementList,
                                singleVariableDeclaration, owningClassInfo);

                localVariableDtoSet.add(variableDeclarationDto);

                return true;
            }

            @Override
            public void endVisit(VariableDeclarationExpression variableDeclarationExpression) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                                variableDeclarationExpression.getType(), fragmentList, owningClassInfo);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }

            @Override
            public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                                variableDeclarationStatement.getType(), fragmentList, owningClassInfo);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }
        });

        return localVariableDtoSet;
    }

    private static TypeInfo convertVarargsIfRequired(SingleVariableDeclaration singleVariableDeclaration,
                                                     TypeInfo typeInfo) {
        if (singleVariableDeclaration.isVarargs()) {
            return new VarargTypeInfo(typeInfo);
        } else {
            return typeInfo;
        }
    }

    private static VariableDeclarationDto getVariableDeclarationDto(Set<Artifact> dependentArtifactSet,
                                                                    String javaVersion,
                                                                    List<String> importStatementList,
                                                                    SingleVariableDeclaration declaration,
                                                                    OwningClassInfo owningClassInfo) {
        String name = declaration.getName().getFullyQualifiedName();
        Type declarationType = declaration.getType();
        TypeInfo declarationTypeInfo = convertVarargsIfRequired(declaration,
                getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, declarationType, owningClassInfo));

        ASTNode scopedNode = getVariableDeclarationScopedNode(declaration);

        if (scopedNode != null) {
            int startOffset = scopedNode.getStartPosition();
            int endOffSet = startOffset + scopedNode.getLength();

            return new VariableDeclarationDto(name, declarationTypeInfo, new VariableScope(startOffset, endOffSet), declarationType);

        } else {
            return null;
        }
    }

    private static VariableDeclarationDto getClassNameFromVariableMap(String name,
                                                                      Expression expression,
                                                                      Map<String, Set<VariableDeclarationDto>> variableNameMap) {
        if (variableNameMap.containsKey(name)) {
            int position = expression.getParent().getStartPosition();
            Set<VariableDeclarationDto> variableDeclarationDtoSet = variableNameMap.get(name);
            List<VariableDeclarationDto> selectedVariableDeclarationDto = new ArrayList<>();

            for (VariableDeclarationDto vd : variableDeclarationDtoSet) {
                VariableScope vs = vd.getScope();

                if (vs.getStartOffset() <= position && position <= vs.getEndOffset()) {
                    selectedVariableDeclarationDto.add(vd);
                }
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

    private static List<TypeInfo> getTypeInfoList(Set<Artifact> dependentArtifactSet,
                                                   String javaVersion,
                                                   List<String> importStatementList,
                                                   List<Type> typeList,
                                                  OwningClassInfo owningClassInfo) {
        List<TypeInfo> typeInfoList = new ArrayList<>();

        for (Type type : typeList) {
            typeInfoList.add(getTypeInfo(dependentArtifactSet, javaVersion, importStatementList, type, owningClassInfo));
        }

        return typeInfoList;
    }

    private static List<AnonymousClassDeclaration> getAnonymousClassDeclarationList(BodyDeclaration declaration) {
        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(declaration);

        AnonymousClassVisitor anonymousClassVisitor = new AnonymousClassVisitor();
        abstractTypeDeclaration.accept(anonymousClassVisitor);

        return anonymousClassVisitor.getAnonymousClassDeclarationList();
    }

    private static List<VariableDeclarationDto> getVariableDeclarationDtoList(Set<Artifact> dependentArtifactSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              Type declarationType,
                                                                              List<VariableDeclarationFragment> fragmentList,
                                                                              OwningClassInfo owningClassInfo) {

        TypeInfo declarationTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                declarationType, owningClassInfo);

        return fragmentList.stream().map(fragment -> {
            ASTNode scopedNode = getVariableDeclarationScopedNode(fragment);
            String name = fragment.getName().getFullyQualifiedName();

            int startOffset = fragment.getStartPosition();
            int endOffSet = startOffset + (scopedNode != null ? scopedNode.getLength() : 0);

            return new VariableDeclarationDto(name, declarationTypeInfo, new VariableScope(startOffset, endOffSet), declarationType);

        }).collect(Collectors.toList());
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
                                                       OwningClassInfo owningClassInfo) {
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
                                boundType, owningClassInfo);

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
        String owningQualifiedClassName = Objects.nonNull(owningClassInfo)
                ? owningClassInfo.getOwningQualifiedClassName()
                : null;
        TypeInfo owningClassTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                importStatementList, owningQualifiedClassName, owningClassInfo);

        if (Objects.nonNull(owningClassTypeInfo) && owningClassTypeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) owningClassTypeInfo;

            List<FormalTypeParameterInfo> formalTypeArgumentList = parameterizedTypeInfo.getTypeArgumentList().stream()
                    .filter(TypeInfo::isFormalTypeParameterInfo)
                    .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                    .collect(Collectors.toList());

            if (formalTypeArgumentList.stream().anyMatch(ft -> ft.getTypeParameter().equals(name))) {
                return formalTypeArgumentList.stream()
                        .filter(ft -> ft.getTypeParameter().equals(name))
                        .collect(Collectors.toList())
                        .get(0);
            }
        }

        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name, owningClassInfo);
    }

    private static TypeInfo getTypeInfoFromQualifiedType(Set<Artifact> dependentArtifactSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          QualifiedType qualifiedType,
                                                          OwningClassInfo owningClassInfo) {

        String name = qualifiedType.getName().getFullyQualifiedName();
        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name, owningClassInfo);
    }

    private static TypeInfo getTypeInfoFromFieldName(Set<Artifact> dependentArtifactSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      String fieldName,
                                                      OwningClassInfo owningClassInfo) {

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
                assert Objects.nonNull(fieldClassTypeInfo) && fieldClassTypeInfo.isParameterizedTypeInfo();

                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) fieldClassTypeInfo;

                return parameterizedTypeInfo.getTypeArgumentList().stream()
                        .filter(TypeInfo::isFormalTypeParameterInfo)
                        .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                        .filter(formalTypeParameterInfo -> formalTypeParameterInfo.getTypeParameter().equals(typeParameter))
                        .collect(Collectors.toList())
                        .get(0);

            } else {
                String fieldTypeClassName = typeInfoExtractedFromFieldSignature.getQualifiedClassName();

                TypeInfo typeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                        fieldTypeClassName, owningClassInfo);

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
            String fieldTypeClassName = fieldInfo.getTypeAsStr();

            return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList,
                    fieldTypeClassName, owningClassInfo);
        }
    }

    private static TypeInfo getTypeInfoFromClassName(Set<Artifact> dependentArtifactSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      String className,
                                                      OwningClassInfo owningClassInfo) {
        if (Objects.isNull(className)) {
            return null;
        }

        if (PRIMITIVE_TYPE_LIST.contains(className.replaceAll("\\[]", ""))) {
            int dimension = StringUtils.countMatches(className, "[]");

            if (dimension > 0) {
                return new ArrayTypeInfo(new PrimitiveTypeInfo(className.replaceAll("\\[]", "")), dimension);
            } else {
                return new PrimitiveTypeInfo(className);
            }
        }

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentArtifactSet, javaVersion, importStatementList,
                className, owningClassInfo);

        assert !classInfoList.isEmpty();

        return getTypeInfoFromClassInfo(className, classInfoList.get(0));
    }

    private static Map<String, TypeInfo> getInferredFormalTypeParameterMap(MethodInfo methodInfo,
                                                                           List<TypeInfo> argumentTypeInfoList) {
        List<TypeInfo> methodArgumentTypeInfoList = methodInfo.getArgumentTypeInfoList();
        Map<String, TypeInfo> inferredFormalTypeParameterValueMap = new HashMap<>();

        for (int i = 0; i < methodArgumentTypeInfoList.size(); i++) {
            TypeInfo methodArgument = methodArgumentTypeInfoList.get(i);
            TypeInfo argument = methodInfo.isVarargs() && methodArgument.isArrayTypeInfo() && i == argumentTypeInfoList.size()
                    ? null
                    : argumentTypeInfoList.get(i);

            if (methodArgument.isFormalTypeParameterInfo() && Objects.nonNull(argument) && !argument.isNullTypeInfo()) {
                FormalTypeParameterInfo formalTypeParameterMethodArgInfo = (FormalTypeParameterInfo) methodArgument;
                inferredFormalTypeParameterValueMap.put(formalTypeParameterMethodArgInfo.getTypeParameter(), argument);

            } else if (methodArgument.isArrayTypeInfo() && Objects.nonNull(argument) && argument.isArrayTypeInfo()) {
                ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;
                ArrayTypeInfo argArray = (ArrayTypeInfo) argument;

                if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();
                    inferredFormalTypeParameterValueMap.put(formalTypeParameterInfo.getTypeParameter(), argArray.getElementTypeInfo());
                }
            } else if (methodArgument.isParameterizedTypeInfo() && Objects.nonNull(argument)
                    && argument.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) argument).isParameterized()) {
                ParameterizedTypeInfo parameterizedArgTypeInfo = (ParameterizedTypeInfo) argument;

                /*
                 * Assuming that methodArgument and argument will both have same type-parameter list.
                 */
                for (TypeInfo typeArgument: parameterizedArgTypeInfo.getTypeArgumentList()) {
                    if (typeArgument.isFormalTypeParameterInfo()) {
                        FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeArgument;

                        inferredFormalTypeParameterValueMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo.getBaseTypeInfo());
                    }
                }
            }
        }

        return inferredFormalTypeParameterValueMap;
    }

    private static TypeInfo getTypeInfoFromClassInfo(String className, ClassInfo classInfo) {
        /*
         * When we are constructing typeinfo from classname, if we find signature not null, we will construct a
         *  parameterized type info. Parameterized type info has list of type arguments.
         *  All the types will be formalTypeParameter.
         */
        if (StringUtils.isNotEmpty(classInfo.getSignature())) {
            ClassSignatureFormalTypeParameterExtractor extractor = new ClassSignatureFormalTypeParameterExtractor();

            SignatureReader signatureReader = new SignatureReader(classInfo.getSignature());
            signatureReader.accept(extractor);

            String qualifiedClassName = classInfo.getQualifiedName();

            if (extractor.getTypeArgumentList().isEmpty()) {
                QualifiedTypeInfo qualifiedTypeInfo = new QualifiedTypeInfo(qualifiedClassName);

                //if className is array populate array dimension
                if (className.contains("[]")) {
                    int numberOfDimension = StringUtils.countMatches(className, "[]");

                    return new ArrayTypeInfo(qualifiedTypeInfo, numberOfDimension);
                }

                return qualifiedTypeInfo;
            } else {
                ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo(qualifiedClassName);
                parameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(extractor.getTypeArgumentList()));

                //if className is array populate array dimension
                if (className.contains("[]")) {
                    int numberOfDimension = StringUtils.countMatches(className, "[]");

                    return new ArrayTypeInfo(parameterizedTypeInfo, numberOfDimension);
                }

                return parameterizedTypeInfo;
            }
        } else {
            String qualifiedClassName = classInfo.getQualifiedName();

            //if className is array populate array dimension
            if (className.contains("[]")) {
                int numberOfDimension = StringUtils.countMatches(className, "[]");

                QualifiedTypeInfo qualifiedTypeInfo = new QualifiedTypeInfo(qualifiedClassName);
                return new ArrayTypeInfo(qualifiedTypeInfo, numberOfDimension);

            } else {
                return new QualifiedTypeInfo(qualifiedClassName);
            }
        }
    }

}
