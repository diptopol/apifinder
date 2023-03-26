package ca.concordia.jaranalyzer.service;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.DataSource;
import ca.concordia.jaranalyzer.util.DbUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 7/13/2022 7:29 PM
 */
public class JarInfoService {

    private static final Logger logger = LoggerFactory.getLogger(JarInfoService.class);

    private static Cache<String, Map<Artifact, Integer>> jarIdCache;

    private static Cache<String, List<Integer>> javaJarIdCache;

    static {
        if (Objects.isNull(jarIdCache)) {
            jarIdCache  = Caffeine.newBuilder()
                    .expireAfterAccess(5, TimeUnit.MINUTES)
                    .maximumSize(100)
                    .build();
        }

        if (Objects.isNull(javaJarIdCache)) {
            javaJarIdCache  = Caffeine.newBuilder()
                    .expireAfterAccess(5, TimeUnit.MINUTES)
                    .maximumSize(100)
                    .build();
        }
    }

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

    private Map<Artifact, Integer> getJarInfoIdMapUsingMemCache(Set<Artifact> artifactSet) {
        String jarIdCacheKey = artifactSet.stream().filter(Objects::nonNull)
                .map(a -> a.getGroupId().concat(":").concat(a.getArtifactId()).concat(":").concat(a.getVersion()))
                .sorted()
                .collect(Collectors.joining(";"));

        Map<Artifact, Integer> jarIdMap = jarIdCache.getIfPresent(jarIdCacheKey);

        if (Objects.isNull(jarIdMap)) {
            jarIdMap = new LinkedHashMap<>();

            for (Artifact artifact: artifactSet) {
                int jarId = getJarInfoId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                jarIdMap.put(artifact, jarId);
            }

            if (!jarIdMap.isEmpty()) {
                jarIdCache.put(jarIdCacheKey, jarIdMap);
            }
        }

        return jarIdMap;
    }


    public List<Integer> getJarIdList(Set<Artifact> artifactSet, String javaVersion, List<Integer> internalDependencyJarIdList) {
        List<Integer> jarIdList = new ArrayList<>();

        Map<Artifact, Integer> jarIdMap = getJarInfoIdMapUsingMemCache(artifactSet);

        for (Artifact artifact: artifactSet) {
            int jarId = jarIdMap.get(artifact);

            if (jarId > 0) {
                if (artifact.isInternalDependency() && Objects.nonNull(internalDependencyJarIdList)) {
                    internalDependencyJarIdList.add(jarId);
                }
            }

            jarIdList.add(jarId);
        }

        jarIdList.addAll(getJavaJarInfoIdUsingMemCache(javaVersion));



        return jarIdList;
    }

    private List<Integer> getJavaJarInfoIdUsingMemCache(String javaVersion) {
        List<Integer> javaJarIdList = javaJarIdCache.getIfPresent(javaVersion);

        if (Objects.isNull(javaJarIdList)) {
            javaJarIdList = getJavaJarInfoId(javaVersion);

            if (!javaJarIdList.isEmpty()) {
                javaJarIdCache.put(javaVersion, javaJarIdList);
            }
        }

        return new ArrayList<>(javaJarIdList);
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
