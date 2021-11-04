package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.*;
import ca.concordia.jaranalyzer.TypeInferenceAPI;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import org.objectweb.asm.signature.SignatureReader;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtility {

    public static List<String> getImportStatementList(CompilationUnit compilationUnit) {
        List<ImportDeclaration> importDeclarationList = compilationUnit.imports();

        return importDeclarationList.stream()
                .map(ImportObject::new)
                .map(ImportObject::getImportStatement)
                .collect(Collectors.toList());
    }

    public static Map<String, Set<VariableDeclarationDto>> getVariableNameMap(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                String javaVersion,
                                                                                List<String> importStatementList,
                                                                                ASTNode methodExpression) {

        assert methodExpression instanceof MethodInvocation
                || methodExpression instanceof SuperMethodInvocation
                || methodExpression instanceof ClassInstanceCreation
                || methodExpression instanceof ConstructorInvocation
                || methodExpression instanceof SuperConstructorInvocation;

        Map<String, Set<VariableDeclarationDto>> variableNameMap = new HashMap<>();

        Set<VariableDeclarationDto> fieldVariableDeclarationList =
                getFieldVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList, methodExpression);

        populateVariableNameMap(variableNameMap, fieldVariableDeclarationList);

        MethodDeclaration methodDeclaration = (MethodDeclaration) getClosestASTNode(methodExpression, MethodDeclaration.class);

        if (methodDeclaration != null) {
            Set<VariableDeclarationDto> methodParameterVariableDeclarationList =
                    getMethodParameterVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList, methodDeclaration);

            populateVariableNameMap(variableNameMap, methodParameterVariableDeclarationList);

            Set<VariableDeclarationDto> localVariableDeclarationList =
                    getMethodLocalVariableDtoList(dependentJarInformationSet, javaVersion, importStatementList, methodDeclaration);

            populateVariableNameMap(variableNameMap, localVariableDeclarationList);
        }

        return variableNameMap;
    }

    public static List<TypeObject> getArgumentTypeObjList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                      List<Expression> argumentList) {
        List<TypeObject> argumentTypeObjList = new ArrayList<>();

        for (Expression argument : argumentList) {
            TypeObject typeObject = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, argument);

            if (typeObject != null) {
                argumentTypeObjList.add(typeObject);
            }
        }

        return argumentTypeObjList;
    }

    public static Set<VariableDeclarationDto> getFieldVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                 String javaVersion,
                                                                                  List<String> importStatementList,
                                                                                  ASTNode node) {

        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(node);
        FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();

        return Arrays.stream(fieldDeclarations).map(fieldDeclaration -> {
            List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

            return getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                    fieldDeclaration.getType(), fragmentList);
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
                                              Map<String, String> classFormalTypeParameterMap) {

        for (MethodInfo methodInfo: methodInfoList) {
            if (methodInfo.getSignature() != null) {
                MethodArgumentFormalTypeParameterExtractor extractor =
                        new MethodArgumentFormalTypeParameterExtractor(methodArgumentTypeObjList);
                SignatureReader reader = new SignatureReader(methodInfo.getSignature());

                reader.accept(extractor);

                classFormalTypeParameterMap.putAll(extractor.getFormalTypeParameterMap());

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
                                                  Map<String, String> formalTypeParameterMap) {
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
            TypeObject argumentTypeObj = getTypeObj(dependentJarInformationSet, javaVersion, importStatementList, argumentType);

            ClassSignatureFormalTypeParameterExtractor extractor =
                    new ClassSignatureFormalTypeParameterExtractor(Collections.singletonList(argumentTypeObj.getQualifiedClassName()));

            SignatureReader signatureReader = new SignatureReader(argumentTypeObj.getSignature());
            signatureReader.accept(extractor);

            TypeObject typeObject = new TypeObject("java.lang.Class");

            Map<String, TypeObject> argumentTypeObjMap = extractor.getFormalTypeParameterMap().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new TypeObject(e.getValue())));

            typeObject.setArgumentTypeObjectMap(argumentTypeObjMap);

            return typeObject;

        } else if (expression instanceof ParenthesizedExpression) {
            ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) expression;

            return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    parenthesizedExpression.getExpression(), formalTypeParameterMap);


        } else if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;

            Expression fieldAccessExpression = fieldAccess.getExpression();
            String className = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, fieldAccessExpression, formalTypeParameterMap).getQualifiedClassName();

            String name = fieldAccess.getName().getFullyQualifiedName();

            if (className != null && !className.equals("null")) {
                name = className + "." + name;
            }

            List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

            if (fieldInfoList.size() > 0) {
                FieldInfo fieldInfo = fieldInfoList.get(0);
                populateFormalTypeParameterMapFromFieldInfo(dependentJarInformationSet, javaVersion, importStatementList,
                        fieldInfo, formalTypeParameterMap);


                return new TypeObject(fieldInfo.getTypeAsStr());
            } else {
                return new TypeObject("null");
            }
        } else if (expression instanceof SuperFieldAccess) {
            SuperFieldAccess superFieldAccess = (SuperFieldAccess) expression;

            String name = superFieldAccess.getName().getFullyQualifiedName();

            List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

            if (fieldInfoList.size() > 0) {
                return new TypeObject(fieldInfoList.get(0).getTypeAsStr());
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
                    then, formalTypeParameterMap).getQualifiedClassName();
            String elseClassName = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    elseExp, formalTypeParameterMap).getQualifiedClassName();

            return new TypeObject(!thenClassName.equals("null") ? thenClassName : elseClassName);

        } else if (expression instanceof CastExpression) {
            return new TypeObject(((CastExpression) expression).getType().toString());

        } else if (expression instanceof NumberLiteral) {
            return new TypeObject(getPrimitiveType((NumberLiteral) expression));

        } else if (expression instanceof ArrayCreation) {
            ArrayCreation arrayCreation = (ArrayCreation) expression;

            ArrayType arrayType = arrayCreation.getType();

            return getTypeObj(dependentJarInformationSet, javaVersion, importStatementList, arrayType);

        } else if (expression instanceof ArrayAccess) {
            ArrayAccess arrayAccess = (ArrayAccess) expression;

            Expression array = arrayAccess.getArray();
            TypeObject arrayType = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList,
                    variableNameMap, array, formalTypeParameterMap);

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

            TypeObject leftExpressionTypeObj = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    left, formalTypeParameterMap);
            TypeObject rightExpressionClassNameTypeObj = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    right, formalTypeParameterMap);

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
                    prefixExpression.getOperand(), formalTypeParameterMap);

        } else if (expression instanceof PostfixExpression) {
            PostfixExpression postfixExpression = (PostfixExpression) expression;

            return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    postfixExpression.getOperand(), formalTypeParameterMap);

        } else if (expression instanceof Name) {
            String name = ((Name) expression).getFullyQualifiedName();

            if (expression instanceof QualifiedName) {
                String firstPart = name.substring(0, name.indexOf("."));
                VariableDeclarationDto selected = getClassNameFromVariableMap(firstPart, expression, variableNameMap);
                String className = selected != null ? selected.getTypeStr() : null;

                if (className != null) {
                    name = className + name.substring(name.indexOf("."));
                }

                List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentJarInformationSet,
                        javaVersion, importStatementList, name);

                if (fieldInfoList.size() > 0) {
                    FieldInfo fieldInfo = fieldInfoList.get(0);
                    populateFormalTypeParameterMapFromFieldInfo(dependentJarInformationSet, javaVersion,
                            importStatementList, fieldInfo, formalTypeParameterMap);

                    return new TypeObject(fieldInfo.getTypeAsStr());
                } else {
                    List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

                    if (classInfoList.size() > 0) {
                        return new TypeObject(classInfoList.get(0).getQualifiedName());
                    } else {
                        return new TypeObject("null");
                    }
                }
            } else if (expression instanceof SimpleName) {
                VariableDeclarationDto selected = getClassNameFromVariableMap(name, expression, variableNameMap);
                String className = selected != null ? selected.getTypeStr() : null;

                if (selected != null) {
                    Type typeOfSelected = selected.getType();

                    if (typeOfSelected.isParameterizedType()) {
                        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList, className);

                        ClassInfo classInfo = classInfoList.get(0);

                        if (classInfo.getSignature() != null) {
                            ParameterizedType parameterizedType = (ParameterizedType) typeOfSelected;
                            List<Type> types = parameterizedType.typeArguments();

                            List<String> typeClassNameList = new ArrayList();

                            for (Type type : types) {
                                typeClassNameList.add(getTypeObj(dependentJarInformationSet, javaVersion, importStatementList, type).getQualifiedClassName());
                            }

                            ClassSignatureFormalTypeParameterExtractor formalTypeParameterExtractor =
                                    new ClassSignatureFormalTypeParameterExtractor(typeClassNameList);
                            SignatureReader signatureReader = new SignatureReader(classInfo.getSignature());

                            signatureReader.accept(formalTypeParameterExtractor);

                            if (formalTypeParameterMap != null) {
                                formalTypeParameterMap.putAll(formalTypeParameterExtractor.getFormalTypeParameterMap());
                            }
                        }
                    }
                }

                if (className != null) {
                    return new TypeObject(className);
                } else {
                    List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(dependentJarInformationSet,
                            javaVersion, importStatementList, name);

                    if (fieldInfoList.size() > 0) {
                        FieldInfo fieldInfo = fieldInfoList.get(0);
                        populateFormalTypeParameterMapFromFieldInfo(dependentJarInformationSet, javaVersion,
                                importStatementList, fieldInfo, formalTypeParameterMap);

                        return new TypeObject(fieldInfo.getTypeAsStr());
                    } else {
                        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet,
                                javaVersion, importStatementList, name);

                        if (classInfoList.size() > 0) {
                            return new TypeObject(classInfoList.get(0).getQualifiedName());
                        } else {
                            return new TypeObject("null");
                        }
                    }

                }
            } else {
                return new TypeObject("null");
            }

        } else if (expression instanceof ClassInstanceCreation) {
            ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;
            List<Expression> arguments = classInstanceCreation.arguments();
            String callerClassName = getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    classInstanceCreation.getExpression(), formalTypeParameterMap).getQualifiedClassName();
            callerClassName = (callerClassName == null || callerClassName.equals("null")) ? null : callerClassName;

            List<TypeObject> argumentTypeObjList = getArgumentTypeObjList(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, arguments);

            List<String> argumentTypeClassNameList = argumentTypeObjList.stream()
                    .map(TypeObject::getQualifiedClassName)
                    .collect(Collectors.toList());

            List<MethodInfo> methods = TypeInferenceAPI.getAllMethods(dependentJarInformationSet, javaVersion, importStatementList,
                    classInstanceCreation.getType().toString(),
                    classInstanceCreation.arguments().size(), callerClassName, false,
                    argumentTypeClassNameList.toArray(new String[0]));

            // if the getAllMethods returns empty, the method can be a private construct.
            return new TypeObject(methods.isEmpty() ? "null" : methods.get(0).getClassInfo().getQualifiedName());
        } else if (expression instanceof MethodInvocation) {

            MethodInvocation methodInvocation = (MethodInvocation) expression;
            List<Expression> arguments = methodInvocation.arguments();
            String methodName = methodInvocation.getName().getIdentifier();

            boolean isStaticImport = false;
            for (String importPackage : importStatementList) {
                if (importPackage.startsWith("import static") && importPackage.endsWith(methodName)) {
                    isStaticImport = true;
                }
            }

            String className = getQualifiedClassName(typeDeclaration);
            Expression callerClassExpression = methodInvocation.getExpression();
            String callerClassName = callerClassExpression != null
                    ? getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                    callerClassExpression, formalTypeParameterMap).getQualifiedClassName()
                    : (isStaticImport ? null : className.replace("%", "").replace("#", "."));

            callerClassName = (callerClassName == null || callerClassName.equals("null")) ? null : callerClassName;

            List<TypeObject> argumentTypeObjList = getArgumentTypeObjList(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, arguments);

            List<String> argumentTypeClassNameList = argumentTypeObjList.stream()
                    .map(TypeObject::getQualifiedClassName)
                    .collect(Collectors.toList());

            List<MethodInfo> methods = TypeInferenceAPI.getAllMethods(dependentJarInformationSet, javaVersion, importStatementList,
                    methodName,
                    methodInvocation.arguments().size(), callerClassName, false, argumentTypeClassNameList.toArray(new String[0]));

            // if the getAllMethods returns empty, the method can be a private construct.
            return new TypeObject(methods.isEmpty() ? "null" : methods.get(0).getReturnType());

        } else if (expression instanceof SuperMethodInvocation) {

            SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) expression;
            List<Expression> arguments = superMethodInvocation.arguments();
            String methodName = superMethodInvocation.getName().getIdentifier();

            String className = getQualifiedClassName(typeDeclaration);
            String callerClassName = className.replace("%", "").replace("#", ".");
            List<TypeObject> argumentTypeObjList = getArgumentTypeObjList(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, arguments);

            List<String> argumentTypeClassNameList = argumentTypeObjList.stream()
                    .map(TypeObject::getQualifiedClassName)
                    .collect(Collectors.toList());

            List<MethodInfo> methods = TypeInferenceAPI.getAllMethods(dependentJarInformationSet, javaVersion, importStatementList,
                    methodName,
                    arguments.size(), callerClassName, true, argumentTypeClassNameList.toArray(new String[0]));

            // if the getAllMethods returns empty, the method can be a private construct.
            return new TypeObject(methods.isEmpty() ? "null" : methods.get(0).getReturnType());

        } else if (expression instanceof LambdaExpression) {
            LambdaExpression lambdaExpression = (LambdaExpression) expression;

            ASTNode body = lambdaExpression.getBody();

            if (body instanceof Expression) {
                Expression bodyExpression = (Expression) body;

                return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                        bodyExpression, formalTypeParameterMap);
            } else {
                return new TypeObject("null");
            }

        } else {
            return null;
        }
    }

    public static TypeObject getTypeObjFromExpression(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                  String javaVersion,
                                                  List<String> importStatementList,
                                                  Map<String, Set<VariableDeclarationDto>> variableNameMap,
                                                  Expression expression) {
        return getTypeObjFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap,
                expression, null);
    }

    public static TypeObject getTypeObj(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    List<String> importStatementList,
                                    Type type) {
        if (type == null) {
            return null;
        }

        if (type instanceof PrimitiveType) {
            return new TypeObject(type.toString());

        } else if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            Type elementType = arrayType.getElementType();
            TypeObject elementTypeObj = null;
            String elementTypeStr;

            if (!elementType.isPrimitiveType()) {
                if (elementType instanceof SimpleType) {
                    elementTypeObj = getTypeObjForSimpleType(dependentJarInformationSet, javaVersion, importStatementList, ((SimpleType) elementType));
                    elementTypeStr = elementTypeObj.getQualifiedClassName();

                } else if (elementType instanceof QualifiedType) {
                    elementTypeObj = getTypeNameForQualifiedType(dependentJarInformationSet, javaVersion, importStatementList, (QualifiedType) elementType);
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
            return getTypeObjForSimpleType(dependentJarInformationSet, javaVersion, importStatementList, ((SimpleType) type));

        } else if (type instanceof QualifiedType) {
            return getTypeNameForQualifiedType(dependentJarInformationSet, javaVersion, importStatementList, (QualifiedType) type);


        } else if (type instanceof ParameterizedType) {
            Type internalType = ((ParameterizedType) type).getType();

            if (internalType instanceof SimpleType) {
                return getTypeObjForSimpleType(dependentJarInformationSet, javaVersion, importStatementList, ((SimpleType) internalType));

            } else if (internalType instanceof QualifiedType) {
                return getTypeNameForQualifiedType(dependentJarInformationSet, javaVersion, importStatementList, (QualifiedType) internalType);

            } else {
                throw new IllegalStateException();
            }
        } else {
            return new TypeObject(type.toString());
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
                                                                                             MethodDeclaration methodDeclaration) {
        if (methodDeclaration != null) {
            List<SingleVariableDeclaration> declarationList = methodDeclaration.parameters();

            return declarationList.stream()
                    .map(declaration -> getVariableDeclarationDto(dependentJarInformationSet, javaVersion, importStatementList, declaration))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private static Set<VariableDeclarationDto> getMethodLocalVariableDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              MethodDeclaration methodDeclaration) {
        Set<VariableDeclarationDto> localVariableDtoSet = new HashSet<>();

        methodDeclaration.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(SingleVariableDeclaration singleVariableDeclaration) {
                VariableDeclarationDto variableDeclarationDto =
                        getVariableDeclarationDto(dependentJarInformationSet, javaVersion, importStatementList,
                                singleVariableDeclaration);

                localVariableDtoSet.add(variableDeclarationDto);

                return true;
            }

            @Override
            public void endVisit(VariableDeclarationExpression variableDeclarationExpression) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                                variableDeclarationExpression.getType(), fragmentList);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }

            @Override
            public void endVisit(VariableDeclarationStatement variableDeclarationStatement) {
                List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();

                List<VariableDeclarationDto> variableDeclarationDtoList =
                        getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                                variableDeclarationStatement.getType(), fragmentList);

                localVariableDtoSet.addAll(variableDeclarationDtoList);
            }
        });

        return localVariableDtoSet;
    }

    private static VariableDeclarationDto getVariableDeclarationDto(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                    String javaVersion,
                                                                    List<String> importStatementList,
                                                                    SingleVariableDeclaration declaration) {
        String name = declaration.getName().getFullyQualifiedName();
        Type declarationType = declaration.getType();
        String declarationTypeClassName = getTypeObj(dependentJarInformationSet, javaVersion, importStatementList, declarationType).getQualifiedClassName();

        ASTNode scopedNode = getVariableDeclarationScopedNode(declaration);

        if (scopedNode != null) {
            int startOffset = scopedNode.getStartPosition();
            int endOffSet = startOffset + scopedNode.getLength();

            return new VariableDeclarationDto(name, declarationTypeClassName, new VariableScope(startOffset, endOffSet), declarationType);

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
        if (token.contains(".")) {
            if (token.endsWith("f") || token.endsWith("F"))
                return "float";
            else
                return "double";
        }
        if (!token.contains(".")) {
            if (token.endsWith("l") || token.endsWith("L"))
                return "long";
            else
                return "int";
        }
        return null;
    }

    private static void populateFormalTypeParameterMapFromFieldInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                    String javaVersion,
                                                                    List<String> importStatementList,
                                                                    FieldInfo fieldInfo,
                                                                    Map<String, String> formalTypeParameterMap) {
        if (fieldInfo.getSignature() == null) {
            return;
        }

        FieldSignatureFormalTypeParameterExtractor formalTypeParameterExtractor = new FieldSignatureFormalTypeParameterExtractor();
        SignatureReader reader = new SignatureReader(fieldInfo.getSignature());
        reader.accept(formalTypeParameterExtractor);

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList,
                formalTypeParameterExtractor.getTypeClassName());

        ClassInfo classInfo = classInfoList.get(0);

        ClassSignatureFormalTypeParameterExtractor formalTypeParameterExtractorFromClass =
                new ClassSignatureFormalTypeParameterExtractor(formalTypeParameterExtractor.getTypeArgumentClassNameList());
        reader = new SignatureReader(classInfo.getSignature());
        reader.accept(formalTypeParameterExtractorFromClass);

        if (formalTypeParameterMap != null) {
            formalTypeParameterMap.putAll(formalTypeParameterExtractorFromClass.getFormalTypeParameterMap());
        }
    }

    private static String getQualifiedClassName(TypeDeclaration typeDeclaration) {
        String declaringClassQualifiedName = getDeclaringClassQualifiedName(typeDeclaration);
        if(declaringClassQualifiedName.equals("")) {
            return typeDeclaration.getName().getIdentifier();
        }
        else {
            if (typeDeclaration.isPackageMemberTypeDeclaration()) {
                return declaringClassQualifiedName + "." + typeDeclaration.getName().getIdentifier();
            }
            else {
                return declaringClassQualifiedName + "#" + typeDeclaration.getName().getIdentifier();
            }
        }
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
                                                                              List<VariableDeclarationFragment> fragmentList) {

        String declarationTypeClassName = getTypeObj(dependentJarInformationSet, javaVersion, importStatementList,
                declarationType)
                .getQualifiedClassName();

        return fragmentList.stream().map(fragment -> {
            ASTNode scopedNode = getVariableDeclarationScopedNode(fragment);
            String name = fragment.getName().getFullyQualifiedName();

            int startOffset = fragment.getStartPosition();
            int endOffSet = startOffset + (scopedNode != null ? scopedNode.getLength() : 0);

            return new VariableDeclarationDto(name, declarationTypeClassName, new VariableScope(startOffset, endOffSet), declarationType);

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

    private static TypeObject getTypeObjForSimpleType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      SimpleType simpleType) {
        String name = simpleType.getName().getFullyQualifiedName();

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

/*        if (classInfoList.size() > 1) {
            logger.debug("Fetch Type : "
                    + ", ClassInfoList : " + classInfoList.toString()
                    + ", File Name : " + fileName
                    + ", Import List : " + importedPackages_
                    + ", Type Name: " + name);
        }*/

        return classInfoList.size() == 0
                ? new TypeObject(name)
                : new TypeObject(classInfoList.get(0).getQualifiedName(), classInfoList.get(0).getSignature());
    }

    //TODO: check whether query for qualified name is needed or not
    private static TypeObject getTypeNameForQualifiedType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                          String javaVersion,
                                                          List<String> importStatementList,
                                                          QualifiedType qualifiedType) {

        String name = qualifiedType.getName().getFullyQualifiedName();
        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

        /*if (classInfoList.size() > 1) {
            logger.debug("Fetch Type : "
                    + ", ClassInfoList : " + classInfoList.toString()
                    + ", File Name : " + fileName
                    + ", Import List : " + importedPackages_
                    + ", Type Name: " + name);

        }*/

        return classInfoList.size() == 0
                ? new TypeObject(name)
                : new TypeObject(classInfoList.get(0).getQualifiedName(), classInfoList.get(0).getSignature());
    }

}
