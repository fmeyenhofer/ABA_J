package rest;

import org.jdom.Element;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to hold the metadata attributes for the atlas annotation structures
 * Conventions:
 *      - a structure without a parent, has a parent-ID of -1
 *      - a graph path includes all the nodes from the root node, including the node itself -> len(path) >= 1
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("WeakerAccess")
public class AtlasStructure {

    private Integer id;
    private Integer depth;
    private String acronym;
    private String name;
    private ArrayList<Integer> graphPath;
    private Color color;
    private String svgPath;
    private Boolean active;
    private Boolean hasActiveChildren;
    private List<float[]> contourCoordinates;

    private static String[] attributeNames = new String[]{"id", "depth", "acronym", "name",
            "structure-id-path", "color-hex-triplet"};


    /**
     * Constructor
     *
     * @param id      of the structure
     * @param depth   in the hierarchy (or hierarchy level)
     * @param acronym aka abbreviation
     * @param name    explicit name or description of the structure
     * @param path    path through the tree -> list of ID's of the parent structures
     * @param color   colorHexTriple (from ABA)
     */
    public AtlasStructure(String id, String depth, String acronym, String name, String path, String color, boolean active) {
        setId(id);
        setDepth(depth);
        setAcronym(acronym);
        setName(name);
        setGraphPath(path);
        setColor(color);
        setActivated(active);
    }

    /**
     * Construct a AtlasStructure object from a xml element, containing a ABA Structure
     *
     * @param element xml ABA structure element
     */
    public AtlasStructure(Element element) {
        for (String attributeName : attributeNames) {
            Element child = element.getChild(attributeName);
            if (child != null) {
                String value = child.getValue().replace("\"", "");

                switch (attributeName) {
                    case "id":
                        setId(value);
                        break;
                    case "depth":
                        setDepth(value);
                        break;
                    case "acronym":
                        setAcronym(value);
                        break;
                    case "name":
                        setName(value);
                        break;
                    case "structure-id-path":
                        setGraphPath(value);
                        break;
                    case "color-hex-triplet":
                        setColor(value);
                        break;
                }
            }
        }

        this.setActivated(false);
        this.setHasActiveChildren(false);
    }

    /**
     * Parse the parent structure ID's separated by "/"
     *
     * @param path string of parent ID's
     * @return List of parent ID's
     */
    private ArrayList<Integer> parseStructurePath(String path) {
        ArrayList<Integer> structurePath = new ArrayList<>();
        for (String node : path.split("/")) {
            if (!node.isEmpty()) {
                structurePath.add(Integer.parseInt(node));
            }
        }

        return structurePath;
    }

    /**
     * Get the ID of the parent structure
     *
     * @return parent structure ID or -1 if it has no parent (or itself as parent)
     */
    public Integer getParentId() {
        Integer parent = -1;
        ArrayList<Integer> path = getGraphPath();

        if (path.size() == 1) {
            parent = -1;
        } else if (path.size() > 1) {
            int index = path.size() - 2;
            parent = path.get(index);
        }

        return parent;
    }

    public List<Integer> getParentIds() {
        List<Integer> ids = new ArrayList<>();
        ArrayList<Integer> path = getGraphPath();
        if (path.size() > 1) {
            ids = path.subList(0, path.size() - 1);
        }
        return ids;
    }

    public Boolean hasActiveChildren() {
        return hasActiveChildren;
    }

    public void setHasActiveChildren(Boolean hasActiveChildren) {
        this.hasActiveChildren = hasActiveChildren;
    }

    public List<float[]> getContourCoordinates() {
        return contourCoordinates;
    }

    public void setContourCoordinates(List<float[]> coordinates) {
        this.contourCoordinates = coordinates;
    }

    public void addContouCoordinate(float[] coordinate) {
        this.contourCoordinates.add(coordinate);
    }

    public void setActivated(boolean flag) {
        this.active = flag;
    }

    public boolean isActivated() {
        return this.active;
    }

    public void setSvgPath(String svgPath) {
        this.svgPath = svgPath;
    }

    public String getSvgPath() {
        return this.svgPath;
    }

    public Integer getId() {
        return id;
    }

    public void setId(String id) {
        this.id = Integer.parseInt(id);
    }

    public Integer getDepth() {
        return depth;
    }

    private void setDepth(String depth) {
        this.depth = Integer.parseInt(depth);
    }

    public String getAcronym() {
        return acronym;
    }

    private void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<Integer> getGraphPath() {
        return graphPath;
    }

    void setGraphPath(String graphPath) {
        this.graphPath = parseStructurePath(graphPath);
    }

    public Color getColor() {
        return this.color;
    }

    private void setColor(String colorHexTripple) {
        this.color = Color.decode("#" + colorHexTripple);
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public String toString() {
        String out = this.getAcronym();

        if (!this.getName().isEmpty()) {
            out += " (" + this.getName() + ")";
        }

        return out;
    }
}
