package dev.bgame.lanplus.cosmetics.blockbench;

import com.google.gson.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class BlockbenchImporter {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private BlockbenchImporter() {
    }

    public static ImportedModel read(String id, Path file) throws IOException {
        JsonObject project = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        return convert(id, project, file.getParent());
    }

    public static String clipName(String id, String animation) {
        return "animation." + slug(id, "cosmetic") + "." + slug(animation, "animation");
    }

    static ImportedModel convert(String id, JsonObject project) throws IOException {
        return convert(id, project, null);
    }

    private static ImportedModel convert(String id, JsonObject project, Path directory) throws IOException {
        TextureAtlas textures = TextureAtlas.create(project, directory);
        GeometryConverter converter = new GeometryConverter(id, project, textures);
        byte[] geometry = GSON.toJson(converter.geometry()).getBytes(StandardCharsets.UTF_8);
        JsonObject animation = converter.animations();
        byte[] animations = animation == null ? null : GSON.toJson(animation).getBytes(StandardCharsets.UTF_8);
        return new ImportedModel(geometry, animations, textures.png());
    }

    public record ImportedModel(byte[] geometry, byte[] animations, byte[] texture) {
    }

    private static final class GeometryConverter {

        private final String id;
        private final JsonObject project;
        private final TextureAtlas textures;
        private final Map<String, JsonObject> groups = new LinkedHashMap<>();
        private final Map<String, JsonObject> elements = new LinkedHashMap<>();
        private final Map<String, String> boneNames = new LinkedHashMap<>();
        private final Map<String, JsonObject> bonesByName = new LinkedHashMap<>();
        private final Set<String> usedBoneNames = new HashSet<>();
        private final Set<String> emittedNodes = new HashSet<>();
        private final Set<String> visitedElements = new HashSet<>();
        private final JsonArray bones = new JsonArray();
        private String rootBoneName;

        private GeometryConverter(String id, JsonObject project, TextureAtlas textures) {
            this.id = slug(id, "cosmetic");
            this.project = project;
            this.textures = textures;
            indexProject();
        }

        private JsonObject geometry() {
            JsonArray outliner = array(project, "outliner");
            for (JsonElement entry : outliner) {
                walk(entry, null);
            }
            for (Map.Entry<String, JsonObject> entry : elements.entrySet()) {
                if (!visitedElements.contains(entry.getKey())) {
                    emitElement(entry.getKey(), entry.getValue(), null);
                }
            }

            Bounds bounds = Bounds.from(elements.values());
            JsonObject description = new JsonObject();
            description.addProperty("identifier", "geometry." + id);
            description.addProperty("texture_width", textures.width());
            description.addProperty("texture_height", textures.height());
            description.addProperty("visible_bounds_width", Math.max(1, bounds.horizontalSpan() / 16));
            description.addProperty("visible_bounds_height", Math.max(1, bounds.height() / 16));
            description.add("visible_bounds_offset", vector(0, bounds.centerY() / 16, 0));

            JsonObject model = new JsonObject();
            model.add("description", description);
            model.add("bones", bones);

            JsonArray models = new JsonArray();
            models.add(model);
            JsonObject root = new JsonObject();
            root.addProperty("format_version", "1.12.0");
            root.add("minecraft:geometry", models);
            return root;
        }

        private JsonObject animations() {
            JsonArray sourceAnimations = array(project, "animations");
            if (sourceAnimations.isEmpty()) {
                return null;
            }

            JsonObject converted = new JsonObject();
            Set<String> names = new HashSet<>();
            for (JsonElement sourceElement : sourceAnimations) {
                if (!sourceElement.isJsonObject()) {
                    continue;
                }
                JsonObject source = sourceElement.getAsJsonObject();
                String clip = uniqueAnimationName(names, string(source, "name", "animation"));
                JsonObject animation = new JsonObject();
                animation.addProperty("animation_length", number(source, "length", 0));
                String loop = string(source, "loop", "once");
                if ("loop".equals(loop) || booleanValue(source.get("loop"))) {
                    animation.addProperty("loop", true);
                } else if ("hold".equals(loop)) {
                    animation.addProperty("hold_on_last_frame", true);
                }

                JsonObject animatedBones = new JsonObject();
                JsonObject animators = object(source, "animators");
                for (Map.Entry<String, JsonElement> animatorEntry : animators.entrySet()) {
                    String boneName = boneNames.get(animatorEntry.getKey());
                    if (boneName == null || !bonesByName.containsKey(boneName)
                            || !animatorEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject animator = animatorEntry.getValue().getAsJsonObject();
                    JsonArray keyframes = array(animator, "keyframes");
                    JsonObject channels = convertChannels(keyframes);
                    if (!channels.isEmpty()) {
                        animatedBones.add(boneName, channels);
                    }
                }
                animation.add("bones", animatedBones);
                converted.add(clipName(id, clip), animation);
            }
            if (converted.isEmpty()) {
                return null;
            }
            JsonObject root = new JsonObject();
            root.addProperty("format_version", "1.8.0");
            root.add("animations", converted);
            return root;
        }

        private JsonObject convertChannels(JsonArray keyframes) {
            Map<String, TreeMap<Double, JsonElement>> channels = new LinkedHashMap<>();
            channels.put("rotation", new TreeMap<>());
            channels.put("position", new TreeMap<>());
            channels.put("scale", new TreeMap<>());
            for (JsonElement keyframeElement : keyframes) {
                if (!keyframeElement.isJsonObject()) {
                    continue;
                }
                JsonObject keyframe = keyframeElement.getAsJsonObject();
                TreeMap<Double, JsonElement> channel = channels.get(string(keyframe, "channel", ""));
                JsonArray points = array(keyframe, "data_points");
                if (channel == null || points.isEmpty() || !points.get(0).isJsonObject()) {
                    continue;
                }
                JsonObject point = points.get(0).getAsJsonObject();
                JsonArray value = vector(component(point, "x"), component(point, "y"), component(point, "z"));
                String interpolation = string(keyframe, "interpolation", "linear").toLowerCase(Locale.ROOT);
                if ("catmullrom".equals(interpolation) || "step".equals(interpolation)) {
                    JsonObject eased = new JsonObject();
                    eased.add("post", value);
                    eased.addProperty("lerp_mode", interpolation);
                    channel.put(number(keyframe, "time", 0), eased);
                } else {
                    channel.put(number(keyframe, "time", 0), value);
                }
            }

            JsonObject result = new JsonObject();
            for (Map.Entry<String, TreeMap<Double, JsonElement>> channel : channels.entrySet()) {
                if (channel.getValue().isEmpty()) {
                    continue;
                }
                JsonObject values = new JsonObject();
                for (Map.Entry<Double, JsonElement> entry : channel.getValue().entrySet()) {
                    values.add(timestamp(entry.getKey()), entry.getValue());
                }
                result.add(channel.getKey(), values);
            }
            return result;
        }

        private void indexProject() {
            for (JsonElement element : array(project, "groups")) {
                if (element.isJsonObject()) {
                    JsonObject group = element.getAsJsonObject();
                    String uuid = string(group, "uuid", "");
                    if (!uuid.isEmpty()) {
                        groups.put(uuid, group);
                        boneNames.put(uuid, uniqueBoneName(string(group, "name", "bone")));
                    }
                }
            }
            for (JsonElement elementValue : array(project, "elements")) {
                if (!elementValue.isJsonObject()) {
                    continue;
                }
                JsonObject element = elementValue.getAsJsonObject();
                String uuid = string(element, "uuid", "");
                if (uuid.isEmpty()) {
                    continue;
                }
                elements.put(uuid, element);
                String type = string(element, "type", "cube");
                if ("mesh".equals(type) || "armature".equals(type) || "armature_bone".equals(type)) {
                    boneNames.put(uuid, uniqueBoneName(string(element, "name", type)));
                }
            }
        }

        private void walk(JsonElement entry, String parent) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                String uuid = entry.getAsString();
                JsonObject element = elements.get(uuid);
                if (element != null) {
                    emitElement(uuid, element, parent);
                }
                return;
            }
            if (!entry.isJsonObject()) {
                return;
            }

            JsonObject outlinerNode = entry.getAsJsonObject();
            String uuid = string(outlinerNode, "uuid", "");
            JsonObject node = groups.get(uuid);
            boolean elementNode = false;
            if (node == null) {
                node = elements.get(uuid);
                elementNode = node != null;
            }
            String childParent = parent;
            if (node != null && exported(node)) {
                childParent = emitBoneNode(uuid, node, parent);
                if (elementNode) {
                    visitedElements.add(uuid);
                }
            } else if (node != null) {
                return;
            }
            for (JsonElement child : array(outlinerNode, "children")) {
                walk(child, childParent);
            }
        }

        private String emitBoneNode(String uuid, JsonObject node, String parent) {
            String name = boneNames.computeIfAbsent(uuid,
                    ignored -> uniqueBoneName(string(node, "name", "bone")));
            if (emittedNodes.add(uuid)) {
                JsonObject bone = bone(name, parent, vector(node, "origin", 0, 0, 0),
                        vector(node, "rotation", 0, 0, 0));
                bones.add(bone);
                bonesByName.put(name, bone);
            }
            return name;
        }

        private void emitElement(String uuid, JsonObject element, String parent) {
            if (!visitedElements.add(uuid) || !exported(element)) {
                return;
            }
            switch (string(element, "type", "cube")) {
                case "cube" -> addCube(parentBone(parent), element);
                case "mesh" -> addMesh(uuid, element, parent);
                case "armature", "armature_bone" -> emitBoneNode(uuid, element, parent);
                default -> {
                }
            }
        }

        private JsonObject parentBone(String parent) {
            if (parent != null) {
                return bonesByName.get(parent);
            }
            if (rootBoneName == null) {
                rootBoneName = uniqueBoneName("root");
                JsonObject root = bone(rootBoneName, null, vector(0, 0, 0), vector(0, 0, 0));
                bones.add(root);
                bonesByName.put(rootBoneName, root);
            }
            return bonesByName.get(rootBoneName);
        }

        private void addCube(JsonObject bone, JsonObject element) {
            JsonArray from = vector(element, "from", 0, 0, 0);
            JsonArray to = vector(element, "to", 0, 0, 0);
            JsonObject cube = new JsonObject();
            cube.add("origin", from.deepCopy());
            cube.add("size", vector(
                    to.get(0).getAsDouble() - from.get(0).getAsDouble(),
                    to.get(1).getAsDouble() - from.get(1).getAsDouble(),
                    to.get(2).getAsDouble() - from.get(2).getAsDouble()));
            cube.add("pivot", vector(element, "origin",
                    (from.get(0).getAsDouble() + to.get(0).getAsDouble()) / 2,
                    (from.get(1).getAsDouble() + to.get(1).getAsDouble()) / 2,
                    (from.get(2).getAsDouble() + to.get(2).getAsDouble()) / 2));
            cube.add("rotation", vector(element, "rotation", 0, 0, 0));
            if (element.has("inflate")) {
                cube.addProperty("inflate", number(element, "inflate", 0));
            }
            if (booleanValue(element.get("mirror_uv"))) {
                cube.addProperty("mirror", true);
            }

            JsonObject convertedFaces = new JsonObject();
            JsonObject faces = object(element, "faces");
            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                if (!faceEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                JsonArray uv = array(face, "uv");
                if (uv.size() < 4 || face.get("texture") == null || face.get("texture").isJsonNull()) {
                    continue;
                }
                int texture = textures.index(face.get("texture"));
                double u1 = textures.u(texture, uv.get(0).getAsDouble());
                double v1 = textures.v(texture, uv.get(1).getAsDouble());
                double u2 = textures.u(texture, uv.get(2).getAsDouble());
                double v2 = textures.v(texture, uv.get(3).getAsDouble());
                JsonObject convertedFace = new JsonObject();
                convertedFace.add("uv", vector(u1, v1));
                convertedFace.add("uv_size", vector(u2 - u1, v2 - v1));
                if (face.has("rotation")) {
                    convertedFace.addProperty("uv_rotation", face.get("rotation").getAsInt());
                }
                convertedFaces.add(faceEntry.getKey(), convertedFace);
            }
            cube.add("uv", convertedFaces);
            cubes(bone).add(cube);
        }

        private void addMesh(String uuid, JsonObject element, String parent) {
            String name = boneNames.computeIfAbsent(uuid,
                    ignored -> uniqueBoneName(string(element, "name", "mesh")));
            if (!emittedNodes.add(uuid)) {
                return;
            }
            JsonObject meshBone = bone(name, parent, vector(element, "origin", 0, 0, 0),
                    vector(element, "rotation", 0, 0, 0));
            meshBone.add("poly_mesh", polyMesh(element));
            bones.add(meshBone);
            bonesByName.put(name, meshBone);
        }

        private JsonObject polyMesh(JsonObject element) {
            JsonObject sourceVertices = object(element, "vertices");
            JsonArray origin = vector(element, "origin", 0, 0, 0);
            double originX = origin.get(0).getAsDouble();
            double originY = origin.get(1).getAsDouble();
            double originZ = origin.get(2).getAsDouble();
            List<String> vertexIds = new ArrayList<>();
            List<double[]> positions = new ArrayList<>();
            Map<String, Integer> positionIndices = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : sourceVertices.entrySet()) {
                if (!entry.getValue().isJsonArray() || entry.getValue().getAsJsonArray().size() < 3) {
                    continue;
                }
                JsonArray position = entry.getValue().getAsJsonArray();
                positionIndices.put(entry.getKey(), positions.size());
                vertexIds.add(entry.getKey());
                positions.add(new double[]{position.get(0).getAsDouble() + originX,
                        position.get(1).getAsDouble() + originY,
                        position.get(2).getAsDouble() + originZ});
            }

            List<Polygon> polygons = polygons(element, positionIndices);
            boolean smooth = "smooth".equals(string(element, "shading", "flat"));
            List<double[]> normals = normals(positions, polygons, smooth);
            JsonArray uvs = new JsonArray();
            JsonArray convertedPolygons = new JsonArray();
            for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
                Polygon polygon = polygons.get(polygonIndex);
                JsonArray convertedPolygon = new JsonArray();
                for (int position : polygon.positions()) {
                    String vertexId = vertexIds.get(position);
                    JsonArray sourceUv = polygon.uvs().has(vertexId) && polygon.uvs().get(vertexId).isJsonArray()
                            ? polygon.uvs().getAsJsonArray(vertexId) : new JsonArray();
                    double u = !sourceUv.isEmpty() ? sourceUv.get(0).getAsDouble() : 0;
                    double v = !sourceUv.isEmpty() && sourceUv.size() > 1 ? sourceUv.get(1).getAsDouble() : 0;
                    int uvIndex = uvs.size() / 2;
                    uvs.add(textures.u(polygon.texture(), u));
                    uvs.add(textures.v(polygon.texture(), v));
                    convertedPolygon.add(vector(position, smooth ? position : polygonIndex, uvIndex));
                }
                convertedPolygons.add(convertedPolygon);
            }

            JsonObject mesh = new JsonObject();
            mesh.addProperty("normalized_uvs", false);
            mesh.add("positions", flatten(positions));
            mesh.add("normals", flatten(normals));
            mesh.add("uvs", uvs);
            mesh.add("polys", convertedPolygons);
            return mesh;
        }

        private List<Polygon> polygons(JsonObject element, Map<String, Integer> positionIndices) {
            List<Polygon> polygons = new ArrayList<>();
            for (JsonElement faceElement : object(element, "faces").asMap().values()) {
                if (!faceElement.isJsonObject()) {
                    continue;
                }
                JsonObject face = faceElement.getAsJsonObject();
                JsonArray sourceVertices = array(face, "vertices");
                List<Integer> facePositions = new ArrayList<>();
                for (JsonElement sourceVertex : sourceVertices) {
                    Integer position = positionIndices.get(sourceVertex.getAsString());
                    if (position != null) {
                        facePositions.add(position);
                    }
                }
                if (facePositions.size() < 3) {
                    continue;
                }
                int texture = textures.index(face.get("texture"));
                JsonObject uvs = object(face, "uv");
                if (facePositions.size() <= 4) {
                    polygons.add(new Polygon(facePositions.stream().mapToInt(Integer::intValue).toArray(), texture, uvs));
                    continue;
                }
                for (int i = 1; i < facePositions.size() - 1; i++) {
                    polygons.add(new Polygon(new int[]{facePositions.getFirst(), facePositions.get(i),
                            facePositions.get(i + 1)}, texture, uvs));
                }
            }
            return polygons;
        }

        private static List<double[]> normals(List<double[]> positions, List<Polygon> polygons, boolean smooth) {
            if (!smooth) {
                List<double[]> result = new ArrayList<>(polygons.size());
                for (Polygon polygon : polygons) {
                    result.add(faceNormal(positions, polygon.positions()));
                }
                return result;
            }
            List<double[]> result = new ArrayList<>(positions.size());
            for (int i = 0; i < positions.size(); i++) {
                result.add(new double[3]);
            }
            for (Polygon polygon : polygons) {
                double[] normal = faceNormal(positions, polygon.positions());
                for (int position : polygon.positions()) {
                    double[] accumulated = result.get(position);
                    accumulated[0] += normal[0];
                    accumulated[1] += normal[1];
                    accumulated[2] += normal[2];
                }
            }
            for (double[] normal : result) {
                normalize(normal);
            }
            return result;
        }

        private static double[] faceNormal(List<double[]> positions, int[] polygon) {
            double[] a = positions.get(polygon[0]);
            double[] b = positions.get(polygon[1]);
            double[] c = positions.get(polygon[2]);
            double abX = b[0] - a[0];
            double abY = b[1] - a[1];
            double abZ = b[2] - a[2];
            double acX = c[0] - a[0];
            double acY = c[1] - a[1];
            double acZ = c[2] - a[2];
            double[] normal = {
                    abY * acZ - abZ * acY,
                    abZ * acX - abX * acZ,
                    abX * acY - abY * acX
            };
            normalize(normal);
            return normal;
        }

        private static void normalize(double[] vector) {
            double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
            if (length == 0) {
                vector[1] = 1;
                return;
            }
            vector[0] /= length;
            vector[1] /= length;
            vector[2] /= length;
        }

        private String uniqueBoneName(String requested) {
            String base = slug(requested, "bone");
            String candidate = base;
            int suffix = 2;
            while (!usedBoneNames.add(candidate)) {
                candidate = base + "_" + suffix++;
            }
            return candidate;
        }

        private String uniqueAnimationName(Set<String> names, String requested) {
            String base = slug(requested, "animation");
            String candidate = base;
            int suffix = 2;
            while (!names.add(candidate)) {
                candidate = base + "_" + suffix++;
            }
            return candidate;
        }

        private static JsonObject bone(String name, String parent, JsonArray pivot, JsonArray rotation) {
            JsonObject bone = new JsonObject();
            bone.addProperty("name", name);
            if (parent != null) {
                bone.addProperty("parent", parent);
            }
            bone.add("pivot", pivot);
            bone.add("rotation", rotation);
            return bone;
        }

        private static JsonArray cubes(JsonObject bone) {
            if (!bone.has("cubes")) {
                bone.add("cubes", new JsonArray());
            }
            return bone.getAsJsonArray("cubes");
        }

        private record Polygon(int[] positions, int texture, JsonObject uvs) {
        }
    }

    private static final class TextureAtlas {

        private final List<TextureRegion> regions;
        private final Map<String, Integer> uuids;
        private final int width;
        private final int height;
        private final byte[] png;

        private TextureAtlas(List<TextureRegion> regions, Map<String, Integer> uuids, int width, int height,
                             byte[] png) {
            this.regions = regions;
            this.uuids = uuids;
            this.width = width;
            this.height = height;
            this.png = png;
        }

        private static TextureAtlas create(JsonObject project, Path directory) throws IOException {
            JsonArray sourceTextures = array(project, "textures");
            JsonObject resolution = object(project, "resolution");
            double fallbackWidth = number(resolution, "width", 16);
            double fallbackHeight = number(resolution, "height", 16);
            List<TextureRegion> regions = new ArrayList<>();
            Map<String, Integer> uuids = new HashMap<>();
            boolean hasImage = false;
            int atlasWidth = 0;
            int atlasHeight = 1;

            if (sourceTextures.isEmpty()) {
                regions.add(new TextureRegion(0, Math.max(1, fallbackWidth), Math.max(1, fallbackHeight),
                        Math.max(1, (int) Math.ceil(fallbackWidth)), Math.max(1, (int) Math.ceil(fallbackHeight)), null));
                atlasWidth = regions.getFirst().width();
                atlasHeight = regions.getFirst().height();
            } else {
                for (JsonElement textureElement : sourceTextures) {
                    if (!textureElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject texture = textureElement.getAsJsonObject();
                    double uvWidth = number(texture, "uv_width", fallbackWidth);
                    double uvHeight = number(texture, "uv_height", fallbackHeight);
                    if (uvWidth <= 0) {
                        uvWidth = Math.max(1, fallbackWidth);
                    }
                    if (uvHeight <= 0) {
                        uvHeight = Math.max(1, fallbackHeight);
                    }
                    BufferedImage image = image(texture, directory);
                    int imageWidth = image == null ? Math.max(1, (int) Math.ceil(uvWidth)) : image.getWidth();
                    int frameHeight = image == null ? Math.max(1, (int) Math.ceil(uvHeight))
                            : Math.clamp((int) Math.round(uvHeight * imageWidth / uvWidth), 1, image.getHeight());
                    TextureRegion region = new TextureRegion(atlasWidth, uvWidth, uvHeight, imageWidth, frameHeight, image);
                    regions.add(region);
                    String uuid = string(texture, "uuid", "");
                    if (!uuid.isEmpty()) {
                        uuids.put(uuid, regions.size() - 1);
                    }
                    atlasWidth += imageWidth;
                    atlasHeight = Math.max(atlasHeight, frameHeight);
                    hasImage |= image != null;
                }
            }
            if (regions.isEmpty()) {
                regions.add(new TextureRegion(0, fallbackWidth, fallbackHeight,
                        Math.max(1, (int) Math.ceil(fallbackWidth)), Math.max(1, (int) Math.ceil(fallbackHeight)), null));
                atlasWidth = regions.getFirst().width();
                atlasHeight = regions.getFirst().height();
            }
            return new TextureAtlas(regions, uuids, Math.max(1, atlasWidth), atlasHeight,
                    hasImage ? atlasPng(regions, atlasWidth, atlasHeight) : null);
        }

        private int index(JsonElement reference) {
            if (reference == null || reference.isJsonNull()) {
                return 0;
            }
            try {
                int index = reference.getAsInt();
                return index >= 0 && index < regions.size() ? index : 0;
            } catch (RuntimeException ignored) {
                return uuids.getOrDefault(reference.getAsString(), 0);
            }
        }

        private double u(int texture, double value) {
            TextureRegion region = regions.get(valid(texture));
            return region.x() + value * region.width() / region.uvWidth();
        }

        private double v(int texture, double value) {
            TextureRegion region = regions.get(valid(texture));
            return value * region.height() / region.uvHeight();
        }

        private int valid(int texture) {
            return texture >= 0 && texture < regions.size() ? texture : 0;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private byte[] png() {
            return png;
        }

        private static BufferedImage image(JsonObject texture, Path directory) {
            String source = string(texture, "source", "");
            try {
                if (source.startsWith("data:image/") && source.contains(",")) {
                    byte[] bytes = Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
                    return ImageIO.read(new ByteArrayInputStream(bytes));
                }
                if (directory == null) {
                    return null;
                }
                String relative = string(texture, "relative_path", "");
                if (relative.isBlank()) {
                    relative = string(texture, "path", "");
                }
                if (relative.isBlank()) {
                    return null;
                }
                Path candidate = directory.resolve(relative.replace('\\', '/')).normalize();
                Path normalizedDirectory = directory.toAbsolutePath().normalize();
                if (!candidate.toAbsolutePath().normalize().startsWith(normalizedDirectory)
                        || !Files.isRegularFile(candidate)) {
                    return null;
                }
                return ImageIO.read(candidate.toFile());
            } catch (IllegalArgumentException | IOException ignored) {
                return null;
            }
        }

        private static byte[] atlasPng(List<TextureRegion> regions, int width, int height) throws IOException {
            BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (TextureRegion region : regions) {
                if (region.image() == null) {
                    continue;
                }
                int[] pixels = region.image().getRGB(0, 0, region.width(), region.height(), null, 0, region.width());
                atlas.setRGB(region.x(), 0, region.width(), region.height(), pixels, 0, region.width());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(atlas, "png", output);
            return output.toByteArray();
        }

        private record TextureRegion(int x, double uvWidth, double uvHeight, int width, int height,
                                     BufferedImage image) {
        }
    }

    private static final class Bounds {

        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private static Bounds from(Iterable<JsonObject> elements) {
            Bounds bounds = new Bounds();
            for (JsonObject element : elements) {
                JsonArray from = array(element, "from");
                JsonArray to = array(element, "to");
                if (from.size() >= 3) {
                    bounds.include(from);
                }
                if (to.size() >= 3) {
                    bounds.include(to);
                }
                for (JsonElement vertex : object(element, "vertices").asMap().values()) {
                    if (vertex.isJsonArray() && vertex.getAsJsonArray().size() >= 3) {
                        JsonArray point = vertex.getAsJsonArray();
                        JsonArray origin = vector(element, "origin", 0, 0, 0);
                        bounds.include(vector(point.get(0).getAsDouble() + origin.get(0).getAsDouble(),
                                point.get(1).getAsDouble() + origin.get(1).getAsDouble(),
                                point.get(2).getAsDouble() + origin.get(2).getAsDouble()));
                    }
                }
            }
            if (!Double.isFinite(bounds.minX)) {
                bounds.include(vector(0, 0, 0));
                bounds.include(vector(16, 16, 16));
            }
            return bounds;
        }

        private void include(JsonArray point) {
            minX = Math.min(minX, point.get(0).getAsDouble());
            minY = Math.min(minY, point.get(1).getAsDouble());
            minZ = Math.min(minZ, point.get(2).getAsDouble());
            maxX = Math.max(maxX, point.get(0).getAsDouble());
            maxY = Math.max(maxY, point.get(1).getAsDouble());
            maxZ = Math.max(maxZ, point.get(2).getAsDouble());
        }

        private double horizontalSpan() {
            return Math.max(maxX - minX, maxZ - minZ);
        }

        private double height() {
            return maxY - minY;
        }

        private double centerY() {
            return (minY + maxY) / 2;
        }
    }

    private static JsonObject object(JsonObject source, String key) {
        return source.has(key) && source.get(key).isJsonObject() ? source.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject source, String key) {
        return source.has(key) && source.get(key).isJsonArray() ? source.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonArray vector(JsonObject source, String key, double x, double y, double z) {
        JsonArray value = array(source, key);
        return value.size() >= 3 ? value.deepCopy() : vector(x, y, z);
    }

    private static JsonArray vector(double... values) {
        JsonArray array = new JsonArray(values.length);
        for (double value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray vector(JsonElement... values) {
        JsonArray array = new JsonArray(values.length);
        for (JsonElement value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray flatten(List<double[]> values) {
        JsonArray flattened = new JsonArray();
        for (double[] value : values) {
            for (double component : value) {
                flattened.add(component);
            }
        }
        return flattened;
    }

    private static JsonElement component(JsonObject point, String key) {
        if (!point.has(key) || !point.get(key).isJsonPrimitive()) {
            return new com.google.gson.JsonPrimitive(0);
        }
        String value = point.get(key).getAsString();
        try {
            return new com.google.gson.JsonPrimitive(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return new com.google.gson.JsonPrimitive(value);
        }
    }

    private static String string(JsonObject source, String key, String fallback) {
        return source.has(key) && source.get(key).isJsonPrimitive() ? source.get(key).getAsString() : fallback;
    }

    private static double number(JsonObject source, String key, double fallback) {
        try {
            return source.has(key) ? source.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    private static boolean exported(JsonObject value) {
        return !value.has("export") || !value.get("export").isJsonPrimitive() || value.get("export").getAsBoolean();
    }

    private static String timestamp(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String slug(String value, String fallback) {
        String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? fallback : slug;
    }
}

