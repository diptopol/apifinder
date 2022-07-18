package ca.concordia.jaranalyzer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * @author Diptopol
 * @since 7/13/2022 9:03 PM
 */
public class DbUtils {

    private static final Logger logger = LoggerFactory.getLogger(DbUtils.class);

    public static String getInClausePlaceHolder(int numberOfArguments) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < numberOfArguments; i++) {
            stringBuilder.append(" ?");

            if (i < numberOfArguments -1) {
                stringBuilder.append(",");
            }
        }

        return stringBuilder.toString();
    }

    public static void closeResources(PreparedStatement pst, ResultSet resultSet) {
        try {
            if (Objects.nonNull(resultSet)) {
                resultSet.close();
            }

            if (Objects.nonNull(pst)) {
                pst.close();
            }

        } catch (SQLException e) {
            logger.error("Could not close", e);
        }
    }

}
