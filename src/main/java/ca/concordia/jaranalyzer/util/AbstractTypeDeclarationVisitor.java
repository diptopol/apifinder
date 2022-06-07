package ca.concordia.jaranalyzer.util;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Diptopol
 * @since 6/3/2022 4:22 PM
 */
public class AbstractTypeDeclarationVisitor extends ASTVisitor {

    private List<AbstractTypeDeclaration> abstractTypeDeclarationList = new ArrayList<>();

    @Override
    public boolean visit(TypeDeclaration node) {
        abstractTypeDeclarationList.add(node);

        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        abstractTypeDeclarationList.add(node);

        return true;
    }

    public List<AbstractTypeDeclaration> getAbstractTypeDeclarationList() {
        return abstractTypeDeclarationList;
    }

}
