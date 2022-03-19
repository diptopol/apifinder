package ca.concordia.jaranalyzer.artifactextractor;

import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * @author Diptopol
 * @since 3/15/2022 9:15 PM
 */
public class ArtifactExtractorResolver {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactExtractorResolver.class);

    private enum BuildTool {
        MAVEN,
        GRADLE
    }

    private String commitId;
    private String projectName;
    private Git git;

    public ArtifactExtractorResolver(String commitId,
                                     String projectName,
                                     Git git) {
        this.commitId = commitId;
        this.projectName = projectName;
        this.git = git;
    }

    public ArtifactExtractorResolver(String commitId,
                                     String projectName,
                                     String cloneLink) {
        this.commitId = commitId;
        this.projectName = projectName;

        Path pathToProject = Utility.getProjectPath(projectName);
        this.git = GitUtil.openRepository(projectName, cloneLink, pathToProject);
    }

    /*
     * this.git.getRepository().getDirectory() will return the .git directory. So we are interested about the parent directory
     * of .git directory.
     */
    public ArtifactExtractor getArtifactExtractor() {
        Path projectDirectory = this.git.getRepository().getDirectory().getParentFile().toPath();
        BuildTool buildTool = getBuildTool(projectDirectory);

        ArtifactExtractor artifactExtractor;

        if (BuildTool.MAVEN.equals(buildTool)) {
            artifactExtractor = new MavenArtifactExtractor(commitId, projectName, git);

        } else if (BuildTool.GRADLE.equals(buildTool)) {
            artifactExtractor = new GradleArtifactExtractor(commitId, projectName, git);

        } else {
            logger.info("Build Tool not recognized for Project: {}, Path: {}", projectName, projectDirectory);
            throw new IllegalStateException();
        }

        return artifactExtractor;
    }

    private static BuildTool getBuildTool(Path projectDirectory) {
        if (projectDirectory.resolve("pom.xml").toFile().exists()) {
            return BuildTool.MAVEN;
        } else if (projectDirectory.resolve("build.gradle").toFile().exists()) {
            return BuildTool.GRADLE;
        }

        return null;
    }

}
