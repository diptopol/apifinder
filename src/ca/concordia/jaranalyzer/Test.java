package ca.concordia.jaranalyzer;

import static org.junit.Assert.fail;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		JarAnalyzer jarAnalyzer = new JarAnalyzer();
		JarInfo jarInfo = jarAnalyzer.findAndAnalyzeJar("org.specs.runner.JUnit");
		if(jarInfo != null)
		jarAnalyzer.SaveToDb(jarInfo);
	}

}
