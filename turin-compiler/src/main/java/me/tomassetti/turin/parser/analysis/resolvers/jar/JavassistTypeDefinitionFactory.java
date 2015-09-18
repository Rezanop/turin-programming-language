package me.tomassetti.turin.parser.analysis.resolvers.jar;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import me.tomassetti.turin.jvm.JvmConstructorDefinition;
import me.tomassetti.turin.jvm.JvmMethodDefinition;
import me.tomassetti.turin.jvm.JvmNameUtils;
import me.tomassetti.turin.parser.ast.TypeDefinition;
import me.tomassetti.turin.parser.ast.typeusage.ArrayTypeUsage;
import me.tomassetti.turin.parser.ast.typeusage.PrimitiveTypeUsage;
import me.tomassetti.turin.parser.ast.typeusage.ReferenceTypeUsage;
import me.tomassetti.turin.parser.ast.typeusage.TypeUsage;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JavassistTypeDefinitionFactory {

    private static final JavassistTypeDefinitionFactory INSTANCE = new JavassistTypeDefinitionFactory();

    public static JavassistTypeDefinitionFactory getInstance() {
        return INSTANCE;
    }

    public static JvmMethodDefinition toMethodDefinition(CtMethod method) throws NotFoundException {
        return new JvmMethodDefinition(JvmNameUtils.canonicalToInternal(method.getDeclaringClass().getName()), method.getName(), calcSignature(method), Modifier.isStatic(method.getModifiers()));
    }

    public static String calcSignature(CtClass clazz) {
        if (clazz.isPrimitive()) {
            switch (clazz.getName()) {
                case "boolean":
                    return "Z";
                case "byte":
                    return "B";
                case "char":
                    return "C";
                case "short":
                    return "S";
                case "int":
                    return "I";
                case "long":
                    return "J";
                case "float":
                    return "F";
                case "double":
                    return "D";
                case "void":
                    return "V";
                default:
                    throw new UnsupportedOperationException(clazz.getName());
            }
        } else if (clazz.isArray()){
            try {
                return "[" + calcSignature(clazz.getComponentType());
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            return "L" + clazz.getName().replaceAll("\\.", "/") + ";";
        }
    }

    public static String calcSignature(CtMethod method) throws NotFoundException {
        List<String> paramTypesSignatures = Arrays.stream(method.getParameterTypes()).map((t) -> calcSignature(t)).collect(Collectors.toList());
        return "(" + String.join("", paramTypesSignatures) + ")" + calcSignature(method.getReturnType());
    }

    public static TypeUsage toTypeUsage(CtClass type) {
        if (type.isArray()) {
            try {
                return new ArrayTypeUsage(toTypeUsage(type.getComponentType()));
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        } else if (type.isPrimitive()) {
            return PrimitiveTypeUsage.getByName(type.getName());
        } else {
            return new ReferenceTypeUsage(type.getName());
        }
    }

    public TypeDefinition getTypeDefinition(CtClass clazz) {
        if (clazz.isArray()) {
            throw new IllegalArgumentException();
        }
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        return new JavassistTypeDefinition(clazz);
    }

    public static JvmConstructorDefinition toConstructorDefinition(CtConstructor constructor) throws NotFoundException {
        return new JvmConstructorDefinition(JvmNameUtils.canonicalToInternal(constructor.getDeclaringClass().getName()), calcSignature(constructor));
    }

    private static String calcSignature(CtConstructor constructor) throws NotFoundException {
        List<String> paramTypesSignatures = Arrays.stream(constructor.getParameterTypes()).map((t) -> calcSignature(t)).collect(Collectors.toList());
        return "(" + String.join("", paramTypesSignatures) + ")V";
    }
}