package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.util.PropertyReader;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Diptopol
 * @since 9/25/2021 12:59 PM
 */
public class TypeInferenceV2APITest {

    @BeforeClass
    public static void loadExternalLibrary() {
        loadPreviousJFreeChartJar();
    }

    @Test
    public void testMethodExtraction() {
        String filePath = "testProjectDirectory/jfreechart-fx/jfreechart-fx/src/main/java/org/jfree/chart/fx/ChartCanvas.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted(listener,\"listener\")")) {
                    Set<Tuple3<String, String, String>> jarInformationSet1 = new HashSet<>();
                    jarInformationSet1.add(new Tuple3<>("org.jfree", "jfreechart", "1.5.1"));
                    jarInformationSet1.add(new Tuple3<>("org.jfree", "org.jfree.chart.fx", "2.0"));

                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet1, javaVersion, methodInvocation);

                    assert "org.jfree.chart.util.Args::public static void nullNotPermitted(java.lang.Object, java.lang.String)"
                            .equals(methodInfo.toString());
                };

                return false;
            }
        });
    }

    private static void loadPreviousJFreeChartJar() {
        TypeInferenceFluentAPI.getInstance().loadJar("org.jfree", "jfreechart", "1.5.1");
        TypeInferenceFluentAPI.getInstance().loadJar("org.jfree", "org.jfree.chart.fx", "2.0");
    }
}
