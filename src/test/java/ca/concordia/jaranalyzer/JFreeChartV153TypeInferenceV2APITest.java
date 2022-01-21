package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.PropertyReader;
import io.vavr.Tuple3;
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

    @Test
    public void testMatchingUnionTypeMultiCatchExceptionBlockArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xml/DatasetReader.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new RuntimeException(e)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, classInstanceCreation);

                    assert "java.lang.RuntimeException::public void RuntimeException(java.lang.Throwable)"
                            .equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testMatchingPrioritizationOfWrappedPrimitiveArguments() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/statistics/BoxAndWhiskerItem.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ConstructorInvocation constructorInvocation) {
                if (constructorInvocation.toString().startsWith("this(Double.valueOf(mean),Double.valueOf(median),Double.valueOf(q1)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert ("org.jfree.data.statistics.BoxAndWhiskerItem::public void BoxAndWhiskerItem(java.lang.Number, " +
                            "java.lang.Number, java.lang.Number, java.lang.Number, java.lang.Number, java.lang.Number, " +
                            "java.lang.Number, java.lang.Number, java.util.List)").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testMatchingPrimitiveTypeWithComparable() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xy/XYIntervalDataItem.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation superConstructorInvocation) {
                if (superConstructorInvocation.toString().startsWith("super(x,new XYInterval(xLow,xHigh")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, superConstructorInvocation);

                    assert ("org.jfree.data.ComparableObjectItem::public void " +
                            "ComparableObjectItem(java.lang.Comparable, java.lang.Object)").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testInnerClassAsInvokerType() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/time/DynamicTimeSeriesCollection.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("this.valueHistory[s].getData(this.oldestAt)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert "org.jfree.data.time.DynamicTimeSeriesCollection.ValueSequence::public float getData(int)".equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testTypeExtractionFromNonParameterizedFieldAccess() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/labels/MultipleXYSeriesLabelGenerator.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("this.seriesLabelLists.put(key,null)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert "java.util.Map::public abstract java.lang.Object put(java.lang.Object, java.lang.Object)"
                            .equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testTypeExtractionFromNonParameterizedLocalVariable() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/labels/MultipleXYSeriesLabelGenerator.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        String javaVersion = PropertyReader.getProperty("java.version");

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("labelList.add(label)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert "java.util.List::public abstract boolean add(java.lang.Object)".equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testFormalTypeResolutionForRawMethod() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/util/ResourceBundleWrapper.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("urlsNoBase.toArray(new URL[0])")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert "java.util.List::public abstract java.lang.Object[] toArray(java.lang.Object[])"
                            .equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testMatchingPrioritizationOfCurrentClassHierarchyImport() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/renderer/xy/DeviationStepRenderer.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation constructorInvocation) {
                if (constructorInvocation.toString().startsWith("s.setLastPointGood(false)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert "org.jfree.chart.renderer.xy.XYLineAndShapeRenderer.State::public void setLastPointGood(boolean)"
                            .equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testPopulatingArrayDimensionDuringFieldAccess() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/util/AbstractObjectList.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation constructorInvocation) {
                if (constructorInvocation.toString().startsWith("Arrays.fill(this.objects,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert "java.util.Arrays::public static void fill(java.lang.Object[], java.lang.Object)".equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    /*
     * Java allows to override method with a return type that is a subtype of the return type of the overridden
     * method (covariant return type). But to facilitate that javac create an extra method (bridge method)
     * with return type of the overridden method. We need to exclude the bridge method from the result.
     *
     * @see <a href="https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html">Bridge Method</a>
     */
    @Test
    public void testExclusionOfBridgeMethod() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xml/ValueHandler.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation constructorInvocation) {
                if (constructorInvocation.toString().contains("this.currentText.delete(0,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert "java.lang.StringBuffer::public synchronized java.lang.StringBuffer delete(int, int)"
                            .equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testArgumentAsMethodInvocationWithArrayReturnType() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/category/DefaultIntervalCategoryDataset.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ConstructorInvocation constructorInvocation) {
                if (constructorInvocation.toString().contains("this(DataUtils.createNumberArray2D(starts)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert ("org.jfree.data.category.DefaultIntervalCategoryDataset::public void" +
                            " DefaultIntervalCategoryDataset(java.lang.Number[][], java.lang.Number[][])").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testClassInstanceCreationOutsideMethodDeclaration() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/DialShape.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation constructorInvocation) {
                if (constructorInvocation.toString().startsWith("new DialShape(\"DialShape.CIRCLE\")")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert "org.jfree.chart.plot.DialShape::private void DialShape(java.lang.String)".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodInvocationFromEnumTypeDeclaration() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/ui/RectangleAnchor.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Args.nullNotPermitted(rectangle,\"rectangle\")")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert "org.jfree.chart.util.Args::public static void nullNotPermitted(java.lang.Object, java.lang.String)".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testForParameterizedTypeOfArray() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/DefaultKeyedValues.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation constructorInvocation) {
                if (constructorInvocation.toString().startsWith("this.keys.size()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, constructorInvocation);

                    assert "java.util.List::public abstract int size()".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

}
