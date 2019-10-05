package ca.concordia.jaranalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import ca.concordia.jaranalyzer.util.Utility;
import javassist.bytecode.AnnotationDefaultAttribute;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertyKeyStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.OrP;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.Repository;
import us.orgst.DBModels.JaranalysisApplication;
import us.orgst.DBModels.JaranalysisApplicationBuilder;
import us.orgst.DBModels.jaranalysis.jaranalysis.class_information.ClassInformation;

import static java.util.stream.Collectors.toList;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*;

public class APIFinderImpl implements APIFinder {


	private List<JarInfo> jarInfosFromPom;
	private List<JarInfo> jarInfosFromRepository;
	private List<Integer> jarIDs ;
	private boolean couldGenerateEffectivePom = false;

	private static final String username = "root";
	private static final String pwd = "anjaneya99";
	private GraphTraversalSource traverser;

	public APIFinderImpl(String pr){

	}

	public APIFinderImpl(GraphTraversalSource t) {
		this.traverser = t;
	}

	public static void analyzeJar( String groupId, String artifactID, String version, String path,String sha){
		new APIFinderImpl(artifactID,groupId,version, path,sha);
	}


	public static void persistJarsCommit(String groupId, String artifactID, String version, String sha){
		JaranalysisApplication app = new JaranalysisApplicationBuilder().build();
		JarAnalyzer analyzer = new JarAnalyzer(app);
		analyzer.getJarID(groupId, artifactID, version)
				.ifPresent(i -> analyzer.persistCommitJar(sha,i));
		app.close();
	}

	public static void analyseJavaJars(String path, String javaVersion){
		new APIFinderImpl(new JarAnalyzer(path),path,javaVersion);
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
						 params = md.parameters();
						g.addVertex("Kind","Method", "ReturnType",md.getReturnType2().toString(), "ParamType", )

					}
				}
			}else if(t instanceof EnumDeclaration){

			}
		}


		cuw.addAllImportsStatements(imports.stream().filter(x->!x.isOnDemand()).map(x->x.getName().getFullyQualifiedName()).collect(toList()));
		cuw.addAllImportsOnDemand(imports.stream().filter(x->x.isOnDemand()).map(x->x.getName().getFullyQualifiedName()).collect(toList()));
		cuw.setPackage(Optional.ofNullable(cu.getPackage())
				.map(PackageDeclaration::getName).map(Name::getFullyQualifiedName).orElse(""));
		cuw.setFile(fileName);
		cuw.addAllUsedTypes(getAllTypeGraphsIn(cu));
		final List<AbstractTypeDeclaration> typeDeclarations = cu.types();
		for(AbstractTypeDeclaration a : typeDeclarations){
			if(a instanceof TypeDeclaration){
				cuw.addClasses(getClassWorld((TypeDeclaration) a));
			}
			if (a instanceof EnumDeclaration){
				cuw.addClasses(getClassWorld((EnumDeclaration) a));
			}
		}
		return cuw.build();

	}


	public APIFinderImpl(String artifactID, String groupID, String version, String path, String commitID){
		try {
			JaranalysisApplication app = new JaranalysisApplicationBuilder().build();
			if(Files.exists(Paths.get(path)))
				System.out.println(path);

			JarAnalyzer analyzer = new JarAnalyzer(app);
            if(Files.exists(Paths.get(path))) {
                System.out.println(path);
                JarFile jarFile = new JarFile(new File(path));
                Integer jarID = analyzer.analyzeJar(jarFile, groupID, artifactID, version);
                if (jarID != null && jarID != -1)
                    analyzer.persistCommitJar(commitID, jarID);
            }
		}
		catch (IOException e) {
			System.out.println("Could not open the JAR");
		}
	}


	public Set<MethodInfo> findAllMethods(List<String> imports,
										  String methodName, int numberOfParameters) {
//		Set<MethodInfo> matchedMethods = new LinkedHashSet<MethodInfo>();
//		List<String> importStatements = new ArrayList<String>(imports);
//
//		if (methodName.contains(".")) {
//			importStatements.add(methodName);
//		}
//
//		for (String importedPackage : importStatements) {
//			findMatchingMethods(jarInfosFromRepository, matchedMethods,
//					importedPackage, methodName, numberOfParameters);
//
//			findMatchingMethods(jarInfosFromPom, matchedMethods,
//					importedPackage, methodName, numberOfParameters);
//		}
		return new LinkedHashSet<MethodInfo>();
	}

	private void findMatchingMethods(List<JarInfo> jarInfos,
			Set<MethodInfo> matchedMethods, String importedPackage,
			String methodName, int numberOfParameters) {
		for (JarInfo jarInfo : jarInfos) {
			if (jarInfo == null)
				continue;
			findMatchingMethod(jarInfo, matchedMethods, importedPackage,
					methodName, numberOfParameters);
		}
	}

	private void findMatchingMethod(JarInfo jarInfo,
			Set<MethodInfo> matchedMethods, String importedPackage,
			String methodName, int numberOfParameters) {
		for (ClassInfo classInfo : jarInfo.getClasses(importedPackage)) {
			matchedMethods.addAll(classInfo.getMethods(methodName,
					numberOfParameters));
		}
	}

	public Set<String> getAllPoms(String projname) {
		return getFiles(projname, "pom.xml");
	}


	private Set<String> getAllJars(String projname) {
		return  getFiles(projname, "jar");
	}

	public static Set<String> getFiles(String directory, String type) {
//		Set<String> jarFiles = new HashSet<String>();
//		File dir = new File(directory);
//		if (dir.listFiles() != null)
//			for (File file : dir.listFiles()) {
//				if (file.isDirectory() && !file.getName().equals("bin")) {
//					jarFiles.addAll(getFiles(file.getAbsolutePath(), type));
//				} else if (file.getAbsolutePath().toLowerCase()
//						.endsWith((type.toLowerCase()))) {
//					jarFiles.add(file.getAbsolutePath());
//				}
//			}
		return new HashSet<>();
	}



	public Set<ClassInformation> findAllTypes(List<String> qualifiers, String lookupType) throws Exception {
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

	private void findMatchingTypes(List<JarInfo> jarInfos, Set<ClassInfo> matchedTypes, String importedPackage, String typeName) {
		for (JarInfo jarInfo : jarInfos) {
			if (jarInfo == null)
				continue;
			findMatchingType(jarInfo, matchedTypes, importedPackage, typeName);
		}
	}

	private void findMatchingType(JarInfo jarInfo,
			Set<ClassInfo> matchedTypes, String importedPackage,
			String typeName) {
		for (ClassInfo classInfo : jarInfo.getClasses(importedPackage)) {
			if (classInfo.getName().equals(typeName)
					|| classInfo.getQualifiedName().equals(typeName)) {
				matchedTypes.add(classInfo);
			}
		}
	}

	public Set<FieldInfo> findAllFields(List<String> imports, String fieldName) {

		return new HashSet<>();
	}

	private void findMatchingFields(List<JarInfo> jarInfos,
			Set<FieldInfo> matchedFields, String importedPackage,
			String fieldName) {
		for (JarInfo jarInfo : jarInfos) {
			if (jarInfo == null)
				continue;
			findMatchingField(jarInfo, matchedFields, importedPackage,
					fieldName);
		}
	}

	private void findMatchingField(JarInfo jarInfo,
			Set<FieldInfo> matchedFields, String importedPackage,
			String fieldName) {
		for (ClassInfo classInfo : jarInfo.getClasses(importedPackage)) {
			matchedFields.addAll(classInfo.getFieldsByName(fieldName));
		}
	}
}
