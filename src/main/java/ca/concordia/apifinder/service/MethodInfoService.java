package ca.concordia.apifinder.service;

import ca.concordia.apifinder.entity.MethodInfo;
import ca.concordia.apifinder.models.typeInfo.*;
import ca.concordia.apifinder.util.DataSource;
import ca.concordia.apifinder.util.DbUtils;
import ca.concordia.apifinder.util.EntityUtils;
import ca.concordia.apifinder.util.signaturevisitor.MethodArgumentExtractor;
import ca.concordia.apifinder.util.signaturevisitor.MethodReturnTypeExtractor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 7/13/2022 8:59 PM
 */
public class MethodInfoService {

    private static final Logger logger = LoggerFactory.getLogger(MethodInfoService.class);

    private ClassInfoService classInfoService;

    public MethodInfoService() {
        classInfoService = new ClassInfoService();
    }

    public MethodInfoService(ClassInfoService classInfoService) {
        this.classInfoService = classInfoService;
    }

    public List<MethodInfo> getMethodInfoList(List<Integer> classInfoIdList, String methodName) {
        List<MethodInfo> methodInfoList = new ArrayList<>();

        try (Connection connection = DataSource.getConnection()) {
            methodInfoList = fetchMethodInfoCore(classInfoIdList, methodName, connection);
            populateArgumentList(methodInfoList, connection);
            populateThrownClassNameList(methodInfoList, connection);

            for (MethodInfo methodInfo: methodInfoList) {
                populateMethodArgumentTypeInfoList(methodInfo);
                populateMethodReturnTypeInfo(methodInfo);
                methodInfo.setClassInfo(classInfoService.getClassInfoUsingMemoryCache(methodInfo.getClassInfoId(), connection));
                updateFormalTypeParameterBaseType(methodInfo);
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        }

        return methodInfoList;
    }

    public List<MethodInfo> getInnerClassMethodInfoList(Set<String> qualifiedClassNameSet,
                                                        List<Integer> jarIdList,
                                                        String methodName) {
        Set<String> innerClassQNameSet = classInfoService.getInnerClassQualifiedNameSet(qualifiedClassNameSet, jarIdList);
        List<MethodInfo> methodInfoList = new ArrayList<>();

        while (!innerClassQNameSet.isEmpty()) {
            List<Integer> innerClassIdList = classInfoService.getClassInfoIdList(jarIdList, innerClassQNameSet);
            methodInfoList = getMethodInfoList(innerClassIdList, methodName);

            if (!methodInfoList.isEmpty()) {
                return methodInfoList;
            }

            innerClassQNameSet = classInfoService.getInnerClassQualifiedNameSet(innerClassQNameSet, jarIdList);
        }

        return methodInfoList;
    }

    public List<MethodInfo> getAbstractMethodInfoList(List<Integer> jarIdList, Set<String> qualifiedClassNameSet) {
        List<MethodInfo> methodInfoList = new ArrayList<>();

        try (Connection connection = DataSource.getConnection()) {
            methodInfoList = fetchAbstractMethodInfoCore(qualifiedClassNameSet, jarIdList, connection);
            populateArgumentList(methodInfoList, connection);
            populateThrownClassNameList(methodInfoList, connection);

            for (MethodInfo methodInfo: methodInfoList) {
                populateMethodArgumentTypeInfoList(methodInfo);
                populateMethodReturnTypeInfo(methodInfo);
                methodInfo.setClassInfo(classInfoService.getClassInfoUsingMemoryCache(methodInfo.getClassInfoId(), connection));
                updateFormalTypeParameterBaseType(methodInfo);
            }

            connection.commit();
        } catch (SQLException e) {
            logger.error("Error", e);
        }

        return methodInfoList;
    }

    private List<MethodInfo> fetchAbstractMethodInfoCore(Set<String> qualifiedClassNameSet,
                                                         List<Integer> jarIdList,
                                                         Connection connection) throws SQLException {
        List<MethodInfo> methodInfoList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT m.* FROM method m JOIN class c ON (m.class_id = c.id)" +
                " WHERE c.jar_id IN (" + DbUtils.getInClausePlaceHolder(jarIdList.size()) + ")" +
                " AND c.q_name IN (" + DbUtils.getInClausePlaceHolder(qualifiedClassNameSet.size()) + ")" +
                " AND m.is_abstract = ?";

        try {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (Integer jarId : jarIdList) {
                pst.setInt(index++, jarId);
            }

            for (String qualifiedClassName: qualifiedClassNameSet) {
                pst.setString(index++, qualifiedClassName);
            }

            pst.setBoolean(index, true);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                methodInfoList.add(getMethodInfo(resultSet));
            }

        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        return methodInfoList;
    }

    private List<MethodInfo> fetchMethodInfoCore(List<Integer> classInfoIdList,
                                                 String methodName,
                                                 Connection connection) throws SQLException {
        List<MethodInfo> methodInfoList = new ArrayList<>();

        PreparedStatement pst = null;
        ResultSet resultSet = null;

        String query = "SELECT m.* FROM method m WHERE m.class_id IN (" + DbUtils.getInClausePlaceHolder(classInfoIdList.size()) + ")" +
                " AND m.name = ?";

        try {
            pst = connection.prepareStatement(query);

            int index = 1;
            for (int classInfoId : classInfoIdList) {
                pst.setInt(index++, classInfoId);
            }

            pst.setString(index, methodName);

            resultSet = pst.executeQuery();

            while (resultSet.next()) {
                MethodInfo methodInfo = getMethodInfo(resultSet);
                methodInfoList.add(methodInfo);
            }
        } finally {
            DbUtils.closeResources(pst, resultSet);
        }

        List<MethodInfo> orderedMethodInfoList = new ArrayList<>();

        for (Integer classInfoId: classInfoIdList) {
            orderedMethodInfoList.addAll(methodInfoList.stream()
                    .filter(m -> m.getClassInfoId() == classInfoId)
                    .collect(Collectors.toList()));
        }

        return orderedMethodInfoList;
    }

    private MethodInfo getMethodInfo(ResultSet resultSet) throws SQLException {
        MethodInfo methodInfo = new MethodInfo();

        methodInfo.setId(resultSet.getInt("id"));
        methodInfo.setName(resultSet.getString("name"));

        methodInfo.setAbstract(resultSet.getBoolean("is_abstract"));
        methodInfo.setConstructor(resultSet.getBoolean("is_constructor"));
        methodInfo.setStatic(resultSet.getBoolean("is_static"));
        methodInfo.setPublic(resultSet.getBoolean("is_public"));
        methodInfo.setPrivate(resultSet.getBoolean("is_private"));
        methodInfo.setProtected(resultSet.getBoolean("is_protected"));
        methodInfo.setSynchronized(resultSet.getBoolean("is_synchronized"));
        methodInfo.setFinal(resultSet.getBoolean("is_final"));
        methodInfo.setVarargs(resultSet.getBoolean("is_varargs"));
        methodInfo.setBridgeMethod(resultSet.getBoolean("is_bridge_method"));

        methodInfo.setSignature(resultSet.getString("signature"));
        methodInfo.setInternalClassConstructorPrefix(resultSet.getString("internal_class_constructor_prefix"));
        methodInfo.setReturnType(Type.getType(resultSet.getString("return_type_descriptor")));

        methodInfo.setClassInfoId(resultSet.getInt("class_id"));
        return methodInfo;
    }

    private void populateArgumentList(List<MethodInfo> methodInfoList, Connection connection) throws SQLException {
        for (MethodInfo methodInfo: methodInfoList) {
            PreparedStatement pst = null;
            ResultSet resultSet = null;

            String query = "SELECT arg.argument_type_descriptor argument_type_descriptor FROM argument_type_descriptor arg" +
                    " WHERE arg.method_id = ? ORDER BY arg.precedence_order ASC";

            List<Type> argumentTypeList = new ArrayList<>();

            try {
                pst = connection.prepareStatement(query);
                pst.setInt(1, methodInfo.getId());

                resultSet = pst.executeQuery();

                while (resultSet.next()) {
                    argumentTypeList.add(Type.getType(resultSet.getString("argument_type_descriptor")));
                }

                methodInfo.setArgumentTypes(argumentTypeList.toArray(new Type[0]));
            } finally {
                DbUtils.closeResources(pst, resultSet);
            }
        }
    }

    private void populateThrownClassNameList(List<MethodInfo> methodInfoList, Connection connection) throws SQLException {
        for (MethodInfo methodInfo: methodInfoList) {
            PreparedStatement pst = null;
            ResultSet resultSet = null;

            String query = "SELECT thr.thrown_class_name thrown_class_name FROM thrown_class_name thr" +
                    " WHERE thr.method_id = ? ORDER BY thr.precedence_order ASC";

            List<String> thrownClassQNameList = new ArrayList<>();

            try {
                pst = connection.prepareStatement(query);
                pst.setInt(1, methodInfo.getId());

                resultSet = pst.executeQuery();

                while (resultSet.next()) {
                    thrownClassQNameList.add(resultSet.getString("thrown_class_name"));
                }

                methodInfo.setThrownInternalClassNames(thrownClassQNameList);
            } finally {
                DbUtils.closeResources(pst, resultSet);
            }
        }
    }

    private void populateMethodArgumentTypeInfoList(MethodInfo methodInfo) {
        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();
        List<TypeInfo> formalTypeParameterList = new ArrayList<>();

        if (Objects.nonNull(methodInfo.getSignature())) {
            MethodArgumentExtractor methodArgumentExtractor = new MethodArgumentExtractor();
            SignatureReader signatureReader = new SignatureReader(methodInfo.getSignature());

            signatureReader.accept(methodArgumentExtractor);
            formalTypeParameterList = methodArgumentExtractor.getFormalTypeParameterList();

            methodInfo.setFormalTypeParameterList(formalTypeParameterList);
            methodInfo.setArgumentTypeInfoList(methodArgumentExtractor.getArgumentList());
        } else {
            for (Type argumentType: methodInfo.getArgumentTypes()) {
                argumentTypeInfoList.add(EntityUtils.getTypeInfo(argumentType));
            }

            methodInfo.setFormalTypeParameterList(formalTypeParameterList);
            methodInfo.setArgumentTypeInfoList(argumentTypeInfoList);
        }
    }

    private void populateMethodReturnTypeInfo(MethodInfo methodInfo) {
        if (Objects.nonNull(methodInfo.getSignature())) {
            MethodReturnTypeExtractor extractor = new MethodReturnTypeExtractor();

            SignatureReader reader = new SignatureReader(methodInfo.getSignature());
            reader.accept(extractor);

            methodInfo.setReturnTypeInfo(extractor.getReturnTypeInfo());
        } else {
            if (Type.VOID_TYPE.equals(methodInfo.getReturnType())) {
                methodInfo.setReturnTypeInfo(new VoidTypeInfo());
            } else {
                methodInfo.setReturnTypeInfo(EntityUtils.getTypeInfo(methodInfo.getReturnType()));
            }
        }
    }

    private void updateFormalTypeParameterBaseType(MethodInfo methodInfo) {
        if (Objects.nonNull(methodInfo.getClassInfo())) {
            TypeInfo classTypeInfo = methodInfo.getClassInfo().getTypeInfo();

            if (classTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo parameterizedClassTypeInfo = (ParameterizedTypeInfo) classTypeInfo;
                List<TypeInfo> typeArgumentList = parameterizedClassTypeInfo.getTypeArgumentList();

                Map<String, TypeInfo> baseTypeInfoMap = typeArgumentList.stream()
                        .filter(TypeInfo::isFormalTypeParameterInfo)
                        .map(t -> (FormalTypeParameterInfo) t)
                        .collect(Collectors.toMap(FormalTypeParameterInfo::getTypeParameter,
                                FormalTypeParameterInfo::getBaseTypeInfo));

                for (TypeInfo argument : methodInfo.getArgumentTypeInfoList()) {
                    populateBaseType(argument, baseTypeInfoMap);
                }

                populateBaseType(methodInfo.getReturnTypeInfo(), baseTypeInfoMap);
            }
        }
    }

    private void populateBaseType(TypeInfo typeInfo, Map<String, TypeInfo> baseTypeInfoMap) {
        if (typeInfo.isFormalTypeParameterInfo()
                && baseTypeInfoMap.containsKey(((FormalTypeParameterInfo) typeInfo).getTypeParameter())) {

            FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeInfo;
            formalTypeParameterInfo.setBaseTypeInfo(baseTypeInfoMap.get(formalTypeParameterInfo.getTypeParameter()));
        } else if (typeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;

            for (TypeInfo typeArgument: parameterizedTypeInfo.getTypeArgumentList()) {
                populateBaseType(typeArgument, baseTypeInfoMap);
            }
        }
    }

}
