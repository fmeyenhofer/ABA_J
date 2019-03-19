import io.AraIO;
import img.AraImgPlus;
import rest.AllenRefVol;

import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.img.Img;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;
import org.scijava.app.StatusService;

/**
 * TODO: make it possible to output a 2D section instead of a volume
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > Section to ARA")
public class MapSection2Ara<T extends RealType<T> & NativeType<T>> extends AraIO implements Command {

    @Parameter(label = "Input section")
    private ImgPlus section;

    @Parameter(label = "Output section (mapped on ARA)", type = ItemIO.OUTPUT)
    private ImgPlus warp;


    @Parameter
    private LogService log;

    @Parameter
    private StatusService status;


    @Override
    public void run() {

        AraImgPlus sec;
        if (section instanceof AraImgPlus) {
            sec = (AraImgPlus) section;
        } else {
            ui.showDialog("The input section does not have a mapping. " +
                    "\nPlease align the section first and save it as ARA.SEC");
            return;
        }

        Img<T> vol = sec.mapSection2Template();
        warp = new ImgPlus(vol, "Mapped section " + section.getName(), AllenRefVol.getAxes());
    }
}
