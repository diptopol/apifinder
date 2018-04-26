package ca.concordia.jaranalyzer.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

public class GitUtil {

	public Repository openRepository(String repositoryPath) throws Exception {
	    File folder = new File(repositoryPath);
	    Repository repository;
	    if (folder.exists()) {
	        RepositoryBuilder builder = new RepositoryBuilder();
	        repository = builder
	            .setGitDir(new File(folder, ".git"))
	            .readEnvironment()
	            .findGitDir()
	            .build();
	    } else {
	        throw new FileNotFoundException(repositoryPath);
	    }
	    return repository;
	}
	
	public String getNearestTag(Repository repository) throws Exception {
		String nearestTag = "";
		final ArrayList<Ref> tags = new ArrayList<Ref>();
		final RevWalk walk = new RevWalk(repository);
		walk.reset();
		ObjectId id = repository.resolve(repository.getFullBranch());
		RevCommit commit = walk.parseCommit(id);
		walk.reset();
		for (final Ref ref : repository.getTags().values()) {
			final RevObject obj = walk.parseAny(ref.getObjectId());
			final RevCommit tagCommit;
			if (obj instanceof RevCommit) {
				tagCommit = (RevCommit) obj;
			} else if (obj instanceof RevTag) {
				tagCommit = walk.parseCommit(((RevTag) obj).getObject());
			} else {
				continue;
			}
			tags.add(ref);
			if (commit.equals(tagCommit) || walk.isMergedInto(commit, tagCommit)) {
				nearestTag = ref.getName();
				break;
			}
		}
		
		if(nearestTag.isEmpty() && !tags.isEmpty())
		{
			nearestTag = tags.get(tags.size()-1).getName();
		}
		
		if(nearestTag.contains("refs/tags/")){
			nearestTag = nearestTag.replace("refs/tags/", "");
		}
		return nearestTag;
	}

	public Set<Ref> getAllReleaseTags(Repository repository) throws Exception {
		final Set<Ref> tags = new HashSet<Ref>();
		final RevWalk walk = new RevWalk(repository);
		walk.reset();
		for (final Ref ref : repository.getTags().values()) {
			final RevObject obj = walk.parseAny(ref.getObjectId());
			if (!(obj instanceof RevCommit) && !(obj instanceof RevTag)) {
				continue;
			}
			tags.add(ref);
		}
		return tags;
	}

	public String getRelease(Repository repository, String release) {
		String downloadUrl = repository.getConfig().getString( "remote", "origin", "url" ) + "/archive/" + release + ".zip";
		
		return null;
	}
}
