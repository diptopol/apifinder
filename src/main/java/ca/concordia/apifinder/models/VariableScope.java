package ca.concordia.apifinder.models;

/**
 * @author Diptopol
 * @since 9/19/2021 6:28 PM
 */
public class VariableScope {

    private final int startOffset;
    private final int endOffset;

    public VariableScope(int startOffset, int endOffset) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + endOffset;
        result = prime * result + startOffset;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        VariableScope other = (VariableScope) obj;

        if (endOffset != other.endOffset)
            return false;

        if (startOffset != other.startOffset)
            return false;

        return true;
    }

    @Override
    public String toString() {
        return "VariableScope{" +
                "startOffset=" + startOffset +
                ", endOffset=" + endOffset +
                '}';
    }

}
