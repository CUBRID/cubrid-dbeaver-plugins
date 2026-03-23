package org.cubrid.dbeaver.export.loaddb.core;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cubrid.dbeaver.export.loaddb.model.CubridExportSettings;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridSequence;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.ext.cubrid.model.CubridView;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.ext.generic.model.GenericSequence;
import org.jkiss.dbeaver.ext.generic.model.GenericTableBase;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public class CubridLoadDBRepository {

    private DBRProgressMonitor monitor;
    private CubridDataSource dataSource;
    private CubridExportSettings settings;
    private CubridUser selectedSchema;
    private boolean isMultiSchema;
    private final Map<String, Map<String, String>> collationCache = new HashMap<>();
    private final Map<String, SerialExtraInfo> serialExtraCache = new HashMap<>();

    public CubridLoadDBRepository(
        DBRProgressMonitor monitor,
        CubridDataSource dataSource,
        CubridExportSettings settings,
        CubridUser selectedSchema
    ) {
        this.monitor = monitor;
        this.dataSource = dataSource;
        this.settings = settings;
        this.isMultiSchema = dataSource.getSupportMultiSchema();
        this.selectedSchema = selectedSchema;
    }

    public List<CubridUser> loadUsers() {
    	if (selectedSchema != null) {
            return java.util.Collections.singletonList(selectedSchema);
        }
        List<CubridUser> users = new ArrayList<>();
        try {
            for (GenericSchema schema : dataSource.getCubridUsers(monitor)) {
                if (monitor.isCanceled()) break;
                if (schema instanceof CubridUser user) {
                    users.add(user);
                }
            }
        } catch (DBException e) {
            settings.addError("Load User: Can't read user list - " + e.getMessage());
        }
        return users;
    }

    public List<CubridTable> loadTables(List<CubridUser> users) {
        List<CubridTable> tables = new ArrayList<>();
        try {
            for (String name : settings.getTables()) {
                if (monitor.isCanceled()) break;
                String[] values = name.split("\\.");
                if (values.length == 2) {
                    CubridUser user = (CubridUser) dataSource.getSchema(values[0]);
                    if (user == null) continue;
                    CubridTable table = (CubridTable) user.getTable(monitor, values[1]);
                    if (table != null) tables.add(table);
                } else {
                    for (CubridUser user : users) {
                        CubridTable table = (CubridTable) user.getTable(monitor, name);
                        if (table != null) {
                            tables.add(table);
                        }
                    }
                }
            }
        } catch (DBException e) {
            settings.addError("Load Table: Can't read table list - " + e.getMessage());
        }
        return tables;
    }

    public List<CubridSequence> loadSerials(List<CubridUser> users) {
        List<CubridSequence> serials = new ArrayList<>();
        try {
            for (CubridUser user : users) {
                if (monitor.isCanceled()) break;
                for (GenericSequence sequence : user.getSequences(monitor)) {
                    if (monitor.isCanceled()) break;
                    if (sequence instanceof CubridSequence serial) {
                        serials.add(serial);
                    }
                }
            }
        } catch (DBException e) {
            settings.addError("Load Serial: Can't read serial list - " + e.getMessage());
        }
        return serials;
    }

    public Map<String, String> loadCollationByAttr(GenericTableBase table) {
        String cacheKey = (isMultiSchema ? table.getSchema().getName() + "." : "") + table.getName();
        Map<String, String> cached = collationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, String> collationByAttr = new HashMap<>();
        String query = "SELECT attr_name, collation FROM db_attribute WHERE class_name = ?"
            + (isMultiSchema ? " AND owner_name = ?" : "");
        query = dataSource.wrapShardQuery(query);

        try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource, "Load Columns")) {
            try (JDBCPreparedStatement dbStat = session.prepareStatement(query)) {
                dbStat.setString(1, table.getName());
                if (isMultiSchema) {
                    dbStat.setString(2, table.getSchema().getName());
                }
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    while (dbResult.next()) {
                        String attrName = JDBCUtils.safeGetString(dbResult, "attr_name");
                        String collation = JDBCUtils.safeGetString(dbResult, "collation");
                        collationByAttr.put(attrName, collation);
                    }
                }
            }
        } catch (Exception e) {
            settings.addError("Column Collation: Can't read column collation for table " + table.getName() + " - " + e.getMessage());
        }

        collationCache.put(cacheKey, collationByAttr);
        return collationByAttr;
    }

    public SerialExtraInfo loadSerialExtraInfo(CubridSequence serial) {
        String key = (isMultiSchema ? serial.getOwner().getName() + "." : "") + serial.getName();
        SerialExtraInfo cached = serialExtraCache.get(key);
        if (cached != null) {
            return cached;
        }

        String query = "SELECT * FROM db_serial WHERE name = ?" + (isMultiSchema ? " AND owner.name = ?" : "");
        query = dataSource.wrapShardQuery(query);

        SerialExtraInfo extra = new SerialExtraInfo(null, null, false);

        try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource, "Load Serial")) {
            try (JDBCPreparedStatement dbStat = session.prepareStatement(query)) {
                dbStat.setString(1, serial.getName());
                if (isMultiSchema) {
                    dbStat.setString(2, serial.getOwner().getName());
                }

                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    if (dbResult.next()) {
                        String className = JDBCUtils.safeGetString(dbResult, "class_name");
                        String attrName = JDBCUtils.safeGetString(dbResult, "att_name");
                        if (attrName == null) {
                            attrName = JDBCUtils.safeGetString(dbResult, "attr_name");
                        }
                        boolean started = JDBCUtils.safeGetBoolean(dbResult, "started");
                        extra = new SerialExtraInfo(className, attrName, started);
                    }
                }
            }
        } catch (Exception e) {
            settings.addError("Serial Extra Info: Can't read serial extra info for " + serial.getName() + " - " + e.getMessage());
        }

        serialExtraCache.put(key, extra);
        return extra;
    }

    public CubridSequence findSerial(CubridTable table, String columnName, List<CubridSequence> serials) {
        for (CubridSequence serial : serials) {
            SerialExtraInfo extra = loadSerialExtraInfo(serial);

            boolean sameTable = extra.className != null && table.getName().equalsIgnoreCase(extra.className);
            boolean sameColumn = extra.attrName != null && columnName.equalsIgnoreCase(extra.attrName);

            boolean sameOwner = true;
            if (isMultiSchema) {
                sameOwner = table.getSchema().equals(serial.getOwner());
            }

            if (sameTable && sameColumn && sameOwner) {
                return serial;
            }
        }
        return null;
    }

    public String loadCreateViewDDL(CubridView view) {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, view, "Load view ddl")) {
            String sql = String.format("show create view %s", view.getFullyQualifiedName(DBPEvaluationContext.DDL));
            sql = dataSource.wrapShardQuery(sql);

            try (JDBCPreparedStatement dbStat = session.prepareStatement(sql);
                 JDBCResultSet dbResult = dbStat.executeQuery()) {

                if (dbResult.next()) {
                    return JDBCUtils.safeGetStringTrimmed(dbResult, "Create View");
                }
            }
        } catch (Exception e) {
            settings.addError("View Query: Can't read view metadata for " + view.getName() + " - " + e.getMessage());
        }
        return null;
    }

    private void writeFile(StringBuilder builder, String fileName, String fileCharset, boolean append) {
        try (BufferedWriter w = (fileCharset != null && fileCharset.trim().length() > 0)
            ? new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(fileName, append), fileCharset))
            : new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(fileName, append)))
        ) {
            w.write(builder.toString());
        } catch (Exception e) {
            settings.addError("Error saving file: Error while writing file " + fileName + " - " + e.getMessage());
        }
    }

    public void saveFile(StringBuilder builder, String fileName, String fileCharset) {
        writeFile(builder, fileName, fileCharset, false);
    }

    public void appendFile(StringBuilder builder, String fileName, String fileCharset) {
        writeFile(builder, fileName, fileCharset, true);
    }

    public static class SerialExtraInfo {
        public final String className;
        public final String attrName;
        public final boolean started;

        public SerialExtraInfo(String className, String attrName, boolean started) {
            this.className = className;
            this.attrName = attrName;
            this.started = started;
        }
    }
}
