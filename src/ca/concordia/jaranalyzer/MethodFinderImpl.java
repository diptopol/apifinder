package ca.concordia.jaranalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import ca.concordia.jaranalyzer.util.Utility;

public class MethodFinderImpl implements MethodFinder {

	private List<JarInfo> jarInfosFromPom;
	private List<JarInfo> jarInfosFromRepository;
	private List<JarInfo> jarInfosFromJdk;

	public MethodFinderImpl(String projLocation) {
		JarAnalyzer analyzer = new JarAnalyzer();
		jarInfosFromJdk = new ArrayList<JarInfo>();
		jarInfosFromRepository = new ArrayList<JarInfo>();
		jarInfosFromPom = new ArrayList<JarInfo>();
		
		String javaHome = System.getProperty("java.home");
		String javaVersion = System.getProperty("java.version");
		List<String> jarFiles = Utility.getFiles(javaHome, ".jar");
		for (String jarLocation : jarFiles) {
			try {
				JarFile jarFile = new JarFile(new File(jarLocation));
				JarInfo jarInfo = analyzer.AnalyzeJar(jarFile, "JAVA", jarFile.getName(), javaVersion);
				jarInfosFromJdk.add(jarInfo);
			} catch (IOException e) {
				e.printStackTrace();
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
	}

	public ArrayList<MethodInfo> findAll(ArrayList<String> imports,
			String methodName, int numberOfParameters) {
		JarAnalyzer analyzer = new JarAnalyzer();
		ArrayList<MethodInfo> matchedMethods = new ArrayList<MethodInfo>();

		for (String importedPackage : imports) {
			for (JarInfo jarInfo : jarInfosFromPom) {
				if (jarInfo == null)
					continue;
				for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
					if (methodInfo.getQualifiedClassName().contains(importedPackage)) {
						if (methodInfo.getName().equals(methodName)
								&& methodInfo.getArgumentTypes().length == numberOfParameters)
							matchedMethods.add(methodInfo);
					}
				}
			}
			if (matchedMethods.size() > 0)
				return matchedMethods;

			for (JarInfo jarInfo : jarInfosFromRepository) {
				if (jarInfo == null)
					continue;
				for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
					if (methodInfo.getQualifiedClassName().contains(importedPackage)) {
						if (methodInfo.getName().equals(methodName)
								&& methodInfo.getArgumentTypes().length == numberOfParameters)
							matchedMethods.add(methodInfo);
					}
				}
			}
			if (matchedMethods.size() > 0)
				return matchedMethods;

			JarInfo jarInfo = analyzer.findAndAnalyzeJar(importedPackage);
			if (jarInfo == null)
				continue;

			for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
				if (methodInfo.getQualifiedClassName().contains(importedPackage)) {
					if (methodInfo.getName().equals(methodName)
							&& methodInfo.getArgumentTypes().length == numberOfParameters)
						matchedMethods.add(methodInfo);
				}
			}

		}
		return matchedMethods;
	}

	public ArrayList<MethodInfo> findAll(ArrayList<String> imports,
			String methodCall) {
		// TODO Auto-generated method stub
		return null;
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
}
