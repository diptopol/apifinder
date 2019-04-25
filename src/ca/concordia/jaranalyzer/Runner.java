package ca.concordia.jaranalyzer;

import static ca.concordia.jaranalyzer.Util.getCommits;

import com.jasongoodwin.monads.Try;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

import java.util.List;

public class Runner {

    public static void main(String args[])  {


        //    Map<String, String> ps = Util.readProjects(path + "projects.txt");




        Try<Git> gitRepo = Util.tryCloningRepo("guava", "https://github.com/google/guava.git", "/Users/ameya/FinalResults/diffTools/Corpus/");
        List<RevCommit> commits = getCommits(gitRepo, RevSort.COMMIT_TIME_DESC);
        // Collections.reverse(commits);
        System.out.println(commits.size());

        getJarIds("/Users/ameya/FinalResults/diffTools/Corpus/" + "guava/").forEach(System.out::println);

//        int i = 0;
//        List<JarTFG> jars = new ArrayList<>();
////        for(RevCommit commit: commits){
////            System.out.println(commit.getId().getName());
////        }

        gitRepo.onSuccess(Git::close);

    }


    public static List<Integer> getJarIds(String projectPath){
        APIFinderImpl a = new APIFinderImpl(projectPath);
        return a.getJarIDs();

//        return jars.stream()
//                .map(j -> Dependency.newBuilder()
//                        .setArtifactID(j.getArtifactId())
//                        .setGroupID(j.getGroupId())
//                        .setVersion(j.getVersion()).build())
//                .collect(Collectors.toList());
    }

}
