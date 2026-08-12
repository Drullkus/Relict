package us.drullk.relict.moonconfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A single-mesh glTF binary, flattened into the arrays a rasterizer wants.
 * <p>
 * This is deliberately not a general glTF reader. NASA's Phobos and Deimos models are one mesh with one
 * primitive, no node transform, and an embedded base colour texture, so anything outside that shape is
 * rejected loudly rather than half-supported.
 */
public final class GlbModel {

    private static final int MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final int TRIANGLES = 4;
    private static final int FLOAT = 5126;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int UNSIGNED_SHORT = 5123;
    private static final int UNSIGNED_INT = 5125;

    private final float[] positions;
    private final float[] normals;
    private final float[] uvs;
    private final int[] indices;
    private final BufferedImage albedo;

    private GlbModel(float[] positions, float[] normals, float[] uvs, int[] indices, BufferedImage albedo) {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.indices = indices;
        this.albedo = albedo;
    }

    public static GlbModel read(byte[] glb) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt() != MAGIC) {
            throw new IOException("Not a glTF binary");
        }

        buffer.getInt(); // version
        buffer.getInt(); // total length

        JsonObject json = null;
        ByteBuffer bin = null;

        while (buffer.remaining() >= 8) {
            int length = buffer.getInt();
            int type = buffer.getInt();
            byte[] chunk = new byte[length];
            buffer.get(chunk);

            if (type == CHUNK_JSON) {
                json = JsonParser.parseString(new String(chunk, StandardCharsets.UTF_8)).getAsJsonObject();
            } else if (type == CHUNK_BIN) {
                bin = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
            }
        }

        if (json == null || bin == null) {
            throw new IOException("glTF binary is missing its JSON or BIN chunk");
        }

        requireUntransformedNodes(json);
        JsonObject primitive = onlyPrimitive(json);
        if (primitive.has("mode") && primitive.get("mode").getAsInt() != TRIANGLES) {
            throw new IOException("Only triangle meshes are supported");
        }

        JsonObject attributes = primitive.getAsJsonObject("attributes");
        return new GlbModel(
                readFloats(json, bin, attributes.get("POSITION").getAsInt(), 3),
                readFloats(json, bin, attributes.get("NORMAL").getAsInt(), 3),
                readFloats(json, bin, attributes.get("TEXCOORD_0").getAsInt(), 2),
                readIndices(json, bin, primitive.get("indices").getAsInt()),
                readAlbedo(json, bin));
    }

    private static JsonObject onlyPrimitive(JsonObject json) throws IOException {
        JsonArray meshes = json.getAsJsonArray("meshes");
        if (meshes.size() != 1) {
            throw new IOException("Expected exactly one mesh, found " + meshes.size());
        }

        JsonArray primitives = meshes.get(0).getAsJsonObject().getAsJsonArray("primitives");
        if (primitives.size() != 1) {
            throw new IOException("Expected exactly one primitive, found " + primitives.size());
        }

        return primitives.get(0).getAsJsonObject();
    }

    private static void requireUntransformedNodes(JsonObject json) throws IOException {
        for (var node : json.getAsJsonArray("nodes")) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("matrix") || object.has("rotation") || object.has("scale") || object.has("translation")) {
                throw new IOException("Node transforms are not supported; bake them into the mesh before export");
            }
        }
    }

    private static float[] readFloats(JsonObject json, ByteBuffer bin, int accessorIndex, int components) throws IOException {
        JsonObject accessor = json.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        if (accessor.get("componentType").getAsInt() != FLOAT) {
            throw new IOException("Expected float accessor " + accessorIndex);
        }

        JsonObject view = json.getAsJsonArray("bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        int base = intOrZero(view, "byteOffset") + intOrZero(accessor, "byteOffset");
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : components * Float.BYTES;

        float[] values = new float[count * components];
        for (int element = 0; element < count; element++) {
            for (int component = 0; component < components; component++) {
                values[element * components + component] = bin.getFloat(base + element * stride + component * Float.BYTES);
            }
        }

        return values;
    }

    private static int[] readIndices(JsonObject json, ByteBuffer bin, int accessorIndex) throws IOException {
        JsonObject accessor = json.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        JsonObject view = json.getAsJsonArray("bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        int base = intOrZero(view, "byteOffset") + intOrZero(accessor, "byteOffset");
        int componentType = accessor.get("componentType").getAsInt();

        int[] indices = new int[count];
        for (int index = 0; index < count; index++) {
            indices[index] = switch (componentType) {
                case UNSIGNED_BYTE -> bin.get(base + index) & 0xFF;
                case UNSIGNED_SHORT -> bin.getShort(base + index * Short.BYTES) & 0xFFFF;
                case UNSIGNED_INT -> bin.getInt(base + index * Integer.BYTES);
                default -> throw new IOException("Unsupported index component type " + componentType);
            };
        }

        return indices;
    }

    private static BufferedImage readAlbedo(JsonObject json, ByteBuffer bin) throws IOException {
        int textureIndex = json.getAsJsonArray("materials").get(0).getAsJsonObject()
                .getAsJsonObject("pbrMetallicRoughness")
                .getAsJsonObject("baseColorTexture")
                .get("index").getAsInt();
        int imageIndex = json.getAsJsonArray("textures").get(textureIndex).getAsJsonObject().get("source").getAsInt();
        JsonObject image = json.getAsJsonArray("images").get(imageIndex).getAsJsonObject();
        if (!image.has("bufferView")) {
            throw new IOException("Only textures embedded in the BIN chunk are supported");
        }

        JsonObject view = json.getAsJsonArray("bufferViews").get(image.get("bufferView").getAsInt()).getAsJsonObject();
        byte[] encoded = new byte[view.get("byteLength").getAsInt()];
        bin.duplicate().position(intOrZero(view, "byteOffset")).get(encoded);

        // The models declare .jpg names with image/png mime types, so trust the bytes rather than either.
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        if (decoded == null) {
            throw new IOException("Could not decode the embedded base colour texture");
        }

        return decoded;
    }

    private static int intOrZero(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    public int triangleCount() {
        return this.indices.length / 3;
    }

    public int vertexCount() {
        return this.positions.length / 3;
    }

    public int index(int position) {
        return this.indices[position];
    }

    public float positionX(int vertex) {
        return this.positions[vertex * 3];
    }

    public float positionY(int vertex) {
        return this.positions[vertex * 3 + 1];
    }

    public float positionZ(int vertex) {
        return this.positions[vertex * 3 + 2];
    }

    public float normalX(int vertex) {
        return this.normals[vertex * 3];
    }

    public float normalY(int vertex) {
        return this.normals[vertex * 3 + 1];
    }

    public float normalZ(int vertex) {
        return this.normals[vertex * 3 + 2];
    }

    public float u(int vertex) {
        return this.uvs[vertex * 2];
    }

    public float v(int vertex) {
        return this.uvs[vertex * 2 + 1];
    }

    public BufferedImage albedo() {
        return this.albedo;
    }

}
