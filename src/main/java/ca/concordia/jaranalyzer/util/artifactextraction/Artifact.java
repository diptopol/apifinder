package ca.concordia.jaranalyzer.util.artifactextraction;

/**
 * @author Diptopol
 * @since 3/12/2022 4:36 PM
 */
public class Artifact {

    private String groupId;
    private String artifactId;
    private String version;
    private String type;

    public Artifact(String groupId, String artifactId, String version, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
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

}
