package ca.concordia.jaranalyzer;

import java.util.List;

public interface MethodFinder {
	List<MethodInfo> findAll (List<String> imports, String methodName, int numberOfParameters);
}
