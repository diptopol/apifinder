package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.models.JarInformation;
import ca.concordia.jaranalyzer.util.artifactextraction.Artifact;
import org.apache.maven.shared.invoker.*;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

import static ca.concordia.jaranalyzer.util.FileUtils.deleteDirectory;
import static ca.concordia.jaranalyzer.util.FileUtils.readFile;
import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static java.util.stream.Collectors.toSet;

/**
 * @author Diptopol
 * @since 12/27/2020 5:17 PM
 */
public class ExternalJarExtractionUtility {

    private static final Logger logger = LoggerFactory.getLogger(ExternalJarExtractionUtility.class);

    public static JarInformation getJarInfo(Artifact artifact) {
        String url = "https://repo1.maven.org/maven2/" + artifact.getGroupId().replace('.', '/') + "/"
                + artifact.getArtifactId() + "/" + artifact.getVersion()
                + "/" + artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";

        return getAsJarInformation(url, artifact);
    }

    private static JarInformation getAsJarInformation(JarFile jarFile, Artifact artifact) {
        if (jarFile == null)
            return null;

        return new JarInformation(jarFile, artifact);
    }

    private static JarInformation getAsJarInformation(String url, Artifact artifact) {
        JarFile jarFile = DownloadJar(url);
        return getAsJarInformation(jarFile, artifact);
    }

    private static JarFile DownloadJar(String jarUrl) {
        String jarName = Utility.getJarName(jarUrl);
        String jarsPath = PropertyReader.getProperty("jars.path");

        String jarLocation = jarsPath + '/' + jarName;
        JarFile jarFile = null;
        File file = new File(jarLocation);
        if (file.exists()) {
            try {
                return new JarFile(new File(jarLocation));
            } catch (IOException e) {
                logger.error("Cannot open jar: " + jarLocation, e);
            }
        }
        try {
            Utility.downloadUsingStream(jarUrl, jarLocation);
        } catch (IOException e) {
            logger.error("Could not download jar: " + jarUrl, e);
        }

        try {
            jarFile = new JarFile(new File(jarLocation));
        } catch (IOException e) {
            logger.error("Cannot open jar: " + jarLocation, e);
        }

        return jarFile;
    }

}
