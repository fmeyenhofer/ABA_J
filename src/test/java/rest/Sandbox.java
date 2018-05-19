package rest;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imglib2.*;
import net.imglib2.algorithm.region.localneighborhood.old.LocalNeighborhood;
import net.imglib2.algorithm.region.localneighborhood.old.LocalNeighborhoodCursor;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.io.IOService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.imageio.ImageIO;
import javax.xml.transform.TransformerException;
import java.awt.image.RenderedImage;
import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Display Test")
public class Sandbox implements Command {

    @Parameter
    OpService opService;

    @Parameter
    DisplayService displayService;

    @Override
    public void run() {

        int[] center = new int[] {15,10};
        Dimensions dims = new FinalDimensions(31, 21);

        Img<BitType> img = opService.create().img(dims, new BitType());

        RandomAccess<BitType> ra = img.randomAccess();
        ra.setPosition(center);
        ra.get().set(true);

        displayService.createDisplay("original", img);

        IntervalView view = Views.interval(img, new long[]{5, 5}, new long[]{25, 15});
        ImageJFunctions.show(view, "view - ImageJFunctions.show()");

        Display disp1 = displayService.createDisplay("view - DisplayService.createDisplay()", view);

        ImagePlus imp = ImageJFunctions.wrap(view, "wrapped");
        Display disp2 = displayService.createDisplay("view - wrapped to ImagePlus", imp);
        System.out.println("tadaa");
    }

    public static void main(String[] args) throws IOException, TransformerException, URISyntaxException {
        ImageJ ij = net.imagej.Main.launch(args);
        ij.command().run(Sandbox.class, false);
    }
}
