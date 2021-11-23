package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.PropertyReader;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jgit.lib.Repository;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;

/**
 * @author Diptopol
 * @since 11/23/2021 12:11 PM
 */
public class JFreeChartV153TypeInferenceV2APITest {

    private static Set<Tuple3<String, String, String>> jarInformationSet;
    private static String javaVersion;

    @BeforeClass
    public static void loadExternalLibrary() {
        javaVersion = getProperty("java.version");

        String projectName = "jfreechart-1.5.3";
        Path projectDirectory = Paths.get("testProjectDirectory").resolve(projectName);
        String projectUrl = "https://github.com/jfree/jfreechart.git";
        // Also need to manually check-out the project to this commit.
        String commitId = "09e374f617c3a5c2e68d260f17a03f1e3f584121";

        Repository repository = GitUtil.getRepository(projectName, projectUrl, projectDirectory);
        jarInformationSet = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, repository);

        String jFreeChartGroupId = "org.jfree";
        String jFreeChartArtifactId = "jfreechart";
        String jFreeChartVersion = "1.5.3";
        TypeInferenceFluentAPI.getInstance().loadJar(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion);
        jarInformationSet.add(new Tuple3<>(jFreeChartGroupId, jFreeChartArtifactId, jFreeChartVersion));
    }

    @Test
    public void testMatchingObjectArrayAsArgumentAgainstLangObject() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/util/AbstractObjectList.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("System.arraycopy(this.objects,0,enlarged,0,this.objects.length)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert ("java.lang.System::public static void arraycopy(java.lang.Object, int, " +
                            "java.lang.Object, int, int)").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testMatchingPrimitiveNumericArgumentAgainstNumber() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xy/CategoryTableXYDataset.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("add(x,y,seriesName,true)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert ("org.jfree.data.xy.CategoryTableXYDataset::public void add(java.lang.Number," +
                            " java.lang.Number, java.lang.String, boolean)").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

}
