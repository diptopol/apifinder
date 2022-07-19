package ca.concordia.jaranalyzer.service;

import ca.concordia.jaranalyzer.entity.FieldInfo;
import ca.concordia.jaranalyzer.util.DataSource;
import ca.concordia.jaranalyzer.util.DbUtils;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Diptopol
 * @since 7/15/2022 12:06 AM
 */
public class FieldInfoService {

    private static final Logger logger = LoggerFactory.getLogger(FieldInfoService.class);

    private ClassInfoService classInfoService;

    public FieldInfoService() {
        this.classInfoService = new ClassInfoService();
    }

    public FieldInfoService(ClassInfoService classInfoService) {
        this.classInfoService = classInfoService;
    }

    public List<FieldInfo> getFieldInfoList(Set<String> qualifiedClassNameSet,
                                            List<Integer> jarIdList,
                                            String fieldName) {

        List<FieldInfo> fieldInfoList = new ArrayList<>();

        try (Connection connection = DataSource.getConnection()) {
            fieldInfoList = fetchCoreFieldInfo(qualifiedClassNameSet, jarIdList, fieldName, connection);

            for (FieldInfo fieldInfo: fieldInfoList) {
                fieldInfo.setClassInfo(classInfoService.getClassInfo(fieldInfo.getClassInfoId(), connection));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        }

        return fieldInfoList;
    }

    public List<FieldInfo> fetchCoreFieldInfo(Set<String> qualifiedClassNameSet,
                                              List<Integer> jarIdList,
                                              String fieldName,
                                              Connection connection) throws SQLException {

        if (qualifiedClassNameSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<FieldInfo> fieldInfoList = new ArrayList<>();
        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT f.* FROM field f" +
                " JOIN class c ON (f.class_id = c.id)" +
                " WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.q_name IN (" + DbUtils.getInClausePlaceHolder(qualifiedClassNameSet.size()) + ")" +
                " AND f.name = BINARY ?";

        try {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String qName : qualifiedClassNameSet) {
                pst.setString(index++, qName);
            }

            pst.setString(index, fieldName);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                fieldInfoList.add(getFieldInfo(resultSet));
            }

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return fieldInfoList;
    }

    private FieldInfo getFieldInfo(ResultSet resultSet) throws SQLException {
        FieldInfo fieldInfo = new FieldInfo();

        fieldInfo.setId(resultSet.getInt("id"));
        fieldInfo.setName(resultSet.getString("name"));

        fieldInfo.setPublic(resultSet.getBoolean("is_public"));
        fieldInfo.setPrivate(resultSet.getBoolean("is_private"));
        fieldInfo.setProtected(resultSet.getBoolean("is_protected"));
        fieldInfo.setStatic(resultSet.getBoolean("is_static"));

        fieldInfo.setType(Type.getType(resultSet.getString("type_descriptor")));
        fieldInfo.setSignature(resultSet.getString("signature"));

        fieldInfo.setClassInfoId(resultSet.getInt("class_id"));

        return fieldInfo;
    }

}
