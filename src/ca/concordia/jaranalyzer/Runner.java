package ca.concordia.jaranalyzer;

import java.io.File;

public class Runner {
    public static void main(String args[]) {
        String s = new File("").getAbsolutePath().concat("../jars/codemodel-2.6.jar");
        new APIFinderImpl("com.sun.ccoooooodemodel","codemodel","2.6"

                , s
                ,"21a7a07b2dc634184fd7a81f239359bc07620dfd");

    }




//    private static List<DiffEntry> getDiffs(Repository repo, Git g, RevCommit c) throws GitAPIException, IOException {
//        return g.diff()
//                .setOldTree(prepareTreeParser(c.getParent(0).getId().getName(), repo))
//                .setNewTree(prepareTreeParser(c.getId().getName(), repo))
//                .call();
//    }
//
//    public static CanonicalTreeParser prepareTreeParser(String sha, Repository repository) throws IOException {
//        RevWalk walk = new RevWalk(repository) ;
//        RevCommit commit = walk.parseCommit(repository.resolve(sha));
//        RevTree tree = walk.parseTree(commit.getTree().getId());
//        CanonicalTreeParser treeParser = new CanonicalTreeParser();
//        ObjectReader reader = repository.newObjectReader();
//        treeParser.reset(reader, tree.getId());
//        walk.dispose();
//        return treeParser;
//    }
//
//
//    private static boolean checkoutCommit(Git g, RevCommit c) {
//        try {
//            g.checkout().setName(c.getId().getName()).call();
//            return true;
//        }catch(Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//    }
//
//    private static boolean containsFilesOFType(String direc, String type)  {
//        try {
//            return Files.find(Paths.get(direc), Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toString().endsWith(type))
//                    .findFirst().isPresent();
//        }catch (Exception e){
//            throw new RuntimeException("could not find " + direc);
//        }
//    }

}

