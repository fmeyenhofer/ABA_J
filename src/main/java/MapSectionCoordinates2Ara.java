import rest.*;
import io.AraIO;
import io.AraMapping;
import img.AraImgPlus;
import table.ResultsTableConverter;
import table.TableConventions;

import net.imagej.DefaultDataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.table.*;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > Section Coords. to ARA")
public class MapSectionCoordinates2Ara implements Command {

    @Parameter(label = "section image")
    private ImgPlus secImg;

//    @Parameter(label = "measurements", type = ItemIO.OUTPUT)
    private GenericTable outputTable;


    @Parameter
    private UIService ui;

    @Parameter
    private LogService log;

    @Parameter
    private DisplayService disp;


    @Override
    public void run() {
        if (secImg instanceof AraImgPlus) {
            AraImgPlus araImg = (AraImgPlus) secImg;

            AllenClient client = AllenClient.getInstance();
            try {
                outputTable = getTable();

                if (outputTable == null) {
                    outputTable = new DefaultGenericTable();
                }

                // Check if the table contains coordinates
                ArrayList<String> headerNames = new ArrayList<>(outputTable.getColumnCount());
                for (int c = 0; c < outputTable.getColumnCount(); c++) {
                    headerNames.add(outputTable.getColumnHeader(c));
                }
                TableConventions.Header header = TableConventions.Header.findContained(headerNames);

                // Get the ROI centroids if the table does not contain any coordinate columns
                if (header == null) {
                    header = TableConventions.Header.CENTROID;

                    DoubleColumn sec_x = new DoubleColumn(header.getX());
                    DoubleColumn sec_y = new DoubleColumn(header.getY());
                    GenericColumn roi_name = new GenericColumn("Label");
                    RoiManager roim = RoiManager.getRoiManager();
                    if (roim.getCount() > 0) {
                        for (Roi roi : roim.getRoisAsArray()) {
                            roi_name.add(roi.getName());
                            double[] centroid = roi.getContourCentroid();
                            sec_x.add(centroid[0]);
                            sec_y.add(centroid[1]);
                        }
                    } else {
                        ui.showDialog("This plugin needs a table with coordinates or ROI's to work with");
                        return;
                    }

                    outputTable.add(sec_x);
                    outputTable.add(sec_y);
                    outputTable.add(roi_name);
                }

                // Get the atlas data
                AllenRefVol refVol = client.getReferenceVolume(Atlas.Modality.ANNOTATION, araImg.getTemplateResolution());
                AtlasStructureGraph structureGraph = client.getAnnotationStructureGraph(AllenAtlas.MOUSE3D);

                // Create the additional columns
                DoubleColumn ara_x = new DoubleColumn("ARA X");
                DoubleColumn ara_y = new DoubleColumn("ARA Y");
                DoubleColumn ara_z = new DoubleColumn("ARA Z");
                IntColumn id_col = new IntColumn("Annotation ID");
                GenericColumn name_col = new GenericColumn("Annotation Name");
                GenericColumn acro_col = new GenericColumn("Annotation Acronym");

                DoubleColumn xCol = (DoubleColumn) outputTable.get(header.getX());
                DoubleColumn yCol = (DoubleColumn) outputTable.get(header.getY());
                
                RandomAccessibleInterval<FloatType> rai = refVol.getRai();
                RandomAccess<FloatType> ra = rai.randomAccess();

                for (int index = 0; index < outputTable.getRowCount(); index++) {
                    double[] s_coord = new double[]{xCol.get(index), yCol.get(index)};
                    double[] t_coord = araImg.getTemplateCoordinate(s_coord);

                    ara_x.add(t_coord[0]);
                    ara_y.add(t_coord[1]);
                    ara_z.add(t_coord[2]);

                    ra.setPosition(new long[]{Math.round(t_coord[0]), Math.round(t_coord[1]),Math.round(t_coord[2])});
                    Float value = ra.get().getRealFloat();
                    int id = value.intValue();
                    AtlasStructure structure = structureGraph.getGraph().get(id);
                    String name = (structure == null) ? "None" : structure.getName();
                    String acronym = (structure == null) ? "None" : structure.getAcronym();

                    id_col.add(id);
                    name_col.add(name);
                    acro_col.add(acronym);
                }

                outputTable.add(ara_x);
                outputTable.add(ara_y);
                outputTable.add(ara_z);
                outputTable.add(id_col);
                outputTable.add(acro_col);
                outputTable.add(name_col);

                // TODO: the back and forth converting between ij1 and ij2 tables is only because ij2 tables cannot be saved via UI. Output ij2 table via output parameter when possible.
                ResultsTable ijTable = ResultsTableConverter.convertIJ2toIJ1(outputTable);
                ijTable.show("Results");
            } catch (TransformerException e) {
                log.error("XML parser error");
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Cannot load annotation volume");
                e.printStackTrace();
            } catch (URISyntaxException e) {
                log.error("Cannot download annotation volume");
                e.printStackTrace();
            }
        } else {
            ui.showDialog("The input image appears not to have any mapping yet. " +
                    "Use 'Plugins > Allen Brain Atlas > 2. Alignment > Interactive' to establish a mapping." +
                    "Otherwise the file can always be saved in another format.");
        }
    }

    private DefaultGenericTable getTable() {
        for (Display<?> display : disp.getDisplays()) {
            if (display.get(0) instanceof DefaultGenericTable) {
                return (DefaultGenericTable) display.get(0);
            }
        }

        ResultsTable inputTable = ResultsTable.getResultsTable();
        if (inputTable != null && inputTable.getCounter() > 0) {
            return ResultsTableConverter.convertIJ1toIJ2(inputTable);
        }

        return null;
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        String path = "/Users/turf/switchdrive/SJMCS/data/devel/alignment/ali50/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-13.ome.tif";
        Object img = ij.io().open(path);
        DefaultDataset ds = (DefaultDataset) img;
        AraIO araIO = new AraIO();
        File imgFile = new File(path);
        File mapFile = araIO.deriveMappingFile(imgFile);
        AraMapping mapping = AraMapping.load(mapFile);
        AraImgPlus araImg = new AraImgPlus(ds.getImgPlus().getImg(), mapping);
        araImg.setSource(imgFile.getAbsolutePath());
        araImg.setName(imgFile.getName());
        ij.ui().show(araImg);
        
        DoubleColumn x = new DoubleColumn("X");
        x.add(50.0);
        x.add(100.5);
        x.add(210.0);
        DoubleColumn y = new DoubleColumn("Y");
        y.add(70.0);
        y.add(110.1);
        y.add(300.0);
        DefaultGenericTable table = new DefaultGenericTable();
        table.add(x);
        table.add(y);
        ij.ui().show("example coordinates", table);

        ij.command().run(MapSectionCoordinates2Ara.class, true);
    }
}
