package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;

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
    public void testLoadExternalJars() {
        String commitId = "b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c";
        String projectName = "RefactoringMinerIssueReproduction";
        Path projectDirectory = Path.of("testProjectDirectory").resolve(projectName);

        Repository repository = GitUtil.getRepository(projectName,
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git", projectDirectory);

        TypeInferenceAPI.loadExternalJars(commitId, projectName, repository);
        TypeInferenceAPI.loadExternalJars(commitId, projectName, repository);

        List<String> qualifiedNameList = TypeInferenceAPI.getQualifiedClassName("Refactoring");

        assert qualifiedNameList.size() == 1;
        assert "org.refactoringminer.api.Refactoring".equals(qualifiedNameList.get(0));
    }

    @Test
    public void testGetAllMethods() {
        String javaVersion = getProperty("java.version");

        List<String> importList = new ArrayList<>();
        importList.add("java.util.Collections");

        List<MethodInfo> methodInfoList =
                TypeInferenceAPI.getAllMethods(new HashSet<>(), javaVersion, importList,
                        "reverse", 1);

        assert methodInfoList.size() == 1;

        assert "java.util.Collections::public static void reverse(java.util.List)".equals(methodInfoList.get(0).toString());
    }
}
