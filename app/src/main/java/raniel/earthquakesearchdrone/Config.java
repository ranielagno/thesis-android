package raniel.earthquakesearchdrone;

/**
 * Created by Raniel on 2/1/2018.
 */

public enum Config {
    dev_server("http://192.168.8.100:5000/drone"),
    prod_server("https://doddering-piranha-0610.dataplicity.io/drone");

    private String value;

    Config(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.getValue();
    }

}
