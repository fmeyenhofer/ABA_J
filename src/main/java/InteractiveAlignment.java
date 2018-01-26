
import gui.InteractiveAlignmentUi;
import img.AraImgPlus;
import img.SectionImageTool;
import rest.AllenClient;
import rest.AllenRefVol;
import rest.Atlas;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import mpicbg.spim.data.SpimDataException;

import org.scijava.Initializable;
import org.scijava.app.StatusService;
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
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;


/**
 * @author Felix Meyenhofer
 */
//@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Mapping > Interactive Alignment")
@Plugin(type = Command.class, menuPath = "Plugins > Interactive Alignment")
public class InteractiveAlignment <T extends RealType<T>> extends DynamicCommand implements Initializable {

    @Parameter(label = "Section image")
    private ImgPlus<T> secImg;

    @Parameter(label = "Plane of section")
    private String planeOfSection;

    @Parameter(label = "Reference atlas resolution")
    private String araResolution;

    @Parameter(label = "Section resolution", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = {"estimate", "metadata"})
    private String resolutionMethod;


    @Parameter(label = "Reference modality")
    private String araModality;


    @Parameter
    private OpService ops;

    @Parameter
    private StatusService status;

    @Parameter
    private LogService log;


    @Override
    public void initialize() {
        double metaRes = getResolution();
        Atlas.VoxelResolution resolution;
        if (metaRes > -1) {
            resolution = Atlas.VoxelResolution.getClosest(getResolution());
        } else {
            Atlas.PlaneOfSection plane = Atlas.PlaneOfSection.get(planeOfSection);
            int dim = plane.getSectionAxes()[0];
            resolution = Atlas.VoxelResolution.getClosest(secImg.dimension(0), dim);
        }

        final MutableModuleItem<String> araResItem = getInfo().getMutableInput("araResolution", String.class);
        araResItem.setChoices(Atlas.VoxelResolution.getLabels());
        araResItem.setDefaultValue(resolution.getLabel());

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

        AllenClient client = AllenClient.getInstance();
        client.setLogger(log);

        try {
            // Load the reference volume
            AllenRefVol refVol = client.getReferenceVolume(araModality, araResolution);

            // determine the initial transform of the input section
            double sectionResolution;
            switch (resolutionMethod) {
                case "estimate":
                    sectionResolution = estimateSectionResolution(plane);
                    break;
                    
                case "metadata":
                    sectionResolution = getResolution();
                    break;

                default:
                    sectionResolution = volumeResolution.getValue();
            }

            // determine initial transform for the section image
            AraImgPlus<T> section = new AraImgPlus(secImg, plane, sectionResolution);

            // initialize the UI and open it
            InteractiveAlignmentUi ui = new InteractiveAlignmentUi(section, refVol, ops);
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

    private double estimateSectionResolution(Atlas.PlaneOfSection plane) throws TransformerException, IOException, URISyntaxException, SpimDataException {
        double sectionResolution;
        Atlas.VoxelResolution refRes = Atlas.VoxelResolution.FIFTY;
        Img<BitType> refMask = AllenRefVol.getSectionMask(refRes, plane);
        double refArea = ops.stats().sum(refMask).getRealDouble();

        double scale1 = ((double) refMask.dimension(0)) / ((double) secImg.dimension(0));
        RandomAccessibleInterval<T> secImgSca = Views.subsample(secImg, Math.round(1 / scale1));

        Img secImgScaImg = ImgView.wrap(secImgSca, new ArrayImgFactory());
        Img<BitType> secMask = SectionImageTool.createMask(secImgScaImg, ops);

        double secArea = ops.stats().sum(secMask).getRealDouble();
        double scale2 = refArea / secArea;
        sectionResolution = refRes.getValue() * scale1 * scale2;

        return sectionResolution;
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
            case "milimeter":
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


    public static void main(String[] args) throws IOException {
//        String path = "/Users/turf/switchdrive/SJMCS/data/devel/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-09 - Series 2.tif";
//        String path = "/Users/turf/switchdrive/SJMCS/data/devel/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-10 - Series 3.tif";
//        String path = "/Users/turf/switchdrive/SJMCS/data/devel/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-07 - series 2.tif";
//        String path = "/Users/turf/switchdrive/SJMCS/data/devel/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-10 - Series 3.tif";
//        String path = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";
        String path = "/Users/turf/switchdrive/SJMCS/data/devel/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00 - series 3.tif";

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        Object img = ij.io().open(path);
        ij.ui().show(img);

        ij.command().run(InteractiveAlignment.class, true);
    }
}
