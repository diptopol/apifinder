package ca.concordia.apifinder.artifactresolver;

import org.apache.maven.settings.building.SettingsBuildingException;

import java.util.Objects;

/**
 * @author Diptopol
 * @since 3/14/2022 2:09 PM
 */
public class ArtifactResolverFactory {

    private static ArtifactResolver artifactResolver;

    public static ArtifactResolver getArtifactResolver() {
        if (Objects.isNull(artifactResolver)) {
            try {
                artifactResolver = new ArtifactResolver();
            } catch (SettingsBuildingException ex) {
                ex.printStackTrace();
            }

        }

        return artifactResolver;
    }

}
