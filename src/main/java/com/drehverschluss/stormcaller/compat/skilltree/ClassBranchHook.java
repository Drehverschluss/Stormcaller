package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.Stormcaller;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.puffish.skillsmod.api.config.ConfigContext;
import net.puffish.skillsmod.api.json.JsonPath;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.reader.ConfigReader;
import net.puffish.skillsmod.config.reader.PackConfigReader;
import net.puffish.skillsmod.util.DisposeContext;

/**
 * Puts the Stormcaller branch into the Class Skills tab of Skill Tree while Pufferfish's Skills loads its data (server
 * start and {@code /reload}). Called from {@code mixin.skills.ConfigReaderMixin} and {@code SkillsModMixin}.
 * <ul>
 *     <li>Works on the final data: the files come through the same reader Pufferfish's Skills uses, after all data packs.</li>
 *     <li>Only if the original tab loaded fine. The extended tab is checked with Pufferfish's own parser first; on any
 *     problem the original stays and the own Stormcaller tab is used (warning in the log). Other tabs never lose anything.</li>
 *     <li>The own tab is left out only if the branch is in the Class Skills tab in the same load: never both, never none.</li>
 *     <li>Titles, texts, icons and rewards come from the definitions of the own tab, the place from
 *     {@code stormcaller:stormcaller_skill_tree/class_branch.json} ({@link ClassBranchLayout}).</li>
 * </ul>
 */
public final class ClassBranchHook {
    public static final String SKILL_TREE_NAMESPACE = "skill_tree_rpgs";
    public static final String CLASS_TAB = "class_skills";
    /** Prefix of all our node ids in foreign tabs. */
    public static final String PREFIX = "stormcaller_";
    private static final ResourceLocation LAYOUT = Stormcaller.id("stormcaller_skill_tree/class_branch.json");

    private static boolean loading;
    private static boolean injected;
    private static boolean ownTabRead;

    private ClassBranchHook() {}

    /** Start of a load of Pufferfish's Skills. */
    public static void onLoadStart() {
        loading = true;
        injected = false;
        ownTabRead = false;
    }

    /** The category list of one data pack: leaves out the own tab if the branch is in the Class Skills tab already. */
    public static List<String> filterOwnTab(String namespace, List<String> ids) {
        if (!Stormcaller.MODID.equals(namespace)) {
            return ids;
        }
        ownTabRead = true;
        if (injected && ids.contains(SkillTab.STORMCALLER_CATEGORY.getPath())) {
            List<String> out = new ArrayList<>(ids);
            out.remove(SkillTab.STORMCALLER_CATEGORY.getPath());
            return out;
        }
        return ids;
    }

    /** After one category was read: the extended Class Skills tab, or the original. */
    public static Result<CategoryConfig, Problem> afterReadCategory(ConfigReader reader, ResourceManager resources, String namespace,
                                                                    String id, int position, ConfigContext context,
                                                                    Result<CategoryConfig, Problem> original) {
        if (!SKILL_TREE_NAMESPACE.equals(namespace) || !CLASS_TAB.equals(id)) {
            return original;
        }
        SkillTreeVariant.Choice choice = SkillTreeVariant.choose();
        if (!choice.classTree()) {
            Stormcaller.LOGGER.info("Stormcaller skills: own tab ({}).", choice.reason());
            return original;
        }
        if (!loading) {
            Stormcaller.LOGGER.warn("Stormcaller skills: start of the load not seen, using the own tab.");
            return original;
        }
        if (original.getSuccess().isEmpty()) {
            Stormcaller.LOGGER.warn("Stormcaller skills: {}:{} failed to load on its own, left untouched, using the own tab.",
                    namespace, id);
            return original;
        }
        if (ownTabRead) {
            Stormcaller.LOGGER.warn("Stormcaller skills: the own tab was read before {}:{}, using the own tab.", namespace, id);
            return original;
        }
        try {
            long start = System.nanoTime();
            Extended extended = extend(reader, resources, namespace, id, position, context, choice);
            if (extended.result().getSuccess().isPresent()) {
                original.getSuccess().get().dispose(new DisposeContext(ServerLifecycleHooks.getCurrentServer()));
                injected = true;
                Stormcaller.LOGGER.info("Stormcaller skills: Class Skills tab of Skill Tree ({}), {} ({} ms).", choice.reason(),
                        extended.description(), (System.nanoTime() - start) / 1_000_000);
                return extended.result();
            }
            Stormcaller.LOGGER.warn("Stormcaller skills could not be added to {}:{}, using the own tab instead: {}", namespace, id,
                    extended.result().getFailure().map(Object::toString).orElse("?"));
        } catch (Exception e) {
            Stormcaller.LOGGER.warn("Stormcaller skills could not be added to {}:{}, using the own tab instead: {}", namespace, id,
                    e.toString());
        }
        return original;
    }

    private record Extended(Result<CategoryConfig, Problem> result, String description) {}

    private static Extended extend(ConfigReader reader, ResourceManager resources, String namespace, String id, int position,
                                   ConfigContext context, SkillTreeVariant.Choice choice) throws Exception {
        JsonObject general = read(reader, id, "category.json");
        JsonObject definitions = read(reader, id, "definitions.json").deepCopy();
        JsonObject skills = read(reader, id, "skills.json").deepCopy();
        JsonElement connectionsElement = reader.read(Path.of("categories", id, "connections.json")).getSuccess().orElseThrow().getJson();
        if (!connectionsElement.isJsonObject()) {
            throw new IllegalStateException("connections.json has the old list form");
        }
        JsonObject connections = connectionsElement.getAsJsonObject().deepCopy();

        PackConfigReader own = new PackConfigReader(resources, Stormcaller.MODID);
        JsonObject ownDefinitions = own.read(Path.of("categories", "stormcaller", "definitions.json")).getSuccess()
                .orElseThrow(() -> new IllegalStateException("definitions of the own tab missing")).getJson().getAsJsonObject();
        JsonObject ownSkills = own.read(Path.of("categories", "stormcaller", "skills.json")).getSuccess()
                .orElseThrow(() -> new IllegalStateException("nodes of the own tab missing")).getJson().getAsJsonObject();
        ClassBranchLayout.Settings settings = ClassBranchLayout.parse(readLayout(resources));
        ClassBranchLayout.Form form = ClassBranchLayout.chooseForm(settings, skills);

        // place: respace, else gap (with the reason)
        ClassBranchLayout.Placement placement;
        if (choice.respace()) {
            try {
                placement = ClassBranchLayout.respace(skills, connections, settings, form);
            } catch (ClassBranchLayout.NoPlace e) {
                Stormcaller.LOGGER.info("Stormcaller skills: layout respace not possible ({}), using layout gap.", e.getMessage());
                placement = ClassBranchLayout.gap(skills, connections, settings, form);
            }
        } else {
            placement = ClassBranchLayout.gap(skills, connections, settings, form);
        }

        // foreign nodes: only their positions change (respace)
        for (Map.Entry<String, int[]> e : placement.moved().entrySet()) {
            JsonObject node = skills.getAsJsonObject(e.getKey());
            node.addProperty("x", e.getValue()[0]);
            node.addProperty("y", e.getValue()[1]);
        }
        // our definitions (single source: the own tab)
        for (String key : ownDefinitions.keySet()) {
            if (definitions.has(key)) {
                throw new IllegalStateException("definition id " + key + " is already used in " + namespace + ":" + id);
            }
            definitions.add(key, ownDefinitions.get(key).deepCopy());
        }
        // our nodes
        for (Map.Entry<String, int[]> e : placement.ours().entrySet()) {
            String node = e.getKey();
            String skillId = PREFIX + node;
            if (skills.has(skillId)) {
                throw new IllegalStateException("node id " + skillId + " is already used in " + namespace + ":" + id);
            }
            JsonObject ownNode = ownSkills.has(node) ? ownSkills.getAsJsonObject(node) : null;
            if (ownNode == null || !ownNode.has("definition")) {
                throw new IllegalStateException("node " + node + " of the layout is not in the own tab");
            }
            JsonObject out = new JsonObject();
            out.addProperty("definition", ownNode.get("definition").getAsString());
            out.addProperty("x", e.getValue()[0]);
            out.addProperty("y", e.getValue()[1]);
            if ("root".equals(node)) {
                out.addProperty("root", true);
            }
            skills.add(skillId, out);
        }
        addLinks(connections, "normal", form.normal());
        addLinks(connections, "exclusive", form.exclusive());

        String base = namespace + ":puffish_skills/categories/" + id + "/";
        Optional<net.puffish.skillsmod.api.json.JsonElement> experience = optional(reader, id, "experience.json");
        Optional<net.puffish.skillsmod.api.json.JsonElement> exchange = optional(reader, id, "exchange.json");
        Result<CategoryConfig, Problem> result = CategoryConfig.parse(ResourceLocation.fromNamespaceAndPath(namespace, id), position,
                wrap(general, base + "category.json"), wrap(definitions, base + "definitions.json (+stormcaller)"),
                wrap(skills, base + "skills.json (+stormcaller)"), wrap(connections, base + "connections.json (+stormcaller)"),
                experience, exchange, context);
        return new Extended(result, placement.description());
    }

    private static JsonObject read(ConfigReader reader, String id, String file) {
        return reader.read(Path.of("categories", id, file)).getSuccess()
                .orElseThrow(() -> new IllegalStateException(file + " cannot be read")).getJson().getAsJsonObject();
    }

    private static Optional<net.puffish.skillsmod.api.json.JsonElement> optional(ConfigReader reader, String id, String file) {
        Path path = Path.of("categories", id, file);
        return reader.exists(path) ? Optional.of(reader.read(path).getSuccess().orElseThrow()) : Optional.empty();
    }

    private static JsonObject readLayout(ResourceManager resources) throws Exception {
        Resource resource = resources.getResource(LAYOUT).orElseThrow(() -> new IllegalStateException(LAYOUT + " missing"));
        try (Reader in = resource.openAsReader()) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    private static void addLinks(JsonObject connections, String group, List<String[]> pairs) {
        JsonObject g = connections.has(group) ? connections.getAsJsonObject(group) : new JsonObject();
        JsonArray list = g.has("bidirectional") ? g.getAsJsonArray("bidirectional") : new JsonArray();
        for (String[] pair : pairs) {
            JsonArray a = new JsonArray();
            a.add(PREFIX + pair[0]);
            a.add(PREFIX + pair[1]);
            list.add(a);
        }
        g.add("bidirectional", list);
        connections.add(group, g);
    }

    private static net.puffish.skillsmod.api.json.JsonElement wrap(JsonObject json, String name) {
        return net.puffish.skillsmod.api.json.JsonElement.parseString(json.toString(), JsonPath.create(name)).getSuccess().orElseThrow();
    }
}
