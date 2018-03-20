package ca.concordia.jaranalyzer;

import java.util.ArrayList;
import java.util.List;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		APIFinder mf = new APIFinderImpl("");
		List<String> imports = new ArrayList<String>();
		imports.add("org.specs.runner.JUnit");
		List<MethodInfo> matches = mf.findAllMethods(imports, "initialize", 0);
		System.out.println(matches);
	}

}
