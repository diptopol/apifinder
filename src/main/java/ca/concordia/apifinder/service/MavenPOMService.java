package ca.concordia.apifinder.service;

import ca.concordia.apifinder.util.DataSource;
import ca.concordia.apifinder.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Diptopol
 * @since 7/19/2022 8:19 PM
 */
public class MavenPOMService {

    private static final Logger logger = LoggerFactory.getLogger(MavenPOMService.class);

    public String getEffectivePOM(String remoteUrl, String commitId) {
        PreparedStatement pst = null;
        ResultSet resultSet = null;
        String effectivePOM = null;

        String query = "SELECT effective_pom FROM maven_effective_pom WHERE project_remote_url = ? AND commit_id = ?";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            pst.setString(1, remoteUrl);
            pst.setString(2, commitId);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                effectivePOM = resultSet.getString("effective_pom");
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return effectivePOM;
    }

    public void saveEffectivePOM(String remoteUrl, String commitId, String effectivePOM) {
        PreparedStatement pst = null;

        String insertQuery = "INSERT INTO maven_effective_pom (project_remote_url, commit_id, effective_pom)" +
                " VALUES(?, ?, ?)";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(insertQuery);

            pst.setString(1, remoteUrl);
            pst.setString(2, commitId);
            pst.setString(3, effectivePOM);

            pst.executeUpdate();

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, null);
        }

    }

}
