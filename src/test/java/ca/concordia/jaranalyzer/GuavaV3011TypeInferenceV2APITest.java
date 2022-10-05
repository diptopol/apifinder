package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.GitUtil;
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
 * @since 3/23/2022 3:06 PM
 */
public class GuavaV3011TypeInferenceV2APITest {

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    /*
     * For running the test we have to check out to a specific commit. So after completion of all test we intend to
     * revert the change. So to do the revert we are storing the defaultBranchName here.
     */
    private static String defaultBranchName;

    private static Git git;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "guava";
        String projectUrl = "https://github.com/google/guava.git";
        String commitId = "43a53bc0328c7efd1da152f3b56b1fd241c88d4c";

        loadTestProjectDirectory(projectName, projectUrl, commitId);
        loadJavaPackageAndExternalJars(projectName, projectUrl, commitId);
    }

    @AfterClass
    public static void revertGitChange() {
        GitUtil.checkoutToCommit(git, defaultBranchName);
    }

    @Test
    public void testNestedInnerClassInstantiationFromInsideSiblingInnerClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ServiceManager.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new AwaitHealthGuard()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.util.concurrent.ServiceManager.ServiceManagerState.AwaitHealthGuard::void ServiceManager$ServiceManagerState$AwaitHealthGuard()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFieldVariableDeclarationBelowMethodInvocation() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/base/Throwables.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("invokeAccessibleNonThrowingMethod(getStackTraceElementMethod,jla,t,n)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.base.Throwables" +
                            "::private static java.lang.Object invokeAccessibleNonThrowingMethod(java.lang.reflect.Method," +
                            " java.lang.Object, java.lang.Object[])").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testResolutionOfCreationReference() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/CollectSpliterators.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation superConstructorInvocation) {
                if (superConstructorInvocation.toString().startsWith("super(prefix,from,function,FlatMapSpliteratorOfInt::new")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), superConstructorInvocation);

                    assert ("com.google.common.collect.CollectSpliterators.FlatMapSpliteratorOfPrimitive" +
                            "::void CollectSpliterators$FlatMapSpliteratorOfPrimitive(java.util.Spliterator.OfInt," +
                            " java.util.Spliterator, java.util.function.Function," +
                            " com.google.common.collect.CollectSpliterators.FlatMapSpliterator.Factory," +
                            " int, long)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeResolutionForFunctionInterfaceArguments() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/CollectCollectors.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Collector.of(ImmutableList::builder,ImmutableList.Builder::add")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.stream.Collector::public static java.util.stream.Collector of(java.util.function.Supplier," +
                            " java.util.function.BiConsumer, java.util.function.BinaryOperator," +
                            " java.util.function.Function, java.util.stream.Collector.Characteristics[])")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeParameterResolutionForExpressionReference() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ArrayTable.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("CollectSpliterators.indexed(size(),Spliterator.ORDERED,this::getEntry)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.CollectSpliterators" +
                            "::static java.util.Spliterator indexed(int, int," +
                            " java.util.function.IntFunction)").equals(methodInfo.toString());

                    assert ("[PrimitiveTypeInfo{qualifiedClassName='int'}, PrimitiveTypeInfo{qualifiedClassName='int'}," +
                            " ParameterizedTypeInfo{qualifiedClassName='java.util.function.IntFunction'," +
                            " isParameterized=true," +
                            " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.util.Map.Entry'," +
                            " isParameterized=true, typeArgumentList=[FormalTypeParameterInfo{typeParameter='K'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                            " FormalTypeParameterInfo{typeParameter='V'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}]}]")
                            .equals(methodInfo.getArgumentTypeInfoList().toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testResolutionOfLambdaExpression() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/AtomicLongMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("getAndUpdate(key,x")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.util.concurrent.AtomicLongMap::public long getAndUpdate(K," +
                            " java.util.function.LongUnaryOperator)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testCreationReferenceWithInnerClassConstructorWithByteCodeAddedArgument() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/CompactHashMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("CollectSpliterators.indexed(")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.CollectSpliterators" +
                            "::static java.util.Spliterator indexed(int, int, java.util.function.IntFunction)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testThisExpressionAsInvokerType() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/CompactHashMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("CompactHashMap.this.remove(keys[indexToRemove])")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.CompactHashMap::public V remove(java.lang.Object)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testContextSpecificOwningClassInfoCreation() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("makeBuilder(keySet.size())")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableMap.SerializedForm" +
                            "::com.google.common.collect.ImmutableMap.Builder makeBuilder(int)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testOwningClassInfoRelativeToPositionOfFieldDeclaration() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/MapMakerInternalMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Math.min(builder.getConcurrencyLevel(),MAX_SEGMENTS)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.Math::public static int min(int, int)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodInfoPrioritizationIfNoMethodArguments() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ServiceManager.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Lists.newArrayList()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.Lists" +
                            "::public static java.util.ArrayList newArrayList()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testDistanceBetweenObjectArrayAndObjectMethodArg() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ServiceManager.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("logger.log(Level.FINE,\"Service {0} has terminated. Previous state was: {1}\"")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.logging.Logger::" +
                            "public void log(java.util.logging.Level, java.lang.String, java.lang.Object[])")
                            .equals(methodInfo.toString());

                    assert !methodInfo.getClassInfo().isInternalDependency();
                }

                return true;
            }
        });
    }

    @Test
    public void testTypeArgumentPassingDuringMethodInvocation() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ServiceManager.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("ImmutableList.<Service>of(new NoOpService())")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableList" +
                            "::public static com.google.common.collect.ImmutableList of(com.google.common.util.concurrent.Service)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testUseOfClassFromSuperClassWithoutImport() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/RegularImmutableMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.getParent().toString().equals("candidateKey=entry.getKey()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableEntry::public final java.lang.Object getKey()").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testVarargsMethodArgumentSizeCheck() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ForwardingListenableFuture.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("Preconditions.checkNotNull(delegate)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.base.Preconditions::public static com.google.common.util.concurrent.ListenableFuture" +
                            " checkNotNull(com.google.common.util.concurrent.ListenableFuture)").equals(methodInfo.toString());
                }

                return false;
            }
        });
    }

    @Test
    public void testOuterMostClassAsOwningClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ServiceManager.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().equals("new FailedService(service)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.util.concurrent.ServiceManager.FailedService" +
                            "::void ServiceManager$FailedService(com.google.common.util.concurrent.Service)").equals(methodInfo.toString());

                    assert methodInfo.getClassInfo().isInternalDependency();
                }

                return true;
            }
        });
    }

    @Test
    public void testVarargsMethodArguments() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("fromEntries(Ordering.natural(),false,entries,entries.length)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableSortedMap::" +
                            "private static com.google.common.collect.ImmutableSortedMap fromEntries(java.util.Comparator," +
                            " boolean, java.util.Map.Entry[], int)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFieldOfSuperOfInnerInvokerClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("fromEntries(comparator,false,entries,size)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableSortedMap::" +
                            "private static com.google.common.collect.ImmutableSortedMap fromEntries(java.util.Comparator," +
                            " boolean, java.util.Map.Entry[], int)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingConcreteSuperClassAlbeitClassDistance() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("Ordering.natural().equals(comparator)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.Object::public boolean equals(java.lang.Object)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testSuperMethodInvocationWithFormalTypeMethodArguments() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperMethodInvocation superMethodInvocation) {
                if (superMethodInvocation.toString().startsWith("super.put(key,value)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), superMethodInvocation);

                    assert ("com.google.common.collect.ImmutableMap.Builder" +
                            "::public com.google.common.collect.ImmutableMap.Builder put(K, V)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFindingFormalTypeParameterFromNearestMethodArguments() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("ImmutableList.of(k1)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableList" +
                            "::public static com.google.common.collect.ImmutableList of(K)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodOfSuperClassOfInnerClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("asList().iterator()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableList" +
                            "::public com.google.common.collect.UnmodifiableIterator iterator()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testInnerClassInstanceCreationWithOuterClassAsArgument() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().equals("new SerializedForm<>(this)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.collect.ImmutableSortedMap.SerializedForm" +
                            "::void ImmutableSortedMap$SerializedForm(com.google.common.collect.ImmutableSortedMap)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testInnerClassSuperInstanceCreationWithOuterClassAsArgument() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation superConstructorInvocation) {
                if (superConstructorInvocation.toString().startsWith("super(sortedMap)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), superConstructorInvocation);

                    assert ("com.google.common.collect.ImmutableMap.SerializedForm" +
                            "::void ImmutableMap$SerializedForm(com.google.common.collect.ImmutableMap)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testMethodCallInsideAnonymousInnerClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("size()")
                        && methodInvocation.getParent().toString()
                        .equals("CollectSpliterators.indexed(size(),ImmutableSet.SPLITERATOR_CHARACTERISTICS,this::get)")) {

                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.ImmutableAsList::public int size()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizingPublicMethodOverPrivate() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().equals("new AssertionError(\"should never be called\")")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("java.lang.AssertionError::public void AssertionError(java.lang.Object)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeParameterResolutionForClassInstanceCreation() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new AbstractMap.SimpleImmutableEntry<>(")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("java.util.AbstractMap.SimpleImmutableEntry" +
                            "::public void AbstractMap$SimpleImmutableEntry(K, V)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testWildCardMethodArgument() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ImmutableSortedMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("comparator.compare(e1.getKey()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Comparator::public abstract int compare(K, K)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testNonDeferringAbstractMethodOfOwningClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ForwardingListenableFuture.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().equals("delegate()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.util.concurrent.ForwardingListenableFuture" +
                            "::protected abstract com.google.common.util.concurrent.ListenableFuture delegate()")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testTypeInferenceWithSubClassNonParameterizedAndParentParameterizedType() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ReverseNaturalOrdering.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("NaturalOrdering.INSTANCE.max(a,b,c,rest")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.Ordering::public E max(E, E, E, E[])").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeResolutionFromReturnStatement() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/graph/AbstractValueGraph.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("AbstractValueGraph.this.adjacentNodes(node)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.graph.ValueGraph::public abstract java.util.Set adjacentNodes(N)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizationOfNonVarargsOverVarargs() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/CollectPreconditions.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("checkState(canRemove,\"no calls to next() since the last call to remove()\")")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.base.Preconditions" +
                            "::public static void checkState(boolean, java.lang.Object)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testNameQualifiedNameResolution() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/base/Optional.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("fromNullable(javaUtilOptional.orElse(null))")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.base.Optional" +
                            "::public static com.google.common.base.Optional fromNullable(T)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFetchingFieldInstanceInsideAnonymousInnerClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/base/Optional.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("iterator.hasNext()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Iterator::public abstract boolean hasNext()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testResolvingFormalTypeOutsideMethodDeclaration() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/cache/RemovalListeners.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("listener.onRemoval(notification)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.cache.RemovalListener" +
                            "::public abstract void onRemoval(com.google.common.cache.RemovalNotification)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testSelectionOfElementFromIndexOfVarargs() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/FluentIterable.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("inputs[i].iterator()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.Iterable::public abstract java.util.Iterator iterator()")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrimitiveTypeWrapperConversionDuringFormalTypeParameterResolution() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/ServiceManager.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Maps.immutableEntry(service")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.Maps" +
                            "::public static java.util.Map.Entry immutableEntry(com.google.common.util.concurrent.Service, java.lang.Long)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeParameterInferFromVariableDeclarationStatement() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/util/concurrent/MoreExecutors.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("TrustedListenableFutureTask.create(command,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.util.concurrent.TrustedListenableFutureTask" +
                            "::static com.google.common.util.concurrent.TrustedListenableFutureTask create(java.lang.Runnable," +
                            " java.lang.Void)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeParameterInferenceFromReturnStatement() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/Collections2.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("endOfData()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.AbstractIterator::protected final java.util.List endOfData()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testClassInstantiationFiltering() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/Multimaps.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new UnmodifiableMultimap<>(delegate)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.collect.Multimaps.UnmodifiableMultimap" +
                            "::void Multimaps$UnmodifiableMultimap(com.google.common.collect.Multimap)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testCaseSensitiveFieldNameMatching() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/ArrayTable.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("Array.newInstance(")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.lang.reflect.Array::public static java.lang.Object newInstance(java.lang.Class, int[])" +
                            " throws java.lang.IllegalArgumentException, java.lang.NegativeArraySizeException")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testRemovalOfInferredArgumentOfInnerCLass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/AbstractSetMultimap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation methodInvocation) {
                if (methodInvocation.toString().startsWith("new WrappedSet(key,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.AbstractMapBasedMultimap.WrappedSet" +
                            "::void AbstractMapBasedMultimap$WrappedSet(K, java.util.Set)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizationOfPrimitiveToObjectOverTypeNarrowDown() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/hash/BloomFilter.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("checkArgument(fpp > 0.0")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.base.Preconditions" +
                            "::public static void checkArgument(boolean, java.lang.String, java.lang.Object)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeResolutionFromParameterizedTypeArgument() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/hash/BloomFilter.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("strategy.mightContain(object,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.hash.BloomFilter.Strategy" +
                            "::public abstract boolean mightContain(T, com.google.common.hash.Funnel," +
                            " int, com.google.common.hash.BloomFilterStrategies.LockFreeBitArray)").equals(methodInfo.toString());

                    assert ("[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}," +
                            " ParameterizedTypeInfo{qualifiedClassName='com.google.common.hash.Funnel'," +
                            " isParameterized=true, typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                            " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName='java.lang.Object'}}]}," +
                            " PrimitiveTypeInfo{qualifiedClassName='int'}," +
                            " QualifiedTypeInfo{qualifiedClassName='com.google.common.hash.BloomFilterStrategies.LockFreeBitArray'}]").equals(methodInfo.getArgumentTypeInfoList().toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testNestedFieldNameResolution() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/hash/BloomFilter.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("LockFreeBitArray.toPlainArray(bf.bits.data)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.hash.BloomFilterStrategies.LockFreeBitArray" +
                            "::public static long[] toPlainArray(java.util.concurrent.atomic.AtomicLongArray)")
                            .equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testFormalTypeParameterResolutionForSuperConstructorInvocation() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/base/FinalizableWeakReference.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation superConstructorInvocation) {
                if (superConstructorInvocation.toString().startsWith("super(referent,")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(),
                            superConstructorInvocation);

                    assert ("java.lang.ref.WeakReference" +
                            "::public void WeakReference(T, java.lang.ref.ReferenceQueue)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testCloneMethodForVarargs() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/math/Quantiles.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("dataset.clone()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(),
                            methodInvocation);

                    assert ("java.lang.Object::protected double[] clone()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testPrioritizationOfAbstractMethod() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/graph/AbstractValueGraph.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("AbstractValueGraph.this.nodes()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.graph.ValueGraph::public abstract java.util.Set nodes()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testTypeParameterExtensionMethodDeclaration() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/Ordering.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("min(a,b)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.collect.Ordering::public E min(E, E)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testNestedInnerClassConstructor() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/graph/DirectedGraphConnections.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new NodeConnection.Pred<>(thisNode)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.graph.DirectedGraphConnections.NodeConnection.Pred" +
                            "::void DirectedGraphConnections$NodeConnection$Pred(N)").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testCloneMethodReturnType() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/math/Quantiles.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new ScaleAndIndexes(scale,indexes.clone())")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.math.Quantiles.ScaleAndIndexes" +
                            "::private void Quantiles$ScaleAndIndexes(int, int[])").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testOrderingOfNonEnclosingInnerClass() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/StandardTable.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation classInstanceCreation) {
                if (classInstanceCreation.toString().startsWith("new EntrySet()")
                        && classInstanceCreation.getParent().getParent().getParent().toString().startsWith("@Override protected Set<Entry<R,Map<C,V>>> createEntrySet(){")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), classInstanceCreation);

                    assert ("com.google.common.collect.StandardTable.RowMap.EntrySet::void StandardTable$RowMap$EntrySet()").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testArrayCreation() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/reflect/TypeResolver.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("combined.toArray(new Type[0])")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("java.util.Set" +
                            "::public abstract java.lang.reflect.Type[] toArray(java.lang.reflect.Type[])").equals(methodInfo.toString());
                }

                return true;
            }
        });
    }

    @Test
    public void testOrderOfReturnStatement() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/Multimaps.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);
        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.toString().startsWith("checkNotNull(delegate)")
                        && methodInvocation.getParent().getParent().getParent().toString().contains("unmodifiableMultimap(ImmutableMultimap<K,V> delegate)")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                    assert ("com.google.common.base.Preconditions" +
                            "::public static com.google.common.collect.ImmutableMultimap checkNotNull(com.google.common.collect.ImmutableMultimap)").equals(methodInfo.toString());
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

    private static void loadJavaPackageAndExternalJars(String projectName, String projectUrl, String commitId) {
        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadJavaAndExternalJars(commitId, projectName, projectUrl);
    }

}
