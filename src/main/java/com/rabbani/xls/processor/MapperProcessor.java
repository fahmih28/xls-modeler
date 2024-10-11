package com.rabbani.xls.processor;

import com.rabbani.xls.annotation.Col;
import com.rabbani.xls.annotation.Deserialize;
import com.rabbani.xls.annotation.Serialize;
import com.rabbani.xls.annotation.Xls;
import com.rabbani.xls.engine.Deserializer;
import com.rabbani.xls.engine.Serializer;
import com.rabbani.xls.engine.Mapper;
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

import static java.util.Arrays.asList;

@SupportedAnnotationTypes(value = {"com.rabbani.xls.annotation.Xls"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MapperProcessor extends AbstractProcessor {

    private interface MapperFactoryConstants {
        String GET_METHOD = "get";
    }

    private interface MapperConstants {
        String MAPPED_INSTANCE_METHOD = "mappedInstance";
    }

    private static final String REGISTER_MAPPER_FIELD = "REGISTER_MAPPER";

    private static final String CLASS_KIND = "class";

    private static final String INTEFACE_KIND = "interface";

    private static final String PACKAGE = "com.rabbani.xls.engine.impl";

    private static final String DER_SER_METHOD = "convert";

    private static final String SERIALIZER_CLASS = "__Serializer";

    private static final String DESERIALIZER_CLASS = "__Serializer";

    private static final String QUALIFIED_SERIALIZER_CLASS  = PACKAGE+"."+SERIALIZER_CLASS;

    private static final String QUALIFIED_DESERIALIZER_CLASS  = PACKAGE+"."+DESERIALIZER_CLASS;

    private static final String MAPPER_IMPLEMENTATION_PREFIX = "___Mapper";

    private static final String MAPPER_FACTORY_CLASSNAME = "AutoMapperFactory";

    private static final String INSTANCE_FACTORY_FIELD = "instanceFactory";

    private static final String CASE_SENSITIVE_FIELD = "caseSensitive";

    private Map<String, String> typeMaps = new HashMap<>();

    private Map<String, StaticDerSer> serializerRegistry = new HashMap<>();

    private Map<String, StaticDerSer> deserializerRegistry = new HashMap<>();

    private Types types;

    private Filer filer;

    private Elements elements;

    private TypeElement mapElement;

    private TypeElement classElement;

    private TypeElement mapperElement;

    private TypeElement hashMapElement;

    private TypeElement dynamicMapperElement;

    private TypeMirror stringUtilsTypeMirror;

    private TypeMirror stringTypeMirror;

    private TypeMirror wildcardTypeMirror;

    private TypeMirror cellTypeMirror;

    private TypeElement uncheckedConsumerElement;

    private TypeElement columnMapperElement;

    private TypeMirror byteType;

    private TypeMirror shortType;

    private TypeMirror integerType;

    private TypeMirror longType;

    private ExecutableElement serializerMethodElement;

    private ExecutableElement deserializerMethodElement;

    private TypeMirror characterType;

    private TypeMirror floatType;

    private TypeMirror doubleType;

    private TypeMirror booleanType;

    private TypeMirror stringType;

    private TypeElement serializeElement;

    private TypeElement deserializeElement;

    private Messager messager;

    private boolean isMapperWritten = false;

    private static final String CELL_VAR = "cell";

    private static final String VALUE_VAR = "value";

    private static final String CELL_VALUE_STRING_VAR = "cellValue";

    private static final String COLUMN_MAPPERS_REGISTER = "columnMapperRegister";

    private IdentifierUtils globalIdentifier = new IdentifierUtils();


    @Override
    public synchronized boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        types = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        elements = processingEnv.getElementUtils();
        mapElement = elements.getTypeElement(Map.class.getName());
        classElement = elements.getTypeElement(Class.class.getName());
        hashMapElement = elements.getTypeElement(HashMap.class.getName());
        mapperElement = elements.getTypeElement(Mapper.class.getName());
        dynamicMapperElement = elements.getTypeElement(Mapper.class.getName());
        serializeElement = elements.getTypeElement(Serialize.class.getName());
        deserializeElement = elements.getTypeElement(Deserialize.class.getName());
        stringUtilsTypeMirror = elements.getTypeElement(StringUtils.class.getName()).asType();
        uncheckedConsumerElement = elements.getTypeElement(UncheckedConsumer.class.getName());
        wildcardTypeMirror = types.getWildcardType(null, null);
        columnMapperElement = elements.getTypeElement(Mapper.ColumnMapper.class.getCanonicalName());
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
        stringTypeMirror = elements.getTypeElement(String.class.getName()).asType();
        messager = processingEnv.getMessager();
        initDerSerNecessaryProps();

        for (Element element : roundEnv.getElementsAnnotatedWith(Xls.class)) {
            Xls xls = element.getAnnotation(Xls.class);
            boolean isCaseSensitive = xls.caseSensitive();
            TypeElement typeElement = (TypeElement) element;
            dynamicMapperDomainWriter(typeElement, isCaseSensitive);
        }

        if (!isMapperWritten) {
            isMapperWritten = true;
            writeMapperFactory();
            writeSerializerRegistry();
            writeDeserializerRegistry();
        }

        return true;
    }

    private void initDerSerNecessaryProps() {
        TypeElement serializerElement = elements.getTypeElement(Serializer.class.getName());
        for (Element serializerElementMember : serializerElement.getEnclosedElements()) {
            if (serializerElementMember.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement element = (ExecutableElement) serializerElementMember;
            String name = element.getSimpleName().toString();
            if (DER_SER_METHOD.equals(name)) {
                serializerMethodElement = element;
            }
        }

        TypeElement deserializerElement = elements.getTypeElement(Deserializer.class.getName());
        for (Element deserializerElementMember : deserializerElement.getEnclosedElements()) {
            if (deserializerElementMember.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement element = (ExecutableElement) deserializerElementMember;
            String name = element.getSimpleName().toString();
            if (DER_SER_METHOD.equals(name)) {
                deserializerMethodElement = element;
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

            DeclaredType fieldMap = types.getDeclaredType(mapElement, types.getDeclaredType(classElement, wildcardTypeMirror), types.getDeclaredType(mapperElement, wildcardTypeMirror));
            writer.emitField(fieldMap.toString(), REGISTER_MAPPER_FIELD, EnumSet.of(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC));
            writer.emitEmptyLine();

            writer.beginInitializer(true);
            writer.emitStatement("%s = new %s<>()", REGISTER_MAPPER_FIELD, types.getDeclaredType(hashMapElement));
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
            writer.emitStatement("return (%s)%s.get(%s)", types.getDeclaredType(mapperElement, typeVariable).toString(), REGISTER_MAPPER_FIELD, paramName);
            writer.endMethod();
            writer.emitEmptyLine();
            writer.endType();
            writer.close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void dynamicMapperDomainWriter(TypeElement target, boolean isCaseSensitive) {
        try {
            IdentifierUtils identifierUtils = new IdentifierUtils();
            validateAccessibleConstructor(target,Collections.emptyList());
            String packageName = elements.getPackageOf(target).getQualifiedName().toString();
            String className = target.getSimpleName().toString() + MAPPER_IMPLEMENTATION_PREFIX;
            String qualifiedClassName = packageName + "." + className;
            DeclaredType targetTypeMirror = (DeclaredType) target.asType();

            DeclaredType extendedType = types.getDeclaredType(dynamicMapperElement, targetTypeMirror);
            typeMaps.put(target.toString(), qualifiedClassName);

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
            writer.emitStatement("%s = %s::new", INSTANCE_FACTORY_FIELD, target.getQualifiedName().toString());
            writer.emitStatement("%s = %s", CASE_SENSITIVE_FIELD, String.valueOf(isCaseSensitive));
            DeclaredType processFuncType = types.getDeclaredType(uncheckedConsumerElement, cellTypeMirror, targetTypeMirror);
            DeclaredType columnMapperTypeMirror = types.getDeclaredType(columnMapperElement, targetTypeMirror);
            for (Element element : target.getEnclosedElements()) {
                if (!(element instanceof VariableElement)) {
                    continue;
                }

                boolean shouldSkip = element.getModifiers().stream().anyMatch(modifier -> modifier == Modifier.TRANSIENT
                        || modifier == Modifier.PRIVATE
                        || modifier == Modifier.STATIC
                        || modifier == Modifier.FINAL);

                if (shouldSkip) {
                    continue;
                }

                VariableElement field = (VariableElement) element;
                Col col = field.getAnnotation(Col.class);
                String label = (col != null) ? col.value() : field.getSimpleName().toString();

                String serializerIdentifier = processSerializer(field);
                String deserializerIdentifier = processDeserializer(field);
                String serializerVar = writeSerializeImplementation(writer, identifierUtils, field, serializerIdentifier, processFuncType, label);
                String deserializerVar = writeDeserializeImplementation(writer, identifierUtils, field, deserializerIdentifier, processFuncType, label);
                writer.emitStatement("%s.put(\"%s\",new %s(\"%2$s\",%s,%s))", COLUMN_MAPPERS_REGISTER, isCaseSensitive ? label : label.toLowerCase(), columnMapperTypeMirror, serializerVar, deserializerVar);
            }
            writer.endMethod();
            writer.endType();
            writer.close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    private String processSerializer(VariableElement variableElement) throws Exception {
        AnnotationMirror serializer = variableElement.getAnnotationMirrors()
                .stream()
                .filter(annotation -> annotation.getAnnotationType().asElement().equals(serializeElement))
                .findAny()
                .orElse(null);

        if (serializer == null) {
            return null;
        }

        StaticDerSer staticDerSer = new StaticDerSer();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> serEntry : serializer.getElementValues().entrySet()) {
            String iName = serEntry.getKey().getSimpleName().toString();
            if ("param".equals(iName)) {
                staticDerSer.param = serEntry.getValue().getValue().toString();
            } else if ("value".equals(iName)) {
                staticDerSer.type = (DeclaredType) serEntry.getValue().getValue();
            }
        }

        TypeMirror fieldTypeMirror = variableElement.asType();


        Set<Modifier> implementedSerializer = staticDerSer.type.asElement().getModifiers();
        if (!implementedSerializer.contains(Modifier.PUBLIC) || implementedSerializer.contains(Modifier.ABSTRACT)) {
            raiseError(variableElement, staticDerSer.type.toString() + " must be public and non abstract class");
        }

        validateAccessibleConstructor((TypeElement) staticDerSer.type.asElement(),asList(stringTypeMirror));

        TypeMirror serializeParamType = ((ExecutableType) types.asMemberOf(staticDerSer.type, serializerMethodElement)).getParameterTypes().get(0);
        if (!types.isAssignable(fieldTypeMirror, serializeParamType)) {
            raiseError(variableElement, "has serialize of type "+staticDerSer.type.toString() + " type <" + fieldTypeMirror.toString() + "> cannot be assigned to <" + serializeParamType.toString() + ">");
        }

        return serializerRegistry.computeIfAbsent(staticDerSer.type.toString() + "#" + staticDerSer.param, identifier -> {
            staticDerSer.name = globalIdentifier.createName(identifier);
            return staticDerSer;
        }).name;
    }

    private void validateAccessibleConstructor(TypeElement typeElement,List<? extends TypeMirror> requiredParamTypes) {
        if (!elements.getAllMembers(typeElement)
                .stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .anyMatch(executableElement -> {
                    if(executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                            List<? extends VariableElement> constParams = executableElement.getParameters();
                            if(requiredParamTypes.size() != constParams.size()) {
                                return false;
                            }

                            for(int i = 0;i < constParams.size();i++) {
                                TypeMirror paramType = constParams.get(i).asType();
                                if(!types.isAssignable(requiredParamTypes.get(i),paramType)){
                                    return  false;
                                }
                            }
                            return true;
                    }
                    return false;
                })
        ) {
            raiseError(typeElement, requiredParamTypes.isEmpty()?"no-args constructor is required":"required constructor with these type "+requiredParamTypes);
        }
    }

    private String processDeserializer(VariableElement variableElement) throws Exception {
        AnnotationMirror deserializer = variableElement.getAnnotationMirrors()
                .stream()
                .filter(annotation -> annotation.getAnnotationType().asElement().equals(deserializeElement))
                .findAny()
                .orElse(null);

        if (deserializer == null) {
            return null;
        }

        StaticDerSer staticDerSer = new StaticDerSer();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> deserEntry : deserializer.getElementValues().entrySet()) {
            String iName = deserEntry.getKey().getSimpleName().toString();
            if ("param".equals(iName)) {
                staticDerSer.param = deserEntry.getValue().getValue().toString();
            } else if ("value".equals(iName)) {
                staticDerSer.type = (DeclaredType) deserEntry.getValue().getValue();
            }
        }

        TypeMirror fieldTypeMirror = variableElement.asType();

        Set<Modifier> implementedDeserializer = staticDerSer.type.asElement().getModifiers();
        if (!implementedDeserializer.contains(Modifier.PUBLIC) || implementedDeserializer.contains(Modifier.ABSTRACT)) {
            raiseError(variableElement, staticDerSer.type.toString() + " must be public and non abstract class");
        }

        validateAccessibleConstructor((TypeElement) staticDerSer.type.asElement(),asList(stringTypeMirror));

        TypeMirror deserializeParamType = ((ExecutableType) types.asMemberOf(staticDerSer.type, deserializerMethodElement)).getParameterTypes().get(0);
        if (!types.isAssignable(deserializeParamType, fieldTypeMirror)) {
            raiseError(variableElement, "has deserialize of type "+staticDerSer.type.toString() + " type <" + deserializeParamType.toString() + "> cannot be assigned to <" + fieldTypeMirror.toString() + ">");
        }

        return deserializerRegistry.computeIfAbsent(staticDerSer.type.toString() + "#" + staticDerSer.param, identifier -> {
            staticDerSer.name = globalIdentifier.createName(identifier);
            return staticDerSer;
        }).name;
    }

    private void raiseError(VariableElement element, String message) {
        String rootCause = element.getEnclosingElement() + "." + element.getSimpleName();
        messager.printMessage(Diagnostic.Kind.ERROR, "Error at " + rootCause + ": " + message, element);
    }

    private void raiseError(TypeElement element, String message) {
        String rootCause = element.getEnclosingElement() + "." + element.getSimpleName();
        messager.printMessage(Diagnostic.Kind.ERROR, "Error at " + rootCause + ": " + message, element);
    }

    private String writeDeserializeImplementation(JavaWriter writer,
                                                IdentifierUtils identifierUtils,
                                                VariableElement fieldElement,
                                                String deserializerIdentifier,
                                                DeclaredType serializerConsumer,
                                                String label) throws Exception {


        TypeMirror fieldTypeMirror = fieldElement.asType();
        TypeKind fieldTypeKind = fieldTypeMirror.getKind();

        String readerIdentifier = identifierUtils.createName(serializerConsumer.toString() + "Reader");

        String fieldName = fieldElement.getSimpleName().toString();
        writer.beginControlFlow("%s %s = (%s,%s)->", serializerConsumer.toString(), readerIdentifier, CELL_VAR, VALUE_VAR);
        writer.emitStatement("String %s = %s.getStringCellValue()", CELL_VALUE_STRING_VAR, CELL_VAR);
        if (deserializerIdentifier != null) {
            writer.emitStatement("%s.%s = %s.%s.%s(%s)",VALUE_VAR, fieldName,QUALIFIED_DESERIALIZER_CLASS, deserializerIdentifier,DER_SER_METHOD,CELL_VALUE_STRING_VAR);
        } else if (fieldTypeKind == TypeKind.BYTE || fieldTypeMirror.equals(byteType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Byte.parseByte(%s)", VALUE_VAR, fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.SHORT || fieldTypeMirror.equals(shortType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Short.parseShort(%s)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.INT || fieldTypeMirror.equals(integerType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Integer.parseInt(%s)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.LONG || fieldTypeMirror.equals(longType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Long.parseLong(%s)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "0L" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.FLOAT || fieldTypeMirror.equals(floatType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Float.parseFloat(%s)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "0F" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.DOUBLE || fieldTypeMirror.equals(doubleType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Double.parseDouble(%s)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "0D" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.CHAR || fieldTypeMirror.equals(characterType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = %s.charAt(0)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "\u0000" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeKind == TypeKind.BOOLEAN || fieldTypeMirror.equals(booleanType)) {
            writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.%s = Boolean.parseBoolean(%s)", VALUE_VAR,fieldName, CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
            writer.beginControlFlow("else");
            String setValue = fieldTypeKind.isPrimitive() ? "\u0000" : "null";
            writer.emitStatement("%s.%s = %s", VALUE_VAR,fieldName, setValue);
            writer.endControlFlow();
        } else if (fieldTypeMirror.equals(stringType)) {
            writer.emitStatement("%s.%s = !%s.isEmpty(%s)?%s:%s", VALUE_VAR,fieldName, stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR, CELL_VALUE_STRING_VAR, "null");
        }

        writer.endControlFlow("");
        return readerIdentifier;
    }

    private String writeSerializeImplementation(JavaWriter writer,
                                                  IdentifierUtils identifierUtils,
                                                  VariableElement fieldElement,
                                                  String serializerIdentifier,
                                                  DeclaredType serializerConsumer,
                                                  String label) throws Exception {

        TypeMirror fieldTypeMirror = fieldElement.asType();
        TypeKind fieldTypeKind = fieldTypeMirror.getKind();
        String fieldName = fieldElement.getSimpleName().toString();

        String deserializeIdentifier = identifierUtils.createName(serializerConsumer.toString() + "Writer");

        writer.beginControlFlow("%s %s = (%s,%s)->", serializerConsumer.toString(), deserializeIdentifier, CELL_VAR, VALUE_VAR);
        if (serializerIdentifier != null) {
            writer.emitStatement("String %s = %s.%s.%s(%s.%s)", CELL_VALUE_STRING_VAR,QUALIFIED_SERIALIZER_CLASS, serializerIdentifier, DER_SER_METHOD, VALUE_VAR,fieldName);
            writer.beginControlFlow("if(%s != null)",CELL_VALUE_STRING_VAR);
            writer.emitStatement("%s.setCellValue(%s)",CELL_VAR,CELL_VALUE_STRING_VAR);
            writer.endControlFlow();
        } else {
            if (fieldTypeKind == TypeKind.BYTE || fieldTypeMirror.equals(byteType)) {
                writer.emitStatement("Byte %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.SHORT || fieldTypeMirror.equals(shortType)) {
                writer.emitStatement("Short %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.INT || fieldTypeMirror.equals(integerType)) {
                writer.emitStatement("Integer %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.LONG || fieldTypeMirror.equals(longType)) {
                writer.emitStatement("Long %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.FLOAT || fieldTypeMirror.equals(floatType)) {
                writer.emitStatement("Float %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.DOUBLE || fieldTypeMirror.equals(doubleType)) {
                writer.emitStatement("Float %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.CHAR || fieldTypeMirror.equals(characterType)) {
                writer.emitStatement("Character %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(String.valueOf(%s))", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeKind == TypeKind.BOOLEAN || fieldTypeMirror.equals(booleanType)) {
                writer.emitStatement("Boolean %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            } else if (fieldTypeMirror.equals(stringType)) {
                writer.emitStatement("String %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, fieldName);
                writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                writer.endControlFlow();
            }
        }
        writer.endControlFlow("");
        return deserializeIdentifier;
    }


    private void writeSerializerRegistry() {
        if (serializerRegistry.isEmpty()) {
            return;
        }
        JavaWriter codeWriter = null;
        try {
            JavaFileObject source = filer.createSourceFile(QUALIFIED_SERIALIZER_CLASS);
            codeWriter = new JavaWriter(source.openWriter());
            codeWriter.emitPackage(PACKAGE);
            codeWriter.emitEmptyLine();
            codeWriter.beginType(SERIALIZER_CLASS, INTEFACE_KIND, EnumSet.of(Modifier.PUBLIC));
            for (Map.Entry<String, StaticDerSer> entry : serializerRegistry.entrySet()) {
                StaticDerSer derSer = entry.getValue();
                codeWriter.emitField(derSer.type.toString(), derSer.name, EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), String.format("new %s(\"%s\")", derSer.type.toString(), derSer.param));
            }
            codeWriter.endType();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (codeWriter != null) {
                    codeWriter.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void writeDeserializerRegistry() {
        if (deserializerRegistry.isEmpty()) {
            return;
        }
        JavaWriter codeWriter = null;
        try {
            JavaFileObject source = filer.createSourceFile(QUALIFIED_DESERIALIZER_CLASS);
            codeWriter = new JavaWriter(source.openWriter());
            codeWriter.emitPackage(PACKAGE);
            codeWriter.emitEmptyLine();
            codeWriter.beginType(DESERIALIZER_CLASS, INTEFACE_KIND, EnumSet.of(Modifier.PUBLIC));
            for (Map.Entry<String, StaticDerSer> entry : serializerRegistry.entrySet()) {
                StaticDerSer derSer = entry.getValue();
                codeWriter.emitField(derSer.type.toString(), derSer.name, EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), String.format("new %s(\"%s\")", derSer.type.toString(), derSer.param));
            }
            codeWriter.endType();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (codeWriter != null) {
                    codeWriter.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    static class StaticDerSer {
        String name;
        DeclaredType type;
        String param;
    }
}
