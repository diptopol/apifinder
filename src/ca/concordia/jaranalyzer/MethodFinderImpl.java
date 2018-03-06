package ca.concordia.jaranalyzer;

import java.util.ArrayList;

public class MethodFinderImpl implements MethodFinder {

	@Override
	public ArrayList<MethodInfo> findAll(ArrayList<String> imports,
			String methodName, int numberOfParameters) {
		JarAnalyzer analyzer = new JarAnalyzer();
		ArrayList<MethodInfo> matchedMethods = new ArrayList<MethodInfo>();
		for (String importedPackage : imports) {
			JarInfo jarInfo = analyzer.findAndAnalyzeJar(importedPackage);
			if(jarInfo == null)
				continue;
			for (MethodInfo methodInfo : jarInfo.getAllMethods()) {
				if(methodInfo.getClassName().equals(importedPackage)){
					if(methodInfo.getName().equals(methodName) && methodInfo.getArgumentTypes().length == numberOfParameters)
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
