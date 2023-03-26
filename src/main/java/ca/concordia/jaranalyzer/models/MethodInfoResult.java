package ca.concordia.jaranalyzer.models;

import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.util.AuditInfo;

/**
 * @author Diptopol
 * @since 2/12/2023 3:23 AM
 */
public class MethodInfoResult {

    private MethodInfo methodInfo;

    private AuditInfo auditInfo;

    private Exception exception;

    public MethodInfoResult(MethodInfo methodInfo, AuditInfo auditInfo) {
        this.methodInfo = methodInfo;
        this.auditInfo = auditInfo;
    }

    public MethodInfoResult(AuditInfo auditInfo, Exception exception) {
        this.auditInfo = auditInfo;
        this.exception = exception;
    }

    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    public AuditInfo getAuditInfo() {
        return auditInfo;
    }

    public Exception getException() {
        return exception;
    }

}
