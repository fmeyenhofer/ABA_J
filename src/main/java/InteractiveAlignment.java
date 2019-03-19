import gui.bdv.InteractiveAlignmentUi;
import gui.SwingUtils;
import img.AraImgPlus;
import img.SectionImageTool;
import rest.AllenClient;
import rest.AllenRefVol;
import rest.Atlas;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;

import mpicbg.spim.data.SpimDataException;

import org.scijava.ui.UIService;
import org.scijava.Initializable;
import org.scijava.app.StatusService;
import org.scijava.display.DisplayService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import io.scif.ImageMetadata;
import io.scif.img.SCIFIOImgPlus;

import javax.xml.transform.TransformerException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * TODO: Add multi-channel support
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 2. Alignment > Interactive")
public class InteractiveAlignment extends DynamicCommand implements Initializable {

    @Parameter(label = "Section image")
    private ImgPlus secImg;

    @Parameter(label = "Plane of section")
    private String planeOfSection;

    @Parameter(label = "Reference atlas resolution")
    private String araResolution;

    @Parameter(label = "Section resolution", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = {"estimate", "metadata"})
    private String resolutionMethod;

    @Parameter(label = "Reference modality")
    private String araModality;

    @Parameter(label = "Outline sampling levels")
    private int levels = 4;

    @Parameter(label = "Optimize correspondences")
    private boolean optimize = false;

    @Parameter(label = "Remove correspondence outliers")
    private boolean outliers = false;

    
    @Parameter
    private OpService ops;

    @Parameter
    private StatusService status;

    @Parameter
    private DisplayService display;

    @Parameter
    private LogService log;

    @Parameter
    private UIService ui;


    @Override
    public void initialize() {
//        double metaRes = getResolution();
//        Atlas.VoxelResolution resolution;
//        if (metaRes > -1) {
//            resolution = Atlas.VoxelResolution.getClosest(getResolution());
//        } else {
//            Atlas.PlaneOfSection plane = Atlas.PlaneOfSection.get(planeOfSection);
//            int dim = plane.getSectionAxes()[0];
//            resolution = Atlas.VoxelResolution.getClosest(secImg.dimension(0), dim);
//        }

        final MutableModuleItem<String> araResItem = getInfo().getMutableInput("araResolution", String.class);
        araResItem.setChoices(Atlas.VoxelResolution.getLabels());
//        araResItem.setDefaultValue(resolution.getLabel());

        final MutableModuleItem<String> araModItem = getInfo().getMutableInput("araModality", String.class);
        araModItem.setChoices(Atlas.Modality.getLabels());
        araModItem.setDefaultValue(Atlas.Modality.AUTOFLUO.toString());

        final MutableModuleItem<String> pofItem = getInfo().getMutableInput("planeOfSection", String.class);
        pofItem.setChoices(Atlas.PlaneOfSection.getLabels());
    }

    @Override
    public void run() {
        Atlas.PlaneOfSection plane = Atlas.PlaneOfSection.get(planeOfSection);
        Atlas.VoxelResolution volumeResolution = Atlas.VoxelResolution.get(araResolution);
        Atlas.Modality modality = Atlas.Modality.get(araModality);

        AllenClient client = AllenClient.getInstance();
        client.setLogService(log);
        client.setStatusService(status);

        try {
            status.showStatus("load reference volume (template)");
            AllenRefVol refVol = client.getReferenceVolume(modality, volumeResolution);

            // determine the initial transform of the input section
            double sectionResolution;
            switch (resolutionMethod) {
                case "estimate":
                    sectionResolution = SectionImageTool.estimateSectionResolution(secImg, plane, ops);
                    break;

                case "metadata":
                    sectionResolution = getResolution();
                    break;

                default:
                    sectionResolution = volumeResolution.getValue();
            }

            // determine initial transform for the section image
            AraImgPlus section = new AraImgPlus(secImg.getImg(), sectionResolution, plane, volumeResolution);   // TODO: pass the imgplus
            section.setName(new File(secImg.getSource()).getName() + " - aligned");
            section.setSource(secImg.getSource());

            // TODO this currently (pom-scijava 25.0.0) only works if the "Menu > Edit > Options > ImageJ2... > Use SCIFIO for opening image files" is checked.
//            display.createDisplay(section);
            ui.show(section);

//            Display imgWindow = display.getDisplay(secImg.getName()); // Does not work
//            imgWindow.close();
            Frame secImgDisplay = SwingUtils.grabFrame(secImg.getName());
            if (secImgDisplay != null) {
                secImgDisplay.dispose();
            }

            // initialize the UI and open it
            InteractiveAlignmentUi ui = new InteractiveAlignmentUi(section, refVol, levels, optimize, outliers, ops, status);
            ui.createAndShow();

        } catch (TransformerException e) {
            log.error("Could not read xml.");
            e.printStackTrace();
        } catch (IOException e) {
            log.error("Could not download reference volume");
            e.printStackTrace();
        } catch (URISyntaxException e) {
            log.error("Could not reach server");
            e.printStackTrace();
        } catch (SpimDataException e) {
            log.error("Could not load reference volume");
            e.printStackTrace();
        }
    }

    private double getResolution() {
        Map<String,Object> map = secImg.getProperties();
        ImageMetadata imgMetadata = (ImageMetadata) map.get(SCIFIOImgPlus.IMAGE_META);
        if (imgMetadata == null) {
            return -1;
        }

        double resolution = imgMetadata.getAxes().get(0).calibratedValue(1);
        String unit = imgMetadata.getAxes().get(0).unit();

        double factor;
        switch (unit) {
            case "millimeter":
                factor = 0.001;
                break;
            case "micrometer":
                factor = 1;
                break;
            case "micron":
                factor = 1;
                break;
            case "nanometer":
                factor = 1000;
                break;
            default:
                factor = 1;
        }
        resolution *= factor;
        return resolution;
    }
}
