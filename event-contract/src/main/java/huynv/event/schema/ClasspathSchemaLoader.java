package huynv.event.schema;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads JSON Schema documents from the classpath using schema identifiers as file names.
 */
public final class ClasspathSchemaLoader {

    private final String basePath;

    /**
     * Creates a schema loader using the default base path of {@code schemas/}.
     *
     * @return Initializes a schema loader.
     */
    public ClasspathSchemaLoader() {
        this("schemas/");
    }

    /**
     * Creates a schema loader using an explicit base path.
     *
     * @param basePath Base classpath directory containing schema files.
     * @return Initializes a schema loader.
     */
    public ClasspathSchemaLoader(String basePath) {
        this.basePath = basePath == null ? "schemas/" : basePath;
    }

    /**
     * Loads a JSON Schema document as a string from the classpath.
     *
     * @param schemaId Schema identifier used to resolve the schema resource name.
     * @return Returns the JSON Schema content.
     */
    public String loadSchema(String schemaId) {
        Objects.requireNonNull(schemaId, "schemaId");
        String resourceName = basePath + schemaId + ".json";
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IllegalStateException("JSON Schema resource not found for schemaId=" + schemaId + " at " + resourceName + ".");
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read JSON Schema resource for schemaId=" + schemaId + ".", ex);
        }
    }
}


