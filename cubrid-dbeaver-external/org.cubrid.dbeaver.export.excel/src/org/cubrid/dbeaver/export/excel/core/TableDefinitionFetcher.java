package org.cubrid.dbeaver.export.excel.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTableColumn;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.ext.generic.model.GenericTableConstraintColumn;
import org.jkiss.dbeaver.ext.generic.model.GenericUniqueKey;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;

public class TableDefinitionFetcher {

    public static List<CubridTable> getTables(DBRProgressMonitor monitor, CubridDataSource dataSource) throws DBException {
        List<CubridTable> tables = new ArrayList<>();
        for (GenericSchema schema : dataSource.getCubridUsers(monitor)) {
            if (schema instanceof CubridUser user) {
                tables.addAll(user.getPhysicalTables(monitor));
            }
        }
        return tables;
    }

    public static List<CubridTableColumn> getColumns(DBRProgressMonitor monitor, CubridTable table) throws DBException {
        return table.getAttributes(monitor);
    }

    public static List<GenericUniqueKey> getConstraints(DBRProgressMonitor monitor, CubridTable table) throws DBException {
        return table.getConstraints(monitor);
    }

    public static Set<String> getPrimaryKeyColumnNames(DBRProgressMonitor monitor, CubridTable table) throws DBException {
        Set<String> pkColumnNames = new HashSet<>();
        for (GenericUniqueKey pk : getConstraints(monitor, table)) {
            if (pk.getConstraintType() == DBSEntityConstraintType.PRIMARY_KEY) {
                List<GenericTableConstraintColumn> refs = pk.getAttributeReferences(monitor);
                if (refs == null) {
                    continue;
                }

                for (GenericTableConstraintColumn pkColumn : refs) {
                    if (pkColumn != null && pkColumn.getAttribute() != null) {
                        pkColumnNames.add(pkColumn.getAttribute().getName());
                    }
                }
            }
        }
        return pkColumnNames;
    }

    public static List<IndexKey> getIndexes(DBRProgressMonitor monitor, CubridTable table) throws DBException {
        List<IndexKey> indexColumns = new ArrayList<>();
        boolean isSupportMultiSchema = table.getDataSource().getSupportMultiSchema();

        String query = "SELECT k.index_name, k.key_attr_name, k.asc_desc, k.key_order + 1 AS key_position\n"
                + "FROM db_index_key k JOIN db_index i ON k.index_name = i.index_name\n"
                + (isSupportMultiSchema ? "AND k.owner_name = i.owner_name\n" : "")
                + "AND k.class_name = i.class_name AND i.is_foreign_key = 'NO'\n"
                + "WHERE k.class_name = ?\n"
                + (isSupportMultiSchema ? "AND k.owner_name = ?\n" : "")
                + "ORDER BY k.index_name, k.key_order";

        query = table.getDataSource().wrapShardQuery(query);

        try (JDBCSession session = DBUtils.openMetaSession(monitor, table, "Load Indexes");
            JDBCPreparedStatement dbStat = session.prepareStatement(query)) {

            dbStat.setString(1, table.getName());
            if (isSupportMultiSchema) {
                dbStat.setString(2, table.getSchema().getName());
            }

            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                while (dbResult.next()) {
                    String indexName = JDBCUtils.safeGetString(dbResult, "index_name");
                    if (indexName == null) {
                        continue;
                    }
                    String columnName = JDBCUtils.safeGetString(dbResult, "key_attr_name");
                    int keyPosition = JDBCUtils.safeGetInteger(dbResult, "key_position");

                    IndexKey index = indexColumns.stream().filter(k -> k.getIndexName().equals(indexName)).findFirst().orElse(null);
                    if (index != null) {
                        index.addColumn(columnName, keyPosition);
                    } else {
                        index = new IndexKey(indexName);
                        index.addColumn(columnName, keyPosition);
                        indexColumns.add(index);
                    }
                }
            }
        } catch (SQLException | DBCException e) {
            throw new DBException("Failed to load indexes for table: " + table.getName(), e);
        }
        return indexColumns;
    }

    public static String getDDL(DBRProgressMonitor monitor, CubridTable table) throws DBException {
        Map<String, Object> options = new HashMap<>();
        options.put("ddl.source", true);
        options.put("ddl.separateForeignKeys", false);

        String ddl = table.getObjectDefinitionText(monitor, options);
        if (ddl == null || ddl.isBlank()) {
            return "";
        }

        String[] lines = ddl.split("\\r?\\n");
        int startIdx = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().toUpperCase().startsWith("CREATE")) {
                startIdx = i;
                break;
            }
        }
        return Arrays.stream(lines).skip(startIdx).collect(Collectors.joining(System.lineSeparator())).trim();
    }

    public static class IndexKey {
        private String indexName;
        private List<IndexColumn> columns = new ArrayList<>();

        public IndexKey(String indexName) {
            this.indexName = indexName;
        }

        public void addColumn(String columnName, int keyPosition) {
            this.columns.add(new IndexColumn(columnName, keyPosition));
        }

        public String getIndexName() { return indexName; }
        public List<IndexColumn> getColumns() { return columns; }

    }

    public static class IndexColumn {
        private String columnName;
        private int keyPosition;

        public IndexColumn(String columnName, int keyPosition) {
            this.columnName = columnName;
            this.keyPosition = keyPosition;
        }

        public String getColumnName() { return columnName; }
        public int getKeyPosition() { return keyPosition; }
    }
}
