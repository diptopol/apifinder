package ca.concordia.jaranalyzer.util;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

import static ca.concordia.jaranalyzer.util.FileUtils.createFolderIfAbsent;

public class GitUtil {

    public static String getNearestTagCommit(String SHAId, Git git) {
        Repository repository = git.getRepository();

        String tagName = null;

        try (RevWalk walk = new RevWalk(repository)) {
            walk.sort(RevSort.COMMIT_TIME_DESC, true);
            walk.markStart(walk.parseCommit(ObjectId.fromString(SHAId)));

            for (RevCommit revCommit : walk) {
                Map<ObjectId, String> namedCommits = git.nameRev().addPrefix("refs/tags/").add(revCommit.getId()).call();

                if (namedCommits.containsKey(revCommit.getId())) {
                    tagName = namedCommits.get(revCommit.getId());

                    if (tagName.contains("~")) {
                        tagName = tagName.substring(0, tagName.indexOf("~"));
                    }

                    break;
                }
            }

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }


        String nearestTagCommitId = null;
        if (Objects.nonNull(tagName)) {
            try (RevWalk walk = new RevWalk(repository)) {
                nearestTagCommitId = walk.parseCommit(repository.resolve("refs/tags/".concat(tagName))).getName();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return Objects.nonNull(nearestTagCommitId) ? nearestTagCommitId : SHAId;
    }

    public static Optional<RevCommit> findCommit(String SHAId, Repository repo) {
        List<RevCommit> mergeCommits = getMergedCommitList(repo);

        if (mergeCommits.stream().anyMatch(x -> x.getId().getName().equals(SHAId)))
            return Optional.empty();
        return io.vavr.control.Try.of(() -> new RevWalk(repo))
                .flatMap(x -> {
                    return io.vavr.control.Try.of(() -> x.parseCommit(ObjectId.fromString(SHAId)));
                })
                .onFailure(e -> e.printStackTrace())
                .toJavaOptional();
    }

    /**
     * @param repository Git repo
     * @param cmt        the particular commit
     * @param pred       matcher for files to populate the content for
     * @return filePath * content
     */
    public static Map<Path, String> populateFileContents(Repository repository, String cmt,
                                                         Predicate<String> pred)  {
        Map<Path, String> fileContents = new HashMap<>();
        Optional<RevCommit> commit = findCommit(cmt, repository);
        if (commit.isPresent()) {
            return populateFileContent(repository, commit.get(), pred);
        }
        return fileContents;
    }

    public static Map<Path, String> populateFileContent(Repository repository, RevCommit cmt,
                                                        Predicate<String> pred) {
        Map<Path, String> fileContents = new HashMap<>();
        RevTree parentTree = cmt.getTree();
        if(parentTree!=null) {
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(parentTree);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String pathString = treeWalk.getPathString();
                    if (pred.test(pathString)) {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(loader.openStream(), writer);
                        fileContents.put(Paths.get(pathString), writer.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fileContents;
    }

    public static Git openRepository(String projectName, String cloneLink, Path pathToProject) {
        if (Files.exists(pathToProject)) {
            try (Git git = Git.open(pathToProject.resolve(projectName).toFile())) {
                return git;
            } catch (IOException e) {
                throw new RuntimeException("Could not open " + projectName, e);
            }

        } else {
            createFolderIfAbsent(pathToProject);
            try {
                return Git.cloneRepository()
                        .setURI(cloneLink)
                        .setDirectory(pathToProject.resolve(projectName).toFile())
                        .call();
            } catch (GitAPIException e) {
                throw new RuntimeException("Could not clone" + projectName, e);
            }
        }
    }

    public static String checkoutToCommit(Git git, String commitIdOrBranchName) {
        try {
            String previousBranchName = git.getRepository().getBranch();
            git.checkout().setName(commitIdOrBranchName).call();

            return previousBranchName;
        } catch (IOException e) {
            throw new RuntimeException("Could not get branch name", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Could not checkout to : " + commitIdOrBranchName, e);
        }
    }

    private static List<RevCommit> getMergedCommitList(Repository repo) {
        return io.vavr.control.Try.of(() -> {
            RevWalk walk = new RevWalk(repo);
            walk.markStart(walk.parseCommit(repo.resolve("HEAD")));
            walk.setRevFilter(RevFilter.ONLY_MERGES);
            return walk;
        }).map(walk -> {
            Iterator<RevCommit> iter = walk.iterator();
            List<RevCommit> l = new ArrayList<>();
            while (iter.hasNext()) {
                l.add(iter.next());
            }
            walk.dispose();
            walk.close();
            return l;
        }).getOrElse(new ArrayList<>());
    }

}
