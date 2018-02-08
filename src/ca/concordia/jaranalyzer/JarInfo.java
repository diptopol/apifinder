package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.util.Utility;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarInfo {
	private String name;
	private String group;
	private String artifact;
	private String version;

	private ArrayList<ClassInfo> classes;

	public JarInfo(JarFile jarFile) {
		this.name = Utility.getJarName(jarFile.getName());
		this.classes = new ArrayList<ClassInfo>();
		Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			String entryName = entry.getName();
			if (entryName.endsWith(".class")) {
				ClassNode classNode = new ClassNode();
				InputStream classFileInputStream;
				try {
					classFileInputStream = jarFile.getInputStream(entry);
					try {
						ClassReader classReader = new ClassReader(classFileInputStream);
						classReader.accept(classNode, 0);
					} catch (Exception e) {
						System.out.println("Could not read class file");
						e.printStackTrace();
					} finally {
						classFileInputStream.close();
					}
				} catch (Exception e) {
					System.out.println("Could not read class file");
					e.printStackTrace();
				}
				classes.add(new ClassInfo(classNode));
			}
		}
	}

	public String toString() {
		String jarString = name;
		for (ClassInfo classFile : classes) {
			jarString += "\n" + classFile.toString();
		}
		return jarString;
	}

	public ArrayList<MethodInfo> getAllPublicMethods() {
		ArrayList<MethodInfo> publicMethods = new ArrayList<MethodInfo>();
		for (ClassInfo classInfo : classes) {
			for (MethodInfo methodInfo : classInfo.getMethods()) {
				if (methodInfo.isPublic())
					publicMethods.add(methodInfo);
			}
		}
		return publicMethods;
	}

	public ArrayList<ClassInfo> getClasses() {
		return classes;
	}

	public String getName() {
		return name;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getArtifact() {
		return artifact;
	}

	public void setArtifact(String artifact) {
		this.artifact = artifact;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

}
