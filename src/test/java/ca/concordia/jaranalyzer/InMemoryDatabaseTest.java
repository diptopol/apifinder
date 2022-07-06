package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractor;
import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractorResolver;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple2;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Diptopol
 * @since 6/21/2022 12:21 PM
 */
public class InMemoryDatabaseTest {

    @Test
    public void loadKubernetesClientApi() {
        String projectName = "kubernetes-client-api";
        String projectUrl = "https://github.com/kubernetes-client/java.git";
        String commitId = "e99aad1c83d5044efa04cb91cb75680b21234045";

        Tuple2<String, Set<Artifact>> dependencyTuple = loadExternalJars(projectName, projectUrl, commitId);

        assert dependencyTuple._2().size() == 55;
    }

    @Test
    public void loadFabric8ioKubernetesClient() {
        String projectName = "kubernetes-client";
        String projectUrl = "https://github.com/fabric8io/kubernetes-client.git";
        String commitId = "54a0dcd9fa0303a10c7a2a595e7c26526d7006a0";

        Tuple2<String, Set<Artifact>> dependencyTuple = loadExternalJars(projectName, projectUrl, commitId);

        assert dependencyTuple._2().size() == 250;
    }

    @Test
    public void testJavaVersion() {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("54a0dcd9fa0303a10c7a2a595e7c26526d7006a0",
                "kubernetes-client",
                "https://github.com/fabric8io/kubernetes-client.git");

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();

        assert "11".equals(extractor.getJavaVersion());
    }

    @Test
    public void loadFabric8ioKubernetesClientForDifferentCommits() {
        String projectName = "kubernetes-client";
        String projectUrl = "https://github.com/fabric8io/kubernetes-client.git";
        String startCommitId = "54a0dcd9fa0303a10c7a2a595e7c26526d7006a0";
        //int numberOfCommitsToVisit = 100;
        int numberOfCommitsToVisit = 1;

        Path projectDirectory = Paths.get("testProjectDirectory").resolve(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, projectDirectory);
        Repository repository = git.getRepository();

        RevWalk walk = new RevWalk(repository);
        try {
            walk.markStart(walk.parseCommit(repository.resolve(startCommitId)));
            walk.setRevFilter(RevFilter.NO_MERGES);

            Iterator<RevCommit> commitIterator = walk.iterator();
            int numberOfVisit = 0;

            while (commitIterator.hasNext()) {
                if (numberOfVisit == numberOfCommitsToVisit) {
                    break;
                }

                RevCommit revCommit = commitIterator.next();
                String commitId = revCommit.getId().getName();

                loadExternalJars(projectName, projectUrl, commitId);
                numberOfVisit++;
            }

            walk.dispose();
            walk.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Tuple2<String, Set<Artifact>> loadExternalJars(String projectName, String projectUrl, String commitId) {
        Path pathToProject = Utility.getProjectPath(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, pathToProject);

        //TypeInferenceFluentAPI.resetCaching();

        return TypeInferenceFluentAPI.getInstance("JavaJars-kubernetes").loadExternalJars(commitId, projectName, git);
    }

    @Test
    public void testNearestTag() {
        String projectName = "kubernetes-client";
        String projectUrl = "https://github.com/fabric8io/kubernetes-client.git";
        String commitId = "1ce8e203a1785b5abbaf20738a37d5129962d9f6";

        Path projectDirectory = Paths.get("testProjectDirectory").resolve(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, projectDirectory);

        String nearestTagCommit = GitUtil.getNearestTagCommit(commitId, git);

        assert "54a0dcd9fa0303a10c7a2a595e7c26526d7006a0".equals(nearestTagCommit);
    }

}
