package ca.concordia.apifinder.util;

import ca.concordia.apifinder.artifactresolver.ArtifactResolver;
import ca.concordia.apifinder.artifactresolver.ArtifactResolverFactory;
import ca.concordia.apifinder.entity.JarInfo;
import ca.concordia.apifinder.models.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;

import static ca.concordia.apifinder.util.PropertyReader.getProperty;

public class Utility {

	private static final Logger logger = LoggerFactory.getLogger(Utility.class);

    public static Set<JarInfo> getJarInfoSet(Set<Artifact> artifactDtoSet) {
        Set<JarInfo> jarInfoSet = new HashSet<>();

        for (Artifact artifactDto: artifactDtoSet) {
            jarInfoSet.addAll(getJarInfoSet(artifactDto));
        }

        return jarInfoSet;
    }

    public static Set<JarInfo> getJarInfoSet(Artifact artifact) {
        ArtifactResolver artifactResolver = ArtifactResolverFactory.getArtifactResolver();
        Set<org.eclipse.aether.artifact.Artifact> artifactSet = artifactResolver.resolveArtifact(artifact);

        return convertToJarInfoSet(artifactSet);
    }

    public static List<String> getFiles(String directory, String type) {
        List<String> jarFiles = new ArrayList<>();
        File dir = new File(directory);
        File[] files = dir.listFiles();

        if (Objects.nonNull(files)) {
            for (File file : files) {
                if (file.isDirectory()) {
                    jarFiles.addAll(getFiles(file.getAbsolutePath(), type));
                } else if (file.getAbsolutePath().toLowerCase().endsWith((type.toLowerCase()))) {
                    jarFiles.add(file.getAbsolutePath());
                }
            }
        }

        return jarFiles;
    }

    public static Path getProjectPath(String projectName) {
        Path pathToCorpus = Path.of(getProperty("corpus.path"));

        return pathToCorpus.resolve("Project_" + projectName);
    }

    public static <T> void removeSingleElementFromCollection(Collection<T> collection, Predicate<T> condition) {
        for (T element: collection) {
            if (condition.test(element)) {
                collection.remove(element);
                break;
            }
        }
    }

    public static Integer getMajorJavaVersion(String javaVersion) {
        if (Objects.isNull(javaVersion)) {
            javaVersion = "8";
        }

        Integer majorJavaVersion = parseJavaVersion(javaVersion);

        if (Objects.isNull(majorJavaVersion)) {
            logger.error("Could not find Java Version, Version: {}", javaVersion);
            majorJavaVersion = Utility.parseJavaVersion("8");
        }

        return majorJavaVersion;
    }

    public static String convertJavaVersion(String javaVersion) {
        return Objects.nonNull(javaVersion) && javaVersion.startsWith("1.")
                ? javaVersion.replace("1.", "")
                : javaVersion;
    }

    private static Integer parseJavaVersion(String javaVersion) {
        try {
            javaVersion = convertJavaVersion(javaVersion);

            double version = Double.parseDouble(javaVersion);

            if (version >= 9) {
                return (int) version;
            } else {
                return (int) version;
            }
        } catch (NumberFormatException e) {
            logger.error("Error", e);
        }

        return null;
    }

    private static Set<JarInfo> convertToJarInfoSet(Set<org.eclipse.aether.artifact.Artifact> artifactSet) {
        Set<JarInfo> jarInfoSet = new HashSet<>();

        for (org.eclipse.aether.artifact.Artifact artifact: artifactSet) {
            JarFile jarFile = getJarFile(artifact.getFile());

            if (Objects.nonNull(jarFile)) {
                JarInfo jarInfo = new JarInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), jarFile);
                jarInfoSet.add(jarInfo);
            }
        }

        return jarInfoSet;
    }

    private static JarFile getJarFile(File file) {
        if (file.getName().endsWith(".jar")) {
            try {
                return new JarFile(file);

            } catch (IOException e) {
                logger.error("Error", e);
            }
        }

        return null;
    }

}
