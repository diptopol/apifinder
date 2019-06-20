package ca.concordia.jaranalyzer.SourceCodeAnalyzer;


import com.T2R.common.Models.TypeWorldOuterClass.TypeWorld.ClassWorld;
import com.sun.source.util.TreeScanner;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.List;

public class CreateClassWorld extends TreeScanner<List<ClassWorld>, Integer> {

    public static void getTypeDeclarations(CompilationUnit cu ){
        List<AbstractTypeDeclaration> topLeveltypes = cu.types();
        for(AbstractTypeDeclaration a : topLeveltypes){

        }
    }

}






