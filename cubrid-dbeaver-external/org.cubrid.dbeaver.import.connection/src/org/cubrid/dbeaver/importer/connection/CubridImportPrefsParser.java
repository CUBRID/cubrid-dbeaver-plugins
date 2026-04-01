package org.cubrid.dbeaver.importer.connection;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class CubridImportPrefsParser {

    private static final String KEY_DATABASES = "CUBRID_DATABASES";

    public static List<CMDatabase> parse(Path prefsFile) throws Exception {
        Properties props = new Properties();
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(prefsFile), StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        String dbsXml = props.getProperty(KEY_DATABASES)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\=", "=")
                .replace("\\:", ":");
        List<CMDatabase> dbs = dbsXml == null ? List.of() : parseDatabasesXml(dbsXml);

        return dbs;
    }

    private static List<CMDatabase> parseDatabasesXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList nodes = doc.getElementsByTagName("database");

        List<CMDatabase> dbs = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);

            String brokerIp = e.getAttribute("brokerIp");
            String brokerPort = e.getAttribute("brokerPort");
            String address = e.getAttribute("address");
            String port = e.getAttribute("port");

            CMDatabase db = new CMDatabase();

            if (notBlank(brokerIp) && notBlank(brokerPort)) {
                db.host = brokerIp.trim();
                db.port = brokerPort.trim();
            } else {
                db.host = address != null ? address.trim() : "";
                db.port = port != null ? port.trim() : "";
            }

            db.dbName = e.getAttribute("dbName");
            db.dbUser = e.getAttribute("dbUser");

            if (notBlank(db.host) && notBlank(db.port) && notBlank(db.dbName)) {
                dbs.add(db);
            }
        }
        return dbs;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public static class CMDatabase {
        public String host;
        public String port;
        public String dbName;
        public String dbUser;
    }
}
