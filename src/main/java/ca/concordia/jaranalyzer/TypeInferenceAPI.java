package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple3;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceAPI.class);

    private static TinkerGraph tinkerGraph;
    private static JarAnalyzer jarAnalyzer;

    static {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");

        tinkerGraph = TinkerGraph.open(configuration);
        jarAnalyzer = new JarAnalyzer(tinkerGraph);

        if (!Files.exists(getJarStoragePath())) {
            createClassStructureGraphForJavaJars();
            storeClassStructureGraph();
        } else {
            loadClassStructureGraph();
        }
    }

    public static Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom(commitId, projectName, repository);

        Set<Tuple3<String, String, String>> jarArtifactInfoSetForLoad = jarArtifactInfoSet.stream()
                .filter(jarArtifactInfo -> !isJarExists(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3))
                .collect(Collectors.toSet());

        jarArtifactInfoSetForLoad.forEach(jarArtifactInfo -> {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3);

            jarAnalyzer.toGraph(jarInformation);
        });

        if (jarArtifactInfoSetForLoad.size() > 0) {
            storeClassStructureGraph();
        }

        return jarArtifactInfoSet;
    }

    public static List<MethodInfo> getAllMethods(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters) {

        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion);

        List<String> importStaticList = importList.stream().filter(im -> im.startsWith("import static")).collect(Collectors.toList());
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());
        Set<String> qualifiedClassNameSet = new HashSet<>();

        List<String> packageNameList = nonImportStaticList.stream()
                .filter(im -> im.endsWith(".*"))
                .map(im -> im.substring(0, im.lastIndexOf(".*")).replace("import", "").trim())
                .collect(Collectors.toList());

        qualifiedClassNameSet.addAll(
                nonImportStaticList.stream()
                        .filter(im -> !im.endsWith(".*"))
                        .map(im -> im.replace("import", "").trim())
                        .collect(Collectors.toSet())
        );

        qualifiedClassNameSet.addAll(
                importStaticList.stream()
                        .map(im -> im.substring(0, im.lastIndexOf(".")).replace("import static", "").trim())
                        .collect(Collectors.toSet())
        );

        if (methodName.contains(".")) {
            qualifiedClassNameSet.add(methodName);

            /*
             * It is difficult to differentiate fully qualified class constructor and inner class constructor. Currently,
             * assuming that if there is only one dot exists, considering it as inner class constructor. Otherwise
             * considering method name as a fully qualified class constructor. This assumption will not always hold. If
             * better alternative solution is found, code will be updated.
             */
            methodName = methodName.substring(methodName.lastIndexOf(".") + 1);
        }

        qualifiedClassNameSet.addAll(
                tinkerGraph.traversal().V(jarVertexIds)
                        .out("ContainsPkg")
                        .has("Kind", "Package")
                        .has("Name", TextP.within(packageNameList))
                        .out("Contains")
                        .has("Kind", "Class")
                        .out("Declares")
                        .has("Kind", "Method")
                        .has("Name", methodName)
                        .in("Declares")
                        .<String>values("QName")
                        .toSet()
        );

        qualifiedClassNameSet.addAll(
                tinkerGraph.traversal().V(jarVertexIds)
                        .out("ContainsPkg").out("Contains")
                        .has("Kind", "Class")
                        .has("QName", TextP.within(qualifiedClassNameSet))
                        .out("extends", "implements")
                        .<String>values("Name")
                        .toSet()
        );

        qualifiedClassNameSet.addAll(
                tinkerGraph.traversal().V(jarVertexIds)
                        .out("ContainsPkg").out("Contains")
                        .has("Kind", "Class")
                        .has("QName", TextP.within(qualifiedClassNameSet))
                        .out("Declares")
                        .has("Kind", "InnerClass").<String>values("Name")
                        .toSet()
        );

        List<ClassInfo> classInfoList = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(qualifiedClassNameSet))
                .toStream()
                .map(ClassInfo::new)
                .collect(Collectors.toList());

        List<MethodInfo> methodInfoList = new ArrayList<>();

        for (ClassInfo classInfo : classInfoList) {
            List<MethodInfo> selectedMethodInfoList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("QName", classInfo.getQualifiedName())
                    .out("Declares")
                    .has("Kind", "Method")
                    .has("Name", methodName)
                    .toStream()
                    .map(v -> new MethodInfo(v, classInfo))
                    .filter(methodInfo -> methodInfo.getArgumentTypes().length == numberOfParameters)
                    .collect(Collectors.toList());

            methodInfoList.addAll(selectedMethodInfoList);
        }

        return methodInfoList;
    }

    private static Object[] getJarVertexIds(Set<Tuple3<String, String, String>> jarInformationSet, String javaVersion) {
        Set<Object> jarVertexIdSet = new HashSet<>();

        jarInformationSet.forEach(j -> {
            jarVertexIdSet.addAll(
                    tinkerGraph.traversal().V()
                            .has("Kind", "Jar")
                            .has("GroupId", j._1)
                            .has("ArtifactId", j._2)
                            .has("Version", j._3)
                            .toStream()
                            .map(Element::id)
                            .collect(Collectors.toSet())
            );
        });

        jarVertexIdSet.addAll(
                tinkerGraph.traversal().V()
                        .has("Kind", "Jar")
                        .has("ArtifactId", "Java")
                        .has("Version", javaVersion)
                        .toStream()
                        .map(Element::id)
                        .collect(Collectors.toSet())
        );

        return jarVertexIdSet.toArray(new Object[0]);
    }

    static List<String> getQualifiedClassName(String className) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Class")
                .has("Name", className)
                .<String>values("QName")
                .toList();
    }

    static JarAnalyzer getJarAnalyzer() {
        return jarAnalyzer;
    }

    static boolean isJarExists(String groupId, String artifactId, String version) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", groupId)
                .has("ArtifactId", artifactId)
                .has("Version", version)
                .toSet().size() > 0;
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

    static void storeClassStructureGraph() {
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
