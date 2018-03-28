package ca.concordia.jaranalyzer;

import java.util.List;
import java.util.Set;

public interface APIFinder {
	Set<MethodInfo> findAllMethods (List<String> imports, String methodName, int numberOfParameters);
	Set<ClassInfo> findAllTypes (List<String> imports, String typeName);
	Set<FieldInfo> findAllFields (List<String> imports, String fieldName);
}
