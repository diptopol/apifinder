package ca.concordia.jaranalyzer.util;

import static ca.concordia.jaranalyzer.util.FileUtils.createFolderIfAbsent;
import static java.util.stream.Collectors.toMap;


import com.jasongoodwin.monads.Try;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.function.Predicate;

public class GitUtil {

    public static Try<Git> tryCloningRepo(String projectName, String cloneLink, Path pathToProject) {
        createFolderIfAbsent(pathToProject);
        return Try.ofFailable(() -> Git.open(pathToProject.resolve(projectName).toFile()))
                .onFailure(e -> System.out.println("Did not find " + projectName + " at" + pathToProject.toString()))
                .orElseTry(() ->
                        Git.cloneRepository().setURI(cloneLink).setDirectory(pathToProject.resolve(projectName).toFile()).call())
                .onFailure(e -> System.out.println("Could not clone " + projectName));

    }

    public static Map<String, String> readProjects(String path){
        try {
            return Files.readAllLines(Paths.get(path)).parallelStream()
                    .map(e -> new SimpleImmutableEntry<>(e.split(",")[0], e.split(",")[1]))
                    .collect(toMap(e -> e.getKey(), e -> e.getValue()));
        }catch (Exception e){
            System.out.println("Could not read projects");
            throw new RuntimeException("Could not read projects");
        }
    }

    public static List<RevCommit> getCommits(Git git, RevSort order) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        String input = "2015-01-01" ;
        return Try.ofFailable(() -> {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));
            walk.sort(order);
            walk.setRevFilter(CommitTimeRevFilter.after(ft.parse(input)));
            return walk;
        })
                .map(walk -> {
                    Iterator<RevCommit> iter = walk.iterator();
                    List<RevCommit> l = new ArrayList<>();
                    while(iter.hasNext()){
                        l.add(iter.next()); }
                    walk.dispose();
                    return l;
                })
                .onSuccess(l -> System.out.println("Total number of commits found : " + l.size()))
                .onFailure(Throwable::printStackTrace)

                .orElse(new ArrayList<>());
    }

    public static CompilationUnit getCuFor(String content){
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(false);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setSource(content.toCharArray());
        return  (CompilationUnit)parser.createAST(null);
    }

    public static String getFileContent(Repository repository, TreeWalk treeWalk) throws IOException {
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repository.open(objectId);
        StringWriter writer = new StringWriter();
        IOUtils.copy(loader.openStream(), writer);
        return Optional.ofNullable(writer.toString()).orElse("");
    }

    public static Optional<RevCommit> findCommit(String SHAId, Repository repo) {

        List<RevCommit> mergeCommits = io.vavr.control.Try.of(() -> {
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


    public static Repository getRepository(String projectName, String cloneLink, Path pathToProject) {
        Repository repo;
        if (Files.exists(pathToProject))
            repo = Try.ofFailable(() -> Git.open(pathToProject.resolve(projectName).toFile()))
                    .orElseThrow(() -> new RuntimeException("Could not open " + projectName)).getRepository();
        else repo = tryCloningRepo(projectName, cloneLink, pathToProject)
                .orElseThrow(() -> new RuntimeException("Could not clone" + projectName)).getRepository();
        return repo;
    }
}
