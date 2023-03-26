package ca.concordia.apifinder.models;

import org.eclipse.jdt.core.dom.ImportDeclaration;

/**
 * @author Diptopol
 * @since 9/19/2021 12:23 PM
 */
public class ImportObject {

    private final String importName;
    private final boolean onDemand;
    private final boolean isStatic;

    public ImportObject(ImportDeclaration importDeclaration) {
        this.importName = importDeclaration.getName().getFullyQualifiedName();
        this.onDemand = importDeclaration.isOnDemand();
        this.isStatic = importDeclaration.isStatic();
    }

    public String getImportStatement() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("import ");

        if (isStatic) {
            stringBuilder.append("static ");
        }

        stringBuilder.append(this.importName);

        if (onDemand) {
            stringBuilder.append(".*");
        }

        return stringBuilder.toString();
    }

    public String getImportName() {
        return importName;
    }

    public boolean isOnDemand() {
        return onDemand;
    }

    public boolean isStatic() {
        return isStatic;
    }

}
