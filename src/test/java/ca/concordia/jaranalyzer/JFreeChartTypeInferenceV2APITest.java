package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.PropertyReader;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
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
public class JFreeChartTypeInferenceV2APITest {

    private static Set<Tuple3<String, String, String>> jarInformationSet;
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
        TypeInferenceFluentAPI.getInstance().loadJar(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion);
        jarInformationSet.add(new Tuple3<>(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion));
    }

    @Test
    public void testSuperConstructorMethodExtraction() {
        String filePath = "testProjectDirectory/jfreechart-1.0.19/jfreechart-1.0.19/source/org/jfree/chart/plot/CombinedRangeCategoryPlot.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        String javaVersion = PropertyReader.getProperty("java.version");

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
        String javaVersion = PropertyReader.getProperty("java.version");

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

}
