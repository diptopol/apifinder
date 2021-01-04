package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import org.eclipse.jgit.lib.Repository;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Diptopol
 * @since 12/28/2020 8:21 PM
 */
public class JFreeChartTests {

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "jfreechart-fx";
        Path projectDirectory = Path.of("testProjectDirectory").resolve(projectName);
        String projectUrl = "https://github.com/jfree/jfreechart-fx.git";
        String commitId = "35d53459e854a2bb39d6f012ce9b78ec8ab7f0f9";

        Repository repository = GitUtil.getRepository(projectName, projectUrl, projectDirectory);
        TypeInferenceAPI.loadExternalJars(commitId, projectName, repository);
    }

    @Test
    public void findMethodInSuperclassOfImport() {
        List<String> imports = Arrays.asList("java.lang.*",
                "org.jfree.chart.needle.*",
                "java.awt.Graphics2D",
                "java.awt.geom.GeneralPath", "java.awt.geom.Point2D",
                "java.awt.geom.Rectangle2D", "java.io.Serializable");

        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "getMinX", 0);

        assert "[public double getMinX()]".equals(matches.toString());
    }

    @Test
    public void findMethodInSuperclassOfImportLocatedInAnotherJar() {
        List<String> imports = Arrays.asList("import static org.junit.jupiter.api.Assertions.assertEquals",
                "import static org.junit.jupiter.api.Assertions.assertTrue",
                "import static org.junit.jupiter.api.Assertions.assertFalse",
                "java.awt.BasicStroke",
                "java.awt.Color",
                "org.jfree.chart.TestUtils",
                "org.jfree.chart.util.PublicCloneable",
                "org.junit.jupiter.api.Test");

        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "assertTrue", 2);

        List<String> methodSignatureList = new ArrayList<>();
        methodSignatureList.add("public static void assertTrue(java.util.function.BooleanSupplier, java.lang.String)");
        methodSignatureList.add("public static void assertTrue(boolean, java.util.function.Supplier)");
        methodSignatureList.add("public static void assertTrue(java.util.function.BooleanSupplier, java.util.function.Supplier)");
        methodSignatureList.add("public static void assertTrue(boolean, java.lang.String)");

        assert matches.size() == methodSignatureList.size()
                && matches.stream().allMatch(match -> methodSignatureList.contains(match.toString()));
    }

    @Test
    public void findClassConstructorWithQualifiedName() {
    	List<String> imports = Collections.singletonList("java.lang.*");
    	List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "java.util.ArrayList", 0);

    	assert "[public void ArrayList()]".equals(matches.toString());
    }

    @Test
    public void findClassConstructorWithNonQualifiedName() {
        List<String> imports = Arrays.asList("java.lang.*", "java.util.*");
        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "ArrayList", 1);

        assert "[public void ArrayList(java.util.Collection), public void ArrayList(int)]".equals(matches.toString());
    }

    @Test
    public void findMethod() {
        List<String> imports = Arrays.asList("java.lang.*", "org.jfree.chart.block.*",
                "java.awt.Graphics2D", "java.awt.geom.Rectangle2D",
                "java.io.Serializable", "java.util.List",
                "org.jfree.ui.Size2D",
                "org.jfree.data.Range");

        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "constrain", 1);

        assert "[public double constrain(double)]".equals(matches.toString());
    }

    @Test
    public void findMethodInSuperInterfaceOfImport() {
        List<String> imports = Arrays.asList("java.lang.*",
                "org.jfree.chart.renderer.category.*",
                "java.awt.Graphics2D",
                "java.awt.Paint",
                "java.awt.Shape",
                "java.awt.Stroke",
                "java.awt.geom.Line2D",
                "java.awt.geom.Rectangle2D",
                "java.io.IOException",
                "java.io.ObjectInputStream",
                "java.io.ObjectOutputStream",
                "java.io.Serializable",
                "java.util.List",
                "org.jfree.chart.LegendItem",
                "org.jfree.chart.axis.CategoryAxis",
                "org.jfree.chart.axis.ValueAxis",
                "org.jfree.chart.event.RendererChangeEvent",
                "org.jfree.chart.plot.CategoryPlot",
                "org.jfree.chart.plot.PlotOrientation",
                "org.jfree.data.category.CategoryDataset",
                "org.jfree.data.statistics.MultiValueCategoryDataset",
                "org.jfree.util.BooleanList",
                "org.jfree.util.BooleanUtilities",
                "org.jfree.util.ObjectUtilities",
                "org.jfree.util.PublicCloneable",
                "org.jfree.util.ShapeUtilities");

        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "getRowKey", 1);
        assert "[public abstract java.lang.Comparable getRowKey(int)]".equals(matches.toString());
    }


    @Test
    public void findInnerClassConstructorWithOuterClassConcatenated() {
        List<String> imports = Arrays.asList("java.lang.*",
                "org.jfree.data.time.*", "java.util.Calendar", "java.util.TimeZone",
                "org.jfree.data.DomainInfo", "org.jfree.data.Range", "org.jfree.data.RangeInfo",
                "org.jfree.data.general.SeriesChangeEvent", "org.jfree.data.xy.AbstractIntervalXYDataset",
                "org.jfree.data.xy.IntervalXYDataset");

        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(imports, "ValueSequence", 2);

        assert "[public void DynamicTimeSeriesCollection$ValueSequence(org.jfree.data.time.DynamicTimeSeriesCollection, int)]".equals(matches.toString());
    }

}
