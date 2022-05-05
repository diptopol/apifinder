package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;

/**
 * @author Diptopol
 * @since 3/23/2022 3:06 PM
 */
public class GuavaV3011TypeInferenceV2APITest {

    private static Set<Artifact> jarInformationSet;
    private static String javaVersion;

    /*
     * For running the test we have to check out to a specific commit. So after completion of all test we intend to
     * revert the change. So to do the revert we are storing the defaultBranchName here.
     */
    private static String defaultBranchName;

    private static Git git;

    @BeforeClass
    public static void loadExternalLibrary() {
        javaVersion = getProperty("java.version");

        String projectName = "guava";
        String projectUrl = "https://github.com/google/guava.git";
        String commitId = "43a53bc0328c7efd1da152f3b56b1fd241c88d4c";

        loadTestProjectDirectory(projectName, projectUrl, commitId);
        loadExternalJars(projectName, projectUrl, commitId);
        loadGuava();
    }

    @AfterClass
    public static void revertGitChange() {
        GitUtil.checkoutToCommit(git, defaultBranchName);
    }

    @Test
    public void testUseOfClassFromSuperClassWithoutImport() {
        String filePath = "testProjectDirectory/guava/guava/guava/src/com/google/common/collect/RegularImmutableMap.java";
        CompilationUnit compilationUnit = TestUtils.getCompilationUnitFromFile(filePath);

        compilationUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                if (methodInvocation.getParent().toString().equals("candidateKey=entry.getKey()")) {
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, classInstanceCreation);

                    assert ("com.google.common.util.concurrent.ServiceManager.FailedService" +
                            "::void ServiceManager$FailedService(com.google.common.util.concurrent.Service)").equals(methodInfo.toString());
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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, superMethodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, classInstanceCreation);

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
                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, superConstructorInvocation);

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

                    MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(jarInformationSet, javaVersion, methodInvocation);

                    assert ("com.google.common.collect.ImmutableAsList::public int size()").equals(methodInfo.toString());
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
        Path pathToProject = Utility.getProjectPath(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, pathToProject);

        jarInformationSet = TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, git);
    }

    private static void loadGuava() {
        String guavaGroupId = "com.google.guava";
        String guavaArtifactId = "guava";
        String guavaVersion = "30.1-jre";
        TypeInferenceFluentAPI.getInstance().loadJar(new Artifact(guavaGroupId, guavaArtifactId, guavaVersion));
        jarInformationSet.add(new Artifact(guavaGroupId, guavaArtifactId, guavaVersion));
    }

}
