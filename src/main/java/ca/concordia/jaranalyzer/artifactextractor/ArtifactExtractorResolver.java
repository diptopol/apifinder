package ca.concordia.jaranalyzer.artifactextractor;

import ca.concordia.jaranalyzer.util.GitUtil;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

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

    private final String commitId;
    private final String projectName;

    private Git git;
    private String cloneUrl;

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
        this.cloneUrl = cloneLink;
    }

    /*
     * this.git.getRepository().getDirectory() will return the .git directory. So we are interested about the parent directory
     * of .git directory.
     */
    public ArtifactExtractor getArtifactExtractor() {
        BuildTool buildTool = getBuildTool();

        ArtifactExtractor artifactExtractor;

        if (BuildTool.MAVEN.equals(buildTool)) {
            artifactExtractor = Objects.nonNull(git)
                    ? new MavenArtifactExtractor(commitId, projectName, git)
                    : new MavenArtifactExtractor(commitId, projectName, cloneUrl);

        } else if (BuildTool.GRADLE.equals(buildTool)) {
            artifactExtractor = Objects.nonNull(git)
                    ? new GradleArtifactExtractor(commitId, projectName, git)
                    : new GradleArtifactExtractor(commitId, projectName, cloneUrl);

        } else {
            logger.info("Build Tool not recognized for Project: {}", projectName);
            throw new IllegalStateException();
        }

        return artifactExtractor;
    }

    private BuildTool getBuildTool() {
        if (Objects.nonNull(this.git)) {
            Path projectDirectory = this.git.getRepository().getDirectory().getParentFile().toPath();

            if (projectDirectory.resolve("pom.xml").toFile().exists()) {
                return BuildTool.MAVEN;
            } else if (projectDirectory.resolve("build.gradle").toFile().exists()
                    || projectDirectory.resolve("build.gradle.kts").toFile().exists()) {
                return BuildTool.GRADLE;
            }
        } else {
            if (GitUtil.isFileExists(this.cloneUrl, "pom.xml")) {
                return BuildTool.MAVEN;
            } else if (GitUtil.isFileExists(this.cloneUrl, "build.gradle")) {
                return BuildTool.GRADLE;
            }
        }

        return null;
    }

}
