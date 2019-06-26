package ca.concordia.jaranalyzer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplication;
import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplicationBuilder;


public class Runner {
    public static final String path = "/Users/ameya/FinalResults/diffTools/Corpus/";
    public static final String jars_path = "/Users/ameya/FinalResults/diffTools/jars/";
    public static final JarAnalysisApplication app = new JarAnalysisApplicationBuilder().build();
//    public static final JarInformationManager jm = app.getOrThrow(JarInformationManager.class);;
//    public static final PackageInformationManager pkgM = app.getOrThrow(PackageInformationManager.class);
//    public static final ClassInformationManager clsM = app.getOrThrow(ClassInformationManager.class);
//    public static final MethodInformationManager mthdM = app.getOrThrow(MethodInformationManager.class);
//    public static final FieldInformationManager fldM = app.getOrThrow(FieldInformationManager.class);
//    public static final MethodArgTypeInformationManager mthdArgM = app.getOrThrow(MethodArgTypeInformationManager.class);
//    public static final CommitsEffectivePomManager cmtEffM = app.getOrThrow(CommitsEffectivePomManager.class);
//    public static final ProjectsManager prjctM = app.getOrThrow(ProjectsManager.class);
//    public static final CommitsManager cmtM = app.getOrThrow(CommitsManager.class);

    public static void main(String args[]) throws Throwable {
     //   Map<String, String> ps = Util.readProjects(path + "projects.txt");



//        for (Entry<String, String> p : ps.entrySet()) {
//            final Try<Git> gitRepo = Util.tryCloningRepo(p.getKey(), p.getValue(), path)
//                    .onFailure(e -> e.printStackTrace());
//            if (gitRepo.isSuccess()) {
//                Git repo = gitRepo.get();
//                final List<RevCommit> cs =
//                        getCommits(repo, RevSort.COMMIT_TIME_DESC);
////                                .stream().filter(x -> x.getId().getName().equals(""))
////                        .collect(Collectors.toList());
//               // Collections.reverse(cs);
//
//                Projects pr = prjctM.stream().filter(x -> x.getName().equals(p.getKey())).findFirst()
//                        .orElseGet(()->prjctM.persist(new ProjectsImpl().setName(p.getKey())
//                                .setGitCloneLink(p.getValue())
//                                .setNoOfCommit(cs.size())));
//
//                for (RevCommit c : cs) {
//                    final Commits cm = new CommitsImpl().setSha(c.getId().getName())
//                            .setProjectId(pr.getId())
//                            .setTime(new Timestamp(c.getCommitterIdent().getWhen().getTime()))
//                            .setContainsJava(containsFilesOFType(path + p.getKey(), ".java"));
//
//                    boolean collectsJars = getDiffs(repo.getRepository(),repo,c)
//                            .stream().anyMatch(x -> x.getOldPath().contains("pom.xml") || x.getNewPath().contains("pom.xml"));
//
//                    if(collectsJars) {
//                        // All this dirtiness to recover from checkout exception
//                        if (!checkoutCommit(repo, c)) {
//                            boolean deleted = Files.deleteIfExists(Paths.get(path + p.getKey()));
//                            if (deleted) {
//                                repo = Util.tryCloningRepo(p.getKey(), p.getValue(), path)
//                                        .orElseThrow(() -> new RuntimeException("Could not clone again"));
//                                if (!checkoutCommit(repo, c))
//                                    cmtM.persist(cm.setCouldCheckout(false));
//                            } else {
//                                cmtM.persist(cm.setCouldCheckout(false));
//                            }
//                        }
//                        if (cmtM.stream().noneMatch(x -> c.getId().getName().equals(x.getSha())))
//                            cmtM.persist(cm.setCouldCheckout(true));
//                        if (cm.getContainsJava().isPresent() && cm.getContainsJava().getAsBoolean()) {
//                            //
//                            new Analyze(app, path, pr.getName(), c.getId().getName());
//                        }
//                    }
//                }
//            }
//        }
        new APIFinderImpl(new JarAnalyzer(app,jars_path));


    }




    private static List<DiffEntry> getDiffs(Repository repo, Git g, RevCommit c) throws GitAPIException, IOException {
        return g.diff()
                .setOldTree(prepareTreeParser(c.getParent(0).getId().getName(), repo))
                .setNewTree(prepareTreeParser(c.getId().getName(), repo))
                .call();
    }

    public static CanonicalTreeParser prepareTreeParser(String sha, Repository repository) throws IOException {
        RevWalk walk = new RevWalk(repository) ;
        RevCommit commit = walk.parseCommit(repository.resolve(sha));
        RevTree tree = walk.parseTree(commit.getTree().getId());
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        ObjectReader reader = repository.newObjectReader();
        treeParser.reset(reader, tree.getId());
        walk.dispose();
        return treeParser;
    }


    private static boolean checkoutCommit(Git g, RevCommit c) {
        try {
            g.checkout().setName(c.getId().getName()).call();
            return true;
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean containsFilesOFType(String direc, String type)  {
        try {
            return Files.find(Paths.get(direc), Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toString().endsWith(type))
                    .findFirst().isPresent();
        }catch (Exception e){
            throw new RuntimeException("could not find " + direc);
        }
    }

}

