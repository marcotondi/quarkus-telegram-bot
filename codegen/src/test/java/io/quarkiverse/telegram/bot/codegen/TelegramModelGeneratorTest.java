package io.quarkiverse.telegram.bot.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TelegramModelGeneratorTest {

    @Test
    void testGenerateModelsFromSchema(@TempDir Path tempDir) throws Exception {
        String schemaJson = """
                {
                  "components": {
                    "schemas": {
                      "User": {
                        "type": "object",
                        "description": "Telegram user representation",
                        "required": ["id", "is_bot", "first_name"],
                        "properties": {
                          "id": { "type": "integer", "format": "int64", "description": "User id" },
                          "is_bot": { "type": "boolean" },
                          "first_name": { "type": "string" },
                          "username": { "type": "string" },
                          "default": { "type": "boolean", "description": "Keyword collision test" }
                        }
                      },
                      "SimpleSealed": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "type",
                          "mapping": {
                            "v1": "#/components/schemas/Sub1",
                            "v2": "#/components/schemas/Sub2"
                          }
                        },
                        "oneOf": [
                          { "$ref": "#/components/schemas/Sub1" },
                          { "$ref": "#/components/schemas/Sub2" }
                        ]
                      },
                      "Sub1": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "name": { "type": "string" }
                        }
                      },
                      "Sub2": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "count": { "type": "integer" }
                        }
                      }
                    }
                  }
                }
                """;

        Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, schemaJson);

        Path outDir = tempDir.resolve("generated");

        TelegramModelGenerator.main(new String[] {
                schemaFile.toAbsolutePath().toString(),
                outDir.toAbsolutePath().toString(),
                "test.pkg"
        });

        Path userJava = outDir.resolve("test/pkg/User.java");
        assertTrue(Files.exists(userJava), "User.java should be generated");

        String userContent = Files.readString(userJava);
        assertTrue(userContent.contains("public record User("), "Should generate record User");
        assertTrue(userContent.contains("@JsonProperty(\"id\") long id"), "Should generate long id");
        assertTrue(userContent.contains("@JsonProperty(\"is_bot\") boolean isBot"),
                "Should map snake_case is_bot to camelCase isBot");
        assertTrue(userContent.contains("@JsonProperty(\"default\") Boolean defaultValue"),
                "Should avoid Java keyword 'default' using 'defaultValue'");
        assertTrue(userContent.contains("public static Builder builder()"), "Should generate builder");

        Path sealedJava = outDir.resolve("test/pkg/SimpleSealed.java");
        assertTrue(Files.exists(sealedJava), "SimpleSealed.java should be generated");

        String sealedContent = Files.readString(sealedJava);
        assertTrue(sealedContent.contains("public sealed interface SimpleSealed"), "Should generate sealed interface");
        assertTrue(sealedContent.contains("permits Sub1, Sub2"), "Should permit subclasses");
        assertTrue(sealedContent.contains("@JsonTypeInfo"), "Should have Jackson polymorphism annotations");
    }
}
