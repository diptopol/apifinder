package ca.concordia.apifinder.artifactextractor;

import ca.concordia.apifinder.models.Artifact;

import java.util.Set;

/**
 * @author Diptopol
 * @since 3/15/2022 9:09 PM
 */
public abstract class ArtifactExtractor {

    public abstract String getJavaVersion();

    public abstract Set<Artifact> getDependentArtifactSet();

}
