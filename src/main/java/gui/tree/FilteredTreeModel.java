package gui.tree;

import rest.AtlasStructure;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.JTree;

/**
 * TreeModel, that allows interactive node filtering.
 * The {@link FilteredTreeModel} is inserted between the {@link JTree}
 * and the {@link TreeModel}.
 * It dynamically returns only nodes that match the {@link FilteredTreeModel#filter}
 * or nodes that have a child that matches it.
 *
 * @author Adrian Walker (adrian.walker@bcs.org)
 * modified by Feilx Meyenhofer
 */
@SuppressWarnings("WeakerAccess")
public final class FilteredTreeModel implements TreeModel {

    private TreeModel treeModel;
    private String filter;
    private boolean activeOnly = true;

    public FilteredTreeModel(final TreeModel treeModel) {
        this.treeModel = treeModel;
        this.filter = "";
    }

    /**
     * Get the tree model.
     * (This class is inserting itself between JTree and TreeModel)
     *
     * @return {@link TreeModel}
     */
    public TreeModel getTreeModel() {
        return treeModel;
    }

    /**
     * Get the count of the currently shown nodes (all nodes minus the filtered ones)
     *
     * @param node tree node
     * @return count of currently shown nodes
     */
    int getNodeCount(Object node) {
        int count = 1;

        int childCount = getChildCount(node);
        for (int c = 0; c < childCount; c++) {
            Object child = getChild(node, c);
            count += getNodeCount(child);
        }

        return count;
    }

    /**
     * Set the flag to show only active nodes
     *
     * @param status false -> show all nodes, true -> show only nodes with active children
     */
    public void setActiveOnly(boolean status) {
        this.activeOnly = status;
    }

    /**
     * Set the filter string
     *
     * @param filter string
     */
    public void setFilter(final String filter) {
        this.filter = filter;
    }

    /**
     * Find the nodes whose name matches the filter string, or if
     * the node contains a child matching the string.
     *
     * @param node tree node
     * @param filter string
     * @return true for a match and false for no match.
     */
    private boolean recursiveMatch(final Object node, final String filter) {
        if (activeOnly) {
            AtlasStructure structure = (AtlasStructure)((DefaultMutableTreeNode)node).getUserObject();
            if (!structure.hasActiveChildren() && !structure.isActivated()) {
                return false;
            }
        }

        boolean matches = filter.isEmpty() || node.toString().toLowerCase().matches(filter);

        int childCount = treeModel.getChildCount(node);
        for (int i = 0; i < childCount; i++) {
            Object child = treeModel.getChild(node, i);
            matches |= recursiveMatch(child, filter);
        }

        return matches;
    }

    /** {@inheritDoc} */
    @Override
    public Object getRoot() {
        return treeModel.getRoot();
    }

    /** {@inheritDoc} */
    @Override
    public Object getChild(final Object parent, final int index) {
        int count = 0;
        int childCount = treeModel.getChildCount(parent);
        for (int i = 0; i < childCount; i++) {
            Object child = treeModel.getChild(parent, i);
            if (recursiveMatch(child, filter)) {
                if (count == index) {
                    return child;
                }
                count++;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public int getChildCount(final Object parent) {
        int count = 0;
        int childCount = treeModel.getChildCount(parent);
        for (int i = 0; i < childCount; i++) {
            Object child = treeModel.getChild(parent, i);
            if (recursiveMatch(child, filter)) {
                count++;
            }
        }
        return count;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLeaf(final Object node) {
        return treeModel.isLeaf(node);
    }

    /** {@inheritDoc} */
    @Override
    public void valueForPathChanged(final TreePath path, final Object newValue) {
        treeModel.valueForPathChanged(path, newValue);
    }

    /** {@inheritDoc} */
    @Override
    public int getIndexOfChild(final Object parent, final Object childToFind) {
        int childCount = treeModel.getChildCount(parent);
        for (int i = 0; i < childCount; i++) {
            Object child = treeModel.getChild(parent, i);
            if (recursiveMatch(child, filter)) {
                if (childToFind.equals(child)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public void addTreeModelListener(final TreeModelListener l) {
        treeModel.addTreeModelListener(l);
    }

    /** {@inheritDoc} */
    @Override
    public void removeTreeModelListener(final TreeModelListener l) {
        treeModel.removeTreeModelListener(l);
    }
}
