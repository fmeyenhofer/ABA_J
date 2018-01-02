package gui.tree;

import rest.AtlasStructure;

import java.util.HashMap;

/**
 * @author Felix Meyenhofer
 */
public interface AtlasStructureSelectionListener {

    void valueChanged(HashMap<Integer, AtlasStructure> structures);

}
