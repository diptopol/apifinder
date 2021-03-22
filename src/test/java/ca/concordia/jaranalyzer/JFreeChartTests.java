package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import io.vavr.Tuple3;
import org.eclipse.jgit.lib.Repository;
import org.junit.BeforeClass;
import org.junit.Test;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Diptopol
 * @since 12/28/2020 8:21 PM
 */
public class JFreeChartTests {

    private static Set<Tuple3<String, String, String>> jarInformationSet;
    private static String javaVersion;

    @BeforeClass
    public static void loadExternalLibrary() {
        javaVersion = getProperty("java.version");

        String projectName = "jfreechart-fx";
        Path projectDirectory = Path.of("testProjectDirectory").resolve(projectName);
        String projectUrl = "https://github.com/jfree/jfreechart-fx.git";
        String commitId = "35d53459e854a2bb39d6f012ce9b78ec8ab7f0f9";

        Repository repository = GitUtil.getRepository(projectName, projectUrl, projectDirectory);
        jarInformationSet = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, repository);
        loadPreviousJFreeChartJar();
    }

    @Test
    public void findMethodInSuperclassOfImport() {
        List<String> imports = Arrays.asList("java.lang.*",
                "org.jfree.chart.needle.*",
                "java.awt.Graphics2D",
                "java.awt.geom.GeneralPath", "java.awt.geom.Point2D",
                "java.awt.geom.Rectangle2D", "java.io.Serializable");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "getMinX", 0).getMethodList();


        assert "[java.awt.geom.RectangularShape::public double getMinX()]".equals(matches.toString());
    }

    @Test
    public void findMethodWithStaticImport() {
        List<String> imports = Arrays.asList("import static org.junit.jupiter.api.Assertions.assertEquals",
                "import static org.junit.jupiter.api.Assertions.assertTrue",
                "import static org.junit.jupiter.api.Assertions.assertFalse",
                "java.awt.BasicStroke",
                "java.awt.Color",
                "org.jfree.chart.TestUtils",
                "org.jfree.chart.util.PublicCloneable",
                "org.junit.jupiter.api.Test");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports,
                "assertTrue", 2).getMethodList();

        List<String> methodSignatureList = new ArrayList<>();
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(java.util.function.BooleanSupplier, java.lang.String)");
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(boolean, java.util.function.Supplier)");
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(java.util.function.BooleanSupplier, java.util.function.Supplier)");
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(boolean, java.lang.String)");

        assert matches.size() == methodSignatureList.size()
                && matches.stream().allMatch(match -> methodSignatureList.contains(match.toString()));
    }

    @Test
    public void findClassConstructorWithQualifiedName() {
        List<String> imports = Collections.singletonList("java.lang.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "java.util.ArrayList", 0).getMethodList();

        assert "[java.util.ArrayList::public void ArrayList()]".equals(matches.toString());
    }


    /**
     * Currently, private inner classes are not excluded. If it is essential to exclude private inner classes further
     * improvement will be needed.
     */
    @Test
    public void findClassConstructorWithNonQualifiedName() {
        List<String> imports = Arrays.asList("java.lang.*", "java.util.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "ArrayList", 1).getMethodList();

        List<String> methodSignatureList = new ArrayList<>();
        methodSignatureList.add("java.util.Arrays.ArrayList::void Arrays$ArrayList(java.lang.Object[])");
        methodSignatureList.add("java.util.ArrayList::public void ArrayList(int)");
        methodSignatureList.add("java.util.ArrayList::public void ArrayList(java.util.Collection)");

        assert matches.size() == methodSignatureList.size()
                && matches.stream().allMatch(match -> methodSignatureList.contains(match.toString()));
    }

    @Test
    public void findMethod() {
        List<String> imports = Arrays.asList("java.lang.*", "org.jfree.chart.block.*",
                "java.awt.Graphics2D", "java.awt.geom.Rectangle2D",
                "java.io.Serializable", "java.util.List",
                "org.jfree.ui.Size2D",
                "org.jfree.data.Range");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "constrain", 1).getMethodList();

        assert "[org.jfree.data.Range::public double constrain(double)]".equals(matches.toString());
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

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "getRowKey", 1).getMethodList();

        assert "[org.jfree.data.KeyedValues2D::public abstract java.lang.Comparable getRowKey(int)]".equals(matches.toString());
    }

    @Test
    public void findInnerClassConstructorWithoutQualifiedName() {
        List<String> imports = Arrays.asList("java.lang.*", "org.jfree.chart.axis.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(singleton(new Tuple3<>("org.jfree", "jfreechart", "1.0.19")),
                javaVersion, imports, "BaseTimelineSegmentRange", 2).getMethodList();

        assertEquals("[org.jfree.chart.axis.SegmentedTimeline.BaseTimelineSegmentRange::" +
                "public void SegmentedTimeline$BaseTimelineSegmentRange(long, long)]", matches.toString());
    }

    @Test
    public void findInnerClassConstructorWithOuterClassConcatenated() {
        List<String> imports = Arrays.asList("java.lang.*",
                "org.jfree.data.time.*", "java.util.Calendar", "java.util.TimeZone",
                "org.jfree.data.DomainInfo", "org.jfree.data.Range", "org.jfree.data.RangeInfo",
                "org.jfree.data.general.SeriesChangeEvent", "org.jfree.data.xy.AbstractIntervalXYDataset",
                "org.jfree.data.xy.IntervalXYDataset");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "DynamicTimeSeriesCollection.ValueSequence", 1)
                .getMethodList();

        assert ("[org.jfree.data.time.DynamicTimeSeriesCollection.ValueSequence::" +
                "public void DynamicTimeSeriesCollection$ValueSequence(int)]").equals(matches.toString());
    }

    @Test
    public void findInnerClassConstructorWithOuterClassConcatenatedAndOuterClassImport() {
        List<String> imports = Arrays.asList("import java.lang.*", "import java.awt.geom.Point2D");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "Point2D.Double", 2).getMethodList();

        assert "[java.awt.geom.Point2D.Double::public void Point2D$Double(double, double)]".equals(matches.toString());
    }

    @Test
    public void findInnerClassConstructorWithoutOuterClassConcatenated() {
        List<String> imports = Arrays.asList("java.lang.*",
                "org.jfree.data.time.*", "java.util.Calendar", "java.util.TimeZone",
                "org.jfree.data.DomainInfo", "org.jfree.data.Range", "org.jfree.data.RangeInfo",
                "org.jfree.data.general.SeriesChangeEvent", "org.jfree.data.xy.AbstractIntervalXYDataset",
                "org.jfree.data.xy.IntervalXYDataset");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "ValueSequence", 1).getMethodList();

        assert ("[org.jfree.data.time.DynamicTimeSeriesCollection.ValueSequence" +
                "::public void DynamicTimeSeriesCollection$ValueSequence(int)]").equals(matches.toString());
    }

    @Test
    public void findMethodInNestedType() {
        List<String> imports = Arrays.asList(
                "java.lang.*",
                "org.jfree.chart.axis.*",
                "java.io.Serializable",
                "java.util.ArrayList",
                "java.util.Calendar",
                "java.util.Collections",
                "java.util.Date",
                "java.util.GregorianCalendar",
                "java.util.Iterator",
                "java.util.List",
                "java.util.Locale",
                "java.util.SimpleTimeZone",
                "java.util.TimeZone");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(singleton(new Tuple3<>("org.jfree", "jfreechart", "1.0.19")), javaVersion, imports,
                "Segment", 1)
                .getMethodList();

        assertEquals("[org.jfree.chart.axis.SegmentedTimeline.Segment::" +
                "protected void SegmentedTimeline$Segment(long)]", matches.toString());
    }

    @Test
    public void findMethodInSuperclassOfImportLocatedInAnotherJar() {
        List<String> imports = Arrays.asList(
                "java.lang.*",
                "org.jfree.chart.demo.*",
                "java.awt.Color", "java.awt.Dimension",
                "java.awt.GradientPaint",
                "org.jfree.chart.ChartFactory",
                "org.jfree.chart.ChartPanel",
                "org.jfree.chart.JFreeChart",
                "org.jfree.chart.axis.CategoryAxis",
                "org.jfree.chart.axis.CategoryLabelPositions",
                "org.jfree.chart.axis.NumberAxis",
                "org.jfree.chart.plot.CategoryPlot",
                "org.jfree.chart.plot.PlotOrientation",
                "org.jfree.chart.renderer.category.BarRenderer",
                "org.jfree.data.category.CategoryDataset",
                "org.jfree.data.category.DefaultCategoryDataset",
                "org.jfree.ui.ApplicationFrame",
                "org.jfree.ui.RefineryUtilities");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(singleton(new Tuple3<>("org.jfree", "jfreechart", "1.0.19")),
                javaVersion, imports, "setPreferredSize", 1)
                .getMethodList();

        assertEquals("[javax.swing.JComponent::public void setPreferredSize(java.awt.Dimension)]", matches.toString());
    }

    @Test
    public void findConstructorWithDiamondSign() {
        List<String> imports = Arrays.asList("import java.lang.*",
                "import org.jfree.chart.axis.*",
                "import java.util.ArrayList",
                "import java.util.List",
                "import java.util.Objects",
                "import org.jfree.chart.ui.RectangleEdge");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet,
                javaVersion, imports, "ArrayList<>", 0).getMethodList();

        assertEquals("[java.util.ArrayList::public void ArrayList()]", matches.toString());
    }

    @Test
    public void findConstructorWithArgumentTypeArray() {
        List<String> imports = Arrays.asList("java.lang.*", "java.util.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "ArrayList", 1)
                .setArgumentTypeAsCriteria(0, "java.lang.Object[]")
                .getMethodList();

        assert "[java.util.Arrays.ArrayList::void Arrays$ArrayList(java.lang.Object[])]".equals(matches.toString());
    }

    @Test
    public void findConstructorWithArgumentTypeSubClass() {
        List<String> imports = Arrays.asList("java.lang.*", "java.util.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet, javaVersion, imports,
                "ArrayList", 1)
                .setInvokerTypeAsCriteria("ArrayList")
                .setArgumentTypeAsCriteria(0, "ArrayList")
                .getMethodList();

        assert "[java.util.ArrayList::public void ArrayList(java.util.Collection)]".equals(matches.toString());
    }


    /**
     *  There are 4 method signature of assertTrue with parameter size 2<br><br>
     *
     *  public static void assertTrue(java.util.function.BooleanSupplier, java.lang.String)<br>
     *  public static void assertTrue(boolean, java.util.function.Supplier)<br>
     *  public static void assertTrue(java.util.function.BooleanSupplier, java.util.function.Supplier)<br>
     *  public static void assertTrue(boolean, java.lang.String)<br><br>
     *
     *  `setArgumentTypeAsCriteria(argumentIndex, argumentType)` method will help API to search for method which has that
     *  `argumentType` class or any super class of that for `argumentIndex`.<br><br>
     *
     *  This method assume argumentIndex always will start from 0.<br><br>
     *
     *  This tests verifies whether API can filter out method list with partial argument types as parameter
     *  (e.g., assertTrue method has 4 method signature with parameter size 2. So passing argument index 1 and argument type
     *  `java.util.function.Supplier`, API will filter out all the methods which does not contain
     *  `java.util.function.Supplier` as second parameter)
     *
     */
    @Test
    public void findMethodWithPartialArgumentTypeWithIndex() {
        List<String> imports = Arrays.asList("import static org.junit.jupiter.api.Assertions.assertEquals",
                "import static org.junit.jupiter.api.Assertions.assertTrue",
                "import static org.junit.jupiter.api.Assertions.assertFalse",
                "java.awt.BasicStroke",
                "java.awt.Color",
                "org.jfree.chart.TestUtils",
                "org.jfree.chart.util.PublicCloneable",
                "org.junit.jupiter.api.Test");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "assertTrue", 2)
                .setInvokerTypeAsCriteria("org.junit.jupiter.api.Assertions")
                .setArgumentTypeAsCriteria(1, "java.util.function.Supplier")
                .getMethodList();

        List<String> methodSignatureList = new ArrayList<>();
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(boolean, java.util.function.Supplier)");
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(java.util.function.BooleanSupplier, java.util.function.Supplier)");

        assert matches.size() == methodSignatureList.size()
                && matches.stream().allMatch(match -> methodSignatureList.contains(match.toString()));
    }

    @Test
    public void findMethodWithStaticImportAndWithoutInvoker() {
        List<String> imports = Arrays.asList("import static org.junit.jupiter.api.Assertions.assertEquals",
                "import static org.junit.jupiter.api.Assertions.assertTrue",
                "import static org.junit.jupiter.api.Assertions.assertFalse",
                "java.awt.BasicStroke",
                "java.awt.Color",
                "org.jfree.chart.TestUtils",
                "org.jfree.chart.util.PublicCloneable",
                "org.junit.jupiter.api.Test");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "assertTrue", 2)
                .setArgumentTypeAsCriteria(1, "java.util.function.Supplier")
                .getMethodList();

        List<String> methodSignatureList = new ArrayList<>();
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(boolean, java.util.function.Supplier)");
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(java.util.function.BooleanSupplier, java.util.function.Supplier)");

        assert matches.size() == methodSignatureList.size()
                && matches.stream().allMatch(match -> methodSignatureList.contains(match.toString()));
    }

    @Test
    public void findMethodWithChildCallerClass() {
        List<String> imports = Arrays.asList("import java.lang.*",
                "import org.jfree.data.xy.*");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "getStartX", 2)
                .setInvokerTypeAsCriteria("org.jfree.data.xy.AbstractIntervalXYDataset")
                .setArgumentTypeAsCriteria(0, "int")
                .setArgumentTypeAsCriteria(1, "int")
                .getMethodList();

        assert "[org.jfree.data.xy.IntervalXYDataset::public abstract java.lang.Number getStartX(int, int)]".equals(matches.toString());
    }

    @Test
    public void findMethodForSuperOfCallerClass() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.urls.*", "import org.jfree.data.xy.XYZDataset");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "generateURL", 3)
                .setInvokerTypeAsCriteria("org.jfree.chart.urls.StandardXYZURLGenerator")
                .setSuperInvokerTypeAsCriteria(true)
                .setArgumentTypeAsCriteria(0, "XYZDataset")
                .setArgumentTypeAsCriteria(1, "int")
                .setArgumentTypeAsCriteria(2, "int")
                .getMethodList();

        assert "[org.jfree.chart.urls.StandardXYURLGenerator::public java.lang.String generateURL(org.jfree.data.xy.XYDataset, int, int)]".equals(matches.toString());
    }

    @Test
    public void findMethodForPrimitiveTypeWideningConversion() {
        List<String> imports = Arrays.asList("import java.util.*");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "Date", 1)
                .setArgumentTypeAsCriteria(0, "int")
                .getMethodList();

        assert "[java.util.Date::public void Date(long)]".equals(matches.toString());
    }

    @Test
    public void findMethodForPrimitiveTypeNarrowingConversion() {
        List<String> imports = Arrays.asList("import java.util.*");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet, javaVersion, imports, "Date", 1)
                .setArgumentTypeAsCriteria(0, "double")
                .getMethodList();

        assert "[java.util.Date::public void Date(long)]".equals(matches.toString());
    }

    private static void loadPreviousJFreeChartJar() {
        String groupId = "org.jfree";
        String artifactId = "jfreechart";
        String version = "1.0.19";

        TypeInferenceAPI.loadJar(groupId, artifactId, version);
    }

}
