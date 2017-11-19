package rest;

import org.jdom.Element;
import java.util.ArrayList;

/**
 * Class to hold the metadata attributes for the atlas annotation structures
 * Per convention, a structure without a parent, has a parent-ID of -1
 *
 * @author Felix Meyenhofer
 */
public class AtlasStructure {

    private int id;
    private Integer depth;
    private String acronym;
    private String name;
    private ArrayList<Integer> structurePath;
    private String color;

    /**
     * Constructor
     *
     * @param id of the structure
     * @param depth in the hierarchy (or hierarchy level)
     * @param acronym aka abbreviation
     * @param name explicit name or description of the structure
     * @param path path through the tree -> list of ID's of the parent structures
     * @param color color (from ABA)
     */
    public AtlasStructure(String id, String depth, String acronym, String name, String path, String color) {
        setId(id);
        setDepth(depth);
        setAcronym(acronym);
        setName(name);
        setStructurePath(parseStructurePath(path));
        setColor(color);
    }

    public AtlasStructure(Element element) {
        this(element.getChild("id").getValue(),
                element.getChild("depth").getValue(),
                element.getChild("acronym").getValue(),
                element.getChild("safe-name").getValue(),
                element.getChild("structure-id-path").getValue(),
                element.getChild("color-hex-triplet").getValue());
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
    public int getParentId() {
        int parent = -1;
        ArrayList<Integer> path = getStructurePath();

        if (path.size() == 1) {
            parent = path.get(0);
            if (parent == getId()) {
                parent = -1;
            }
        } else if (path.size() > 1) {
            int index = path.size() - 2;
            parent = path.get(index);
        }

        return parent;
    }

    public int getId() {
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

    public ArrayList<Integer> getStructurePath() {
        return structurePath;
    }

    private void setStructurePath(ArrayList<Integer> structurePath) {
        this.structurePath = structurePath;
    }

    public String getColor() {
        return color;
    }

    private void setColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return this.getAcronym() + " (" + this.getName() + ")";
    }
}
