package ca.concordia.jaranalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
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

		/*
		 * ArrayList<JarInfo> allJars = new ArrayList<JarInfo>();
		 * allJars.addAll(jarInfosFromRepository);
		 * allJars.addAll(jarInfosFromPom); for (JarInfo jarInfo : allJars) {
		 * for (ClassInfo classInfo : jarInfo.getClasses()) { if
		 * (classInfo.getSuperClassInfo() == null &&
		 * !classInfo.getSuperClassName().isEmpty()) { for (JarInfo jar :
		 * allJars) { for (ClassInfo cls : jar.getClasses()) { if
		 * (cls.getQualifiedName().equals( classInfo.getSuperClassName())) {
		 * classInfo.setSuperClassInfo(cls); } } } } } }
		 */
	}

	public List<MethodInfo> findAllMethods(List<String> imports,
			String methodName, int numberOfParameters) {
		JarAnalyzer analyzer = new JarAnalyzer();
		List<MethodInfo> matchedMethods = new ArrayList<MethodInfo>();
		List<String> importStatements = new ArrayList<String>(imports);

		if (methodName.contains(".")) {
			importStatements.add(methodName);
		}

		for (String importedPackage : importStatements) {
			findMatchingMethods(jarInfosFromRepository, matchedMethods,
					importedPackage, methodName, numberOfParameters);
			if (matchedMethods.size() > 0)
				return matchedMethods;

			findMatchingMethods(jarInfosFromPom, matchedMethods,
					importedPackage, methodName, numberOfParameters);
			if (matchedMethods.size() > 0)
				return matchedMethods;

			/*
			 * JarInfo jarInfo = analyzer.findAndAnalyzeJar(importedPackage); if
			 * (jarInfo == null) continue;
			 * 
			 * findMatchingMethod(jarInfo, matchedMethods, importedPackage,
			 * methodName, numberOfParameters);
			 */

		}
		return matchedMethods;
	}

	private void findMatchingMethods(List<JarInfo> jarInfos,
			List<MethodInfo> matchedMethods, String importedPackage,
			String methodName, int numberOfParameters) {
		for (JarInfo jarInfo : jarInfos) {
			if (jarInfo == null)
				continue;
			findMatchingMethod(jarInfo, matchedMethods, importedPackage,
					methodName, numberOfParameters);
		}
	}

	/*
	 * private void findMatchingMethod(JarInfo jarInfo, List<MethodInfo>
	 * matchedMethods, String importedPackage, String methodName, int
	 * numberOfParameters) { for (MethodInfo methodInfo :
	 * jarInfo.getAllMethods()) { if
	 * (methodInfo.getQualifiedClassName().contains(importedPackage)) { if
	 * (methodInfo.getName().equals(methodName) &&
	 * methodInfo.getArgumentTypes().length == numberOfParameters)
	 * matchedMethods.add(methodInfo); } } }
	 */

	private void findMatchingMethod(JarInfo jarInfo,
			List<MethodInfo> matchedMethods, String importedPackage,
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

	public List<ClassInfo> findAllTypes(List<String> imports, String typeName) {
		JarAnalyzer analyzer = new JarAnalyzer();
		List<ClassInfo> matchedTypes = new ArrayList<ClassInfo>();

		List<String> importStatements = new ArrayList<String>(imports);

		if (typeName.contains(".")) {
			importStatements.add(typeName);
		}

		for (String importedPackage : importStatements) {
			findMatchingTypes(jarInfosFromRepository, matchedTypes,
					importedPackage, typeName);
			if (matchedTypes.size() > 0)
				return matchedTypes;

			findMatchingTypes(jarInfosFromPom, matchedTypes, importedPackage,
					typeName);
			if (matchedTypes.size() > 0)
				return matchedTypes;

			/*
			 * JarInfo jarInfo = analyzer.findAndAnalyzeJar(importedPackage); if
			 * (jarInfo == null) continue;
			 * 
			 * findMatchingType(jarInfo, matchedTypes, importedPackage,
			 * typeName);
			 */
		}
		return matchedTypes;
	}

	private void findMatchingTypes(List<JarInfo> jarInfos,
			List<ClassInfo> matchedTypes, String importedPackage,
			String typeName) {
		for (JarInfo jarInfo : jarInfos) {
			if (jarInfo == null)
				continue;
			findMatchingType(jarInfo, matchedTypes, importedPackage, typeName);
		}
	}

	private void findMatchingType(JarInfo jarInfo,
			List<ClassInfo> matchedTypes, String importedPackage,
			String typeName) {
		for (ClassInfo classInfo : jarInfo.getClasses()) {
			if (classInfo.getQualifiedName().contains(importedPackage)) {
				if (classInfo.getName().equals(typeName)) {
					matchedTypes.add(classInfo);
				}
			}
		}

	}

	public List<FieldInfo> findAllFields(List<String> imports, String fieldName) {
		JarAnalyzer analyzer = new JarAnalyzer();
		List<FieldInfo> matchedFields = new ArrayList<FieldInfo>();
		
		List<String> importStatements = new ArrayList<String>(imports);

		if (fieldName.contains(".")) {
			importStatements.add(fieldName);
		}

		
		for (String importedPackage : importStatements) {
			findMatchingFields(jarInfosFromRepository, matchedFields,
					importedPackage, fieldName);
			if (matchedFields.size() > 0)
				return matchedFields;

			findMatchingFields(jarInfosFromPom, matchedFields, importedPackage,
					fieldName);
			if (matchedFields.size() > 0)
				return matchedFields;

			/*
			 * JarInfo jarInfo = analyzer.findAndAnalyzeJar(importedPackage); if
			 * (jarInfo == null) continue;
			 * 
			 * findMatchingField(jarInfo, matchedFields, importedPackage,
			 * fieldName);
			 */
		}
		return matchedFields;
	}

	private void findMatchingFields(List<JarInfo> jarInfos,
			List<FieldInfo> matchedFields, String importedPackage,
			String fieldName) {
		for (JarInfo jarInfo : jarInfos) {
			if (jarInfo == null)
				continue;
			findMatchingField(jarInfo, matchedFields, importedPackage,
					fieldName);
		}
	}

	private void findMatchingField(JarInfo jarInfo,
			List<FieldInfo> matchedFields, String importedPackage,
			String fieldName) {
		for (ClassInfo classInfo : jarInfo.getClasses()) {
			if (classInfo.getQualifiedName().contains(importedPackage)) {
				matchedFields.addAll(classInfo.getFieldsByName(fieldName));
			}
		}
	}
}
