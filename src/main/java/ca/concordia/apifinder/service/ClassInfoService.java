package ca.concordia.apifinder.service;

import ca.concordia.apifinder.entity.ClassInfo;
import ca.concordia.apifinder.models.typeInfo.ParameterizedTypeInfo;
import ca.concordia.apifinder.models.typeInfo.QualifiedTypeInfo;
import ca.concordia.apifinder.models.typeInfo.TypeInfo;
import ca.concordia.apifinder.util.DataSource;
import ca.concordia.apifinder.util.DbUtils;
import ca.concordia.apifinder.util.EntityUtils;
import ca.concordia.apifinder.util.signaturevisitor.ClassSignatureFormalTypeParameterExtractor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Diptopol
 * @since 7/14/2022 10:39 AM
 */
public class ClassInfoService {

    private static final Logger logger = LoggerFactory.getLogger(ClassInfoService.class);

    private static Cache<String, List<ClassInfo>> classLoaderCacheFromJarIdList;

    private static Cache<String, Set<String>> superClassLoaderCache;

    private static Cache<Integer, ClassInfo> classLoaderCacheFromId;

    public ClassInfoService() {
        if (Objects.isNull(classLoaderCacheFromJarIdList)) {
            classLoaderCacheFromJarIdList  = Caffeine.newBuilder()
                    .expireAfterAccess(5, TimeUnit.MINUTES)
                    .maximumSize(500)
                    .build();
        }

        if (Objects.isNull(classLoaderCacheFromId)) {
            classLoaderCacheFromId = Caffeine.newBuilder()
                    .expireAfterAccess(5, TimeUnit.MINUTES)
                    .maximumSize(500)
                    .build();
        }

        if (Objects.isNull(superClassLoaderCache)) {
            superClassLoaderCache = Caffeine.newBuilder()
                    .expireAfterAccess(5, TimeUnit.MINUTES)
                    .maximumSize(500)
                    .build();
        }
    }

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

        Map<String, List<Integer>> classInfoNameMap = new HashMap<>();

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
                    classInfoNameMap.put(resultSet.getString("q_name"),
                            new ArrayList<>(Arrays.asList(resultSet.getInt("id"))));
                } else {
                    List<Integer> classIdList = classInfoNameMap.get(resultSet.getString("q_name"));
                    classIdList.add(resultSet.getInt("id"));

                    classInfoNameMap.put(resultSet.getString("q_name"), classIdList);
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
                classInfoIdList.addAll(classInfoNameMap.get(qName));
            }
        }

        return classInfoIdList;
    }

    public List<ClassInfo> getClassInfoListUsingInMemoryCache(List<Integer> jarIdList, String className) {

        String classCacheKey = className.concat(":")
                .concat(String.join(",", jarIdList.stream().sorted().map(String::valueOf).toArray(String[]::new)));


        List<ClassInfo> classInfoList = classLoaderCacheFromJarIdList.getIfPresent(classCacheKey);

        if (Objects.isNull(classInfoList)) {
            classInfoList = getClassInfoList(jarIdList, className);

            classLoaderCacheFromJarIdList.put(classCacheKey, classInfoList);
        }

        return getCopiedClassInfoList(classInfoList);
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

    public ClassInfo getClassInfoUsingMemoryCache(int classInfoId, Connection connection) throws SQLException {
        ClassInfo classInfo = classLoaderCacheFromId.getIfPresent(classInfoId);

        if (Objects.isNull(classInfo)) {
            classInfo = getClassInfo(classInfoId, connection);

            if (Objects.nonNull(classInfo)) {
                classLoaderCacheFromId.put(classInfoId, classInfo);
            }
        }

        return getCopiedClassInfo(classInfo);
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

    public Set<String> getSuperClassQNameSetUsingMemCache(Set<String> classQualifiedNameSet, List<Integer> jarIdList, String type) {
        String classQNameKey = String.join(",", classQualifiedNameSet.stream().sorted().toArray(String[]::new));
        classQNameKey = classQNameKey.concat(":")
                .concat(String.join(",", jarIdList.stream().sorted().map(String::valueOf).toArray(String[]::new)));

        if (Objects.nonNull(type)) {
            classQNameKey = classQNameKey.concat(":").concat(type);
        }

        Set<String> superClassSet = superClassLoaderCache.getIfPresent(classQNameKey);

        if (Objects.isNull(superClassSet)) {
            superClassSet = getSuperClassQNameSet(classQualifiedNameSet, jarIdList, type);

            superClassLoaderCache.put(classQNameKey, superClassSet);
        }

        return new LinkedHashSet<>(superClassSet);
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

    private List<ClassInfo> getCopiedClassInfoList(List<ClassInfo> classInfoList) {
        List<ClassInfo> copiedCLassInfoList = new ArrayList<>();

        for (ClassInfo classInfo : classInfoList) {
            copiedCLassInfoList.add(getCopiedClassInfo(classInfo));
        }

        return copiedCLassInfoList;
    }

    private ClassInfo getCopiedClassInfo(ClassInfo classInfo) {
        if (Objects.isNull(classInfo)) {
            return null;
        }

        TypeInfo newClassTypeInfo = getClassTypeInfo(classInfo.getType(), classInfo.getQualifiedName(),
                classInfo.getSignature());

        return new ClassInfo(classInfo, newClassTypeInfo);
    }

    private TypeInfo getClassTypeInfo(Type type, String qualifiedName, String signature) {
        if (Objects.isNull(signature)) {
            return EntityUtils.getTypeInfo(type);
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

}
