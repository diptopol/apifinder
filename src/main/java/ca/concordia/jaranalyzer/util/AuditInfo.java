package ca.concordia.jaranalyzer.util;

/**
 * @author Diptopol
 * @since 2/10/2023 2:18 AM
 */
public class AuditInfo {

    private int numberOfResolvedMethodCount;

    private int numberOfResolvedClassCount;

    private int numberOfResolvedFieldCount;

    private int numberOfResolvedMethodReference;

    public AuditInfo() {
    }

    public int getNumberOfResolvedMethodCount() {
        return numberOfResolvedMethodCount;
    }

    public int getNumberOfResolvedClassCount() {
        return numberOfResolvedClassCount;
    }

    public int getNumberOfResolvedFieldCount() {
        return numberOfResolvedFieldCount;
    }

    public int getNumberOfResolvedMethodReference() {
        return numberOfResolvedMethodReference;
    }

    public void incrementMethodCount() {
        this.numberOfResolvedMethodCount++;
    }

    public void incrementClassCount() {
        this.numberOfResolvedClassCount++;
    }

    public void incrementFieldCount() {
        this.numberOfResolvedFieldCount++;
    }

    public void incrementMethodReferenceCount() {
        this.numberOfResolvedMethodReference++;
    }

}
