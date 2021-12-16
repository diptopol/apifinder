package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.Models.TypeObject;
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
        importStatementList.add("import java.lang.*");

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, methodInvocation);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                methodInvocation, importStatementList, variableNameMap);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           SuperMethodInvocation superMethodInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superMethodInvocation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        importStatementList.add("import java.lang.*");

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, superMethodInvocation);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                superMethodInvocation, importStatementList, variableNameMap);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           ClassInstanceCreation classInstanceCreation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(classInstanceCreation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        importStatementList.add("import java.lang.*");

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, classInstanceCreation);

        List<MethodInfo> methodInfoList = InferenceUtility.getEligibleMethodInfoList(dependentJarInformationSet, javaVersion,
                classInstanceCreation, importStatementList, variableNameMap);

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    ConstructorInvocation constructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(constructorInvocation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        importStatementList.add("import java.lang.*");

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion, importStatementList, constructorInvocation);

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(constructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        String methodName;

        if (className.contains("%.")) {
            methodName = className.substring(className.lastIndexOf("%.") + 2);
        } else if (className.contains(".")) {
            methodName = className.substring(className.lastIndexOf(".") + 1);
        } else {
            methodName = className;
        }

        methodName = methodName.replace("#", ".");

        List<Expression> argumentList = constructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, methodName, numberOfParameters)
                .setInvokerType(callerClassName);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

    public static MethodInfo getMethodInfo(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                    String javaVersion,
                                    SuperConstructorInvocation superConstructorInvocation) {

        CompilationUnit compilationUnit = (CompilationUnit) InferenceUtility.getCompilationUnit(superConstructorInvocation);
        List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
        importStatementList.add("import java.lang.*");

        Map<String, Set<VariableDeclarationDto>> variableNameMap =
                InferenceUtility.getVariableNameMap(dependentJarInformationSet, javaVersion,
                        importStatementList, superConstructorInvocation);

        TypeDeclaration typeDeclaration = (TypeDeclaration) InferenceUtility.getTypeDeclaration(superConstructorInvocation);
        Type superClassType = typeDeclaration.getSuperclassType();
        String superClassName;

        if (superClassType == null) {
            superClassName = "java.lang.Object";
        } else {
            superClassName = InferenceUtility.getTypeObj(dependentJarInformationSet, javaVersion,
                    importStatementList, superClassType).getQualifiedClassName();
        }

        MethodDeclaration methodDeclaration =
                (MethodDeclaration) InferenceUtility.getClosestASTNode(superConstructorInvocation, MethodDeclaration.class);

        String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);
        String callerClassName = className.replace("%", "").replace("#", ".");

        List<Expression> argumentList = superConstructorInvocation.arguments();
        int numberOfParameters = argumentList.size();

        List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(dependentJarInformationSet,
                javaVersion, importStatementList, variableNameMap, argumentList);

        TypeInferenceFluentAPI.Criteria searchCriteria = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependentJarInformationSet, javaVersion,
                importStatementList, superClassName, numberOfParameters)
                .setInvokerType(callerClassName)
                .setSuperInvoker(true);

        for (int i = 0; i < argumentTypeObjList.size(); i++) {
            searchCriteria.setArgumentType(i, argumentTypeObjList.get(i).getQualifiedClassName());
        }

        List<MethodInfo> methodInfoList = searchCriteria.getMethodList();

        return methodInfoList.isEmpty() ? null : methodInfoList.get(0);
    }

}
