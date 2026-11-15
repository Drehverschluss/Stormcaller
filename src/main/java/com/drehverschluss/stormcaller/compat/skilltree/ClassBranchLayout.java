package com.drehverschluss.stormcaller.compat.skilltree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Place of the Stormcaller branch in a circle of class branches (Class Skills tab of Skill Tree, also in the circles of
 * More RPG Classes - Skill Tree or other add-ons). Pure maths on the JSON of the tab, no Pufferfish's Skills classes.
 * <ul>
 *     <li>{@link #respace}: all branches spread out evenly again, the Stormcaller root on the same circle. Each foreign
 *     branch turns as a whole around the centre (its shape stays), all move outwards along their axis if they get too
 *     close. Needs a regular circle.</li>
 *     <li>{@link #gap}: foreign branches stay, the Stormcaller root moves outwards in a gap between two of them.</li>
 * </ul>
 * Settings and the shape of the branch come from {@code stormcaller:stormcaller_skill_tree/class_branch.json}.
 */
final class ClassBranchLayout {
    private ClassBranchLayout() {}

    /** The tab does not fit {@link #respace} (or nothing fits at all); the message says why. */
    static final class NoPlace extends Exception {
        NoPlace(String message) {
            super(message);
        }
    }

    record Form(String name, double rootRadius, String after, String before, Map<String, double[]> nodes,
                List<String[]> normal, List<String[]> exclusive) {}

    record Settings(double minNode, double minLine, int maxShift, int shiftStep, double radiusTolerance, double angleTolerance,
                    int gapMaxRootRadius, int gapStep, List<Form> forms) {}

    /**
     * @param ours        positions of our nodes (by node id of the form)
     * @param moved       new positions of foreign nodes (empty for gap)
     * @param description for the log
     */
    record Placement(Map<String, int[]> ours, Map<String, int[]> moved, String description) {}

    private record Node(String id, String definition, double x, double y, boolean root) {}

    // ------------------------------------------------------------------ settings

    static Settings parse(JsonObject json) {
        JsonObject respace = json.getAsJsonObject("respace");
        JsonObject gap = json.getAsJsonObject("gap");
        List<Form> forms = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("forms")) {
            JsonObject f = e.getAsJsonObject();
            Map<String, double[]> nodes = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> n : f.getAsJsonObject("nodes").entrySet()) {
                JsonArray a = n.getValue().getAsJsonArray();
                nodes.put(n.getKey(), new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
            }
            if (!nodes.containsKey("root")) {
                throw new IllegalArgumentException("form " + f.get("name").getAsString() + " has no node 'root'");
            }
            JsonObject links = f.getAsJsonObject("links");
            JsonObject place = f.getAsJsonObject("place");
            forms.add(new Form(f.get("name").getAsString(), f.get("root_radius").getAsDouble(),
                    place != null && place.has("after") ? place.get("after").getAsString() : null,
                    place != null && place.has("before") ? place.get("before").getAsString() : null,
                    nodes, pairs(links.getAsJsonArray("normal"), nodes), pairs(links.getAsJsonArray("exclusive"), nodes)));
        }
        if (forms.isEmpty()) {
            throw new IllegalArgumentException("no forms");
        }
        return new Settings(json.get("min_node_distance").getAsDouble(), json.get("min_line_distance").getAsDouble(),
                respace.get("max_shift").getAsInt(), Math.max(1, respace.get("shift_step").getAsInt()),
                respace.get("radius_tolerance").getAsDouble(), respace.get("angle_tolerance").getAsDouble(),
                gap.get("max_root_radius").getAsInt(), Math.max(1, gap.get("step").getAsInt()), forms);
    }

    private static List<String[]> pairs(JsonArray array, Map<String, double[]> nodes) {
        List<String[]> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (JsonElement e : array) {
            JsonArray p = e.getAsJsonArray();
            String a = p.get(0).getAsString();
            String b = p.get(1).getAsString();
            if (!nodes.containsKey(a) || !nodes.containsKey(b)) {
                throw new IllegalArgumentException("link to an unknown node: " + a + " - " + b);
            }
            out.add(new String[]{a, b});
        }
        return out;
    }

    /** The form made for a circle of this size (closest root radius). */
    static Form chooseForm(Settings settings, JsonObject skills) {
        List<Node> roots = nodes(skills).stream().filter(Node::root).toList();
        double radius = roots.stream().mapToDouble(r -> Math.hypot(r.x, r.y)).average().orElse(0);
        Form best = settings.forms().get(0);
        for (Form f : settings.forms()) {
            if (Math.abs(f.rootRadius() - radius) < Math.abs(best.rootRadius() - radius)) {
                best = f;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ respace

    static Placement respace(JsonObject skills, JsonObject connections, Settings settings, Form form) throws NoPlace {
        List<Node> all = nodes(skills);
        List<Node> roots = new ArrayList<>(all.stream().filter(Node::root).toList());
        if (roots.size() < 2) {
            throw new NoPlace("fewer than two roots");
        }
        roots.sort((a, b) -> Double.compare(angle(a), angle(b)));
        double radius = roots.stream().mapToDouble(r -> Math.hypot(r.x, r.y)).average().orElseThrow();
        Node off = roots.stream().max((a, b) -> Double.compare(Math.abs(Math.hypot(a.x, a.y) - radius),
                Math.abs(Math.hypot(b.x, b.y) - radius))).orElseThrow();
        if (Math.abs(Math.hypot(off.x, off.y) - radius) > settings.radiusTolerance()) {
            throw new NoPlace("root " + off.definition + " is not on the circle (radius " + fmt(Math.hypot(off.x, off.y)) + ", the others "
                    + "about " + fmt(radius) + ")");
        }
        double expected = 360.0 / roots.size();
        for (int i = 0; i < roots.size(); i++) {
            double step = norm360(angle(roots.get((i + 1) % roots.size())) - angle(roots.get(i)));
            if (Math.abs(step - expected) > settings.angleTolerance()) {
                throw new NoPlace("angle between " + roots.get(i).definition + " and " + roots.get((i + 1) % roots.size()).definition
                        + " is " + fmt(step) + " instead of " + fmt(expected) + " deg");
            }
        }
        // branches: connected nodes, exactly one root each, nothing left over
        Map<String, Integer> branchOf = branches(skills, connections);
        Map<Integer, List<Node>> members = new HashMap<>();
        for (Node n : all) {
            members.computeIfAbsent(branchOf.get(n.id), k -> new ArrayList<>()).add(n);
        }
        Map<String, List<Node>> branchOfRoot = new HashMap<>();
        for (List<Node> branch : members.values()) {
            List<Node> rs = branch.stream().filter(Node::root).toList();
            if (rs.size() != 1) {
                throw new NoPlace(rs.isEmpty() ? "nodes without a root (" + branch.get(0).id + ")"
                        : "two roots in one branch (" + rs.get(0).definition + ", " + rs.get(1).definition + ")");
            }
            branchOfRoot.put(rs.get(0).id, branch);
        }
        List<String[]> foreignPairs = linePairs(skills, connections);

        int insert = insertIndex(roots, form);
        int count = roots.size() + 1;
        double step = 2 * Math.PI / count;
        // new index per foreign root (ours at 'insert'); offset so that the foreign branches turn as little as possible
        int[] index = new int[roots.size()];
        double sin = 0;
        double cos = 0;
        for (int i = 0; i < roots.size(); i++) {
            index[i] = i < insert ? i : i + 1;
            double delta = Math.toRadians(angle(roots.get(i))) - index[i] * step;
            sin += Math.sin(delta);
            cos += Math.cos(delta);
        }
        double offset = Math.atan2(sin, cos);
        double ourAngle = offset + insert * step;

        for (int shift = 0; shift <= settings.maxShift(); shift += settings.shiftStep()) {
            List<double[][]> branchPoints = new ArrayList<>();
            List<List<double[]>> branchLines = new ArrayList<>();
            Map<String, double[]> moved = new HashMap<>();
            for (int i = 0; i < roots.size(); i++) {
                Node root = roots.get(i);
                double newAngle = offset + index[i] * step;
                double turn = newAngle - Math.toRadians(angle(root));
                double ox = shift * Math.cos(newAngle);
                double oy = shift * Math.sin(newAngle);
                List<Node> branch = branchOfRoot.get(root.id);
                double[][] pts = new double[branch.size()][];
                for (int k = 0; k < branch.size(); k++) {
                    Node n = branch.get(k);
                    pts[k] = new double[]{n.x * Math.cos(turn) - n.y * Math.sin(turn) + ox, n.x * Math.sin(turn) + n.y * Math.cos(turn) + oy};
                    moved.put(n.id, pts[k]);
                }
                branchPoints.add(pts);
            }
            // lines of the foreign branches at their new places
            for (int i = 0; i < roots.size(); i++) {
                branchLines.add(new ArrayList<>());
            }
            Map<String, Integer> rootIndex = new HashMap<>();
            for (int i = 0; i < roots.size(); i++) {
                for (Node n : branchOfRoot.get(roots.get(i).id)) {
                    rootIndex.put(n.id, i);
                }
            }
            for (String[] pair : foreignPairs) {
                double[] a = moved.get(pair[0]);
                double[] b = moved.get(pair[1]);
                Integer bi = rootIndex.get(pair[0]);
                if (a != null && b != null && bi != null) {
                    branchLines.get(bi).add(new double[]{a[0], a[1], b[0], b[1]});
                }
            }
            Map<String, int[]> ours = place(form, ourAngle, radius + shift);
            branchPoints.add(toPoints(ours));
            branchLines.add(ourLines(form, ours));
            double[] min = minBetween(branchPoints, branchLines, settings);
            if (min[0] >= settings.minNode() && min[1] >= settings.minLine()) {
                Map<String, int[]> rounded = new HashMap<>();
                moved.forEach((id, p) -> rounded.put(id, new int[]{(int) Math.round(p[0]), (int) Math.round(p[1])}));
                String before = roots.get((insert - 1 + roots.size()) % roots.size()).definition;
                String after = roots.get(insert % roots.size()).definition;
                return new Placement(ours, rounded, String.format(
                        "layout respace (form %s): %d branches every %s deg, moved out %d px, between %s and %s, closest branches %s px",
                        form.name(), count, fmt(360.0 / count), shift, before, after, fmt(min[0])));
            }
        }
        throw new NoPlace("not enough room up to max_shift " + settings.maxShift() + " px");
    }

    /** Index in the sorted root list where our branch goes: after 'after', ideally right before 'before'. */
    private static int insertIndex(List<Node> roots, Form form) {
        int after = -1;
        int before = -1;
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).definition.equals(form.after())) {
                after = i;
            }
            if (roots.get(i).definition.equals(form.before())) {
                before = i;
            }
        }
        if (after >= 0 && (before < 0 || (after + 1) % roots.size() == before)) {
            return after + 1;
        }
        if (before >= 0) {
            return before;
        }
        if (after >= 0) {
            return after + 1;
        }
        return roots.size();
    }

    // ------------------------------------------------------------------ gap

    static Placement gap(JsonObject skills, JsonObject connections, Settings settings, Form form) throws NoPlace {
        List<Node> all = nodes(skills);
        List<Node> roots = new ArrayList<>(all.stream().filter(Node::root).toList());
        if (roots.size() < 2) {
            throw new NoPlace("fewer than two roots");
        }
        roots.sort((a, b) -> Double.compare(angle(a), angle(b)));
        double minRadius = roots.stream().mapToDouble(r -> Math.hypot(r.x, r.y)).min().orElseThrow();
        List<double[]> lines = lines(skills, connections);
        int insert = insertIndex(roots, form);
        // gaps: the preferred one first, then by size
        List<double[]> gaps = new ArrayList<>(); // {middle angle, width, preferred, index of the gap}
        for (int i = 0; i < roots.size(); i++) {
            double from = angle(roots.get(i));
            double width = norm360(angle(roots.get((i + 1) % roots.size())) - from);
            gaps.add(new double[]{Math.toRadians(from + width / 2), width, (i + 1) % roots.size() == insert % roots.size() ? 1 : 0, i});
        }
        gaps.sort((a, b) -> a[2] != b[2] ? Double.compare(b[2], a[2]) : Double.compare(b[1], a[1]));
        for (double[] g : gaps) {
            for (int r0 = (int) Math.round(minRadius); r0 <= settings.gapMaxRootRadius(); r0 += settings.gapStep()) {
                Map<String, int[]> ours = place(form, g[0], r0);
                double minNode = Double.MAX_VALUE;
                double minLine = Double.MAX_VALUE;
                for (int[] p : ours.values()) {
                    for (Node n : all) {
                        minNode = Math.min(minNode, Math.hypot(p[0] - n.x, p[1] - n.y));
                    }
                    for (double[] l : lines) {
                        minLine = Math.min(minLine, segment(p[0], p[1], l));
                    }
                }
                if (minNode >= settings.minNode() && minLine >= settings.minLine()) {
                    int i = (int) g[3];
                    return new Placement(ours, Map.of(), String.format(
                            "layout gap (form %s): between %s and %s, root radius %d, closest foreign node %s px",
                            form.name(), roots.get(i).definition, roots.get((i + 1) % roots.size()).definition, r0, fmt(minNode)));
                }
            }
        }
        throw new NoPlace("no gap with room up to root radius " + settings.gapMaxRootRadius());
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, int[]> place(Form form, double angle, double rootRadius) {
        Map<String, int[]> out = new LinkedHashMap<>();
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        for (Map.Entry<String, double[]> e : form.nodes().entrySet()) {
            double r = rootRadius + e.getValue()[0];
            double side = e.getValue()[1];
            out.put(e.getKey(), new int[]{(int) Math.round(r * c - side * s), (int) Math.round(r * s + side * c)});
        }
        return out;
    }

    private static double[][] toPoints(Map<String, int[]> ours) {
        double[][] pts = new double[ours.size()][];
        int i = 0;
        for (int[] p : ours.values()) {
            pts[i++] = new double[]{p[0], p[1]};
        }
        return pts;
    }

    private static List<double[]> ourLines(Form form, Map<String, int[]> ours) {
        List<double[]> out = new ArrayList<>();
        for (List<String[]> group : List.of(form.normal(), form.exclusive())) {
            for (String[] p : group) {
                int[] a = ours.get(p[0]);
                int[] b = ours.get(p[1]);
                out.add(new double[]{a[0], a[1], b[0], b[1]});
            }
        }
        return out;
    }

    /** {smallest node distance, smallest node-to-line distance} between nodes of different branches. */
    private static double[] minBetween(List<double[][]> points, List<List<double[]>> lines, Settings settings) {
        int n = points.size();
        double[][] box = new double[n][];
        for (int i = 0; i < n; i++) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] p : points.get(i)) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            box[i] = new double[]{minX, minY, maxX, maxY};
        }
        double reach = Math.max(settings.minNode(), settings.minLine()) * 3;
        double minNode = Double.MAX_VALUE;
        double minLine = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (box[i][0] - reach > box[j][2] || box[j][0] - reach > box[i][2] || box[i][1] - reach > box[j][3]
                        || box[j][1] - reach > box[i][3]) {
                    continue;
                }
                for (double[] a : points.get(i)) {
                    for (double[] b : points.get(j)) {
                        minNode = Math.min(minNode, Math.hypot(a[0] - b[0], a[1] - b[1]));
                    }
                    for (double[] l : lines.get(j)) {
                        minLine = Math.min(minLine, segment(a[0], a[1], l));
                    }
                }
                for (double[] b : points.get(j)) {
                    for (double[] l : lines.get(i)) {
                        minLine = Math.min(minLine, segment(b[0], b[1], l));
                    }
                }
            }
        }
        return new double[]{minNode, minLine};
    }

    private static List<Node> nodes(JsonObject skills) {
        List<Node> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : skills.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            out.add(new Node(e.getKey(), o.has("definition") ? o.get("definition").getAsString() : "?", o.get("x").getAsDouble(),
                    o.get("y").getAsDouble(), o.has("root") && o.get("root").getAsBoolean()));
        }
        return out;
    }

    /** All connections of the tab as id pairs (normal and exclusive, both directions kinds). */
    private static List<String[]> linePairs(JsonObject skills, JsonObject connections) {
        List<String[]> out = new ArrayList<>();
        for (String group : new String[]{"normal", "exclusive"}) {
            if (!connections.has(group) || !connections.get(group).isJsonObject()) {
                continue;
            }
            JsonObject g = connections.getAsJsonObject(group);
            for (String kind : new String[]{"bidirectional", "unidirectional"}) {
                if (!g.has(kind)) {
                    continue;
                }
                for (JsonElement e : g.getAsJsonArray(kind)) {
                    JsonArray p = e.getAsJsonArray();
                    String a = p.get(0).getAsString();
                    String b = p.get(1).getAsString();
                    if (skills.has(a) && skills.has(b)) {
                        out.add(new String[]{a, b});
                    }
                }
            }
        }
        return out;
    }

    private static List<double[]> lines(JsonObject skills, JsonObject connections) {
        List<double[]> out = new ArrayList<>();
        for (String[] p : linePairs(skills, connections)) {
            JsonObject a = skills.getAsJsonObject(p[0]);
            JsonObject b = skills.getAsJsonObject(p[1]);
            out.add(new double[]{a.get("x").getAsDouble(), a.get("y").getAsDouble(), b.get("x").getAsDouble(), b.get("y").getAsDouble()});
        }
        return out;
    }

    /** Union-find over all connections: node id -> branch number. */
    private static Map<String, Integer> branches(JsonObject skills, JsonObject connections) {
        Map<String, String> parent = new HashMap<>();
        for (String id : skills.keySet()) {
            parent.put(id, id);
        }
        for (String[] p : linePairs(skills, connections)) {
            String a = find(parent, p[0]);
            String b = find(parent, p[1]);
            if (!a.equals(b)) {
                parent.put(a, b);
            }
        }
        Map<String, Integer> number = new HashMap<>();
        Map<String, Integer> out = new HashMap<>();
        for (String id : skills.keySet()) {
            out.put(id, number.computeIfAbsent(find(parent, id), k -> number.size()));
        }
        return out;
    }

    private static String find(Map<String, String> parent, String id) {
        String r = id;
        while (!parent.get(r).equals(r)) {
            r = parent.get(r);
        }
        String c = id;
        while (!parent.get(c).equals(r)) {
            String next = parent.get(c);
            parent.put(c, r);
            c = next;
        }
        return r;
    }

    private static double angle(Node n) {
        return norm360(Math.toDegrees(Math.atan2(n.y, n.x)));
    }

    private static double norm360(double deg) {
        double d = deg % 360;
        return d < 0 ? d + 360 : d;
    }

    private static double segment(double px, double py, double[] l) {
        double dx = l[2] - l[0];
        double dy = l[3] - l[1];
        double len = dx * dx + dy * dy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - l[0]) * dx + (py - l[1]) * dy) / len));
        return Math.hypot(px - l[0] - t * dx, py - l[1] - t * dy);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
