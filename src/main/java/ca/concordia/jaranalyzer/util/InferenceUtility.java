package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.*;
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

    private static final List<String> PRIMITIVE_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean"));

    public static List<String> getImportStatementList(CompilationUnit compilationUnit) {
        List<ImportDeclaration> importDeclarationList = compilationUnit.imports();

        return importDeclarationList.stream()
                .map(ImportObject::new)
                .map(ImportObject::getImportStatement)
                .collect(Collectors.toList());
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                             String javaVersion,
                                                             MethodInvocation methodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             String owningPackageName) {

        String methodName = methodInvocation.getName().getIdentifier();
        int numberOfParameters = methodInvocation.arguments().size();
        List<Expression> argumentList = methodInvocation.arguments();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningPackageName);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(methodInvocation,
                        MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);

        boolean isStaticImport = importStatementList.stream()
                .anyMatch(importStatement -> importStatement.startsWith("import static")
                        && importStatement.endsWith(methodName));

        Expression expression = methodInvocation.getExpression();

        TypeObject callerClassTypeObj = null;
        String callerClassName;

        if (Objects.nonNull(expression)) {
            callerClassTypeObj = InferenceUtility.getTypeObjFromExpression(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, expression, owningPackageName);

            callerClassName = callerClassTypeObj.getQualifiedClassName();
        } else {
            callerClassName = isStaticImport ? null : className.replace("%", "").replace("#", ".");
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningPackageName(owningPackageName);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        /*
         * To make the generic classes backward compatible, for raw type any kind of inference will not be conducted.
         */
        if (Objects.nonNull(callerClassTypeObj) && !callerClassTypeObj.isRawType()) {
            InferenceUtility.resolveMethodGenericTypeInfo(methodInfoList, argumentTypeObjList, callerClassTypeObj.getArgumentTypeObjectMap());
        }

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                             String javaVersion,
                                                             SuperMethodInvocation superMethodInvocation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             String owningPackageName) {

        String methodName = superMethodInvocation.getName().getIdentifier();
        int numberOfParameters = superMethodInvocation.arguments().size();
        List<Expression> argumentList = superMethodInvocation.arguments();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningPackageName);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superMethodInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningPackageName(owningPackageName)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();
        InferenceUtility.resolveMethodGenericTypeInfo(methodInfoList, argumentTypeObjList, Collections.emptyMap());

        return methodInfoList;
    }

    public static List<MethodInfo> getEligibleMethodInfoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                             String javaVersion,
                                                             ClassInstanceCreation classInstanceCreation,
                                                             List<String> importStatementList,
                                                             Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                             String owningPackageName) {

        String methodName = classInstanceCreation.getType().toString();
        int numberOfParameters = classInstanceCreation.arguments().size();
        List<Expression> argumentList = classInstanceCreation.arguments();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList, owningPackageName);

        Type type = classInstanceCreation.getType();
        String callerClassName = null;

        if (type.isSimpleType()) {
            SimpleType simpleType = (SimpleType) type;
            Expression simpleTypeExpression = simpleType.getName();
            callerClassName = InferenceUtility.getTypeObjFromExpression(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, simpleTypeExpression, owningPackageName).getQualifiedClassName();
            callerClassName = (callerClassName == null || callerClassName.equals("null")) ? null : callerClassName;
        }

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setOwningPackageName(owningPackageName);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        return searchCriteria.getMethodList();
    }

    public static Map<String, Set<VariableDeclarationDto>> getVariableNameMap(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              ASTNode methodExpression,
                                                                              String owningPackageName) {

        assert methodExpression instanceof MethodInvocation
                || methodExpression instanceof SuperMethodInvocation
                || methodExpression instanceof ClassInstanceCreation
                || methodExpression instanceof ConstructorInvocation
                || methodExpression instanceof SuperConstructorInvocation;

        Map<String, Set<VariableDeclarationDto>> variableNameMap = new HashMap<>();

        Set<VariableDeclarationDto> fieldVariableDeclarationList =
                getFieldVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                        methodExpression, owningPackageName);

        populateVariableNameMap(variableNameMap, fieldVariableDeclarationList);

        MethodDeclaration methodDeclaration = (MethodDeclaration) getClosestASTNode(methodExpression, MethodDeclaration.class);

        if (methodDeclaration != null) {
            Set<VariableDeclarationDto> methodParameterVariableDeclarationList =
                    getMethodParameterVariableDeclarationDtoList(dependentJarInformationSet, javaVersion,
                            importStatementList, methodDeclaration, owningPackageName);

            populateVariableNameMap(variableNameMap, methodParameterVariableDeclarationList);

            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getMethodLocalVariableDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                            methodDeclaration, owningPackageName);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);
        }

        return variableNameMap;
    }

    public static List<TypeObject> getArgumentTypeObjList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                          List<Expression> argumentList,
                                                          String owningPackageName) {
        List<TypeObject> argumentTypeObjList = new ArrayList<>();

        for (Expression argument : argumentList) {
            TypeObject typeObject = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, argument, owningPackageName);

            if (typeObject != null) {
                argumentTypeObjList.add(typeObject);
            }
        }

        return argumentTypeObjList;
    }

    public static Set<VariableDeclarationDto> getFieldVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                 String javaVersion,
                                                                                 List<String> importStatementList,
                                                                                 ASTNode node,
                                                                                 String owningPackageName) {

        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(node);
        FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();

        return Arrays.stream(fieldDeclarations).map(fieldDeclaration -> {
            List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

            return getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                    fieldDeclaration.getType(), fragmentList, owningPackageName);
        }).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public static ASTNode getTypeDeclaration(ASTNode node) {
        return getClosestASTNode(node, TypeDeclaration.class);
    }

    public static ASTNode getCompilationUnit(ASTNode node) {
        return getClosestASTNode(node, CompilationUnit.class);
    }

    public static String getDeclaringClassQualifiedName(BodyDeclaration declaration) {
        String declaringClassQualifiedName = "";
        ASTNode parent = declaration.getParent();

        List<AnonymousClassDeclaration> anonymousClassDeclarationList = getAnonymousClassDeclarationList(declaration);

        while (parent != null) {
            if (parent instanceof CompilationUnit) {
                CompilationUnit cu = (CompilationUnit) parent;
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

            } else if (parent instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) parent;
                String typeDeclarationName = typeDeclaration.getName().getIdentifier();

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = typeDeclarationName;
                } else {
                    declaringClassQualifiedName = typeDeclarationName + "#" + declaringClassQualifiedName;
                }
            } else if (parent instanceof AnonymousClassDeclaration) {
                AnonymousClassDeclaration anonymousClassDeclaration = (AnonymousClassDeclaration) parent;
                String anonymousClassName = anonymousClassDeclarationList.contains(anonymousClassDeclaration)
                        ? String.valueOf(anonymousClassDeclarationList.indexOf(anonymousClassDeclaration)) : "";

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = anonymousClassName;
                } else {
                    declaringClassQualifiedName = anonymousClassName + "#" + declaringClassQualifiedName;
                }
            }
            parent = parent.getParent();
        }

        return declaringClassQualifiedName;
    }

    public static void resolveMethodGenericTypeInfo(List<MethodInfo> methodInfoList,
                                                    List<TypeObject> methodArgumentTypeObjList,
                                                    Map<String, TypeObject> classFormalTypeParameterMap) {

        for (MethodInfo methodInfo : methodInfoList) {
            if (methodInfo.getSignature() != null) {
                MethodArgumentFormalTypeParameterExtractor extractor =
                        new MethodArgumentFormalTypeParameterExtractor(methodArgumentTypeObjList);
                SignatureReader reader = new SignatureReader(methodInfo.getSignature());

                reader.accept(extractor);

                for (String key : extractor.getFormalTypeParameterMap().keySet()) {
                    TypeObject value = extractor.getFormalTypeParameterMap().get(key);

                    if (!classFormalTypeParameterMap.containsKey(key)) {
                        classFormalTypeParameterMap.put(key, value);
                    }
                }

                if (!classFormalTypeParameterMap.isEmpty()) {
                    GenericTypeResolutionAdapter genericTypeResolutionAdapter =
                            new GenericTypeResolutionAdapter(classFormalTypeParameterMap);
                    reader = new SignatureReader(methodInfo.getSignature());
                    reader.accept(genericTypeResolutionAdapter);

                    methodInfo.setArgumentTypes(genericTypeResolutionAdapter.getMethodArgumentTypes());
                    methodInfo.setReturnType(genericTypeResolutionAdapter.getMethodReturnType());
                }
            }
        }
    }

    public static ASTNode getClosestASTNode(ASTNode node, Class<? extends ASTNode> nodeClazz) {
        if (nodeClazz.isInstance(node)) {
            return node;
        }

        ASTNode parent = node.getParent();

        while (!(nodeClazz.isInstance(parent))) {
            parent = parent.getParent();
        }

        return parent;
    }

    public static TypeObject getTypeObjFromExpression(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                      Expression expression,
                                                      String owningPackageName) {
        if (expression == null) {
            return null;
        }

        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(expression);

        if (expression instanceof NullLiteral) {
            return new TypeObject("null");

        } else if (expression instanceof ThisExpression) {
            ThisExpression thisExpression = (ThisExpression) expression;
            String className = thisExpression.getQualifier() != null ? thisExpression.getQualifier().getFullyQualifiedName()
                    : getQualifiedClassName(typeDeclaration);

            className = className.replace("%", "").replace("#", ".");

            return new TypeObject(className);

        } else if (expression instanceof TypeLiteral) {
            Type argumentType = ((TypeLiteral) expression).getType();
            TypeObject argumentTypeObj = getTypeObj(dependentJarInformationSet, javaVersion, importStatementList,
                    argumentType, owningPackageName);

            TypeObject typeObject = new TypeObject("java.lang.Class");

            if (argumentTypeObj.getSignature() != null) {
                ClassSignatureFormalTypeParameterExtractor extractor =
                        new ClassSignatureFormalTypeParameterExtractor(Collections.singletonList(argumentTypeObj));

                SignatureReader signatureReader = new SignatureReader(argumentTypeObj.getSignature());
                signatureReader.accept(extractor);

                typeObject.setArgumentTypeObjectMap(extractor.getFormalTypeParameterMap());
            }

            return typeObject;

        } else if (expression instanceof ParenthesizedExpression) {
            ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) expression;

            return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    parenthesizedExpression.getExpression(), owningPackageName);


        } else if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;

            Expression fieldAccessExpression = fieldAccess.getExpression();
            TypeObject fieldAccessClassObj = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, fieldAccessExpression, owningPackageName);

            String className = fieldAccessClassObj.getQualifiedClassName();

            String name = fieldAccess.getName().getFullyQualifiedName();

            if (className != null && !className.equals("null")) {
                name = className + "." + name;
            }

            TypeObject fieldTypeObj = getTypeObjFromFieldName(dependentJarInformationSet, javaVersion,
                    importStatementList, name, owningPackageName);

            if (Objects.nonNull(fieldTypeObj)) {
                return fieldTypeObj;
            } else {
                return new TypeObject("null");
            }
        } else if (expression instanceof SuperFieldAccess) {
            SuperFieldAccess superFieldAccess = (SuperFieldAccess) expression;

            String name = superFieldAccess.getName().getFullyQualifiedName();

            TypeObject fieldTypeObj = getTypeObjFromFieldName(dependentJarInformationSet, javaVersion,
                    importStatementList, name, owningPackageName);

            if (Objects.nonNull(fieldTypeObj)) {
                return fieldTypeObj;
            } else {
                return new TypeObject("null");
            }
        } else if (expression instanceof BooleanLiteral) {
            return new TypeObject("boolean");

        } else if (expression instanceof StringLiteral) {
            return new TypeObject("java.lang.String");

        } else if (expression instanceof CharacterLiteral) {
            return new TypeObject("char");

        } else if (expression instanceof ConditionalExpression) {
            ConditionalExpression conditionalExpression = (ConditionalExpression) expression;

            Expression then = conditionalExpression.getThenExpression();
            Expression elseExp = conditionalExpression.getElseExpression();

            String thenClassName = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    then, owningPackageName).getQualifiedClassName();
            String elseClassName = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    elseExp, owningPackageName).getQualifiedClassName();

            return new TypeObject(!thenClassName.equals("null") ? thenClassName : elseClassName);

        } else if (expression instanceof CastExpression) {
            return new TypeObject(((CastExpression) expression).getType().toString());

        } else if (expression instanceof NumberLiteral) {
            return new TypeObject(getPrimitiveType((NumberLiteral) expression));

        } else if (expression instanceof ArrayCreation) {
            ArrayCreation arrayCreation = (ArrayCreation) expression;

            ArrayType arrayType = arrayCreation.getType();

            return getTypeObj(dependentJarInformationSet, javaVersion, importStatementList, arrayType, owningPackageName);

        } else if (expression instanceof ArrayAccess) {
            ArrayAccess arrayAccess = (ArrayAccess) expression;

            Expression array = arrayAccess.getArray();
            TypeObject arrayType = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, array, owningPackageName);

            if (arrayAccess.getIndex() != null) {
                arrayType.setQualifiedClassName(StringUtils.substringBeforeLast(arrayType.getQualifiedClassName(),
                        "[]"));
            }

            return arrayType;

        } else if (expression instanceof InfixExpression) {
            InfixExpression infixExpression = (InfixExpression) expression;

            Expression left = infixExpression.getLeftOperand();
            Expression right = infixExpression.getRightOperand();
            InfixExpression.Operator operator = infixExpression.getOperator();

            TypeObject leftExpressionTypeObj = getTypeObjFromExpression(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, left, owningPackageName);
            TypeObject rightExpressionClassNameTypeObj = getTypeObjFromExpression(dependentJarInformationSet,
                    javaVersion, importStatementList, variableNameMap, right, owningPackageName);

            if (operator.equals(InfixExpression.Operator.CONDITIONAL_AND)
                    || operator.equals(InfixExpression.Operator.CONDITIONAL_OR)
                    || operator.equals(InfixExpression.Operator.GREATER)
                    || operator.equals(InfixExpression.Operator.GREATER_EQUALS)
                    || operator.equals(InfixExpression.Operator.EQUALS)
                    || operator.equals(InfixExpression.Operator.NOT_EQUALS)
                    || operator.equals(InfixExpression.Operator.LESS)
                    || operator.equals(InfixExpression.Operator.LESS_EQUALS)) {

                return new TypeObject("boolean");
            } else if (operator.equals(InfixExpression.Operator.PLUS)
                    || operator.equals(InfixExpression.Operator.MINUS)
                    || operator.equals(InfixExpression.Operator.TIMES)
                    || operator.equals(InfixExpression.Operator.DIVIDE)
                    || operator.equals(InfixExpression.Operator.REMAINDER)
                    || operator.equals(InfixExpression.Operator.XOR)
                    || operator.equals(InfixExpression.Operator.AND)
                    || operator.equals(InfixExpression.Operator.OR)) {

                if (operator.equals(InfixExpression.Operator.PLUS)
                        && ("java.lang.String".equals(leftExpressionTypeObj.getQualifiedClassName())
                        || "java.lang.String".equals(rightExpressionClassNameTypeObj.getQualifiedClassName()))) {

                    return new TypeObject("java.lang.String");
                }

                List<String> operandPrecedentList = new ArrayList<String>(Arrays.asList("byte", "short", "int", "long", "float", "double"));

                int positionOfLeft = operandPrecedentList.indexOf(leftExpressionTypeObj.getQualifiedClassName());
                int positionOfRight = operandPrecedentList.indexOf(rightExpressionClassNameTypeObj.getQualifiedClassName());

                return positionOfLeft > positionOfRight ? leftExpressionTypeObj : rightExpressionClassNameTypeObj;
            } else if (operator.equals(InfixExpression.Operator.LEFT_SHIFT)
                    || operator.equals(InfixExpression.Operator.RIGHT_SHIFT_SIGNED)
                    || operator.equals(InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED)) {
                return leftExpressionTypeObj;
            } else {
                return null;
            }

        } else if (expression instanceof PrefixExpression) {
            PrefixExpression prefixExpression = (PrefixExpression) expression;
            return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    prefixExpression.getOperand(), owningPackageName);

        } else if (expression instanceof PostfixExpression) {
            PostfixExpression postfixExpression = (PostfixExpression) expression;

            return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    postfixExpression.getOperand(), owningPackageName);

        } else if (expression instanceof Name) {
            String name = ((Name) expression).getFullyQualifiedName();

            if (expression instanceof QualifiedName) {
                String firstPart = name.substring(0, name.indexOf("."));
                VariableDeclarationDto selected = getClassNameFromVariableMap(firstPart, expression, variableNameMap);
                String className = selected != null ? selected.getTypeObj().getQualifiedClassName() : null;

                if (className != null) {
                    name = className + name.substring(name.indexOf("."));
                }

                TypeObject fieldTypeObj = getTypeObjFromFieldName(dependentJarInformationSet, javaVersion,
                        importStatementList, name, owningPackageName);

                if (Objects.nonNull(fieldTypeObj)) {
                    return fieldTypeObj;
                } else {
                    return getTypeObjFromClassName(dependentJarInformationSet, javaVersion, importStatementList, name,
                            owningPackageName);
                }
            } else if (expression instanceof SimpleName) {
                VariableDeclarationDto selected = getClassNameFromVariableMap(name, expression, variableNameMap);
                TypeObject classTypeObj = selected != null ? selected.getTypeObj() : null;

                if (selected != null && classTypeObj != null) {
                    Type typeOfSelected = selected.getType();
                    List<TypeObject> typeArgumentClassObjList = new ArrayList();

                    if (typeOfSelected.isParameterizedType()) {
                        ParameterizedType parameterizedType = (ParameterizedType) typeOfSelected;
                        typeArgumentClassObjList = getTypeObjList(dependentJarInformationSet, javaVersion,
                                importStatementList, parameterizedType.typeArguments(), owningPackageName);
                        classTypeObj.setParameterized(true);
                    }

                    classTypeObj.setArgumentTypeObjectList(typeArgumentClassObjList);
                }

                if (Objects.nonNull(classTypeObj)) {
                    return classTypeObj;
                } else {
                    TypeObject fieldTypeObj = getTypeObjFromFieldName(dependentJarInformationSet, javaVersion,
                            importStatementList, name, owningPackageName);

                    if (Objects.nonNull(fieldTypeObj)) {
                        return fieldTypeObj;
                    } else {
                        return getTypeObjFromClassName(dependentJarInformationSet, javaVersion, importStatementList,
                                name, owningPackageName);
                    }

                }
            } else {
                return new TypeObject("null");
            }

        } else if (expression instanceof ClassInstanceCreation) {
            ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;

            List<TypeObject> typeArgumentClassObjList = getTypeObjList(dependentJarInformationSet, javaVersion,
                    importStatementList, classInstanceCreation.typeArguments(), owningPackageName);

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                    classInstanceCreation, importStatementList, variableNameMap, owningPackageName);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new TypeObject("null");
            } else {
                String className = methodInfoList.get(0).getClassInfo().getQualifiedName();
                TypeObject classTypeObj = getTypeObjFromClassName(dependentJarInformationSet, javaVersion,
                        importStatementList, className, owningPackageName);
                classTypeObj.setArgumentTypeObjectList(typeArgumentClassObjList);

                return classTypeObj;
            }
        } else if (expression instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) expression;

            List<TypeObject> typeArgumentClassObjList = getTypeObjList(dependentJarInformationSet, javaVersion,
                    importStatementList, methodInvocation.typeArguments(), owningPackageName);

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                    methodInvocation, importStatementList, variableNameMap, owningPackageName);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new TypeObject("null");
            } else {
                String returnTypeClassName = methodInfoList.get(0).getReturnType();

                TypeObject returnTypeClassTypeObj = getTypeObjFromClassName(dependentJarInformationSet, javaVersion,
                        importStatementList, returnTypeClassName, owningPackageName);
                returnTypeClassTypeObj.setArgumentTypeObjectList(typeArgumentClassObjList);

                return returnTypeClassTypeObj;
            }
        } else if (expression instanceof SuperMethodInvocation) {
            SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) expression;

            List<TypeObject> typeArgumentClassObjList = getTypeObjList(dependentJarInformationSet, javaVersion,
                    importStatementList, superMethodInvocation.typeArguments(), owningPackageName);

            List<MethodInfo> methodInfoList = getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                    superMethodInvocation, importStatementList, variableNameMap, owningPackageName);

            // if the getAllMethods returns empty, the method can be a private construct.
            if (methodInfoList.isEmpty()) {
                return new TypeObject("null");
            } else {
                String returnTypeClassName = methodInfoList.get(0).getReturnType();

                TypeObject returnTypeClassTypeObj = getTypeObjFromClassName(dependentJarInformationSet, javaVersion,
                        importStatementList, returnTypeClassName, owningPackageName);
                returnTypeClassTypeObj.setArgumentTypeObjectList(typeArgumentClassObjList);

                return returnTypeClassTypeObj;
            }
        } else if (expression instanceof LambdaExpression) {
            LambdaExpression lambdaExpression = (LambdaExpression) expression;

            ASTNode body = lambdaExpression.getBody();

            if (body instanceof Expression) {
                Expression bodyExpression = (Expression) body;

                return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                        bodyExpression, owningPackageName);
            } else {
                return new TypeObject("null");
            }

        } else {
            return null;
        }
    }

    public static TypeObject getTypeObj(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                        String javaVersion,
                                        List<String> importStatementList,
                                        Type type,
                                        String owningPackageName) {
        if (type == null) {
            return null;
        }

        if (type instanceof PrimitiveType) {
            return new TypeObject(type.toString());

        } else if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            Type elementType = arrayType.getElementType();
            TypeObject elementTypeObj;
            String elementTypeStr;

            if (!elementType.isPrimitiveType()) {
                if (elementType instanceof SimpleType) {
                    elementTypeObj = getTypeObjFromSimpleType(dependentJarInformationSet, javaVersion,
                            importStatementList, ((SimpleType) elementType), owningPackageName);
                    elementTypeStr = elementTypeObj.getQualifiedClassName();

                } else if (elementType instanceof QualifiedType) {
                    elementTypeObj = getTypeObjFromQualifiedType(dependentJarInformationSet, javaVersion,
                            importStatementList, (QualifiedType) elementType, owningPackageName);
                    elementTypeStr = elementTypeObj.getQualifiedClassName();
                } else {
                    throw new IllegalStateException();
                }

            } else {
                elementTypeObj = new TypeObject(elementType.toString());
                elementTypeStr = elementType.toString();
            }

            StringBuilder elementTypeStrBuilder = new StringBuilder(elementTypeStr);
            for (int i = 0; i < arrayType.getDimensions(); i++) {
                elementTypeStrBuilder.append("[]");
            }

            elementTypeObj.setQualifiedClassName(elementTypeStrBuilder.toString());

            return elementTypeObj;

        } else if (type instanceof SimpleType) {
            return getTypeObjFromSimpleType(dependentJarInformationSet, javaVersion, importStatementList,
                    (SimpleType) type, owningPackageName);

        } else if (type instanceof QualifiedType) {
            return getTypeObjFromQualifiedType(dependentJarInformationSet, javaVersion, importStatementList,
                    (QualifiedType) type, owningPackageName);


        } else if (type instanceof ParameterizedType) {
            Type internalType = ((ParameterizedType) type).getType();

            if (internalType instanceof SimpleType) {
                return getTypeObjFromSimpleType(dependentJarInformationSet, javaVersion, importStatementList,
                        (SimpleType) internalType, owningPackageName);

            } else if (internalType instanceof QualifiedType) {
                return getTypeObjFromQualifiedType(dependentJarInformationSet, javaVersion, importStatementList,
                        (QualifiedType) internalType, owningPackageName);

            } else {
                throw new IllegalStateException();
            }
        } else if (type instanceof UnionType) {
            List<Type> typeList = ((UnionType) type).types();

            /*UnionType can be found for multicatch block exception where type is determined based on the common super
            class of all the types. For simplicity, we will use the first type as type of argument. If we can find
            scenario where this approach does not work, we will improve our approach.*/
            Type firstType = typeList.get(0);
            return getTypeObj(dependentJarInformationSet, javaVersion, importStatementList, firstType, owningPackageName);

        } else {
            return new TypeObject(type.toString());
        }
    }

    public static boolean isPrimitiveType(String argumentTypeClassName) {
        return PRIMITIVE_TYPE_LIST.contains(argumentTypeClassName);
    }

    public static String getQualifiedClassName(TypeDeclaration typeDeclaration) {
        String declaringClassQualifiedName = getDeclaringClassQualifiedName(typeDeclaration);
        if (declaringClassQualifiedName.equals("")) {
            return typeDeclaration.getName().getIdentifier();
        } else {
            if (typeDeclaration.isPackageMemberTypeDeclaration()) {
                return declaringClassQualifiedName + "." + typeDeclaration.getName().getIdentifier();
            } else {
                return declaringClassQualifiedName + "#" + typeDeclaration.getName().getIdentifier();
            }
        }
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

    private static Set<VariableDeclarationDto> getMethodParameterVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                            String javaVersion,
                                                                                            List<String> importStatementList,
                                                                                            MethodDeclaration methodDeclaration,
                                                                                            String owningPackageName) {
        if (methodDeclaration != null) {
            List<SingleVariableDeclaration> declarationList = methodDeclaration.parameters();

            return declarationList.stream()
                    .map(declaration -> getVariableDeclarationDto(dependentJarInformationSet, javaVersion,
                            importStatementList, declaration, owningPackageName))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private static Set<VariableDeclarationDto> getMethodLocalVariableDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                             String javaVersion,
                                                                             List<String> importStatementList,
                                                                             MethodDeclaration methodDeclaration,
                                                                             String owningPackageName) {
        Set<VariableDeclarationDto> localVariableDtoSet = new HashSet<>();

        methodDeclaration.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(SingleVariableDeclaration singleVariableDeclaration) {
                VariableDeclarationDto variableDeclarationDto =
                        getVariableDeclarationDto(dependentJarInformationSet, javaVersion, importStatementList,
                                singleVariableDeclaration, owningPackageName);

                localVariableDtoSet.add(variableDeclarationDto);

                return true;
            }

            @Override
            public void endVisit(VariableDeclarationExpression variableDeclarationExpression) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                                variableDeclarationExpression.getType(), fragmentList, owningPackageName);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }

            @Override
            public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                                variableDeclarationStatement.getType(), fragmentList, owningPackageName);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }
        });

        return localVariableDtoSet;
    }

    private static VariableDeclarationDto getVariableDeclarationDto(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                    String javaVersion,
                                                                    List<String> importStatementList,
                                                                    SingleVariableDeclaration declaration,
                                                                    String owningPackageName) {
        String name = declaration.getName().getFullyQualifiedName();
        Type declarationType = declaration.getType();
        TypeObject declarationTypeObj = getTypeObj(dependentJarInformationSet, javaVersion, importStatementList,
                declarationType, owningPackageName);

        ASTNode scopedNode = getVariableDeclarationScopedNode(declaration);

        if (scopedNode != null) {
            int startOffset = scopedNode.getStartPosition();
            int endOffSet = startOffset + scopedNode.getLength();

            return new VariableDeclarationDto(name, declarationTypeObj, new VariableScope(startOffset, endOffSet), declarationType);

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

    private static List<TypeObject> getTypeObjList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                   String javaVersion,
                                                   List<String> importStatementList,
                                                   List<Type> typeList, String owningPackageName) {
        List<TypeObject> typeObjList = new ArrayList<>();

        for (Type type : typeList) {
            typeObjList.add(getTypeObj(dependentJarInformationSet, javaVersion,
                    importStatementList, type, owningPackageName));
        }

        return typeObjList;
    }

    private static List<AnonymousClassDeclaration> getAnonymousClassDeclarationList(BodyDeclaration declaration) {
        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(declaration);

        AnonymousClassVisitor anonymousClassVisitor = new AnonymousClassVisitor();
        typeDeclaration.accept(anonymousClassVisitor);

        return anonymousClassVisitor.getAnonymousClassDeclarationList();
    }

    private static List<VariableDeclarationDto> getVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              Type declarationType,
                                                                              List<VariableDeclarationFragment> fragmentList,
                                                                              String owningPackageName) {

        TypeObject declarationTypeObj = getTypeObj(dependentJarInformationSet, javaVersion, importStatementList,
                declarationType, owningPackageName);

        return fragmentList.stream().map(fragment -> {
            ASTNode scopedNode = getVariableDeclarationScopedNode(fragment);
            String name = fragment.getName().getFullyQualifiedName();

            int startOffset = fragment.getStartPosition();
            int endOffSet = startOffset + (scopedNode != null ? scopedNode.getLength() : 0);

            return new VariableDeclarationDto(name, declarationTypeObj, new VariableScope(startOffset, endOffSet), declarationType);

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

    private static TypeObject getTypeObjFromSimpleType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                       String javaVersion,
                                                       List<String> importStatementList,
                                                       SimpleType simpleType,
                                                       String owningPackageName) {
        String name = simpleType.getName().getFullyQualifiedName();
        return getTypeObjFromClassName(dependentJarInformationSet, javaVersion, importStatementList, name, owningPackageName);
    }

    private static TypeObject getTypeObjFromQualifiedType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          QualifiedType qualifiedType,
                                                          String owningPackageName) {

        String name = qualifiedType.getName().getFullyQualifiedName();
        return getTypeObjFromClassName(dependentJarInformationSet, javaVersion, importStatementList, name, owningPackageName);
    }

    private static TypeObject getTypeObjFromFieldName(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      String fieldName,
                                                      String owningPackageName) {

        List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentJarInformationSet,
                javaVersion, importStatementList, fieldName, owningPackageName);

        if (fieldInfoList.isEmpty()) {
            return null;
        }

        FieldInfo fieldInfo = fieldInfoList.get(0);

        List<TypeObject> typeArgumentClassObjList = new ArrayList<>();

        if (Objects.nonNull(fieldInfo.getSignature())) {
            FieldSignatureFormalTypeParameterExtractor formalTypeParameterExtractor
                    = new FieldSignatureFormalTypeParameterExtractor();

            SignatureReader reader = new SignatureReader(fieldInfo.getSignature());
            reader.accept(formalTypeParameterExtractor);
            typeArgumentClassObjList = formalTypeParameterExtractor.getTypeArgumentClassObjList();
        }

        TypeObject classTypeObj = getTypeObjFromClassName(dependentJarInformationSet, javaVersion, importStatementList,
                fieldInfo.getTypeAsStr(), owningPackageName)
                .setParameterized(!typeArgumentClassObjList.isEmpty());

        classTypeObj.setArgumentTypeObjectList(typeArgumentClassObjList);

        return classTypeObj;
    }

    private static TypeObject getTypeObjFromClassName(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      String className,
                                                      String owningPackageName) {

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList,
                className, owningPackageName);

        if (classInfoList.isEmpty()) {
            return new TypeObject(className);
        }

        ClassInfo classInfo = classInfoList.get(0);

        return new TypeObject(classInfo.getQualifiedName()).setSignature(classInfo.getSignature());
    }

}
