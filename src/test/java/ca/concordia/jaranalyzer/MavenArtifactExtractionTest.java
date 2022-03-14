package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.JarInformation;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.artifactextraction.Artifact;
import ca.concordia.jaranalyzer.util.artifactextraction.MavenArtifactExtraction;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Diptopol
 * @since 12/27/2020 5:55 PM
 */
public class MavenArtifactExtractionTest {

    @Test
    public void testGetDependentArtifactSetFromEffectivePOM() {
        Set<Artifact> jarArtifactInfoSet =
                MavenArtifactExtraction.getDependentArtifactSet("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
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
        Set<Artifact> dependentArtifactSet =
                MavenArtifactExtraction.getDependentArtifactSet("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                        "RefactoringMinerIssueReproduction",
                        "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        assert dependentArtifactSet.size() == 1;

        Artifact artifact = dependentArtifactSet.iterator().next();

        JarInformation jarInformation = ExternalJarExtractionUtility.getJarInfo(artifact);

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

        Set<Artifact> dependentArtifactSet =
                MavenArtifactExtraction.getDependentArtifactSet("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c", projectName, repository);

        Artifact artifact = dependentArtifactSet.iterator().next();

        assert "com.github.tsantalis".equals(artifact.getGroupId())
                && "refactoring-miner".equals(artifact.getArtifactId())
                && "2.0.2".equals(artifact.getVersion());
    }

}
