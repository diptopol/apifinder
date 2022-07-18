package ca.concordia.jaranalyzer.service;

import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.entity.FieldInfo;
import ca.concordia.jaranalyzer.entity.JarInfo;
import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.util.DataSource;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Diptopol
 * @since 7/13/2022 6:41 PM
 */
public class JarInfoSaveService {

    private static final Logger logger = LoggerFactory.getLogger(JarInfoSaveService.class);

    public void saveJarInfo(JarInfo jarInfo) {
        logger.info("Save JarInfo of {}:{}:{}", jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());

        Connection connection = null;
        try {
            connection = DataSource.getConnection();
            int jarId = insertJarInfo(jarInfo, connection);
            Map<Integer, List<String>> innerClassQNameMap = new HashMap<>();

            if (jarId > 0) {
                for (ClassInfo classInfo : jarInfo.getClassInfoList()) {
                    int classInfoId = insertClassInfo(classInfo, jarId, connection);

                    if (classInfoId > 0) {
                        for (MethodInfo methodInfo: classInfo.getMethodInfoList()) {
                            insertMethodInfo(methodInfo, classInfoId, connection);
                        }

                        for (FieldInfo fieldInfo: classInfo.getFieldInfoList()) {
                            insertFieldInfo(fieldInfo, classInfoId, connection);
                        }

                        insertSuperClassRelation(classInfo, classInfoId, connection);

                        if (!classInfo.isInnerClass()) {
                            innerClassQNameMap.put(classInfoId, classInfo.getInnerClassQNameList());
                        }
                    }
                }

                for (Map.Entry<Integer, List<String>> entry: innerClassQNameMap.entrySet()) {
                    int classInfoId = entry.getKey();
                    List<String> innerClassQNameList = entry.getValue();
                    insertInnerClassQNameList(classInfoId, innerClassQNameList, jarId, connection);
                }
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Could not process JarInfo of {}:{}:{}", jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
            logger.error("Error", e);

            if (Objects.nonNull(connection)) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    logger.error("Could not process JarInfo of {}:{}:{}", jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
                    logger.error("Error", e);
                }
            }
        } finally {
            try {
                if (Objects.nonNull(connection)) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Could not close connection", e);
            }
        }
    }

    private void insertInnerClassQNameList(int classInfoId, List<String> innerClassQNameList,
                                           int jarId, Connection connection) throws SQLException {

        for (String classQName: innerClassQNameList) {
            int innerClassInfoId = getClassInfoId(classQName, jarId, connection);

            if (innerClassInfoId > 0) {
                insertInnerClassRelation(classInfoId, innerClassInfoId, connection);
            }
        }
    }

    private int getClassInfoId(String classQName, int jarId, Connection connection) throws SQLException {
        String query = "SELECT id from class WHERE q_name = ? AND jar_id = ?";
        ResultSet resultSet = null;
        Integer classInfoId = null;

        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setString(1, classQName);
            pst.setInt(2, jarId);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                classInfoId = resultSet.getInt("id");
            }
        } finally {
            if (Objects.nonNull(resultSet)) {
                resultSet.close();
            }
        }

        return Objects.nonNull(classInfoId) ? classInfoId : 0;
    }

    private int insertJarInfo(JarInfo jarInfo, Connection connection) throws SQLException {
        String insertQuery = "INSERT INTO jar (group_id, artifact_id, version) VALUES (?, ?, ?)";

        try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setString(1, jarInfo.getGroupId());
            pst.setString(2, jarInfo.getArtifactId());
            pst.setString(3, jarInfo.getVersion());

            pst.executeUpdate();
        }

        return getLastInsertedId(connection);
    }

    private void insertThrownClassNameList(MethodInfo methodInfo, int methodInfoId, Connection connection) throws SQLException {
        int precedenceOrder = 0;

        for (String thrownClassName: methodInfo.getThrownInternalClassNames()) {
            String insertQuery = "INSERT INTO thrown_class_name(precedence_order, thrown_class_name, method_id)" +
                    " VALUES(?, ?, ?)";

            try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
                pst.setInt(1, precedenceOrder++);
                pst.setString(2, thrownClassName);
                pst.setInt(3, methodInfoId);

                pst.executeUpdate();
            }
        }

    }

    private void insertMethodArgumentDescriptorList(MethodInfo methodInfo, int methodInfoId, Connection connection) throws SQLException {
        List<String> argumentTypeDescriptorList = new ArrayList<>();

        for (Type type : methodInfo.getArgumentTypes()) {
            argumentTypeDescriptorList.add(type.getDescriptor());
        }

        int precedenceOrder = 0;
        for (String argumentTypeDescriptor : argumentTypeDescriptorList) {
            String insertQuery = "INSERT INTO argument_type_descriptor (precedence_order, argument_type_descriptor," +
                    " method_id) VALUES(?, ?, ?)";

            try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
                pst.setInt(1, precedenceOrder++);
                pst.setString(2, argumentTypeDescriptor);
                pst.setInt(3, methodInfoId);

                pst.executeUpdate();
            }
        }
    }

    private int insertMethodInfoCore(MethodInfo methodInfo, int classInfoId, Connection connection) throws SQLException {
        String insertQuery = "INSERT INTO method (class_id, name, is_abstract, is_constructor, is_static, is_public," +
                " is_private, is_protected, is_synchronized, is_final, is_varargs, is_bridge_method, signature," +
                " internal_class_constructor_prefix, return_type_descriptor)" +
                " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setInt(1, classInfoId);

            pst.setString(2, methodInfo.getName());

            pst.setBoolean(3, methodInfo.isAbstract());
            pst.setBoolean(4, methodInfo.isConstructor());
            pst.setBoolean(5, methodInfo.isStatic());
            pst.setBoolean(6, methodInfo.isPublic());
            pst.setBoolean(7, methodInfo.isPrivate());
            pst.setBoolean(8, methodInfo.isProtected());
            pst.setBoolean(9, methodInfo.isSynchronized());
            pst.setBoolean(10, methodInfo.isFinal());
            pst.setBoolean(11, methodInfo.isVarargs());
            pst.setBoolean(12, methodInfo.isBridgeMethod());

            pst.setString(13, methodInfo.getSignature());
            pst.setString(14, methodInfo.getInternalClassConstructorPrefix());
            pst.setString(15, methodInfo.getReturnType().getDescriptor());

            pst.executeUpdate();
        }

        return getLastInsertedId(connection);
    }

    private void insertMethodInfo(MethodInfo methodInfo, int classInfoId, Connection connection) throws SQLException {
        int methodInfoId = insertMethodInfoCore(methodInfo, classInfoId, connection);
        insertMethodArgumentDescriptorList(methodInfo, methodInfoId, connection);
        insertThrownClassNameList(methodInfo, methodInfoId, connection);
    }

    private void insertFieldInfo(FieldInfo fieldInfo, int classInfoId, Connection connection) throws SQLException {
        String insertQuery = "INSERT INTO field (class_id, name, is_public, is_private, is_protected, is_static," +
                " type_descriptor, signature) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setInt(1, classInfoId);

            pst.setString(2, fieldInfo.getName());

            pst.setBoolean(3, fieldInfo.isPublic());
            pst.setBoolean(4, fieldInfo.isPrivate());
            pst.setBoolean(5, fieldInfo.isProtected());
            pst.setBoolean(6, fieldInfo.isStatic());

            pst.setString(7, fieldInfo.getType().getDescriptor());
            pst.setString(8, fieldInfo.getSignature());

            pst.executeUpdate();
        }
    }

    private void insertSuperClassRelation(ClassInfo classInfo, int classInfoId, Connection connection) throws SQLException {
        int precedenceOrder = 0;

        String insertQuery = "INSERT INTO super_class_relation (child_class_id, parent_class_q_name, type, precedence)" +
                " VALUES(?, ?, ?, ?)";

        if (Objects.nonNull(classInfo.getSuperClassQName())) {
            try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
                pst.setInt(1, classInfoId);
                pst.setString(2, classInfo.getSuperClassQName());
                pst.setString(3, "SUPER_CLASS");
                pst.setInt(4, precedenceOrder);

                pst.executeUpdate();
            }
        }

        for (String interfaceQName: classInfo.getInterfaceQNameList()) {
            try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
                pst.setInt(1, classInfoId);
                pst.setString(2, interfaceQName);
                pst.setString(3, "INTERFACE");
                pst.setInt(4, precedenceOrder);

                pst.executeUpdate();
            }
        }
    }

    private void insertInnerClassRelation(int classInfoId, int innerClassInfoId, Connection connection) throws SQLException {
        String insertQuery = "INSERT INTO inner_class(parent_class_id, inner_class_id) VALUES(?, ?)";

        try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setInt(1, classInfoId);
            pst.setInt(2, innerClassInfoId);

            pst.executeUpdate();
        }
    }

    private int insertClassInfo(ClassInfo classInfo, int jarId, Connection connection) throws SQLException {
        String insertQuery = "INSERT INTO class (name, q_name, package_name, is_abstract, is_interface, is_enum," +
                " is_public, is_private, is_protected, is_inner_class, is_anonymous_inner_class, type_descriptor," +
                " signature, jar_id) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setString(1, classInfo.getName());
            pst.setString(2, classInfo.getQualifiedName());
            pst.setString(3, classInfo.getPackageName());

            pst.setBoolean(4, classInfo.isAbstract());
            pst.setBoolean(5, classInfo.isInterface());
            pst.setBoolean(6, classInfo.isEnum());
            pst.setBoolean(7, classInfo.isPublic());
            pst.setBoolean(8, classInfo.isPrivate());
            pst.setBoolean(9, classInfo.isProtected());
            pst.setBoolean(10, classInfo.isInnerClass());
            pst.setBoolean(11, classInfo.isAnonymousInnerClass());

            pst.setString(12, classInfo.getType().getDescriptor());
            pst.setString(13, classInfo.getSignature());
            pst.setInt(14, jarId);

            pst.executeUpdate();
        }

        return getLastInsertedId(connection);
    }

    private int getLastInsertedId(Connection connection) throws SQLException {
        String query = "SELECT LAST_INSERT_ID()";
        ResultSet resultSet = null;
        Integer id = null;

        try (PreparedStatement pst = connection.prepareStatement(query)) {
            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                id = resultSet.getInt(1);
            }
        } finally {
            if (Objects.nonNull(resultSet)) {
                resultSet.close();
            }
        }

        return Objects.nonNull(id) ? id : 0;
    }

}
