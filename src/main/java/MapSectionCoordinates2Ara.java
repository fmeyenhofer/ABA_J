import gui.CoordinateColumnHeaderDialog;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.io.IOService;
import rest.*;
import io.AraIO;
import io.AraMapping;
import img.AraImgPlus;
import table.ResultsTableConverter;
import table.TableConventions;
import table.XYHeaders;

import net.imagej.DefaultDataset;
import net.imagej.ImageJ;
import net.imagej.table.*;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.measure.ResultsTable;

import javax.naming.ConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > Section Coords. to ARA")
public class MapSectionCoordinates2Ara extends AraIO implements Command {

    @Parameter(label = "Output tables", choices = {"show", "save"})
    String action = "show";

    @Parameter
    private LogService log;

    @Parameter
    private DisplayService disp;

    @Parameter
    private IOService ioService;
//
//    @Parameter
//    private ImageDisplayService imgDispService;


    @Override
    public void run() {
        AllenClient client = AllenClient.getInstance();
//        if (secImg instanceof AraImgPlus) {

        List<String> imgIds = getImageDisplays();

        if (imgIds.size() < 1) {
            imgIds = getMappedImagePaths();
        }

        XYHeaders header = null;

        for (String imgId : imgIds) {
            log.info("Process section image: " + imgId);


            try {
                AraImgPlus img = getImage(imgId);

                GenericTable resTable = null;
                String tableName = "Results";
                // If there is only one image we look first for a open table (maybe it has just been produced
                // ... and we won't bother to first save it.
                if (imgIds.size() == 1) {
                    resTable = getTable();
                }

                if (resTable == null) {
                    File resPath = deriveResultFile(new File(imgId));
                    if (resPath.exists()) {
                        ResultsTable ij1Table = ResultsTable.open2(resPath.getAbsolutePath());
                        if (ij1Table == null) {
                            log.error("Aborted. Could not read result file" + resPath);
                            return;
                        }
                        resTable = ResultsTableConverter.convertIJ1toIJ2(ij1Table);
                        tableName = resPath.getName();
                    } else {
                        log.warn("... no results found");
                        continue;
                    }
                }

                // Check if the table contains coordinates
                ArrayList<String> headerNames = new ArrayList<>(resTable.getColumnCount());
                for (int c = 0; c < resTable.getColumnCount(); c++) {
                    headerNames.add(resTable.getColumnHeader(c));
                }

                // TODO: header logic is bad
                if (header == null ||
                        !headerNames.contains(header.getXColumn()) ||
                        !headerNames.contains(header.getYColumn())) {
                    header = TableConventions.Header.findContained(headerNames);
                }

                // Prompt the user to select headers if necessary
                if (header == null) {
                    HashMap<String, String[]> tableInfo = new HashMap<>();
                    tableInfo.put(tableName, headerNames.toArray(new String[0]));
                    header = CoordinateColumnHeaderDialog.createAndShow(tableInfo);
                    if (header == null) {
                        log.info("Column header selection aborted.");
                        return;
                    }
                }

                mapSectionCoordinates(client, img, resTable, header, tableName);

            } catch (TransformerException e) {
                log.error("XML parser error");
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Cannot load annotation volume");
                e.printStackTrace();
            } catch (URISyntaxException e) {
                log.error("Cannot download annotation volume");
                e.printStackTrace();
            } catch (ConfigurationException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                log.error("ARA serialization error");
                e.printStackTrace();
            }
        }

//        } else {
//            ui.showDialog("The input image appears not to have any mapping yet. " +
//                    "Use 'Plugins > Allen Brain Atlas > 2. Alignment > Interactive' to establish a mapping." +
//                    "Otherwise the file can always be saved in another format.");
//        }
    }

    private void mapSectionCoordinates(AllenClient client, AraImgPlus araImg, GenericTable table, XYHeaders header, String tableName)
            throws TransformerException, IOException, URISyntaxException {

//        // Get the ROI centroids if the table does not contain any coordinate columns
//        if (header == null) {
//            header = TableConventions.Header.CENTROID;
//            table = getRoiCentroids(TableConventions.Header.CENTROID);
//        }

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

        DoubleColumn xCol = (DoubleColumn) table.get(header.getXColumn());
        DoubleColumn yCol = (DoubleColumn) table.get(header.getYColumn());

        RandomAccessibleInterval<FloatType> rai = refVol.getRai();
        RandomAccess<FloatType> ra = rai.randomAccess();

        for (int index = 0; index < table.getRowCount(); index++) {
            double[] s_coord = new double[]{xCol.get(index), yCol.get(index)};
            double[] t_coord = araImg.getTemplateCoordinate(s_coord);

            ara_x.add(t_coord[0]);
            ara_y.add(t_coord[1]);
            ara_z.add(t_coord[2]);

            ra.setPosition(new long[]{Math.round(t_coord[0]), Math.round(t_coord[1]), Math.round(t_coord[2])});
            Float value = ra.get().getRealFloat();
            int id = value.intValue();
            AtlasStructure structure = structureGraph.getGraph().get(id);
            String name = (structure == null) ? "None" : structure.getName();
            String acronym = (structure == null) ? "None" : structure.getAcronym();

            id_col.add(id);
            name_col.add(name);
            acro_col.add(acronym);
        }

        table.add(ara_x);
        table.add(ara_y);
        table.add(ara_z);
        table.add(id_col);
        table.add(acro_col);
        table.add(name_col);

        // TODO: the back and forth converting between ij1 and ij2 tables is only because ij2 tables cannot be saved via UI.
        ResultsTable ijTable = ResultsTableConverter.convertIJ2toIJ1(table);
        if (action.equals("show")) {
            ijTable.show(tableName);
        } else {
            File outPath = deriveResultFile(new File(araImg.getSource()));
            String outStr = outPath.getAbsolutePath().substring(0, outPath.getAbsolutePath().length() - 3) + "mapped.txt";
            if (new File(outStr).exists()) {// TODO: this would be done before the very end
                log.warn("Output already exists:" + outStr);
            } else {
                ijTable.saveAs(outStr);
            }
        }
    }

//    private GenericTable getRoiCentroids(TableConventions.Header header) {
//        DoubleColumn sec_x = new DoubleColumn(header.getXColumn());
//        DoubleColumn sec_y = new DoubleColumn(header.getYColumn());
//        GenericColumn roi_name = new GenericColumn("Label");
//        RoiManager roim = RoiManager.getRoiManager();
//        if (roim.getCount() > 0) {
//            for (Roi roi : roim.getRoisAsArray()) {
//                roi_name.add(roi.getName());
//                double[] centroid = roi.getContourCentroid();
//                sec_x.add(centroid[0]);
//                sec_y.add(centroid[1]);
//            }
//        } else {
//            ui.showDialog("This plugin needs a table with coordinates or ROI's to work with");
//            return null;
//        }
//
//        outputTable.add(sec_x);
//        outputTable.add(sec_y);
//        outputTable.add(roi_name);
//        return outputTable;
//    }

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

//        String path = "/Users/turf/switchdrive/SJMCS/data/devel/alignment/ali50/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-13.ome.tif";
        String path = "/Users/meyenhof/switchdrive/SJMCS/data/devel/coordmap/170815_Insu1_1_Fos - 2018-04-10 17.26.21-FITC_section-00_FITC.ome.tif";
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

        ResultsTable table = ResultsTable.open2("/Users/meyenhof/switchdrive/SJMCS/data/devel/coordmap/170815_Insu1_1_Fos - 2018-04-10 17.26.21-FITC_section-00_FITC.txt");
        table.show("Results");

//        DoubleColumn x = new DoubleColumn("X");
//        x.add(50.0);
//        x.add(100.5);
//        x.add(210.0);
//        DoubleColumn y = new DoubleColumn("Y");
//        y.add(70.0);
//        y.add(110.1);
//        y.add(300.0);
//        DefaultGenericTable table = new DefaultGenericTable();
//        table.add(x);
//        table.add(y);
//        ij.ui().show("example coordinates", table);

        ij.command().run(MapSectionCoordinates2Ara.class, true);
    }
}
