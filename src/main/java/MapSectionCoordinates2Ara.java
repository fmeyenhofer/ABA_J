import org.scijava.table.DefaultGenericTable;
import rest.*;
import io.AraIO;
import img.AraImgPlus;
import table.AraResultsTable;
import table.ResultsTableConverter;
import table.XYHeaders;
import gui.CoordinateColumnHeaderDialog;

import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ImgPlus;

import ij.measure.ResultsTable;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > Section Coords. to ARA")
public class MapSectionCoordinates2Ara extends AraIO implements Command {

    @Parameter(label = "Input image")
    ImgPlus secImg;


    @Parameter
    private LogService log;

    @Parameter
    private DisplayService disp;


    @Override
    public void run() {
        AllenClient client = AllenClient.getInstance();

        AraImgPlus img;
        if (secImg instanceof AraImgPlus) {
            img = (AraImgPlus) secImg;
        } else {
            log.error("Mapping aborted: The input section does not have an ARA mapping.");
            return;
        }

        try {
            AraResultsTable resTable;
            resTable = getTable();

            if (resTable == null) {
                File resPath = deriveResultFile(new File(img.getSource()));
                if (resPath.exists()) {
                    ResultsTable ij1Table = ResultsTable.open2(resPath.getAbsolutePath());
                    if (ij1Table == null) {
                        log.error("Aborted. Could not read result file" + resPath);
                        return;
                    }
                    resTable = (AraResultsTable) ResultsTableConverter.convertIJ1toIJ2(ij1Table);
                    resTable.setName(resPath.getName());
                } else {
                    log.warn("Mapping aborted: No results found (open or as file next to the image input)");
                    return;
                }
            }

            XYHeaders header = resTable.getCoordinateHeaders();

            // Prompt the user to select headers if necessary
            if (header == null) {
                HashMap<String, String[]> tableInfo = new HashMap<>();
                tableInfo.put(resTable.getName(), resTable.getHeaderNames().toArray(new String[0]));
                header = CoordinateColumnHeaderDialog.createAndShow(tableInfo);
                if (header.getColumns().contains(null)) {
                    log.info("Column header selection aborted.");
                    return;
                }
            }

            AraResultsTable mappedTable = resTable.mapSectionCoordinates(client, img, header);
            mappedTable.setName(resTable.getName() + " - ARA mapped");
            mappedTable.show();

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

    private AraResultsTable getTable() {
        for (Display<?> display : disp.getDisplays()) {
            if (display.get(0) instanceof DefaultGenericTable) {
                return new AraResultsTable((DefaultGenericTable) display.get(0));
            }
        }

        ResultsTable inputTable = ResultsTable.getResultsTable();
        if (inputTable != null && inputTable.getCounter() > 0) {
            return new AraResultsTable(inputTable);
        }

        return null;
    }
}
