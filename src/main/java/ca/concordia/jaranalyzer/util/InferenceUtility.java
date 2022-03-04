package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.*;
import ca.concordia.jaranalyzer.Models.typeInfo.*;
import ca.concordia.jaranalyzer.TypeInferenceAPI;
import ca.concordia.jaranalyzer.TypeInferenceFluentAPI;
import io.vavr.Tuple3;
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
                                                   CompilationUnit compilationUnit,
                                                   ASTNode methodNode) {
        // all java classes can access methods and classes of java.lang package without import statement
        importStatementList.add("import java.lang.*");

        // all classes under the current package can be accessed without import statement
        PackageDeclaration packageDeclaration = compilationUnit.getPackage();
        importStatementList.add("import " + packageDeclaration.getName().getFullyQualifiedName() + ".*");

        // added inner classes of the current file in the import statement
        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) InferenceUtility.getAbstractTypeDeclaration(methodNode);
        importStatementList.add("import " + InferenceUtility.getDeclaringClassQualifiedName(abstractTypeDeclaration));

        if (abstractTypeDeclaration instanceof TypeDeclaration) {
            TypeDeclaration typeDeclaration = (TypeDeclaration) abstractTypeDeclaration;

            TypeDeclaration[] innerTypeDeclarationArray = typeDeclaration.getTypes();

            for (TypeDeclaration innerClassDeclaration : innerTypeDeclarationArray) {
                importStatementList.add("import " +
                        InferenceUtility.getDeclaringClassQualifiedName(innerClassDeclaration).replaceAll("\\$", "."));
            }
        }
    }

    /*
     * TODO: Need to think about method type argument.
     */
    public static List<MethodInfo> getEligibleMethodInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                             String javaVersion,
                                                             MethodInvocation methodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             String owningClassQualifiedName) {

        String methodName = methodInvocation.getName().getIdentifier();
        int numberOfParameters = methodInvocation.arguments().size();
        List<Expression> argumentList = methodInvocation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassQualifiedName);

        BodyDeclaration bodyDeclaration =
                (BodyDeclaration) InferenceUtility.getClosestASTNode(methodInvocation,
                        BodyDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(bodyDeclaration);

        boolean isStaticImport = importStatementList.stream()
                .anyMatch(importStatement -> importStatement.startsWith("import static")
                        && importStatement.endsWith(methodName));

        Expression expression = methodInvocation.getExpression();

        TypeInfo callerClassTypeInfo = null;
        String callerClassName;

        if (Objects.nonNull(expression)) {
            callerClassTypeInfo = InferenceUtility.getTypeInfoFromExpression(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, expression, owningClassQualifiedName);

            callerClassName = callerClassTypeInfo.getQualifiedClassName();
        } else {
            callerClassName = isStaticImport ? null : className.replace("%", "").replace("#", ".");

            if (Objects.nonNull(callerClassName)) {
                callerClassTypeInfo = getTypeInfoFromClassName(dependentJarInformationSet, javaVersion,
                        importStatementList, callerClassName, owningClassQualifiedName);
            }
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owningClassQualifiedName);

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
        transformTypeInfoRepresentation(dependentJarInformationSet, javaVersion, importStatementList,
                owningClassQualifiedName, methodInfoList, argumentTypeInfoList, callerClassTypeInfo);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                             String javaVersion,
                                                             SuperMethodInvocation superMethodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             String owningClassQualifiedName) {

        String methodName = superMethodInvocation.getName().getIdentifier();
        int numberOfParameters = superMethodInvocation.arguments().size();
        List<Expression> argumentList = superMethodInvocation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassQualifiedName);

        BodyDeclaration bodyDeclaration =
                (BodyDeclaration) InferenceUtility.getClosestASTNode(superMethodInvocation, BodyDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(bodyDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owningClassQualifiedName)
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
        transformTypeInfoRepresentation(dependentJarInformationSet, javaVersion, importStatementList,
                owningClassQualifiedName, methodInfoList, argumentTypeInfoList, null);
        conversionToVarargsMethodArgument(methodInfoList);

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                             String javaVersion,
                                                             ClassInstanceCreation classInstanceCreation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             String owningClassQualifiedName) {

        String methodName = classInstanceCreation.getType().toString();
        int numberOfParameters = classInstanceCreation.arguments().size();
        List<Expression> argumentList = classInstanceCreation.arguments();

        List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningClassQualifiedName);

        Type type = classInstanceCreation.getType();
        TypeInfo callerClassTypeInfo = InferenceUtility.getTypeInfo(dependentJarInformationSet, javaVersion,
                importStatementList, type, owningClassQualifiedName);
        String callerClassName = Objects.nonNull(callerClassTypeInfo)
                ? callerClassTypeInfo.getQualifiedClassName()
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
                                getTypeInfoList(dependentJarInformationSet, javaVersion, importStatementList,
                                        returnTypeArgumentList, owningClassQualifiedName);

                        assert callerClassTypeInfo.isParameterizedTypeInfo();

                        ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) callerClassTypeInfo;
                        parameterizedTypeInfo.setParameterized(true);
                        parameterizedTypeInfo.setTypeArgumentList(returnTypeInfoArgumentList);
                    }
                }
            }
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningClassQualifiedName(owningClassQualifiedName);

        for (int i = 0; i < argumentTypeInfoList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeInfoList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        InferenceUtility.transformTypeInfoRepresentation(dependentJarInformationSet, javaVersion, importStatementList,
                owningClassQualifiedName, methodInfoList, argumentTypeInfoList, callerClassTypeInfo);

        return methodInfoList;
    }

    public static Map<String, Set<VariableDeclarationDto>> getVariableNameMap(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              ASTNode methodExpression,
                                                                              String owningClassQualifiedName) {

        assert methodExpression instanceof MethodInvocation
                || methodExpression instanceof SuperMethodInvocation
                || methodExpression instanceof ClassInstanceCreation
                || methodExpression instanceof ConstructorInvocation
                || methodExpression instanceof SuperConstructorInvocation;

        Map<String, Set<VariableDeclarationDto>> variableNameMap = new HashMap<>();

        Set<VariableDeclarationDto> fieldVariableDeclarationSet =
                getFieldVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                        methodExpression, owningClassQualifiedName);

        populateVariableNameMap(variableNameMap, fieldVariableDeclarationSet);

        MethodDeclaration methodDeclaration = (MethodDeclaration) getClosestASTNode(methodExpression, MethodDeclaration.class);

        if (methodDeclaration != null) {
            Set<VariableDeclarationDto> methodParameterVariableDeclarationSet =
                    getMethodParameterVariableDeclarationDtoList(dependentJarInformationSet, javaVersion,
                            importStatementList, methodDeclaration, owningClassQualifiedName);

            populateVariableNameMap(variableNameMap, methodParameterVariableDeclarationSet);

            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getMethodLocalVariableDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                            methodDeclaration, owningClassQualifiedName);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);
        }

        return variableNameMap;
    }

    public static List<TypeInfo> getArgumentTypeInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                          List<Expression> argumentList,
                                                          String owningClassQualifiedName) {
        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();

        for (Expression argument : argumentList) {
            TypeInfo typeInfo = getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, argument, owningClassQualifiedName);

            if (typeInfo != null) {
                argumentTypeInfoList.add(typeInfo);
            }
        }

        return argumentTypeInfoList;
    }

    public static Set<VariableDeclarationDto> getFieldVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                 String javaVersion,
                                                                                 List<String> importStatementList,
                                                                                 ASTNode node,
                                                                                 String owningClassQualifiedName) {

        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(node);

        if (abstractTypeDeclaration instanceof TypeDeclaration) {
            TypeDeclaration typeDeclaration = (TypeDeclaration) abstractTypeDeclaration;

            FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();

            return Arrays.stream(fieldDeclarations).map(fieldDeclaration -> {
                List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

                return getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                        fieldDeclaration.getType(), fragmentList, owningClassQualifiedName);
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

    public static TypeInfo getTypeInfoFromExpression(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                      Expression expression,
                                                      String owningClassQualifiedName) {
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

            className = className.replace("%", "").replace("#", ".");

            return new QualifiedTypeInfo(className);

        } else if (expression instanceof TypeLiteral) {
            Type argumentType = ((TypeLiteral) expression).getType();
            TypeInfo argumentTypeInfo = getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                    argumentType, owningClassQualifiedName);

            if (argumentTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo argumentParameterizedTypeInfo = (ParameterizedTypeInfo) argumentTypeInfo;

                ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo("java.lang.Class");
                parameterizedTypeInfo.setTypeArgumentList(argumentParameterizedTypeInfo.getTypeArgumentList());

                return parameterizedTypeInfo;
            } else {
                return new QualifiedTypeInfo("java.lang.Class");
            }
        } else if (expression instanceof ParenthesizedExpression) {
            ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) expression;

            return getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    parenthesizedExpression.getExpression(), owningClassQualifiedName);


        } else if (expression instanceof FieldAccess) {
            /*
             * There is a scope of fetching fieldAccess from variableNameMap. For that we need to keep origin of the
             * variable (e.g., field instance, local variable, method argument).
             *
             * TODO: store origin of variable
             */
            FieldAccess fieldAccess = (FieldAccess) expression;

            Expression fieldAccessExpression = fieldAccess.getExpression();
            TypeInfo fieldAccessTypeInfo = getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, fieldAccessExpression, owningClassQualifiedName);

            String className = fieldAccessTypeInfo.getName();

            String name = fieldAccess.getName().getFullyQualifiedName();

            if (className != null && !className.equals("null")) {
                name = className + "." + name;
            }

            TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentJarInformationSet, javaVersion,
                    importStatementList, name, owningClassQualifiedName);

            assert Objects.nonNull(fieldTypeInfo);

            return fieldTypeInfo;
        } else if (expression instanceof SuperFieldAccess) {
            SuperFieldAccess superFieldAccess = (SuperFieldAccess) expression;

            String name = superFieldAccess.getName().getFullyQualifiedName();

            TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentJarInformationSet, javaVersion,
                    importStatementList, name, owningClassQualifiedName);

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

            TypeInfo thenExpressionTypeInfo = getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    then, owningClassQualifiedName);
            TypeInfo elseExpressionTypeInfo = getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    elseExp, owningClassQualifiedName);

            return !thenExpressionTypeInfo.isNullTypeInfo() ? thenExpressionTypeInfo : elseExpressionTypeInfo;
        } else if (expression instanceof CastExpression) {
            Type castedType = ((CastExpression) expression).getType();

            return getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList, castedType,
                    owningClassQualifiedName);

        } else if (expression instanceof NumberLiteral) {
            return new PrimitiveTypeInfo(getPrimitiveType((NumberLiteral) expression));

        } else if (expression instanceof ArrayCreation) {
            ArrayCreation arrayCreation = (ArrayCreation) expression;

            ArrayType arrayType = arrayCreation.getType();

            return getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList, arrayType, owningClassQualifiedName);
        } else if (expression instanceof ArrayAccess) {
            /*
             * In array access, we are trying to determine the type of the variable. There can be two scenarios.
             * Scenario 1: for single dimension array access (e.g., obj[i]) we will get the type of obj.
             *
             * Scenario 2: for multiple dimension array access (e.g., obj[i][j]) we will get reduced dimension array.
             */

            ArrayAccess arrayAccess = (ArrayAccess) expression;

            Expression array = arrayAccess.getArray();
            TypeInfo typeInfo = getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, array, owningClassQualifiedName);

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

            TypeInfo leftExpressionTypeInfo = getTypeInfoFromExpression(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, left, owningClassQualifiedName);
            TypeInfo rightExpressionTypeInfo = getTypeInfoFromExpression(dependentJarInformationSet,
                    javaVersion, importStatementList, variableNameMap, right, owningClassQualifiedName);

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

            return getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    prefixExpression.getOperand(), owningClassQualifiedName);

        } else if (expression instanceof PostfixExpression) {
            PostfixExpression postfixExpression = (PostfixExpression) expression;

            return getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    postfixExpression.getOperand(), owningClassQualifiedName);

        } else if (expression instanceof Name) {
            String name = ((Name) expression).getFullyQualifiedName();

            if (expression instanceof QualifiedName) {
                String firstPart = name.substring(0, name.indexOf("."));
                VariableDeclarationDto selected = getClassNameFromVariableMap(firstPart, expression, variableNameMap);
                String className = selected != null ? selected.getTypeInfo().getQualifiedClassName() : null;

                if (className != null) {
                    name = className + name.substring(name.indexOf("."));
                }

                TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentJarInformationSet, javaVersion,
                        importStatementList, name, owningClassQualifiedName);

                if (Objects.nonNull(fieldTypeInfo)) {
                    return fieldTypeInfo;
                } else {
                    return getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList, name,
                            owningClassQualifiedName);
                }
            } else if (expression instanceof SimpleName) {
                VariableDeclarationDto selected = getClassNameFromVariableMap(name, expression, variableNameMap);
                TypeInfo classTypeInfo = selected != null ? selected.getTypeInfo() : null;

                if (Objects.nonNull(classTypeInfo)) {
                    return classTypeInfo;
                } else {
                    TypeInfo fieldTypeInfo = getTypeInfoFromFieldName(dependentJarInformationSet, javaVersion,
                            importStatementList, name, owningClassQualifiedName);

                    if (Objects.nonNull(fieldTypeInfo)) {
                        return fieldTypeInfo;
                    } else {
                        return getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList,
                                name, owningClassQualifiedName);
                    }
                }
            } else {
                throw new IllegalStateException();
            }

        } else if (expression instanceof ClassInstanceCreation) {
            ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;

            List<TypeInfo> typeArgumentList = getTypeInfoList(dependentJarInformationSet, javaVersion,
                    importStatementList, classInstanceCreation.typeArguments(), owningClassQualifiedName);

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                    classInstanceCreation, importStatementList, variableNameMap, owningClassQualifiedName);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new NullTypeInfo();
            } else {
                String className = methodInfoList.get(0).getClassInfo().getQualifiedName();

                TypeInfo classTypeInfo = getTypeInfoFromClassName(dependentJarInformationSet, javaVersion,
                        importStatementList, className, owningClassQualifiedName);

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

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                    methodInvocation, importStatementList, variableNameMap, owningClassQualifiedName);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new NullTypeInfo();
            } else {
                return methodInfoList.get(0).getReturnTypeInfo();
            }
        } else if (expression instanceof SuperMethodInvocation) {
            SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) expression;

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                    superMethodInvocation, importStatementList, variableNameMap, owningClassQualifiedName);

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

                return getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                        bodyExpression, owningClassQualifiedName);
            } else {
                return new NullTypeInfo();
            }

        } else if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;

            return getTypeInfoFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, assignment.getLeftHandSide(), owningClassQualifiedName);

        } else {
            return null;
        }
    }

    public static TypeInfo getTypeInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                        String javaVersion,
                                        List<String> importStatementList,
                                        Type type,
                                        String owningClassQualifiedName) {
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
                    TypeInfo typeInfo = getTypeInfoFromSimpleType(dependentJarInformationSet, javaVersion,
                            importStatementList, ((SimpleType) elementType), owningClassQualifiedName);

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());

                } else if (elementType instanceof QualifiedType) {
                    TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentJarInformationSet, javaVersion,
                            importStatementList, (QualifiedType) elementType, owningClassQualifiedName);

                    return new ArrayTypeInfo(typeInfo, arrayType.getDimensions());

                } else if (elementType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) elementType;

                    List<TypeInfo> typeArgumentList = getTypeInfoList(dependentJarInformationSet, javaVersion,
                            importStatementList, parameterizedType.typeArguments(), owningClassQualifiedName);

                    TypeInfo typeInfo = getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                            parameterizedType.getType(), owningClassQualifiedName);

                    assert typeInfo.isParameterizedTypeInfo();

                    ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;
                    parameterizedTypeInfo.setParameterized(true);
                    parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);

                    return new ArrayTypeInfo(parameterizedTypeInfo, arrayType.getDimensions());
                } else {
                    throw new IllegalStateException();
                }

            } else {
                return new ArrayTypeInfo(new PrimitiveTypeInfo(elementType.toString()), arrayType.getDimensions());
            }
        } else if (type instanceof SimpleType) {
            return getTypeInfoFromSimpleType(dependentJarInformationSet, javaVersion, importStatementList,
                    (SimpleType) type, owningClassQualifiedName);

        } else if (type instanceof QualifiedType) {
            return getTypeInfoFromQualifiedType(dependentJarInformationSet, javaVersion, importStatementList,
                    (QualifiedType) type, owningClassQualifiedName);


        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type internalType = parameterizedType.getType();

            List<TypeInfo> typeArgumentList = getTypeInfoList(dependentJarInformationSet, javaVersion,
                    importStatementList, parameterizedType.typeArguments(), owningClassQualifiedName);

            if (internalType instanceof SimpleType) {
                TypeInfo typeInfo = getTypeInfoFromSimpleType(dependentJarInformationSet, javaVersion, importStatementList,
                        (SimpleType) internalType, owningClassQualifiedName);

                assert typeInfo.isParameterizedTypeInfo();

                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

                if (!typeArgumentList.isEmpty()) {
                    parameterizedTypeInfo.setParameterized(true);
                    parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);
                }

                return parameterizedTypeInfo;

            } else if (internalType instanceof QualifiedType) {
                TypeInfo typeInfo = getTypeInfoFromQualifiedType(dependentJarInformationSet, javaVersion, importStatementList,
                        (QualifiedType) internalType, owningClassQualifiedName);

                assert typeInfo.isParameterizedTypeInfo();

                ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;
                parameterizedTypeInfo.setParameterized(true);
                parameterizedTypeInfo.setTypeArgumentList(typeArgumentList);

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
            return getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList, firstType, owningClassQualifiedName);

        } else if (type instanceof WildcardType) {
            WildcardType wildCardType = (WildcardType) type;

            Type boundType = wildCardType.getBound();

            if (Objects.nonNull(boundType)) {
                return getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList, boundType, owningClassQualifiedName);
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
    public static void transformTypeInfoRepresentation(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                       String javaVersion,
                                                       List<String> importStatementList,
                                                       String owningClassQualifiedName,
                                                       List<MethodInfo> methodInfoList,
                                                       List<TypeInfo> argumentTypeInfoList,
                                                       TypeInfo invokerTypeInfo) {

        for (MethodInfo methodInfo : methodInfoList) {
            convertParameterizedTypeIfRequired(dependentJarInformationSet, javaVersion, importStatementList,
                    owningClassQualifiedName, methodInfo);

            Map<String, TypeInfo> replacedTypeInfoMap = new HashMap<>();

            /*
             * Caller class may not be invoker sometimes. Any super class of caller class can be invoker. So added
             * this class check before extracting formal type parameters of invoker type.
             */
            if (!invokerTypeInfo.getQualifiedClassName().equals(methodInfo.getQualifiedClassName())) {
                invokerTypeInfo = methodInfo.getClassInfo().getTypeInfo();
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
                    if (methodInfo.getReturnTypeInfo().isFormalTypeParameterInfo()) {
                        FormalTypeParameterInfo formalTypeParameterMethodReturnTypeInfo = (FormalTypeParameterInfo) methodInfo.getReturnTypeInfo();

                        if (invokerFormalTypeParameterMap.containsKey(formalTypeParameterMethodReturnTypeInfo.getTypeParameter())) {
                            FormalTypeParameterInfo invokerFormalTypeParameterInfo =
                                    invokerFormalTypeParameterMap.get(formalTypeParameterMethodReturnTypeInfo.getTypeParameter());

                            methodInfo.setReturnTypeInfo(invokerFormalTypeParameterInfo.getBaseTypeInfo());

                            replacedTypeInfoMap.put(invokerFormalTypeParameterInfo.getTypeParameter(), invokerFormalTypeParameterInfo.getBaseTypeInfo());
                        }
                    }
                } else {
                    /*
                     * If callerClass is ParameterizedTypeInfo but parameterized=false, then there can be two scenarios.
                     *
                     * Scenario 1. the callerClass can be owning class, then we should show the formalTypeParameterInfo
                     * as it is.
                     *
                     * Scenario 2. the callerClass can be another class, then we have to show the base type of formalTypeParameterInfo
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

                                    TypeInfo typeInfo = methodInfo.getClassInfo().getQualifiedName().equals(owningClassQualifiedName)
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

                                TypeInfo typeInfo = methodInfo.getClassInfo().getQualifiedName().equals(owningClassQualifiedName)
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

                Map<String, TypeInfo> inferredFormalTypeParameterValueMap = new HashMap<>();

                for (int i = 0; i < methodArgumentTypeInfoList.size(); i++) {
                    TypeInfo methodArgument = methodArgumentTypeInfoList.get(i);
                    TypeInfo argument = methodInfo.isVarargs() && methodArgument.isArrayTypeInfo() && i == argumentTypeInfoList.size()
                            ? null
                            : argumentTypeInfoList.get(i);

                    if (methodArgument.isFormalTypeParameterInfo() && Objects.nonNull(argument)) {
                        FormalTypeParameterInfo formalTypeParameterMethodArgInfo = (FormalTypeParameterInfo) methodArgument;
                        inferredFormalTypeParameterValueMap.put(formalTypeParameterMethodArgInfo.getTypeParameter(), argument);

                    } else if (methodArgument.isArrayTypeInfo() && Objects.nonNull(argument) && argument.isArrayTypeInfo()) {
                        ArrayTypeInfo methodArgArray = (ArrayTypeInfo) methodArgument;
                        ArrayTypeInfo argArray = (ArrayTypeInfo) argument;

                        if (methodArgArray.getElementTypeInfo().isFormalTypeParameterInfo()) {
                            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) methodArgArray.getElementTypeInfo();
                            inferredFormalTypeParameterValueMap.put(formalTypeParameterInfo.getTypeParameter(), argArray.getElementTypeInfo());
                        }
                    }
                }

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
                                        : formalTypeParameterInfo.getBaseTypeInfo();

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
                processFormalTypeParameter(dependentJarInformationSet, javaVersion, importStatementList,
                        owningClassQualifiedName, methodInfo, replacedTypeInfoMap, invokerFormalTypeParameterInfo);
            }

            transformTypeRepresentation(methodInfo, replacedTypeInfoMap);
        }
    }

    /*
     * There are scenarios when parameterized type is called without type arguments in method signature.
     * (e.g., public Stack getCurrentSeriesPoints() {})
     */
    private static void convertParameterizedTypeIfRequired(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                           String javaVersion,
                                                           List<String> importStatementList,
                                                           String owningClassQualifiedName,
                                                           MethodInfo methodInfo) {

        for (int i = 0; i < methodInfo.getArgumentTypeInfoList().size(); i++) {
            TypeInfo argument = methodInfo.getArgumentTypeInfoList().get(i);

            if (argument.isQualifiedTypeInfo()) {
                QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) argument;

                TypeInfo typeInfo = InferenceUtility.getTypeInfoFromClassName(dependentJarInformationSet, javaVersion,
                        importStatementList, qualifiedTypeInfo.getQualifiedClassName(), owningClassQualifiedName);

                if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                    methodInfo.getArgumentTypeInfoList().set(i, typeInfo);
                }
            }
        }

        if (methodInfo.getReturnTypeInfo().isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) methodInfo.getReturnTypeInfo();

            TypeInfo typeInfo = InferenceUtility.getTypeInfoFromClassName(dependentJarInformationSet, javaVersion,
                    importStatementList, qualifiedTypeInfo.getQualifiedClassName(), owningClassQualifiedName);

            if (Objects.nonNull(typeInfo) && typeInfo.isParameterizedTypeInfo()) {
                methodInfo.setReturnTypeInfo(typeInfo);
            }
        }
    }

    private static void processFormalTypeParameter(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                   String javaVersion, List<String> importStatementList,
                                                   String owningClassQualifiedName,
                                                   MethodInfo methodInfo,
                                                   Map<String, TypeInfo> replacedTypeInfoMap,
                                                   FormalTypeParameterInfo invokerFormalTypeParameterInfo) {
        TypeInfo baseType = invokerFormalTypeParameterInfo.getBaseTypeInfo();

        TypeInfo invokerBaseTypeInfo = getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList,
                baseType.getQualifiedClassName(), owningClassQualifiedName);

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
        if (!formalTypeParameterMap.isEmpty()) {
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

    private static Set<VariableDeclarationDto> getMethodParameterVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                            String javaVersion,
                                                                                            List<String> importStatementList,
                                                                                            MethodDeclaration methodDeclaration,
                                                                                            String owningClassQualifiedName) {
        if (methodDeclaration != null) {
            List<SingleVariableDeclaration> declarationList = methodDeclaration.parameters();

            return declarationList.stream()
                    .map(declaration -> getVariableDeclarationDto(dependentJarInformationSet, javaVersion,
                            importStatementList, declaration, owningClassQualifiedName))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private static Set<VariableDeclarationDto> getMethodLocalVariableDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                             String javaVersion,
                                                                             List<String> importStatementList,
                                                                             MethodDeclaration methodDeclaration,
                                                                             String owningClassQualifiedName) {
        Set<VariableDeclarationDto> localVariableDtoSet = new HashSet<>();

        methodDeclaration.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(SingleVariableDeclaration singleVariableDeclaration) {
                VariableDeclarationDto variableDeclarationDto =
                        getVariableDeclarationDto(dependentJarInformationSet, javaVersion, importStatementList,
                                singleVariableDeclaration, owningClassQualifiedName);

                localVariableDtoSet.add(variableDeclarationDto);

                return true;
            }

            @Override
            public void endVisit(VariableDeclarationExpression variableDeclarationExpression) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                                variableDeclarationExpression.getType(), fragmentList, owningClassQualifiedName);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }

            @Override
            public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                                variableDeclarationStatement.getType(), fragmentList, owningClassQualifiedName);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }
        });

        return localVariableDtoSet;
    }

    private static VariableDeclarationDto getVariableDeclarationDto(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                    String javaVersion,
                                                                    List<String> importStatementList,
                                                                    SingleVariableDeclaration declaration,
                                                                    String owningClassQualifiedName) {
        String name = declaration.getName().getFullyQualifiedName();
        Type declarationType = declaration.getType();
        TypeInfo declarationTypeInfo = getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                declarationType, owningClassQualifiedName);

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

    private static List<TypeInfo> getTypeInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                   String javaVersion,
                                                   List<String> importStatementList,
                                                   List<Type> typeList, String owningClassQualifiedName) {
        List<TypeInfo> typeInfoList = new ArrayList<>();

        for (Type type : typeList) {
            typeInfoList.add(getTypeInfo(dependentJarInformationSet, javaVersion,
                    importStatementList, type, owningClassQualifiedName));
        }

        return typeInfoList;
    }

    private static List<AnonymousClassDeclaration> getAnonymousClassDeclarationList(BodyDeclaration declaration) {
        AbstractTypeDeclaration abstractTypeDeclaration = (AbstractTypeDeclaration) getAbstractTypeDeclaration(declaration);

        AnonymousClassVisitor anonymousClassVisitor = new AnonymousClassVisitor();
        abstractTypeDeclaration.accept(anonymousClassVisitor);

        return anonymousClassVisitor.getAnonymousClassDeclarationList();
    }

    private static List<VariableDeclarationDto> getVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              Type declarationType,
                                                                              List<VariableDeclarationFragment> fragmentList,
                                                                              String owningClassQualifiedName) {

        TypeInfo declarationTypeInfo = getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                declarationType, owningClassQualifiedName);

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

    private static TypeInfo getTypeInfoFromSimpleType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                       String javaVersion,
                                                       List<String> importStatementList,
                                                       SimpleType simpleType,
                                                       String owningClassQualifiedName) {
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
                    Type boundType = boundTypeList.get(0) instanceof ParameterizedType
                            ? ((ParameterizedType) boundTypeList.get(0)).getType()
                            : (ParameterizedType) boundTypeList.get(0);

                    TypeInfo boundTypeInfo = getTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                            boundType, owningClassQualifiedName);

                    return new FormalTypeParameterInfo(name, new QualifiedTypeInfo(boundTypeInfo.getQualifiedClassName()));
                }
            }
        }

        /*
         * Checking formal type parameter of thw owning class
         */
        TypeInfo owningClassTypeInfo = getTypeInfoFromClassName(dependentJarInformationSet, javaVersion,
                importStatementList, owningClassQualifiedName, owningClassQualifiedName);

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

        return getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList, name, owningClassQualifiedName);
    }

    private static TypeInfo getTypeInfoFromQualifiedType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          QualifiedType qualifiedType,
                                                          String owningCLassQualifiedName) {

        String name = qualifiedType.getName().getFullyQualifiedName();
        return getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList, name, owningCLassQualifiedName);
    }

    private static TypeInfo getTypeInfoFromFieldName(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      String fieldName,
                                                      String owningClassQualifiedName) {

        List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentJarInformationSet,
                javaVersion, importStatementList, fieldName, owningClassQualifiedName);

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

                TypeInfo typeInfo = getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList,
                        fieldTypeClassName, owningClassQualifiedName);

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

            return getTypeInfoFromClassName(dependentJarInformationSet, javaVersion, importStatementList,
                    fieldTypeClassName, owningClassQualifiedName);
        }
    }

    private static TypeInfo getTypeInfoFromClassName(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      String className,
                                                      String owningClassQualifiedName) {
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

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList,
                className, owningClassQualifiedName);

        assert !classInfoList.isEmpty();

        return getTypeInfoFromClassInfo(className, classInfoList.get(0));
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
