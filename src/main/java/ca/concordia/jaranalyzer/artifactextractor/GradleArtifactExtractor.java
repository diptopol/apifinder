package ca.concordia.jaranalyzer.artifactextractor;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.FileUtils;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.PropertyReader;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jgit.api.Git;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradlePublication;
import org.gradle.tooling.model.gradle.ProjectPublications;
import org.gradle.tooling.model.idea.*;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static ca.concordia.jaranalyzer.util.FileUtils.deleteDirectory;

/**
 * @author Diptopol
 * @since 3/15/2022 10:21 PM
 */
public class GradleArtifactExtractor extends ArtifactExtractor {

    private static final Logger logger = LoggerFactory.getLogger(MavenArtifactExtractor.class);

    private static final String GRADLE_HOME_PATH = PropertyReader.getProperty("gradle.home");

    private final String commitId;
    private String projectName;
    private final String cloneUrl;

    private String javaVersion;
    private Set<Artifact> artifactSet;
    private Git git;
    private File projectDirectory;

    public GradleArtifactExtractor(String commitId, String projectName, String cloneUrl) {
        this.commitId = commitId;
        this.projectName = projectName;
        this.cloneUrl = cloneUrl;
        populateDependencyInfo();
    }

    public GradleArtifactExtractor(String commitId, String projectName, Git git) {
        this.commitId = commitId;
        this.projectName = projectName;
        this.git = git;
        this.cloneUrl = git.getRepository().getConfig().getString("remote", "origin", "url");
        this.projectDirectory = Utility.getProjectPath(projectName).resolve(projectName).toFile();
        populateDependencyInfo();
    }

    private void populateDependencyInfo() {
        if (!new File(GRADLE_HOME_PATH).exists()) {
            throw new RuntimeException("Maven Home is not configured properly");
        }

        if (Objects.nonNull(git)) {
            populateDependencyInfoFromLocal();
        } else {
            populateDependencyInfoFromRemote();
        }
    }

    private void populateDependencyInfoFromLocal() {
        String branchName = GitUtil.checkoutToCommit(git, this.commitId);

        GradleConnector connector = getGradleConnector(this.projectDirectory);
        populateJavaVersionAndArtifactSet(connector);

        GitUtil.checkoutToCommit(git, branchName);
    }

    private void populateDependencyInfoFromRemote() {
        Path projectPath = Utility.getProjectPath(this.projectName);
        FileUtils.createFolderIfAbsent(projectPath);

        Map<Path, String> gradleFileContentsMap = new HashMap<>();
        try {
            GitHub gitHub = GitUtil.connectGithub();
            String repositoryName = GitUtil.extractRepositoryName(this.cloneUrl);
            GHRepository ghRepository = gitHub.getRepository(repositoryName);
            GHTree ghTree = ghRepository.getTree(this.commitId);
            gradleFileContentsMap = GitUtil.populateFileContents(ghTree,
                    new ArrayList<>(Arrays.asList("build.gradle", "settings.gradle")),
                    new ArrayList<>(Arrays.asList(".gradle", ".header")),
                    new ArrayList<>(Arrays.asList(".github")));
        } catch (IOException e) {
            logger.error("Error occurred", e);
        }

        if (gradleFileContentsMap.isEmpty()) {
            return;
        }

        Path projectDirectory = projectPath.resolve("tmp").resolve(this.commitId);
        FileUtils.materializeAtBase(projectDirectory, gradleFileContentsMap);

        GradleConnector connector = getGradleConnector(projectDirectory.toFile());
        populateJavaVersionAndArtifactSet(connector);

        deleteDirectory(projectDirectory);
    }

    @Override
    public String getJavaVersion() {
        return this.javaVersion;
    }

    @Override
    public Set<Artifact> getDependentArtifactSet() {
        return this.artifactSet;
    }

    private void populateJavaVersionAndArtifactSet(GradleConnector connector) {
        Set<Artifact> artifactSet = new HashSet<>();

        try (ProjectConnection connection = connector.connect()) {
            IdeaProject project = connection.getModel(IdeaProject.class);

            this.javaVersion = project.getJavaLanguageSettings().getLanguageLevel().getMajorVersion();

            for (IdeaModule module : project.getModules()) {
                Artifact moduleArtifact = getProjectArtifact(module);

                if (Objects.nonNull(moduleArtifact)) {
                    artifactSet.add(moduleArtifact);
                }

                for (IdeaDependency dependency : module.getDependencies()) {
                    if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                        IdeaSingleEntryLibraryDependency ideaSingleEntryLibraryDependency =
                                (IdeaSingleEntryLibraryDependency) dependency;

                        GradleModuleVersion gradleModuleVersion = ideaSingleEntryLibraryDependency.getGradleModuleVersion();

                        if (Objects.nonNull(gradleModuleVersion)) {
                            artifactSet.add(new Artifact(gradleModuleVersion.getGroup(), gradleModuleVersion.getName(),
                                    gradleModuleVersion.getVersion(), getType(ideaSingleEntryLibraryDependency.getFile().getName())));
                        }
                    }
                }
            }
        } catch (GradleConnectionException e) {
            logger.error("Error occurred", e);
        }

        this.artifactSet = artifactSet;
    }

    private Artifact getProjectArtifact(IdeaModule module) {
        GradleProject gradleProject = module.getGradleProject();
        File moduleDirectory = gradleProject.getProjectDirectory();
        GradleConnector connector = getGradleConnector(moduleDirectory);
        Artifact artifact = null;

        try (ProjectConnection connection = connector.connect()) {
            ProjectPublications projectPublications = connection.getModel(ProjectPublications.class);

            DomainObjectSet<? extends GradlePublication> gradlePublicationSet = projectPublications.getPublications();

            for (GradlePublication gradlePublication: gradlePublicationSet) {
                if (Objects.nonNull(gradlePublication.getId())) {
                    GradleModuleVersion gradleModuleVersion = gradlePublication.getId();

                    artifact = new Artifact(gradleModuleVersion.getGroup(), gradleModuleVersion.getName(), gradleModuleVersion.getVersion());
                    artifact.setInternalDependency(true);
                }
            }

        } catch (GradleConnectionException e) {
            logger.error("Error occurred", e);
        }

        return artifact;
    }

    private GradleConnector getGradleConnector(File projectDirectory) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.useInstallation(new File(GRADLE_HOME_PATH));
        connector.forProjectDirectory(projectDirectory);

        return connector;
    }

    private String getType(String fileName) {
        if (fileName.endsWith(Artifact.JAR_TYPE)) {
            return Artifact.JAR_TYPE;
        } else if (fileName.endsWith(Artifact.POM_TYPE)) {
            return Artifact.POM_TYPE;
        } else {
            logger.info("Unrecognized type for file: {}", fileName);
            throw new IllegalStateException();
        }
    }

}
