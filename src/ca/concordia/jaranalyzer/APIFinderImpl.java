package ca.concordia.jaranalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import ca.concordia.jaranalyzer.util.Utility;

public class APIFinderImpl implements APIFinder {

	private List<JarInfo> jarInfosFromPom;
	private List<JarInfo> jarInfosFromRepository;

	public APIFinderImpl(String projLocation) {
		JarAnalyzer analyzer = new JarAnalyzer();
		jarInfosFromRepository = new ArrayList<JarInfo>();
		jarInfosFromPom = new ArrayList<JarInfo>();
		
		
/*		try {
			GitUtil gitUtil = new GitUtil();
			Repository repo = gitUtil.openRepository(projLocation);
			String release = gitUtil.getNearestTag(repo);
			String releaseFolder = gitUtil.getRelease(repo, release);
			System.out.println(release);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}*/

		String javaHome = System.getProperty("java.home");
		String javaVersion = System.getProperty("java.version");
		if (javaHome != null) {
			List<String> jarFiles = Utility.getFiles(javaHome, ".jar");
			for (String jarLocation : jarFiles) {
				try {
					JarFile jarFile = new JarFile(new File(jarLocation));
					JarInfo jarInfo = analyzer.AnalyzeJar(jarFile, "JAVA",
							jarFile.getName(), javaVersion);
					jarInfosFromRepository.add(jarInfo);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (!projLocation.isEmpty()) {
			jarInfosFromPom = analyzer
					.analyzeJarsFromPOM(getAllPoms(projLocation));
			for (String jarPath : getAllJars(projLocation)) {
				JarFile jarFile;
				try {
					jarFile = new JarFile(new File(jarPath));
					jarInfosFromRepository.add(analyzer.AnalyzeJar(jarFile, "",
							"", ""));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		ArrayList<JarInfo> allJars = new ArrayList<JarInfo>();
		allJars.addAll(jarInfosFromRepository);
		allJars.addAll(jarInfosFromPom);
		for (JarInfo jarInfo : allJars) {
			for (ClassInfo classInfo : jarInfo.getClasses()) {
				if (classInfo.getSuperClassInfo() == null &&
						!classInfo.getSuperClassName().equals("java.lang.Object")) {
					for (JarInfo jar : allJars) {
						for (ClassInfo cls : jar.getClasses()) {
							if (cls.getQualifiedName().equals( classInfo.getSuperClassName())) {
								classInfo.setSuperClassInfo(cls);
							}
						}
					}
				}
			}
		} 
	}


	public List<JarInfo> getJarInfosFromPom() {
		return jarInfosFromPom;
	}

	public Set<MethodInfo> findAllMethods(List<String> imports,
										  String methodName, int numberOfParameters) {
		Set<MethodInfo> matchedMethods = new LinkedHashSet<MethodInfo>();
		List<String> importStatements = new ArrayList<String>(imports);

		if (methodName.contains(".")) {
			importStatements.add(methodName);
		}

		for (String importedPackage : importStatements) {
			findMatchingMethods(jarInfosFromRepository, matchedMethods,
					importedPackage, methodName, numberOfParameters);

			findMatchingMethods(jarInfosFromPom, matchedMethods,
					importedPackage, methodName, numberOfParameters);
		}
		return matchedMethods;
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

	private Set<String> getAllPoms(String projname) {
		Set<String> poms = new HashSet<String>();
		poms = getFiles(projname, "pom.xml");

		return poms;
	}

	private Set<String> getAllJars(String projname) {
		Set<String> jars = new HashSet<String>();
		jars = getFiles(projname, "jar");

		return jars;
	}

	private static Set<String> getFiles(String directory, String type) {
		Set<String> jarFiles = new HashSet<String>();
		File dir = new File(directory);
		if (dir.listFiles() != null)
			for (File file : dir.listFiles()) {
				if (file.isDirectory() && !file.getName().equals("bin")) {
					jarFiles.addAll(getFiles(file.getAbsolutePath(), type));
				} else if (file.getAbsolutePath().toLowerCase()
						.endsWith((type.toLowerCase()))) {
					jarFiles.add(file.getAbsolutePath());
				}
			}
		return jarFiles;
	}

	public Set<ClassInfo> findAllTypes(List<String> imports, String typeName) {
		Set<ClassInfo> matchedTypes = new LinkedHashSet<ClassInfo>();

		List<String> importStatements = new ArrayList<String>(imports);

		if (typeName.contains(".")) {
			importStatements.add(typeName);
		}

		for (String importedPackage : importStatements) {
			findMatchingTypes(jarInfosFromRepository, matchedTypes,
					importedPackage, typeName);

			findMatchingTypes(jarInfosFromPom, matchedTypes, importedPackage,
					typeName);
		}
		return matchedTypes;
	}

	private void findMatchingTypes(List<JarInfo> jarInfos,
			Set<ClassInfo> matchedTypes, String importedPackage,
			String typeName) {
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
		Set<FieldInfo> matchedFields = new LinkedHashSet<FieldInfo>();
		
		List<String> importStatements = new ArrayList<String>(imports);

		if (fieldName.contains(".")) {
			importStatements.add(fieldName);
		}
		
		for (String importedPackage : importStatements) {
			findMatchingFields(jarInfosFromRepository, matchedFields,
					importedPackage, fieldName);

			findMatchingFields(jarInfosFromPom, matchedFields, importedPackage,
					fieldName);
		}
		return matchedFields;
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
