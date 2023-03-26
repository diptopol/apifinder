package ca.concordia.apifinder.artifactextractor;

import ca.concordia.apifinder.models.Artifact;
import ca.concordia.apifinder.util.FileUtils;
import ca.concordia.apifinder.util.GitUtil;
import ca.concordia.apifinder.util.PropertyReader;
import ca.concordia.apifinder.util.Utility;
import io.vavr.Tuple2;
import org.apache.commons.lang3.StringUtils;
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
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ca.concordia.apifinder.util.FileUtils.deleteDirectory;

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
            gradleFileContentsMap = populateGradleBuildFileContents(ghRepository, this.commitId);
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

    private Map<Path, String> populateGradleBuildFileContents(GHRepository ghRepository, String commitId) {
        Map<String, String> buildFileContentsMap = new HashMap<>();

        Tuple2<String, String> settingsNameContentTuple = getGradleFileNameContentTuple(ghRepository, commitId, "settings.gradle");

        if (Objects.nonNull(settingsNameContentTuple)) {
            buildFileContentsMap.put(settingsNameContentTuple._1(), settingsNameContentTuple._2());

            List<String> subModuleList = getSubModuleList(settingsNameContentTuple._2());

            if (!subModuleList.isEmpty()) {
                String subModulePath = getSubModulePath(subModuleList.get(0), ghRepository, commitId);

                if (Objects.nonNull(subModulePath)) {
                    for (String subModuleName: subModuleList) {
                        String subModuleDirectoryPath = subModulePath.concat(subModuleName).concat("/");

                        Tuple2<String, String> subModuleBuildContentTuple =
                                getGradleFileNameContentTuple(ghRepository, commitId,
                                        subModuleDirectoryPath.concat(subModuleName).concat(".gradle"));

                        if (Objects.nonNull(subModuleBuildContentTuple)) {
                            populateBuildRelatedFileList(ghRepository, commitId, buildFileContentsMap, subModuleDirectoryPath, subModuleBuildContentTuple);

                            if (!buildFileContentsMap.containsKey(subModuleBuildContentTuple._1())) {
                                buildFileContentsMap.put(subModuleBuildContentTuple._1(), subModuleBuildContentTuple._2());
                            }
                        }
                    }
                }
            }
        }

        Tuple2<String, String> buildNameContentTuple = getGradleFileNameContentTuple(ghRepository, commitId, "build.gradle");

        if (Objects.nonNull(buildNameContentTuple)) {
            populateBuildRelatedFileList(ghRepository, commitId, buildFileContentsMap, "", buildNameContentTuple);
            populateSpotlessFile(ghRepository, commitId, buildFileContentsMap, buildNameContentTuple);
            buildFileContentsMap.put(buildNameContentTuple._1(), buildNameContentTuple._2());
        }

        return buildFileContentsMap.entrySet()
                .stream()
                .collect(Collectors.toMap(e -> Paths.get(e.getKey()), Map.Entry::getValue));
    }

    private void populateSpotlessFile(GHRepository ghRepository,
                                      String commitId,
                                      Map<String, String> buildFileContentsMap,
                                      Tuple2<String, String> buildContentTuple) {

        List<String> spotlessFilePathList = getSpotlessFileList(buildContentTuple._2());

        for (String spotlessFilePath: spotlessFilePathList) {
            Tuple2<String, String> spotlessFilePathTuple =
                    getGradleFileNameContentTuple(ghRepository, commitId, spotlessFilePath);

            if (Objects.nonNull(spotlessFilePathTuple) && !buildFileContentsMap.containsKey(spotlessFilePathTuple._1())) {
                buildFileContentsMap.put(spotlessFilePathTuple._1(), spotlessFilePathTuple._2());
            }
        }
    }

    private void populateBuildRelatedFileList(GHRepository ghRepository,
                                              String commitId,
                                              Map<String, String> buildFileContentsMap,
                                              String directoryPath,
                                              Tuple2<String, String> buildContentTuple) {
        List<String> buildRelatedFilePathList = getBuildRelatedFiles(buildContentTuple._2());

        for (String buildRelatedFilePath: buildRelatedFilePathList) {
            if (!buildRelatedFilePath.contains("/")) {
                buildRelatedFilePath = directoryPath.concat(buildRelatedFilePath);
            }

            Tuple2<String, String> innerBuildRelatedFilePathTuple =
                    getGradleFileNameContentTuple(ghRepository, commitId, buildRelatedFilePath);

            if (Objects.nonNull(innerBuildRelatedFilePathTuple)) {
                populateBuildRelatedFileList(ghRepository, commitId, buildFileContentsMap, directoryPath, innerBuildRelatedFilePathTuple);

                if (!buildFileContentsMap.containsKey(innerBuildRelatedFilePathTuple._1())) {
                    buildFileContentsMap.put(innerBuildRelatedFilePathTuple._1(), innerBuildRelatedFilePathTuple._2());
                }
            }
        }
    }

    private String getSubModulePath(String subModuleName, GHRepository ghRepository, String commitId) {
        List<Tuple2<GHTree, String>> subModulePathToVisit = new ArrayList<>();

        try {
            GHTree ghTree = ghRepository.getTree(commitId);
            subModulePathToVisit.add(new Tuple2<>(ghTree, ""));

            while (!subModulePathToVisit.isEmpty()) {
                Tuple2<GHTree, String> pathTuple = subModulePathToVisit.get(0);
                subModulePathToVisit.remove(0);
                String path = pathTuple._2();

                for (GHTreeEntry ghTreeEntry: pathTuple._1().getTree()) {
                    if (ghTreeEntry.getPath().equals(subModuleName.concat(".gradle"))
                            || ghTreeEntry.getPath().equals(subModuleName.concat(".gradle.kts"))) {
                        return path.replaceAll(subModuleName.concat("/"), "");

                    } else if (ghTreeEntry.getType().equals("tree") && !ghTreeEntry.getPath().startsWith(".")
                            && !Arrays.asList("src", "config").contains(ghTreeEntry.getPath())) {
                        subModulePathToVisit.add(new Tuple2<>(ghTreeEntry.asTree(), path.concat(ghTreeEntry.getPath().concat("/"))));
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Error", e);
        }

        return null;
    }

    private List<String> getSpotlessFileList(String buildGradleContent) {
        List<String> filePathList = new ArrayList<>();

        buildGradleContent = buildGradleContent.replaceAll("\n", " newline ")
                .replaceAll("\r", "")
                .replaceAll("spotless(\\s*)\\{", "spotless{");

        if (buildGradleContent.contains("spotless{")) {
            buildGradleContent = buildGradleContent.substring(buildGradleContent.indexOf("licenseHeaderFile"),
                    buildGradleContent.indexOf("newline", buildGradleContent.indexOf("licenseHeaderFile")));
            Pattern licenseHeaderFilePattern = Pattern.compile("(?<=licenseHeaderFile)(.*)(?=[\"'])");
            Matcher licenseHeaderFileMatcher = licenseHeaderFilePattern.matcher(buildGradleContent);

            while (licenseHeaderFileMatcher.find()) {
                String matchingText = licenseHeaderFileMatcher.group(1);

                int startIndex = 0;
                if (matchingText.contains("'")) {
                    startIndex = matchingText.indexOf("'");
                } else if (matchingText.contains("\"")) {
                    startIndex = matchingText.indexOf("\"");
                }

                String licenseFilePath = matchingText.substring(startIndex + 1);

                if (licenseFilePath.startsWith("$")) {
                    licenseFilePath = licenseFilePath.substring(licenseFilePath.indexOf("/") + 1);
                }

                filePathList.add(licenseFilePath);
            }
        }

        return filePathList;
    }

    private List<String> getBuildRelatedFiles(String buildGradleContent) {
        List<String> filePathList = new ArrayList<>();

        buildGradleContent = buildGradleContent.replaceAll("apply(\\s*)from:(\\s*)", "applyfrom:");

        if (buildGradleContent.contains("applyfrom")) {
            Pattern applyFromPattern = Pattern.compile("(?<=applyfrom:)(.*)");
            Matcher applyFromMatcher = applyFromPattern.matcher(buildGradleContent);

            while (applyFromMatcher.find()) {
                String filePath = applyFromMatcher.group(1)
                        .replaceAll("'", "")
                        .replaceAll("\"", "");

                if (filePath.startsWith("$")) {
                    filePath = filePath.substring(filePath.indexOf("/") + 1);
                }

                filePathList.add(filePath);
            }
        }

        return filePathList;
    }

    private List<String> getSubModuleList(String settingsContent) {
        List<String> subModuleList = new ArrayList<>();
        settingsContent = settingsContent.replaceAll("\n", "").replaceAll("\r", "")
                .replaceAll("include(\\s*)\\(", "include(");;

        if (settingsContent.contains("include(")) {
            Pattern subModulePattern = Pattern.compile("(?<=include\\()(.*?)(?=\\))");
            Matcher subModuleMatcher = subModulePattern.matcher(settingsContent);

            while (subModuleMatcher.find()) {
                String matchedContent = subModuleMatcher.group(1).replaceAll("\\s", "");
                List<String> moduleNameList =
                        getProcessedSubModuleNameList(new ArrayList<>(Arrays.asList(matchedContent.split(","))));

                subModuleList.addAll(moduleNameList);
            }
        } else if (settingsContent.contains("include")) {
            Pattern subModuleMatcher = Pattern.compile("(?<=include)(.*?)(?=(include|=|$))");
            Matcher matcher = subModuleMatcher.matcher(settingsContent);

            while (matcher.find()) {
                String matchedContent = matcher.group(1);

                int index = -1;

                if (matchedContent.lastIndexOf("\"") >= 0) {
                    index = matchedContent.lastIndexOf("\"");
                } else if (matchedContent.lastIndexOf("'") >= 0) {
                    index = matchedContent.lastIndexOf("'");
                }

                if (index >= 0) {
                    matchedContent = matchedContent.substring(0, index + 1);
                }

                List<String> moduleNameList =
                        getProcessedSubModuleNameList(new ArrayList<>(Arrays.asList(matchedContent.split(","))));

                subModuleList.addAll(moduleNameList);
            }
        }

        return subModuleList;
    }

    private List<String> getProcessedSubModuleNameList(List<String> subModuleList) {
        return subModuleList.stream()
                .map(StringUtils::trim)
                .map(m -> m.replaceAll("\"", ""))
                .map(m -> m.replaceAll("'", ""))
                .map(m -> m.replaceAll(":", "/"))
                .collect(Collectors.toList());
    }

    private Tuple2<String, String> getGradleFileNameContentTuple(GHRepository ghRepository, String commitId, String filePath) {
        try {
            GHContent ghContent = ghRepository.getFileContent(filePath, commitId);
            String content = FileUtils.getFileContent(ghContent.read());

            return new Tuple2<>(filePath, content);
        } catch (IOException e) {
            try {
                filePath = filePath.concat(".kts");

                GHContent ghContent = ghRepository.getFileContent(filePath, commitId);
                String content = FileUtils.getFileContent(ghContent.read());

                return new Tuple2<>(filePath, content);
            } catch (IOException e1) {
                return null;
            }
        }
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

                    if (moduleArtifact.getVersion().endsWith("-SNAPSHOT")) {
                        Artifact artifact = new Artifact(moduleArtifact.getGroupId(),
                                moduleArtifact.getArtifactId(),
                                moduleArtifact.getVersion().replace("-SNAPSHOT", ""),
                                moduleArtifact.getType());

                        artifact.setInternalDependency(true);

                        artifactSet.add(artifact);
                    }
                }

                for (IdeaDependency dependency : module.getDependencies()) {
                    if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                        IdeaSingleEntryLibraryDependency ideaSingleEntryLibraryDependency =
                                (IdeaSingleEntryLibraryDependency) dependency;

                        GradleModuleVersion gradleModuleVersion = ideaSingleEntryLibraryDependency.getGradleModuleVersion();

                        if (Objects.nonNull(gradleModuleVersion)) {
                            artifactSet.add(new Artifact(gradleModuleVersion.getGroup(), gradleModuleVersion.getName(),
                                    gradleModuleVersion.getVersion(), getType(ideaSingleEntryLibraryDependency.getFile().getName())));

                            if (gradleModuleVersion.getVersion().endsWith("-SNAPSHOT")) {
                                artifactSet.add(new Artifact(gradleModuleVersion.getGroup(), gradleModuleVersion.getName(),
                                        gradleModuleVersion.getVersion().replace("-SNAPSHOT", ""),
                                        getType(ideaSingleEntryLibraryDependency.getFile().getName())));
                            }
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
