package ca.concordia.jaranalyzer.service;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.DataSource;
import ca.concordia.jaranalyzer.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Diptopol
 * @since 7/13/2022 7:29 PM
 */
public class JarInfoService {

    private static final Logger logger = LoggerFactory.getLogger(JarInfoService.class);

    public boolean isJarExists(String groupId, String artifactId, String version) {
        PreparedStatement pst = null;
        String query = "SELECT id FROM jar WHERE group_id = ? AND artifact_id = ? AND version = ?";
        ResultSet resultSet = null;
        boolean exists = false;

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            pst.setString(1, groupId);
            pst.setString(2, artifactId);
            pst.setString(3, version);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                exists = true;
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return exists;
    }

    public boolean isJavaVersionExists(String javaVersion) {
        PreparedStatement pst = null;
        String query = "SELECT id FROM jar WHERE artifact_id = ? AND version = ?";
        ResultSet resultSet = null;
        boolean exists = false;

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            pst.setString(1, "Java");
            pst.setString(2, javaVersion);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                exists = true;
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return exists;
    }

    public List<Integer> getJarIdList(Set<Artifact> artifactSet, String javaVersion) {
        List<Integer> jarIdList = new ArrayList<>();

        artifactSet.forEach(artifact -> {
            int jarId = getJarInfoId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());

            if (jarId > 0) {
                jarIdList.add(jarId);
            }
        });

        jarIdList.addAll(getJavaJarInfoId(javaVersion));

        return jarIdList;
    }

    private List<Integer> getJavaJarInfoId(String javaVersion) {
        PreparedStatement pst = null;
        String query = "SELECT id FROM jar WHERE artifact_id = ? AND version = ?";
        ResultSet resultSet = null;
        List<Integer> jarIdList = new ArrayList<>();

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            pst.setString(1, "Java");
            pst.setString(2, javaVersion);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                jarIdList.add(resultSet.getInt("id"));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return jarIdList;
    }

    private Integer getJarInfoId(String groupId, String artifactId, String version) {
        PreparedStatement pst = null;
        String query = "SELECT id FROM jar WHERE group_id = ? AND artifact_id = ? AND version = ?";
        ResultSet resultSet = null;
        int jarId = 0;

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            pst.setString(1, groupId);
            pst.setString(2, artifactId);
            pst.setString(3, version);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                jarId = resultSet.getInt("id");
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return jarId;
    }

}
