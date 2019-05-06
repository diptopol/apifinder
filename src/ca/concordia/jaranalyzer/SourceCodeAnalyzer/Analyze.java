package ca.concordia.jaranalyzer.SourceCodeAnalyzer;

import static ca.concordia.jaranalyzer.Runner.pkgM;

import com.jasongoodwin.monads.Try;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import ca.concordia.jaranalyzer.DBModels.JarAnalysisApplication;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.jarinformation.JarInformationManager;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationImpl;

public class Analyze {




    public Analyze(JarAnalysisApplication app, String path, String projectName, String sha){

        JarInformationManager jm = app.getOrThrow(JarInformationManager.class);

        Try.ofFailable(() -> FileUtils.listFiles(new File(path + projectName + "/"),
                new RegexFileFilter("([^\\s]+(?=\\.(java))\\.\\2)"),
                DirectoryFileFilter.DIRECTORY)).onFailure(e -> e.printStackTrace())
            .onSuccess(files -> {
                JarInformation ji = jm.persist(new JarInformationImpl()
                        .setArtifactId(projectName).setGroupId("source code").setVersion(sha));
                files.parallelStream().forEach(f -> Try.ofFailable(() -> getCompilationUnitTree(f.getAbsolutePath()))
                                .onSuccess(cu -> analyzeCode(cu,ji.getId()))
                );
            });
    }

    public void analyzeCode(CompilationUnitTree cu, int jarID){
        Optional<PackageInformation> o_pkgI =pkgM.stream().filter(p -> p.getName().equals(cu.getPackageName().toString())
                && p.getJarId() == jarID).findFirst();
        PackageInformation pkgI;
        pkgI = o_pkgI.orElseGet(() -> pkgM.persist(new PackageInformationImpl()
                .setName(cu.getPackageName().toString())
                .setJarId(jarID)));
        new ClassScanner(pkgI.getName()).scan(cu.getTypeDecls(),(int)pkgI.getId());

    }

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

    static CompilationUnitTree getCompilationUnitTree(String path) throws IOException, URISyntaxException {
        final String code = readFile(path);
        JavaFileObject jobj = new JFileObj(code);
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        final JavaFileManager fm = tool.getStandardFileManager(null, null, null);
        JavacTask ct = (JavacTask) tool.getTask(null, fm, null, null,
                null, Arrays.asList(jobj));
        return ct.parse().iterator().next();
    }




    static String readFile(String path)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
