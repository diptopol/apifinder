package ca.concordia.jaranalyzer.SourceCodeAnalyzer;

import com.jasongoodwin.monads.Try;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplication;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitseffectivepom.CommitsEffectivePomManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationManager;

public class Analyze {


    private String jarsPath;
    private JarAnalysisApplication app;
    private JarInformationManager jm;
    private PackageInformationManager pkgM;

    private CommitsEffectivePomManager cmtEffM;


    public Analyze(JarAnalysisApplication app, String path, String projectName, String sha){

        JarInformationManager jm = app.getOrThrow(JarInformationManager.class);
        this.app = app;
        this.jm = this.app.getOrThrow(JarInformationManager.class);
        pkgM = app.getOrThrow(PackageInformationManager.class);

        cmtEffM = app.getOrThrow(CommitsEffectivePomManager.class);


        Try.ofFailable(() -> getJavaFiles(path+ projectName + "/")).onFailure(e -> e.printStackTrace())
            .onSuccess(files -> {
                JarInformation ji = jm.persist(new JarInformationImpl()
                        .setArtifactId(projectName).setGroupId("source code").setVersion(sha));
                files.parallelStream().forEach(f -> Try.ofFailable(() -> getCompilationUnitTree(f.getAbsolutePath()))
                                .onSuccess(cu -> analyzeCode(cu,ji.getId()))
                );
            });

//
    }

    private static Collection<File> getJavaFiles(String path) {
        return FileUtils.listFiles(new File(path ),
                new RegexFileFilter("([^\\s]+(?=\\.(java))\\.\\2)"),
                DirectoryFileFilter.DIRECTORY);
    }

    public void analyzeCode(CompilationUnitTree cu, int jarID){
        Optional<PackageInformation> o_pkgI =pkgM.stream().filter(p -> p.getName().equals(cu.getPackageName().toString())
                && p.getJarId() == jarID).findFirst();
        PackageInformation pkgI;
        pkgI = o_pkgI.orElseGet(() -> pkgM.persist(new PackageInformationImpl()
                .setName(cu.getPackageName().toString())
                .setJarId(jarID)));
        new ClassScanner(pkgI.getName(), app).scan(cu.getTypeDecls(),(int)pkgI.getId());

    }


//    public CompilationUnitWorld createCuWorld(CompilationUnit cu, String fileName){
//
//        List<ImportDeclaration> imports = cu.imports();
//        List<String> importedTypes = new ArrayList<>();
//        List<String> importedOnDemandTypes= new ArrayList<>();
//        for(ImportDeclaration importDeclaration : imports) {
//            importedTypes.add(importDeclaration.getName().getFullyQualifiedName());
//            if(importDeclaration.isOnDemand()){
//                importedOnDemandTypes.add(importDeclaration.getName().getFullyQualifiedName());
//            }
//        }
//
//        return CompilationUnitWorld.newBuilder()
//                .setPackage(Optional.ofNullable(cu.getPackage().getName()).map(x->x.toString()).orElse(""))
//                .setFileName(fileName)
//                .addAllImportsStatements(importedTypes)
//                .addAllImportsOnDemand(importedOnDemandTypes)
//                .addAllClasses().build();
//
////        Optional<PackageInformation> o_pkgI =pkgM.stream().filter(p -> p.getName().equals(cu.getPackageName().toString())
////                && p.getJarId() == jarID).findFirst();
////        PackageInformation pkgI;
////        pkgI = o_pkgI.orElseGet(() -> pkgM.persist(new PackageInformationImpl()
////                .setName(cu.getPackageName().toString())
////                .setJarId(jarID)));
//
//
//    }

    static class JFileObj extends SimpleJavaFileObject {
        private String text;

        protected JFileObj(String text) {
            super(URI.create(""), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    public static CompilationUnitTree getCompilationUnitTree(String path) throws IOException {
        final String code = readFile(path);
        JavaFileObject jobj = new JFileObj(code);
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        final JavaFileManager fm = tool.getStandardFileManager(null, null, null);
        JavacTask ct = (JavacTask) tool.getTask(null, fm, null, null,
                null, Arrays.asList(jobj));
        return ct.parse().iterator().next();
    }

    public static Optional<CompilationUnitTree> getCompilationUnitTreeFromCode(String code) {
        //final String code = readFile(path);
        try {
            JavaFileObject jobj = new JFileObj(code);
            final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
            final JavaFileManager fm = tool.getStandardFileManager(null, null, null);
            JavacTask ct = (JavacTask) tool.getTask(null, fm, null, null,
                    null, Arrays.asList(jobj));
            return Optional.of(ct.parse().iterator().next());
        }
        catch(Throwable e){
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static Optional<CompilationUnitTree> getCUTree(String path) {
        try {
            final String code = readFile(path);
            JavaFileObject jobj = new JFileObj(code);
            final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
            final JavaFileManager fm = tool.getStandardFileManager(null, null, null);
            JavacTask ct = (JavacTask) tool.getTask(null, fm, null, null,
                    null, Arrays.asList(jobj));
            return Optional.of(ct.parse().iterator().next());
        }catch (Exception e){
            e.printStackTrace();
            return Optional.empty();
        }

    }


    public static Optional<File> containsClass(String dirPath, final String clsName){
        Try<Collection<File>> javaFiles = Try.ofFailable(() -> getJavaFiles(dirPath)).onFailure(Throwable::printStackTrace);

        if(!javaFiles.isSuccess())
            return Optional.empty();

        return javaFiles.getUnchecked().stream()
             //   .peek(f -> System.out.println("searching in: " + f.getAbsolutePath()))
                .filter(f -> getCUTree(f.getAbsolutePath())
                   .map(cu -> new ContainsClass(clsName).scan(cu,null)).orElse(false))
                       .findFirst();
    }

    public static Optional<File> containsClassH(String dirPath, final String clsName){
        Try<Collection<File>> javaFiles = Try.ofFailable(() -> getJavaFiles(dirPath)).onFailure(Throwable::printStackTrace);

        if(!javaFiles.isSuccess())
            return Optional.empty();

        final String searchFileHeursitc = clsName.contains(".") ? clsName.substring(0,clsName.lastIndexOf(".")) : clsName;
        final String className = clsName.contains(".") ? clsName.substring(clsName.indexOf(".")+1) : clsName;
        final Collection<File> files = javaFiles.getUnchecked();
        return files.stream()
                .filter(x -> x.getName().contains(searchFileHeursitc))
           //     .peek(f -> System.out.println("H searching in: " + f.getAbsolutePath() + " " + f.getName()))
                .filter(f -> getCUTree(f.getAbsolutePath()).map(cu -> new ContainsClass(className).scan(cu, null))
                        .orElse(false))
                .findFirst();



    }





    static String readFile(String path)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
