package ca.concordia.jaranalyzer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;


import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.FieldInfo;
import ca.concordia.jaranalyzer.Models.JarInfo;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.Utility;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;

import static java.util.stream.Collectors.toList;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*;

public class APIFinderImpl  {


	private GraphTraversalSource traverser;

	public APIFinderImpl(GraphTraversalSource t) {
		this.traverser = t;
	}

	public APIFinderImpl(JarAnalyzer analyzer, String javaHome, String javaVersion, Stream<String> s){
		System.out.println(javaHome);
			if (javaHome != null) {
				List<String> jarFiles = Utility.getFiles(javaHome, "jar");
				System.out.println(jarFiles.size());
				for (String jarLocation : jarFiles) {
					try {
						Path path = Paths.get(jarLocation);
						if(Files.exists(path)) {
							JarFile jarFile = new JarFile(new File(jarLocation));
							analyzer.jarToGraph(jarFile, path.getFileName().toString(), "Java", javaVersion);
						}
					} catch (Exception  e) {
						e.printStackTrace();
						System.out.println(e.toString());
						System.out.println("Could not open the JAR");
					}
				}

			}



		analyzer.graph.traversal().io("D:\\MyProjects\\apache-tinkerpop-gremlin-server-3.4.3\\data\\JavaJars.kryo").write();
	}

	public static void getCompilationUnitWorld(CompilationUnit cu, String fileName, TinkerGraph g) throws JavaModelException {
		List<ImportDeclaration> imports = cu.imports();

		List<AbstractTypeDeclaration> types = cu.types();
		for(AbstractTypeDeclaration t : types){
			if(t instanceof TypeDeclaration){
				TypeDeclaration td = (TypeDeclaration)t;
				if(td.isInterface()){
					Vertex tp = g.addVertex("Kind", "Interface", "Name", td.getName().toString());
					imports.stream().map(i->i.getName().toString()).forEach(i -> {
						Vertex iv = g.addVertex("Kind", "Import", "Name", i);
						tp.addEdge("Imports", iv);
					});
					for(MethodDeclaration md: td.getMethods()){
//						 params = md.parameters();
						//g.addVertex("Kind","Method", "ReturnType",md.getReturnType2().toString(), "ParamType", )

					}
				}
			}else if(t instanceof EnumDeclaration){

			}
		}

//
//		cuw.addAllImportsStatements(imports.stream().filter(x->!x.isOnDemand()).map(x->x.getName().getFullyQualifiedName()).collect(toList()));
//		cuw.addAllImportsOnDemand(imports.stream().filter(x->x.isOnDemand()).map(x->x.getName().getFullyQualifiedName()).collect(toList()));
//		cuw.setPackage(Optional.ofNullable(cu.getPackage())
//				.map(PackageDeclaration::getName).map(Name::getFullyQualifiedName).orElse(""));
//		cuw.setFile(fileName);
//		cuw.addAllUsedTypes(getAllTypeGraphsIn(cu));
//		final List<AbstractTypeDeclaration> typeDeclarations = cu.types();
//		for(AbstractTypeDeclaration a : typeDeclarations){
//			if(a instanceof TypeDeclaration){
//				cuw.addClasses(getClassWorld((TypeDeclaration) a));
//			}
//			if (a instanceof EnumDeclaration){
//				cuw.addClasses(getClassWorld((EnumDeclaration) a));
//			}
//		}
//		return cuw.build();

	}


	public Set findAllTypes(List<String> qualifiers, String lookupType) throws Exception {
		traverser.V()
				.has("Kind","Class")
				.where(or(has("QName",TextP.within(qualifiers)),
						has("QName",TextP.within(qualifiers.stream().map(x->x +"."+ lookupType).collect(toList())))))
				.out("Declares").has("Kind","Method")
				.valueMap("Name","ReturnType","ParamType")
				.forEachRemaining(x ->{
					x.entrySet().forEach(z -> {
						System.out.println(z.getKey());
						System.out.println(z.getValue());
						System.out.println(z.getValue().getClass().toString());
					});
					System.out.println("---");
				} );
		return new HashSet<>();
	}

}
