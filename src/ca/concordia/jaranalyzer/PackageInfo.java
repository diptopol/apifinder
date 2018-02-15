package ca.concordia.jaranalyzer;

import java.util.ArrayList;

public class PackageInfo {
	private int id;
	private String name;
	private int jarId;
	private ArrayList<ClassInfo> classes;

	public PackageInfo(String packageName) {
		this.classes = new ArrayList<ClassInfo>();
		this.jarId = jarId;
		this.name = packageName;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getJarId() {
		return jarId;
	}

	public ArrayList<ClassInfo> getClasses() {
		return classes;
	}
	
	public boolean addClass(ClassInfo classInfo){
		classes.add(classInfo);
		return true;
	}
	
	public String toString() {
		String packageString = "PACKAGE: " + name;
		for (ClassInfo classFile : classes) {
			packageString += "\n" + classFile.toString();
		}
		return packageString;
	}
}
