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
	private String groupId;
	private String artifactId;
	private String version;

	private ArrayList<PackageInfo> packages;

	public JarInfo(JarFile jarFile, String groupId, String artifactId, String version) {
		this.artifactId = artifactId;
		this.groupId = groupId;
		this.version = version;
		this.name = Utility.getJarName(jarFile.getName());
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
				String packageName = newClass.getQualifiedName().substring(0, newClass.getQualifiedName().lastIndexOf('.'));
				PackageInfo packageInfo = getPackageInfo(packageName);
				packageInfo.addClass(newClass);
			}
		}
		
		for (ClassInfo classInfo : getClasses()) {
			if(!classInfo.getSuperClassName().equals("java/lang/Object")){
				for (ClassInfo cls : getClasses()) {
					if(cls.getQualifiedName().equals(classInfo.getSuperClassName())){
						classInfo.setSuperClassInfo(cls);
					}
				}
			}
		}
	}

	private PackageInfo getPackageInfo(String packageName) {
		for (PackageInfo packageInfo : packages) {
			if(packageInfo.getName().equals(packageName)){
				return packageInfo;
			}
		}
		PackageInfo packageInfo = new PackageInfo(packageName);
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

	public ArrayList<MethodInfo> getAllMethods() {
		ArrayList<MethodInfo> publicMethods = new ArrayList<MethodInfo>();
		for (ClassInfo classInfo : getClasses()) {
			for (MethodInfo methodInfo : classInfo.getMethods()) {
//				if (methodInfo.isPublic())
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
	
	public String getName() {
		return name;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public ArrayList<ClassInfo> getClasses(String importedPackage) {
		ArrayList<ClassInfo> matchedClasses = new ArrayList<ClassInfo>();
		
		for (ClassInfo classInfo : getClasses()) {
			if(classInfo.getQualifiedName().startsWith(importedPackage)){
				matchedClasses.add(classInfo);
			}
		}
		return matchedClasses;
	}
}
