package cnn;

import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.VGG19;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
public class VGGTest {
    public static void main(String[] args) throws IOException {

        File file = new File("/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/26836491_25um_red_300.tif");
        INDArray img = Dl4jTools.loadImg(file);

        ZooModel model = new VGG19();
        Model pretrained= model.initPretrained(PretrainedType.IMAGENET);
        ComputationGraph graph = (ComputationGraph) pretrained;
        
        INDArray[] out = graph.output(img);

        System.out.println("done.");
    }
}
