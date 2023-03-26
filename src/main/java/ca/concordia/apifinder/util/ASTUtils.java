package ca.concordia.apifinder.util;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.Map;

/**
 * @author Diptopol
 * @since 3/22/2023 3:08 PM
 */
public class ASTUtils {

    public static CompilationUnit getCompilationUnit(String sourceCode, String javaVersion) {
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(getJavaCoreVersion(javaVersion), options);
        parser.setCompilerOptions(options);

        parser.setSource(sourceCode.toCharArray());

        return (CompilationUnit) parser.createAST(null);
    }

    public static String getJavaCoreVersion(String javaVersion) {
        if (javaVersion.equals("8")) {
            return JavaCore.VERSION_1_8;
        } else if (javaVersion.equals("11")) {
            return JavaCore.VERSION_11;
        } else {
            return JavaCore.VERSION_1_8;
        }
    }

}
