package cnn;

import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.VGG19;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
public class DeepImageAnalogy {
    public static void main(String[] args) throws IOException {
//        File modelFile = new File(ZooModel.ROOT_CACHE_DIR,"vgg19_imagenet_initialized.zip");


//        Model net;
//        if (modelFile.exists()) {
//            System.out.println("Load model " + modelFile);
//            net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
//        } else {
        System.out.println("Load VGG19 pre-trained on ImageNet");
        ZooModel model = new VGG19();
        Model pretrained= model.initPretrained(PretrainedType.IMAGENET);

        ComputationGraph graph = (ComputationGraph) pretrained;

        for (Layer layer : graph.getLayers()) {
            System.out.println(layer.getIndex() + "\t" + layer.conf().getLayer().getLayerName() + " " + layer.getClass().getName());
        }

//            System.out.println("Serialize initialized model " + modelFile);
//            ModelSerializer.writeModel(net, modelFile, true);
//        }

        System.out.println("Model configuration variables:");
        NeuralNetConfiguration conf =  pretrained.conf();
        for (String variable : conf.getVariables()) {
            System.out.println("\t" + variable);
        }


//        Evaluation eval = graph.output();
        
        
//         graph.output()
    }
}
