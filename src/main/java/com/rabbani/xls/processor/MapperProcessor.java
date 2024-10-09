package com.rabbani.xls.processor;

import com.rabbani.xls.annotation.Col;
import com.rabbani.xls.annotation.Xls;
import com.rabbani.xls.engine.DerSer;
import com.rabbani.xls.engine.DynamicMapper;
import com.rabbani.xls.engine.MapperFactory;
import com.rabbani.xls.util.IdentifierUtils;
import com.rabbani.xls.util.StringUtils;
import com.rabbani.xls.util.UncheckedConsumer;
import com.squareup.javawriter.JavaWriter;
import org.apache.poi.ss.usermodel.Cell;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.*;

@SupportedAnnotationTypes(value = {"com.rabbani.xls.annotation.Xls"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MapperProcessor extends AbstractProcessor {

    private interface MapperFactoryConstants {
        String GET_METHOD = "getDynamic";
    }

    private interface MapperConstants {
        String MAPPED_INSTANCE_METHOD = "mappedInstance";
    }

    private static final String REGISTER_MAPPER_FIELD = "REGISTER_MAPPER";

    private static final String CLASS_KIND = "class";

    private static final String INTEFACE_KIND = "interface";

    private static final String PACKAGE = "com.rabbani.xls.engine.impl";

    private static final String SERIALIZER_CLASS = "__Serializer";

    private static final String DESERIALIZER_CLASS = "__Deserializer";

    private static final String MAPPER_IMPLEMENTATION_PREFIX = "___Mapper";

    private static final String MAPPER_FACTORY_CLASSNAME = "AutoMapperFactory";

    private Map<String, String> typeMaps = new HashMap<>();

    private Map<String, StaticDerSer> serializerRegistry = new HashMap<>();

    private Map<String, StaticDerSer> deserializerRegistry = new HashMap<>();

    private Types types;

    private Filer filer;

    private Elements elements;

    private Messager messager;

    private TypeElement mapType;

    private TypeElement classType;

    private TypeElement mapperType;

    private TypeElement hashMapType;

    private TypeElement dynamicMapperType;

    private TypeMirror stringUtilsTypeMirror;

    private TypeMirror wildcardTypeMirror;

    private TypeMirror cellTypeMirror;

    private TypeElement uncheckedConsumerType;

    private TypeElement columnMapperType;

    private TypeElement colType;

    private TypeMirror byteType;

    private TypeMirror shortType;

    private TypeMirror integerType;

    private TypeMirror longType;

    private TypeMirror characterType;

    private TypeMirror floatType;

    private TypeMirror doubleType;

    private TypeMirror booleanType;

    private TypeMirror stringType;

    private boolean isMapperWritten = false;

    private static final String CELL_VAR = "cell";

    private static final String VALUE_VAR = "value";

    private static final String CELL_VALUE_STRING_VAR = "cellValue";

    private static final String COLUMN_MAPPERS_REGISTER = "columnMapperRegister";

    private IdentifierUtils globalIdentifier = new IdentifierUtils();


    @Override
    public synchronized boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager = processingEnv.getMessager();
        types = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        elements = processingEnv.getElementUtils();
        mapType = elements.getTypeElement(Map.class.getName());
        classType = elements.getTypeElement(Class.class.getName());
        hashMapType = elements.getTypeElement(HashMap.class.getName());
        mapperType = elements.getTypeElement(DynamicMapper.class.getName());
        dynamicMapperType = elements.getTypeElement(DynamicMapper.class.getName());
        colType = elements.getTypeElement(Col.class.getName());
        stringUtilsTypeMirror = elements.getTypeElement(StringUtils.class.getName()).asType();
        uncheckedConsumerType = elements.getTypeElement(UncheckedConsumer.class.getName());
        wildcardTypeMirror = types.getWildcardType(null, null);
        columnMapperType = elements.getTypeElement(DynamicMapper.ColumnMapper.class.getCanonicalName());
        cellTypeMirror = elements.getTypeElement(Cell.class.getName()).asType();
        byteType = elements.getTypeElement(Byte.class.getName()).asType();
        shortType = elements.getTypeElement(Short.class.getName()).asType();
        integerType = elements.getTypeElement(Integer.class.getName()).asType();
        longType = elements.getTypeElement(Long.class.getName()).asType();
        floatType = elements.getTypeElement(Float.class.getName()).asType();
        doubleType = elements.getTypeElement(Double.class.getName()).asType();
        characterType = elements.getTypeElement(Character.class.getName()).asType();
        booleanType = elements.getTypeElement(Boolean.class.getName()).asType();
        stringType = elements.getTypeElement(String.class.getName()).asType();

        roundEnv.getElementsAnnotatedWith(Xls.class)
                .stream()
                .map(TypeElement.class::cast)
                .forEach(this::dynamicMapperDomainWriter);
        if (!isMapperWritten) {
            writeMapperFactory();
            isMapperWritten = true;
            writeSerializerRegistry();
            writeDeserializerRegistry();
        }

        return true;
    }

    private void writeSerializerRegistry(){
        JavaWriter codeWriter = null;
        try {
            JavaFileObject source = filer.createSourceFile(PACKAGE + "." + SERIALIZER_CLASS);
            codeWriter = new JavaWriter(source.openWriter());
            codeWriter.emitPackage(PACKAGE);
            codeWriter.emitEmptyLine();
            codeWriter.beginType(SERIALIZER_CLASS,INTEFACE_KIND,EnumSet.of(Modifier.PUBLIC));
            for(Map.Entry<String,StaticDerSer> entry:serializerRegistry.entrySet()){
                StaticDerSer derSer = entry.getValue();
                codeWriter.emitField(derSer.type.toString(),derSer.name,EnumSet.of(Modifier.PUBLIC,Modifier.FINAL),String.format("new %s(\"%s\")",derSer.type.toString(),derSer.param));
            }
            codeWriter.endType();
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
        finally {
            try {
                if (codeWriter != null) {
                    codeWriter.close();
                }
            }
            catch (Exception e){
                throw new RuntimeException(e);
            }
        }
    }

    private void writeDeserializerRegistry(){
        JavaWriter codeWriter = null;
        try {
            JavaFileObject source = filer.createSourceFile(PACKAGE + "." + DESERIALIZER_CLASS);
            codeWriter = new JavaWriter(source.openWriter());
            codeWriter.emitPackage(PACKAGE);
            codeWriter.emitEmptyLine();
            codeWriter.beginType(DESERIALIZER_CLASS,INTEFACE_KIND,EnumSet.of(Modifier.PUBLIC));
            for(Map.Entry<String,StaticDerSer> entry:deserializerRegistry.entrySet()){
                StaticDerSer derSer = entry.getValue();
                codeWriter.emitField(derSer.type.toString(),derSer.name,EnumSet.of(Modifier.PUBLIC,Modifier.FINAL),String.format("new %s(\"%s\")",derSer.type.toString(),derSer.param));
            }
            codeWriter.endType();
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
        finally {
            try {
                if (codeWriter != null) {
                    codeWriter.close();
                }
            }
            catch (Exception e){
                throw new RuntimeException(e);
            }
        }
    }

    private void writeMapperFactory() {
        try {
            IdentifierUtils identifierUtils = new IdentifierUtils();
            TypeElement mapperFactory = elements.getTypeElement(MapperFactory.class.getName());
            String qualifiedClassName = PACKAGE + "." + MAPPER_FACTORY_CLASSNAME;

            JavaFileObject source = filer.createSourceFile(qualifiedClassName);
            JavaWriter writer = new JavaWriter(source.openWriter());
            writer.emitPackage(PACKAGE);

            writer.beginType(MAPPER_FACTORY_CLASSNAME, CLASS_KIND, EnumSet.of(Modifier.PUBLIC), null, mapperFactory.asType().toString());
            writer.emitEmptyLine();

            DeclaredType fieldMap = types.getDeclaredType(mapType, types.getDeclaredType(classType, wildcardTypeMirror), types.getDeclaredType(mapperType, wildcardTypeMirror));
            writer.emitField(fieldMap.toString(), REGISTER_MAPPER_FIELD, EnumSet.of(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC));
            writer.emitEmptyLine();

            writer.beginInitializer(true);
            writer.emitStatement("%s = new %s<>()", REGISTER_MAPPER_FIELD, types.getDeclaredType(hashMapType));
            for (String className : typeMaps.keySet()) {
                String mapperType = typeMaps.get(className);
                writer.emitStatement("%s.put(%s.class,new %s())", REGISTER_MAPPER_FIELD, className, mapperType);
            }
            writer.endInitializer();


            Element element = mapperFactory.getEnclosedElements().stream()
                    .filter(targetElement -> targetElement.getKind() == ElementKind.METHOD && MapperFactoryConstants.GET_METHOD.equals(targetElement.getSimpleName().toString()))
                    .findAny().orElse(null);
            ExecutableElement implementedMethod = (ExecutableElement) element;
            ExecutableType execType = (ExecutableType) implementedMethod.asType();
            TypeMirror typeVariable = execType.getTypeVariables().get(0).asElement().asType();
            TypeMirror parameter = execType.getParameterTypes().get(0);
            String paramName = identifierUtils.createName(parameter.toString());
            writer.emitEmptyLine();
            writer.emitAnnotation(Override.class);
            writer.beginMethod("<" + typeVariable.toString() + ">" + execType.getReturnType().toString(), implementedMethod.getSimpleName().toString(), EnumSet.of(Modifier.PUBLIC), parameter.toString(), paramName);
            writer.emitStatement("return (%s)%s.get(%s)", types.getDeclaredType(mapperType, typeVariable).toString(), REGISTER_MAPPER_FIELD, paramName);
            writer.endMethod();
            writer.emitEmptyLine();
            writer.endType();
            writer.close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void dynamicMapperDomainWriter(TypeElement target) {
        try {
            String packageName = elements.getPackageOf(target).getQualifiedName().toString();
            String className = target.getSimpleName().toString() + MAPPER_IMPLEMENTATION_PREFIX;
            String qualifiedClassName = packageName + "." + className;
            DeclaredType targetTypeMirror = (DeclaredType) target.asType();

            DeclaredType extendedType = types.getDeclaredType(dynamicMapperType, targetTypeMirror);
            typeMaps.put(target.toString(), qualifiedClassName);

            ExecutableElement instanceMethodFactory = (ExecutableElement) extendedType.asElement().getEnclosedElements()
                    .stream()
                    .filter(element -> element.getKind() == ElementKind.METHOD &&
                            element.getSimpleName().toString().equals(MapperConstants.MAPPED_INSTANCE_METHOD) &&
                            element.getModifiers().contains(Modifier.ABSTRACT))
                    .findAny().get();


            JavaFileObject source = filer.createSourceFile(qualifiedClassName);
            JavaWriter writer = new JavaWriter(source.openWriter());
            writer.emitPackage(packageName);
            writer.beginType(className, CLASS_KIND, EnumSet.of(Modifier.PUBLIC), extendedType.toString());
            writer.emitEmptyLine();
            writer.beginConstructor(EnumSet.of(Modifier.PUBLIC));
            writer.emitStatement("super()");
            writer.emitStatement("init()");
            writer.endConstructor();
            writer.emitEmptyLine();

            writer.beginMethod("void", "init", EnumSet.of(Modifier.PRIVATE));
            DeclaredType serializerConsumer = types.getDeclaredType(uncheckedConsumerType,cellTypeMirror,targetTypeMirror);
            IdentifierUtils identifierUtils = new IdentifierUtils();
            DeclaredType columnMapperTypeMirror = types.getDeclaredType(columnMapperType,targetTypeMirror);
            for (Element element : target.getEnclosedElements()) {

                AnnotationMirror colDescription = Optional.<List<? extends AnnotationMirror>>ofNullable(element.getAnnotationMirrors())
                        .flatMap(annotationMirrors -> annotationMirrors.stream()
                                .filter(annotationMirror -> annotationMirror.getAnnotationType().asElement().equals(colType))
                                .findAny())
                        .orElse(null);
                if (colDescription == null) {
                    continue;
                }

                VariableElement field = (VariableElement) element;

                String label = null;
                AnnotationMirror serializer = null;
                AnnotationMirror deserializer = null;
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> valueEntry : colDescription.getElementValues().entrySet()) {
                    ExecutableElement executableElement = valueEntry.getKey();
                    AnnotationValue annotationValue = valueEntry.getValue();
                    String name = executableElement.getSimpleName().toString();
                    if ("value".equals(name)) {
                        label = annotationValue.getValue().toString();
                    } else if ("serializer".equals(name)) {
                        serializer =  (AnnotationMirror) annotationValue.getValue();
                    } else if ("deserializer".equals(name)) {
                        deserializer =  (AnnotationMirror) annotationValue.getValue();
                    }
                }


                String serializerVar = writeSerializeImplementation(writer,identifierUtils,field,serializer,serializerConsumer,label);
                String deserializerVar = writeDeserializeImplementation(writer,identifierUtils,field,deserializer,serializerConsumer,label);
                writer.emitStatement("%s.put(\"%s\",new %s(\"%2$s\",%s,%s))", COLUMN_MAPPERS_REGISTER,label,columnMapperTypeMirror,serializerVar,deserializerVar);
            }
            writer.endMethod();
            writer.emitEmptyLine();
            writer.emitAnnotation(Override.class);
            writer.beginMethod(target.getQualifiedName().toString(), instanceMethodFactory.getSimpleName().toString(), EnumSet.of(Modifier.PUBLIC));
            writer.emitStatement("return new %s()", target.getQualifiedName().toString());
            writer.endMethod();
            writer.emitEmptyLine();
            writer.endType();
            writer.close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private String writeSerializeImplementation(JavaWriter writer,
                                              IdentifierUtils identifierUtils,
                                              VariableElement fieldElement,
                                              AnnotationMirror annotationMirror,
                                              DeclaredType serializerConsumer,
                                              String label) throws Exception{

        String serializerIdentifier = null;
        if(annotationMirror != null) {
            StaticDerSer staticDerSer = new StaticDerSer();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> serEntry : annotationMirror.getElementValues().entrySet()) {
                String iName = serEntry.getKey().getSimpleName().toString();
                if ("param".equals(iName)) {
                    staticDerSer.param = serEntry.getValue().getValue().toString();
                } else if ("value".equals(iName)) {
                    staticDerSer.type = (DeclaredType) serEntry.getValue().getValue();
                }
            }

            serializerIdentifier = serializerRegistry.computeIfAbsent(staticDerSer.type.toString() + "#" + staticDerSer.param, identifier -> {
                staticDerSer.name = globalIdentifier.createName(identifier);
                return staticDerSer;
            }).name;
        }


        TypeMirror fieldTypeMirror = fieldElement.asType();
        TypeKind fieldTypeKind = fieldTypeMirror.getKind();

        String readerIdentifier = identifierUtils.createName(serializerConsumer.toString()+"Reader");

        writer.beginControlFlow("%s %s = (%s,%s)->", serializerConsumer.toString(), readerIdentifier, CELL_VAR, VALUE_VAR);
        if(serializerIdentifier != null){
            writer.emitStatement("%s.%s.apply(%s,\"%s\",%s)",PACKAGE+"."+SERIALIZER_CLASS,serializerIdentifier,CELL_VAR,label,VALUE_VAR);
        }
        else {
            writer.emitStatement("String %s = %s.getStringCellValue()", CELL_VALUE_STRING_VAR, CELL_VAR);

            if (fieldTypeKind == TypeKind.BYTE || fieldTypeMirror.equals(byteType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Byte.parseByte(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.SHORT || fieldTypeMirror.equals(shortType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Short.parseShort(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.INT || fieldTypeMirror.equals(integerType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Integer.parseInt(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.LONG || fieldTypeMirror.equals(longType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Long.parseLong(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "0L" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.FLOAT || fieldTypeMirror.equals(floatType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Float.parseFloat(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "0F" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.DOUBLE || fieldTypeMirror.equals(doubleType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Double.parseDouble(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "0D" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.CHAR || fieldTypeMirror.equals(characterType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = %s.charAt(0)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "\u0000" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.BOOLEAN || fieldTypeMirror.equals(booleanType)) {
                writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.%s = Boolean.parseBoolean(%s)", VALUE_VAR, fieldElement.getSimpleName(), CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
                writer.beginControlFlow("else");
                String setValue = fieldTypeKind.isPrimitive() ? "\u0000" : "null";
                writer.emitStatement("%s.%s = %s", VALUE_VAR, fieldElement.getSimpleName(), setValue);
                writer.endControlFlow();
            } else if (fieldTypeMirror.equals(stringType)) {
                writer.emitStatement("%s.%s = (!%s.isEmpty(%s))%s:%s", VALUE_VAR, fieldElement.getSimpleName(), stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR, CELL_VALUE_STRING_VAR, "null");
            }
        }
        writer.endControlFlow("");
        return readerIdentifier;
    }

    private String writeDeserializeImplementation(JavaWriter writer,
                                                IdentifierUtils identifierUtils,
                                                VariableElement fieldElement,
                                                AnnotationMirror annotationMirror,
                                                DeclaredType serializerConsumer,
                                                String label) throws Exception{

        String deserializerIdentifier = null;
        if(annotationMirror != null) {
            StaticDerSer staticDerSer = new StaticDerSer();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> serEntry : annotationMirror.getElementValues().entrySet()) {
                String iName = serEntry.getKey().getSimpleName().toString();
                if ("param".equals(iName)) {
                    staticDerSer.param = serEntry.getValue().getValue().toString();
                } else if ("value".equals(iName)) {
                    staticDerSer.type = (DeclaredType) serEntry.getValue().getValue();
                }
            }

            deserializerIdentifier = deserializerRegistry.computeIfAbsent(staticDerSer.type.toString() + "#" + staticDerSer.param, identifier -> {
                staticDerSer.name = globalIdentifier.createName(identifier);
                return staticDerSer;
            }).name;
        }


        TypeMirror fieldTypeMirror = fieldElement.asType();
        TypeKind fieldTypeKind = fieldTypeMirror.getKind();
        String fieldName = fieldElement.getSimpleName().toString();

        String deserializeIdentifier = identifierUtils.createName(serializerConsumer.toString()+"Writer");

        writer.beginControlFlow("%s %s = (%s,%s)->", serializerConsumer.toString(), deserializeIdentifier, CELL_VAR, VALUE_VAR);
        if(deserializerIdentifier != null){
            writer.emitStatement("%s.%s.apply(%s,\"%s\",%s)",PACKAGE+"."+SERIALIZER_CLASS,deserializerIdentifier,CELL_VAR,label,VALUE_VAR);
        }
        else {
            if (fieldTypeKind == TypeKind.BYTE || fieldTypeMirror.equals(byteType)) {
                writer.emitStatement("Byte %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.SHORT || fieldTypeMirror.equals(shortType)) {
                writer.emitStatement("Short %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.INT || fieldTypeMirror.equals(integerType)) {
                writer.emitStatement("Integer %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.LONG || fieldTypeMirror.equals(longType)) {
                writer.emitStatement("Long %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.FLOAT || fieldTypeMirror.equals(floatType)) {
                writer.emitStatement("Float %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.DOUBLE || fieldTypeMirror.equals(doubleType)) {
                writer.emitStatement("Float %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.CHAR || fieldTypeMirror.equals(characterType)) {
                writer.emitStatement("Character %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(String.valueOf(%s))", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.BOOLEAN || fieldTypeMirror.equals(booleanType)) {
                writer.emitStatement("Boolean %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeMirror.equals(stringType)) {
                writer.emitStatement("String %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR,fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            }
        }
        writer.endControlFlow("");
        return deserializeIdentifier;
    }

    static class StaticDerSer{
        String name;
        DeclaredType type;
        String param;

    }
}
