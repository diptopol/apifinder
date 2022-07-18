package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * @author Diptopol
 * @since 10/12/2021 10:06 PM
 */
public class JFreeChartV1019TypeInferenceV2APITest {

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    /*
     * For running the test we have to check out to a specific commit. So after completion of all test we intend to
     * revert the change. So to do the revert we are storing the defaultBranchName here.
     */
    private static String defaultBranchName;

    private static Git git;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "jfreechart-1.0.19";
        String projectUrl = "https://github.com/jfree/jfreechart.git";
        String commitId = "b3f5f21ba0fe32a8f7eccb6760a79df30628be3e";

        loadTestProjectDirectory(projectName, projectUrl, commitId);
        loadExternalJars(projectName, projectUrl, commitId);
    }

    @AfterClass
    public static void revertGitChange() {
        GitUtil.checkoutToCommit(git, defaultBranchName);
    }

    @Test
    public void testSuperConstructorMethodExtraction() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/plot/CombinedRangeCategoryPlot.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation superConstructorInvocation) {
                if (superConstructorInvocation.toString().startsWith("super(null,null,rangeAxis,null);")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), superConstructorInvocation);

                    assert ("org.jfree.chart.plot.CategoryPlot::public void CategoryPlot(org.jfree.data.category.CategoryDataset, " +
                            "org.jfree.chart.axis.CategoryAxis, org.jfree.chart.axis.ValueAxis, " +
                            "org.jfree.chart.renderer.category.CategoryItemRenderer)").equals(methodInfo.toString());
                };

                return false;
            }
        });
    }

    @Test
    public void testDefaultImport() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/data/Range.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new IllegalArgumentException(msg)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert "java.lang.IllegalArgumentException::public void IllegalArgumentException(java.lang.String)"
                            .equals(methodInfo.toString());
                };

                return false;
            }
        });
    }

    @Test
    public void testMultipleArgumentWithSameNameDifferentDistance() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/data/xy/DefaultHighLowDataset.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("createNumberArray(high)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.xy.DefaultHighLowDataset::" +
                            "public static java.lang.Number[] createNumberArray(double[])").equals(methodInfo.toString());
                };

                return false;
            }
        });
    }

    @Test
    public void testEvaluationGenericMethodArguments() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/plot/Marker.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("this.listenerList.getListeners")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("javax.swing.event.EventListenerList::public java.util.EventListener[] " +
                            "getListeners(java.lang.Class)").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    /*
     * The method signature is `<T:Ljava/lang/Object;>(Ljava/util/List<+Ljava/lang/Comparable<-TT;>;>;TT;)I`
     * For parameterized argument types we are setting isParameterized = true. The reason is that in method signature
     * (e.g., <-TT;>) the type argument is passed. But for formal type TT there is no type argument, hence
     * isParameterized = false.
     *
     * But if formal type parameter is like <K::Ljava/lang/Comparable<TK;>;> we are only considering formal type parameter
     * K with parameterized type Comparable with no type argument, hence isParameterized = false for simplicity.
     */
    @Test
    public void testComplexGenericTypeMethodArguments() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/data/DefaultKeyedValues2D.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Collections.binarySearch(this.rowKeys")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "java.util.Collections::public static int binarySearch(java.util.List, java.lang.Comparable)"
                            .equals(methodInfo.toString());

                    assert ("[ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                            " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable'," +
                            " isParameterized=true," +
                            " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable'," +
                            " isParameterized=false, typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]}]}," +
                            " ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable', isParameterized=false," +
                            " typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testNonFormalArgumentType() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/renderer/category/AbstractCategoryItemRenderer.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("this.itemLabelGeneratorMap.put(series")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Map::public abstract org.jfree.chart.labels.CategoryItemLabelGenerator " +
                            "put(java.lang.Integer, org.jfree.chart.labels.CategoryItemLabelGenerator)")
                            .equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testCurrentPackageImports() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/renderer/xy/DeviationRenderer.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new State(info)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("org.jfree.chart.renderer.xy.DeviationRenderer.State" +
                            "::public void DeviationRenderer$State(org.jfree.chart.plot.PlotRenderingInfo)")
                            .equals(methodInfo.toString());

                }

                return false;
            }
        });
    }

    @Test
    public void testMethodInvocationFromStaticBlock() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/axis/SegmentedTimeline.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation classInstanceCreation) {
                if (classInstanceCreation.toString().equals("cal.getTime().getTime()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("java.util.Date::public long getTime()").equals(methodInfo.toString());
                }

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

        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, git);
    }

}
