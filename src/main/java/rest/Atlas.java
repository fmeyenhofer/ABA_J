package rest;


/**
 * Minimum priors to interact with the API
 *
 * @author Felix Meyenhofer
 */
class Atlas {

    static final String DEFAULT_PRODUCT_NAME = "Mouse Brain Reference Data";
    static final int DEFAULT_PRODUCT_ID = 12;

    enum Species {
        HUMAN,
        MOUSE
    }

    enum PlaneOfSection {
        CORONAL,
        SAGITAL,
        HORIZONTAL
    }
}
