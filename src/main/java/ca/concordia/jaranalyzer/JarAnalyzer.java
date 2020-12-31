package ca.concordia.jaranalyzer;


import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.PackageInfo;
import io.vavr.Tuple;
import io.vavr.Tuple3;
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
        Vertex jar = graph.addVertex("Kind", "Jar", "ArtifactId", j.getArtifactId(), "Version", j.getVersion(), "GroupId", j.getGroupId());
        for (PackageInfo p : j.getPackages()) {
            Vertex pkg = graph.addVertex("Kind", "Package", "Name", p.getName());
            jar.addEdge("ContainsPkg", pkg);
            for (ClassInfo c : p.getClasses()) {

                Vertex cls = graph.addVertex("Kind", "Class", "isAbstract", c.isAbstract(),
                        "isInterface", c.isInterface(), "isEnum", c.isEnum(), "Name", c.getName(),
                        "isPublic", c.isPublic(), "isPrivate", c.isPrivate(), "isProtected", c.isProtected(),
                        "QName", c.getQualifiedName());

                cls.property("typeDescriptor", c.getType().getDescriptor());

                pkg.addEdge("Contains", cls);

                if (!c.getSuperClassName().isEmpty())
                    cls.addEdge("extends", graph.addVertex("Kind", "SuperClass", "Name", c.getSuperClassName()));
                c.getSuperInterfaceNames().stream()
                        .forEach(e -> cls.addEdge("implements", graph.addVertex("Kind", "SuperInterface", "Name", e)));

                c.getMethods().stream().filter(x -> !x.isPrivate())
                        .forEach(m -> {
                            Vertex x = graph.addVertex("Kind", "Method", "Name", m.getName(),
                                    "isAbstract", m.isAbstract(), "isConstructor", m.isConstructor(),
                                    "isStatic", m.isStatic(), "isPublic", m.isPublic(), "isPrivate", m.isPrivate(),
                                    "isProtected", m.isProtected(), "isSynchronized", m.isSynchronized(),
                                    "className", m.getClassName());

                            x.property("returnTypeDescriptor", m.getReturnTypeAsType().getDescriptor());

                            for (Type type : m.getArgumentTypes()) {
                                x.property(VertexProperty.Cardinality.list, "argumentTypeDescriptorList", type.getDescriptor());
                            }

                            for (String thrownInternalClassName : m.getThrownInternalClassNames()) {
                                x.property(VertexProperty.Cardinality.set, "thrownInternalClassNames", thrownInternalClassName);
                            }

                            cls.addEdge("Declares", x);
                        });

                c.getFields().stream().filter(x -> !x.isPrivate())
                        .forEach(f -> cls.addEdge("Declares", graph.addVertex("Kind", "Field", "Name", f.getName(), "ReturnType", f.getType().toString())));

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
