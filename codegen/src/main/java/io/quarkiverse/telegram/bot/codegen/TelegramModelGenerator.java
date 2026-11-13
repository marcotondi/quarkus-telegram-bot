package io.quarkiverse.telegram.bot.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Modifier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

public class TelegramModelGenerator {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "default", "class", "public", "private", "protected", "new", "return", "switch", "case",
            "for", "while", "do", "import", "package", "native", "super", "this", "enum", "assert",
            "finally", "throw", "throws", "try", "catch", "void", "final", "abstract", "static",
            "interface", "extends", "implements", "byte", "short", "int", "long", "float", "double",
            "char", "boolean");

    private static final Set<String> SKIP_SCHEMAS = Set.of("InputFile");

    private static final Set<String> KEYBOARD_MARKUP_SCHEMAS = Set.of(
            "InlineKeyboardMarkup", "ReplyKeyboardMarkup", "ReplyKeyboardRemove", "ForceReply");

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: TelegramModelGenerator <schemaPath> <outputDir> <targetPackage>");
            System.exit(1);
        }

        Path schemaPath = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        String targetPackage = args[2];

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(schemaPath.toFile());
        JsonNode schemas = root.path("components").path("schemas");

        Map<String, List<String>> sealedPermits = collectSealedPermits(schemas);

        var fields = schemas.fields();
        int generatedCount = 0;
        while (fields.hasNext()) {
            var entry = fields.next();
            String schemaName = entry.getKey();
            JsonNode schema = entry.getValue();

            if (SKIP_SCHEMAS.contains(schemaName)) {
                continue;
            }

            boolean isPolymorphic = schema.has("oneOf") || schema.has("anyOf");

            TypeSpec typeSpec = isPolymorphic
                    ? buildSealedInterface(schemaName, schema, schemas, targetPackage)
                    : buildRecord(schemaName, schema, sealedPermits.getOrDefault(schemaName, List.of()), targetPackage);

            JavaFile.builder(targetPackage, typeSpec)
                    .skipJavaLangImports(true)
                    .indent("    ")
                    .build()
                    .writeTo(outputDir);

            generatedCount++;
        }

        System.out.println("TelegramModelGenerator successfully generated " + generatedCount + " types in " + outputDir);
    }

    private static TypeSpec buildRecord(
            String name,
            JsonNode schema,
            List<String> superInterfaces,
            String pkg) {

        Set<String> requiredFields = new HashSet<>();
        if (schema.has("required")) {
            schema.path("required").forEach(n -> requiredFields.add(n.asText()));
        }

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
        List<ParameterSpec> paramsList = new ArrayList<>();
        JsonNode props = schema.path("properties");

        if (props.isObject()) {
            var propFields = props.fields();
            while (propFields.hasNext()) {
                var prop = propFields.next();
                String jsonName = prop.getKey();
                JsonNode propSchema = prop.getValue();
                boolean isRequired = requiredFields.contains(jsonName);

                TypeName fieldType = resolveType(propSchema, isRequired, pkg, jsonName);
                String javaFieldName = toCamelCaseSafe(jsonName);

                ParameterSpec param = ParameterSpec.builder(fieldType, javaFieldName)
                        .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                                .addMember("value", "$S", jsonName)
                                .build())
                        .build();

                constructor.addParameter(param);
                paramsList.add(param);
            }
        }

        TypeSpec.Builder recordBuilder = TypeSpec.recordBuilder(name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(JsonIgnoreProperties.class)
                        .addMember("ignoreUnknown", "$L", true)
                        .build())
                .addAnnotation(AnnotationSpec.builder(JsonInclude.class)
                        .addMember("value", "$T.NON_NULL", JsonInclude.Include.class)
                        .build())
                .recordConstructor(constructor.build());

        // JavaDoc description if available
        if (schema.has("description")) {
            recordBuilder.addJavadoc("$L\n", schema.get("description").asText().replace("$", "$$"));
        }

        for (String iface : superInterfaces) {
            recordBuilder.addSuperinterface(ClassName.get(pkg, iface));
        }

        if (KEYBOARD_MARKUP_SCHEMAS.contains(name)) {
            recordBuilder.addSuperinterface(ClassName.get(pkg, "ReplyMarkup"));
        }

        // Static builder() method and Builder class
        recordBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(pkg, name, "Builder"))
                .addStatement("return new Builder()")
                .build());

        TypeSpec.Builder innerBuilder = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(pkg, name));

        StringBuilder returnArgs = new StringBuilder();

        for (ParameterSpec p : paramsList) {
            innerBuilder.addField(p.type(), p.name(), Modifier.PRIVATE);

            MethodSpec setter = MethodSpec.methodBuilder(p.name())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get(pkg, name, "Builder"))
                    .addParameter(p.type(), p.name())
                    .addStatement("this.$L = $L", p.name(), p.name())
                    .addStatement("return this")
                    .build();
            innerBuilder.addMethod(setter);

            if (returnArgs.length() > 0) {
                returnArgs.append(", ");
            }
            returnArgs.append(p.name());
        }

        buildMethod.addStatement("return new $T(" + returnArgs + ")", ClassName.get(pkg, name));
        innerBuilder.addMethod(buildMethod.build());

        recordBuilder.addType(innerBuilder.build());

        return recordBuilder.build();
    }

    private record DiscriminatorInfo(String propertyName, Map<String, String> subtypeToValue) {
    }

    private static final Pattern DISCRIMINATOR_PATTERN = Pattern
            .compile("(?:always|must be)\\s+[“\"*]([^”\"*]+)[”\"*]");

    private static DiscriminatorInfo findDiscriminator(JsonNode variants, JsonNode allSchemas) {
        String[] candidates = { "status", "source", "type" };
        for (String cand : candidates) {
            Map<String, String> values = new HashMap<>();
            boolean valid = true;
            for (JsonNode sub : variants) {
                if (!sub.has("$ref")) {
                    valid = false;
                    break;
                }
                String ref = sub.path("$ref").asText();
                String subName = ref.substring(ref.lastIndexOf('/') + 1);
                JsonNode subSchema = allSchemas.path(subName);
                JsonNode prop = subSchema.path("properties").path(cand);
                if (prop.isMissingNode()) {
                    valid = false;
                    break;
                }
                String val = extractDiscriminatorValue(prop);
                if (val == null || values.containsValue(val)) {
                    valid = false;
                    break;
                }
                values.put(subName, val);
            }
            if (valid && !values.isEmpty()) {
                return new DiscriminatorInfo(cand, values);
            }
        }
        return null;
    }

    private static String extractDiscriminatorValue(JsonNode prop) {
        String desc = prop.path("description").asText("");
        Matcher m = DISCRIMINATOR_PATTERN.matcher(desc);
        if (m.find()) {
            return m.group(1).replace("\\_", "_");
        }
        if (prop.has("enum") && prop.path("enum").size() == 1) {
            return prop.path("enum").get(0).asText();
        }
        return null;
    }

    private static TypeSpec buildSealedInterface(String name, JsonNode schema, JsonNode allSchemas, String pkg) {
        TypeSpec.Builder ifaceBuilder = TypeSpec.interfaceBuilder(name)
                .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

        if (schema.has("description")) {
            ifaceBuilder.addJavadoc("$L\n", schema.get("description").asText().replace("$", "$$"));
        }

        JsonNode variants = schema.has("oneOf") ? schema.path("oneOf") : schema.path("anyOf");
        DiscriminatorInfo discr = findDiscriminator(variants, allSchemas);

        if ("MaybeInaccessibleMessage".equals(name)) {
            ifaceBuilder.addAnnotation(AnnotationSpec.builder(JsonTypeInfo.class)
                    .addMember("use", "$T.Id.DEDUCTION", JsonTypeInfo.class)
                    .addMember("defaultImpl", "$T.class", ClassName.get(pkg, "Message"))
                    .build());
        } else if (discr != null) {
            ifaceBuilder.addAnnotation(AnnotationSpec.builder(JsonTypeInfo.class)
                    .addMember("use", "$T.Id.NAME", JsonTypeInfo.class)
                    .addMember("include", "$T.As.EXISTING_PROPERTY", JsonTypeInfo.class)
                    .addMember("property", "$S", discr.propertyName())
                    .addMember("visible", "$L", true)
                    .build());
        } else {
            ifaceBuilder.addAnnotation(AnnotationSpec.builder(JsonTypeInfo.class)
                    .addMember("use", "$T.Id.DEDUCTION", JsonTypeInfo.class)
                    .build());
        }

        AnnotationSpec.Builder subTypesBuilder = AnnotationSpec.builder(JsonSubTypes.class);

        for (JsonNode sub : variants) {
            if (sub.has("$ref")) {
                String ref = sub.path("$ref").asText();
                String subName = ref.substring(ref.lastIndexOf('/') + 1);
                ClassName subClassName = ClassName.get(pkg, subName);
                ifaceBuilder.addPermittedSubclass(subClassName);

                AnnotationSpec.Builder typeAnnotation = AnnotationSpec.builder(JsonSubTypes.Type.class)
                        .addMember("value", "$T.class", subClassName);
                if (discr != null) {
                    typeAnnotation.addMember("name", "$S", discr.subtypeToValue().get(subName));
                }
                subTypesBuilder.addMember("value", "$L", typeAnnotation.build());
            }
        }

        ifaceBuilder.addAnnotation(subTypesBuilder.build());

        return ifaceBuilder.build();
    }

    private static TypeName resolveType(JsonNode prop, boolean required, String pkg, String propName) {
        if (prop.has("$ref")) {
            String ref = prop.get("$ref").asText();
            String refName = ref.substring(ref.lastIndexOf('/') + 1);
            if ("InputFile".equals(refName)) {
                return ClassName.get("io.quarkiverse.telegram.bot.runtime.model", "InputFile");
            }
            return ClassName.get(pkg, refName);
        }

        if (prop.has("anyOf") || prop.has("oneOf")) {
            return ClassName.get(Object.class);
        }

        String type = prop.path("type").asText();
        return switch (type) {
            case "integer" -> {
                boolean is64 = "int64".equals(prop.path("format").asText())
                        || (propName != null && (propName.equals("id") || propName.endsWith("_id") || propName.equals("date")
                                || propName.endsWith("_date")))
                        || prop.path("description").asText().contains("64-bit")
                        || prop.path("description").asText().contains("52 significant bits");
                yield is64
                        ? (required ? TypeName.LONG : TypeName.LONG.box())
                        : (required ? TypeName.INT : TypeName.INT.box());
            }
            case "number" -> required ? TypeName.DOUBLE : TypeName.DOUBLE.box();
            case "boolean" -> required ? TypeName.BOOLEAN : TypeName.BOOLEAN.box();
            case "string" -> ClassName.get(String.class);
            case "array" -> ParameterizedTypeName.get(
                    ClassName.get(List.class),
                    resolveType(prop.path("items"), false, pkg, null));
            default -> ClassName.get(Object.class);
        };
    }

    private static Map<String, List<String>> collectSealedPermits(JsonNode schemas) {
        Map<String, List<String>> childToParents = new HashMap<>();
        var it = schemas.fields();
        while (it.hasNext()) {
            var entry = it.next();
            JsonNode val = entry.getValue();
            JsonNode variants = val.has("oneOf") ? val.path("oneOf") : (val.has("anyOf") ? val.path("anyOf") : null);
            if (variants != null) {
                String parent = entry.getKey();
                for (JsonNode sub : variants) {
                    if (sub.has("$ref")) {
                        String ref = sub.path("$ref").asText();
                        String child = ref.substring(ref.lastIndexOf('/') + 1);
                        childToParents.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
                    }
                }
            }
        }
        return childToParents;
    }

    private static String toCamelCaseSafe(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        String candidate = sb.toString();
        return JAVA_KEYWORDS.contains(candidate) ? candidate + "Value" : candidate;
    }
}
