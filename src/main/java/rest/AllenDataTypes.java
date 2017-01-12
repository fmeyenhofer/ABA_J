package rest;

/**
 * @author Felix Meyenhofer
 */
enum AllenDataTypes {
    SVG(".svg"),
    IMG(".jpg"),
    JSON(".json");

    private String fileExtension;

    AllenDataTypes(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    String getFileExtension() {
        return this.fileExtension;
    }
}
