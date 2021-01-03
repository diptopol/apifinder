package ca.concordia.jaranalyzer;


import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.PackageInfo;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.ofNullable;


public class JarAnalyzer {

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

    public void toGraph(JarInformation j) {
        GraphTraversalSource graphTraversalSource = graph.traversal();

        Vertex jar = graphTraversalSource.addV()
                .property("Kind", "Jar")
                .property("ArtifactId", j.getArtifactId())
                .property("Version", j.getVersion())
                .property("GroupId", j.getGroupId())
                .next();

        for (PackageInfo p : j.getPackages()) {
            Vertex pkg = graphTraversalSource.addV()
                    .property("Kind", "Package")
                    .property("Name", p.getName())
                    .next();

            graphTraversalSource.addE("ContainsPkg").from(jar).to(pkg).iterate();

            for (ClassInfo c : p.getClasses()) {
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
                        .property("typeDescriptor", c.getType().getDescriptor())
                        .next();

                graphTraversalSource.addE("Contains").from(pkg).to(cls).iterate();

                if (!c.getSuperClassName().isEmpty()) {
                    Vertex superClass = graphTraversalSource.addV()
                            .property("Kind", "SuperClass")
                            .property("Name", c.getSuperClassName())
                            .next();

                    graphTraversalSource.addE("extends").from(cls).to(superClass).iterate();
                }

                c.getInnerClassNameList()
                        .forEach(ic -> {
                            Vertex innerClass = graphTraversalSource.addV()
                                    .property("Kind", "InnerClass")
                                    .property("Name", ic)
                                    .next();

                            graphTraversalSource.addE("Declares").from(cls).to(innerClass).iterate();
                        });

                c.getSuperInterfaceNames()
                        .forEach(e -> {
                            Vertex superInterface = graphTraversalSource.addV()
                                    .property("Kind", "SuperInterface")
                                    .property("Name", e).next();

                            graphTraversalSource.addE("implements").from(cls).to(superInterface).iterate();
                        });

                c.getMethods().stream().filter(x -> !x.isPrivate())
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
                                    .property("className", m.getClassName())
                                    .property("returnTypeDescriptor", m.getReturnTypeAsType().getDescriptor())
                                    .next();

                            for (Type type : m.getArgumentTypes()) {
                                graphTraversalSource.V(x.id())
                                        .property(VertexProperty.Cardinality.list, "argumentTypeDescriptorList",
                                                type.getDescriptor()).next();
                            }

                            for (String thrownInternalClassName : m.getThrownInternalClassNames()) {
                                graphTraversalSource.V(x.id())
                                        .property(VertexProperty.Cardinality.set, "thrownInternalClassNames",
                                                thrownInternalClassName);
                            }

                            graphTraversalSource.addE("Declares").from(cls).to(x).iterate();
                        });

                c.getFields().stream().filter(x -> !x.isPrivate())
                        .forEach(f -> {
                            Vertex field = graphTraversalSource.addV()
                                    .property("Kind", "Field")
                                    .property("Name", f.getName())
                                    .property("ReturnType", f.getType().toString())
                                    .next();

                            graphTraversalSource.addE("Declares").from(cls).to(field).iterate();
                        });
            }
        }
    }

    public static Map<String, Tuple3<List<String>, List<String>, Boolean>> getHierarchyCompositionMap(JarInformation j) {

        return j.getPackages().stream().flatMap(x -> x.getClasses().stream())
                .collect(toMap(ClassInfo::getQualifiedName
                        , x -> Tuple.of(
                                concat(ofNullable(x.getSuperClassName()), x.getSuperInterfaceNames().stream()).collect(toList())
                                , x.getFields().stream().map(f -> f.getType().toString()).collect(toList())
                                , x.isEnum())));
    }


    public void jarToGraph(JarFile jarFile, String groupId, String artifactId, String version) {
        JarInformation ji = new JarInformation(jarFile, groupId, artifactId, version);
        toGraph(ji);
    }
}
