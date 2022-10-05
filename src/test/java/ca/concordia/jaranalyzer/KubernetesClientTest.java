package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.FileUtils;
import ca.concordia.jaranalyzer.util.GitUtil;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * @author Diptopol
 * @since 9/16/2022 11:02 AM
 */
public class KubernetesClientTest {

    private static Logger logger = LoggerFactory.getLogger(KubernetesClientTest.class);

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    private static String projectUrl;
    private static String commitId;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "kubernetes-client";

        projectUrl = "https://github.com/fabric8io/kubernetes-client.git";
        commitId = "43af167c663031dac37f08dda36e35e512462071";

        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadJavaAndExternalJars(commitId, projectName, projectUrl);
    }

    @Test
    public void testNestedClassFieldFetch() {
        String filePath = "crd-generator/apt/src/main/java/io/fabric8/crd/generator/apt/CustomResourceAnnotationProcessor.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("messager.printMessage(Diagnostic.Kind.NOTE,\"Generating CRD \"")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("javax.annotation.processing.Messager" +
                                "::public abstract void printMessage(javax.tools.Diagnostic.Kind, java.lang.CharSequence)").equals(methodInfo.toString());

                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testFormalTypeParameterResolutionFromParameterizedArgument() {
        String filePath = "crd-generator/apt/src/main/java/io/fabric8/crd/generator/apt/CustomResourceAnnotationProcessor.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().equals("customResource.getAnnotation(Group.class).value()")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("io.fabric8.kubernetes.model.annotation.Group::public abstract java.lang.String value()").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testAssociatedJarsLoadWithDependencyJar() {
        String filePath = "httpclient-jetty/src/main/java/io/fabric8/kubernetes/client/jetty/JettyHttpResponse.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().equals("response.getHeaders().stream()")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("org.eclipse.jetty.http.HttpFields" +
                                "::public abstract java.util.stream.Stream stream()").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testFormalTypeParameterPopulation() {
        String filePath = "httpclient-jetty/src/main/java/io/fabric8/kubernetes/client/jetty/JettyHttpResponse.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("response.getHeaders().stream().reduce(")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("[ParameterizedTypeInfo{qualifiedClassName='java.util.HashMap', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}]}]}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.function.BiFunction', isParameterized=true," +
                                " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.util.HashMap', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}]}]}," +
                                " QualifiedTypeInfo{qualifiedClassName='org.eclipse.jetty.http.HttpField'}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.HashMap', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}]}]}]}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.function.BinaryOperator', isParameterized=true," +
                                " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName='java.util.HashMap', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}," +
                                " ParameterizedTypeInfo{qualifiedClassName='java.util.List', isParameterized=true," +
                                " typeArgumentList=[QualifiedTypeInfo{qualifiedClassName='java.lang.String'}]}]}]}]")
                                .equals(methodInfo.getArgumentTypeInfoList().toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testMethodInvocationInsideNestedLambdaExpression() {
        String filePath = "httpclient-jetty/src/main/java/io/fabric8/kubernetes/client/jetty/JettyHttpResponse.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("e.getValue()")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("org.eclipse.jetty.http.HttpField::public java.lang.String getValue()").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testFunctionalInterfaceTypeArgumentResolutionFromArg() {
        String filePath = "kubernetes-model-generator/kubernetes-model-core/src/main/java/io/fabric8/kubernetes/api/model/HasMetadata.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().endsWith(".findFirst()")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("java.util.stream.Stream::public abstract java.util.Optional findFirst()").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testFunctionalInterfaceTypeArgumentResolutionFromArg1() {
        String filePath = "crd-generator/api/src/main/java/io/fabric8/crd/generator/AbstractJsonSchema.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().endsWith(".orElse(property.getName())")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("java.util.Optional::public java.lang.String orElse(java.lang.String)").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testFunctionalInterfaceTypeArgumentResolutionFromArg2() {
        String filePath = "kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/cache/ProcessorStore.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().equals("items.stream().map(this::updateInternal)")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("ParameterizedTypeInfo{qualifiedClassName='java.util.stream.Stream'," +
                                " isParameterized=true," +
                                " typeArgumentList=[ParameterizedTypeInfo{qualifiedClassName=" +
                                "'io.fabric8.kubernetes.client.informers.impl.cache.ProcessorListener.Notification'," +
                                " isParameterized=true, typeArgumentList=[FormalTypeParameterInfo{typeParameter='T'," +
                                " baseTypeInfo=QualifiedTypeInfo{qualifiedClassName=" +
                                "'io.fabric8.kubernetes.api.model.HasMetadata'}}]}]}").equals(methodInfo.getReturnTypeInfo().toString());
                    }

                    return true;
                }
            });
        }
    }

    private String getFileContentFromRemote(String filePath, String projectUrl, String commitId) {
        try {
            GitHub gitHub = GitUtil.connectGithub();
            String repositoryName = GitUtil.extractRepositoryName(projectUrl);
            GHRepository ghRepository = gitHub.getRepository(repositoryName);

            GHContent ghContent = ghRepository.getFileContent(filePath, commitId);

            return FileUtils.getFileContent(ghContent.read());
        } catch (IOException e) {
            logger.error("Error occurred", e);
        }

        return null;
    }

}
