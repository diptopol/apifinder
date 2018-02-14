package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.dao.JarManager;
import ca.concordia.jaranalyzer.dao.MethodManager;
import ca.concordia.jaranalyzer.model.Jar;
import ca.concordia.jaranalyzer.model.Method;
import ca.concordia.jaranalyzer.util.Utility;

import org.json.JSONArray;
import org.json.JSONObject;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class JarAnalyzer {

	private String jarsPath;

	public JarAnalyzer() {
		File file = new File("C:\\jars");
		file.mkdirs();
		jarsPath = file.getAbsolutePath();
	}

	public JarInfo AnalyzeJar(String group, String artifactId, String version) {
		version = version.replaceAll("[^0-9.]", "");
		JarInfo jarInfo;
		String url = "http://central.maven.org/maven2/" + group + "/" + artifactId + "/" + version + "/" + artifactId
				+ "-" + version + ".jar";
		jarInfo = AnalyzeJar(url);

		if (jarInfo == null) {
			url = "http://central.maven.org/maven2/org/" + group + "/" + artifactId + "/" + version + "/" + artifactId
					+ "-" + version + ".jar";
			jarInfo = AnalyzeJar(url);
		}
		
		if (jarInfo == null) {
			url = "http://central.maven.org/maven2/" + group.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId
					+ "-" + version + ".jar";
			jarInfo = AnalyzeJar(url);
		}

		return jarInfo;
	}

	public JarInfo AnalyzeJar(String url) {
		JarFile jarFile = DownloadJar(url);
		return AnalyzeJar(jarFile);
	}

	public JarFile DownloadJar(String jarUrl) {
		String jarName = Utility.getJarName(jarUrl);
		String jarLocation = jarsPath.toString() + '/' + jarName;
		JarFile jarFile = null;
		File file = new File(jarLocation);
		if(file.exists()){
			try {
				return new JarFile(new File(jarLocation));
			} catch (IOException e) {
//				System.out.println("Cannot open jar: " + jarLocation);
			}
		}
		try {
			Utility.downloadUsingStream(jarUrl, jarLocation);
		} catch (IOException e) {
//			System.out.println("Could not download jar: " + jarUrl);
		}

		
		try {
			jarFile = new JarFile(new File(jarLocation));
		} catch (IOException e) {
//			System.out.println("Cannot open jar: " + jarLocation);
		}
		return jarFile;
	}

	public JarInfo AnalyzeJar(JarFile jarFile) {
		if (jarFile == null)
			return null;
		JarInfo jarInfo = new JarInfo(jarFile);
		return jarInfo;
	}

	public void SaveToDb(JarInfo jarInfo) {
		Jar jar = new Jar();
		jar.setName(jarInfo.getName());
		JarManager manager = new JarManager();
		if (manager.exists(jar.getName()))
			return;
		jar = manager.create(jar);
		manager.readAll();
		manager.exit();

		MethodManager oldMethodManager = new MethodManager();
		for (MethodInfo methodInfo : jarInfo.getAllPublicMethods()) {
			methodInfo.setJar(jar.getId());

			Method method = new Method();
			method.setJar(methodInfo.getJar());
			method.setName(methodInfo.getName());
			method.setReturnType(methodInfo.getReturnType());
			method.setArguments(methodInfo.getArgumentTypes().length);
			String parameters = "";
			for (Type type : methodInfo.getArgumentTypes()) {
				parameters += type.getClassName().substring(type.getClassName().lastIndexOf('.') + 1) + " ";
			}
			method.setArgumentTypes(parameters);
			oldMethodManager.create(method);
		}
		manager.exit();
	}

	
	public JarInfo findAndAnalyzeJar(String importDeclarationName) {
		String requestUrl = "http://search.maven.org/solrsearch/select?q=fc:%22" + importDeclarationName + "%22&rows=1&wt=json";  
		String response = "";
		try {
			response = Utility.getHTML(requestUrl);
			
			JSONObject object = new JSONObject(response);			
			JSONObject responseObject = object.getJSONObject("response");
			JSONArray responseDocs = responseObject.getJSONArray("docs");

			if(responseDocs.length() > 0){
				JSONObject jarDetail = (JSONObject) responseDocs.get(0);	
				String group = jarDetail.getString("g");
				String artifact = jarDetail.getString("a");
				String version = jarDetail.getString("v");
				return AnalyzeJar(group, artifact, version);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
