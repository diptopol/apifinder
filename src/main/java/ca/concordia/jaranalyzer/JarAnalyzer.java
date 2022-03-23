package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractor;
import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractorResolver;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.ClassInfo;
import ca.concordia.jaranalyzer.models.PackageInfo;
import ca.concordia.jaranalyzer.models.JarInfo;
import ca.concordia.jaranalyzer.util.Utility;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;


public class JarAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(JarAnalyzer.class);

    /*
     * After removal of APIFinderImpl, this instance will be made private
     */
    public TinkerGraph graph;

    public JarAnalyzer() {
        graph = TinkerGraph.open();
        graph.createIndex("Kind", Vertex.class);
    }

    public JarAnalyzer(TinkerGraph graph) {
        this.graph = graph;
        this.graph.createIndex("Kind", Vertex.class);
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

            for (ClassInfo c : p.getClasses()) {
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

                if (!c.getSuperClassName().isEmpty()) {
                    Vertex superClass = graphTraversalSource.addV()
                            .property("Kind", "SuperClass")
                            .property("Name", c.getSuperClassName())
                            .next();

                    graphTraversalSource.addE("extends").from(cls).to(superClass).iterate();
                }

                innerClassQNameMap.put(cls.id(), c.getInnerClassNameList());

                c.getSuperInterfaceNames()
                        .forEach(e -> {
                            Vertex superInterface = graphTraversalSource.addV()
                                    .property("Kind", "SuperInterface")
                                    .property("Name", e).next();

                            graphTraversalSource.addE("implements").from(cls).to(superInterface).iterate();
                        });

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

    public Set<Artifact> loadExternalJars(String commitId, String projectName, Git git) {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver(commitId, projectName, git);
        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> jarArtifactInfoSet = extractor.getDependentArtifactSet();

        Set<Artifact> jarArtifactInfoSetForLoad = jarArtifactInfoSet.stream()
                .filter(jarArtifactInfo -> !isJarExists(jarArtifactInfo))
                .collect(Collectors.toSet());

        if (!jarArtifactInfoSetForLoad.isEmpty()) {
            Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(jarArtifactInfoSet);
            toGraph(jarInfoSet);
            storeClassStructureGraph();
        }

        return jarArtifactInfoSet;
    }

    public void loadJar(Artifact artifact) {
        if (!isJarExists(artifact)) {
            Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(artifact);

            Set<JarInfo> jarInfoSetForLoad = jarInfoSet.stream()
                    .filter(jarInfo -> !isJarExists(jarInfo.getArtifact()))
                    .collect(Collectors.toSet());

            if (!jarInfoSetForLoad.isEmpty()) {
                toGraph(jarInfoSetForLoad);
                storeClassStructureGraph();
            }
        }
    }

    public void storeClassStructureGraph() {
        logger.info("storing graph");

        graph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    public void loadClassStructureGraph() {
        logger.info("loading graph");

        graph.traversal().io(getJarStoragePath().toString())
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
                        JarInfo jarInfo = new JarInfo(path.getFileName().toString(),"Java", javaVersion, jarFile);

                        toGraph(jarInfo);
                    }
                } catch (Exception e) {
                    logger.error("Could not open the JAR", e);
                }
            }
        }
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
