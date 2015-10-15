package me.tomassetti.turin.parser.analysis.resolvers.compiled;

import javassist.ClassPath;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import me.tomassetti.turin.parser.analysis.resolvers.TypeResolver;
import me.tomassetti.turin.parser.ast.FormalParameter;
import me.tomassetti.turin.parser.ast.invokables.FunctionDefinition;
import me.tomassetti.turin.parser.ast.NodeTypeDefinition;
import me.tomassetti.turin.parser.ast.typeusage.TypeUsage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public abstract class AbstractCompiledTypeResolver<CE extends ClasspathElement> implements TypeResolver  {
    protected Map<String, CE> classpathElements = new HashMap<>();
    protected Map<String, CE> functionElements = new HashMap<>();
    protected Set<String> packages = new HashSet<>();

    @Override
    public boolean existPackage(String packageName) {
        return packages.contains(packageName);
    }

    protected class CompiledClassPath implements ClassPath {

        @Override
        public InputStream openClassfile(String qualifiedName) throws NotFoundException {
            try {
                if (classpathElements.containsKey(qualifiedName)) {
                    return classpathElements.get(qualifiedName).toInputStream();
                } else {
                    return null;
                }
            } catch (IOException e) {
                throw new NotFoundException(e.getMessage());
            }
        }

        @Override
        public URL find(String qualifiedName) {
            if (classpathElements.containsKey(qualifiedName)) {
                return classpathElements.get(qualifiedName).toURL();
            } else {
                return null;
            }
        }

        @Override
        public void close() {
            // nothing to do here
        }
    }

    protected String classFileToClassName(File classFile, File root){
        String absPathFile = classFile.getAbsolutePath();
        String absPathRoot = root.getAbsolutePath();
        if (!(absPathFile.length() > absPathRoot.length())){
            throw new IllegalStateException();
        }
        String relativePath = absPathFile.substring(absPathRoot.length());
        if (!relativePath.endsWith(".class")){
            throw new IllegalStateException();
        }
        String className = relativePath.substring(0, relativePath.length() - ".class".length());
        className = className.replaceAll("/", ".");
        className = className.replaceAll("\\$", ".");
        if (className.startsWith(".")) {
            className = className.substring(1);
        }
        return className;
    }

    protected String classFileToFunctionName(File classFile, File root){
        String absPathFile = classFile.getParentFile().getAbsolutePath();
        String absPathRoot = root.getAbsolutePath();
        if (!(absPathFile.length() > absPathRoot.length())){
            throw new IllegalStateException();
        }
        String relativePath = absPathFile.substring(absPathRoot.length());
        String functionName = relativePath;
        functionName = functionName.replaceAll("/", ".");
        functionName = functionName.replaceAll("\\$", ".");
        functionName += ".";
        functionName += classFile.getName().substring(FunctionDefinition.CLASS_PREFIX.length());
        functionName = functionName.substring(0, functionName.length() - ".class".length());
        if (functionName.startsWith(".")) {
            functionName = functionName.substring(1);
        }
        return functionName;
    }

    @Override
    public Optional<NodeTypeDefinition> resolveAbsoluteTypeName(String typeName) {
        if (classpathElements.containsKey(typeName)) {
            try {
                CtClass ctClass = classpathElements.get(typeName).toCtClass();
                return Optional.of(new JavassistNodeTypeDefinition(ctClass));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<FunctionDefinition> resolveAbsoluteFunctionName(String typeName) {
        if (functionElements.containsKey(typeName)) {
            try {
                CtClass ctClass = functionElements.get(typeName).toCtClass();
                if (ctClass.getDeclaredMethods().length != 1) {
                    throw new UnsupportedOperationException();
                }
                CtMethod invokeMethod = ctClass.getDeclaredMethods()[0];
                if (!invokeMethod.getName().equals(FunctionDefinition.INVOKE_METHOD_NAME)) {
                    throw new UnsupportedOperationException();
                }
                // necessary to get local var names
                MethodInfo methodInfo = invokeMethod.getMethodInfo();
                CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
                LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);

                TypeUsage returnType = JavassistTypeDefinitionFactory.toTypeUsage(invokeMethod.getReturnType());
                List<FormalParameter> formalParameters = new ArrayList<>();

                int i=0;
                for (CtClass paramType : invokeMethod.getParameterTypes()) {
                    TypeUsage type =JavassistTypeDefinitionFactory.toTypeUsage(paramType);
                    String paramName = attr.variableName(i);
                    formalParameters.add(new FormalParameter(type, paramName));
                    i++;
                }
                FunctionDefinition functionDefinition = new LoadedFunctionDefinition(typeName, returnType, formalParameters);
                return Optional.of(functionDefinition);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Optional.empty();
        }
    }
}
