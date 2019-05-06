package ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitseffectivepom.generated;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitseffectivepom.CommitsEffectivePom;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.manager.Manager;
import com.speedment.runtime.field.Field;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * The generated base interface for the manager of every {@link
 * ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitseffectivepom.CommitsEffectivePom}
 * entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public interface GeneratedCommitsEffectivePomManager extends Manager<CommitsEffectivePom> {
    
    TableIdentifier<CommitsEffectivePom> IDENTIFIER = TableIdentifier.of("JarAnalysis", "JarAnalysis", "CommitsEffectivePom");
    List<Field<CommitsEffectivePom>> FIELDS = unmodifiableList(asList(
        CommitsEffectivePom.SHA,
        CommitsEffectivePom.EFFECTIVE_POM
    ));
    
    @Override
    default Class<CommitsEffectivePom> getEntityClass() {
        return CommitsEffectivePom.class;
    }
}