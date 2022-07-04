package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple2;
import org.eclipse.jgit.api.Git;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.*;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;

/**
 * @author Diptopol
 * @since 12/28/2020 8:21 PM
 */
public class JFreeChartTests {

    private static Tuple2<String, Set<Artifact>> dependencyTuple;
    private static String javaVersion;

    @BeforeClass
    public static void loadExternalLibrary() {
        javaVersion = getProperty("java.version");

        String projectName = "jfreechart-fx";
        String projectUrl = "https://github.com/jfree/jfreechart-fx.git";
        String commitId = "35d53459e854a2bb39d6f012ce9b78ec8ab7f0f9";

        loadExternalJars(projectName, projectUrl, commitId);
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
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "getMinX", 0).getMethodList();


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
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
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
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
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
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
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
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
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

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
                "getRowKey", 1).getMethodList();

        assert "[org.jfree.data.KeyedValues2D::public abstract java.lang.Comparable getRowKey(int)]".equals(matches.toString());
    }

    @Test
    public void findInnerClassConstructorWithoutQualifiedName() {
        List<String> imports = Arrays.asList("java.lang.*", "org.jfree.chart.axis.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(singleton(new Artifact("org.jfree", "jfreechart", "1.0.19")),
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

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
                "DynamicTimeSeriesCollection.ValueSequence", 1)
                .getMethodList();

        assert ("[org.jfree.data.time.DynamicTimeSeriesCollection.ValueSequence::" +
                "public void DynamicTimeSeriesCollection$ValueSequence(int)]").equals(matches.toString());
    }

    @Test
    public void findInnerClassConstructorWithOuterClassConcatenatedAndOuterClassImport() {
        List<String> imports = Arrays.asList("import java.lang.*", "import java.awt.geom.Point2D");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
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

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
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
                .new Criteria(singleton(new Artifact("org.jfree", "jfreechart", "1.0.19")), javaVersion, imports,
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
                .new Criteria(singleton(new Artifact("org.jfree", "jfreechart", "1.0.19")),
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

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(),
                dependencyTuple._1(), imports, "ArrayList<>", 0).getMethodList();

        assertEquals("[java.util.ArrayList::public void ArrayList()]", matches.toString());
    }

    @Test
    public void findConstructorWithArgumentTypeArray() {
        List<String> imports = Arrays.asList("java.lang.*", "java.util.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
                "ArrayList", 1)
                .setArgumentType(0, "java.lang.Object[]")
                .getMethodList();

        assert "[java.util.Arrays.ArrayList::void Arrays$ArrayList(java.lang.Object[])]".equals(matches.toString());
    }

    @Test
    public void findMethodWhereInvokerClassArray() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.data.general.*", "import java.io.Serializable",
                "import java.util.Collections", "import java.util.List", "import org.jfree.chart.util.ParamChecks",
                "import org.jfree.data.DefaultKeyedValues", "import org.jfree.data.KeyedValues", "import org.jfree.data.UnknownKeyException",
                "import org.jfree.util.PublicCloneable", "import org.jfree.util.SortOrder");

        Set<Artifact> jarInformationSet1 = new HashSet<>();

        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceAPI.getAllMethods(jarInformationSet1, javaVersion, imports,
                "clone", 0, "int[]", false, null, null);

        assert "[java.lang.Object::protected int[] clone()]".equals(matches.toString());
    }

    @Test
    public void findConstructorWithArgumentTypeSubClass() {
        List<String> imports = Arrays.asList("java.lang.*", "java.util.*");
        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports,
                "ArrayList", 1)
                .setInvokerClassName("ArrayList")
                .setArgumentType(0, "ArrayList")
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
     *  `setArgumentType(argumentIndex, argumentType)` method will help API to search for method which has that
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
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "assertTrue", 2)
                .setInvokerClassName("org.junit.jupiter.api.Assertions")
                .setArgumentType(1, "java.util.function.Supplier")
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
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "assertTrue", 2)
                .setArgumentType(1, "java.util.function.Supplier")
                .getMethodList();

        List<String> methodSignatureList = new ArrayList<>();
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(boolean, java.util.function.Supplier)");
        methodSignatureList.add("org.junit.jupiter.api.Assertions::public static void assertTrue(java.util.function.BooleanSupplier, java.util.function.Supplier)");

        assert matches.size() == methodSignatureList.size()
                && matches.stream().allMatch(match -> methodSignatureList.contains(match.toString()));
    }

    @Test
    public void findMethodWithChildInvokerClass() {
        List<String> imports = Arrays.asList("import java.lang.*",
                "import org.jfree.data.xy.*");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "getStartX", 2)
                .setInvokerClassName("org.jfree.data.xy.AbstractIntervalXYDataset")
                .setArgumentType(0, "int")
                .setArgumentType(1, "int")
                .getMethodList();

        assert "[org.jfree.data.xy.IntervalXYDataset::public abstract java.lang.Number getStartX(int, int)]".equals(matches.toString());
    }

    @Test
    public void findMethodForSuperInvokerClass() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.urls.*", "import org.jfree.data.xy.XYZDataset");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "generateURL", 3)
                .setInvokerClassName("org.jfree.chart.urls.StandardXYZURLGenerator")
                .setSuperInvoker(true)
                .setArgumentType(0, "XYZDataset")
                .setArgumentType(1, "int")
                .setArgumentType(2, "int")
                .getMethodList();

        assert "[org.jfree.chart.urls.StandardXYURLGenerator::public java.lang.String generateURL(org.jfree.data.xy.XYDataset, int, int)]".equals(matches.toString());
    }

    @Test
    public void findMethodForPrimitiveTypeWideningConversion() {
        List<String> imports = Arrays.asList("import java.util.*");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "Date", 1)
                .setArgumentType(0, "int")
                .getMethodList();

        assert "[java.util.Date::public void Date(long)]".equals(matches.toString());
    }

    @Test
    public void findMethodForPrimitiveTypeNarrowingConversion() {
        List<String> imports = Arrays.asList("import java.util.*");

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(dependencyTuple._2(), dependencyTuple._1(), imports, "Date", 1)
                .setArgumentType(0, "double")
                .getMethodList();

        assert "[java.util.Date::public void Date(long)]".equals(matches.toString());
    }

    /**
     * The internal process of selection of methods is done in 4 steps. In each step, we first match method name and
     * parameter size initially. If we found any match we are doing further filtering like invokerType check, argumentTypes
     * check. If after filtration, we are unable to find single method instances we move to next step.
     *
     * This test is added to check whether the further filtration is done properly or not. This test search for a method
     * named 'setWidth(org.jfree.ui.Size2D)' with 1 argument. In first step, we can find method with same name and
     * argument size. But invokerType do not match. So the process will move on to next steps. In third step, we will
     * receive the appropriate method "setWidth(org.jfree.chart.block.AbstractBlock)".
     */
    @Test
    public void findMethodForMultipleOccurrencesInDifferentSteps() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.block.*",
                "import java.awt.Graphics2D", "import java.awt.geom.Rectangle2D", "import java.io.Serializable",
                "import org.jfree.ui.Size2D", "import org.jfree.util.PublicCloneable");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "setWidth", 1)
                .setInvokerClassName("org.jfree.chart.block.EmptyBlock")
                .setArgumentType(0, "double")
                .setSuperInvoker(true)
                .getMethodList();

        assert "[org.jfree.chart.block.AbstractBlock::public void setWidth(double)]".equals(matches.toString());
    }

    @Test
    public void findMethodForImportedArgumentTypes() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.plot.*",
                "import org.jfree.chart.renderer.xy.XYBlockRenderer", "import org.jfree.data.Range",
                "import org.jfree.data.contour.ContourDataset", "import org.jfree.data.contour.DefaultContourDataset");

        List<MethodInfo> methodInfoList = TypeInferenceFluentAPI.getInstance()
                .new Criteria(singleton(
                new Artifact("org.jfree", "jfreechart", "1.0.19")), javaVersion, imports, "getZValueRange",
                2).setInvokerClassName("DefaultContourDataset")
                .setArgumentType(0, "Range")
                .setArgumentType(1, "Range").getMethodList();

        assert "[org.jfree.data.contour.DefaultContourDataset::public org.jfree.data.Range getZValueRange(org.jfree.data.Range, org.jfree.data.Range)]"
                .equals(methodInfoList.toString());
    }

    @Test
    public void findMethodWhereMethodExistInSuperClass() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.data.general.*",
                "import java.io.Serializable", "import org.jfree.data.DefaultKeyedValue",
                "import org.jfree.data.KeyedValue", "import org.jfree.util.ObjectUtilities");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "getValue", 0)
                .setInvokerClassName("KeyedValue")
                .getMethodList();

        assert "[org.jfree.data.Value::public abstract java.lang.Number getValue()]".equals(matches.toString());
    }

    @Test
    public void findMethodWithMultipleSameTypeArgumentInstances() {
        List<String> imports = Arrays.asList("org.jfree.chart.axis.ExtendedCategoryAxis", "import java.lang.*",
                "import org.jfree.chart.axis.*", "import static org.junit.Assert.assertEquals", "import static org.junit.Assert.assertFalse",
                "import static org.junit.Assert.assertTrue", "import java.awt.Color", "import java.awt.Font",
                "import java.awt.GradientPaint", "import org.jfree.chart.TestUtilities", "import org.junit.Test");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "addSubLabel", 2)
                .setInvokerClassName("org.jfree.chart.axis.ExtendedCategoryAxis")
                .setArgumentType(0, "java.lang.String")
                .setArgumentType(1, "java.lang.String")
                .getMethodList();

        assert "[org.jfree.chart.axis.ExtendedCategoryAxis::public void addSubLabel(java.lang.Comparable, java.lang.String)]".equals(matches.toString());
    }

    @Test
    public void findMethodWithExactMatchingOfPrimitiveTypes() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.plot.dial.*",
                "import java.awt.BasicStroke", "import java.awt.Color", "import java.awt.Graphics2D",
                "import java.awt.Paint", "import java.awt.Shape", "import java.awt.Stroke", "import java.awt.geom.Arc2D",
                "import java.awt.geom.Area", "import java.awt.geom.GeneralPath", "import java.awt.geom.Point2D",
                "import java.awt.geom.Rectangle2D", "import java.io.IOException", "import java.io.ObjectInputStream",
                "import java.io.ObjectOutputStream", "import java.io.Serializable", "import org.jfree.chart.HashUtilities",
                "import org.jfree.chart.util.ParamChecks", "import org.jfree.io.SerialUtilities", "import org.jfree.util.PaintUtilities",
                "import org.jfree.util.PublicCloneable");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports,
                "Color", 3)
                .setInvokerClassName("Color")
                .setArgumentType(0, "int")
                .setArgumentType(1, "int")
                .setArgumentType(2, "int")
                .getMethodList();

        assert "[java.awt.Color::public void Color(int, int, int)]".equals(matches.toString());
    }

    @Test
    public void findMethodWithNonAbstractImplCheck() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.renderer.category.*",
                "import java.awt.Graphics2D", "import java.awt.geom.Rectangle2D", "import java.io.Serializable",
                "import org.jfree.chart.axis.CategoryAxis", "import org.jfree.chart.axis.ValueAxis",
                "import org.jfree.chart.entity.EntityCollection", "import org.jfree.chart.event.RendererChangeEvent",
                "import org.jfree.chart.labels.CategoryItemLabelGenerator", "import org.jfree.chart.labels.ItemLabelAnchor",
                "import org.jfree.chart.labels.ItemLabelPosition", "import org.jfree.chart.plot.CategoryPlot",
                "import org.jfree.chart.plot.PlotOrientation", "import org.jfree.data.DataUtilities", "import org.jfree.data.Range",
                "import org.jfree.data.category.CategoryDataset", "import org.jfree.data.general.DatasetUtilities", "import org.jfree.ui.RectangleEdge",
                "import org.jfree.ui.TextAnchor", "import org.jfree.util.PublicCloneable");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "setBasePositiveItemLabelPosition", 1)
                .setInvokerClassName("org.jfree.chart.renderer.category.StackedBarRenderer")
                .setArgumentType(0, "org.jfree.chart.labels.ItemLabelPosition")
                .getMethodList();

        assert "[org.jfree.chart.renderer.AbstractRenderer::public void setBasePositiveItemLabelPosition(org.jfree.chart.labels.ItemLabelPosition)]".equals(matches.toString());
    }

    @Test
    public void findMethodWhereMultipleClassExistsWithSameMethodSignatureInPackage() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.data.xy.*");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "equals", 1)
                .setInvokerClassName("org.jfree.data.xy.YIntervalSeries")
                .setArgumentType(0, "org.jfree.data.xy.YIntervalSeries")
                .getMethodList();

        assert "[org.jfree.data.ComparableObjectSeries::public boolean equals(java.lang.Object)]".equals(matches.toString());
    }

    @Test
    public void findMethodWhereArgumentIsInnerClass() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.*", "import java.awt.BasicStroke",
                "import java.awt.Color", "import java.awt.Font", "import java.awt.GradientPaint", "import java.awt.font.TextAttribute",
                "import java.awt.geom.Line2D", "import java.awt.geom.Rectangle2D", "import java.text.AttributedString", "import org.jfree.ui.GradientPaintTransformType",
                "import org.jfree.ui.StandardGradientPaintTransformer", "import org.junit.Test", "import static org.junit.Assert.assertEquals",
                "import static org.junit.Assert.assertFalse", "import static org.junit.Assert.assertSame", "import static org.junit.Assert.assertNotSame");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "addAttribute", 2)
                .setInvokerClassName("java.text.AttributedString")
                .setArgumentType(0,"java.awt.font.TextAttribute")
                .setArgumentType(1, "java.awt.Font")
                .getMethodList();

        assert "[java.text.AttributedString::public void addAttribute(java.text.AttributedCharacterIterator.Attribute, java.lang.Object)]".equals(matches.toString());
    }

    @Test
    public void findMethodWhereMultipleCandidateInDifferentParentHierarchy() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.renderer.xy.*", "import java.awt.Graphics2D",
                "import java.awt.Paint", "import java.awt.Stroke", "import java.awt.geom.Line2D", "import java.awt.geom.Rectangle2D", "import java.io.Serializable",
                "import org.jfree.chart.HashUtilities", "import org.jfree.chart.axis.ValueAxis", "import org.jfree.chart.entity.EntityCollection",
                "import org.jfree.chart.event.RendererChangeEvent", "import org.jfree.chart.labels.XYToolTipGenerator", "import org.jfree.chart.plot.CrosshairState",
                "import org.jfree.chart.plot.PlotOrientation", "import org.jfree.chart.plot.PlotRenderingInfo", "import org.jfree.chart.plot.XYPlot",
                "import org.jfree.chart.urls.XYURLGenerator", "import org.jfree.data.xy.XYDataset", "import org.jfree.ui.RectangleEdge", "import org.jfree.util.PublicCloneable");

        Set<Artifact> jarInformationSet1 = new HashSet<>();

        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "setBaseToolTipGenerator", 1)
                .setArgumentType(0, "org.jfree.chart.labels.XYToolTipGenerator")
                .getMethodList();

        assert "[org.jfree.chart.renderer.xy.AbstractXYItemRenderer::public void setBaseToolTipGenerator(org.jfree.chart.labels.XYToolTipGenerator)]".equals(matches.toString());
    }

    @Test
    public void findMethodWhereMultipleCandidatesWithPartialArgumentMatching() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.data.xy.*",
                "import org.jfree.data.ComparableObjectItem", "import org.jfree.data.ComparableObjectSeries",
                "import org.jfree.data.general.SeriesChangeEvent");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "add", 2)
                .setInvokerClassName("org.jfree.data.xy.YIntervalSeries")
                .setSuperInvoker(true)
                .setArgumentType(0, "org.jfree.data.xy.YIntervalDataItem")
                .setArgumentType(1, "boolean")
                .getMethodList();

        assert "[org.jfree.data.ComparableObjectSeries::protected void add(org.jfree.data.ComparableObjectItem, boolean)]".equals(matches.toString());
    }

    @Test
    public void findMethodWhereNullArgumentComparingWithArray() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.data.category.*",
                "import java.util.ArrayList", "import java.util.Arrays", "import java.util.Collections", "import java.util.List",
                "import java.util.ResourceBundle", "import org.jfree.chart.util.ParamChecks", "import org.jfree.chart.util.ResourceBundleWrapper",
                "import org.jfree.data.DataUtilities", "import org.jfree.data.UnknownKeyException", "import org.jfree.data.general.AbstractSeriesDataset");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "DefaultIntervalCategoryDataset", 4)
                .setInvokerClassName("org.jfree.data.category.DefaultIntervalCategoryDataset")
                .setArgumentType(0, "null")
                .setArgumentType(1, "null")
                .setArgumentType(2, "java.lang.Number[][]")
                .setArgumentType(3, "java.lang.Number[][]")
                .getMethodList();

        assertEquals("[org.jfree.data.category.DefaultIntervalCategoryDataset::" +
                "public void DefaultIntervalCategoryDataset(java.lang.Comparable[], " +
                "java.lang.Comparable[], java.lang.Number[][], java.lang.Number[][])]", matches.toString());
    }

    @Test
    public void findMethodWithClosestArgumentMatching() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.util.*", "import java.awt.Color", "import java.awt.Graphics2D",
                "import java.awt.image.BufferedImage", "import java.awt.image.DataBufferInt", "import java.io.Serializable", "import org.jfree.chart.HashUtilities");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "hashCode", 2)
                .setInvokerClassName("org.jfree.chart.HashUtilities")
                .setArgumentType(0, "int")
                .setArgumentType(1, "java.awt.Color")
                .getMethodList();

        assertEquals("[org.jfree.chart.HashUtilities::public static int hashCode(int, java.awt.Paint)]", matches.toString());
    }

    @Test
    public void findMethodWithPrimitiveTypeUnWrappingArgument() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.data.general.*", "import java.io.Serializable",
                "import java.util.Collections", "import java.util.List", "import org.jfree.chart.util.ParamChecks",
                "import org.jfree.data.DefaultKeyedValues", "import org.jfree.data.KeyedValues", "import org.jfree.data.UnknownKeyException",
                "import org.jfree.util.PublicCloneable", "import org.jfree.util.SortOrder");

        Set<Artifact> jarInformationSet1 = new HashSet<>();

        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "addValue", 2)
                .setArgumentType(0, "java.lang.Comparable")
                .setArgumentType(1, "java.lang.Number").getMethodList();

        assert "[org.jfree.data.DefaultKeyedValues::public void addValue(java.lang.Comparable, java.lang.Number)]".equals(matches.toString());
    }

    @Test
    public void findMethodWithVariableArguments() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.util.*",
                "import java.awt.Graphics2D", "import java.awt.Rectangle", "import java.awt.geom.Rectangle2D",
                "import java.awt.image.BufferedImage", "import java.io.BufferedOutputStream", "import java.io.File",
                "import java.io.FileNotFoundException", "import java.io.FileOutputStream", "import java.io.IOException",
                "import java.io.OutputStream", "import java.lang.reflect.Constructor", "import java.lang.reflect.InvocationTargetException",
                "import java.lang.reflect.Method", "import javax.imageio.ImageIO", "import org.jfree.ui.Drawable",
                "import org.jfree.chart.util.ExportUtils");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "getConstructor", 2)
                .setInvokerClassName("java.lang.Class")
                .setArgumentType(0, "java.lang.Class")
                .setArgumentType(1, "java.lang.Class")
                .getMethodList();

        assert ("[java.lang.Class::public java.lang.reflect.Constructor getConstructor(java.lang.Class[]) " +
                "throws java.lang.NoSuchMethodException, java.lang.SecurityException]").equals(matches.toString());
    }

    @Test
    public void findMethodWithSuperClassAsVariableArguments() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.util.*",
                "import java.awt.Graphics2D", "import java.awt.Rectangle", "import java.awt.geom.Rectangle2D",
                "import java.awt.image.BufferedImage", "import java.io.BufferedOutputStream", "import java.io.File",
                "import java.io.FileNotFoundException", "import java.io.FileOutputStream", "import java.io.IOException",
                "import java.io.OutputStream", "import java.lang.reflect.Constructor", "import java.lang.reflect.InvocationTargetException",
                "import java.lang.reflect.Method", "import javax.imageio.ImageIO", "import org.jfree.ui.Drawable",
                "import org.jfree.chart.util.ExportUtils");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance().new Criteria(jarInformationSet1, javaVersion, imports,
                "newInstance", 2)
                .setInvokerClassName("java.lang.reflect.Constructor")
                .setArgumentType(0, "int")
                .setArgumentType(1, "int")
                .getMethodList();

        assert ("[java.lang.reflect.Constructor::public java.lang.Object newInstance(java.lang.Object[])" +
                " throws java.lang.InstantiationException, java.lang.IllegalAccessException," +
                " java.lang.IllegalArgumentException, java.lang.reflect.InvocationTargetException]").equals(matches.toString());
    }

    @Test
    public void finMethodsWithDeferredMatching() {
        List<String> imports = Arrays.asList("import java.lang.*", "import java.awt.geom.Arc2D");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "Double", 1)
                .setArgumentType(0, "double")
                .getMethodList();

        assert "[java.lang.Double::public void Double(double)]".equals(matches.toString());
    }

    @Test
    public void findMethodWithDeferredNonAbstractSelection() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.renderer.category.*",
                "import java.awt.Graphics2D", "import java.awt.geom.Rectangle2D", "import java.io.Serializable",
                "import org.jfree.chart.axis.CategoryAxis", "import org.jfree.chart.axis.ValueAxis",
                "import org.jfree.chart.entity.EntityCollection", "import org.jfree.chart.event.RendererChangeEvent",
                "import org.jfree.chart.labels.CategoryItemLabelGenerator", "import org.jfree.chart.labels.ItemLabelAnchor",
                "import org.jfree.chart.labels.ItemLabelPosition", "import org.jfree.chart.plot.CategoryPlot",
                "import org.jfree.chart.plot.PlotOrientation", "import org.jfree.data.DataUtilities",
                "import org.jfree.data.Range", "import org.jfree.data.category.CategoryDataset",
                "import org.jfree.data.general.DatasetUtilities", "import org.jfree.ui.RectangleEdge",
                "import org.jfree.ui.TextAnchor", "import org.jfree.util.PublicCloneable");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "setBaseShape", 1)
                .setInvokerClassName("org.jfree.chart.renderer.xy.XYShapeRenderer")
                .setArgumentType(0, "java.awt.geom.Ellipse2D.Double")
                .getMethodList();

        assert "[org.jfree.chart.renderer.AbstractRenderer::public void setBaseShape(java.awt.Shape)]".equals(matches.toString());
    }

    @Test
    public void findMethodWithFinalKeyword() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.annotations.*",
                "import java.awt.BasicStroke", "import java.awt.Color", "import java.awt.Graphics2D",
                "import java.awt.Paint", "import java.awt.Stroke", "import java.awt.geom.GeneralPath",
                "import java.awt.geom.Rectangle2D", "import java.io.IOException", "import java.io.ObjectInputStream",
                "import java.io.ObjectOutputStream", "import java.io.Serializable", "import java.util.Arrays",
                "import org.jfree.chart.HashUtilities", "import org.jfree.chart.axis.ValueAxis",
                "import org.jfree.chart.plot.Plot", "import org.jfree.chart.plot.PlotOrientation",
                "import org.jfree.chart.plot.PlotRenderingInfo", "import org.jfree.chart.plot.XYPlot",
                "import org.jfree.chart.util.ParamChecks", "import org.jfree.io.SerialUtilities",
                "import org.jfree.ui.RectangleEdge", "import org.jfree.util.ObjectUtilities",
                "import org.jfree.util.PaintUtilities", "import org.jfree.util.PublicCloneable",
                "import org.jfree.chart.annotations.XYPolygonAnnotation");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "moveTo", 2)
                .setInvokerClassName("java.awt.geom.GeneralPath")
                .setArgumentType(0, "float")
                .setArgumentType(1, "float")
                .getMethodList();

        assert "[java.awt.geom.Path2D.Float::public synchronized final void moveTo(float, float)]".equals(matches.toString());
    }

    @Test
    public void testInnerClassConstructor() {
        List<String> imports = Arrays.asList("import java.lang.*", "import org.jfree.chart.util.*",
                "import java.awt.geom.Line2D", "import java.awt.geom.Rectangle2D", "import org.jfree.chart.util.LineUtilities");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "Line2D.Double", 4)
                .setArgumentType(0, "double")
                .setArgumentType(1, "double")
                .setArgumentType(1, "double")
                .setArgumentType(1, "double")
                .getMethodList();

        assert "[java.awt.geom.Line2D.Double::public void Line2D$Double(double, double, double, double)]".equals(matches.toString());
    }


    @Test
    public void testObjectArrayDimensionMatch() {
        List<String> imports = Arrays.asList("import java.io.Serializable", "import java.util.Arrays",
                "import java.util.Collections", "import java.util.Date", "import java.util.List",
                "import org.jfree.chart.util.ParamChecks", "import org.jfree.util.PublicCloneable");

        Set<Artifact> jarInformationSet1 = new HashSet<>();
        jarInformationSet1.add(new Artifact("junit", "junit", "4.11"));
        jarInformationSet1.add(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        jarInformationSet1.add(new Artifact("org.jfree", "jcommon", "1.0.23"));
        jarInformationSet1.add(new Artifact("javax.servlet", "servlet-api", "2.5"));

        List<MethodInfo> matches = TypeInferenceFluentAPI.getInstance()
                .new Criteria(jarInformationSet1, javaVersion, imports, "seriesNameListFromDataArray", 1)
                .setInvokerClassName("org.jfree.data.xy.DefaultWindDataset")
                .setArgumentType(0, "java.lang.Object[][][]")
                .getMethodList();

        assert ("[org.jfree.data.xy.DefaultWindDataset::public static java.util.List" +
                " seriesNameListFromDataArray(java.lang.Object[][])]").equals(matches.toString());
    }

    private static void loadExternalJars(String projectName, String projectUrl, String commitId) {
        Path pathToProject = Utility.getProjectPath(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, pathToProject);

        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, git);
    }

    private static void loadPreviousJFreeChartJar() {
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact("org.jfree", "jfreechart", "1.0.19"));
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact("org.jfree", "jcommon", "1.0.23"));
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact("junit", "junit", "4.11"));
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact("javax.servlet", "servlet-api", "2.5"));
    }

}
