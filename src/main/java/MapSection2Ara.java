import img.AraImgPlus;
import rest.AllenRefVol;

import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

/**
 * TODO: make it possible to output a 2D section instead of a volume
 * TODO: allow mapping of multiple sections (open or from directory)
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > Section(s) to ARA")
public class MapSection2Ara implements Command {

    @Parameter(label = "section image")
    private ImgPlus section;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus warp;


    @Parameter
    private UIService ui;


    @Override
    public void run() {
        if (section instanceof AraImgPlus) {
            AraImgPlus ara = (AraImgPlus) section;
            Img img = ara.mapSection2Template();
            warp = new ImgPlus(img, "mapped section " + ara.getSectionNumber(), AllenRefVol.getAxis());
        } else {
            ui.showDialog("The section needs to be aligned. ");
        }
    }
}
