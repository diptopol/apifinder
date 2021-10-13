package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.Models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * @author Diptopol
 * @since 9/24/2021 4:25 PM
 */
public class TypeInferenceV2API {

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           MethodInvocation methodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(methodInvocation);

        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, methodInvocation);

        String methodName = methodInvocation.getName().getIdentifier();
        int numberOfParameters = methodInvocation.arguments().size();
        List<Expression> argumentList = methodInvocation.arguments();

        List<String> argumentClassNameList = InferenceUtility.getArgumentClassNameList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(methodInvocation,
                        MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);

        boolean isStaticImport = importStatementList.stream()
                .anyMatch(importStatement -> importStatement.startsWith("import static")
                        && importStatement.endsWith(methodName));

        Expression expression = methodInvocation.getExpression();

        Map<String, String> classFormalTypeParameterMap = new HashMap<>();
        String callerClassName = expression != null
                ? InferenceUtility.getClassNameFromExpression(dependentJarInformationSet, javaVersion,
                importStatementList, variableNameMap, expression, classFormalTypeParameterMap)
                : (isStaticImport ? null : className.replace("%", "").replace("#", "."));

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName);

        for (int i = 0; i < argumentClassNameList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentClassNameList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();
        InferenceUtility.resolveMethodGenericTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                methodInfoList, argumentList, argumentClassNameList, classFormalTypeParameterMap);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, superMethodInvocation);

        List<Expression> argumentList = superMethodInvocation.arguments();

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superMethodInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        String methodName = superMethodInvocation.getName().getIdentifier();
        int numberOfParameters = superMethodInvocation.arguments().size();

        List<String> argumentClassNameList = InferenceUtility.getArgumentClassNameList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentClassNameList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentClassNameList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();
        InferenceUtility.resolveMethodGenericTypeInfo(dependentJarInformationSet, javaVersion, importStatementList,
                methodInfoList, argumentList, argumentClassNameList, Collections.emptyMap());

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, classInstanceCreation);

        List<Expression> argumentList = classInstanceCreation.arguments();
        Type type = classInstanceCreation.getType();
        String callerClassName = null;

        if (type.isSimpleType()) {
            SimpleType simpleType = (SimpleType) type;
            Expression simpleTypeExpression = simpleType.getName();
            callerClassName = InferenceUtility.getClassNameFromExpression(dependentJarInformationSet, javaVersion,
                    importStatementList, variableNameMap, simpleTypeExpression);
            callerClassName = (callerClassName == null || callerClassName.equals("null")) ? null : callerClassName;
        }

        List<String> argumentClassNameList = InferenceUtility.getArgumentClassNameList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        String methodName = classInstanceCreation.getType().toString();
        int numberOfParameters = classInstanceCreation.arguments().size();

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName);

        for (int i = 0; i < argumentClassNameList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentClassNameList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    ConstructorInvocation constructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, constructorInvocation);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(constructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        String methodName;

        if (className.contains("%.")) {
            methodName = className.substring(className.lastIndexOf("%.") + 2, className.length());
        } else if (className.contains(".")) {
            methodName = className.substring(className.lastIndexOf(".") + 1, className.length());
        } else {
            methodName = className;
        }

        List<Expression> argumentList = constructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<String> argumentClassNameList = InferenceUtility.getArgumentClassNameList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName);

        for (int i = 0; i < argumentClassNameList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentClassNameList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    SuperConstructorInvocation superConstructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superConstructorInvocation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion,
                        importStatementList, superConstructorInvocation);

        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
        Type superClassType = typeDeclaration.getSuperclassType();
        String superClassName;

        if (superClassType == null) {
            superClassName = "java.lang.Object";
        } else {
            superClassName = InferenceUtility.getTypeClassName(dependentJarInformationSet, javaVersion,
                    importStatementList, superClassType);
        }

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superConstructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        List<Expression> argumentList = superConstructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<String> argumentClassNameList = InferenceUtility.getArgumentClassNameList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, superClassName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentClassNameList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentClassNameList.get(i));
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

}
