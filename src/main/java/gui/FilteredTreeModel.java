package gui;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * TODO: customize icons
 *
 * @author Adrian Walker (adrian.walker@bcs.org)
 * modified by Feilx Meyenhofer
 */
public final class FilteredTreeModel implements TreeModel {

    private TreeModel treeModel;
    private String filter;

    public FilteredTreeModel(final TreeModel treeModel) {
        this.treeModel = treeModel;
        this.filter = "";
    }

    public TreeModel getTreeModel() {
        return treeModel;
    }

    public int getNodeCount(Object node) {
        int count = 1;

        int childCount = getChildCount(node);
        for (int c = 0; c < childCount; c++) {
            Object child = getChild(node, c);
            count += getNodeCount(child);
        }

        return count;
    }

    public void setFilter(final String filter) {
        this.filter = filter;
    }

    private boolean recursiveMatch(final Object node, final String filter) {

        boolean matches = filter.isEmpty() || node.toString().toLowerCase().matches(filter);

        int childCount = treeModel.getChildCount(node);
        for (int i = 0; i < childCount; i++) {
            Object child = treeModel.getChild(node, i);
            matches |= recursiveMatch(child, filter);
        }

        return matches;
    }

    @Override
    public Object getRoot() {
        return treeModel.getRoot();
    }

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

    @Override
    public boolean isLeaf(final Object node) {
        return treeModel.isLeaf(node);
    }

    @Override
    public void valueForPathChanged(final TreePath path, final Object newValue) {
        treeModel.valueForPathChanged(path, newValue);
    }

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

    @Override
    public void addTreeModelListener(final TreeModelListener l) {
        treeModel.addTreeModelListener(l);
    }

    @Override
    public void removeTreeModelListener(final TreeModelListener l) {
        treeModel.removeTreeModelListener(l);
    }
}
