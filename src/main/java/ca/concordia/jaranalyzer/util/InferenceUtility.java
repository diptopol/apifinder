package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.ImportObject;
import ca.concordia.jaranalyzer.Models.VariableDeclarationDto;
import ca.concordia.jaranalyzer.Models.VariableScope;
import ca.concordia.jaranalyzer.TypeInferenceAPI;
import io.vavr.Tuple3;
import org.eclipse.jdt.core.dom.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 9/19/2021 12:17 PM
 */
public class InferenceUtility {

    public static List<String> getImportStatementList(CompilationUnit compilationUnit) {
        List<ImportDeclaration> importDeclarationList = compilationUnit.imports();

        return importDeclarationList.stream()
                .map(ImportObject::new)
                .map(ImportObject::getImportStatement)
                .collect(Collectors.toList());
    }

    public static List<VariableDeclarationDto> getFieldVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                                  String javaVersion,
                                                                                  List<String> importStatementList,
                                                                                  ASTNode node) {

        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(node);
        FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();

        return Arrays.stream(fieldDeclarations).map(fieldDeclaration -> {
            List<VariableDeclarationFragment> fragmentList = fieldDeclaration.fragments();

            return getVariableDeclarationDtoList(dependentJarInformationSet, javaVersion, importStatementList,
                    fieldDeclaration.getType(), fragmentList);
        }).flatMap(Collection::stream).collect(Collectors.toList());
    }

    public static ASTNode getTypeDeclaration(ASTNode node) {
        return getClosestASTNode(node, TypeDeclaration.class);
    }

    public static String getDeclaringClassQualifiedName(BodyDeclaration declaration) {
        String declaringClassQualifiedName = "";
        ASTNode parent = declaration.getParent();

        List<AnonymousClassDeclaration> anonymousClassDeclarationList = getAnonymousClassDeclarationList(declaration);

        while (parent != null) {
            if (parent instanceof CompilationUnit) {
                CompilationUnit cu = (CompilationUnit) parent;
                PackageDeclaration packageDeclaration = cu.getPackage();
                String packageName = packageDeclaration != null ? packageDeclaration.getName().getFullyQualifiedName() : "";

                //TODO: Need to understand why percentage is needed
                /*if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = packageDeclaration.getName().getFullyQualifiedName() + "%";
                } else {
                    declaringClassQualifiedName = packageDeclaration.getName().getFullyQualifiedName() + "%." + declaringClassQualifiedName;
                }*/

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = packageName;
                } else {
                    declaringClassQualifiedName = (!packageName.equals("") ? packageName + "." : "") + declaringClassQualifiedName;
                }

            } else if (parent instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) parent;
                String typeDeclarationName = typeDeclaration.getName().getIdentifier();

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = typeDeclarationName;
                } else {
                    declaringClassQualifiedName = typeDeclarationName + "#" + declaringClassQualifiedName;
                }
            } else if (parent instanceof AnonymousClassDeclaration) {
                AnonymousClassDeclaration anonymousClassDeclaration = (AnonymousClassDeclaration) parent;
                String anonymousClassName = anonymousClassDeclarationList.contains(anonymousClassDeclaration)
                        ? String.valueOf(anonymousClassDeclarationList.indexOf(anonymousClassDeclaration)) : "";

                if (declaringClassQualifiedName.equals("")) {
                    declaringClassQualifiedName = anonymousClassName;
                } else {
                    declaringClassQualifiedName = anonymousClassName + "#" + declaringClassQualifiedName;
                }
            }
            parent = parent.getParent();
        }

        return declaringClassQualifiedName;
    }

    public static ASTNode getClosestASTNode(ASTNode node, Class<? extends ASTNode> nodeClazz) {
        if (nodeClazz.isInstance(node)) {
            return node;
        }

        ASTNode parent = node.getParent();

        while (!(nodeClazz.isInstance(parent))) {
            parent = parent.getParent();
        }

        return parent;
    }

    private static List<AnonymousClassDeclaration> getAnonymousClassDeclarationList(BodyDeclaration declaration) {
        TypeDeclaration typeDeclaration = (TypeDeclaration) getTypeDeclaration(declaration);

        AnonymousClassVisitor anonymousClassVisitor = new AnonymousClassVisitor();
        typeDeclaration.accept(anonymousClassVisitor);

        return anonymousClassVisitor.getAnonymousClassDeclarationList();
    }

    private static List<VariableDeclarationDto> getVariableDeclarationDtoList(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                                              String javaVersion,
                                                                              List<String> importStatementList,
                                                                              Type declarationType,
                                                                              List<VariableDeclarationFragment> fragmentList) {

        String declarationTypeClassName = getTypeClassName(dependentJarInformationSet, javaVersion, importStatementList, declarationType);

        return fragmentList.stream().map(fragment -> {
            ASTNode scopedNode = getVariableDeclarationScopedNode(fragment);
            String name = fragment.getName().getFullyQualifiedName();

            int startOffset = fragment.getStartPosition();
            int endOffSet = startOffset + (scopedNode != null ? scopedNode.getLength() : 0);

            return new VariableDeclarationDto(name, declarationTypeClassName, new VariableScope(startOffset, endOffSet), declarationType);

        }).collect(Collectors.toList());
    }

    private static ASTNode getVariableDeclarationScopedNode(VariableDeclaration variableDeclaration) {
        if (variableDeclaration instanceof SingleVariableDeclaration) {
            return variableDeclaration.getParent();
        } else if (variableDeclaration instanceof VariableDeclarationFragment) {
            return variableDeclaration.getParent().getParent();
        }

        return null;
    }

    private static String getTypeClassName(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                           String javaVersion,
                                           List<String> importStatementList,
                                           Type type) {
        if (type == null) {
            return null;
        }

        if (type instanceof PrimitiveType) {
            return type.toString();

        } else if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            Type elementType = arrayType.getElementType();
            String elementTypeStr;

            if (!elementType.isPrimitiveType()) {
                if (elementType instanceof SimpleType) {
                    elementTypeStr = getTypeNameForSimpleType(dependentJarInformationSet, javaVersion, importStatementList, ((SimpleType) elementType));

                } else if (elementType instanceof QualifiedType) {
                    elementTypeStr = getTypeNameForQualifiedType(dependentJarInformationSet, javaVersion, importStatementList, (QualifiedType) elementType);
                } else {
                    throw new IllegalStateException();
                }

            } else {
                elementTypeStr = elementType.toString();
            }

            StringBuilder elementTypeStrBuilder = new StringBuilder(elementTypeStr);
            for (int i = 0; i < arrayType.getDimensions(); i++) {
                elementTypeStrBuilder.append("[]");
            }

            return elementTypeStrBuilder.toString();

        } else if (type instanceof SimpleType) {
            return getTypeNameForSimpleType(dependentJarInformationSet, javaVersion, importStatementList, ((SimpleType) type));

        } else if (type instanceof QualifiedType) {
            return getTypeNameForQualifiedType(dependentJarInformationSet, javaVersion, importStatementList, (QualifiedType) type);


        } else if (type instanceof ParameterizedType) {
            Type internalType = ((ParameterizedType) type).getType();

            if (internalType instanceof SimpleType) {
                return getTypeNameForSimpleType(dependentJarInformationSet, javaVersion, importStatementList, ((SimpleType) internalType));

            } else if (internalType instanceof QualifiedType) {
                return getTypeNameForQualifiedType(dependentJarInformationSet, javaVersion, importStatementList, (QualifiedType) internalType);

            } else {
                throw new IllegalStateException();
            }
        } else {
            return type.toString();
        }
    }

    private static String getTypeNameForSimpleType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                   String javaVersion,
                                                   List<String> importStatementList,
                                                   SimpleType simpleType) {
        String name = simpleType.getName().getFullyQualifiedName();

        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

/*        if (classInfoList.size() > 1) {
            logger.debug("Fetch Type : "
                    + ", ClassInfoList : " + classInfoList.toString()
                    + ", File Name : " + fileName
                    + ", Import List : " + importedPackages_
                    + ", Type Name: " + name);
        }*/

        return classInfoList.size() == 0 ? name : classInfoList.get(0).getQualifiedName();
    }

    //TODO: check whether query for qualified name is needed or not
    private static String getTypeNameForQualifiedType(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                      String javaVersion,
                                                      List<String> importStatementList,
                                                      QualifiedType qualifiedType) {

        String name = qualifiedType.getName().getFullyQualifiedName();
        List<ClassInfo> classInfoList = TypeInferenceAPI.getAllTypes(dependentJarInformationSet, javaVersion, importStatementList, name);

        /*if (classInfoList.size() > 1) {
            logger.debug("Fetch Type : "
                    + ", ClassInfoList : " + classInfoList.toString()
                    + ", File Name : " + fileName
                    + ", Import List : " + importedPackages_
                    + ", Type Name: " + name);

        }*/

        return classInfoList.size() == 0 ? name : classInfoList.get(0).getQualifiedName();
    }

}
