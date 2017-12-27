package cnn;

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.KerasModel;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
public class ImportFlowNetModel {

    private static Logger log = LoggerFactory.getLogger(ImportFlowNetModel.class);

    public static void main(String[] args) throws UnsupportedKerasConfigurationException,
            IOException, InvalidKerasConfigurationException {

        String modelJsonFilename = "PATH TO EXPORTED JSON FILE";
        String weightsHdf5Filename = "PATH TO EXPORTED WEIGHTS HDF5 ARCHIVE";
        String modelHdf5Filename = "PATH TO EXPORTED FULL MODEL HDF5 ARCHIVE";
        boolean enforceTrainingConfig = false;  //Controls whether unsupported training-related configs
        //will throw an exception or just generate a warning.

        /* Import VGG 16 model from separate model config JSON and weights HDF5 files.
         * Will not include loss layer or training configuration.
         */
        // Static helper from KerasModelImport
        ComputationGraph model = KerasModelImport.importKerasModelAndWeights(modelJsonFilename, weightsHdf5Filename, enforceTrainingConfig);


//        MultiLayerNetwork km = org.deeplearning4j.nn.modelimport.keras.Model.importSequentialModel(modelHdf5Filename, weightsHdf5Filename);
//
//
//        // KerasModel builder pattern
//        model = new KerasModel.ModelBuilder()
//                .modelJsonFilename(modelJsonFilename)
//                .weightsHdf5Filename(weightsHdf5Filename)
//                .enforceTrainingConfig(enforceTrainingConfig)
//                .buildModel()
//                .getComputationGraph();
//
//        /* Import VGG 16 model from full model HDF5 file. Includes loss layer, if any. */
//        // Static helper from KerasModelImport
//        model = KerasModelImport.importKerasModelAndWeights(modelHdf5Filename, enforceTrainingConfig);
//
//        // KerasModel builder pattern
//        model = new KerasModel.ModelBuilder()
//                .modelHdf5Filename(modelHdf5Filename)
//                .enforceTrainingConfig(enforceTrainingConfig)
//                .buildModel()
//                .getComputationGraph();
//
//        /* Import VGG 16 model config from model config JSON. Will not include loss
//         * layer or training configuration.
//         */
//        // Static helper from KerasModelImport
//        ComputationGraphConfiguration config = KerasModelImport.importKerasModelConfiguration(modelJsonFilename, enforceTrainingConfig);
//
//        // KerasModel builder pattern
//        config = new KerasModel.ModelBuilder()
//                .modelJsonFilename(modelJsonFilename)
//                .enforceTrainingConfig(enforceTrainingConfig)
//                .buildModel()
//                .getComputationGraphConfiguration();
    }

}
