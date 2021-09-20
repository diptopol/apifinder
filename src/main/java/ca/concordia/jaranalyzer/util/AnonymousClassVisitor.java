package ca.concordia.jaranalyzer.util;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Diptopol
 * @since 9/19/2021 11:12 PM
 */
public class AnonymousClassVisitor extends ASTVisitor {

    private final List<AnonymousClassDeclaration> anonymousClassDeclarationList = new ArrayList<>();

    @Override
    public boolean visit(AnonymousClassDeclaration anonymousClassDeclaration) {
        anonymousClassDeclarationList.add(anonymousClassDeclaration);

        return true;
    }

    public List<AnonymousClassDeclaration> getAnonymousClassDeclarationList() {
        return this.anonymousClassDeclarationList;
    }

}
