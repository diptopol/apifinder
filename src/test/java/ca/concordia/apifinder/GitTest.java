package ca.concordia.apifinder;

import ca.concordia.apifinder.util.GitUtil;
import org.junit.Test;

/**
 * @author Diptopol
 * @since 3/7/2023 2:13 PM
 */
public class GitTest {

    @Test
    public void testGitCommitRetrieval() {
        String cloneUrl = "https://github.com/javaparser/javaparser.git";
        String branchOrTagName = "javaparser-parent-3.24.10";

        String commitID = GitUtil.getCommitIdFromGitHubRemote(cloneUrl, branchOrTagName);

        assert "d8d616bc8ec47a349039180015c91a61e373aa3f".equals(commitID);
    }

}
