package ca.concordia.jaranalyzer;

import com.T2R.common.Models.TypeSignatureOuterClass.FilteredType;
import com.T2R.common.Models.TypeSignatureOuterClass.TypeInfo;

import org.objectweb.asm.Type;

public interface Info {

     default TypeInfo getTypeInfo(Type t){
        return TypeInfo.newBuilder()
                .setOf(FilteredType.newBuilder()
                .setInterfaceName(t.getClassName()))
                .build();
    }
}
