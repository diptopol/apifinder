package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.PropertyReader;
import ca.concordia.jaranalyzer.util.artifactextraction.Artifact;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.Repository;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;

/**
 * @author Diptopol
 * @since 10/12/2021 10:06 PM
 */
public class JFreeChartV1019TypeInferenceV2APITest {

    private static Set<Artifact> jarInformationSet;
    private static String javaVersion;

    @BeforeClass
    public static void loadExternalLibrary() {
        javaVersion = getProperty("java.version");

        String projectName = "jfreechart-1.0.19";
        Path projectDirectory = Paths.get("testProjectDirectory").resolve(projectName);
        String projectUrl = "https://github.com/jfree/jfreechart.git";
        // Also need to manually check-out the project to this commit.
        String commitId = "b3f5f21ba0fe32a8f7eccb6760a79df30628be3e";

        Repository repository = GitUtil.getRepository(projectName, projectUrl, projectDirectory);
        jarInformationSet = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, repository);

        String jFreeChartGroupId = "org.jfree";
        String jFreeChartArtifactId = "jfreechart";
        String jFreeChartVersion = "1.0.19";
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion));
        jarInformationSet.add(new Artifact(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion));
    }

    @Test
    public void testSuperConstructorMethodExtraction() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/plot/CombinedRangeCategoryPlot.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation superConstructorInvocation) {
                if (superConstructorInvocation.toString().startsWith("super(null,null,rangeAxis,null);")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, superConstructorInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, classInstanceCreation);

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
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("createNumberArray(high)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("this.listenerList.getListeners")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Collections.binarySearch(this.rowKeys")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert "java.util.Collections::public static int binarySearch(java.util.List, java.lang.Comparable)"
                            .equals(methodInfo.toString());

                    assert ("[ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                            " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable'," +
                            " isParameterized=true, typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]}," +
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
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("this.itemLabelGeneratorMap.put(series")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, classInstanceCreation);

                    assert ("org.jfree.chart.renderer.xy.DeviationRenderer.State" +
                            "::public void DeviationRenderer$State(org.jfree.chart.plot.PlotRenderingInfo)")
                            .equals(methodInfo.toString());

                }

                return false;
            }
        });
    }

}
