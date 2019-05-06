package ca.concordia.jaranalyzer.SourceCodeAnalyzer;


import static ca.concordia.jaranalyzer.Runner.clsM;
import static ca.concordia.jaranalyzer.Runner.fldM;
import static ca.concordia.jaranalyzer.Runner.mthdArgM;
import static ca.concordia.jaranalyzer.Runner.mthdM;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import java.util.Set;

import javax.lang.model.element.Modifier;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.fieldinformation.FieldInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.MethodArgTypeInformationImpl;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodinformation.MethodInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodinformation.MethodInformationImpl;

public class ClassScanner extends TreeScanner<Void, Integer> {

    private String packageNAme ;

    public ClassScanner(String packageName){
        this.packageNAme = packageName;
    }

    public Void visitClass(ClassTree ct, Integer state){
        Set<Modifier> modifiers = ct.getModifiers().getFlags();
        String name = ct.getSimpleName().toString();


        ClassInformation ci = clsM.persist(new ClassInformationImpl()
                .setQualifiedName(packageNAme + name)
                .setName(name)
                .setAccessModifiers(getAccessModifier(modifiers))
                .setIsAbstract(modifiers.contains(Modifier.ABSTRACT))
                .setType(packageNAme + name)
                .setPackageId(state));
        new MethodScanner().scan(ct, ci.getId());
        handleFields(ct, ci);
        return null;
    }

    private void handleFields(ClassTree ct, ClassInformation ci) {
         ct.getMembers().stream()
                .filter(x -> x.getKind().equals(Kind.VARIABLE))
                .map(x -> (VariableTree) x)
                .map(x -> new FieldInformationImpl().setAccessModifier(getAccessModifier(x.getModifiers().getFlags()))
                        .setName(x.getName().toString()).setType(x.getType().toString())
                        .setIsStatic(x.getModifiers().getFlags().contains(Modifier.STATIC))
                        .setClassId(ci.getId()))
                 .forEach(fldM::persist);

    }


    private class MethodScanner extends TreeScanner<Void, Long>{
        public Void visitMethod(MethodTree m, Long classID){
            Set<Modifier> modifiers = m.getModifiers().getFlags();
            MethodInformation mthd = mthdM.persist(new MethodInformationImpl()
                    .setClassId(classID)
                    .setName(m.getName().toString())
                    .setReturnType(m.getReturnType() != null ? m.getReturnType().toString() : "")
                    .setAccessModifiers(getAccessModifier(modifiers))
                    .setIsStatic(modifiers.contains(Modifier.STATIC))
                    .setIsSynchronized(modifiers.contains(Modifier.SYNCHRONIZED))
                    .setIsAbstract(modifiers.contains(Modifier.ABSTRACT)));
            return new MethodArgScanner().scan(m.getParameters(),mthd.getId());
        }
    }


    private class MethodArgScanner extends TreeScanner<Void, Long>{
        public Void visitVariable(VariableTree m, Long mthdID){
            mthdArgM.persist(new MethodArgTypeInformationImpl()
                    .setMethodId(mthdID)
                    .setType(m.getType().toString()));
            return null;
        }
    }


    private String getAccessModifier(Set<Modifier> modifiers) {
        return modifiers.contains(Modifier.PRIVATE)
                ? Modifier.PRIVATE.toString()
                : (modifiers.contains(Modifier.PUBLIC)
                ? Modifier.PUBLIC.toString()
                : (modifiers.contains(Modifier.PROTECTED)
                ? Modifier.PROTECTED.toString()
                : Modifier.DEFAULT.toString()));
    }

}






