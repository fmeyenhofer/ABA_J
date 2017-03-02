package rest;

/**
 * These are the different data files encountered with
 * the Allen Brain Atlas RESTful API.
 *
 * @author Felix Meyenhofer
 */
enum AllenDataTypes {

    SVG(".svg"),
    IMG(".jpg"),
    JSON(".json"),
    XML(".xml");

    private String fileExtension;

    AllenDataTypes(String file_extension) {
        this.fileExtension = file_extension;
    }

    String getFileExtension() {
        return this.fileExtension;
    }
}
