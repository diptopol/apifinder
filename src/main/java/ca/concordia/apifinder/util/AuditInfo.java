package ca.concordia.apifinder.util;

/**
 * @author Diptopol
 * @since 2/10/2023 2:18 AM
 */
public class AuditInfo {

    private int numberOfResolvedMethodCount;

    private int numberOfResolvedClassCount;

    private int numberOfResolvedFieldCount;

    private int numberOfResolvedMethodReferenceCount;

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

    public int getNumberOfResolvedMethodReferenceCount() {
        return numberOfResolvedMethodReferenceCount;
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
        this.numberOfResolvedMethodReferenceCount++;
    }

    public void aggregateOtherAuditInfo(AuditInfo otherAuditInfo) {
        this.numberOfResolvedMethodCount += otherAuditInfo.getNumberOfResolvedMethodCount();
        this.numberOfResolvedClassCount += otherAuditInfo.getNumberOfResolvedClassCount();
        this.numberOfResolvedFieldCount += otherAuditInfo.getNumberOfResolvedFieldCount();
        this.numberOfResolvedMethodReferenceCount += otherAuditInfo.getNumberOfResolvedMethodReferenceCount();
    }

    @Override
    public String toString() {
        return "AuditInfo{" +
                "numberOfResolvedMethodCount=" + numberOfResolvedMethodCount +
                ", numberOfResolvedClassCount=" + numberOfResolvedClassCount +
                ", numberOfResolvedFieldCount=" + numberOfResolvedFieldCount +
                ", numberOfResolvedMethodReferenceCount=" + numberOfResolvedMethodReferenceCount +
                '}';
    }
}
