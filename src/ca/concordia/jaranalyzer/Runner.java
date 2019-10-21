package ca.concordia.jaranalyzer;



import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;


public class Runner {
    public static void main(String args[]) throws Exception {
        if(!Files.exists(Paths.get("D:\\MyProjects\\apache-tinkerpop-gremlin-server-3.4.3\\data\\JavaJars.kryo"))){
        //    APIFinderImpl.analyseJavaJars("D:\\MyProjects\\jdk1.8.0_222-windows-x64\\","8");
        }
        final TinkerGraph newGraph = TinkerGraph.open();
        newGraph.traversal().io("D:\\MyProjects\\JarAnalyzer\\resources\\JavaJars.kryo")
                .with(IO.reader,IO.gryo)
                .read().iterate();
        APIFinderImpl apiF = new APIFinderImpl();
        System.out.println(apiF.findTypeInJars(Arrays.asList("java.util.concurrent.atomic"),"AtomicLong", newGraph.traversal()));
        System.out.println(apiF.findTypeInJars(Arrays.asList("java.util.concurrent.atomic"),"LongAdder", newGraph.traversal()));
        newGraph.clear();

        System.out.println();
    }
}

