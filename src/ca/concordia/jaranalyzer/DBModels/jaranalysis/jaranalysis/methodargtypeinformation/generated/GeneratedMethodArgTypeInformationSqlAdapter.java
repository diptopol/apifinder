package ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.generated;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.MethodArgTypeInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.MethodArgTypeInformationImpl;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.component.SqlAdapter;
import com.speedment.runtime.core.db.SqlFunction;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.speedment.common.injector.State.RESOLVED;
import static com.speedment.runtime.core.internal.util.sql.ResultSetUtil.*;

/**
 * The generated Sql Adapter for a {@link
 * ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.methodargtypeinformation.MethodArgTypeInformation}
 * entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public abstract class GeneratedMethodArgTypeInformationSqlAdapter implements SqlAdapter<MethodArgTypeInformation> {
    
    private final TableIdentifier<MethodArgTypeInformation> tableIdentifier;
    
    protected GeneratedMethodArgTypeInformationSqlAdapter() {
        this.tableIdentifier = TableIdentifier.of("JarAnalysis", "JarAnalysis", "MethodArgTypeInformation");
    }
    
    protected MethodArgTypeInformation apply(ResultSet resultSet, int offset) throws SQLException {
        return createEntity()
            .setMethodId( getLong(resultSet, 1 + offset))
            .setType(     resultSet.getString(2 + offset))
            ;
    }
    
    protected MethodArgTypeInformationImpl createEntity() {
        return new MethodArgTypeInformationImpl();
    }
    
    @Override
    public TableIdentifier<MethodArgTypeInformation> identifier() {
        return tableIdentifier;
    }
    
    @Override
    public SqlFunction<ResultSet, MethodArgTypeInformation> entityMapper() {
        return entityMapper(0);
    }
    
    @Override
    public SqlFunction<ResultSet, MethodArgTypeInformation> entityMapper(int offset) {
        return rs -> apply(rs, offset);
    }
}