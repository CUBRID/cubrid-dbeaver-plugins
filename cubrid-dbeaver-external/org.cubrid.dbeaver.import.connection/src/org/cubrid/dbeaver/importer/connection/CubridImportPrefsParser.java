package org.cubrid.dbeaver.importer.connection;

import java.io.InputStream;
import java.io.StringReader;
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
        try (InputStream input = Files.newInputStream(prefsFile)) {
            props.load(input);
        }

        String dbsXml = cleanPrefsXml(props.getProperty(KEY_DATABASES));
        List<CMDatabase> dbs = dbsXml == null ? List.of() : parseDatabasesXml(dbsXml);

        return dbs;
    }

    private static String cleanPrefsXml(String xml) {
        if (xml == null) {
            return null;
        }
        return xml.replace("\\n", "\n").replace("\\\"", "\"").replace("\\=", "=").replace("\\:", ":");
    }

    private static List<CMDatabase> parseDatabasesXml(String xml) throws Exception {
        DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
        Document doc = builder.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
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
