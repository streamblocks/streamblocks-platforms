package ch.epfl.vlsc.configuration;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;

public class ConfigurationManager {

    private final File file;
    private Configuration configuration;

    public ConfigurationManager(File file) throws JAXBException {
        this.file = file;
        read();
    }

    private void read() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Configuration.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        configuration = (Configuration) unmarshaller.unmarshal(file);
    }

    public void write(File out) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Configuration.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(configuration, out);
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public static void write(File out, Configuration configuration) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Configuration.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(configuration, out);
    }
}
