package ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.superinterfaceclass.generated;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.superinterfaceclass.SuperInterfaceClass;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.ColumnIdentifier;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.field.LongField;
import com.speedment.runtime.field.StringField;
import com.speedment.runtime.typemapper.TypeMapper;

/**
 * The generated base for the {@link
 * ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.superinterfaceclass.SuperInterfaceClass}-interface
 * representing entities of the {@code SuperInterfaceClass}-table in the
 * database.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public interface GeneratedSuperInterfaceClass {
    
    /**
     * This Field corresponds to the {@link SuperInterfaceClass} field that can
     * be obtained using the {@link SuperInterfaceClass#getClassId()} method.
     */
    LongField<SuperInterfaceClass, Long> CLASS_ID = LongField.create(
        Identifier.CLASS_ID,
        SuperInterfaceClass::getClassId,
        SuperInterfaceClass::setClassId,
        TypeMapper.primitive(),
        false
    );
    /**
     * This Field corresponds to the {@link SuperInterfaceClass} field that can
     * be obtained using the {@link SuperInterfaceClass#getSuperInterface()}
     * method.
     */
    StringField<SuperInterfaceClass, String> SUPER_INTERFACE = StringField.create(
        Identifier.SUPER_INTERFACE,
        SuperInterfaceClass::getSuperInterface,
        SuperInterfaceClass::setSuperInterface,
        TypeMapper.identity(),
        false
    );
    
    /**
     * Returns the classId of this SuperInterfaceClass. The classId field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.SuperInterfaceClass.ClassId.
     * 
     * @return the classId of this SuperInterfaceClass
     */
    long getClassId();
    
    /**
     * Returns the superInterface of this SuperInterfaceClass. The
     * superInterface field corresponds to the database column
     * JarAnalysis.JarAnalysis.SuperInterfaceClass.SuperInterface.
     * 
     * @return the superInterface of this SuperInterfaceClass
     */
    String getSuperInterface();
    
    /**
     * Sets the classId of this SuperInterfaceClass. The classId field
     * corresponds to the database column
     * JarAnalysis.JarAnalysis.SuperInterfaceClass.ClassId.
     * 
     * @param classId to set of this SuperInterfaceClass
     * @return        this SuperInterfaceClass instance
     */
    SuperInterfaceClass setClassId(long classId);
    
    /**
     * Sets the superInterface of this SuperInterfaceClass. The superInterface
     * field corresponds to the database column
     * JarAnalysis.JarAnalysis.SuperInterfaceClass.SuperInterface.
     * 
     * @param superInterface to set of this SuperInterfaceClass
     * @return               this SuperInterfaceClass instance
     */
    SuperInterfaceClass setSuperInterface(String superInterface);
    
    enum Identifier implements ColumnIdentifier<SuperInterfaceClass> {
        
        CLASS_ID        ("ClassId"),
        SUPER_INTERFACE ("SuperInterface");
        
        private final String columnId;
        private final TableIdentifier<SuperInterfaceClass> tableIdentifier;
        
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
            return "SuperInterfaceClass";
        }
        
        @Override
        public String getColumnId() {
            return this.columnId;
        }
        
        @Override
        public TableIdentifier<SuperInterfaceClass> asTableIdentifier() {
            return this.tableIdentifier;
        }
    }
}