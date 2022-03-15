package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.util.artifactextraction.Artifact;
import ca.concordia.jaranalyzer.util.artifactextraction.ArtifactResolver;
import ca.concordia.jaranalyzer.util.artifactextraction.ArtifactResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

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

        /*
         * Currently, we are only interested in the artifact (if the type is Jar), otherwise we should fetch all
         * the dependent jars as well.
         */
        if (Artifact.JAR_TYPE.equals(artifact.getType())) {
            artifactSet = filterOutDependencyArtifact(artifactSet, Collections.singleton(artifact));
        }

        return convertToJarInfoSet(artifactSet);
    }

    public static String getJarName(String url) {
        url = url.replace('\\', '/');
        return url.substring(url.lastIndexOf('/') + 1);
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

    public static Path getJarStoragePath() {
        return Path.of(PropertyReader.getProperty("jar.storage.directory"))
                .resolve(PropertyReader.getProperty("jar.storage.filename"));
    }

    private static Set<org.eclipse.aether.artifact.Artifact> filterOutDependencyArtifact(Set<org.eclipse.aether.artifact.Artifact> artifactSet,
                                                                                         Set<Artifact> artifactDtoSet) {

        Set<String> artifactNameList = artifactDtoSet.stream()
                .map(artifactDto -> String.join(":", artifactDto.getGroupId(), artifactDto.getArtifactId(),
                        artifactDto.getVersion()))
                .collect(Collectors.toSet());

        return artifactSet.stream().filter(artifact -> artifactNameList.contains(String.join(":",
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion())))
                .collect(Collectors.toSet());
    }

    private static Set<JarInfo> convertToJarInfoSet(Set<org.eclipse.aether.artifact.Artifact> artifactSet) {
        Set<JarInfo> jarInfoSet = new HashSet<>();

        for (org.eclipse.aether.artifact.Artifact artifact: artifactSet) {
            JarFile jarFile = getJarFile(artifact.getFile());

            if (Objects.nonNull(jarFile)) {
                jarInfoSet.add(new JarInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), jarFile));
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
