package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.dao.JarManager;
import ca.concordia.jaranalyzer.dao.MethodManager;
import ca.concordia.jaranalyzer.model.Jar;
import ca.concordia.jaranalyzer.model.Method;

import java.io.IOException;

public abstract class Test {

	public static void main(String[] args) throws IOException { 
        JarInfo jarInfo = new JarAnalyzer().AnalyzeJar("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar");
        Jar jar = new Jar();
        jar.setName(jarInfo.getName());
        JarManager manager = new JarManager();
        jar = manager.create(jar);
        manager.readAll();
        manager.exit();
        
        MethodManager oldMethodManager = new MethodManager();
        for (MethodInfo methodInfo : jarInfo.getAllPublicMethods()) {
			Method method = new Method();
			method.setJar(jar.getId());
			method.setName(methodInfo.getName());
			method.setReturnType(methodInfo.getReturnType());
			method.setArguments(methodInfo.getArgumentTypes().length);
			oldMethodManager.create(method);
		}
        manager.exit();
	}

}
