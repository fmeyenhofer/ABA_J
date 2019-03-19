package gui.tree;

import rest.AllenClient;
import rest.AllenXml;
import rest.AtlasStructure;

import org.jdom2.Element;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import javax.xml.transform.TransformerException;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;

/**
 * Dialog to browse, filter and select structures of a hierarchical ontology.
 * The multi-selection (cmd, ctl and shift-click) is persistent between different filters. It is reset with a click
 * without modifiers or with a dedicated button.
 *
 *
 * Note: To make the filtering of the tree possible, a custom {@link TreeModel} was needed.
 * This was implemented in the {@link FilteredTreeModel}. Now the tricky thing is that the {@link JTree}
 * actually has two models; the {@link FilteredTreeModel} that encapsulates the {@link DefaultTreeModel}
 * (see {@link FilteredTreeModel#getTreeModel()}. The {@link DefaultTreeModel} contains always all the nodes of the
 * entire input hierarchy. The {@link FilteredTreeModel} between the {@link JTree} and the {@link DefaultTreeModel}
 * then uses a String filter to dynamically only return the visible nodes to the {@link JTree} UI.
 *
 * Since {@link DefaultTreeModel#reload()}, that is needed to render the newly filtered tree, clears the selection,
 * the {@link AtlasStructureSelector} keeps its own record of selected nodes with the
 * {@link AtlasStructureSelector#selection} hashmap. So {@link AtlasStructureSelector#filterAndUpdateTree()}
 * reapplies the selection once the filtering is done.
 *
 * @author Felix Meyenhofer
 *
 */
public class AtlasStructureSelector extends JPanel implements ActionListener {

    /** Filter text input field tooltip */
    private static final String FILTER_TOOLTIP ="Search for a structure using a wildcard pattern (*)";

    /** Status text */
    private JLabel status;

    /** Store for the tree size */
    private final int treeSize;

    /** Structure filter field */
    private JTextField filter;

    /** UI component to visualize the hierarchy tree */
    private JTree tree;

    /** Keep a selection index for all structures (persistence despite tree filtering) */
    private HashMap<Integer, Boolean> selection;

    /** Store the input hierarchy graph */
    private HashMap<Integer, AtlasStructure> graph;

    /** Register of the selection listeners */
    private List<AtlasStructureSelectorListener> listeners = new ArrayList<>();
    private final JButton active;


    /**
     * Constructor
     * 
     * Initializes the UI.
     *
     * @param hierarchy to create the tree selector for
     */
    private AtlasStructureSelector(HashMap<Integer, AtlasStructure> hierarchy) {
        this.graph = hierarchy;

        // Initialize selection
        this.selection = new HashMap<>();
        for (Integer id : hierarchy.keySet()) {
            this.selection.put(id, false);
        }

        // Create the UI element for the tree
        this.tree = createTree(hierarchy);
        this.treeSize = getNodeCount();

        // Make the tree scrollable
        JScrollPane treePanel = new JScrollPane(this.tree);

        // Setup the search field
        this.filter = new JTextField();
        this.filter.setToolTipText(FILTER_TOOLTIP);
        this.filter.getDocument().addDocumentListener(new SearchDocumentListener(this));
        
        // Selection reset button
        JButton reset = new JButton("reset");
        reset.setToolTipText("Reset the selection");
        reset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetSelectedNodes();
            }
        });

        JButton expand = new JButton("<|>");
        expand.setToolTipText("Expand all nodes");
        expand.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                expandTree();
            }
        });

        JButton collapse = new JButton(">|<");
        collapse.setToolTipText("Collapse all nodes");
        collapse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                collapseTree();
            }
        });

        active = new JButton("active");
        active.setToolTipText("toggle visibility: active nodes / all nodes");
        active.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleVisibility();
            }
        });

        // Assemble top panel
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
        searchPanel.add(new JLabel("Filter:"), new GridBagConstraints(0,0,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0,0,0, 0),
                10, 0));
        searchPanel.add(filter, new GridBagConstraints(1,0,
                4,1,
                1,0,
                GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0,0,0, 0),
                0, 0));
        searchPanel.add(active, new GridBagConstraints(1,1,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));
        searchPanel.add(expand, new GridBagConstraints(2,1,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));
        searchPanel.add(collapse, new GridBagConstraints(3,1,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));
        searchPanel.add(reset, new GridBagConstraints(4,1,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));

        // Status panel
        this.status = new JLabel();
        this.status.setForeground(Color.darkGray);
        this.updateStatus();
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(new EmptyBorder(5, 0,0,0));
        statusPanel.add(new JPanel(), new GridBagConstraints(0,0,
                1,1,
                1,0,
                GridBagConstraints.WEST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));
        statusPanel.add(this.status, new GridBagConstraints(1,0,
                1,1,
                1,0,
                GridBagConstraints.EAST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));

        // Import Button
        JComboBox<String> importType = new JComboBox<>(new String[]{"ROI", "Mask"});
        JButton importButton = new JButton("import");
        importButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                notifyImportListeners((String) importType.getSelectedItem());
            }
        });
        JPanel importPanel = new JPanel(new GridBagLayout());
        importPanel.setBorder(new EmptyBorder(5, 0,0,0));
        importPanel.add(new JPanel(), new GridBagConstraints(0,0,
                2,1,
                1,1,
                GridBagConstraints.WEST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));
        importPanel.add(importType, new GridBagConstraints(3,0,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0,0,0, 0),
                0, 0));
        importPanel.add(importButton, new GridBagConstraints(4,0,
                1,1,
                0,0,
                GridBagConstraints.EAST,
                GridBagConstraints.NONE,
                new Insets(0,0,0, 0),
                0, 0));

        // Assemble dialog
        this.setLayout(new GridBagLayout());
        this.setBorder(new EmptyBorder(10, 10, 10, 10));
        this.add(searchPanel, new GridBagConstraints(0,0,1,1,
                0,0,
                GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL,
                new Insets(0,0,0, 0),
                0, 0));
        this.add(treePanel, new GridBagConstraints(0,1,1,1,
                1,1,
                GridBagConstraints.CENTER,
                GridBagConstraints.BOTH,
                new Insets(0,0,0, 0),
                0, 0));
        this.add(statusPanel, new GridBagConstraints(0,2,1,1,
                1,0,
                GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL,
                new Insets(0,0,0, 0),
                0, 0));
        this.add(importPanel, new GridBagConstraints(0,3,1,1,
                0,0,
                GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL,
                new Insets(0,0,0, 0),
                0, 0));
    }

    /**
     * Create the dialog and return the window
     *
     * @param hierarchy to create a tree selector from
     * @return dialog window
     */
    public static AtlasStructureSelector createAndShowDialog(HashMap<Integer, AtlasStructure> hierarchy) {
        AtlasStructureSelector tree = new AtlasStructureSelector(hierarchy);
        ToolTipManager.sharedInstance().registerComponent(tree);

        JDialog dialog = new JDialog();
        dialog.setTitle("Atlas Structure selection");
        dialog.add(tree);
//        dialog.setModal(true);
        dialog.setSize(new Dimension(300, 250));
        dialog.setLocationByPlatform(true);
        dialog.setVisible(true);
        dialog.pack();

        return tree;
    }

    /**
     * Get the currently selected structures.
     *
     * @return selected structures
     */
    private HashMap<Integer, AtlasStructure> getSelectedStructures() {
        HashMap<Integer, AtlasStructure> structures = new HashMap<>();
        for (Integer id : this.selection.keySet()) {
            if (this.selection.get(id)) {
                structures.put(id, this.graph.get(id));
            }
        }

        return structures;
    }

    /**
     * Add a selection change listener
     *
     * @param listener selection change listener
     */
    public void addStructureSelectionListener(AtlasStructureSelectorListener listener) {
        this.listeners.add(listener);
    }

    /**
     * Close dialog window
     */
    public void close() {
        ((JDialog)this.getParent()).dispose();
    }

//    /**
//     * Sort the structure objects by their hierarchy depth (level
//     *
//     * @param map id-structure nodes
//     * @return new id-structure nodes sorted by depth
//     */
//    private static HashMap<Integer, AtlasStructure> sortHierarchyByDepth(HashMap<Integer, AtlasStructure> map) {
//        List<Map.Entry<Integer, AtlasStructure>> list = new LinkedList<>(map.entrySet());
//        list.sort(new Comparator<Map.Entry<Integer, AtlasStructure>>() {
//            @Override
//            public int compare(Map.Entry<Integer, AtlasStructure> o1, Map.Entry<Integer, AtlasStructure> o2) {
//                return o1.getValue().getDepth().compareTo(o2.getValue().getDepth());
//            }
//        });
//
//        HashMap<Integer, AtlasStructure> sorted = new LinkedHashMap<>();
//        for (Map.Entry<Integer, AtlasStructure> entry : list) {
//            sorted.put(entry.getKey(), entry.getValue());
//        }
//
//        return sorted;
//    }

    /**
     * Create a JTree from the structure hierarchy
     *
     * @param structures structure tree
     * @return JTree of the hierarchy
     */
    private JTree createTree(HashMap<Integer, AtlasStructure> structures) {
        HashMap<Integer, DefaultMutableTreeNode> nodes = new HashMap<>();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("brain");
        nodes.put(-1, root);

        // Add all nodes and leaves
        for (AtlasStructure structure : structures.values()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(structure);
            nodes.put(structure.getId(), node);
        }

        // Connect the nodes                              
        for (DefaultMutableTreeNode node : nodes.values()) {
            Object obj = node.getUserObject();
            if (obj instanceof AtlasStructure) {
                AtlasStructure structure = (AtlasStructure) obj;
                Integer pid = structure.getParentId();
                nodes.get(pid).add(node);
            }
        }

        // Create the new tree
        FilteredTreeModel model = new FilteredTreeModel(new DefaultTreeModel(root));
        JTree newTree = new JTree(model);
        newTree.setRootVisible(false);
        newTree.addTreeSelectionListener(new AtlasStructureTreeSelectionListener());
        newTree.setCellRenderer(new AtlasStructureTreeCellRenderer());

        // Register the node tooltips of the custom cell renderer
        ToolTipManager.sharedInstance().registerComponent(newTree);

        // Expand the children of the root (first set of nodes, since root is not visible)
        int nChildren = model.getChildCount(root);
        for (int c = 0; c < nChildren; c++) {
            Object child = model.getChild(root, c);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) child;
            if (node != null) {
                TreePath path = new TreePath(node.getPath());
                newTree.expandPath(path);
            }
        }

        return newTree;
    }

    /**
     * Get the filter string from the text input and apply it to the tree.
     */
    private void filterAndUpdateTree() {
        String input = this.filter.getText().trim().toLowerCase();
        String pattern;
        if (input.contains("*")) {
            pattern = input.replace("*", ".*");
        } else {
            pattern = ".*" + input + ".*";
        }

        // Remove the selection listener before the reload() to avoid that valueChanged(TreeSelectionEvent e)
        // is called when the reload just wiped the selection.
        TreeSelectionListener[] listeners = this.tree.getTreeSelectionListeners();
        for (TreeSelectionListener listener : listeners) {
            this.tree.removeTreeSelectionListener(listener);
        }

        // Set the filter and rebuild the tree
        FilteredTreeModel filteredModel = (FilteredTreeModel) this.tree.getModel();
        filteredModel.setFilter(pattern);
        DefaultTreeModel treeModel = (DefaultTreeModel) filteredModel.getTreeModel();
        treeModel.reload();

        // Re-apply the selection (the reload of fired by the tree-model will clear the selection)
        this.applySelectedNodes(filteredModel.getRoot());

        // Add the selection listener back
        for (TreeSelectionListener listener : listeners) {
            this.tree.addTreeSelectionListener(listener);
        }

        if (input.isEmpty()) {
            collapseTree(this.tree, filteredModel.getRoot());
        } else {
            expandTree(this.tree);
        }

        this.updateStatus();
    }

    /**
     * Expand the entire tree
     */
    private void expandTree() {
        expandTree(this.tree);
    }

    /**
     * Expand all nodes containing children (rows)
     *
     * @param tree current tree
     */
    private static void expandTree(final JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    /**
     * Collapse the entire tree
     */
    private void collapseTree() {
        collapseTree(this.tree, this.tree.getModel().getRoot());
    }

    /**
     * Collapse nodes bellow level 2 that do not contain a selected child node.
     *
     * @param tree current tree
     * @param obj root node
     */
    private static void collapseTree(JTree tree, Object obj) {
        TreeModel model = tree.getModel();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) obj;

        if ((node.getLevel() > 1) && !containsSelectedNode(tree, node)) {
            tree.collapsePath(new TreePath(node.getPath()));
        } else {
            int nChildren = model.getChildCount(node);
            for (int c = 0; c < nChildren; c++) {
                Object child = model.getChild(node, c);
                collapseTree(tree, child);
            }
        }
    }

    /**
     * Check if a node contains a selected child node
     * 
     * @param tree current tree
     * @param node node to check for selected child nodes
     * @return binary answer
     */
    private static boolean containsSelectedNode(JTree tree, DefaultMutableTreeNode node) {
        TreeModel model = tree.getModel();

        if (tree.isPathSelected(new TreePath(node.getPath()))) {
            return true;
        }

        for (int c = 0; c < model.getChildCount(node); c++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, c);
            if (containsSelectedNode(tree, child)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Update the status text at the bottom of the dialog
     */
    private void updateStatus() {
        status.setText("Total/displayed/selected structures: " +
                this.treeSize + "/" +
                getNodeCount() + "/" +
                countSelectSelectedNodes());
    }

    /**
     * Get the total node count (including leafs) of the currently displayed tree.
     *
     * @return node count of the current displayed tree
     */
    private int getNodeCount() {
        FilteredTreeModel model = ((FilteredTreeModel) this.tree.getModel());
        return model.getNodeCount(model.getRoot());
    }

    /**
     * Update the selection register
     *
     * @param obj root node
     * @return flag to indicate if something was changed
     */
    private boolean updateSelectedNodes(Object obj) {
        TreeModel model = this.tree.getModel();
        int childCount = model.getChildCount(obj);

        boolean hasChanged = false;
        for (int c = 0; c < childCount; c++) {
            Object child = model.getChild(obj, c);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) child;
            AtlasStructure structure = (AtlasStructure) node.getUserObject();
            TreePath path = new TreePath(node.getPath());
            boolean wasSelected = this.selection.get(structure.getId());
            boolean isSelected = this.tree.isPathSelected(path);
            this.selection.put(structure.getId(), isSelected);

            // recurse
            hasChanged = hasChanged | (wasSelected != isSelected) | updateSelectedNodes(node);
        }

        return hasChanged;
    }

    /**
     * Apply the selection to the current tree.
     *
     * @param obj root node
     */
    private void applySelectedNodes(Object obj) {
        TreeModel model = ((FilteredTreeModel) this.tree.getModel()).getTreeModel();
        int childCount = model.getChildCount(obj);
        for (int c = 0; c < childCount; c++) {
            Object child = model.getChild(obj, c);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) child;
            AtlasStructure structure = (AtlasStructure) node.getUserObject();
            TreePath path = new TreePath(node.getPath());
            if (this.selection.get(structure.getId())) {
                this.tree.addSelectionPath(path);
            } else {
                this.tree.removeSelectionPath(path);
            }

            applySelectedNodes(node);
        }
    }

    /**
     * Reset the selection and collapse tree up to level 2
     */
    private void resetSelectedNodes() {
        for (Integer key : this.selection.keySet()) {
            this.selection.put(key, false);
        }

        this.filter.setText("");
        this.filterAndUpdateTree();
    }

    /**
     * Count the currently selected nodes (also the invisible/filtered ones)
     *
     * @return count of all selected nodes
     */
    private int countSelectSelectedNodes() {
        int count = 0;
        for (Integer key : this.selection.keySet()) {
            if (this.selection.get(key)) {
                count++;
            }
        }

        return count;
    }

    private void toggleVisibility() {
        FilteredTreeModel filteredModel = (FilteredTreeModel) this.tree.getModel();
        switch (active.getText()) {
            case "active":
                active.setText("all");
                filteredModel.setActiveOnly(false);
                break;
            case "all":
                active.setText("active");
                filteredModel.setActiveOnly(true);
                break;
            default:
                throw new RuntimeException("Button text unknown " + active.getText() + ". This should not happen.");
        }
        this.filterAndUpdateTree();
    }

    /**
     * Notify listening classes
     *
     * @param type import type
     */
    private void notifyImportListeners(String type) {
        for (AtlasStructureSelectorListener listener : listeners) {
            listener.importAction(getSelectedStructures(), type);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void actionPerformed(ActionEvent e) {}


    /**
     * Custom cell renderer allowing for tooltips, icons and different font color depending
     * on the underlying atlas structure.
     */
    public class AtlasStructureTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer)
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;

            if (node.getUserObject() instanceof AtlasStructure) {
                AtlasStructure structure = (AtlasStructure) node.getUserObject();

                renderer.setToolTipText("id = " + structure.getId().toString());

                Icon icon = new MonoChromaticIcon(structure.getColor());
                renderer.setIcon(icon);

                if (structure.isActivated()) {
                    renderer.setForeground(new Color(0, 0, 0));
                } else if (structure.hasActiveChildren()) {
                    renderer.setForeground(new Color(90, 90, 90));
                } else {
                    renderer.setForeground(new Color(200, 200, 200));
                }
            }

            return renderer;
        }
    }


    /**
     * Tree selection listener
     */
    private class AtlasStructureTreeSelectionListener implements TreeSelectionListener {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            boolean hasChanged = updateSelectedNodes(tree.getModel().getRoot());
            updateStatus();

            if (hasChanged) {
                for (AtlasStructureSelectorListener listener : listeners) {
                    HashMap<Integer, AtlasStructure> selection = getSelectedStructures();
                    if (selection != null) {
                        listener.valueChanged(selection);
                    }
                }
            }
        }
    }


    /**
     * Document listener for the filter field
     */
    private class SearchDocumentListener implements DocumentListener {

        private AtlasStructureSelector dialog;

        SearchDocumentListener(AtlasStructureSelector dialog) {
            this.dialog = dialog;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            dialog.filterAndUpdateTree();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            dialog.filterAndUpdateTree();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {}
    }


    /**
     * Quick functionality testing
     *
     * @param args inputs
     * @throws TransformerException cannot load xml
     * @throws IOException file problems
     * @throws URISyntaxException problem with the Allen API (server)
     */
    public static void main(String[] args) throws TransformerException, IOException, URISyntaxException {
        AllenClient client = AllenClient.getInstance();
        AllenXml structuresMetadata = client.getAtlasAnnotationMetadata("12");

        // Fetch the data from the xml
        HashMap<Integer, AtlasStructure> graph = new HashMap<>();
        for (Element element : structuresMetadata.getElements()) {
            AtlasStructure structure = new AtlasStructure(element);
            graph.put(structure.getId(), structure);
        }

        AtlasStructureSelector tree = new AtlasStructureSelector(graph);
        JDialog dialog = new JDialog();
        dialog.setTitle("Atlas Structure selection");
        dialog.add(tree);
        dialog.setModal(true);
        dialog.setSize(new Dimension(400, 600));
        dialog.setLocationByPlatform(true);
        dialog.setVisible(true);
        dialog.toFront();
        dialog.pack();
    }
}
