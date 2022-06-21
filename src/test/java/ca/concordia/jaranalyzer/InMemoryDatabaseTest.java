package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.nio.file.Path;
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

        Set<Artifact> jarInformationSet = loadExternalJars(projectName, projectUrl, commitId);

        assert jarInformationSet.size() == 40;
    }

    @Test
    public void loadFabric8ioKubernetesClient() {
        String projectName = "kubernetes-client";
        String projectUrl = "https://github.com/fabric8io/kubernetes-client.git";
        String commitId = "54a0dcd9fa0303a10c7a2a595e7c26526d7006a0";

        Set<Artifact> jarInformationSet = loadExternalJars(projectName, projectUrl, commitId);

        assert jarInformationSet.size() == 104;
    }

    private static Set<Artifact> loadExternalJars(String projectName, String projectUrl, String commitId) {
        Path pathToProject = Utility.getProjectPath(projectName);
        Git git = GitUtil.openRepository(projectName, projectUrl, pathToProject);

        return TypeInferenceFluentAPI.getInstance().loadExternalJars(commitId, projectName, git);
    }

}
