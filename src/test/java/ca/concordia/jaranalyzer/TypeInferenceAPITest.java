package ca.concordia.jaranalyzer;

import io.vavr.Tuple3;
import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * @author Diptopol
 * @since 12/23/2020 9:54 PM
 */
public class TypeInferenceAPITest {

    @Test
    public void testGetQualifiedClassName() {
        List<String> qualifiedNameList = TypeInferenceAPI.getQualifiedClassName("AtomicLong");

        assert qualifiedNameList.size() == 1;
        assert "java.util.concurrent.atomic.AtomicLong".equals(qualifiedNameList.get(0));
    }

    @Test
    public void testGetJarArtifactInfoFromEffectivePOM() {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                TypeInferenceAPI.getDependenciesFromEffectivePom("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction",
                        "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        assert jarArtifactInfoSet.size() == 1;

        Tuple3<String, String, String> jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarArtifactInfo._1)
                && "refactoring-miner".equals(jarArtifactInfo._2)
                && "2.0.2".equals(jarArtifactInfo._3);
    }
}
