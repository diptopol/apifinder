package ca.concordia.jaranalyzer;

public class RandomCode {


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



//        ps.entrySet().stream().forEach(p -> {
//
//                final List<RevCommit> commits = tryGettingCommits(p);
//
//                                commits.forEach(c ->
//                                        Try.ofFailable(() -> )
//                                                .onFailure(e -> {
//                                                    System.out.println("Could not checkout : " + c.getId().getName());
//                                                    e.printStackTrace();
//                                                })
//                                                .map(r -> {
//
//                                                    return
//                                                }
//                                                )
//
//                                    );
//                                }
//                        );
//            }
//;
//    }



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




}
