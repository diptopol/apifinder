package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.TypeInferenceAPI;
import ca.concordia.jaranalyzer.TypeInferenceFluentAPI;
import ca.concordia.jaranalyzer.models.*;
import ca.concordia.jaranalyzer.models.typeInfo.*;
import ca.concordia.jaranalyzer.util.signaturevisitor.ClassSignatureFormalTypeParameterExtractor;
import ca.concordia.jaranalyzer.util.signaturevisitor.FieldSignatureFormalTypeParameterExtractor;
import ca.concordia.jaranalyzer.util.signaturevisitor.GenericTypeResolutionAdapter;
import org.apache.commons.collections.CollectionUtils;
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

        List<Type> typeArgumentList = methodInvocation.typeArguments();

        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo);

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
                owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList, invokerClassTypeInfo);
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

        List<Type> typeArgumentList = superMethodInvocation.typeArguments();
        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo);


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
                owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList, null);
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

        List<Type> typeArgumentList = classInstanceCreation.typeArguments();
        List<TypeInfo> typeArgumentTypeInfoList = InferenceUtility.getTypeInfoList(dependentArtifactSet, javaVersion,
                importStatementList, typeArgumentList, owningClassInfo);

        Type type = classInstanceCreation.getType();
        TypeInfo invokerClassTypeInfo = null;

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

                        invokerClassTypeInfo = InferenceUtility.getTypeInfo(dependentArtifactSet, javaVersion,
                                importStatementList, type, owningClassInfo);

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
                .setOwningClassInfo(owningClassInfo);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        InferenceUtility.transformTypeInfoRepresentation(dependentArtifactSet, javaVersion, importStatementList,
                owningClassInfo, methodInfoList, argumentTypeInfoList, typeArgumentTypeInfoList, invokerClassTypeInfo);

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
                        methodExpression);

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
                                                                                 ASTNode node) {

        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(node);
        List<FieldDeclaration> fieldDeclarationList = new ArrayList<>();

        abstractTypeDeclaration.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration node) {
                fieldDeclarationList.add(node);

                return true;
            }
        });

        return fieldDeclarationList.stream().map(fieldDeclaration -> {
                    List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

                    OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                            getAllEnclosingClassList(fieldDeclaration, dependentArtifactSet, javaVersion, importStatementList),
                            Collections.emptyList());

                    owningClassInfo.setAccessibleFormalTypeParameterList(
                            getAccessibleFormalTypeParameterList(dependentArtifactSet, javaVersion, importStatementList,
                                    owningClassInfo, fieldDeclaration));

                    return getVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                            fieldDeclaration.getType(), fieldDeclaration.getParent().getStartPosition(), fragmentList, owningClassInfo);
                }).flatMap(Collection::stream)
                .collect(Collectors.toSet());
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
                TypeInfo returnTypeInfo = methodInfoList.get(0).getReturnTypeInfo();

                if (returnTypeInfo.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) returnTypeInfo).isParameterized()) {
                    List<TypeInfo> typeArgumentList = ((ParameterizedTypeInfo) returnTypeInfo).getTypeArgumentList();

                    TypeInfo returnClassTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
                            importStatementList, returnTypeInfo.getQualifiedClassName(), owningClassInfo);

                    if (Objects.nonNull(returnClassTypeInfo) && returnClassTypeInfo.isParameterizedTypeInfo()) {
                        ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) returnClassTypeInfo;

                        if (!typeArgumentList.isEmpty()) {
                            parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                            parameterizedTypeInfo.setParameterized(true);

                            return returnClassTypeInfo;
                        }
                    }
                }

                return returnTypeInfo;
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

        } else if (type instanceof NameQualifiedType) {
            return getTypeInfoFromNameQualifiedType(dependentArtifactSet, javaVersion, importStatementList,
                    (NameQualifiedType) type, owningClassInfo);

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

            } else if (internalType instanceof NameQualifiedType) {
                TypeInfo typeInfo = getTypeInfoFromNameQualifiedType(dependentArtifactSet, javaVersion,
                        importStatementList, (NameQualifiedType) internalType, owningClassInfo);

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
                TypeInfo boundTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                        boundType, owningClassInfo);

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
                                                       TypeInfo invokerTypeInfo) {

        for (MethodInfo methodInfo : methodInfoList) {
            convertParameterizedTypeIfRequired(dependentArtifactSet, javaVersion, importStatementList,
                    owningClassInfo, methodInfo);

            Map<String, TypeInfo> replacedTypeInfoMap = new HashMap<>();
            invokerTypeInfo = getOriginalInvoker(invokerTypeInfo, methodInfo, owningClassInfo);
            Map<String, TypeInfo> formalTypeParameterMap = getInferredFormalTypeParameterMap(invokerTypeInfo, methodInfo, argumentTypeInfoList, typeArgumentTypeInfoList);

            if (Objects.nonNull(owningClassInfo) && Objects.nonNull(invokerTypeInfo)
                    && owningClassInfo.getQualifiedClassNameSetInHierarchy().get(0).contains(invokerTypeInfo.getQualifiedClassName())) {
                populateFormalTypeParametersOfOwningClass(methodInfo, replacedTypeInfoMap);
            }

            transformTypeInfoRepresentationUsingInferredFormalTypeParameterMap(formalTypeParameterMap, methodInfo, replacedTypeInfoMap);
            transformTypeRepresentation(methodInfo, replacedTypeInfoMap);
        }
    }

    public static TypeInfo getTypeInfoFromClassName(Set<Artifact> dependentArtifactSet,
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
        }

        return typeInfo;
    }

    private static TypeInfo getOriginalInvoker(TypeInfo invokerTypeInfo,
                                               MethodInfo methodInfo,
                                               OwningClassInfo owningClassInfo) {

        if (Objects.isNull(invokerTypeInfo) && Objects.nonNull(owningClassInfo)
                && owningClassInfo.getQualifiedClassNameSetInHierarchy().get(0).contains(methodInfo.getQualifiedClassName())) {

            invokerTypeInfo = methodInfo.getClassInfo().getTypeInfo();
        }

        /*
         * Invoker class may not be associated with method. Any super class of invoker class can be associated.
         * So added this class check before extracting formal type parameters of invoker type.
         *
         * Also, moved the type arguments to the updated invoker type for formal type resolution.
         */
        if (Objects.nonNull(invokerTypeInfo)
                && invokerTypeInfo.isParameterizedTypeInfo()
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

        return invokerTypeInfo;
    }

    private static Map<String, TypeInfo> getInferredFormalTypeParameterMap(TypeInfo invokerTypeInfo,
                                                                           MethodInfo methodInfo,
                                                                           List<TypeInfo> argumentTypeInfoList,
                                                                           List<TypeInfo> typeArgumentTypeInfoList) {
        Map<String, TypeInfo> inferredTypeInfoMap = new HashMap<>();

        if (Objects.nonNull(invokerTypeInfo)
                && invokerTypeInfo.isParameterizedTypeInfo()
                && ((ParameterizedTypeInfo) invokerTypeInfo).isParameterized()) {
            ParameterizedTypeInfo invokerParameterizedTypeInfo = (ParameterizedTypeInfo) invokerTypeInfo;

            inferredTypeInfoMap.putAll(
                    invokerParameterizedTypeInfo.getTypeArgumentList()
                            .stream()
                            .filter(TypeInfo::isFormalTypeParameterInfo)
                            .map(typeInfo -> (FormalTypeParameterInfo) typeInfo)
                            .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter,
                                    FormalTypeParameterInfo::getBaseTypeInfo))
            );
        }

        if (!typeArgumentTypeInfoList.isEmpty()) {
            List<TypeInfo> formalTypeParameterList = methodInfo.getFormalTypeParameterList();

            assert formalTypeParameterList.size() == typeArgumentTypeInfoList.size();

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
        } else if (isFormalTypeInferredAllowed(invokerTypeInfo, methodInfo)) {
            inferredTypeInfoMap.putAll(getInferredFormalTypeParameterMapUsingMethodArguments(methodInfo,
                    argumentTypeInfoList, inferredTypeInfoMap));
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

    private static Map<String, TypeInfo> getInferredFormalTypeParameterMapUsingMethodArguments(MethodInfo methodInfo,
                                                                                               List<TypeInfo> argumentTypeInfoList,
                                                                                               Map<String, TypeInfo> formalTypeInfoMap) {
        List<TypeInfo> methodArgumentTypeInfoList = methodInfo.getArgumentTypeInfoList();
        Map<String, TypeInfo> inferredFormalTypeParameterValueMap = new HashMap<>();

        for (int i = 0; i < methodArgumentTypeInfoList.size(); i++) {
            TypeInfo methodArgument = methodArgumentTypeInfoList.get(i);
            TypeInfo argument = (methodInfo.isVarargs() && methodArgument.isArrayTypeInfo() && i == argumentTypeInfoList.size() || i >= argumentTypeInfoList.size())
                    ? null
                    : argumentTypeInfoList.get(i);

            /*
             * Primitive type cannot be replaced as placeholder of formal type parameter. Replaced with wrapper object.
             */
            if (Objects.nonNull(argument) && argument.isPrimitiveTypeInfo()) {
                argument = new QualifiedTypeInfo(TypeInferenceAPI.getPrimitiveWrapperClassName(argument.getQualifiedClassName()));
            }

            if (methodArgument.isFormalTypeParameterInfo()
                    && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) methodArgument).getTypeParameter())
                    && Objects.nonNull(argument) && !argument.isNullTypeInfo()) {
                FormalTypeParameterInfo formalTypeParameterMethodArgInfo = (FormalTypeParameterInfo) methodArgument;
                inferredFormalTypeParameterValueMap.put(formalTypeParameterMethodArgInfo.getTypeParameter(), argument);

            } else if (methodArgument.isArrayTypeInfo() && Objects.nonNull(argument) && argument.isArrayTypeInfo()) {
                ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;
                ArrayTypeInfo argArray = (ArrayTypeInfo) argument;

                if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()
                        && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) methodArgArray.getElementTypeInfo()).getTypeParameter())) {
                    FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();
                    inferredFormalTypeParameterValueMap.put(formalTypeParameterInfo.getTypeParameter(), argArray.getElementTypeInfo());
                }
            } else if (methodArgument.isParameterizedTypeInfo() && Objects.nonNull(argument)
                    && argument.isParameterizedTypeInfo() && ((ParameterizedTypeInfo) argument).isParameterized()) {
                ParameterizedTypeInfo parameterizedArgTypeInfo = (ParameterizedTypeInfo) argument;

                for (TypeInfo typeArgument: parameterizedArgTypeInfo.getTypeArgumentList()) {
                    if (typeArgument.isFormalTypeParameterInfo()
                            && !formalTypeInfoMap.containsKey(((FormalTypeParameterInfo) typeArgument).getTypeParameter())) {
                        FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeArgument;
                        inferredFormalTypeParameterValueMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo.getBaseTypeInfo());
                    }
                }
            }
        }

        return inferredFormalTypeParameterValueMap;
    }

    private static void populateFormalTypeParametersOfOwningClass(MethodInfo methodInfo, Map<String, TypeInfo> replacedTypeInfoMap) {
        List<TypeInfo> typeInfoList = methodInfo.getArgumentTypeInfoList();
        for (TypeInfo typeInfo : typeInfoList) {
            if (typeInfo.isFormalTypeParameterInfo()) {
                FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeInfo;
                replacedTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo);
            }
        }

        TypeInfo returnTypeInfo = methodInfo.getReturnTypeInfo();
        if (returnTypeInfo.isFormalTypeParameterInfo()) {
            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) returnTypeInfo;
            replacedTypeInfoMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo);
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
        if (!formalTypeParameterMap.isEmpty() && Objects.nonNull(methodInfo.getSignature())) {
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
                                variableDeclarationExpression.getType(), null, fragmentList, owningClassInfo);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }

            @Override
            public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentArtifactSet, javaVersion, importStatementList,
                                variableDeclarationStatement.getType(), null, fragmentList, owningClassInfo);

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
                                                                              Integer fieldVariableStartOffset,
                                                                              List<VariableDeclarationFragment> fragmentList,
                                                                              OwningClassInfo owningClassInfo) {

        TypeInfo declarationTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
                declarationType, owningClassInfo);

        return fragmentList.stream().map(fragment -> {
            ASTNode scopedNode = getVariableDeclarationScopedNode(fragment);
            String name = fragment.getName().getFullyQualifiedName();

            int startOffset = Objects.nonNull(fieldVariableStartOffset)
                    ? fieldVariableStartOffset
                    : fragment.getStartPosition();

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

        return getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importStatementList, name, owningClassInfo);
    }

    private static TypeInfo getTypeInfoFromNameQualifiedType(Set<Artifact> dependentArtifactSet,
                                                             String javaVersion,
                                                             List<String> importStatementList,
                                                             NameQualifiedType nameQualifiedType,
                                                             OwningClassInfo owningClassInfo) {

        String qualifierName = nameQualifiedType.getQualifier().getFullyQualifiedName();
        String simpleName = nameQualifiedType.getName().getFullyQualifiedName();
        String name = qualifierName.concat(".").concat(simpleName);

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

    /*
     * Enclosing class list will consist of anonymous inner class and enclosing class declarations.
     */
    public static List<String> getAllEnclosingClassList(ASTNode node,
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

    public static List<FormalTypeParameterInfo> getAccessibleFormalTypeParameterList(Set<Artifact> dependentArtifactSet,
                                                                                     String javaVersion,
                                                                                     List<String> importStatementList,
                                                                                     OwningClassInfo owningClassInfo,
                                                                                     ASTNode methodOrFieldNode) {
        ASTNode node = methodOrFieldNode;
        List<FormalTypeParameterInfo> accessibleFormalTypeParameterList = new ArrayList<>();

        while (Objects.nonNull(node)) {
            if (node instanceof TypeDeclaration) {
                TypeDeclaration typeDeclaration = (TypeDeclaration) node;
                String qualifiedClassName =
                        getDeclaringClassQualifiedName(typeDeclaration).replaceAll("\\$", ".");

                TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentArtifactSet, javaVersion,
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
                        TypeInfo baseType = getTypeInfo(dependentArtifactSet, javaVersion, importStatementList,
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
                                                                List<String> importStatementList) {

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

        OwningClassInfo owningClassInfo = TypeInferenceAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                getAllEnclosingClassList(classInstanceCreation, dependentArtifactSet, javaVersion, importStatementList),
                Collections.emptyList());

        owningClassInfo.setAccessibleFormalTypeParameterList(
                getAccessibleFormalTypeParameterList(dependentArtifactSet, javaVersion, importStatementList,
                        owningClassInfo, classInstanceCreation));

        Type type = classInstanceCreation.getType();
        TypeInfo classTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion,
                importStatementList, type, owningClassInfo);

        assert Objects.nonNull(classTypeInfo);

        return classTypeInfo.getQualifiedClassName();
    }

}
