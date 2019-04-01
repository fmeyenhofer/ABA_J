import img.SectionImageTool;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 1. Pre-Processing > Create Section Mask")
public class SectionMask implements Command {

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
}
