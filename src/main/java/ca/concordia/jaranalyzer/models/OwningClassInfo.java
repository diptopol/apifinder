package ca.concordia.jaranalyzer.models;

import java.util.Collections;
import java.util.Set;

/**
 * @author Diptopol
 * @since 3/23/2022 1:23 PM
 */
public class OwningClassInfo {

    private String owningQualifiedClassName;
    private Set<String> availableQualifiedClassNameSet;

    public OwningClassInfo(String owningQualifiedClassName) {
        this(owningQualifiedClassName, Collections.emptySet());
    }

    public OwningClassInfo(String owningQualifiedClassName, Set<String> availableQualifiedClassNameSet) {
        this.owningQualifiedClassName = owningQualifiedClassName;
        this.availableQualifiedClassNameSet = availableQualifiedClassNameSet;
    }

    public String getOwningQualifiedClassName() {
        return owningQualifiedClassName;
    }

    public Set<String> getAvailableQualifiedClassNameSet() {
        return availableQualifiedClassNameSet;
    }

}
