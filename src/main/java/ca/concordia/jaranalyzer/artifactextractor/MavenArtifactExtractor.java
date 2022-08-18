package ca.concordia.jaranalyzer.artifactextractor;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.service.MavenPOMService;
import ca.concordia.jaranalyzer.util.FileUtils;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import org.apache.maven.shared.invoker.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.FileUtils.deleteDirectory;
import static ca.concordia.jaranalyzer.util.FileUtils.readFile;
import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;

/**
 * @author Diptopol
 * @since 3/14/2022 11:35 AM
 */
public class MavenArtifactExtractor extends ArtifactExtractor {

    private static final Logger logger = LoggerFactory.getLogger(MavenArtifactExtractor.class);

    private final String commitId;
    private final String projectName;
    private final String cloneUrl;
    private final String effectivePOMContent;

    private final MavenPOMService mavenPOMService;

    private Git git;

    public MavenArtifactExtractor(String commitId, String projectName, String cloneUrl) {
        mavenPOMService = new MavenPOMService();

        this.commitId = commitId;
        this.projectName = projectName;
        this.cloneUrl = cloneUrl;
        this.effectivePOMContent = loadEffectivePOMContent();
    }

    public MavenArtifactExtractor(String commitId, String projectName, Git git) {
        mavenPOMService = new MavenPOMService();

        this.commitId = commitId;
        this.projectName = projectName;
        this.git = git;
        this.cloneUrl = git.getRepository().getConfig().getString("remote", "origin", "url");
        this.effectivePOMContent = loadEffectivePOMContent();
    }

    @Override
    public String getJavaVersion() {
        return getJavaVersion(effectivePOMContent);
    }

    @Override
    public Set<Artifact> getDependentArtifactSet() {
        return getDependentArtifactSet(effectivePOMContent);
    }

    private String loadEffectivePOMContent() {
        String effectivePOM = mavenPOMService.getEffectivePOM(this.cloneUrl, this.commitId);

        if (Objects.isNull(effectivePOM)) {
            effectivePOM = getEffectivePOMContent();

            mavenPOMService.saveEffectivePOM(this.cloneUrl, this.commitId, effectivePOM);
        }

        return effectivePOM;
    }

    private String getEffectivePOMContent() {
        String mavenHome = getProperty("maven.home");
        Path projectPath = Utility.getProjectPath(this.projectName);

        if (!new File(mavenHome).exists()) {
            throw new RuntimeException("Maven Home is not configured properly");
        }

        FileUtils.createFolderIfAbsent(projectPath);

        Map<Path, String> pomFileContentsMap = getPOMFileContents();

        if (pomFileContentsMap.isEmpty()) {
            return null;
        }

        Path p = projectPath.resolve("tmp").resolve(this.commitId);
        FileUtils.materializeAtBase(p, pomFileContentsMap);
        Path effectivePomPath = p.resolve("effectivePom.xml");

        if (!effectivePomPath.toFile().exists()) {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(p.resolve("pom.xml").toAbsolutePath().toString()));
            request.setGoals(Arrays.asList("help:effective-pom", "-Doutput=" + effectivePomPath.toAbsolutePath().toString()));
            Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(new File(mavenHome));
            try {
                InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    logger.info("Could not generate effective pom");

                    return null;
                }
            } catch (Exception e) {
                logger.error("Error", e);

                return null;
            }
        }

        String effectivePomPathContent = readFile(effectivePomPath);
        deleteDirectory(p);

        return effectivePomPathContent;
    }

    private Map<Path, String> getPOMFileContents() {
        if (Objects.nonNull(this.git)) {
            return getPOMFileContentsFromLocal();
        } else {
            return getPOMFileContentsFromRemote();
        }
    }

    private Map<Path, String> getPOMFileContentsFromLocal() {
        Repository repository = this.git.getRepository();

        return GitUtil.populateFileContents(repository, this.commitId, x -> x.endsWith("pom.xml"));
    }

    private Map<Path, String> getPOMFileContentsFromRemote() {
        Map<Path, String> pomFileContentsMap = new HashMap<>();
        try {
            GitHub gitHub = GitUtil.connectGithub();
            String repositoryName = GitUtil.extractRepositoryName(this.cloneUrl);
            GHRepository ghRepository = gitHub.getRepository(repositoryName);
            pomFileContentsMap = populatePOMFileContents(ghRepository, this.commitId);
        } catch (IOException e) {
            logger.error("Error occurred", e);
        }

        return pomFileContentsMap;
    }

    public static Map<Path, String> populatePOMFileContents(GHRepository ghRepository, String commitId) {
        Map<Path, String> pomFileContentsMap = new HashMap<>();

        List<String> buildFilePathList = new ArrayList<>(Arrays.asList("pom.xml"));

        while (!buildFilePathList.isEmpty()) {
            String buildFilePath = buildFilePathList.get(0);
            buildFilePathList.remove(0);

            String parentDirectoryPath = buildFilePath.replace("pom.xml", "");
            try {
                GHContent ghContent = ghRepository.getFileContent(buildFilePath, commitId);
                String pomContent = FileUtils.getFileContent(ghContent.read());

                pomFileContentsMap.put(Path.of(buildFilePath), pomContent);

                List<String> subModuleList = getSubModuleList(pomContent);

                for (String subModule: subModuleList) {
                    String subModuleBuildFilePath = parentDirectoryPath.concat(subModule);

                    if (!subModuleBuildFilePath.endsWith(".xml")) {
                        subModuleBuildFilePath = subModuleBuildFilePath.concat("/").concat("pom.xml");
                    }

                    buildFilePathList.add(subModuleBuildFilePath);
                }
            } catch (IOException e) {
                logger.warn("File couldn't be found: {}", buildFilePath);
            }
        }

        return pomFileContentsMap;
    }

    private static List<String> getSubModuleList(String pomContent) {
        if (Objects.isNull(pomContent)) {
            return Collections.emptyList();
        }

        List<String> subModuleList = new ArrayList<>();

        try {
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = saxBuilder.build(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

            Element root = document.getRootElement();
            List<Element> projectElementList = getProjectElementList(root);

            for (Element project: projectElementList) {
                Element modulesElement = getChildElement(project, "modules");

                if (Objects.nonNull(modulesElement)) {
                    List<Element> subModuleElementList = modulesElement.getChildren();

                    for (Element subModuleElement: subModuleElementList) {
                        subModuleList.add(subModuleElement.getValue());
                    }
                }

                Element profilesElement = getChildElement(project, "profiles");

                if (Objects.nonNull(profilesElement)) {
                    List<Element> profileElementList = profilesElement.getChildren();

                    for (Element profileElement: profileElementList) {
                        Element profileModulesElement = getChildElement(profileElement, "modules");

                        if (Objects.nonNull(profileModulesElement)) {
                            List<Element> profileSubModuleElementList = profileModulesElement.getChildren();

                            for (Element profileSubModule: profileSubModuleElementList) {
                                subModuleList.add(profileSubModule.getValue());
                            }
                        }
                    }
                }
            }
        } catch (IOException | JDOMException e) {
            logger.error("Error occurred", e);
        }

        return subModuleList;
    }

    private static String getJavaVersion(String pomContent) {
        if (Objects.isNull(pomContent)) {
            return null;
        }

        List<String> javaVersionList = new ArrayList<>();

        try {
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = saxBuilder.build(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

            Element root = document.getRootElement();

            List<Element> projectElementList = getProjectElementList(root);

            //java 9 and onwards
            for (Element project: projectElementList) {
                Element propertiesElement = getChildElement(project, "properties");

                if (Objects.nonNull(propertiesElement)) {
                    List<Element> propertyElementList = propertiesElement.getChildren();

                    for (Element propertyElement: propertyElementList) {
                        if (propertyElement.getName().equals("maven.compiler.release")) {
                            javaVersionList.add(propertyElement.getValue());
                        }
                    }
                }
            }

            //spring boot specification
            if (javaVersionList.isEmpty()) {
                for (Element project: projectElementList) {
                    Element propertiesElement = getChildElement(project, "properties");

                    if (Objects.nonNull(propertiesElement)) {
                        List<Element> propertyElementList = propertiesElement.getChildren();

                        for (Element propertyElement: propertyElementList) {
                            if (propertyElement.getName().equals("java.version")) {
                                javaVersionList.add(Utility.convertJavaVersion(propertyElement.getValue()));
                            }
                        }
                    }
                }
            }

            //maven compiler-plugin
            if (javaVersionList.isEmpty()) {
                for (Element project: projectElementList) {
                    String javaVersion = getJavaVersionFromPlugin(project);
                    if (Objects.nonNull(javaVersion)) {
                        javaVersionList.add(javaVersion);
                    }
                }
            }

            //maven.compiler.target properties
            if (javaVersionList.isEmpty()) {
                for (Element project: projectElementList) {
                    Element propertiesElement = getChildElement(project, "properties");

                    if (Objects.nonNull(propertiesElement)) {
                        List<Element> propertyElementList = propertiesElement.getChildren();

                        for (Element propertyElement: propertyElementList) {
                            if (propertyElement.getName().equals("maven.compiler.target")) {
                                javaVersionList.add(Utility.convertJavaVersion(propertyElement.getValue()));
                            }
                        }
                    }
                }
            }
        } catch (IOException | JDOMException e) {
            logger.error("Error", e);
        }

        javaVersionList = javaVersionList.stream().filter(Objects::nonNull).collect(Collectors.toList());

        if (javaVersionList.isEmpty()) {
            return getProperty("java.version");
        }

        int javaVersion = Collections.max(javaVersionList.stream()
                .filter(Objects::nonNull)
                .map(Integer::valueOf)
                .collect(Collectors.toList()));

        return String.valueOf(javaVersion);
    }

    private static Set<Artifact> getDependentArtifactSet(String pomContent) {
        if (Objects.isNull(pomContent)) {
            return Collections.emptySet();
        }

        Set<Artifact> dependentArtifactSet = new HashSet<>();
        Set<Artifact> projectArtifactSet = new HashSet<>();

        try {
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = saxBuilder.build(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

            Element root = document.getRootElement();

            List<Element> projectElementList = getProjectElementList(root);

            for (Element project: projectElementList) {
                Map<String, String> propertyMap = getPropertyMap(root);

                String projectGroupId = getElementValue(getChildElement(project, "groupId"));
                String projectArtifactId = getElementValue(getChildElement(project, "artifactId"));
                String projectVersion = getElementValue(getChildElement(project, "version"));

                if (Objects.nonNull(projectVersion) && propertyMap.containsKey(projectVersion)) {
                    projectVersion = propertyMap.get(projectVersion);
                }

                String projectType = getElementValue(getChildElement(project, "packaging"));

                if (Objects.isNull(projectType)) {
                    projectType = "jar";
                }

                Artifact projectArtifact = new Artifact(projectGroupId, projectArtifactId, projectVersion, projectType);
                projectArtifact.setInternalDependency(true);

                projectArtifactSet.add(projectArtifact);

                Element dependenciesElement = getDependenciesElement(project);

                if (Objects.nonNull(dependenciesElement)) {
                    List<Element> dependencyElementList = dependenciesElement.getChildren();

                    for (Element dependency: dependencyElementList) {
                        String dependentArtifactGroupId = getElementValue(getChildElement(dependency, "groupId"));
                        String dependentArtifactArtifactId = getElementValue(getChildElement(dependency, "artifactId"));
                        String dependentArtifactVersion = getElementValue(getChildElement(dependency, "version"));

                        if (Objects.nonNull(dependentArtifactVersion) && propertyMap.containsKey(dependentArtifactVersion)) {
                            dependentArtifactVersion = propertyMap.get(dependentArtifactVersion);
                        }

                        String dependentArtifactType = getElementValue(getChildElement(dependency, "type"));

                        if (Objects.isNull(dependentArtifactType)) {
                            dependentArtifactType = "jar";
                        }

                        dependentArtifactSet.add(new Artifact(dependentArtifactGroupId, dependentArtifactArtifactId,
                                dependentArtifactVersion, dependentArtifactType));
                    }
                }
            }

            dependentArtifactSet.removeIf(projectArtifactSet::contains);
            dependentArtifactSet.addAll(projectArtifactSet);

            return dependentArtifactSet;

        } catch (IOException | JDOMException e) {
            logger.error("Error", e);
        }

        return Collections.emptySet();
    }

    private static String getElementValue(Element element) {
        return Objects.nonNull(element) ? element.getValue() : null;
    }

    private static String getJavaVersionFromPlugin(Element project) {
        Element buildElement = getChildElement(project, "build");

        if (Objects.nonNull(buildElement)) {
            String javaVersion = getJavaVersionFromMavenCompilerPlugin(buildElement);

            if (Objects.nonNull(javaVersion)) {
                return javaVersion;
            }

            Element pluginManagementElement = getChildElement(buildElement, "pluginManagement");

            javaVersion = getJavaVersionFromMavenCompilerPlugin(pluginManagementElement);

            if (Objects.nonNull(javaVersion)) {
                return javaVersion;
            }
        }

        return null;
    }

    private static String getJavaVersionFromMavenCompilerPlugin(Element element) {
        Element pluginsElement = getChildElement(element, "plugins");

        if (Objects.nonNull(pluginsElement)) {
            for (Element pluginElement: pluginsElement.getChildren()) {
                Element artifactIdElement = getChildElement(pluginElement, "artifactId");
                String artifactId = getElementValue(artifactIdElement);

                if ("maven-compiler-plugin".equals(artifactId)) {
                    Element configurationElement = getChildElement(pluginElement, "configuration");

                    if (Objects.nonNull(configurationElement)) {
                        Element sourceElement = getChildElement(configurationElement, "source");
                        return Utility.convertJavaVersion(getElementValue(sourceElement));
                    }
                }
            }
        }

        return null;
    }

    private static Element getDependenciesElement(Element project) {
        Element dependencyManagementElement = getChildElement(project, "dependencyManagement");

        if (Objects.nonNull(dependencyManagementElement)) {
            return getChildElement(dependencyManagementElement, "dependencies");
        } else {
            return getChildElement(project, "dependencies");
        }
    }

    //TODO: need to check pom content with multiple project
    private static List<Element> getProjectElementList(Element root) {
        List<Element> children = root.getChildren();

        if (!children.stream().allMatch(e -> e.getName().equals("project"))) {
            return Collections.singletonList(root);

        } else {
            return children;
        }
    }

    private static Map<String, String> getPropertyMap(Element root) {
        Map<String, String> propertyMap = new HashMap<>();
        Element propertiesElement = getChildElement(root, "properties");

        if (Objects.nonNull(propertiesElement)) {
            List<Element> propertyElementList = propertiesElement.getChildren();

            for (Element property: propertyElementList) {
                propertyMap.put("${" + property.getName() + "}", property.getValue());
            }
        }

        return propertyMap;
    }

    private static Element getChildElement(Element element, String childElementName) {
        List<Element> children = element.getChildren();

        for (Element child: children) {
            if (child.getName().equals(childElementName)) {
                return child;
            }
        }

        return null;
    }

}
