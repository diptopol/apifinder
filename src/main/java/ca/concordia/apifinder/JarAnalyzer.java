package ca.concordia.apifinder;

import ca.concordia.apifinder.artifactextractor.ArtifactExtractor;
import ca.concordia.apifinder.artifactextractor.ArtifactExtractorResolver;
import ca.concordia.apifinder.entity.JarInfo;
import ca.concordia.apifinder.entityExtractor.JarInfoExtractor;
import ca.concordia.apifinder.models.Artifact;
import ca.concordia.apifinder.service.JarInfoSaveService;
import ca.concordia.apifinder.service.JarInfoService;
import ca.concordia.apifinder.util.GitUtil;
import ca.concordia.apifinder.util.Utility;
import io.vavr.Tuple2;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import static ca.concordia.apifinder.util.PropertyReader.getProperty;


public class JarAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(JarAnalyzer.class);

    private static Map<Integer, String> JAVA_STORAGE_PATH = new HashMap<>();

    static {
        JAVA_STORAGE_PATH.put(6, getProperty("java.6.jar.directory"));
        JAVA_STORAGE_PATH.put(7, getProperty("java.7.jar.directory"));
        JAVA_STORAGE_PATH.put(8, getProperty("java.8.jar.directory"));
        JAVA_STORAGE_PATH.put(10, getProperty("java.10.jar.directory"));
        JAVA_STORAGE_PATH.put(11, getProperty("java.11.jar.directory"));
        JAVA_STORAGE_PATH.put(12, getProperty("java.12.jar.directory"));
        JAVA_STORAGE_PATH.put(13, getProperty("java.13.jar.directory"));

        JAVA_STORAGE_PATH = Collections.unmodifiableMap(JAVA_STORAGE_PATH);
    }

    private JarInfoSaveService jarInfoSaveService;
    private JarInfoService jarInfoService;

    public JarAnalyzer() {
        jarInfoSaveService = new JarInfoSaveService();
        jarInfoService = new JarInfoService();
    }

    public Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, String cloneUrl) {
        String nearestTagCommit = GitUtil.getNearestTagCommitId(cloneUrl, commitId);
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver(nearestTagCommit, projectName, cloneUrl);
        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        String javaVersion = extractor.getJavaVersion();
        Integer majorJavaVersion = Utility.getMajorJavaVersion(javaVersion);

        loadJavaPackage(majorJavaVersion);

        Set<Artifact> jarArtifactInfoSet = extractor.getDependentArtifactSet();
        storeArtifactSet(jarArtifactInfoSet);

        return new Tuple2<>(String.valueOf(majorJavaVersion), jarArtifactInfoSet);
    }

    public Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, Git git) {
        String nearestTagCommit = GitUtil.getNearestTagCommitIdFromLocalGit(commitId, git);
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver(nearestTagCommit, projectName, git);
        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        String javaVersion = extractor.getJavaVersion();
        Integer majorJavaVersion = Utility.getMajorJavaVersion(javaVersion);

        loadJavaPackage(majorJavaVersion);

        Set<Artifact> jarArtifactInfoSet = extractor.getDependentArtifactSet();
        storeArtifactSet(jarArtifactInfoSet);

        return new Tuple2<>(String.valueOf(majorJavaVersion), jarArtifactInfoSet);
    }

    public void loadJavaPackage(Integer majorJavaVersion) {
        if (!jarInfoService.isJavaVersionExists(String.valueOf(majorJavaVersion))) {
            saveJavaPackages(majorJavaVersion);
        }
    }

    public void loadJar(Artifact artifact) {
        storeArtifactSet(new HashSet<>(Arrays.asList(artifact)));
    }

    public void saveJavaPackages(Integer majorJavaVersion) {
        String javaJarDirectory = JAVA_STORAGE_PATH.get(majorJavaVersion);

        logger.info("Java Jar Directory: {}", javaJarDirectory);
        logger.info("Major Java Version: {}", majorJavaVersion);

        if (Objects.nonNull(javaJarDirectory) && Objects.nonNull(majorJavaVersion)) {
            if (majorJavaVersion >= 9) {
                List<String> jmodFiles = Utility.getFiles(javaJarDirectory, "jmod");

                for (String jmodFileLocation : jmodFiles) {
                    try {
                        Path path = Paths.get(jmodFileLocation);
                        if (Files.exists(path)) {
                            ZipFile zipFile = new ZipFile(new File(jmodFileLocation));

                            ca.concordia.apifinder.entity.JarInfo jarInfo =
                                    JarInfoExtractor.getJarInfo(path.getFileName().toString(), "Java",
                                            String.valueOf(majorJavaVersion), zipFile);

                            jarInfoSaveService.saveJarInfo(jarInfo);
                        }
                    } catch (IOException e) {
                        logger.error("Could not open the JMOD", e);
                    }
                }
            } else {
                List<String> jarFiles = Utility.getFiles(javaJarDirectory, "jar");
                for (String jarLocation : jarFiles) {
                    try {
                        Path path = Paths.get(jarLocation);
                        if (Files.exists(path)) {
                            JarFile jarFile = new JarFile(new File(jarLocation));
                            ca.concordia.apifinder.entity.JarInfo jarInfo =
                                    JarInfoExtractor.getJarInfo(path.getFileName().toString(), "Java", String.valueOf(majorJavaVersion), jarFile);

                            jarInfoSaveService.saveJarInfo(jarInfo);
                        }
                    } catch (Exception e) {
                        logger.error("Could not open the JAR", e);
                    }
                }
            }
        }
    }

    private void storeArtifactSet(Set<Artifact> artifactSet) {
        Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(artifactSet);

        for (JarInfo jarInfo: jarInfoSet) {
            Artifact artifact = new Artifact(jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
            //add only if not present already
            artifactSet.add(artifact);

            if (!jarInfoService.isJarExists(jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion())) {
                JarInfo jarInfoWithLoadedClassInfo =
                        JarInfoExtractor.getJarInfo(jarInfo.getGroupId(), jarInfo.getArtifactId(),
                                jarInfo.getVersion(), jarInfo.getJarFile());
                jarInfoSaveService.saveJarInfo(jarInfoWithLoadedClassInfo);
            }
        }
    }

}
