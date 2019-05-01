public class DownloadingArtifactsByPomExample {
//
//    public static final String TARGET_LOCAL_REPOSITORY = "/Users/ameya/FinalResults/diffTools/jarss/";
//
//    public static void main(String[] args)
//            throws Exception {
//        File projectPomFile = Paths.get("/Users/ameya/FinalResults/diffTools/Corpus/speedment/", "pom.xml").toAbsolutePath().toFile();
//
//        System.out.printf("loading this sample project's Maven descriptor from %s\n", projectPomFile);
//        System.out.printf("local Maven repository set to %s\n",
//                Paths.get("", TARGET_LOCAL_REPOSITORY).toAbsolutePath());
//
//        RepositorySystem repositorySystem = getRepositorySystem();
//        RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem);
//        //DefaultModel
//
//
//        final DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()
//                .setModelResolver(new ProjectModelResolver(repositorySystemSession,,))
//                .setPomFile(projectPomFile);
//
//        ModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
//
//
//        ModelBuildingResult modelBuildingResult = modelBuilder.build(modelBuildingRequest);
//
//        Model model = modelBuildingResult.getEffectiveModel();
//        System.out.printf("Maven model resolved: %s, parsing its dependencies..\n", model);
//        model.getDependencies().forEach(d -> {
//            System.out.printf("processing dependency: %s\n", d);
//            Artifact artifact = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getType(),
//                    d.getVersion());
//            ArtifactRequest artifactRequest = new ArtifactRequest();
//            artifactRequest.setArtifact(artifact);
//            artifactRequest.setRepositories(getRepositories(repositorySystem, repositorySystemSession));
//
//            try {
//                ArtifactResult artifactResult = repositorySystem
//                        .resolveArtifact(repositorySystemSession, artifactRequest);
//                artifact = artifactResult.getArtifact();
//                System.out.printf("artifact %s resolved to %s\n", artifact, artifact.getFile());
//            } catch (ArtifactResolutionException e) {
//                System.err.printf("error resolving artifact: %s\n", e.getMessage());
//            }
//        });
//
//    }
//
//    public static RepositorySystem getRepositorySystem() {
//        DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();
//        serviceLocator
//                .addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
//        serviceLocator.addService(TransporterFactory.class, FileTransporterFactory.class);
//        serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);
//
//        serviceLocator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
//            @Override
//            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
//                System.err.printf("error creating service: %s\n", exception.getMessage());
//                exception.printStackTrace();
//            }
//        });
//
//        return serviceLocator.getService(RepositorySystem.class);
//    }
//
//    public static DefaultRepositorySystemSession getRepositorySystemSession(RepositorySystem system) {
//        DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils
//                .newSession();
//
//        LocalRepository localRepository = new LocalRepository(TARGET_LOCAL_REPOSITORY);
//        repositorySystemSession.setLocalRepositoryManager(
//                system.newLocalRepositoryManager(repositorySystemSession, localRepository));
//
//     //   repositorySystemSession.setRepositoryListener(new ConsoleRepositoryEventListener());
//
//        return repositorySystemSession;
//    }
//
//    public static List<RemoteRepository> getRepositories(RepositorySystem system,
//                                                            RepositorySystemSession session) {
//        return Arrays.asList(getCentralMavenRepository());
//    }
//
//    private static RemoteRepository getCentralMavenRepository() {
//        return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/")
//                .build();
//    }

}