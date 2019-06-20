package ca.concordia.jaranalyzer.SourceCodeAnalyzer;

import static com.T2R.common.Util.nullHandleReduce;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreeScanner;

public class ContainsClass extends TreeScanner<Boolean, Void> {

    private String className;

    public ContainsClass(String className){
        this.className = className;
    }

    public Boolean visitClass(ClassTree ct, Void state){
        super.visitClass(ct,state);

        return ct.getSimpleName().contentEquals(className);
    }
    public Boolean reduce(Boolean b1, Boolean b2){
        return nullHandleReduce(b1, b2,(a,b) -> a||b, false);
    }


    public class Boo {

    }


}


//d7a8ec278d82466bef86eecffa03ac8e7c3ad557



