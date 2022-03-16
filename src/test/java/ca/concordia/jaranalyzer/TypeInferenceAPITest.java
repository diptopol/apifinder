package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.FieldInfo;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jgit.api.Git;
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
        Path pathToProject = Utility.getProjectPath(projectName);

        Git git = GitUtil.openRepository(projectName,
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git", pathToProject);

        TypeInferenceAPI.loadExternalJars(commitId, projectName, git);
        TypeInferenceAPI.loadExternalJars(commitId, projectName, git);

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

    @Test
    public void testAllFieldTypes() {
        String javaVersion = getProperty("java.version");

        List<String> importList = new ArrayList<>();
        importList.add("import java.awt.*");

        List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(new HashSet<>(), javaVersion, importList,
                "KEY_FRACTIONALMETRICS", null);

        assert "[public static java.awt.RenderingHints$Key KEY_FRACTIONALMETRICS]".equals(fieldInfoList.toString());
    }

}
