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
	private int id;
	private String name;
	private String group;
	private String artifact;
	private String version;

//	private ArrayList<ClassInfo> classes;
	private ArrayList<PackageInfo> packages;

	public JarInfo(JarFile jarFile) {
		this.name = Utility.getJarName(jarFile.getName());
//		this.classes = new ArrayList<ClassNodesInfo>();
		this.packages = new ArrayList<PackageInfo>();
		
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
				ClassInfo newClass = new ClassInfo(classNode);
				String packageName = newClass.getName().substring(0, newClass.getName().lastIndexOf('.'));
				PackageInfo packageInfo = getPackageInfo(packageName);
				packageInfo.addClass(newClass);
//				classes.add(newClass);
			}
		}
	}

	private PackageInfo getPackageInfo(String packageName) {
		for (PackageInfo packageInfo : packages) {
			if(packageInfo.getName().equals(packageName)){
				return packageInfo;
			}
		}
		PackageInfo packageInfo = new PackageInfo(id, packageName);
		packages.add(packageInfo);
		return packageInfo;
	}

	public String toString() {
		String jarString = name;
		for (PackageInfo packageInfo: packages) {
			jarString += "\n\n" + packageInfo.toString();
		}
		return jarString;
	}

	public ArrayList<MethodInfo> getAllPublicMethods() {
		ArrayList<MethodInfo> publicMethods = new ArrayList<MethodInfo>();
		for (ClassInfo classInfo : getClasses()) {
			for (MethodInfo methodInfo : classInfo.getMethods()) {
				if (methodInfo.isPublic())
					publicMethods.add(methodInfo);
			}
		}
		return publicMethods;
	}

	public ArrayList<ClassInfo> getClasses() {
		ArrayList<ClassInfo> classes = new ArrayList<ClassInfo>();
		for (PackageInfo packageInfo : packages) {
			classes.addAll(packageInfo.getClasses());
		}
		return classes;
	}
	
	public ArrayList<PackageInfo> getPackages() {
		return packages;
	}

	public int getId(){
		return id;
	}
	
	public String getName() {
		return name;
	}

	public String getGroup() {
		return group;
	}

//	public void setGroup(String group) {
//		this.group = group;
//	}

	public String getArtifact() {
		return artifact;
	}

//	public void setArtifact(String artifact) {
//		this.artifact = artifact;
//	}

	public String getVersion() {
		return version;
	}

//	public void setVersion(String version) {
//		this.version = version;
//	}

}
