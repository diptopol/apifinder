package ca.concordia.apifinder;

import ca.concordia.apifinder.entity.MethodInfo;
import ca.concordia.apifinder.models.Artifact;
import ca.concordia.apifinder.util.FileUtils;
import ca.concordia.apifinder.util.GitUtil;
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
 * @since 3/2/2023 11:04 AM
 */
public class KubernetesClientJavaTest {

    private static Logger logger = LoggerFactory.getLogger(KubernetesClientJavaTest.class);

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    private static String projectUrl;
    private static String commitId;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "kubernetes-client-api";

        projectUrl = "https://github.com/kubernetes-client/java.git";
        commitId = "e99aad1c83d5044efa04cb91cb75680b21234045";

        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadJavaAndExternalJars(commitId, projectName, projectUrl);
    }


    @Test
    public void testPropagationOfTypeArgsThroughNonGenericSuperClass() {
        String filePath = "proto/src/main/java/io/kubernetes/client/proto/V1beta1Rbac.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("verbs_.set(index,value)")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("java.util.List" +
                                "::public abstract java.lang.String set(int, java.lang.String)").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testOrderOfDeferredMethodInfoList() {
        String filePath = "fluent/src/main/java/io/kubernetes/client/openapi/models/V1beta1FSGroupStrategyOptionsFluentImpl.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("build(ranges)")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("io.kubernetes.client.fluent.BaseFluent" +
                                "::public static java.util.List build(java.util.List)").equals(methodInfo.toString());

                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testDePrioritizingPrimitiveUnWrapping() {
        String filePath = "fluent/src/main/java/io/kubernetes/client/openapi/models/V1SecretProjectionFluentImpl.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("sb.append(optional)")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("java.lang.StringBuilder" +
                                "::public java.lang.StringBuilder append(java.lang.Object)").equals(methodInfo.toString());
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
