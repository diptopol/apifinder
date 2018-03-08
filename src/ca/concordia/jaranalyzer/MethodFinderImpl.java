package ca.concordia.jaranalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

public class MethodFinderImpl implements MethodFinder {

	private List<JarInfo> jarInfosFromPom;
	private List<JarInfo> jarInfosFromRepository;
	
	public MethodFinderImpl(Set<String> allJars, Set<String> allPoms) {
		JarAnalyzer analyzer = new JarAnalyzer();
		jarInfosFromPom = analyzer.analyzeJarsFromPOM(allPoms);
		jarInfosFromRepository = new ArrayList<JarInfo>();
		
		for (String jarPath : allJars) {
			JarFile jarFile;
			try {
				jarFile = new JarFile(new File(jarPath));
				jarInfosFromRepository.add(analyzer.AnalyzeJar(jarFile, "", "",
						""));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	@Override
	public ArrayList<MethodInfo> findAll(ArrayList<String> imports,
			String methodName, int numberOfParameters) {
		JarAnalyzer analyzer = new JarAnalyzer();
		ArrayList<MethodInfo> matchedMethods = new ArrayList<MethodInfo>();

		
		for (String importedPackage : imports) {
			for (JarInfo jarInfo : jarInfosFromPom) {
				if (jarInfo == null)
					continue;
				for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
					if (methodInfo.getClassName().equals(importedPackage)) {
						if (methodInfo.getName().equals(methodName)
								&& methodInfo.getArgumentTypes().length == numberOfParameters)
							matchedMethods.add(methodInfo);
					}
				}	
			}
			if(matchedMethods.size() > 0 )
				return matchedMethods;
			
			for (JarInfo jarInfo : jarInfosFromRepository) {
				if (jarInfo == null)
					continue;
				for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
					if (methodInfo.getClassName().equals(importedPackage)) {
						if (methodInfo.getName().equals(methodName)
								&& methodInfo.getArgumentTypes().length == numberOfParameters)
							matchedMethods.add(methodInfo);
					}
				}	
			}
			if(matchedMethods.size() > 0 )
				return matchedMethods;
			
			
			JarInfo jarInfo = analyzer.findAndAnalyzeJar(importedPackage);
			if (jarInfo == null)
				continue;
			
			
			for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
				if (methodInfo.getClassName().equals(importedPackage)) {
					if (methodInfo.getName().equals(methodName)
							&& methodInfo.getArgumentTypes().length == numberOfParameters)
						matchedMethods.add(methodInfo);
				}
			}

		}
		return matchedMethods;
	}

	@Override
	public ArrayList<MethodInfo> findAll(ArrayList<String> imports,
			String methodCall) {
		// TODO Auto-generated method stub
		return null;
	}
}
