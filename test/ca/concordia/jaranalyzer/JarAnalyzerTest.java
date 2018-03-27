package ca.concordia.jaranalyzer;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ca.concordia.jaranalyzer.util.Utility;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JarAnalyzerTest {

	@Test
	public void downloaderJar() {
		String jarUrl = "http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar";
		JarAnalyzer jarAnalyzer = new JarAnalyzer();
		jarAnalyzer.DownloadJar(jarUrl);

		String jarName = Utility.getJarName(jarUrl);
		String jarLocation = "C:/jars/" + jarName;
		File file = new File(jarLocation);
		boolean jarDownloaded = file.exists();

		assertEquals(true, jarDownloaded);

	}

	@Test
	public void analyzeJar() {
		try {
			String jarUrl = "http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar";
			JarAnalyzer jarAnalyzer = new JarAnalyzer();
			jarAnalyzer.DownloadJar(jarUrl);

			String jarName = Utility.getJarName(jarUrl);
			String jarLocation = "C:/jars/" + jarName;
			JarFile jarFile = new JarFile(new File(jarLocation));

			jarAnalyzer.AnalyzeJar(jarFile, "", "", "");
			assertEquals(true, true);

		} catch (IOException e) {
			e.printStackTrace();
			fail("Exception Thrown");
		}

	}

	@Test
	public void analyzeJarFromUrl() {
		String jarUrl = "http://repository.grepcode.com/java/eclipse.org/4.4.1/plugins/org.eclipse.jface_3.10.1.v20140813-1009.jar";
		JarAnalyzer jarAnalyzer = new JarAnalyzer();
		jarAnalyzer.AnalyzeJar(jarUrl, "", "", "");
		assertEquals(true, true);
	}

	@Test
	public void findJarByClassName() {
		JarAnalyzer jarAnalyzer = new JarAnalyzer();
		JarInfo jarInfo = jarAnalyzer
				.findAndAnalyzeJar("org.specs.runner.JUnit");
		if (jarInfo == null)
			fail("Exception Thrown");
	}

	@Test
	public void findMethod() {
		APIFinder mf = new APIFinderImpl(
				"A:\\Ref-Finder Experiment Projects\\jfreechart\\jfreechart-1.0.13");
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"org.jfree.chart.needle", 
				"java.awt.Graphics2D",
				"java.awt.geom.GeneralPath", "java.awt.geom.Point2D",
				"java.awt.geom.Rectangle2D", "java.io.Serializable"
				});
		List<MethodInfo> matches = mf.findAllMethods(imports, "getMinX", 0);
		// List<FieldInfo> Fieldmatches = mf.findAllFields(imports,
		// "DEFAULT_HORIZONTAL_ALIGNMENT");
		System.out.println(matches);
	}

	@Test
	public void analyzeJarsInFolder() {
		try {
			String location = "C:\\Program Files\\Java\\jdk1.8.0_112";
			JarAnalyzer jarProfiler = new JarAnalyzer();
			List<String> jarFiles = Utility.getFiles(location, ".jar");
			for (String jarLocation : jarFiles) {
				JarFile jarFile = new JarFile(new File(jarLocation));
				jarProfiler.AnalyzeJar(jarFile, "JAVA", jarFile.getName(),
						"1.8.0_112");
				System.out.println(jarLocation);
			}
			assertEquals(true, true);

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception Thrown");
		}
	}

	@Test
	public void analyzeJarsFromPOM() {
		try {
			String location = "A:\\";
			JarAnalyzer jarProfiler = new JarAnalyzer();
			List<String> pomFiles = Utility.getFiles(location, "pom.xml");
			for (String pomLocation : pomFiles) {
				try {
					String groupId;
					String artifactId;
					String version;
					File inputFile = new File(pomLocation);
					DocumentBuilderFactory dbFactory = DocumentBuilderFactory
							.newInstance();
					DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
					Document doc = dBuilder.parse(inputFile);
					doc.getDocumentElement().normalize();
					System.out.println("Root element :"
							+ doc.getDocumentElement().getNodeName());
					NodeList nList = doc.getElementsByTagName("dependency");
					System.out.println("----------------------------");
					for (int temp = 0; temp < nList.getLength(); temp++) {
						try {
							Node nNode = nList.item(temp);

							if (nNode.getNodeType() == Node.ELEMENT_NODE) {
								Element eElement = (Element) nNode;
								groupId = eElement
										.getElementsByTagName("groupId")
										.item(0).getTextContent();
								artifactId = eElement
										.getElementsByTagName("artifactId")
										.item(0).getTextContent();
								version = eElement
										.getElementsByTagName("version")
										.item(0).getTextContent();
								System.out.println("groupId : " + groupId);
								System.out
										.println("artifactId : " + artifactId);
								System.out.println("version : " + version);
								String jarUrl = "http://central.maven.org/maven2/"
										+ groupId
										+ "/"
										+ artifactId
										+ "/"
										+ version
										+ "/"
										+ artifactId
										+ "-"
										+ version + ".jar";
								JarInfo jarInfo = jarProfiler.AnalyzeJar(
										jarUrl, groupId, artifactId, version);
							}
						} catch (Exception e) {

						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				System.out.println(pomLocation);
			}
			assertEquals(true, true);

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception Thrown");
		}
	}
}
