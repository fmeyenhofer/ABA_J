import img.AraImgPlus;

import net.imagej.ImgPlus;

import net.imglib2.img.Img;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Mapping > Warp on Template")
public class MapSection2Template implements Command {

    @Parameter
    private ImgPlus section;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus warp;

    @Parameter
    UIService ui;

    @Override
    public void run() {
        if (section instanceof AraImgPlus) {
            Img img = ((AraImgPlus) section).mapSection2Template();
            warp = new ImgPlus(img, section);
        } else {
            ui.showDialog("The section needs to be aligned. ");
        }
    }
}
