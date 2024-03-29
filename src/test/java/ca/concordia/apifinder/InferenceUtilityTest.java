package ca.concordia.apifinder;

import ca.concordia.apifinder.models.Artifact;
import ca.concordia.apifinder.models.VariableDeclarationDto;
import ca.concordia.apifinder.models.typeInfo.TypeInfo;
import ca.concordia.apifinder.service.ClassInfoService;
import ca.concordia.apifinder.service.JarInfoService;
import ca.concordia.apifinder.util.AuditInfo;
import ca.concordia.apifinder.util.GitUtil;
import ca.concordia.apifinder.util.InferenceUtility;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtilityTest {

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    private static JarInfoService jarInfoService;
    private static ClassInfoService classInfoService;

    /*
     * For running the test we have to check out to a specific commit. So after completion of all test we intend to
     * revert the change. So to do the revert we are storing the defaultBranchName here.
     */
    private static String defaultBranchName;

    private static Git git;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "jfreechart-fx";
        String projectUrl = "https://github.com/jfree/jfreechart-fx.git";
        String commitId = "35d53459e854a2bb39d6f012ce9b78ec8ab7f0f9";

        jarInfoService = new JarInfoService();
        classInfoService = new ClassInfoService();

        loadTestProjectDirectory(projectName, projectUrl, commitId);
        loadExternalJars(projectName, projectUrl, commitId);
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

                    AuditInfo auditInfo = new AuditInfo();

                    Set<VariableDeclarationDto> fieldVariableDeclarationDtoList
                            = InferenceUtility.getFieldVariableDeclarationDtoList(dependencyTuple._2(), dependencyTuple._1(),
                            importStatementList, methodInvocation, new HashMap<>(), jarInfoService, classInfoService, auditInfo);

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

                    AuditInfo auditInfo = new AuditInfo();

                    Map<String, Set<VariableDeclarationDto>> variableNameMap =
                            InferenceUtility.getVariableNameMap(dependencyTuple._2(), dependencyTuple._1(),
                                    importStatementList, methodInvocation, jarInfoService, classInfoService, auditInfo);

                    List<Expression> argumentList = methodInvocation.arguments();

                    List<TypeInfo> argumentTypeInfoList = InferenceUtility.getArgumentTypeInfoList(dependencyTuple._2(),
                            dependencyTuple._1(), importStatementList, variableNameMap, argumentList, null, auditInfo);

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
        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadJavaAndExternalJars(commitId, projectName, projectUrl);
    }

}
