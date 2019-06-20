package ca.concordia.jaranalyzer.SourceCodeAnalyzer;


import static java.util.stream.Collectors.toSet;

import com.T2R.common.Util;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreeScanner;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class ImportScanner extends TreeScanner<Set<String>, Void> {

    public Set<String> visitImport(ImportTree ct, Void state) {
        return Stream.of(ct.getQualifiedIdentifier().toString()).collect(toSet());
    }

    public Set<String> reduce(Set<String> r1, Set<String> r2) {
        return Util.nullHandleReduce(r1, r2, (a, b) -> Stream.concat(a.stream(), b.stream())
                .collect(toSet()), new HashSet<>());
    }

}






