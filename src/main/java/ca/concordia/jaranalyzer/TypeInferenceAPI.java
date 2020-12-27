package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.util.FileUtils;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.util.Utility;
import com.jasongoodwin.monads.Try;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.maven.shared.invoker.*;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

import static ca.concordia.jaranalyzer.util.FileUtils.deleteDirectory;
import static ca.concordia.jaranalyzer.util.FileUtils.readFile;
import static ca.concordia.jaranalyzer.util.GitUtil.tryCloningRepo;
import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;
import static ca.concordia.jaranalyzer.util.Utility.listOfJavaProjectLibraryFromEffectivePom;
import static java.util.stream.Collectors.toSet;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceAPI.class);

    private static TinkerGraph tinkerGraph;
    private static JarAnalyzer jarAnalyzer;

    public static Path pathToCorpus;
    public static String mavenHome;


    static {
//        tinkerGraph = TinkerGraph.open();
//        jarAnalyzer = new JarAnalyzer(tinkerGraph);

//        if (!Files.exists(getJarStoragePath())) {
//            createClassStructureGraphForJavaJars();
//            storeClassStructureGraph();
//        } else {
//            loadClassStructureGraph();
//        }

        pathToCorpus = Path.of(getProperty("PathToCorpus"));
        mavenHome = getProperty("mavenHome");

    }


    public static List<String> getQualifiedClassName(String className) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Class")
                .has("Name", className)
                .<String>values("QName")
                .toList();
    }

    private static void createClassStructureGraphForJavaJars() {
        String javaJarDirectory = getProperty("java.jar.directory");
        String javaVersion = getProperty("java.version");

        logger.info("Java Jar Directory: {}", javaJarDirectory);
        logger.info("Java Version: {}", javaVersion);

        if (javaJarDirectory != null) {
            List<String> jarFiles = Utility.getFiles(javaJarDirectory, "jar");
            for (String jarLocation : jarFiles) {
                try {
                    Path path = Paths.get(jarLocation);
                    if (Files.exists(path)) {
                        JarFile jarFile = new JarFile(new File(jarLocation));
                        jarAnalyzer.jarToGraph(jarFile, path.getFileName().toString(), "Java", javaVersion);
                    }
                } catch (Exception e) {
                    logger.error("Could not open the JAR", e);
                }
            }

        }
    }

    private static Path pathToProjectFolder(String projectName) {
        return pathToCorpus.resolve("Project_" + projectName);
    }


    private static Optional<String> generateEffectivePom(String commitID, final String projectName, String cloneLink) {

        var pathToProject = pathToProjectFolder(projectName);

        Repository repo;
        if (Files.exists(pathToProject))
            repo = Try.ofFailable(() -> Git.open(pathToProject.resolve(projectName).toFile()))
                    .orElseThrow(() -> new RuntimeException("Could not open " + projectName)).getRepository();
        else repo = tryCloningRepo(projectName, cloneLink, pathToProject)
                .orElseThrow(() -> new RuntimeException("Could not clone" + projectName)).getRepository();


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
                    System.out.println("Build Failed");
                    System.out.println("Could not generate effective pom");
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

    public static Set<Tuple3<String, String, String>> getDependenciesFromEffectivePom(String commit, String projectName, String cloneLink) {

        Set<String> deps = generateEffectivePom(commit, projectName, cloneLink)
                .map(Utility::listOfJavaProjectLibraryFromEffectivePom)
                .orElse(new HashSet<>());

        return deps.stream().map(x -> x.split(":"))
                .filter(x -> x.length == 3)
                .map(dep -> Tuple.of(dep[0], dep[1], dep[2]))
                .collect(toSet());
    }

    private static void storeClassStructureGraph() {
        logger.info("storing graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    private static void loadClassStructureGraph() {
        logger.info("loading graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }
}
