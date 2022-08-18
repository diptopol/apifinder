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
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;

import static ca.concordia.jaranalyzer.util.FileUtils.createFolderIfAbsent;

public class GitUtil {

    private static final Logger logger = LoggerFactory.getLogger(GitUtil.class);

    private static GitHub GITHUB = null;
    private static final String GITHUB_URL = "https://github.com/";

    public static String getNearestTagCommitIdFromLocalGit(String SHAId, Git git) {
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
            logger.error("Error occurred", e);
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

    public static String getNearestTagCommitId(String cloneUrl, String commitId) {
        String nearestTagCommitId = null;

        try {
            GitHub gitHub = GitUtil.connectGithub();
            String repositoryName = GitUtil.extractRepositoryName(cloneUrl);
            GHRepository ghRepository = gitHub.getRepository(repositoryName);

            PagedIterable<GHTag> tagList = ghRepository.listTags();
            GHCommit ghCommit = ghRepository.getCommit(commitId);
            Date commitDate = ghCommit.getCommitDate();

            List<GHTag> eligibleTagList = new ArrayList<>();

            for (GHTag ghTag: tagList) {
                if (ghTag.getCommit().getCommitDate().getTime() >= ghCommit.getCommitDate().getTime()) {
                    eligibleTagList.add(ghTag);
                } else {
                    break;
                }
            }

            Collections.reverse(eligibleTagList);

            for (GHTag ghTag: eligibleTagList) {
                PagedIterable<GHCommit> commitListInTag = ghRepository.queryCommits().from(ghTag.getName())
                        .since(getPreviousDay(commitDate)).until(ghTag.getCommit().getCommitDate()).list();

                for (GHCommit commitInTag: commitListInTag) {
                    if (commitInTag.getSHA1().equals(ghCommit.getSHA1())) {
                        nearestTagCommitId = ghTag.getCommit().getSHA1();
                        break;
                    }
                }

                if (Objects.nonNull(nearestTagCommitId)) {
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error occurred", e);
        }

        return Objects.nonNull(nearestTagCommitId) ? nearestTagCommitId : commitId;
    }

    private static Date getPreviousDay(Date date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        localDateTime = localDateTime.minusDays(1);

        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
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

    public static Map<Path, String> populateFileContents(GHTree ghTree,
                                                         List<String> matchingFileNameList,
                                                         List<String> matchingFileExtensionList,
                                                         List<String> exclusionDirectoryList) {
        Map<Path, String> pomFileContentsMap = new HashMap<>();

        try {
            populateFileContents("", ghTree, pomFileContentsMap, matchingFileNameList, matchingFileExtensionList, exclusionDirectoryList);
        } catch (IOException e) {
            logger.error("Error occurred", e);
        }

        return pomFileContentsMap;
    }

    public static GitHub connectGithub() throws IOException {
        if (Objects.isNull(GITHUB)) {
            GITHUB = new GitHubBuilder()
                    .withOAuthToken(PropertyReader.getProperty("github.oauth.token"))
                    .build();
        }

        return GITHUB;
    }

    public static String extractRepositoryName(String cloneURL) {
        int hostLength = 0;
        if (cloneURL.startsWith(GITHUB_URL)) {
            hostLength = GITHUB_URL.length();
        }

        int indexOfDotGit = cloneURL.length();
        if (cloneURL.endsWith(".git")) {
            indexOfDotGit = cloneURL.indexOf(".git");
        } else if (cloneURL.endsWith("/")) {
            indexOfDotGit = cloneURL.length() - 1;
        }

        return cloneURL.substring(hostLength, indexOfDotGit);
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
                logger.error("Error occurred", e);
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

    public static boolean isFileExists(String cloneUrl, String matchingFileName) {
        try {
            GitHub gitHub = GitUtil.connectGithub();
            String repositoryName = GitUtil.extractRepositoryName(cloneUrl);
            GHRepository ghRepository = gitHub.getRepository(repositoryName);

            GHTree ghTree = ghRepository.getTree("HEAD");

            for (GHTreeEntry ghTreeEntry : ghTree.getTree()) {
                if (matchingFileName.contains(ghTreeEntry.getPath())) {
                    return true;
                }
            }

        } catch (IOException e) {
            logger.error("Error occurred", e);
        }

        return false;
    }

    private static void populateFileContents(String path, GHTree ghTree,
                                             Map<Path, String> pomFileContentsMap,
                                             List<String> matchingFileNameList,
                                             List<String> matchingFileExtensionList,
                                             List<String> exclusionDirectoryList)
            throws IOException {
        for (GHTreeEntry ghTreeEntry : ghTree.getTree()) {
            if (ghTreeEntry.getType().equals("tree") && !exclusionDirectoryList.contains(ghTreeEntry.getPath())) {
                populateFileContents(path.concat(ghTreeEntry.getPath()).concat("\\"), ghTreeEntry.asTree(),
                        pomFileContentsMap, matchingFileNameList, matchingFileExtensionList, exclusionDirectoryList);
            } else {
                if (matchingFileNameList.contains(ghTreeEntry.getPath())
                        || matchingFileExtensionList.stream().anyMatch(ext -> ghTreeEntry.getPath().contains(ext))) {
                    pomFileContentsMap.put(Paths.get(path.concat(ghTreeEntry.getPath())), FileUtils.getFileContent(ghTreeEntry.readAsBlob()));
                }
            }
        }
    }

    private static List<RevCommit> getMergedCommitList(Repository repo) {
        return io.vavr.control.Try.of(() -> {
            RevWalk walk = new RevWalk(repo);
            walk.markStart(walk.parseCommit(repo.resolve("HEAD")));
            walk.setRevFilter(RevFilter.ONLY_MERGES);
            return walk;
        }).map(walk -> {
            Iterator<RevCommit> iterator = walk.iterator();
            List<RevCommit> l = new ArrayList<>();
            while (iterator.hasNext()) {
                l.add(iterator.next());
            }
            walk.dispose();
            walk.close();
            return l;
        }).getOrElse(new ArrayList<>());
    }

}
