package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.Models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

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

        Map<String, String> formalTypeParameterMap = new HashMap<>();
        String callerClassName = expression != null
                ? InferenceUtility.getClassNameFromExpression(dependentJarInformationSet, javaVersion, importStatementList, variableNameMap, expression, formalTypeParameterMap)
                : (isStaticImport ? null : className.replace("%", "").replace("#", "."));

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

}
