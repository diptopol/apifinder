package ca.concordia.jaranalyzer.artifactextractor;

import ca.concordia.jaranalyzer.models.Artifact;
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
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Diptopol
 * @since 3/15/2022 10:21 PM
 */
public class GradleArtifactExtractor extends ArtifactExtractor {

    private static final Logger logger = LoggerFactory.getLogger(MavenArtifactExtractor.class);

    private static final String GRADLE_HOME_PATH = PropertyReader.getProperty("gradle.home");

    private final String commitId;
    private final Git git;
    private final File projectDirectory;

    public GradleArtifactExtractor(String commitId, String projectName, Git git) {
        this.commitId = commitId;
        this.git = git;
        this.projectDirectory = Utility.getProjectPath(projectName).resolve(projectName).toFile();
    }

    @Override
    public String getJavaVersion() {
        String branchName = GitUtil.checkoutToCommit(git, this.commitId);
        GradleConnector connector = getGradleConnector(this.projectDirectory);
        String javaVersion = null;

        try (ProjectConnection connection = connector.connect()) {
            IdeaProject project = connection.getModel(IdeaProject.class);

            javaVersion = project.getJavaLanguageSettings().getLanguageLevel().getMajorVersion();
        } catch (GradleConnectionException e) {
            logger.error("Error occurred", e);
        }

        GitUtil.checkoutToCommit(git, branchName);

        return javaVersion;
    }

    @Override
    public Set<Artifact> getDependentArtifactSet() {
        Set<Artifact> artifactSet = new HashSet<>();
        String branchName = GitUtil.checkoutToCommit(git, this.commitId);

        GradleConnector connector = getGradleConnector(this.projectDirectory);

        try (ProjectConnection connection = connector.connect()) {
            IdeaProject project = connection.getModel(IdeaProject.class);

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

        GitUtil.checkoutToCommit(git, branchName);

        return artifactSet;
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
