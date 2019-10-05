package ca.concordia.jaranalyzer;

import us.orgst.DBModels.jaranalysis.jaranalysis.class_information.ClassInformation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface APIFinder {
	Set<MethodInfo> findAllMethods (List<String> imports, String methodName, int numberOfParameters);
	Set<ClassInformation> findAllTypes (List<String> imports, String typeName) throws Exception;
	Set<FieldInfo> findAllFields (List<String> imports, String fieldName);
}
