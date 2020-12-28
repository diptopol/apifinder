package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.JarInformation;
import io.vavr.Tuple;
import io.vavr.Tuple3;
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

    private static Logger logger = LoggerFactory.getLogger(ExternalJarExtractionUtility.class);

    public static Set<Tuple3<String, String, String>> getDependenciesFromEffectivePom(String commit,
                                                                                      String projectName,
                                                                                      String cloneLink) {

        Set<String> deps = generateEffectivePom(commit, projectName, cloneLink)
                .map(Utility::listOfJavaProjectLibraryFromEffectivePom)
                .orElse(new HashSet<>());

        return deps.stream().map(x -> x.split(":"))
                .filter(x -> x.length == 3)
                .map(dep -> Tuple.of(dep[0], dep[1], dep[2]))
                .collect(toSet());
    }

    public static Set<Tuple3<String, String, String>> getDependenciesFromEffectivePom(String commit,
                                                                                      String projectName,
                                                                                      Repository repository) {
        Set<String> deps = generateEffectivePOM(commit, projectName, repository)
                .map(Utility::listOfJavaProjectLibraryFromEffectivePom)
                .orElse(new HashSet<>());

        return deps.stream().map(x -> x.split(":"))
                .filter(x -> x.length == 3)
                .map(dep -> Tuple.of(dep[0], dep[1], dep[2]))
                .collect(toSet());
    }

    private static Path pathToProjectFolder(String projectName) {
        Path pathToCorpus = Path.of(getProperty("PathToCorpus"));

        return pathToCorpus.resolve("Project_" + projectName);
    }

    private static Optional<String> generateEffectivePom(String commitID, final String projectName, String cloneLink) {
        Path pathToProject = pathToProjectFolder(projectName);

        Repository repo = GitUtil.getRepository(projectName, cloneLink, pathToProject);


        return generateEffectivePOM(commitID, projectName, repo);
    }

    private static Optional<String> generateEffectivePOM(String commitID, String projectName, Repository repo) {
        String mavenHome = getProperty("mavenHome");
        Path pathToProject = pathToProjectFolder(projectName);

        FileUtils.createFolderIfAbsent(pathToProject);

        Map<Path, String> poms = GitUtil.populateFileContents(repo, commitID, x -> x.endsWith("pom.xml"));
        Path p = pathToProject.resolve("tmp").resolve(commitID);
        FileUtils.materializeAtBase(p, poms);
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
                    logger.info("Build Failed");
                    logger.info("Could not generate effective pom");
                    return Optional.empty();
                }
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        String effectivePomPathContent = readFile(effectivePomPath);
        deleteDirectory(p);

        return Optional.of(effectivePomPathContent);
    }

    public static JarInformation getJarInfo(String groupId, String artifactId, String version) {
        JarInformation jarInformation;
        String url = "http://central.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
                + "-" + version + ".jar";

        jarInformation = getAsJarInformation(url, groupId, artifactId, version);

        if (jarInformation == null) {
            url = "http://central.maven.org/maven2/org/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
                    + "-" + version + ".jar";

            jarInformation = getAsJarInformation(url, groupId, artifactId, version);
        }

        if (jarInformation == null) {
            url = "http://central.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                    + "/" + artifactId + "-" + version + ".jar";

            jarInformation = getAsJarInformation(url, groupId, artifactId, version);
        }

        if (jarInformation == null) {
            url = "https://repo1.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                    + "/" + artifactId + "-" + version + ".jar";

            jarInformation = getAsJarInformation(url, groupId, artifactId, version);
        }

        return jarInformation;
    }

    private static JarInformation getAsJarInformation(JarFile jarFile, String groupId, String artifactId, String version) {
        if (jarFile == null)
            return null;

        return new JarInformation(jarFile, groupId, artifactId, version);
    }

    private static JarInformation getAsJarInformation(String url, String groupId, String artifactId, String version) {
        JarFile jarFile = DownloadJar(url);
        return getAsJarInformation(jarFile, groupId, artifactId, version);
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
