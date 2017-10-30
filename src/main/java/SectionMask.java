import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BitType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import util.ij2.ImageSectionTools;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Section Mask")
public class SectionMask implements Command{

    @Parameter
    OpService ops;

    @Parameter
    UIService ui;


    @Parameter(type = ItemIO.INPUT)
    Dataset dataset;

    @Parameter(label = "Gaussian smoothing (-1 -> auto estimate)")
    Double sigma = -1.;


    @Override
    public void run() {

        RandomAccessibleInterval rai = dataset.getImgPlus().getImg();
        RandomAccessibleInterval<BitType> msk;

        if (sigma.equals(-1.)) {
            msk = ImageSectionTools.createMask(rai, ops);
        } else {
            msk = ImageSectionTools.createMask(rai, sigma, ops);
        }

        ui.show("mask", msk);
    }

    
    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(SectionMask.class, true);
    }
}
