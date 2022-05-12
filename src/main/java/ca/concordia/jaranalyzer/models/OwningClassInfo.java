package ca.concordia.jaranalyzer.models;

import ca.concordia.jaranalyzer.models.typeInfo.FormalTypeParameterInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 3/23/2022 1:23 PM
 */
public class OwningClassInfo {

    private final List<String> enclosingClassNameList;
    private final List<Set<String>> qualifiedClassNameSetInHierarchy;
    private final List<String> classQNameDeclarationOrderList;

    private List<FormalTypeParameterInfo> accessibleFormalTypeParameterList;

    public OwningClassInfo(List<String> enclosingClassNameList,
                           List<Set<String>> qualifiedClassNameSetInHierarchy,
                           List<String> classQNameDeclarationOrderList) {

        assert enclosingClassNameList.size() > 0;

        this.enclosingClassNameList = enclosingClassNameList;
        this.qualifiedClassNameSetInHierarchy = qualifiedClassNameSetInHierarchy;
        this.classQNameDeclarationOrderList = classQNameDeclarationOrderList;
    }

    public OwningClassInfo(List<String> enclosingClassNameList,
                           List<Set<String>> qualifiedClassNameSetInHierarchy,
                           List<String> classQNameDeclarationOrderList,
                           List<FormalTypeParameterInfo> accessibleFormalTypeParameterList) {

        assert enclosingClassNameList.size() > 0;

        this.enclosingClassNameList = enclosingClassNameList;
        this.qualifiedClassNameSetInHierarchy = qualifiedClassNameSetInHierarchy;
        this.classQNameDeclarationOrderList = classQNameDeclarationOrderList;
        this.accessibleFormalTypeParameterList = accessibleFormalTypeParameterList;
    }

    public String getOuterMostClassName() {
        return this.enclosingClassNameList.get(this.enclosingClassNameList.size() - 1);
    }

    public List<Set<String>> getQualifiedClassNameSetInHierarchy() {
        return qualifiedClassNameSetInHierarchy;
    }

    public List<String> getClassQNameDeclarationOrderList() {
        return classQNameDeclarationOrderList;
    }

    public List<FormalTypeParameterInfo> getAccessibleFormalTypeParameterList() {
        return accessibleFormalTypeParameterList;
    }

    public void setAccessibleFormalTypeParameterList(List<FormalTypeParameterInfo> accessibleFormalTypeParameterList) {
        this.accessibleFormalTypeParameterList = accessibleFormalTypeParameterList;
    }

    public Set<String> getAvailableQualifiedClassNameSet() {
        return qualifiedClassNameSetInHierarchy.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

}
