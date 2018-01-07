package rest;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class regrouping some graph (structure hierarchy) related manipulations
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("WeakerAccess")
public class AtlasStructureGraph {

    private HashMap<Integer, AtlasStructure> graph;

    AtlasStructureGraph(HashMap<Integer, AtlasStructure> graph) {
        this.graph = graph;
    }

    public AtlasStructure getStructureByName(String name) {
        for (int id : graph.keySet()) {
            AtlasStructure structure = graph.get(id);
            if (structure.getName().equals(name)) {
                return structure;
            }
        }

        return null;
    }

    public HashMap<Integer, AtlasStructure> getGraph() {
        return this.graph;
    }

    public int size() {
        return this.graph.size();
    }

    public int[] initialize(HashMap<Double, List<float[]>> contours) {
        Set<Integer> ids = graph.keySet();
        Set<Integer> parents = new HashSet<>();
        int found = 0;
        int notFound = 0;
        for (double id : contours.keySet()) {
            int iid = (int) id;

            if (ids.contains(iid)) {
                AtlasStructure structure = graph.get(iid);
                structure.setActivated(true);
                structure.setContourCoordinates(contours.get(id));
                parents.addAll(structure.getParentIds());
                found++;
            } else if (iid != 0) {
                AtlasStructure structure = new AtlasStructure("" + iid,
                        "1",
                        "id = " + iid,
                        "",
                        "/-2/" + iid + "/",
                        "0f0f0f",
                        true);
                structure.setContourCoordinates(contours.get(id));
                graph.put(structure.getId(), structure);
                notFound++;
            }
        }

        // Special case root node; use the background contour
        if (contours.containsKey(0.0)) {
            AtlasStructure structure = getStructureByName("root");
            structure.setActivated(true);
            structure.setContourCoordinates(contours.get(0.0));
            structure.setColor(new Color(0xC2938E));
            found++;
        }

        // Update parents
        for (int parent : parents) {
            graph.get(parent).setHasActiveChildren(true);
        }

        // Add an artificial structure that can serve as parent to orphans
        if (notFound > 0) {
            AtlasStructure structure = new AtlasStructure("-2" ,
                    "1",
                    "orphans",
                    "not found in structure graph",
                    "/-2/",
                    "0f0f0f",
                    true);
            graph.put(-2, structure);
        }

        return new int[]{found, notFound};
    }
}
