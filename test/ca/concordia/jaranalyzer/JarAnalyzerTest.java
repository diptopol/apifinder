package ca.concordia.jaranalyzer;

import org.junit.BeforeClass;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JarAnalyzerTest {

	private static final String PROJECT_LOCATION = "C:\\Users\\tsantalis\\runtime-EclipseApplication\\jfreechart-1.0.13";
	private static APIFinder apiFinder;

	@BeforeClass
	public static void oneTimeSetUp() {
		apiFinder = new APIFinderImpl(PROJECT_LOCATION);
	}

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
	public void findMethodInSuperclassOfImport() {
		
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"org.jfree.chart.needle", 
				"java.awt.Graphics2D",
				"java.awt.geom.GeneralPath", "java.awt.geom.Point2D",
				"java.awt.geom.Rectangle2D", "java.io.Serializable"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "getMinX", 0);
		// List<FieldInfo> Fieldmatches = mf.findAllFields(imports,
		// "DEFAULT_HORIZONTAL_ALIGNMENT");
		System.out.println(matches);
	}

	@Test
	public void findMethodInSuperclassOfImportLocatedInAnotherJar() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"org.jfree.chart.demo",
				"java.awt.Color", "java.awt.Dimension",
				"java.awt.GradientPaint",
				"org.jfree.chart.ChartFactory",
				"org.jfree.chart.ChartPanel",
				"org.jfree.chart.JFreeChart",
				"org.jfree.chart.axis.CategoryAxis",
				"org.jfree.chart.axis.CategoryLabelPositions",
				"org.jfree.chart.axis.NumberAxis",
				"org.jfree.chart.plot.CategoryPlot",
				"org.jfree.chart.plot.PlotOrientation",
				"org.jfree.chart.renderer.category.BarRenderer",
				"org.jfree.data.category.CategoryDataset",
				"org.jfree.data.category.DefaultCategoryDataset",
				"org.jfree.ui.ApplicationFrame",
				"org.jfree.ui.RefineryUtilities"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "setPreferredSize", 1);
		System.out.println(matches);
	}

	@Test
	public void findMethodInNestedType() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", 
				"org.jfree.chart.axis",
				"java.io.Serializable",
				"java.util.ArrayList",
				"java.util.Calendar",
				"java.util.Collections",
				"java.util.Date",
				"java.util.GregorianCalendar",
				"java.util.Iterator",
				"java.util.List",
				"java.util.Locale",
				"java.util.SimpleTimeZone",
				"java.util.TimeZone"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "Segment", 1);
		System.out.println(matches);
	}

	@Test
	public void findClassConstructorWithQualifiedName() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "java.util.ArrayList", 0);
		System.out.println(matches);
	}

	@Test
	public void findInnerClassConstructorWithoutQualifiedName() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", "org.jfree.chart.axis"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "BaseTimelineSegmentRange", 2);
		System.out.println(matches);
	}

	@Test
	public void findMethod() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", "org.jfree.chart.block", 
				"java.awt.Graphics2D", "java.awt.geom.Rectangle2D", 
				"java.io.Serializable", "java.util.List", 
				"org.jfree.ui.Size2D",
				"org.jfree.data.Range"
				// this is the return type of method
				// org.jfree.chart.block.RectangleConstraint.getHeightRange()
				// and is supplied externally
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "constrain", 1);
		System.out.println(matches);
	}

	@Test
	public void findMethodInSuperInterfaceOfImport() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", 
				"org.jfree.chart.renderer.category",
				"java.awt.Graphics2D", 
				"java.awt.Paint", 
				"java.awt.Shape",
				"java.awt.Stroke", 
				"java.awt.geom.Line2D", 
				"java.awt.geom.Rectangle2D",
				"java.io.IOException", 
				"java.io.ObjectInputStream", 
				"java.io.ObjectOutputStream",
				"java.io.Serializable", 
				"java.util.List",
				"org.jfree.chart.LegendItem",
				"org.jfree.chart.axis.CategoryAxis",
				"org.jfree.chart.axis.ValueAxis",
				"org.jfree.chart.event.RendererChangeEvent",
				"org.jfree.chart.plot.CategoryPlot",
				"org.jfree.chart.plot.PlotOrientation",
				"org.jfree.data.category.CategoryDataset",
				"org.jfree.data.statistics.MultiValueCategoryDataset",
				"org.jfree.util.BooleanList",
				"org.jfree.util.BooleanUtilities",
				"org.jfree.util.ObjectUtilities", 
				"org.jfree.util.PublicCloneable",
				"org.jfree.util.ShapeUtilities"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "getRowKey", 1);
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
