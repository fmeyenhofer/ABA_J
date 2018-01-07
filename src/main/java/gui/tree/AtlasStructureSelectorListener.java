package gui.tree;

import rest.AtlasStructure;

import java.util.HashMap;

/**
 * @author Felix Meyenhofer
 */
public interface AtlasStructureSelectorListener {

    void valueChanged(HashMap<Integer, AtlasStructure> structures);

    void importAction(HashMap<Integer, AtlasStructure> structures, String type);

}
