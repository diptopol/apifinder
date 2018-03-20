package ca.concordia.jaranalyzer;

import java.util.ArrayList;
import java.util.List;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MethodFinder mf = new MethodFinderImpl("");
		List<String> imports = new ArrayList<String>();
		imports.add("org.specs.runner.JUnit");
		List<MethodInfo> matches = mf.findAll(imports, "initialize", 0);
		System.out.println(matches);
	}

}
