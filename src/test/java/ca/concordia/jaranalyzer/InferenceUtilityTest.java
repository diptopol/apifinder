package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.TypeObject;
import ca.concordia.jaranalyzer.Models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import ca.concordia.jaranalyzer.util.PropertyReader;
import org.eclipse.jdt.core.dom.*;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtilityTest {

    @Test
    public void testImportStatementExtraction() {
        String filePath = "testProjectDirectory/jfreechart-fx/jfreechart-fx/src/main/java/org/jfree/chart/fx/interaction/AbstractMouseHandlerFX.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted")) {
                    List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

                    assert ("[import javafx.scene.input.MouseEvent, import javafx.scene.input.ScrollEvent, " +
                            "import org.jfree.chart.fx.ChartCanvas, import org.jfree.chart.util.Args]").equals(importStatementList.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testFieldVariableDeclarationList() {
        String filePath = "testProjectDirectory/jfreechart-fx/jfreechart-fx/src/main/java/org/jfree/chart/fx/interaction/AbstractMouseHandlerFX.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted")) {
                    List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

                    Set<VariableDeclarationDto> fieldVariableDeclarationDtoList
                            = InferenceUtility.getFieldVariableDeclarationDtoList(Collections.emptySet(), javaVersion,
                            importStatementList, methodInvocation);

                    assert "[altKey, ctrlKey, enabled, id, metaKey, shiftKey]"
                            .equals(fieldVariableDeclarationDtoList.stream()
                                    .map(VariableDeclarationDto::getName)
                                    .sorted()
                                    .collect(Collectors.toList())
                                    .toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testQualifiedClassNameExtraction() {
        String filePath = "testProjectDirectory/jfreechart-fx/jfreechart-fx/src/main/java/org/jfree/chart/fx/interaction/AbstractMouseHandlerFX.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted")) {
                    MethodDeclaration methodDeclaration =
                            (MethodDeclaration) InferenceUtility.getClosestASTNode(methodInvocation,
                                    MethodDeclaration.class);
                    String className = InferenceUtility.getDeclaringClassQualifiedName(methodDeclaration);

                    assert "org.jfree.chart.fx.interaction.AbstractMouseHandlerFX".equals(className);
                };

                return false;
            }
        });
    }

    @Test
    public void testExtractArgumentClassNameList() {
        String filePath = "testProjectDirectory/jfreechart-fx/jfreechart-fx/src/main/java/org/jfree/chart/fx/ChartCanvas.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted(listener,\"listener\")")) {
                    List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);

                    Map<String, Set<VariableDeclarationDto>> variableNameMap =
                            InferenceUtility.getVariableNameMap(Collections.emptySet(), javaVersion,
                                    importStatementList, methodInvocation);

                    List<Expression> argumentList = methodInvocation.arguments();

                    List<TypeObject> argumentTypeObjList = InferenceUtility.getArgumentTypeObjList(Collections.emptySet(),
                            javaVersion, importStatementList, variableNameMap, argumentList);

                    List<String> argumentTypeClassNameList = argumentTypeObjList.stream()
                            .map(TypeObject::getQualifiedClassName)
                            .collect(Collectors.toList());

                    assert "[ChartMouseListenerFX, java.lang.String]".equals(argumentTypeClassNameList.toString());
                };

                return false;
            }
        });
    }

}
