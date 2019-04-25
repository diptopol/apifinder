package ca.concordia.jaranalyzer;

import static java.util.stream.Collectors.toMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.jar.JarFile;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationManager;
import ca.concordia.jaranalyzer.dao.JarManager;
import ca.concordia.jaranalyzer.util.Utility;

public class JarAnalyzer {

	private String jarsPath;

	JarManager manager;

	public JarAnalyzer() {
		File file = new File("/Users/ameya/FinalResults/diffTools/jars/");
		file.mkdirs();
		jarsPath = file.getAbsolutePath();
		try {
			/*
			 * File db = new File("mydb.db"); if (!db.exists()) { File emptyDb =
			 * new File("empty.db"); Utility.copyFileUsingChannel(emptyDb, db);
			 * } manager = new JarManager();
			 */

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<JarInfo> analyzeJarsFromPOM(Set<String> pomFiles) {
		List<JarInfo> jarInfos = new ArrayList<>();

		Map<String, String> properties = pomFiles.stream()
				.map(p -> getDocument(new File(p)))
				.filter(Optional::isPresent)
				.flatMap(x -> getProperties(x.get()).entrySet().stream())
				.collect(toMap(Entry::getKey,Entry::getValue,mergeProperties));
		try {
			for (String pomLocation : pomFiles) {
					File inputFile = new File(pomLocation);
					Optional<Document> doc = getDocument(inputFile);
					if(doc.isPresent()) {
						System.out.println("Root element :" + doc.get().getDocumentElement().getNodeName());
						NodeList project = doc.get().getElementsByTagName("project");
						for (int temp = 0; temp < project.getLength(); temp++) {
							JarInfo foundJar = getJarInfoFromDependency(project.item(temp), properties);
							if (foundJar != null)
								jarInfos.add(foundJar);
						}
						NodeList nList = doc.get().getElementsByTagName("dependency");
						System.out.println("----------------------------");
						for (int temp = 0; temp < nList.getLength(); temp++) {
							JarInfo foundJar = getJarInfoFromDependency(nList.item(temp), properties);
							if (foundJar != null)
								jarInfos.add(foundJar);
						}
					}
				System.out.println(pomLocation);
				}
			}
		catch (Exception e) {
			e.printStackTrace();
		}
		return jarInfos;
	}

	private Optional<Document> getDocument(File inputFile)  {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(inputFile);
			doc.getDocumentElement().normalize();
			return Optional.ofNullable(doc);
		}catch (Exception e){
			e.printStackTrace();
			return Optional.empty();
		}
	}

	private Map<String, String> getProperties(Document doc){

		return Optional.ofNullable(doc.getElementsByTagName("properties"))
				.map(p -> p.item(0))
				.map(Node::getChildNodes)
				.filter(p->p.getLength()>0)
				.map(c -> IntStream.range(0, c.getLength()).boxed()
						.filter(i -> !c.item(i).getTextContent().trim().isEmpty() )
						.collect(toMap(i -> c.item(i).getNodeName(), i -> c.item(i).getTextContent(),
								mergeProperties)))
				.orElse(new HashMap<>());
	}

	private BinaryOperator<String> mergeProperties = (s1, s2) -> s1.equals(s2)? s1 : s1+"||"+s2;

	public JarInfo getJarInfoFromDependency(Node nNode, Map<String,String> properties) {
		String groupId;
		String artifactId;
		String version;
		try {
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				groupId = eElement.getElementsByTagName("groupId").item(0).getTextContent();
				artifactId = eElement.getElementsByTagName("artifactId").item(0).getTextContent();
				version = Optional.ofNullable(eElement.getElementsByTagName("version"))
						.map(x -> x.item(0))
						.map(Node::getTextContent)
						.map(x -> properties.entrySet().stream().filter(e ->x.contains( e.getKey()))
								.map(Entry::getValue).findAny().orElse(x))
						.orElse("");
				System.out.println("groupId : " + groupId);
				System.out.println("artifactId : " + artifactId);
				System.out.println("version : " + version);

				JarInfo jarInfo = AnalyzeJar(groupId, artifactId, version);
				return jarInfo;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}


	public Integer persistJarInfo(JarInfo j, JarInformationManager jm) {

		JarInformation ji = new JarInformationImpl()
				.setArtifactId(j.getArtifactId()).setGroupId(j.getGroupId())
				.setVersion(j.getVersion());

		ji = jm.persist(ji);

		System.out.println(ji.getId());
		return ji.getId();
	}

	public JarInfo AnalyzeJar(String groupId, String artifactId, String version) {
		version = version.replaceAll("[^0-9.]", "");
		JarInfo jarInfo;
		String url = "http://central.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
				+ "-" + version + ".jar";
		jarInfo = AnalyzeJar(url, groupId, artifactId, version);

		if (jarInfo == null) {
			url = "http://central.maven.org/maven2/org/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
					+ "-" + version + ".jar";
			jarInfo = AnalyzeJar(url, groupId, artifactId, version);
		}

		if (jarInfo == null) {
			url = "http://central.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
					+ "/" + artifactId + "-" + version + ".jar";
			jarInfo = AnalyzeJar(url, groupId, artifactId, version);
		}

		return jarInfo;
	}

	public JarInfo AnalyzeJar(String url, String groupId, String artifactId, String version) {
		JarFile jarFile = DownloadJar(url);
		return AnalyzeJar(jarFile, groupId, artifactId, version);
	}

	public JarFile DownloadJar(String jarUrl) {
		String jarName = Utility.getJarName(jarUrl);
		String jarLocation = jarsPath.toString() + '/' + jarName;
		JarFile jarFile = null;
		File file = new File(jarLocation);
		if (file.exists()) {
			try {
				return new JarFile(new File(jarLocation));
			} catch (IOException e) {
				// System.out.println("Cannot open jar: " + jarLocation);
			}
		}
		try {
			Utility.downloadUsingStream(jarUrl, jarLocation);
		} catch (IOException e) {
			// System.out.println("Could not download jar: " + jarUrl);
		}

		try {
			jarFile = new JarFile(new File(jarLocation));
		} catch (IOException e) {
			// System.out.println("Cannot open jar: " + jarLocation);
		}
		return jarFile;
	}

	public JarInfo AnalyzeJar(JarFile jarFile, String groupId, String artifactId, String version) {
		if (jarFile == null)
			return null;
		JarInfo jarInfo = new JarInfo(jarFile, groupId, artifactId, version);
		// if(jarInfo != null && groupId != "" && artifactId != "" && version !=
		// "")
		// SaveToDb(jarInfo);
		return jarInfo;
	}

	private void SaveToDb(JarInfo jarInfo) {

		// Jar jar = manager.create(jarInfo);

		// HibernateUtil.getSessionFactory().close();
		/*
		 * MethodManager oldMethodManager = new
		 * MethodManager(HibernateUtil.getSessionFactory()); for (MethodInfo
		 * methodInfo : jarInfo.getAllPublicMethods()) {
		 * methodInfo.setJar(jar.getId());
		 * 
		 * Method method = new Method(); method.setJar(methodInfo.getJar());
		 * method.setName(methodInfo.getName());
		 * method.setReturnType(methodInfo.getReturnType());
		 * method.setArguments(methodInfo.getArgumentTypes().length); String
		 * parameters = ""; for (Type type : methodInfo.getArgumentTypes()) {
		 * parameters +=
		 * type.getClassName().substring(type.getClassName().lastIndexOf('.') +
		 * 1) + " "; } method.setArgumentTypes(parameters);
		 * oldMethodManager.create(method); }
		 */
	}

	public JarInfo findAndAnalyzeJar(String importDeclarationName) {
		String requestUrl = "http://search.maven.org/solrsearch/select?q=fc:%22" + importDeclarationName
				+ "%22&rows=10&wt=json";
		String response = "";
		try {
			response = Utility.getHTML(requestUrl);

			JSONObject object = new JSONObject(response);
			JSONObject responseObject = object.getJSONObject("response");
			JSONArray responseDocs = responseObject.getJSONArray("docs");

			for (int i = 0; i < responseDocs.length(); i++) {
				JSONObject jarDetail = (JSONObject) responseDocs.get(i);
				String group = jarDetail.getString("g");
				String artifact = jarDetail.getString("a");
				String version = jarDetail.getString("v");
				JarInfo jarInfo = AnalyzeJar(group, artifact, version);

				if (jarInfo != null) {
					return jarInfo;
				}
			}
			/*
			 * if(responseDocs.length() > 0){ JSONObject jarDetail =
			 * (JSONObject) responseDocs.get(0); String group =
			 * jarDetail.getString("g"); String artifact =
			 * jarDetail.getString("a"); String version =
			 * jarDetail.getString("v"); JarInfo jarInfo = AnalyzeJar(group,
			 * artifact, version); SaveToDb(jarInfo); return jarInfo; }
			 */
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
