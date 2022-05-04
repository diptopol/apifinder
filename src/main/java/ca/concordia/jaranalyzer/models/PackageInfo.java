package ca.concordia.jaranalyzer.models;

import java.util.ArrayList;
import java.util.List;

public class PackageInfo {

    private final String name;
    private final List<ClassInfo> classList;

    public PackageInfo(String packageName) {
        this.classList = new ArrayList<>();
        this.name = packageName;
    }

    public String getName() {
        return name;
    }

    public List<ClassInfo> getClassList() {
        return classList;
    }

    public void addClass(ClassInfo classInfo) {
        classList.add(classInfo);
    }

    public String toString() {
        String packageString = "PACKAGE: " + name;

        for (ClassInfo classInfo : classList) {
            packageString += "\n" + classInfo.toString();
        }

        return packageString;
    }

}
