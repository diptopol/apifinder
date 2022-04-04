package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtilityTest {

    private static Set<Artifact> jarInformationSet;
    private static String javaVersion;

    /*
     * For running the test we have to check out to a specific commit. So after completion of all test we intend to
     * revert the change. So to do the revert we are storing the defaultBranchName here.
     */
    private static String defaultBranchName;

    private static Git git;

    @BeforeClass
    public static void loadExternalLibrary() {
        javaVersion = getProperty("java.version");

        String projectName = "jfreechart-fx";
        String projectUrl = "https://github.com/jfree/jfreechart-fx.git";
        String commitId = "35d53459e854a2bb39d6f012ce9b78ec8ab7f0f9";

        loadTestProjectDirectory(projectName, projectUrl, commitId);
        loadExternalJars(projectName, projectUrl, commitId);
        loadJFreeChartFx();
    }

    @AfterClass
    public static void revertGitChange() {
        GitUtil.checkoutToCommit(git, defaultBranchName);
    }

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

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted")) {
                    List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
                    InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

                    Set<VariableDeclarationDto> fieldVariableDeclarationDtoList
                            = InferenceUtility.getFieldVariableDeclarationDtoList(jarInformationSet, javaVersion,
                            importStatementList, methodInvocation, null);

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

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted(listener,\"listener\")")) {
                    List<String> importStatementList = InferenceUtility.getImportStatementList(compilationUnit);
                    InferenceUtility.addSpecialImportStatements(importStatementList, compilationUnit);

                    Map<String, Set<VariableDeclarationDto>> variableNameMap =
                            InferenceUtility.getVariableNameMap(jarInformationSet, javaVersion,
                                    importStatementList, methodInvocation, null);

                    List<Expression> argumentList = methodInvocation.arguments();

                    List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(Collections.emptySet(),
                            javaVersion, importStatementList, variableNameMap, argumentList, null);

                    List<String> argumentTypeClassNameList = argumentTypeInfoList.stream()
                            .map(TypeInfo::getQualifiedClassName)
                            .collect(Collectors.toList());

                    assert "[org.jfree.chart.fx.interaction.ChartMouseListenerFX, java.lang.String]".equals(argumentTypeClassNameList.toString());
                };

                return false;
            }
        });
    }

    private static void loadTestProjectDirectory(String projectName, String projectUrl, String commitId) {
        Path projectDirectory = Paths.get("testProjectDirectory").resolve(projectName);

        git = GitUtil.openRepository(projectName, projectUrl, projectDirectory);
        defaultBranchName = GitUtil.checkoutToCommit(git, commitId);
    }

    private static void loadExternalJars(String projectName, String projectUrl, String commitId) {
        Path pathToProject = Utility.getProjectPath(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, pathToProject);

        jarInformationSet = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, git);
    }

    private static void loadJFreeChartFx() {
        String jFreeChartGroupId = "org.jfree";
        String jFreeChartArtifactId = "org.jfree.chart.fx";
        String jFreeChartVersion = "2.0";
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion));
        jarInformationSet.add(new Artifact(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion));
    }

}
