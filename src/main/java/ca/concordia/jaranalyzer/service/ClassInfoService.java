package ca.concordia.jaranalyzer.service;

import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.models.typeInfo.ParameterizedTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.PrimitiveTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.QualifiedTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.DataSource;
import ca.concordia.jaranalyzer.util.DbUtils;
import ca.concordia.jaranalyzer.util.PrimitiveTypeUtils;
import ca.concordia.jaranalyzer.util.signaturevisitor.ClassSignatureFormalTypeParameterExtractor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Diptopol
 * @since 7/14/2022 10:39 AM
 */
public class ClassInfoService {

    private static final Logger logger = LoggerFactory.getLogger(ClassInfoService.class);

    public Map<String, Integer> getClientIdMap(List<Integer> jarIdList, Set<String> qualifiedClassNameSet) {
        Map<String, Integer> clientIdMap = new LinkedHashMap<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT c.id id, c.q_name q_name FROM class c WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.q_name IN (" + DbUtils.getInClausePlaceHolder(qualifiedClassNameSet.size()) + ")";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String qName : qualifiedClassNameSet) {
                pst.setString(index++, qName);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                if (!clientIdMap.containsKey(resultSet.getString("q_name"))) {
                    clientIdMap.put(resultSet.getString("q_name"), resultSet.getInt("id"));
                }
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return clientIdMap;
    }

    public Set<String> getClassQNameSet(List<Integer> jarIdList, List<String> packageNameList) {
        Set<String> classQNameSet = new LinkedHashSet<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT c.q_name q_name FROM class c" +
                " WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.package_name IN (" + DbUtils.getInClausePlaceHolder(packageNameList.size()) + ")";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String packageName : packageNameList) {
                pst.setString(index++, packageName);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                classQNameSet.add(resultSet.getString("q_name"));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return classQNameSet;
    }

    public List<Integer> getClassInfoIdList(List<Integer> jarIdList, List<String> packageNameList) {
        List<Integer> classInfoIdList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT c.id id FROM class c WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.package_name IN (" + DbUtils.getInClausePlaceHolder(packageNameList.size()) + ")";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String packageName : packageNameList) {
                pst.setString(index++, packageName);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                classInfoIdList.add(resultSet.getInt("id"));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return classInfoIdList;
    }

    public List<Integer> getClassInfoIdList(List<Integer> jarIdList, Set<String> qualifiedClassNameSet) {
        if (qualifiedClassNameSet.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> classInfoNameMap = new HashMap<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT c.id id, c.q_name q_name FROM class c" +
                " WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.q_name IN (" + DbUtils.getInClausePlaceHolder(qualifiedClassNameSet.size()) + ")";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String qName : qualifiedClassNameSet) {
                pst.setString(index++, qName);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                if (!classInfoNameMap.containsKey(resultSet.getString("q_name"))) {
                    classInfoNameMap.put(resultSet.getString("q_name"), resultSet.getInt("id"));
                }
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        List<Integer> classInfoIdList = new ArrayList<>();
        for (String qName: qualifiedClassNameSet) {
            if (classInfoNameMap.containsKey(qName)) {
                classInfoIdList.add(classInfoNameMap.get(qName));
            }
        }

        return classInfoIdList;
    }

    public List<ClassInfo> getClassInfoList(List<Integer> jarIdList, String className) {
        List<ClassInfo> classInfoList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT c.* FROM class c WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND MATCH(c.name) AGAINST (?)";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            pst.setString(index, className);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                classInfoList.add(getClassInfo(resultSet));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return classInfoList;
    }

    public ClassInfo getClassInfo(int classInfoId, Connection connection) throws SQLException {
        ClassInfo classInfo = null;

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT c.* FROM class c WHERE c.id = ?";

        try {
            pst = connection.prepareStatement(query);
            pst.setInt(1, classInfoId);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                classInfo = getClassInfo(resultSet);
            }

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return classInfo;
    }

    public Set<String> getInnerClassQualifiedNameSet(Set<String> classQualifiedNameSet, List<Integer> jarIdList) {
        if (classQualifiedNameSet.isEmpty()) {
            return Collections.emptySet();
        }

        List<String> innerClassQualifiedNameList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT icr.inner_class_q_name q_name from inner_class_name icr" +
                " JOIN class pc ON (icr.parent_class_id = pc.id)" +
                " WHERE pc.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND pc.q_name IN (" + DbUtils.getInClausePlaceHolder(classQualifiedNameSet.size()) + ")";

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (Integer jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String qName : classQualifiedNameSet) {
                pst.setString(index++, qName);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                innerClassQualifiedNameList.add(resultSet.getString("q_name"));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return new LinkedHashSet<>(innerClassQualifiedNameList);
    }

    public List<String> getSuperClassQNameList(Integer classInfoId, String type) {
        List<String> superClassQNameList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT scr.parent_class_q_name q_name FROM super_class_relation scr" +
                " JOIN class c ON (scr.child_class_id = c.id)" +
                " WHERE c.id = ?";

        if (Objects.nonNull(type)) {
            query = query.concat(" AND scr.type = ?");
        }

        query = query.concat(" ORDER BY scr.precedence ASC");

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            pst.setInt(index++, classInfoId);

            if (Objects.nonNull(type)) {
                pst.setString(index, type);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                superClassQNameList.add(resultSet.getString("q_name"));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return superClassQNameList;
    }

    public Set<String> getSuperClassQNameSet(Set<String> classQualifiedNameSet, List<Integer> jarIdList, String type) {
        List<String> superClassQNameList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT scr.parent_class_q_name q_name FROM super_class_relation scr" +
                " JOIN class c ON (scr.child_class_id = c.id)" +
                " WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.q_name IN (" + DbUtils.getInClausePlaceHolder(classQualifiedNameSet.size()) + ")";

        if (Objects.nonNull(type)) {
            query = query.concat(" AND scr.type = ?");
        }

        query = query.concat(" ORDER BY scr.precedence ASC");

        try (Connection connection = DataSource.getConnection()) {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (Integer jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String qName : classQualifiedNameSet) {
                pst.setString(index++, qName);
            }

            if (Objects.nonNull(type)) {
                pst.setString(index, type);
            }

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                superClassQNameList.add(resultSet.getString("q_name"));
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return new LinkedHashSet<>(superClassQNameList);
    }

    private ClassInfo getClassInfo(ResultSet resultSet) throws SQLException {
        ClassInfo classInfo = new ClassInfo();

        classInfo.setId(resultSet.getInt("id"));

        classInfo.setName(resultSet.getString("name"));
        classInfo.setQualifiedName(resultSet.getString("q_name"));
        classInfo.setPackageName(resultSet.getString("package_name"));

        classInfo.setAbstract(resultSet.getBoolean("is_abstract"));
        classInfo.setInterface(resultSet.getBoolean("is_interface"));
        classInfo.setEnum(resultSet.getBoolean("is_enum"));
        classInfo.setPublic(resultSet.getBoolean("is_public"));
        classInfo.setPrivate(resultSet.getBoolean("is_private"));
        classInfo.setProtected(resultSet.getBoolean("is_protected"));
        classInfo.setInnerClass(resultSet.getBoolean("is_inner_class"));
        classInfo.setAnonymousInnerClass(resultSet.getBoolean("is_anonymous_inner_class"));

        classInfo.setType(Type.getType(resultSet.getString("type_descriptor")));
        classInfo.setSignature(resultSet.getString("signature"));
        classInfo.setJarId(resultSet.getInt("jar_id"));

        classInfo.setTypeInfo(getClassTypeInfo(classInfo.getType(), classInfo.getQualifiedName(), classInfo.getSignature()));

        return classInfo;
    }


    private TypeInfo getClassTypeInfo(Type type, String qualifiedName, String signature) {
        if (Objects.isNull(signature)) {
            return getTypeInfo(type);
        } else {
            ClassSignatureFormalTypeParameterExtractor extractor = new ClassSignatureFormalTypeParameterExtractor();

            SignatureReader signatureReader = new SignatureReader(signature);
            signatureReader.accept(extractor);

            if (extractor.getTypeArgumentList().isEmpty()) {
                return new QualifiedTypeInfo(qualifiedName);

            } else {
                ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo(qualifiedName);
                parameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(extractor.getTypeArgumentList()));

                return parameterizedTypeInfo;
            }
        }
    }

    /*
     * The current assumption is that type of classInfo can only be a primitive type, or qualified if it is not
     * parameterized type.
     */
    private TypeInfo getTypeInfo(Type type) {
        if (PrimitiveTypeUtils.isPrimitiveType(type.getClassName())) {
            return new PrimitiveTypeInfo(type.getClassName());
        } else {
            return new QualifiedTypeInfo(type.getClassName());
        }
    }

}
