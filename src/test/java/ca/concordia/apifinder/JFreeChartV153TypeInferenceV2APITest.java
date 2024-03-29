package ca.concordia.apifinder;

import ca.concordia.apifinder.entity.MethodInfo;
import ca.concordia.apifinder.models.Artifact;
import ca.concordia.apifinder.util.GitUtil;
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
 * @since 11/23/2021 12:11 PM
 */
public class JFreeChartV153TypeInferenceV2APITest {

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    /*
     * For running the test we have to check out to a specific commit. So after completion of all test we intend to
     * revert the change. So to do the revert we are storing the defaultBranchName here.
     */
    private static String defaultBranchName;

    private static Git git;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "jfreechart-1.5.3";
        String projectUrl = "https://github.com/jfree/jfreechart.git";
        String commitId = "09e374f617c3a5c2e68d260f17a03f1e3f584121";

        loadTestProjectDirectory(projectName, projectUrl, commitId);
        loadExternalJars(projectName, projectUrl, commitId);
    }

    @AfterClass
    public static void revertGitChange() {
        GitUtil.checkoutToCommit(git, defaultBranchName);
    }

    @Test
    public void testMatchingObjectArrayAsArgumentAgainstLangObject() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/util/AbstractObjectList.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("System.arraycopy(this.objects,0,enlarged,0,this.objects.length)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), constructorInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), superConstructorInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("this.seriesLabelLists.put(key,null)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("labelList.add(label)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("s.setLastPointGood(false)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Arrays.fill(this.objects,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("this.currentText.delete(0,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), constructorInvocation);

                    assert ("org.jfree.data.category.DefaultIntervalCategoryDataset::public void" +
                            " DefaultIntervalCategoryDataset(java.lang.Number[][], java.lang.Number[][])").equals(methodInfo.toString());

                    assert ("[ArrayTypeInfo{elementTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Number'}, dimension=2}," +
                            " ArrayTypeInfo{elementTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Number'}, dimension=2}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());

                    assert "VoidTypeInfo{}".equals(methodInfo.getReturnTypeInfo().toString());
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
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new DialShape(\"DialShape.CIRCLE\")")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

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
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("this.keys.size()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "java.util.List::public abstract int size()".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void tesMethodInvocationFortWildCardParameterizedInvokerClass() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/util/ExportUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("c1.newInstance(w")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.reflect.Constructor::public java.lang.Object newInstance(java.lang.Object[]) " +
                            "throws java.lang.InstantiationException, java.lang.IllegalAccessException," +
                            " java.lang.IllegalArgumentException, java.lang.reflect.InvocationTargetException").equals(methodInfo.toString());

                    assert ("[VarargTypeInfo{elementTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());
                    assert ("QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}")
                            .equals(methodInfo.getReturnTypeInfo().toString());
                }

                return true;
            }
        });
    }

    /*
     * Now we are trying to find type arguments with more information. Here the first argument is FlowDataset with type
     * argument K, where K is <K extends Comparable<K>>. So we can deduce that the base class for K would be a
     * Parameterized typeInfo with type argument K where K would be 'java.lang.Object'. Since in the argument,
     * type argument for K is not provided, we cannot infer the type of K, hence it would be 'java.lang.Object'.
     *
     * Also, for this method signature `<K::Ljava/lang/Comparable<TK;>;>(Lorg/jfree/data/flow/FlowDataset<TK;>;TK;I)D`
     * we are only fetching k as parameterized type of java.lang.Comparable with isParameterized false. We are ignoring
     * <TK;> in formal type parameter for now for simplicity.
     *
     * TODO: need to check when type argument is provided whether we can resolve the type accordingly.
     */
    @Test
    public void testClassNameReplacementForFormalParameterAsArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/flow/FlowPlot.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("FlowDatasetUtils.calculateInflow(this.dataset,source,stage)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.flow.FlowDatasetUtils::public static double"
                            + " calculateInflow(org.jfree.data.flow.FlowDataset, java.lang.Comparable, int)")
                            .equals(methodInfo.toString());

                    assert ("[ParameterizedTypeInfo{qualifiedClassName='org.jfree.data.flow.FlowDataset', isParameterized=true," +
                            " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable', isParameterized=false," +
                            " typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]}," +
                            " ParameterizedTypeInfo{qualifiedClassName='java.lang.Comparable', isParameterized=false," +
                            " typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                            " PrimitiveTypeInfo{qualifiedClassName='int'}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());

                }

                return false;
            }
        });
    }

    @Test
    public void testNonParameterizedArgumentForGenericMethodSignature() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/encoders/SunJPEGEncoderAdapter.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("ImageIO.getImageWritersByFormatName")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "javax.imageio.ImageIO::public static java.util.Iterator getImageWritersByFormatName(java.lang.String)".equals(methodInfo.toString());
                    assert "[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}]".equals(methodInfo.getArgumentTypeInfoList().toString());

                    assert ("ParameterizedTypeInfo{qualifiedClassName='java.util.Iterator'," +
                            " isParameterized=true," +
                            " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='javax.imageio.ImageWriter'}]}")
                            .equals(methodInfo.getReturnTypeInfo().toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeAsMethodArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/KeyedValues2DItemKey.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation constructorInvocation) {
                if (constructorInvocation.toString().contains("this.rowKey.compareTo(key.rowKey)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), constructorInvocation);

                    assert "java.lang.Comparable::public abstract int compareTo(R)".equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testFormalTypeAsMethodArgument2() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/DefaultKeyedValues.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("getIndex(key)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "org.jfree.data.DefaultKeyedValues::public int getIndex(K)".equals(methodInfo.toString());
                }

                return false;
            }
        });
    }
    @Test
    public void testFormalTypeAsMethodReturnType() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/general/DefaultPieDataset.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("source.getKey(i)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "org.jfree.data.KeyedValues::public abstract K getKey(int)".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodWithQualfiedArgumentMatchingFormalTypeArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xy/XYItemKey.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("ObjectUtils.hashCode(this.seriesKey)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "org.jfree.chart.util.ObjectUtils::public static int hashCode(java.lang.Object)".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeAsMethodArgumentFromMethodTypeParameter() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/flow/FlowDatasetUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("dataset.getFlow(stage,source,destination)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.flow.FlowDataset::public abstract java.lang.Number getFlow(int, K, K)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testResolutionOfFormalTypeArgumentOfNonOwningClass() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/labels/StandardFlowLabelGenerator.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("dataset.getFlow(key.getStage()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.flow.FlowDataset::public abstract java.lang.Number getFlow(int," +
                            " java.lang.Comparable, java.lang.Comparable)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testReduceArrayDimensionDuringProcessingOfArrayAccess() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/DataUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Arrays.equals(a[i]")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert "java.util.Arrays::public static boolean equals(double[], double[])".equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testArgumentTypeInferenceForVarargs() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xy/DefaultWindDataset.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("Arrays.asList(seriesNames)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Arrays::public static java.util.List asList(java.lang.String[])").equals(methodInfo.toString());

                    assert ("[VarargTypeInfo{elementTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.String'}}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrivateInnerClassInvoker() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/axis/DateAxis.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("date.getTime()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Date::public long getTime()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testArgumentMatchingDistanceForUnwrapping() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/PiePlot.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("Math.max(result,explode)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.Math::public static double max(double, double)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testAssignmentExpressionAsArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/general/DatasetUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("Double.isNaN(v=value.doubleValue())")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.Double::public static boolean isNaN(double)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }


    @Test
    public void testTypeArgumentResolutionFromAssignedVariableInClassInstanceCreation() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/flow/FlowDatasetUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().contains("new NodeKey<>(stage")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("org.jfree.data.flow.NodeKey::public void NodeKey(int, K)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testParameterizedTypeCastExpressionAsArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/flow/DefaultFlowDataset.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("clone.nodes.add((List<K>)CloneUtils.cloneList(list))")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.List::public abstract boolean add(java.util.List)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testAccessingVariableOfOuterMethodFromAnonymousInnerClassMethod() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/renderer/category/MinMaxCategoryRenderer.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("g2.setPaint(fillPaint)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.awt.Graphics2D::public abstract void setPaint(java.awt.Paint)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodInvocationForParameterizedClassType() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/ChartPanel.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("this.chartMouseListeners.getListeners(ChartMouseListener.class)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("javax.swing.event.EventListenerList::public org.jfree.chart.ChartMouseListener[]" +
                            " getListeners(java.lang.Class)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodInvocationFortFormalTypeParameterAsTypeArgumentInferredAsReturnType() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/PiePlot.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("dataset.getKey(section)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);
                    assert ("org.jfree.data.KeyedValues::public abstract K getKey(int)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeResolutionFromSuperClass() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/PiePlot3D.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("getSectionKey(categoryIndex)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.chart.plot.PiePlot::protected java.lang.Comparable getSectionKey(int)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testResolutionOfParameterizedTypeWithFormalTypeAsTypeArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/flow/DefaultFlowDataset.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("this.flows.put(new FlowKey<>(stage")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Map::public abstract java.lang.Number put(org.jfree.data.flow.FlowKey, java.lang.Number)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void tesMethodInvocationInsideAnonymousInnerClass() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/ui/StrokeChooserPanel.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("getSelector().transferFocus()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.awt.Component::public void transferFocus()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testQualifiedArrayArgumentTypeResolution() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/StandardChartTheme.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation methodInvocation) {
                if (methodInvocation.toString().contains("new DefaultDrawingSupplier(new Paint")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.chart.plot.DefaultDrawingSupplier::public void DefaultDrawingSupplier(java.awt.Paint[]," +
                            " java.awt.Paint[], java.awt.Stroke[], java.awt.Stroke[], java.awt.Shape[])").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMultipleWildTypeArgument() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/PiePlot.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("setRenderingHints")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.awt.Graphics2D::public abstract void setRenderingHints(java.util.Map)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodInvocationOfOwningClass() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/plot/PiePlot.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("getDataset().getValue(key)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.KeyedValues::public abstract java.lang.Number getValue(K)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingCloneOfOwnClassThanJavaLangObject() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/labels/MultipleXYSeriesLabelGenerator.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("pc.clone()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.chart.util.PublicCloneable" +
                            "::public abstract java.lang.Object clone() throws java.lang.CloneNotSupportedException")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingArgumentMatchingAmongMultipleAbstractMethods() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/DataUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("data.getValue(i)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.Values::public abstract java.lang.Number getValue(int)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingOwningClassMethod() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/axis/DateAxis.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("setRange(DEFAULT_DATE_RANGE,false,false)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.chart.axis.DateAxis" +
                            "::public void setRange(org.jfree.data.Range, boolean, boolean)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingClassDeclarationOrder() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/xy/CategoryTableXYDataset.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("addChangeListener(this.intervalDelegate)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.general.AbstractDataset" +
                            "::public void addChangeListener(org.jfree.data.general.DatasetChangeListener)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testClassOrderPreservationOfClassNameListInHierarchy() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/ui/ApplicationFrame.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("dispose()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.awt.Window::public void dispose()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testForReducedNumberOfArgumentsForVarargsMethod() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/util/ExportUtils.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("m2.invoke(page)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.reflect.Method::public java.lang.Object invoke(java.lang.Object, java.lang.Object[])" +
                            " throws java.lang.IllegalAccessException, java.lang.IllegalArgumentException," +
                            " java.lang.reflect.InvocationTargetException").equals(methodInfo.toString());

                    assert ("[QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}," +
                            " VarargTypeInfo{elementTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingAmongTypeWideningTypes() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/needle/PinNeedle.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("pointer.moveTo(midX")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.awt.geom.Path2D.Float::public synchronized final void moveTo(float, float)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testArgClassNameMatchingBeforeSuperClassQNameMatchingInVarargMethod() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/labels/StandardFlowLabelGenerator.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("formatter.format(this.template")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Formatter::public java.util.Formatter format(java.lang.String, java.lang.Object[])").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testInvokerTypeEqualsOwningClass() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/data/KeyToGroupMap.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().contains("KeyToGroupMap.clone(this.groups)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("org.jfree.data.KeyToGroupMap" +
                            "::private static java.util.Collection clone(java.util.Collection)" +
                            " throws java.lang.CloneNotSupportedException").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizationOrderOfNonAbstractMethodAboveArgumentDistanceMatching() {
        String filePath = "testProjectDirectory/jfreechart-1.5.3/jfreechart-1.5.3/src/main/java/org/jfree/chart/urls/StandardXYZURLGenerator.java";

        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperMethodInvocation superMethodInvocation) {
                if (superMethodInvocation.toString().contains("super.generateURL(dataset,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), superMethodInvocation);

                    assert ("org.jfree.chart.urls.StandardXYURLGenerator" +
                            "::public java.lang.String generateURL(org.jfree.data.xy.XYDataset, int, int)").equals(methodInfo.toString());
                }

                return true;
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
