package ca.concordia.jaranalyzer;

import java.util.ArrayList;

public interface MethodFinder {
	ArrayList<MethodInfo> findAll (ArrayList<String> imports, String methodName, int numberOfParameters);

	ArrayList<MethodInfo> findAll (ArrayList<String> imports, String methodCall);
}
