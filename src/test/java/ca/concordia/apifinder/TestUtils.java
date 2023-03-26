package ca.concordia.apifinder;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Diptopol
 * @since 9/19/2021 12:21 PM
 */
public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    public static CompilationUnit getCompilationUnitFromFile(String filePath) {
        CompilationUnit compilationUnit = null;

        try {
            String sourceCode = readFile(filePath);
            compilationUnit = getCompilationUnit(sourceCode);

        } catch (Exception e) {
            logger.error("Failed in refactoring extraction", e);
        }

        return compilationUnit;
    }

    public static CompilationUnit getCompilationUnit(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);

        parser.setSource(sourceCode.toCharArray());

        return (CompilationUnit) parser.createAST(null);
    }

    public static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded);
    }

}
