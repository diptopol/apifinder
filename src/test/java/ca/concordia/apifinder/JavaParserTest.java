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
 * @since 2/11/2023 11:17 PM
 */
public class JavaParserTest {

    private static Logger logger = LoggerFactory.getLogger(JavaParserTest.class);

    private static Tuple2<String, Set<Artifact>> dependencyTuple;

    private static String projectUrl;
    private static String commitId;

    @BeforeClass
    public static void loadExternalLibrary() {
        String projectName = "javaparser";

        projectUrl = "https://github.com/javaparser/javaparser.git";
        commitId = "29f535765b9e83a2d39aa6df71d73cfff790d651";

        dependencyTuple = TypeInferenceFluentAPI.getInstance().loadJavaAndExternalJars(commitId, projectName, projectUrl);
    }

    @Test
    public void testNonConstructorFilter() {
        String filePath = "javaparser-core/src/main/java/com/github/javaparser/Range.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().startsWith("range(begin.withLine(beginLine)")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("com.github.javaparser.Range::public static com.github.javaparser.Range " +
                                "range(com.github.javaparser.Position, com.github.javaparser.Position)").equals(methodInfo.toString());
                    }

                    return true;
                }
            });
        }
    }

    @Test
    public void testPrioritizingNonDeferredMethodBasedOnInvokerDistance() {
        String filePath = "javaparser-core/src/main/java/com/github/javaparser/ast/visitor/ObjectIdentityHashCodeVisitor.java";
        String sourceContent = getFileContentFromRemote(filePath, projectUrl, commitId);

        if (Objects.nonNull(sourceContent)) {
            CompilationUnit compilationUnit = TestUtils.getCompilationUnit(sourceContent);

            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation methodInvocation) {
                    if (methodInvocation.toString().equals("n.hashCode()")
                            && methodInvocation.getParent().getParent().getParent().toString()
                            .startsWith("public Integer visit(final AnnotationDeclaration n")) {
                        MethodInfo methodInfo = TypeInferenceV2API.getMethodInfo(dependencyTuple._2(), dependencyTuple._1(), methodInvocation);

                        assert ("com.github.javaparser.ast.Node::public final int hashCode()").equals(methodInfo.toString());
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
