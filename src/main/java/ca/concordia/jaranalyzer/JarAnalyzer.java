package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractor;
import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractorResolver;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.ClassInfo;
import ca.concordia.jaranalyzer.models.JarInfo;
import ca.concordia.jaranalyzer.models.PackageInfo;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple2;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.api.Git;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;


public class JarAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(JarAnalyzer.class);

    private static Map<Integer, String> JAVA_STORAGE_PATH = new HashMap<>();

    static {
        JAVA_STORAGE_PATH.put(6, getProperty("java.6.jar.directory"));
        JAVA_STORAGE_PATH.put(8, getProperty("java.8.jar.directory"));
        JAVA_STORAGE_PATH.put(10, getProperty("java.10.jar.directory"));
        JAVA_STORAGE_PATH.put(11, getProperty("java.11.jar.directory"));
        JAVA_STORAGE_PATH.put(12, getProperty("java.12.jar.directory"));
        JAVA_STORAGE_PATH.put(13, getProperty("java.13.jar.directory"));

        JAVA_STORAGE_PATH = Collections.unmodifiableMap(JAVA_STORAGE_PATH);
    }

    /*
     * After removal of APIFinderImpl, this instance will be made private
     */
    public TinkerGraph graph;

    private Path storageFilePath;

    public JarAnalyzer() {
        graph = TinkerGraph.open();
        graph.createIndex("Kind", Vertex.class);
    }

    public JarAnalyzer(TinkerGraph graph, Path storageFilePath) {
        this.graph = graph;
        this.storageFilePath = storageFilePath;
        this.graph.createIndex("Kind", Vertex.class);
    }

    public Path getStorageFilePath() {
        return storageFilePath;
    }

    public void toGraph(Set<JarInfo> jarInfoSet) {
        for (JarInfo jarInfo : jarInfoSet) {
            toGraph(jarInfo);
        }
    }

    public void toGraph(JarInfo jarInfo) {
        GraphTraversalSource graphTraversalSource = graph.traversal();

        Vertex jar = graphTraversalSource.addV()
                .property("Kind", "Jar")
                .property("GroupId", jarInfo.getArtifact().getGroupId())
                .property("ArtifactId", jarInfo.getArtifact().getArtifactId())
                .property("Version", jarInfo.getArtifact().getVersion())
                .next();

        for (PackageInfo p : jarInfo.getPackageInfoCollection()) {
            Vertex pkg = graphTraversalSource.addV()
                    .property("Kind", "Package")
                    .property("Name", p.getName())
                    .next();

            graphTraversalSource.addE("ContainsPkg").from(jar).to(pkg).iterate();

            Map<Object, List<String>> innerClassQNameMap = new HashMap<>();

            for (ClassInfo c : p.getClassList()) {
                if (c.isAnonymousInnerClass()) {
                    continue;
                }

                Vertex cls = graphTraversalSource.addV()
                        .property("Kind", "Class")
                        .property("isAbstract", c.isAbstract())
                        .property("isInterface", c.isInterface())
                        .property("isEnum", c.isEnum())
                        .property("Name", c.getName())
                        .property("isPublic", c.isPublic())
                        .property("isPrivate", c.isPrivate())
                        .property("isProtected", c.isProtected())
                        .property("QName", c.getQualifiedName())
                        .property("packageName", c.getPackageName())
                        .property("isInnerClass", c.isInnerClass())
                        .property("isAnonymousInnerClass", c.isAnonymousInnerClass())
                        .property("typeDescriptor", c.getType().getDescriptor())
                        .property("signature", c.getSignature())
                        .next();

                graphTraversalSource.addE("Contains").from(pkg).to(cls).iterate();

                int superClassOrder = 0;
                if (!c.getSuperClassName().isEmpty()) {
                    Vertex superClass = graphTraversalSource.addV()
                            .property("Kind", "SuperClass")
                            .property("Name", c.getSuperClassName())
                            .property("Order", superClassOrder++)
                            .next();

                    graphTraversalSource.addE("extends").from(cls).to(superClass).iterate();
                }

                innerClassQNameMap.put(cls.id(), c.getInnerClassNameList());

                for (String superInterfaceName: c.getSuperInterfaceNames()) {
                    Vertex superInterface = graphTraversalSource.addV()
                            .property("Kind", "SuperInterface")
                            .property("Name", superInterfaceName)
                            .property("Order", superClassOrder++)
                            .next();

                    graphTraversalSource.addE("implements").from(cls).to(superInterface).iterate();
                }

                c.getMethods()
                        .forEach(m -> {
                            Vertex x = graphTraversalSource.addV()
                                    .property("Kind", "Method")
                                    .property("Name", m.getName())
                                    .property("isAbstract", m.isAbstract())
                                    .property("isConstructor", m.isConstructor())
                                    .property("isStatic", m.isStatic())
                                    .property("isPublic", m.isPublic())
                                    .property("isPrivate", m.isPrivate())
                                    .property("isProtected", m.isProtected())
                                    .property("isSynchronized", m.isSynchronized())
                                    .property("isFinal", m.isFinal())
                                    .property("isVarargs", m.isVarargs())
                                    .property("isBridgeMethod", m.isBridgeMethod())
                                    .property("className", m.getClassName())
                                    .property("signature", m.getSignature())
                                    .property("returnTypeDescriptor", m.getReturnTypeAsType().getDescriptor())
                                    .next();

                            if (Objects.nonNull(m.getInternalClassConstructorPrefix())) {
                                graphTraversalSource.V(x.id())
                                        .property("internalClassConstructorPrefix", m.getInternalClassConstructorPrefix())
                                        .next();
                            }

                            for (Type type : m.getArgumentTypes()) {
                                graphTraversalSource.V(x.id())
                                        .property(VertexProperty.Cardinality.list, "argumentTypeDescriptorList",
                                                type.getDescriptor()).next();
                            }

                            for (String thrownInternalClassName : m.getThrownInternalClassNames()) {
                                graphTraversalSource.V(x.id())
                                        .property(VertexProperty.Cardinality.set, "thrownInternalClassNames",
                                                thrownInternalClassName).next();
                            }

                            graphTraversalSource.addE("Declares").from(cls).to(x).iterate();
                        });

                c.getFields()
                        .forEach(f -> {
                            Vertex field = graphTraversalSource.addV()
                                    .property("Kind", "Field")
                                    .property("Name", f.getName())
                                    .property("isPublic", f.isPublic())
                                    .property("isPrivate", f.isPrivate())
                                    .property("isProtected", f.isProtected())
                                    .property("isStatic", f.isStatic())
                                    .property("returnTypeDescriptor", f.getType().getDescriptor())
                                    .property("signature", f.getSignature())
                                    .next();

                            graphTraversalSource.addE("Declares").from(cls).to(field).iterate();
                        });
            }

            innerClassQNameMap.forEach((classVertexId, innerClassQNameList) -> {
                if (!innerClassQNameList.isEmpty()) {
                    Vertex classVertex = graphTraversalSource.V(classVertexId).next();

                    innerClassQNameList.forEach(innerClassQName -> {
                        graphTraversalSource.V(jar.id())
                                .out("ContainsPkg")
                                .hasId(pkg.id())
                                .out("Contains")
                                .has("Kind", "Class")
                                .has("QName", innerClassQName)
                                .addE("ContainsInnerClass").from(classVertex)
                                .iterate();
                    });
                }
            });

        }
    }

    public Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, Git git) {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver(commitId, projectName, git);
        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        String javaVersion = extractor.getJavaVersion();
        Integer majorJavaVersion = Utility.getMajorJavaVersion(javaVersion);

        loadJavaPackage(majorJavaVersion);

        Set<Artifact> jarArtifactInfoSet = extractor.getDependentArtifactSet();
        storeArtifactSet(jarArtifactInfoSet);

        return new Tuple2<>(String.valueOf(majorJavaVersion), jarArtifactInfoSet);
    }

    public void loadJavaPackage(Integer majorJavaVersion) {
        if (!isJavaVersionExists(String.valueOf(majorJavaVersion))) {
            createClassStructureGraphForJavaJars(majorJavaVersion);
            storeClassStructureGraph();
        }
    }

    public void loadJar(Artifact artifact) {
        storeArtifactSet(Collections.singleton(artifact));
    }

    public void storeClassStructureGraph() {
        logger.info("storing graph");

        graph.traversal().io(this.storageFilePath.toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    public void loadClassStructureGraph() {
        logger.info("loading graph");

        graph.traversal().io(this.storageFilePath.toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }

    public void createClassStructureGraphForJavaJars() {
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
                        JarInfo jarInfo = new JarInfo(path.getFileName().toString(), "Java", javaVersion, jarFile);

                        toGraph(jarInfo);
                    }
                } catch (Exception e) {
                    logger.error("Could not open the JAR", e);
                }
            }
        }
    }

    public void createClassStructureGraphForJavaJars(Integer majorJavaVersion) {
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
                            JarInfo jarInfo = new JarInfo(path.getFileName().toString(), "Java", String.valueOf(majorJavaVersion), zipFile);

                            toGraph(jarInfo);
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
                            JarInfo jarInfo = new JarInfo(path.getFileName().toString(), "Java", String.valueOf(majorJavaVersion), jarFile);

                            toGraph(jarInfo);
                        }
                    } catch (Exception e) {
                        logger.error("Could not open the JAR", e);
                    }
                }
            }
        }
    }

    private void storeArtifactSet(Set<Artifact> artifactSet) {
        artifactSet = artifactSet.stream()
                .filter(artifact -> !isJarExists(artifact))
                .collect(Collectors.toSet());

        Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(artifactSet);
        boolean saveGraph = false;
        for (JarInfo jarInfo: jarInfoSet) {
            if (!isJarExists(jarInfo.getArtifact())) {
                saveGraph = true;
                toGraph(jarInfoSet);
            }
        }

        if (saveGraph) {
            storeClassStructureGraph();
        }
    }

    private boolean isJavaVersionExists(String javaVersion) {
        return graph.traversal().V()
                .has("Kind", "Jar")
                .has("ArtifactId", "Java")
                .has("Version", javaVersion)
                .toSet().size() > 0;
    }

    private boolean isJarExists(Artifact artifact) {
        return graph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", artifact.getGroupId())
                .has("ArtifactId", artifact.getArtifactId())
                .has("Version", artifact.getVersion())
                .toSet().size() > 0;
    }

}
