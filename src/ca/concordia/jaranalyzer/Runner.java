package ca.concordia.jaranalyzer;

import static ca.concordia.jaranalyzer.Util.getCommits;

import com.jasongoodwin.monads.Try;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplication;
import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplicationBuilder;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commits.Commits;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commits.CommitsImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commits.CommitsManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.projects.Projects;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.projects.ProjectsImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.projects.ProjectsManager;

public class Runner {
    static String path = "/Users/ameya/FinalResults/diffTools/Corpus/";
    public static void main(String args[]) {


        //getJarMavenId(path,"master");

        Map<String, String> ps = Util.readProjects(path + "projects.txt");

        final JarAnalysisApplication app = new JarAnalysisApplicationBuilder().build();
        final ProjectsManager prjctM = app.getOrThrow(ProjectsManager.class);
        final CommitsManager cmtM = app.getOrThrow(CommitsManager.class);
        File csvOutputFile = new File(path + "Status.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            ps.entrySet().stream().forEach(p -> {

                final Try<Git> gitRepo = Util.tryCloningRepo(p.getKey(), p.getValue(), path);
                gitRepo.map(gr -> {
                    List<RevCommit> cs = getCommits(gr, RevSort.COMMIT_TIME_DESC);
                    //   Collections.reverse(cs);
                    return cs;
                })
                        .onSuccess(cs -> {
                                    final Projects pr = new ProjectsImpl().setName(p.getKey())
                                            .setGitCloneLink(p.getValue())
                                            .setNoOfCommit(cs.size());
                                    prjctM.persist(pr);
                                    cs.forEach(c -> {
                                        final Commits cm = new CommitsImpl().setSha(c.getId().getName())
                                                .setProjectId(pr.getId())
                                                .setTime(new Timestamp(c.getCommitterIdent().getWhen().getTime()));
                                        cmtM.persist(cm);
                                        Try.ofFailable(() -> gitRepo.get().checkout().setName(c.getName()).call())
                                                .onFailure(e -> {
                                                    System.out.println("Could not checkout : " + cm.getSha());
                                                    e.printStackTrace();

                                                })
                                                //.map(r -> getJarIds(path, app, c.getId().getName()))
                                                .map(r -> getJarIds(path, app, c.getId().getName(), pr.getName() + "/"))
                                               // .onSuccess(ref -> System.out.println(c.getId().getName() + "   " + c.getCommitterIdent().getWhen()))
                                                //.onFailure(Throwable::printStackTrace)
                                                .onSuccess(l ->
                                                                pw.println(String.join(",", pr.getName(), cm.getSha(), "YAYYY!!!"))
                                                )
                                                .onFailure(e -> {
                                                    e.printStackTrace();;
                                                    pw.println(String.join(",", pr.getName(), cm.getSha(), "OW!!!"));
                                                })
                                                .orElse(new ArrayList<>());
                                    });
                                }
                        );
            });
        }catch (Exception e){
            System.out.println(e);
            e.printStackTrace();
        }
    }



    public static List<Integer> getJarIds(String projectPath, JarAnalysisApplication app, String sha, String prjct){
        APIFinderImpl a = new APIFinderImpl(projectPath,new JarAnalyzer(app,"/Users/ameya/FinalResults/diffTools/jars/"), sha, prjct);
        return a.getJarIDs();
    }

//    public static List<Integer> getJarMavenId(String projectPath, JarAnalysisApplication app,String commit) throws MavenInvocationException, IOException, XmlPullParserException {
//
//
//
//
//        System.out.println(commit);
//        final String prjct = "speedment/";
//        if(Stream.of("pom.xml",".java").allMatch(t -> {
//            try {
//                return containsFilesOFType(path+prjct,t);
//            } catch (IOException e) {
//
//                e.printStackTrace();
//                return false;
//            }
//        })) {
//            InvocationRequest request = new DefaultInvocationRequest();
//            request.setPomFile(new File(projectPath + prjct + "pom.xml"));
//            //mvn help:effective-pom -Doutput=/Users/ameya/FinalResults/diffTools/Corpus/efpom.xml
//            request.setGoals(Arrays.asList("help:effective-pom", "-Doutput=" + path+prjct+"effectivePom.xml"));
//            Invoker invoker = new DefaultInvoker();
//            invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.6.0/"));
//            List<String> res = new ArrayList<>();
//            InvocationResult result = invoker.execute(request);
//            Path p = Paths.get(projectPath + "Status.csv");
//            if (result.getExitCode() != 0) {
//                System.out.println("Effective pom generation Failed");
//                String str = String.join(",", prjct, commit, "OW!!!\n");
//                Files.write(p, str.getBytes(), StandardOpenOption.APPEND);
//                throw new MavenInvocationException("Could not generate effective pom");
//
//            } else {
//                String str = String.join(",", prjct, commit, "YAYYY!!!\n");
//                Files.write(p, str.getBytes(), StandardOpenOption.APPEND);
//                //  pw.println();
//                System.out.println(commit + "YAYYYYYYYYYYYYY");
//                MavenXpp3Reader mavenreader = new MavenXpp3Reader();
//
//                File effectivePomfile = new File( path+prjct+"effectivePom.xml") ;
//
//                Optional<Document> dd = getDocument(effectivePomfile);
//                Optional<NodeList> z = dd.map(d -> d.getElementsByTagName("project"));
//                z.ifPresent(nodeList -> System.out.println(nodeList.getLength()));
//                JarAnalyzer jarAnalyzer = new JarAnalyzer(app, "/Users/ameya/FinalResults/diffTools/jars/");
//                List<JarInfo> ji = jarAnalyzer.analyzeJarsFromEffectivePom(path + prjct + "effectivePom.xml");
//                ji.forEach(j -> {
//                    System.out.println(j.getName() + " " + j.getArtifactId() + " " + j.getGroupId() + " " + j.getVersion());
//                });
////                System.out.println(ji.size());
////                NodeList prjcts = z.get();
////                List<String> internalGroupIds = new ArrayList<>();
////                for(int i = 0 ; i < prjcts.getLength(); i++){
////                    Node x = prjcts.item(i);
////                    for(int j =0; j<x.getChildNodes().getLength(); j ++){
////                        Node xx = x.getChildNodes().item(j);
////                        if(xx.getNodeName().equals("groupId")){
////                            internalGroupIds.add(xx.getTextContent());
////                        }
////
////                    }
////                }
////                Map<String, Long> jiAnlys = ji.stream()
////                        .map(j -> j.getArtifactId() + "--" + j.getGroupId() + "--" + j.getVersion())
////                        .filter(j -> internalGroupIds.stream().noneMatch(j::contains))
////                        .collect(groupingBy(j -> j, counting()));
////
////                jiAnlys.entrySet().forEach(jii -> {
////                    System.out.println(jii.getKey() + " " + jii.getValue());
////                });
//
////                IntStream.range(0,z.get().getLength())
////                        .mapToObj(i -> z.get().item(i))
////                        .forEach(pr -> {
////                            System.out.println(pr.toString());
////
////                            System.out.println(pr.getTextContent());
////                        });
////
////
////
////                Model model = mavenreader.read(new FileReader(effectivePomfile));
////                List<Dependency> deps = model.getDependencies();
////                deps.addAll(model.getDependencyManagement().getDependencies());
////                System.out.println(commit);
////                for (Dependency d: deps) {
////                    System.out.println(d.getArtifactId() + "--" + d.getGroupId() + "--" + d.getVersion());
////                }
//
//
//
//
//
//
//
//
//            }
//        }
//
//        return Arrays.asList(1);
//
//        }

//            //mvn -o dependency:list dependency:copy-dependencies
//            // -DoutputDirectory=/Users/ameya/FinalResults/diffTools/ -DexcludeArtifactIds=com.speedment
//            // -DexcludeTransitive
//            // -fn
//            // | grep ":.*:.*:.*" | cut -d] -f2- | sed 's/:[a-z]*$//g' | sort -u
//            //dependency:get -Dartifact=groupId:artifactId:version
//            InvocationRequest request = new DefaultInvocationRequest();
//            request.setPomFile(new File(projectPath + prjct + "pom.xml"));
//            request.setGoals(Arrays.asList("dependency:copy-dependencies", "dependency:list",
//                    "-DexcludeTransitive",
//                    "-DoutputDirectory=/Users/ameya/FinalResults/diffTools/jarss/",
//                    "-DoutputFile=" + path + "speedmentJarAnalysis/" + commit + ".txt",
//                    "-DappendOutput=true", "-fn"));
//            Invoker invoker = new DefaultInvoker();
//            invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.6.0/"));
//            List<String> res = new ArrayList<>();
//            invoker.setOutputHandler(res::add);
//
//            res.forEach(System.out::println);
//            InvocationResult result = invoker.execute(request);
//
//
//            Path p = Paths.get(projectPath + "Status.csv");
//            if (result.getExitCode() != 0) {
//                System.out.println("Build Failed");
//                String str = String.join(",", prjct, commit, "OW!!!\n");
//                //, result.getExecutionException().toString());
//                Files.write(p, str.getBytes(), StandardOpenOption.APPEND);
//                //pw.println();
//                throw new MavenInvocationException("Could not build");
//
//            } else {
//                String str = String.join(",", prjct, commit, "YAYYY!!!\n");
//                Files.write(p, str.getBytes(), StandardOpenOption.APPEND);
//                //  pw.println();
//                System.out.println(commit + "YAYYYYYYYYYYYYY");
//            }
//        }else{





    public static boolean containsFilesOFType(String direc, String type) throws IOException {
        return Files.find(Paths.get(direc), Integer.MAX_VALUE,(p,a) -> a.isRegularFile() && p.toString().endsWith(type))
                .findFirst().isPresent();
    }


    private static Optional<Document> getDocument(File inputFile)  {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();
            return Optional.ofNullable(doc);
        }catch (Exception e){
            e.printStackTrace();
            return Optional.empty();
        }
    }


}



// Collections.reverse(commits);
//        System.out.println(commits.size());
//
//        getJarIds(path + "guava/").forEach(System.out::println);
//
////        int i = 0;
////        List<JarTFG> jars = new ArrayList<>();
//////        for(RevCommit commit: commits){
//////            System.out.println(commit.getId().getName());
//////        }
//
//        gitRepo.onSuccess(Git::close);





//    public static void foo(RevCommit c, Repository repo) throws IOException {
//        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
//        df.setRepository(repo);
//        df.setDiffComparator(RawTextComparator.DEFAULT);
//        df.setDetectRenames(true);
//        List<DiffEntry> diffs;
//        if(c.getParentCount() > 0) {
//            diffs = df.scan(c.getParent(0).getTree(), c.getTree());
//            filesChanged = diffs.size();
//            for (DiffEntry diff : diffs) {
//
//                for (Edit edit : df.toFileHeader(diff).toEditList()) {
//                    linesDeleted += edit.getEndA() - edit.getBeginA();
//                    linesAdded += edit.getEndB() - edit.getBeginB();
//                }
//            }
//        }
//    }
