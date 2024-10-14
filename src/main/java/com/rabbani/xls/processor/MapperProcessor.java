package com.rabbani.xls.processor;

import com.rabbani.xls.annotation.*;
import com.rabbani.xls.engine.*;
import com.rabbani.xls.util.IdentifierUtils;
import com.rabbani.xls.util.StringUtils;
import com.rabbani.xls.util.UncheckedConsumer;
import com.squareup.javawriter.JavaWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

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
        String COLUMN_MAPPERS_REGISTER = "columnMapperRegister";
        String PRE_SORTED_INSTANCE_FIELD = "PRE_SORTED_INSTANCE_FIELD";
        String ABSTRACT_METHOND_MAPPER = "mapper";

        String INSTANCE_WRITE = "write";
        String INSTANCE_READ = "read";
    }

    private static final String REGISTER_MAPPER_FIELD = "REGISTER_MAPPER";

    private static final String VOID = "void";

    private static final String CLASS_KIND = "class";

    private static final String INTEFACE_KIND = "interface";

    private static final String PACKAGE = "com.rabbani.xls.engine.impl";

    private static final String SERIALIZE_METHOD = "serialize";

    private static final String DESERIALIZE_METHOD = "deserialize";

    private static final String SERIALIZER_CLASS = "__Serializer";

    private static final String DESERIALIZER_CLASS = "__Deserializer";

    private static final String QUALIFIED_SERIALIZER_CLASS = PACKAGE + "." + SERIALIZER_CLASS;

    private static final String QUALIFIED_DESERIALIZER_CLASS = PACKAGE + "." + DESERIALIZER_CLASS;

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

    private TypeElement rowElement;

    private TypeElement errorHandlerElement;

    private TypeMirror stringUtilsTypeMirror;


    private TypeMirror wildcardTypeMirror;

    private TypeMirror cellTypeMirror;

    private TypeElement uncheckedConsumerElement;

    private TypeElement columnMapperElement;

    private TypeElement instanceElement;

    private TypeMirror byteType;

    private TypeMirror shortType;

    private TypeMirror integerType;

    private TypeMirror longType;

    private TypeMirror throwableType;

    private ExecutableElement serializerMethodElement;

    private ExecutableElement deserializerMethodElement;

    private TypeMirror characterType;

    private TypeMirror floatType;

    private TypeMirror doubleType;

    private TypeMirror booleanType;

    private TypeMirror stringType;

    private TypeMirror[] allowedTypes;

    private TypeElement serializeElement;

    private TypeElement deserializeElement;

    private Messager messager;

    private boolean isMapperWritten = false;

    private static final String CELL_VAR = "cell";

    private static final String VALUE_VAR = "value";

    private static final String CELL_VALUE_STRING_VAR = "cellValue";

    private IdentifierUtils globalIdentifier = new IdentifierUtils();


    @Override
    public synchronized boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        types = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        elements = processingEnv.getElementUtils();
        mapElement = elements.getTypeElement(Map.class.getName());
        classElement = elements.getTypeElement(Class.class.getName());
        hashMapElement = elements.getTypeElement(HashMap.class.getName());
        instanceElement = elements.getTypeElement(Mapper.Instance.class.getCanonicalName());
        mapperElement = elements.getTypeElement(Mapper.class.getName());
        serializeElement = elements.getTypeElement(Serialize.class.getName());
        deserializeElement = elements.getTypeElement(Deserialize.class.getName());
        stringUtilsTypeMirror = elements.getTypeElement(StringUtils.class.getName()).asType();
        uncheckedConsumerElement = elements.getTypeElement(UncheckedConsumer.class.getName());
        wildcardTypeMirror = types.getWildcardType(null, null);
        columnMapperElement = elements.getTypeElement(Mapper.ColumnMapper.class.getCanonicalName());
        cellTypeMirror = elements.getTypeElement(Cell.class.getName()).asType();
        throwableType = elements.getTypeElement(Throwable.class.getName()).asType();
        byteType = elements.getTypeElement(Byte.class.getName()).asType();
        shortType = elements.getTypeElement(Short.class.getName()).asType();
        integerType = elements.getTypeElement(Integer.class.getName()).asType();
        longType = elements.getTypeElement(Long.class.getName()).asType();
        floatType = elements.getTypeElement(Float.class.getName()).asType();
        doubleType = elements.getTypeElement(Double.class.getName()).asType();
        characterType = elements.getTypeElement(Character.class.getName()).asType();
        booleanType = elements.getTypeElement(Boolean.class.getName()).asType();
        stringType = elements.getTypeElement(String.class.getName()).asType();
        rowElement = elements.getTypeElement(Row.class.getName());
        errorHandlerElement = elements.getTypeElement(ErrorHandler.class.getName());
        messager = processingEnv.getMessager();
        allowedTypes = new TypeMirror[]{byteType, shortType, integerType, floatType, doubleType, characterType, booleanType, stringType};
        initDerSerNecessaryProps();

        for (Element domainElement : roundEnv.getElementsAnnotatedWith(Xls.class)) {
            writeMapperDomain((TypeElement) domainElement);
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
            if (SERIALIZE_METHOD.equals(name)) {
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
            if (DESERIALIZE_METHOD.equals(name)) {
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

    private void writeMapperDomain(TypeElement target) {
        try {

            DeclaredType targetTypeMirror = (DeclaredType) target.asType();
            Xls xls = target.getAnnotation(Xls.class);
            boolean isCaseSensitive = xls.caseSensitive();

            MapperScheme mapperScheme = new MapperScheme();
            mapperScheme.caseSensitive = isCaseSensitive;
            mapperScheme.properties = new HashMap<>();
            mapperScheme.type = targetTypeMirror;
            mapperScheme.values = xls.columns();


            for (Element element : target.getEnclosedElements()) {
                if (!(element instanceof VariableElement)) {
                    continue;
                }
                VariableElement field = (VariableElement) element;

                boolean shouldSkip = element.getModifiers().stream().anyMatch(modifier -> modifier == Modifier.TRANSIENT
                        || modifier == Modifier.PRIVATE
                        || modifier == Modifier.STATIC
                        || modifier == Modifier.FINAL) || field.getAnnotation(Transient.class) != null;

                if (shouldSkip) {
                    continue;
                }


                Col col = field.getAnnotation(Col.class);
                String fieldName = field.getSimpleName().toString();
                String label = (col != null) ? col.value() : fieldName;
                TypeMirror fieldType = field.asType();

                String serializerIdentifier = processSerializer(field);
                String deserializerIdentifier = processDeserializer(field);
                boolean isAccepted = acceptedType(fieldType);
                if (!isAccepted && serializerIdentifier == null) {
                    raiseError(field, "Unsupported field type, please provide serializer for this field");
                }

                if (!isAccepted && deserializerIdentifier == null) {
                    raiseError(field, "Unsupported field type, please provide deserializer for this field");
                }

                MapperScheme.ColumnScheme columnScheme = new MapperScheme.ColumnScheme();
                columnScheme.label = label;
                columnScheme.type = fieldType;
                columnScheme.name = fieldName;
                columnScheme.canonicalPath = targetTypeMirror + "." + fieldName;
                columnScheme.serializerIdentifier = serializerIdentifier;
                columnScheme.deserializerIdentifier = deserializerIdentifier;
                mapperScheme.properties.put(isCaseSensitive ? label : label.toLowerCase(), columnScheme);
            }

            IdentifierUtils identifierUtils = new IdentifierUtils();
            validateAccessibleConstructor(target, Collections.emptyList());
            String packageName = elements.getPackageOf(target).getQualifiedName().toString();
            String className = target.getSimpleName().toString() + MAPPER_IMPLEMENTATION_PREFIX;
            String qualifiedClassName = packageName + "." + className;
            DeclaredType extendedType = types.getDeclaredType(mapperElement, targetTypeMirror);
            typeMaps.put(target.toString(), qualifiedClassName);

            JavaFileObject source = filer.createSourceFile(qualifiedClassName);
            JavaWriter writer = new JavaWriter(source.openWriter());
            writer.emitPackage(packageName);
            writer.beginType(className, CLASS_KIND, EnumSet.of(Modifier.PUBLIC), extendedType.toString());
            writer.emitEmptyLine();
            writeStaticInitializationOfSortedMapper(writer, mapperScheme, identifierUtils);
            writer.emitEmptyLine();
            writer.beginConstructor(EnumSet.of(Modifier.PUBLIC));
            writer.emitStatement("super()");
            writer.emitStatement("init()");
            writer.endConstructor();
            writer.emitEmptyLine();


            writeInitImplementation(writer, identifierUtils, mapperScheme);
            writeAbstractMapperImplementation(writer, mapperScheme);

            writer.endType();
            writer.close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    private void writeAbstractMapperImplementation(JavaWriter writer, MapperScheme mapperScheme) throws Exception {
        TypeMirror returnType = types.getDeclaredType(instanceElement, mapperScheme.type);
        writer.emitAnnotation(Override.class);
        writer.beginMethod(returnType.toString(), MapperConstants.ABSTRACT_METHOND_MAPPER, EnumSet.of(Modifier.PUBLIC));
        writer.emitStatement("return %s", MapperConstants.PRE_SORTED_INSTANCE_FIELD);
        writer.endMethod();
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

        validateAccessibleConstructor((TypeElement) staticDerSer.type.asElement(), asList(stringType));

        TypeMirror serializeParamType = ((ExecutableType) types.asMemberOf(staticDerSer.type, serializerMethodElement)).getParameterTypes().get(0);
        if (!types.isAssignable(fieldTypeMirror, serializeParamType)) {
            raiseError(variableElement, "has serialize of type " + staticDerSer.type.toString() + " type <" + fieldTypeMirror.toString() + "> cannot be assigned to <" + serializeParamType.toString() + ">");
        }

        return serializerRegistry.computeIfAbsent(staticDerSer.type.toString() + "#" + staticDerSer.param, identifier -> {
            staticDerSer.name = globalIdentifier.createName(identifier);
            return staticDerSer;
        }).name;
    }

    private void validateAccessibleConstructor(TypeElement typeElement, List<? extends TypeMirror> requiredParamTypes) {
        if (!elements.getAllMembers(typeElement)
                .stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .anyMatch(executableElement -> {
                    if (executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                        List<? extends VariableElement> constParams = executableElement.getParameters();
                        if (requiredParamTypes.size() != constParams.size()) {
                            return false;
                        }

                        for (int i = 0; i < constParams.size(); i++) {
                            TypeMirror paramType = constParams.get(i).asType();
                            if (!types.isAssignable(requiredParamTypes.get(i), paramType)) {
                                return false;
                            }
                        }
                        return true;
                    }
                    return false;
                })
        ) {
            raiseError(typeElement, requiredParamTypes.isEmpty() ? "no-args constructor is required" : "required constructor with these type " + requiredParamTypes);
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

        validateAccessibleConstructor((TypeElement) staticDerSer.type.asElement(), asList(stringType));

        TypeMirror deserializeParamType = ((ExecutableType) types.asMemberOf(staticDerSer.type, deserializerMethodElement)).getReturnType();
        if (!types.isAssignable(deserializeParamType, fieldTypeMirror)) {
            raiseError(variableElement, "has deserialize of type " + staticDerSer.type.toString() + " type <" + deserializeParamType.toString() + "> cannot be assigned to <" + fieldTypeMirror.toString() + ">");
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

    private void writeInitImplementation(JavaWriter writer,
                                         IdentifierUtils identifierUtils,
                                         MapperScheme mapperScheme) throws Exception {

        writer.beginMethod("void", "init", EnumSet.of(Modifier.PRIVATE));
        writer.emitStatement("%s = %s::new", INSTANCE_FACTORY_FIELD, mapperScheme.type);
        writer.emitStatement("%s = %s", CASE_SENSITIVE_FIELD, String.valueOf(mapperScheme.caseSensitive));
        TypeMirror fieldApplierType = types.getDeclaredType(uncheckedConsumerElement, cellTypeMirror, mapperScheme.type);
        DeclaredType mapperApplierType = types.getDeclaredType(columnMapperElement, mapperScheme.type);
        for (Map.Entry<String, MapperScheme.ColumnScheme> columnSchemeEntry : mapperScheme.properties.entrySet()) {
            MapperScheme.ColumnScheme field = columnSchemeEntry.getValue();
            TypeMirror type = field.type;
            String label = field.label;
            String name = field.name;
            String serializerIdentifier = field.serializerIdentifier;
            String deserializerIdentifier = field.deserializerIdentifier;

            TypeKind fieldTypeKind = type.getKind();

            String writerIdentifier = identifierUtils.createName(fieldApplierType + "Writer");
            writer.beginControlFlow("%s %s = (%s,%s)->", fieldApplierType, writerIdentifier, CELL_VAR, VALUE_VAR);
            if (serializerIdentifier != null) {
                writer.emitStatement("%s.%s.%s(%s.%s,%s)", QUALIFIED_SERIALIZER_CLASS, serializerIdentifier, SERIALIZE_METHOD, VALUE_VAR, name, CELL_VAR);
            } else {
                if (fieldTypeKind == TypeKind.BYTE || type.equals(byteType)) {
                    writer.emitStatement("Byte %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.SHORT || type.equals(shortType)) {
                    writer.emitStatement("Short %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.INT || type.equals(integerType)) {
                    writer.emitStatement("Integer %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.LONG || type.equals(longType)) {
                    writer.emitStatement("Long %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.FLOAT || type.equals(floatType)) {
                    writer.emitStatement("Float %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.DOUBLE || type.equals(doubleType)) {
                    writer.emitStatement("Float %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.CHAR || type.equals(characterType)) {
                    writer.emitStatement("Character %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(String.valueOf(%s))", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.BOOLEAN || type.equals(booleanType)) {
                    writer.emitStatement("Boolean %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                } else if (type.equals(stringType)) {
                    writer.emitStatement("String %s = %s.%s", CELL_VALUE_STRING_VAR, VALUE_VAR, name);
                    writer.beginControlFlow("if(%s != null)", CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.setCellValue(%s)", CELL_VAR, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                }
            }
            writer.endControlFlow("");

            String readerIdentifier = identifierUtils.createName(fieldApplierType + "Reader");
            writer.beginControlFlow("%s %s = (%s,%s)->", fieldApplierType, readerIdentifier, CELL_VAR, VALUE_VAR);

            if (deserializerIdentifier != null) {
                writer.emitStatement("%s.%s = %s.%s.%s(%s)", VALUE_VAR, name, QUALIFIED_DESERIALIZER_CLASS, deserializerIdentifier, DESERIALIZE_METHOD, CELL_VAR);
            } else {
                writer.emitStatement("String %s = %s.getStringCellValue()", CELL_VALUE_STRING_VAR, CELL_VAR);
                if (fieldTypeKind == TypeKind.BYTE || type.equals(byteType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Byte.parseByte(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.SHORT || type.equals(shortType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Short.parseShort(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.INT || type.equals(integerType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Integer.parseInt(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "0" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.LONG || type.equals(longType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Long.parseLong(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "0L" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.FLOAT || type.equals(floatType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Float.parseFloat(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "0F" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.DOUBLE || type.equals(doubleType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Double.parseDouble(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "0D" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.CHAR || type.equals(characterType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = %s.charAt(0)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "\u0000" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (fieldTypeKind == TypeKind.BOOLEAN || type.equals(booleanType)) {
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR);
                    writer.emitStatement("%s.%s = Boolean.parseBoolean(%s)", VALUE_VAR, name, CELL_VALUE_STRING_VAR);
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    String setValue = fieldTypeKind.isPrimitive() ? "false" : "null";
                    writer.emitStatement("%s.%s = %s", VALUE_VAR, name, setValue);
                    writer.endControlFlow();
                } else if (type.equals(stringType)) {
                    writer.emitStatement("%s.%s = !%s.isEmpty(%s)?%s:%s", VALUE_VAR, name, stringUtilsTypeMirror.toString(), CELL_VALUE_STRING_VAR, CELL_VALUE_STRING_VAR, "null");
                }
            }

            writer.endControlFlow("");
            writer.emitStatement("%s.put(\"%s\",new %s(\"%2$s\",%s,%s))", MapperConstants.COLUMN_MAPPERS_REGISTER, mapperScheme.caseSensitive ? label : label.toLowerCase(), mapperApplierType, readerIdentifier, writerIdentifier);
        }
        writer.endMethod();
    }


    private void writeStaticInitializationOfSortedMapper(JavaWriter writer, MapperScheme mapperScheme, IdentifierUtils identifierUtils) throws Exception {
        TypeMirror instanceType = types.getDeclaredType(instanceElement, mapperScheme.type);

        String rowIdentifier = identifierUtils.createName(rowElement.toString());
        String targetIdentifier = identifierUtils.createName(mapperScheme.type.toString());
        String errorHandlerIdentifier = identifierUtils.createName(errorHandlerElement.toString());

        writer.emitField(instanceType.toString(), MapperConstants.PRE_SORTED_INSTANCE_FIELD, EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL));
        writer.emitEmptyLine();
        writer.beginInitializer(true);
        writer.beginControlFlow("%s = new %s()", MapperConstants.PRE_SORTED_INSTANCE_FIELD, instanceType);

        writer.emitEmptyLine();
        writer.emitAnnotation(Override.class);
        writer.beginMethod(VOID, MapperConstants.INSTANCE_WRITE, EnumSet.of(Modifier.PUBLIC), rowElement.toString(), rowIdentifier, mapperScheme.type.toString(), targetIdentifier, errorHandlerElement.toString(), errorHandlerIdentifier);
        Collection<String> columns = Arrays.asList(mapperScheme.values);
        if (columns.isEmpty()) {
            columns = mapperScheme.properties.keySet();
        }

        int i = 0;
        for (String column : columns) {
            MapperScheme.ColumnScheme columnScheme = mapperScheme.properties.get(mapperScheme.caseSensitive ? column : column.toLowerCase());
            if (columnScheme != null) {
                String serializerIdentifier = columnScheme.serializerIdentifier;
                boolean isPrimitiveType = columnScheme.type.getKind().isPrimitive();
                String name = columnScheme.name;
                String cellIdentifier = identifierUtils.createName(cellTypeMirror.toString());
                String throwIdenfier = identifierUtils.createName(throwableType.toString());
                writer.emitStatement("%s %s = null", cellTypeMirror, cellIdentifier);
                writer.beginControlFlow("try");
                if (columnScheme.serializerIdentifier != null) {
                    writer.emitStatement("%s = %s.createCell(%d)", cellIdentifier, rowIdentifier, i);
                    writer.emitStatement("%s.%s.%s(%s.%s,%s)", QUALIFIED_SERIALIZER_CLASS, serializerIdentifier, SERIALIZE_METHOD, targetIdentifier, name, cellIdentifier);
                } else {
                    if (!isPrimitiveType) {
                        writer.beginControlFlow("if(%s.%s != null)", targetIdentifier, name);
                    }
                    writer.emitStatement("%s = %s.createCell(%d)", cellIdentifier, rowIdentifier, i);
                    writer.emitStatement("%s.setCellValue(%s.%s)", cellIdentifier, targetIdentifier, name);
                    if (!isPrimitiveType) {
                        writer.endControlFlow();
                    }
                }
                writer.endControlFlow();
                writer.beginControlFlow("catch(%s %s)", throwableType, throwIdenfier);
                writer.beginControlFlow("if(%s != null)", errorHandlerIdentifier);
                writer.emitStatement("%s.handle(\"%s\",%s,%s)", errorHandlerIdentifier, column, cellIdentifier, throwIdenfier);
                writer.endControlFlow();
                writer.endControlFlow();
                writer.emitEmptyLine();
                writer.emitEmptyLine();
            }
            i++;
        }

        writer.endMethod();
        writer.emitEmptyLine();
        writer.emitAnnotation(Override.class);
        writer.beginMethod(mapperScheme.type.toString(), MapperConstants.INSTANCE_READ, EnumSet.of(Modifier.PUBLIC), rowElement.toString(), rowIdentifier, errorHandlerElement.toString(), errorHandlerIdentifier);
        i = 0;
        String instanceIdentifier = identifierUtils.createName(mapperScheme.type.toString());
        writer.emitStatement("%s %s = new %1$s()", mapperScheme.type, instanceIdentifier);
        for (String column : columns) {
            MapperScheme.ColumnScheme columnScheme = mapperScheme.properties.get(mapperScheme.caseSensitive ? column : column.toLowerCase());
            if (columnScheme != null) {
                String deserializerIdentifier = columnScheme.deserializerIdentifier;
                TypeMirror type = columnScheme.type;
                TypeKind typeKind = type.getKind();
                String name = columnScheme.name;
                String cellIdentifier = identifierUtils.createName(cellTypeMirror.toString());
                String throwIdenfier = identifierUtils.createName(throwableType.toString());
                writer.emitStatement("%s %s = %s.getCell(%d)", cellTypeMirror, cellIdentifier, rowIdentifier, i);
                writer.beginControlFlow("try");

                if (deserializerIdentifier != null) {
                    writer.emitStatement("%s.%s = %s.%s.%s(%s)", instanceIdentifier, name, QUALIFIED_DESERIALIZER_CLASS, deserializerIdentifier, DESERIALIZE_METHOD, cellIdentifier);
                } else {
                    String cellRawValueIdentifier = identifierUtils.createName(stringType.toString());
                    writer.emitStatement("%s %s = %s.getStringCellValue()", stringType, cellRawValueIdentifier, cellIdentifier);
                    writer.beginControlFlow("if(!%s.isEmpty(%s))", stringUtilsTypeMirror, cellRawValueIdentifier);
                    String fallbackValue = null;
                    if (typeKind == TypeKind.BYTE || type.equals(byteType)) {
                        writer.emitStatement("%s.%s = Byte.parseByte(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "0" : "null";
                    } else if (typeKind == TypeKind.SHORT || type.equals(shortType)) {
                        writer.emitStatement("%s.%s = Short.parseShort(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "0" : "null";
                    } else if (typeKind == TypeKind.INT || type.equals(integerType)) {
                        writer.emitStatement("%s.%s = Integer.parseInt(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "0" : "null";
                    } else if (typeKind == TypeKind.LONG || type.equals(longType)) {
                        writer.emitStatement("%s.%s = Long.parseLong(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "0L" : "null";
                    } else if (typeKind == TypeKind.FLOAT || type.equals(floatType)) {
                        writer.emitStatement("%s.%s = Float.parseFloat(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "0F" : "null";
                    } else if (typeKind == TypeKind.DOUBLE || type.equals(doubleType)) {
                        writer.emitStatement("%s.%s = Double.parseDouble(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "0D" : "null";
                    } else if (typeKind == TypeKind.CHAR || type.equals(characterType)) {
                        writer.emitStatement("%s.%s = %s.charAt(0)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "\u0000" : "null";
                    } else if (typeKind == TypeKind.BOOLEAN || type.equals(booleanType)) {
                        writer.emitStatement("%s.%s = Boolean.parseBoolean(%s)", instanceIdentifier, name, cellRawValueIdentifier);
                        fallbackValue = typeKind.isPrimitive() ? "false" : "null";
                    } else if (type.equals(stringType)) {
                        writer.emitStatement("%s.%s = %s", instanceIdentifier, name, cellRawValueIdentifier);
                    }
                    writer.endControlFlow();
                    writer.beginControlFlow("else");
                    writer.emitStatement("%s.%s = %s", instanceIdentifier, name, fallbackValue);
                    writer.endControlFlow();
                }
                writer.endControlFlow();
                writer.beginControlFlow("catch(%s %s)", throwableType, throwIdenfier);
                writer.beginControlFlow("if(%s != null)", errorHandlerIdentifier);
                writer.emitStatement("%s.handle(\"%s\",%s,%s)", errorHandlerIdentifier, column, cellIdentifier, throwIdenfier);
                writer.endControlFlow();
                writer.endControlFlow();

                writer.emitEmptyLine();
                writer.emitEmptyLine();
            }
            i++;
        }
        writer.emitStatement("return %s", instanceIdentifier);
        writer.endMethod();
        writer.emitEmptyLine();
        writer.endControlFlow("");
        writer.endInitializer();
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
            for (Map.Entry<String, StaticDerSer> entry : deserializerRegistry.entrySet()) {
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

    private boolean acceptedType(TypeMirror checked) {
        if (checked.getKind().isPrimitive()) {
            return true;
        }

        for (TypeMirror allowedType : allowedTypes) {
            if (allowedType.equals(checked)) {
                return true;
            }
        }

        return false;
    }
}
