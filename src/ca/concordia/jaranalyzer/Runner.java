package ca.concordia.jaranalyzer;



import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;


public class Runner {
    public static void main(String args[]) throws Exception {
        if(!Files.exists(Paths.get("D:\\MyProjects\\apache-tinkerpop-gremlin-server-3.4.3\\data\\JavaJars.kryo"))){
        //    APIFinderImpl.analyseJavaJars("D:\\MyProjects\\jdk1.8.0_222-windows-x64\\","8");
        }
        GraphTraversalSource t = traversal().withRemote(DriverRemoteConnection.using("localhost",8182,"g"));
        APIFinderImpl apiF = new APIFinderImpl(t);
        apiF.findAllTypes(Arrays.asList("java.util.Map"),"Entry");
        t.V().has("Kind","Jar").valueMap("ArtifactId","Version","GroupId").toStream()
                .forEach(m -> m.forEach((k,v) -> System.out.println(k + "  " + v.toString())));
        t.close();
        System.out.println();
    }
}

