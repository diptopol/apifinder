package ca.concordia.jaranalyzer.artifactextractor;

import ca.concordia.jaranalyzer.models.Artifact;
import org.eclipse.jgit.lib.Repository;

import java.util.Set;

/**
 * @author Diptopol
 * @since 3/15/2022 9:09 PM
 */
public abstract class ArtifactExtractor {

    public abstract Set<Artifact> getDependentArtifactSet();

}
