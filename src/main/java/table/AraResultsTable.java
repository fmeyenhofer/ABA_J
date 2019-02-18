package table;

import img.AraImgPlus;
import org.scijava.table.*;
import rest.*;

import ij.measure.ResultsTable;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Results table with object coordinates.
 * This class provides the functionality to map coordinates into the ARA.
 *
 * @author Felix Meyenhofer
 */
public class AraResultsTable extends DefaultGenericTable {

    public String X_COLUMN_NAME = "ARA X";
    public String Y_COLUMN_NAME = "ARA Y";
    public String Z_COLUMN_NAME = "ARA Z";
    public String ANNOTATION_ID_COLUMN_NAME = "Annotation ID";
    public String ANNOTATION_NAME_COLUMN_NAME = "Annotation NAME";
    public String ANNOTATION_ACRONYM_COLUMN_NAME = "Annotation Acronym";

    private String name;

    public AraResultsTable(ResultsTable table) {
        this(ResultsTableConverter.convertIJ1toIJ2(table));
    }

    public AraResultsTable(GenericTable table) {
        super(table.getColumnCount(), table.getRowCount());

        for (int c = 0; c < table.getColumnCount(); c++) {
            this.get(c).setHeader(table.get(c).getHeader());
            for (int r = 0; r < table.getRowCount(); r++) {
                this.set(c, r, table.get(c, r));
            }
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        if (name == null) {
            return "Results Table";
        } else {
            return name;
        }
    }

    public List<String> getHeaderNames() {
        ArrayList<String> headerNames = new ArrayList<>(getColumnCount());
        for (int c = 0; c < getColumnCount(); c++) {
            headerNames.add(getColumnHeader(c));
        }

        return headerNames;
    }

    public TableConventions.Header getCoordinateHeaders() {
        return TableConventions.Header.findContained(getHeaderNames());
    }

    public boolean hasCoordinateHeaders() {
        return TableConventions.Header.findContained(getHeaderNames()) != null;
    }

    public AraResultsTable mapSectionCoordinates(AllenClient client, AraImgPlus araImg, XYHeaders header)
            throws TransformerException, IOException, URISyntaxException {

        AraResultsTable table = (AraResultsTable) this.clone();

//        // Get the ROI centroids if the table does not contain any coordinate columns
//        if (header == null) {
//            header = TableConventions.Header.CENTROID;
//            table = getRoiCentroids(TableConventions.Header.CENTROID);
//        }

        // Get the atlas data
        AllenRefVol refVol = client.getReferenceVolume(Atlas.Modality.ANNOTATION, araImg.getTemplateResolution());
        AtlasStructureGraph structureGraph = client.getAnnotationStructureGraph(AllenAtlas.MOUSE3D);

        // Create the additional columns
        DoubleColumn ara_x = new DoubleColumn(X_COLUMN_NAME);
        DoubleColumn ara_y = new DoubleColumn(Y_COLUMN_NAME);
        DoubleColumn ara_z = new DoubleColumn(Z_COLUMN_NAME);
        IntColumn id_col = new IntColumn(ANNOTATION_ID_COLUMN_NAME);
        GenericColumn name_col = new GenericColumn(ANNOTATION_NAME_COLUMN_NAME);
        GenericColumn acro_col = new GenericColumn(ANNOTATION_ACRONYM_COLUMN_NAME);

        GenericColumn xCol = (GenericColumn) this.get(header.getXColumn());
        GenericColumn yCol = (GenericColumn) this.get(header.getYColumn());

        RandomAccessibleInterval<FloatType> rai = refVol.getRai();
        RandomAccess<FloatType> ra = rai.randomAccess();

        for (int index = 0; index < table.getRowCount(); index++) {
            double[] s_coord = new double[]{(double)xCol.get(index), (double)yCol.get(index)};
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

        return table;
    }

    public void show() {
        // TODO: the back and forth converting between ij1 and ij2 tables is only because ij2 tables cannot be saved via UI.
        ResultsTable ijTable = ResultsTableConverter.convertIJ2toIJ1(this);
        ijTable.show(getName());
    }
}
