package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.JarInformation;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.artifactextraction.Artifact;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Diptopol
 * @since 12/27/2020 5:55 PM
 */
public class ExternalJarExtractionUtilityTest {

    @Test
    public void testGetJarArtifactInfoFromEffectivePOM() {
        Set<Artifact> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                        "RefactoringMinerIssueReproduction",
                        "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        assert jarArtifactInfoSet.size() == 1;

        Artifact jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarArtifactInfo.getGroupId())
                && "refactoring-miner".equals(jarArtifactInfo.getArtifactId())
                && "2.0.2".equals(jarArtifactInfo.getVersion());
    }

    @Test
    public void testGetJarInfo() {
        Set<Artifact> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                        "RefactoringMinerIssueReproduction",
                        "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        assert jarArtifactInfoSet.size() == 1;

        Artifact jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        JarInformation jarInformation = ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo);

        assert "com.github.tsantalis".equals(jarInformation.getGroupId())
                && "refactoring-miner".equals(jarInformation.getArtifactId())
                && "2.0.2".equals(jarInformation.getVersion());
    }

    @Test
    public void testGenerateEffectivePOMFromRepository() {
        String projectName = "RefactoringMinerIssueReproduction";
        Path projectDirectory = Path.of("testProjectDirectory").resolve(projectName);

        Repository repository = GitUtil.getRepository(projectName,
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git", projectDirectory);

        Set<Artifact> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c", projectName, repository);

        Artifact jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarArtifactInfo.getGroupId())
                && "refactoring-miner".equals(jarArtifactInfo.getArtifactId())
                && "2.0.2".equals(jarArtifactInfo.getVersion());
    }

}
