package ca.concordia.jaranalyzer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplication;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.fieldinformation.FieldInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.MethodArgTypeInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodinformation.MethodInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationManager;
import ca.concordia.jaranalyzer.util.Utility;

public class JarAnalyzer {

	private String jarsPath;

	JarAnalysisApplication app;
	JarInformationManager jm;
	PackageInformationManager pkgM;
	ClassInformationManager clsM;
	MethodInformationManager mthdM;
	FieldInformationManager fldM;
	MethodArgTypeInformationManager mthdArgM;

	public JarAnalyzer(){}

	public JarAnalyzer(JarAnalysisApplication app, String jarPath) {
		File file = new File(jarPath);
		file.mkdirs();
		jarsPath = file.getAbsolutePath();
		this.app = app;
		this.jm = app.getOrThrow(JarInformationManager.class);
		this.pkgM = app.getOrThrow(PackageInformationManager.class);
		this.clsM = app.getOrThrow(ClassInformationManager.class);
		this.mthdM = app.getOrThrow(MethodInformationManager.class);
		this.mthdArgM = app.getOrThrow(MethodArgTypeInformationManager.class);
		this.fldM = app.getOrThrow(FieldInformationManager.class);
	}

	public List<JarInfo> analyzeJarsFromPOM(Set<String> pomFiles) {
		List<JarInfo> jarInfos = new ArrayList<>();

		Map<String, String> properties = getPropertiesInPom(pomFiles);
		try {
			for (String pomLocation : pomFiles) {
				File inputFile = new File(pomLocation);
				Optional<Document> doc = getDocument(inputFile);
				if (doc.isPresent()) {
					//System.out.println("Root element :" + doc.get().getDocumentElement().getNodeName());
					NodeList project = doc.get().getElementsByTagName("project");
					jarInfos.addAll(getJarInforFromNodeList(project, jm, properties));
					NodeList nList = doc.get().getElementsByTagName("dependency");
					//System.out.println("----------------------------");
					jarInfos.addAll(getJarInforFromNodeList(nList, jm, properties));
					System.out.println(pomLocation);
				}
			}
		}
		catch(Exception e){
				e.printStackTrace();
			}
			return jarInfos;
		}

	public List<JarInfo> analyzeJarsFromEffectivePom(String effectivePomFile) {
		List<JarInfo> jarInfos = new ArrayList<>();

		try {
				File inputFile = new File(effectivePomFile);
				Optional<Document> dd = getDocument(inputFile);
				if (dd.isPresent()) {

					Optional<NodeList> z = dd.map(d -> d.getElementsByTagName("project"));
					z.ifPresent(nodeList -> System.out.println(nodeList.getLength()));
					JarAnalyzer jarAnalyzer = new JarAnalyzer(app, "/Users/ameya/FinalResults/diffTools/jars/");
					List<JarInformation> ji = jarAnalyzer.getJarInformationFromPom(Stream.of(effectivePomFile).collect(toSet()));
					System.out.println(ji.size());
					NodeList prjcts = z.get();
					List<String> internalGroupIds = new ArrayList<>();
					for(int i = 0 ; i < prjcts.getLength(); i++){
						Node x = prjcts.item(i);
						for(int j =0; j<x.getChildNodes().getLength(); j ++){
							Node xx = x.getChildNodes().item(j);
							if(xx.getNodeName().equals("groupId")){
								internalGroupIds.add(xx.getTextContent());
							}

						}
					}
					List<JarInformation> jiAnlys = ji.stream()
							//.map(j -> j.getArtifactId() + "--" + j.getGroupId() + "--" + j.getVersion())
							.filter(j -> internalGroupIds.stream().noneMatch(g -> j.getGroupId().contains(g)))
							.distinct()
							.collect(toList());
					System.out.println(jiAnlys.size());

					//System.out.println("Root element :" + doc.get().getDocumentElement().getNodeName());
//					NodeList project = doc.get().getElementsByTagName("project");
//					jarInfos.addAll(getJarInforFromNodeListEffectivePom(project, jm));
//					NodeList nList = doc.get().getElementsByTagName("dependency");
//					//System.out.println("----------------------------");
//					jarInfos.addAll(getJarInforFromNodeListEffectivePom(nList, jm));
					jarInfos = jiAnlys.stream()
							.map(j -> getJarInfoFromDependencyEffectivePom(j,jm))
							.filter(j -> j.isPresent())
							.map(j -> j.get())
							.collect(toList());


					System.out.println(effectivePomFile);
				}
			}
		catch(Exception e){
			e.printStackTrace();
		}
		return jarInfos;
	}



	private List<JarInfo> getJarInforFromNodeList(NodeList project,JarInformationManager jm,Map<String, String> properties  ){
		return IntStream.range(0, project.getLength())
				.mapToObj(i -> getJarInfoFromDependency(project.item(i), properties, jm))
				.filter(x -> x!=null)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}

//	private List<JarInfo> getJarInforFromNodeListEffectivePom(NodeList project,JarInformationManager jm ){
//		return IntStream.range(0, project.getLength())
//				.mapToObj(i -> getJarInfoFromDependencyEffectivePom(project.item(i), jm))
//				.filter(x -> x!=null)
//				.filter(Optional::isPresent)
//				.map(Optional::get)
//				.collect(Collectors.toList());
//	}

	private Map<String, String> getPropertiesInPom(Set<String> pomFiles) {
		return pomFiles.stream()
				.map(p -> getDocument(new File(p)))
				.filter(Optional::isPresent)
				.flatMap(x -> getProperties(x.get()).entrySet().stream())
				.collect(toMap(Entry::getKey,Entry::getValue,mergeProperties));
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

	private BinaryOperator<String> mergeProperties = (s1, s2) -> s1.equals(s2)? s1 : s1+","+s2;

	public Optional<JarInfo> getJarInfoFromDependency(Node nNode, Map<String, String> properties, JarInformationManager jm) {
		String groupId;
		String artifactId;
		List<String> versions;
		Optional<JarInfo> ji = Optional.empty();
		try {
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				groupId = eElement.getElementsByTagName("groupId").item(0).getTextContent();
				artifactId = eElement.getElementsByTagName("artifactId").item(0).getTextContent();
				versions = Optional.ofNullable(eElement.getElementsByTagName("version"))
						.map(x -> x.item(0))
						.map(Node::getTextContent)
						.map(x -> properties.entrySet().stream().filter(e ->x.contains( e.getKey()))
								.map(Entry::getValue).findAny().orElse(x))
						.map(x -> x.contains(",") ? Arrays.asList(x.split(",")) : Arrays.asList(x))
						.orElse(new ArrayList<>());
				for(String version : versions)
					if(!version.isEmpty() && !version.contains("project.version")) {
						ji = AnalyzeJar(groupId, artifactId, version, jm);
						if(ji == null){
							System.out.println("Could not fetch jar " + artifactId + "--" + groupId + "--" + version);
							ji = Optional.empty();
						}
						else if(!ji.isPresent()) {
	//						System.out.println("groupId : " + groupId);
	//						System.out.println("artifactId : " + artifactId);
	//						System.out.println("version : " + version);
							System.out.println("Could not resolve version : " + version );
						}
					}
//				else{
//
//				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ji;
	}


	public Optional<JarInfo> getJarInfoFromDependencyEffectivePom(JarInformation ji,  JarInformationManager jm) {
		String groupId = ji.getGroupId();
		String artifactId = ji.getArtifactId();
		String version = ji.getVersion();
		Optional<JarInfo> jo = Optional.empty();
		try {
					return AnalyzeJar(groupId, artifactId, version, jm);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return jo;
	}

	public JarInformation getJarInformationForNode(Node nNode, Map<String, String> properties){
		Element eElement = (Element) nNode;
		return new JarInformationImpl()
				.setGroupId(eElement.getElementsByTagName("groupId").item(0).getTextContent())
				.setArtifactId(eElement.getElementsByTagName("artifactId").item(0).getTextContent())
				.setVersion(Optional.ofNullable(eElement.getElementsByTagName("version"))
						.map(x -> x.item(0))
						.map(Node::getTextContent)
						.map(x -> properties.entrySet().stream().filter(e ->x.contains( e.getKey()))
								.map(Entry::getValue).findAny().orElse(x))
						.orElse(""))
				.setId(-1);
	}


	public Integer persistJarInfo(JarInfo j, JarInformationManager jm) {

		JarInformation ji = new JarInformationImpl()
				.setArtifactId(j.getArtifactId()).setGroupId(j.getGroupId())
				.setVersion(j.getVersion());

		ji = jm.persist(ji);

		//System.out.println(ji.getId());
		return ji.getId();
	}


	public List<JarInformation> getJarInformationFromPom(Set<String> pomFiles) {

		List<JarInformation> jarInfos = new ArrayList<>();

		Map<String, String> properties = getPropertiesInPom(pomFiles);
		try {
			for (String pomLocation : pomFiles) {
				File inputFile = new File(pomLocation);
				Optional<Document> doc = getDocument(inputFile);
				if(doc.isPresent()) {
					System.out.println("Root element :" + doc.get().getDocumentElement().getNodeName());
					NodeList project = doc.get().getElementsByTagName("project");
					for (int temp = 0; temp < project.getLength(); temp++) {
						JarInformation foundJar = getJarInformationForNode(project.item(temp), properties);
						if (foundJar != null)
							jarInfos.add(foundJar);
					}
					NodeList nList = doc.get().getElementsByTagName("dependency");
					System.out.println("----------------------------");
					for (int temp = 0; temp < nList.getLength(); temp++) {
						JarInformation foundJar = getJarInformationForNode(nList.item(temp), properties);
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


	public Optional<JarInfo> AnalyzeJar(String groupId, String artifactId, String vrsn, JarInformationManager jm) {

		String version = vrsn.replaceAll("[^0-9.]", "");
		Optional<JarInformation> j = jm.stream().filter(y -> y.getArtifactId().equals(artifactId)
				&& y.getGroupId().equals(groupId)
				&& y.getVersion().equals(version)).findFirst();
		if(!j.isPresent()) {

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
			if (jarInfo != null) {
				persistJarInfo(jarInfo, jm);
			}
			if(jarInfo == null)
				System.out.println("Could not fetch jar for " + groupId + " " + artifactId + " " + version);

			return Optional.ofNullable(jarInfo);
		}else{
			System.out.println("Jar already analysed");
		}
		return Optional.empty();
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


	public Optional<JarInfo> analyzeJar(JarFile jarFile, String groupId, String artifactId, String version) {
		try {
			if (jarFile != null && jm.stream().noneMatch(j -> j.getArtifactId().equals(artifactId)
					&& j.getGroupId().equals(groupId) && j.getVersion().equals(version))) {
				JarInfo ji = new JarInfo(jarFile, groupId, artifactId, version);
				persistJarInfo(ji, jm);
				return Optional.of(ji);
			}
		}catch (Exception e){
			System.out.println("Could not analyse: " + groupId + " " + artifactId + " " + version);
			e.printStackTrace();
		}
		return Optional.empty();
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


	public boolean tryGenerateEffectivePom(String projLocation, String sha, String prjct) {
		String projectPath = projLocation;
		projLocation = projLocation + prjct;
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(new File(projLocation + "pom.xml"));
		request.setGoals(Arrays.asList("help:effective-pom", "-Doutput=" +projLocation+"effectivePom.xml"));
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.6.0/"));
		Path statusPath = Paths.get(projectPath + "Status.csv");
		try {
			InvocationResult result = invoker.execute(request);

			if (result.getExitCode() != 0) {
				System.out.println("Effective pom generation Failed");
				String str = String.join(",", prjct, sha, "OW!!!\n");
				Files.write(statusPath, str.getBytes(), StandardOpenOption.APPEND);
				return false;
			}
			else{
				
				Files.write(statusPath, )
			}
		}catch (Exception e){
			System.out.println(e.toString());
			e.printStackTrace();
		}
		return true;
	}
}
