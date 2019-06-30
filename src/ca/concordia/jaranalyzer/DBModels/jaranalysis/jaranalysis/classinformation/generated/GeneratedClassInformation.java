package ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.generated;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformation;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.ColumnIdentifier;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.manager.Manager;
import com.speedment.runtime.core.util.OptionalUtil;
import com.speedment.runtime.field.BooleanField;
import com.speedment.runtime.field.LongField;
import com.speedment.runtime.field.LongForeignKeyField;
import com.speedment.runtime.field.StringField;
import com.speedment.runtime.typemapper.TypeMapper;

import java.util.Optional;

/**
 * The generated base for the {@link
 * ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.classinformation.ClassInformation}-interface
 * representing entities of the {@code ClassInformation}-table in the database.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public interface GeneratedClassInformation {
    
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getId()} method.
     */
    LongField<ClassInformation, Long> ID = LongField.create(
        Identifier.ID,
        ClassInformation::getId,
        ClassInformation::setId,
        TypeMapper.primitive(),
        true
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getPackageId()} method.
     */
    LongForeignKeyField<ClassInformation, Long, PackageInformation> PACKAGE_ID = LongForeignKeyField.create(
        Identifier.PACKAGE_ID,
        ClassInformation::getPackageId,
        ClassInformation::setPackageId,
        PackageInformation.ID,
        TypeMapper.primitive(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getName()} method.
     */
    StringField<ClassInformation, String> NAME = StringField.create(
        Identifier.NAME,
        ClassInformation::getName,
        ClassInformation::setName,
        TypeMapper.identity(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getQualifiedName()} method.
     */
    StringField<ClassInformation, String> QUALIFIED_NAME = StringField.create(
        Identifier.QUALIFIED_NAME,
        ClassInformation::getQualifiedName,
        ClassInformation::setQualifiedName,
        TypeMapper.identity(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getType()} method.
     */
    StringField<ClassInformation, String> TYPE = StringField.create(
        Identifier.TYPE,
        ClassInformation::getType,
        ClassInformation::setType,
        TypeMapper.identity(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getAccessModifiers()} method.
     */
    StringField<ClassInformation, String> ACCESS_MODIFIERS = StringField.create(
        Identifier.ACCESS_MODIFIERS,
        ClassInformation::getAccessModifiers,
        ClassInformation::setAccessModifiers,
        TypeMapper.identity(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getSuperClass()} method.
     */
    StringField<ClassInformation, String> SUPER_CLASS = StringField.create(
        Identifier.SUPER_CLASS,
        o -> OptionalUtil.unwrap(o.getSuperClass()),
        ClassInformation::setSuperClass,
        TypeMapper.identity(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getIsInterface()} method.
     */
    BooleanField<ClassInformation, Boolean> IS_INTERFACE = BooleanField.create(
        Identifier.IS_INTERFACE,
        ClassInformation::getIsInterface,
        ClassInformation::setIsInterface,
        TypeMapper.primitive(),
        false
    );
    /**
     * This Field corresponds to the {@link ClassInformation} field that can be
     * obtained using the {@link ClassInformation#getIsAbstract()} method.
     */
    BooleanField<ClassInformation, Boolean> IS_ABSTRACT = BooleanField.create(
        Identifier.IS_ABSTRACT,
        ClassInformation::getIsAbstract,
        ClassInformation::setIsAbstract,
        TypeMapper.primitive(),
        false
    );
    
    /**
     * Returns the id of this ClassInformation. The id field corresponds to the
     * database column JarAnalysis.JarAnalysis.ClassInformation.ID.
     * 
     * @return the id of this ClassInformation
     */
    long getId();
    
    /**
     * Returns the packageId of this ClassInformation. The packageId field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.PackageID.
     * 
     * @return the packageId of this ClassInformation
     */
    long getPackageId();
    
    /**
     * Returns the name of this ClassInformation. The name field corresponds to
     * the database column JarAnalysis.JarAnalysis.ClassInformation.Name.
     * 
     * @return the name of this ClassInformation
     */
    String getName();
    
    /**
     * Returns the qualifiedName of this ClassInformation. The qualifiedName
     * field corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.QualifiedName.
     * 
     * @return the qualifiedName of this ClassInformation
     */
    String getQualifiedName();
    
    /**
     * Returns the type of this ClassInformation. The type field corresponds to
     * the database column JarAnalysis.JarAnalysis.ClassInformation.Type.
     * 
     * @return the type of this ClassInformation
     */
    String getType();
    
    /**
     * Returns the accessModifiers of this ClassInformation. The accessModifiers
     * field corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.AccessModifiers.
     * 
     * @return the accessModifiers of this ClassInformation
     */
    String getAccessModifiers();
    
    /**
     * Returns the superClass of this ClassInformation. The superClass field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.SuperClass.
     * 
     * @return the superClass of this ClassInformation
     */
    Optional<String> getSuperClass();
    
    /**
     * Returns the isInterface of this ClassInformation. The isInterface field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.isInterface.
     * 
     * @return the isInterface of this ClassInformation
     */
    boolean getIsInterface();
    
    /**
     * Returns the isAbstract of this ClassInformation. The isAbstract field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.isAbstract.
     * 
     * @return the isAbstract of this ClassInformation
     */
    boolean getIsAbstract();
    
    /**
     * Sets the id of this ClassInformation. The id field corresponds to the
     * database column JarAnalysis.JarAnalysis.ClassInformation.ID.
     * 
     * @param id to set of this ClassInformation
     * @return   this ClassInformation instance
     */
    ClassInformation setId(long id);
    
    /**
     * Sets the packageId of this ClassInformation. The packageId field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.PackageID.
     * 
     * @param packageId to set of this ClassInformation
     * @return          this ClassInformation instance
     */
    ClassInformation setPackageId(long packageId);
    
    /**
     * Sets the name of this ClassInformation. The name field corresponds to the
     * database column JarAnalysis.JarAnalysis.ClassInformation.Name.
     * 
     * @param name to set of this ClassInformation
     * @return     this ClassInformation instance
     */
    ClassInformation setName(String name);
    
    /**
     * Sets the qualifiedName of this ClassInformation. The qualifiedName field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.QualifiedName.
     * 
     * @param qualifiedName to set of this ClassInformation
     * @return              this ClassInformation instance
     */
    ClassInformation setQualifiedName(String qualifiedName);
    
    /**
     * Sets the type of this ClassInformation. The type field corresponds to the
     * database column JarAnalysis.JarAnalysis.ClassInformation.Type.
     * 
     * @param type to set of this ClassInformation
     * @return     this ClassInformation instance
     */
    ClassInformation setType(String type);
    
    /**
     * Sets the accessModifiers of this ClassInformation. The accessModifiers
     * field corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.AccessModifiers.
     * 
     * @param accessModifiers to set of this ClassInformation
     * @return                this ClassInformation instance
     */
    ClassInformation setAccessModifiers(String accessModifiers);
    
    /**
     * Sets the superClass of this ClassInformation. The superClass field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.SuperClass.
     * 
     * @param superClass to set of this ClassInformation
     * @return           this ClassInformation instance
     */
    ClassInformation setSuperClass(String superClass);
    
    /**
     * Sets the isInterface of this ClassInformation. The isInterface field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.isInterface.
     * 
     * @param isInterface to set of this ClassInformation
     * @return            this ClassInformation instance
     */
    ClassInformation setIsInterface(boolean isInterface);
    
    /**
     * Sets the isAbstract of this ClassInformation. The isAbstract field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.ClassInformation.isAbstract.
     * 
     * @param isAbstract to set of this ClassInformation
     * @return           this ClassInformation instance
     */
    ClassInformation setIsAbstract(boolean isAbstract);
    
    /**
     * Queries the specified manager for the referenced PackageInformation. If
     * no such PackageInformation exists, an {@code NullPointerException} will
     * be thrown.
     * 
     * @param foreignManager the manager to query for the entity
     * @return               the foreign entity referenced
     */
    PackageInformation findPackageId(Manager<PackageInformation> foreignManager);
    
    enum Identifier implements ColumnIdentifier<ClassInformation> {
        
        ID               ("ID"),
        PACKAGE_ID       ("PackageID"),
        NAME             ("Name"),
        QUALIFIED_NAME   ("QualifiedName"),
        TYPE             ("Type"),
        ACCESS_MODIFIERS ("AccessModifiers"),
        SUPER_CLASS      ("SuperClass"),
        IS_INTERFACE     ("isInterface"),
        IS_ABSTRACT      ("isAbstract");
        
        private final String columnId;
        private final TableIdentifier<ClassInformation> tableIdentifier;
        
        Identifier(String columnId) {
            this.columnId        = columnId;
            this.tableIdentifier = TableIdentifier.of(    getDbmsId(), 
                getSchemaId(), 
                getTableId());
        }
        
        @Override
        public String getDbmsId() {
            return "JarAnalysis";
        }
        
        @Override
        public String getSchemaId() {
            return "JarAnalysis";
        }
        
        @Override
        public String getTableId() {
            return "ClassInformation";
        }
        
        @Override
        public String getColumnId() {
            return this.columnId;
        }
        
        @Override
        public TableIdentifier<ClassInformation> asTableIdentifier() {
            return this.tableIdentifier;
        }
    }
}