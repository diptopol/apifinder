package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import io.vavr.Tuple3;
import org.junit.Test;

import java.util.Set;

/**
 * @author Diptopol
 * @since 12/27/2020 5:55 PM
 */
public class ExternalJarExtractionUtilityTest {

    @Test
    public void testGetJarArtifactInfoFromEffectivePOM() {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                        "RefactoringMinerIssueReproduction",
                        "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        assert jarArtifactInfoSet.size() == 1;

        Tuple3<String, String, String> jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarArtifactInfo._1)
                && "refactoring-miner".equals(jarArtifactInfo._2)
                && "2.0.2".equals(jarArtifactInfo._3);
    }

    @Test
    public void testGetJarInfo() {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                        "RefactoringMinerIssueReproduction",
                        "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        assert jarArtifactInfoSet.size() == 1;

        Tuple3<String, String, String> jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        JarInformation jarInformation = ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3);

        //System.out.println(jarInformation.getPackages().size());

        assert "com.github.tsantalis".equals(jarInformation.getGroupId())
                && "refactoring-miner".equals(jarInformation.getArtifactId())
                && "2.0.2".equals(jarInformation.getVersion());
    }
}
