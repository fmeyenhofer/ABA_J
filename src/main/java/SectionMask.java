import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import img.SectionImageTool;

import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("FieldCanBeLocal")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 1. Pre-Processing > Create Mask")
public class SectionMask implements Command{

    @Parameter
    private OpService ops;
                                                                                         

    @Parameter(type = ItemIO.INPUT)
    private Dataset dataset;

    @Parameter(label = "Gaussian smoothing (-1 -> auto estimate)")
    private Double sigma = -1.;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus<BitType> output;


    @Override
    public void run() {
        Img<DoubleType> rai = (Img) dataset.getImgPlus();
        Img<BitType> msk;

        if (sigma.equals(-1.)) {
            msk = SectionImageTool.createMask(rai, ops);
        } else {
            msk = SectionImageTool.createMask(rai, sigma, ops);
        }
        
        output = new ImgPlus(msk);
    }

    
    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        Object obj = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif");
        ij.ui().show(obj);
        ij.command().run(SectionMask.class, true);
    }
}
