package ca.concordia.apifinder.service;

import ca.concordia.apifinder.entity.ClassInfo;
import ca.concordia.apifinder.entity.FieldInfo;
import ca.concordia.apifinder.entity.JarInfo;
import ca.concordia.apifinder.entity.MethodInfo;
import ca.concordia.apifinder.util.DataSource;
import ca.concordia.apifinder.util.PropertyReader;
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

    private static final int INSERT_BATCH_SIZE = Integer.parseInt(PropertyReader.getProperty("jar.info.insert.batch.size"));

    public void saveJarInfo(JarInfo jarInfo) {
        logger.info("Save JarInfo of {}:{}:{}", jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());

        Connection classInfoInsertConnection = null;
        int jarId = 0;

        try {
            jarId = insertJarInfo(jarInfo);

            if (jarId > 0) {
                int insertedClassCount = 0;

                for (ClassInfo classInfo : jarInfo.getClassInfoList()) {
                    if (insertedClassCount % INSERT_BATCH_SIZE == 0) {
                        if (Objects.nonNull(classInfoInsertConnection)) {
                            classInfoInsertConnection.commit();
                            classInfoInsertConnection.close();
                        }

                        classInfoInsertConnection = DataSource.getConnection();
                    }

                    int classInfoId = insertClassInfo(classInfo, jarId, classInfoInsertConnection);

                    if (classInfoId > 0) {
                        for (MethodInfo methodInfo : classInfo.getMethodInfoList()) {
                            insertMethodInfo(methodInfo, classInfoId, classInfoInsertConnection);
                        }

                        for (FieldInfo fieldInfo : classInfo.getFieldInfoList()) {
                            insertFieldInfo(fieldInfo, classInfoId, classInfoInsertConnection);
                        }

                        insertSuperClassRelation(classInfo, classInfoId, classInfoInsertConnection);

                        insertInnerClassQNameList(classInfoId, classInfo.getInnerClassQNameList(), classInfoInsertConnection);
                    }

                    insertedClassCount++;
                }

                if (Objects.nonNull(classInfoInsertConnection)) {
                    classInfoInsertConnection.commit();
                    classInfoInsertConnection.close();
                }
            }
        } catch (SQLException e) {
            logger.error("Could not process JarInfo of {}:{}:{}", jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
            logger.error("Error", e);

            if (jarId > 0) {
                insertedClassCleanup(jarId);
            }

            try {
                if (Objects.nonNull(classInfoInsertConnection)) {
                    classInfoInsertConnection.rollback();
                }
            } catch (SQLException ex) {
                logger.error("could not rollback", e);
            }
        } finally {
            try {
                if (Objects.nonNull(classInfoInsertConnection)) {
                    classInfoInsertConnection.close();
                }
            } catch (SQLException e) {
                logger.error("Could not close connection", e);
            }
        }
    }

    private void insertInnerClassQNameList(int classInfoId,
                                           List<String> innerClassQNameList,
                                           Connection connection) throws SQLException {

        for (String classQName : innerClassQNameList) {
            insertInnerClassRelation(classInfoId, classQName, connection);
        }
    }

    private int insertJarInfo(JarInfo jarInfo) throws SQLException {
        String insertQuery = "INSERT INTO jar (group_id, artifact_id, version) VALUES (?, ?, ?)";
        int jarId = 0;

        try (Connection connection = DataSource.getConnection();
             PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setString(1, jarInfo.getGroupId());
            pst.setString(2, jarInfo.getArtifactId());
            pst.setString(3, jarInfo.getVersion());

            pst.executeUpdate();

            jarId = getLastInsertedId(connection);
            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        }

        return jarId;
    }

    private void insertThrownClassNameList(MethodInfo methodInfo, int methodInfoId, Connection connection) throws SQLException {
        int precedenceOrder = 0;

        for (String thrownClassName : methodInfo.getThrownInternalClassNames()) {
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
                pst.setInt(4, precedenceOrder++);

                pst.executeUpdate();
            }
        }

        for (String interfaceQName : classInfo.getInterfaceQNameList()) {
            try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
                pst.setInt(1, classInfoId);
                pst.setString(2, interfaceQName);
                pst.setString(3, "INTERFACE");
                pst.setInt(4, precedenceOrder++);

                pst.executeUpdate();
            }
        }
    }

    private void insertInnerClassRelation(int classInfoId, String innerClassQName, Connection connection) throws SQLException {
        String insertQuery = "INSERT INTO inner_class_name(parent_class_id, inner_class_q_name) VALUES(?, ?)";

        try (PreparedStatement pst = connection.prepareStatement(insertQuery)) {
            pst.setInt(1, classInfoId);
            pst.setString(2, innerClassQName);

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

    private void insertedClassCleanup(int jarId) {
        try (Connection connection = DataSource.getConnection()) {
            deleteSuperClass(jarId, connection);
            deleteInnerClass(jarId, connection);
            deleteField(jarId, connection);

            deleteArgumentTypeDescriptor(jarId, connection);
            deleteThrownClassName(jarId, connection);
            deleteMethod(jarId, connection);

            deleteClass(jarId, connection);
            deleteJar(jarId, connection);

            connection.commit();
        } catch (SQLException e) {
            logger.error("Could not clean up", e);
        }
    }

    private void deleteSuperClass(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM super_class_relation scr" +
                " WHERE EXISTS" +
                " (SELECT 1 FROM class c" +
                " WHERE scr.child_class_id = c.id AND c.jar_id = ?)";

        executeDelete(jarId, connection, query);
    }

    private void deleteInnerClass(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM inner_class ic" +
                " WHERE EXISTS (SELECT 1 FROM class c" +
                " WHERE ic.parent_class_id = c.id AND c.jar_id = ?)";

        executeDelete(jarId, connection, query);
    }

    private void deleteField(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM field f" +
                " WHERE EXISTS (SELECT 1 FROM class c" +
                " WHERE f.class_id = c.id AND c.jar_id = ?)";

        executeDelete(jarId, connection, query);
    }

    private void deleteArgumentTypeDescriptor(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM argument_type_descriptor arg" +
                " WHERE EXISTS (SELECT 1 FROM method m JOIN class c ON (m.class_id = c.id)" +
                " WHERE arg.method_id = m.id AND c.jar_id = ?)";

        executeDelete(jarId, connection, query);
    }

    private void deleteThrownClassName(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM thrown_class_name tcn" +
                " WHERE EXISTS (SELECT 1 FROM method m JOIN class c ON (m.class_id = c.id)" +
                " WHERE tcn.method_id = m.id AND c.jar_id = ?)";

        executeDelete(jarId, connection, query);
    }

    private void deleteMethod(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM method m" +
                " WHERE EXISTS (SELECT 1 FROM class c" +
                " WHERE m.class_id = c.id AND c.jar_id = ?)";

        executeDelete(jarId, connection, query);
    }

    private void deleteClass(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM class c" +
                " WHERE c.jar_id = ?";

        executeDelete(jarId, connection, query);
    }

    private void deleteJar(int jarId, Connection connection) throws SQLException {
        String query = "DELETE FROM jar j WHERE j.id = ?";

        executeDelete(jarId, connection, query);
    }

    private void executeDelete(int jarId, Connection connection, String query) throws SQLException {
        try (PreparedStatement pst = connection.prepareStatement(query)) {
            pst.setInt(1, jarId);

            pst.executeUpdate();
        }
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
