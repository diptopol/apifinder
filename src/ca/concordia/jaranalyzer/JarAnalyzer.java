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
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitseffectivepom.CommitsEffectivePomImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitseffectivepom.CommitsEffectivePomManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.fieldinformation.FieldInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.fieldinformation.FieldInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.MethodArgTypeInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodinformation.MethodInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodinformation.MethodInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodinformation.MethodInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.superinterfaceclass.SuperInterfaceClassImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.superinterfaceclass.SuperInterfaceClassManager;
import ca.concordia.jaranalyzer.util.Utility;


public class JarAnalyzer {


	private String jarsPath;
	private JarAnalysisApplication app;
	private JarInformationManager jm;
	private PackageInformationManager pkgM;
	private ClassInformationManager clsM;
	private MethodInformationManager mthdM;
	private FieldInformationManager fldM;
	private CommitsEffectivePomManager cmtEffM;
	private MethodArgTypeInformationManager mthdArgM;
	private SuperInterfaceClassManager sic;

	public JarAnalyzer(){}

	public JarAnalyzer(JarAnalysisApplication app, String jarPath) {
		File file = new File(jarPath);
		file.mkdirs();
		jarsPath = file.getAbsolutePath();
		this.app = app;
		jm = this.app.getOrThrow(JarInformationManager.class);
		pkgM = app.getOrThrow(PackageInformationManager.class);
		clsM = app.getOrThrow(ClassInformationManager.class);
		mthdM = app.getOrThrow(MethodInformationManager.class);
		fldM = app.getOrThrow(FieldInformationManager.class);
		cmtEffM = app.getOrThrow(CommitsEffectivePomManager.class);
	//	mthdArgM =  app.getOrThrow(MethodArgTypeInformationManager.class);
		sic = app.getOrThrow(SuperInterfaceClassManager.class);
//
//    public static final ProjectsManager prjctM = app.getOrThrow(ProjectsManager.class);
//    public static final CommitsManager cmtM = app.getOrThrow(CommitsManager.class);


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
					JarAnalyzer jarAnalyzer = new JarAnalyzer(app, "/Users/ameya/FinalResults/diffTools/jars/");
					List<JarInformation> ji = jarAnalyzer.getJarInformationFromPom(Stream.of(effectivePomFile).collect(toSet()));
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
							.filter(j -> internalGroupIds.stream().noneMatch(g -> j.getGroupId().contains(g)))
							.distinct()
							.collect(toList());
					System.out.println(jiAnlys.size());
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
			return Optional.of(doc);
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
		if(eElement == null) return null;
		return new JarInformationImpl()
				.setGroupId(Optional.ofNullable(eElement.getElementsByTagName("groupId"))
						.map(o -> o.item(0)).map(Node::getTextContent).orElse(""))
				//.setArtifactId(eElement.getElementsByTagName("artifactId").item(0).getTextContent())
				.setArtifactId(Optional.ofNullable(eElement.getElementsByTagName("artifactId"))
						.map(o -> o.item(0)).map(Node::getTextContent).orElse(""))
				.setVersion(Optional.ofNullable(eElement.getElementsByTagName("version"))
						.map(x -> x.item(0)).map(Node::getTextContent)
//						.map(x -> properties.entrySet().stream().filter(e ->x.contains( e.getKey()))
//								.map(Entry::getValue).findAny().orElse(x))
						.orElse(""))
				.setId(-1);
	}


	public Integer persistJarInfo(JarInfo j, boolean couldFetch) {
		Optional<JarInformation> o_jr = jm.stream().filter(jr -> jr.getGroupId().equals(j.getGroupId())
				&& jr.getArtifactId().equals(j.getArtifactId()) && jr.getVersion().equals(j.getVersion()))
				.findAny();
		if(!o_jr.isPresent()) {
			JarInformation jr = jm.persist(new JarInformationImpl().setArtifactId(j.getArtifactId()).setGroupId(j.getGroupId())
					.setVersion(j.getVersion())
					.setCouldFetch(couldFetch));

			for(PackageInfo p : j.getPackages()){
				PackageInformation pi =pkgM.persist(new PackageInformationImpl().setJarId(jr.getId()).setName(p.getName()));

				//for(ClassInfo c : p.getClasses()){
				p.getClasses().parallelStream().forEach( c -> {
						ClassInformation ci = clsM.persist(new ClassInformationImpl().setQualifiedName(c.getQualifiedName())
						.setAccessModifiers(c.isPublic()?"PUBLIC":(c.isPrivate() ? "PRIVATE" : (c.isProtected() ? "PROTECTED" : "DEFAULT")))
						.setIsAbstract(c.isAbstract())
						.setIsInterface(c.isInterface())
						.setName(c.getName())
						.setSuperClass(c.getSuperClassName())
						.setPackageId(pi.getId())
						.setType(c.getType().toString()));

					for(String s: c.getSuperInterfaceNames()){
						sic.persist(new SuperInterfaceClassImpl().setClassId(ci.getId()).setSuperInterface(s));
					}
					for(MethodInfo m : c.getMethods()){
						MethodInformation mi = mthdM.persist(new MethodInformationImpl()
								.setAccessModifiers(m.isPublic()?"PUBLIC":(m.isPrivate() ? "PRIVATE" : (m.isProtected() ? "PROTECTED" : "DEFAULT")))
								.setClassId(ci.getId())
								.setIsAbstract(m.isAbstract())
								.setIsConstructor(m.isConstructor())
								.setIsStatic(m.isStatic())
								.setIsSynchronized(m.isSynchronized())
								.setName(m.getName())
								.setReturnType(m.getReturnType()));

//						for(Type t : Arrays.asList(m.getArgumentTypes()))
//							mthdArgM.persist(new MethodArgTypeInformationImpl()
//									.setMethodId(mi.getId())
//									.setType(t.getClassName()));
					}

					for(FieldInfo f : c.getFields()){
						fldM.persist(new FieldInformationImpl()
							.setAccessModifier(f.isPublic()?"PUBLIC":(f.isPrivate() ? "PRIVATE" : (f.isProtected() ? "PROTECTED" : "DEFAULT")))
							.setClassId(ci.getId())
							.setName(f.getName())
							.setType(f.getType().getClassName()));
					}
				});

			}

			return jr.getId();

		}else{
			return o_jr.get().getId();
		}
	}

	public Integer persistJarInfo(String groupId, String artifactId, String version , boolean couldFetch) {

		JarInformation ji = jm.stream().filter(jr -> jr.getGroupId().equals(groupId)
				&& jr.getArtifactId().equals(artifactId) && jr.getVersion().equals(version))
				.findAny()
				.orElseGet(() -> jm.persist(new JarInformationImpl().setArtifactId(artifactId).setGroupId(groupId)
						.setVersion(version)
						.setCouldFetch(couldFetch)));

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
		JarInfo jarInfo = getJarInfo(groupId, artifactId, version);
		return jarInfo;
	}


	public Optional<JarInfo> AnalyzeJar(String groupId, String artifactId, String vrsn, JarInformationManager jm) {

		String version = vrsn.replaceAll("[^0-9.]", "");
		Optional<JarInformation> j = queryDbForJar(groupId, artifactId, jm, version);

		if(!j.isPresent()) {

			JarInfo jarInfo;

			if(groupId.contains("spring") || artifactId.contains("spring")){
				System.out.println();
			}

			jarInfo = getJarInfo(groupId, artifactId, version);
			if(jarInfo==null){
				jarInfo = getJarInfo(groupId,artifactId,vrsn);
			}
			if (jarInfo != null) {
				persistJarInfo(jarInfo,  true);
			}
			if(jarInfo == null) {
				System.out.println("Could not fetch jar for " + groupId + " " + artifactId + " " + version);
				persistJarInfo(groupId,artifactId,version, false);

			}
			return Optional.ofNullable(jarInfo);
		}else{
			System.out.println("Jar already analysed");
		}
		return Optional.empty();
	}

	private JarInfo getJarInfo(String groupId, String artifactId, String version) {
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

	private static Optional<JarInformation> queryDbForJar(String groupId, String artifactId, JarInformationManager jm, String version) {
		return jm.stream().filter(y -> y.getArtifactId().equals(artifactId)
				&& y.getGroupId().equals(groupId)
				&& y.getVersion().equals(version)).findFirst();
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
		return jarInfo;
	}


	public Optional<JarInfo> analyzeJar(JarFile jarFile, String groupId, String artifactId, String version) {
		try {
			if (jarFile != null && jm.stream().noneMatch(j -> j.getArtifactId().equals(artifactId)
					&& j.getGroupId().equals(groupId) && j.getVersion().equals(version))) {
				JarInfo ji = new JarInfo(jarFile, groupId, artifactId, version);
				persistJarInfo(ji, true);
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
		projLocation = projLocation + prjct;
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(new File(projLocation + "pom.xml"));
		request.setGoals(Arrays.asList("help:effective-pom", "-Doutput=" +projLocation+"effectivePom.xml"));
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.6.0/"));
		boolean isSuccess = false;
		try {
			InvocationResult result = invoker.execute(request);
			isSuccess =  result.getExitCode() == 0 && new File(projLocation+"effectivePom.xml").exists();
			}
		catch (Exception e){
			System.out.println(e.toString());
			e.printStackTrace();
		}
		if(cmtEffM.stream().noneMatch(x -> x.getSha().equals(sha)))
			cmtEffM.persist(new CommitsEffectivePomImpl().setSha(sha).setEffectivePom(isSuccess));
		return isSuccess;
	}
}
