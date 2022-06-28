package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.JarAnalyzer;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.nio.file.Files;

import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 10/2/2021 9:26 PM
 */
public class TinkerGraphStorageUtility {

    private static TinkerGraph tinkerGraph;

    private static JarAnalyzer jarAnalyzer;

    public static TinkerGraph getTinkerGraph() {
        if (tinkerGraph == null) {
            Configuration configuration = new BaseConfiguration();
            configuration.addProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");

            tinkerGraph = TinkerGraph.open(configuration);

            jarAnalyzer = new JarAnalyzer(tinkerGraph, getJarStoragePath());

            if (!Files.exists(jarAnalyzer.getStorageFilePath())) {
                jarAnalyzer.createClassStructureGraphForJavaJars();
                jarAnalyzer.storeClassStructureGraph();
            } else {
                jarAnalyzer.loadClassStructureGraph();
            }
        }

        return tinkerGraph;
    }

    public static TinkerGraph getTinkerGraph(String storageFileName) {
        if (tinkerGraph == null) {
            Configuration configuration = new BaseConfiguration();
            configuration.addProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");

            tinkerGraph = TinkerGraph.open(configuration);

            jarAnalyzer = new JarAnalyzer(tinkerGraph, getJarStoragePath(storageFileName));

            if (!Files.exists(jarAnalyzer.getStorageFilePath())) {
                jarAnalyzer.createClassStructureGraphForJavaJars();
                jarAnalyzer.storeClassStructureGraph();
            } else {
                jarAnalyzer.loadClassStructureGraph();
            }
        }

        return tinkerGraph;
    }

    public static void resetTinkerGraph() {
        tinkerGraph = null;
    }

    public static JarAnalyzer getJarAnalyzer() {
        getTinkerGraph();

        return jarAnalyzer;
    }

    public static JarAnalyzer getJarAnalyzer(String storageFileName) {
        getTinkerGraph(storageFileName);

        return jarAnalyzer;
    }
}
