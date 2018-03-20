package ca.concordia.jaranalyzer;

import java.util.List;

public interface APIFinder {
	List<MethodInfo> findAllMethods (List<String> imports, String methodName, int numberOfParameters);
	List<ClassInfo> findAllTypes (List<String> imports, String typeName);
}
