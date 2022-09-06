package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractor;
import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractorResolver;
import ca.concordia.jaranalyzer.artifactextractor.MavenArtifactExtractor;
import ca.concordia.jaranalyzer.entity.JarInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import org.junit.Test;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * @author Diptopol
 * @since 12/27/2020 5:55 PM
 */
public class ArtifactExtractorTest {

    @Test
    public void testGoogleArtifactsRetrieval() {
        Artifact artifact = new Artifact("com.android.tools.build", "gradle", "7.1.3");
        Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(artifact);

        assert jarInfoSet.size() == 1;

        JarInfo jarInfo = jarInfoSet.iterator().next();

        assert "com.android.tools.build".equals(jarInfo.getGroupId())
                && "gradle".equals(jarInfo.getArtifactId())
                && "7.1.3".equals(jarInfo.getVersion());
    }

    @Test
    public void testGetDependentArtifactSetFromEffectivePOM() {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction",
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> jarArtifactInfoSet = extractor.getDependentArtifactSet();

        assert jarArtifactInfoSet.size() == 2;

        assert jarArtifactInfoSet.contains(new Artifact("com.github.tsantalis", "refactoring-miner", "2.0.2"));
    }

    @Test
    public void testGetJarInfo() {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction",
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> dependentArtifactSet = extractor.getDependentArtifactSet();

        assert dependentArtifactSet.size() == 2;

        Artifact artifact = dependentArtifactSet.stream()
                .filter(d -> d.getArtifactId().equals("refactoring-miner"))
                .findFirst()
                .orElse(null);

        Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(artifact);

        assert jarInfoSet.size() == 1;

        JarInfo jarInfo = jarInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarInfo.getGroupId())
                && "refactoring-miner".equals(jarInfo.getArtifactId())
                && "2.0.2".equals(jarInfo.getVersion());
    }

    @Test
    public void testGenerateEffectivePOMFromRepository() {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction",
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> dependentArtifactSet = extractor.getDependentArtifactSet();

        assert dependentArtifactSet.contains(new Artifact("com.github.tsantalis", "refactoring-miner", "2.0.2"));
    }

    @Test
    public void testArtifactExtractionFromGradle() {
        String projectName = "mokito";
        String projectCloneLink = "https://github.com/mockito/mockito.git";

        ArtifactExtractorResolver extractorResolver =
                new ArtifactExtractorResolver("ff98622a8f4bbe96ef5405434b5d788fcd118bb4", projectName, projectCloneLink);

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> dependentArtifactSet = extractor.getDependentArtifactSet();


        assert extractor.getJavaVersion().equals("11");
        assert dependentArtifactSet.size() == 65;
    }

    @Test
    public void testPomFileContentExtraction() {
        String cloneUrl = "https://github.com/google/guava.git";
        String commitId = "43a53bc0328c7efd1da152f3b56b1fd241c88d4c";

        try {
            GitHub gitHub = GitUtil.connectGithub();
            String repositoryName = GitUtil.extractRepositoryName(cloneUrl);
            GHRepository ghRepository = gitHub.getRepository(repositoryName);

            Map<Path, String> pomFileContentsMap = MavenArtifactExtractor.populatePOMFileContents(ghRepository, commitId);

            assert 6 == pomFileContentsMap.size();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
