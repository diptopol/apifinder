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
        new APIFinderImpl("com.sun.ccoooooodemodel","codemodel","2.6"
                , "/Users/ameya/FinalResults/diffTools/MigrationMiner/librariesClasses/jar/codemodel-2.6.zip"
                ,"21a7a07b2dc634184fd7a81f239359bc07620dfd");

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

