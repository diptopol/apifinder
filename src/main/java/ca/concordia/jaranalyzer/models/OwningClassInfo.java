package ca.concordia.jaranalyzer.models;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 3/23/2022 1:23 PM
 */
public class OwningClassInfo {

    private final String owningQualifiedClassName;
    private final List<Set<String>> qualifiedClassNameSetInHierarchy;

    public OwningClassInfo(String owningQualifiedClassName, List<Set<String>> qualifiedClassNameSetInHierarchy) {
        this.owningQualifiedClassName = owningQualifiedClassName;
        this.qualifiedClassNameSetInHierarchy = qualifiedClassNameSetInHierarchy;
    }

    public String getOwningQualifiedClassName() {
        return owningQualifiedClassName;
    }

    public List<Set<String>> getQualifiedClassNameSetInHierarchy() {
        return qualifiedClassNameSetInHierarchy;
    }

    public Set<String> getAvailableQualifiedClassNameSet() {
        return qualifiedClassNameSetInHierarchy.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

}
