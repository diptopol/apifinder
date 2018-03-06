package ca.concordia.jaranalyzer;

import static org.junit.Assert.fail;

import java.util.ArrayList;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MethodFinder mf = new MethodFinderImpl();
		ArrayList<String> imports = new ArrayList<String>();
		imports.add("org.specs.runner.JUnit");
		ArrayList<MethodInfo> matches = mf.findAll(imports, "initialize", 0);
		System.out.println(matches);
	}

}
