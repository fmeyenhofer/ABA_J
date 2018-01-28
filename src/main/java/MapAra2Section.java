import img.AraImgPlus;
import rest.AllenClient;
import rest.AllenRefVol;
import rest.Atlas;

import net.imagej.ImgPlus;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.log.LogService;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Mapping > Warp ARA on Section ")
public class MapAra2Section implements Command {

    @Parameter
    private ImgPlus section;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus warp;


    @Parameter
    UIService ui;

    @Parameter
    LogService log;


    @Override
    public void run() {
        if (section instanceof AraImgPlus) {
            // TODO allow for a second input (check if there are several images and propose a dialog to select another one) or select between modalities

            try {
                AraImgPlus ara = (AraImgPlus) section;
                AllenClient client = AllenClient.getInstance();
                AllenRefVol refVol = client.getReferenceVolume(Atlas.Modality.AUTOFLUO, ara.getTemplateResolution());
                Img<UnsignedShortType> img = ara.mapTemplate2Section(refVol.getRai());
                warp = new ImgPlus(img, section);
            } catch (TransformerException e) {
                log.error("Trouble parsing the template data.");
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Trouble opening the template data. Make sure you have an internet connection");
                e.printStackTrace();
            } catch (URISyntaxException e) {
                log.error("Trouble contacting mouse.brain-map.org");
                e.printStackTrace();
            }
        } else {
            ui.showDialog("The image appears not to be aligned yet.");
        }
    }
}