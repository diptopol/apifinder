package ca.concordia.jaranalyzer.entity;

import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

/**
 * @author Diptopol
 * @since 7/13/2022 6:43 PM
 */
public class JarInfo {
    private int id;
    private String groupId;
    private String artifactId;
    private String version;

    private JarFile jarFile;

    //during save
    private List<ClassInfo> classInfoList;

    public JarInfo(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public JarInfo(int id, String groupId, String artifactId, String version) {
        this.id = id;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public JarInfo(String groupId, String artifactId, String version, JarFile jarFile) {
        this(groupId, artifactId, version);
        this.jarFile = jarFile;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public JarFile getJarFile() {
        return jarFile;
    }

    public List<ClassInfo> getClassInfoList() {
        return classInfoList;
    }

    public void setClassInfoList(List<ClassInfo> classInfoList) {
        this.classInfoList = classInfoList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JarInfo jarInfo = (JarInfo) o;

        return groupId.equals(jarInfo.getGroupId()) && artifactId.equals(jarInfo.getArtifactId())
                && version.equals(jarInfo.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

}
