package ca.concordia.jaranalyzer.models;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Diptopol
 * @since 3/12/2022 4:36 PM
 */
public class Artifact implements Serializable {

    private static final long serialVersionUID = 42L;

    private static final String MAVEN_PLUGIN_TYPE = "maven-plugin";
    private static final String BUNDLE_TYPE = "bundle";

    public static final String JAR_TYPE = "jar";
    public static final String POM_TYPE= "pom";

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;

    private boolean isInternalDependency;

    public Artifact(String groupId, String artifactId, String version, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = (MAVEN_PLUGIN_TYPE.equals(type) || BUNDLE_TYPE.equals(type)) ? JAR_TYPE : type;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, JAR_TYPE);
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

    public String getType() {
        return type;
    }

    public boolean isInternalDependency() {
        return isInternalDependency;
    }

    public void setInternalDependency(boolean internalDependency) {
        this.isInternalDependency = internalDependency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact artifact = (Artifact) o;

        return groupId.equals(artifact.groupId) && artifactId.equals(artifact.artifactId) && version.equals(artifact.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }
}
