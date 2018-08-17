import img.AraImgPlus;
import rest.AllenClient;
import rest.AllenRefVol;
import rest.Atlas;

import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Initializable;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
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
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > ARA to Section")
public class MapAra2Section extends DynamicCommand implements Command, Initializable{

    @Parameter(label = "input section")
    private ImgPlus section;

    @Parameter(label = "modalityName")
    private String modalityName;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus warp;


    @Parameter
    private UIService ui;

    @Parameter
    private LogService log;


    @Override
    public void initialize() {
        final MutableModuleItem<String> araModItem = getInfo().getMutableInput("modalityName", String.class);
        araModItem.setChoices(Atlas.Modality.getLabels());
        araModItem.setDefaultValue(Atlas.Modality.AUTOFLUO.toString());
    }

    @Override
    public void run() {
        if (section instanceof AraImgPlus) {
            try {
                Atlas.Modality modality = Atlas.Modality.get(modalityName);
                AraImgPlus ara = (AraImgPlus) section;
                AllenClient client = AllenClient.getInstance();
                AllenRefVol refVol = client.getReferenceVolume(modality, ara.getTemplateResolution());
                Img<UnsignedShortType> img = ara.mapTemplate2Section(refVol.getRai(), modality);
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