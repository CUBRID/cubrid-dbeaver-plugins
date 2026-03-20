package org.cubrid.dbeaver.export.loaddb.core;

import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;

import org.cubrid.dbeaver.export.loaddb.model.CubridExportSettings;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridPartition;
import org.jkiss.dbeaver.ext.cubrid.model.CubridSequence;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTableColumn;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTableIndex;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTrigger;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.ext.cubrid.model.CubridView;
import org.jkiss.dbeaver.ext.generic.model.GenericTableBase;
import org.jkiss.dbeaver.ext.generic.model.GenericTableConstraintColumn;
import org.jkiss.dbeaver.ext.generic.model.GenericTableForeignKey;
import org.jkiss.dbeaver.ext.generic.model.GenericTableForeignKeyColumnTable;
import org.jkiss.dbeaver.ext.generic.model.GenericTableIndex;
import org.jkiss.dbeaver.ext.generic.model.GenericTableIndexColumn;
import org.jkiss.dbeaver.ext.generic.model.GenericUniqueKey;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.utils.CommonUtils;

public class CubridLoadDBSQLBuilder {

    private DBRProgressMonitor monitor;
    private CubridDataSource dataSource;
    private CubridExportSettings settings;
    private CubridLoadDBRepository repo;
    private boolean isMultiSchema;

    public CubridLoadDBSQLBuilder(
        DBRProgressMonitor monitor,
        CubridDataSource dataSource,
        CubridExportSettings settings,
        CubridLoadDBRepository repo
    ) {
        this.monitor = monitor;
        this.dataSource = dataSource;
        this.settings = settings;
        this.isMultiSchema = dataSource.getSupportMultiSchema();
        this.repo = repo;
    }

    public void buildTable(StringBuilder sb, List<CubridTable> tables, List<CubridSequence> serials) {
        // CREATE CLASS
        for (CubridTable table : tables) {
            String isReuseOID = table.isReuseOID() ? "REUSE_OID" : "DONT_REUSE_OID";
            sb.append(String.format(
                "CREATE CLASS %s %s, COLLATE %s%s;\n",
                wrapTable(table),
                isReuseOID,
                table.getCollation().getName(),
                CommonUtils.isEmpty(table.getDescription())
                    ? ""
                    : " COMMENT " + SQLUtils.quoteString(table, table.getDescription())
            ));
            if (!isMultiSchema) {
                sb.append(String.format(
                    "call [change_owner]('%s', '%s') on class [db_root];\n",
                    table.getName(),
                    table.getSchema().getName()
                ));
            }
            sb.append(System.lineSeparator());
        }

        // ALTER CLASS ADD ATTRIBUTE + constraints + partition
        for (CubridTable table : tables) {
            Map<String, String> collationByAttr = repo.loadCollationByAttr(table);

            sb.append(String.format("ALTER CLASS %s ADD ATTRIBUTE", wrapTable(table)));
            try {
                List<CubridTableColumn> columns = table.getAttributes(monitor);

                for (CubridTableColumn column : columns) {
                    String collation = collationByAttr.get(column.getName());
                    sb.append(String.format("\n\t%s %s", wrapString(column.getName()), column.getFullTypeName()));

                    if (collation != null && !collation.equalsIgnoreCase("Not applicable")) {
                        sb.append(String.format(" COLLATE %s", collation));
                    }

                    if (column.isAutoIncrement()) {
                        CubridSequence serial = repo.findSerial(table, column.getName(), serials);
                        if (serial != null) {
                            sb.append(String.format(" AUTO_INCREMENT(%s, %s)", serial.getMinValue(), serial.getIncrementBy()));
                        }
                    }

                    if (column.isRequired()) {
                        sb.append(" NOT NULL");
                    }

                    if (!CommonUtils.isEmpty(column.getDescription())) {
                        sb.append(" COMMENT ").append(SQLUtils.quoteString(column, column.getDescription()));
                    }

                    sb.append(",");
                }

                sb.deleteCharAt(sb.length() - 1).append(";\n");
                buildConstraint(sb, table);

                sb.append(System.lineSeparator());

                if (table.isPartitioned()) {
                    buildPartition(sb, table);
                }
            } catch (DBException e) {
                DBWorkbench.getPlatformUI().showError("Table List", "Can't read table list", e);
            }
        }

        buildSerial(sb, tables, serials);
    }

    private void buildConstraint(StringBuilder sb, CubridTable table) {
        try {
            List<GenericUniqueKey> keys = table.getConstraints(monitor);
            if (keys == null || keys.isEmpty()) {
                return;
            }

            for (GenericUniqueKey key : keys) {
                String constraintType = key.getConstraintType().getName();
                List<GenericTableConstraintColumn> cols = key.getAttributeReferences(monitor);

                StringBuilder colBuilder = new StringBuilder();
                for (int i = 0; i < cols.size(); i++) {
                    colBuilder.append(wrapString(cols.get(i).getName()));
                    if (i != cols.size() - 1) {
                        colBuilder.append(", ");
                    }
                }

                sb.append(String.format(
                    "ALTER CLASS %s ADD ATTRIBUTE \n\tCONSTRAINT %s %s (%s);\n",
                    wrapTable(table),
                    wrapString(key.getName()),
                    constraintType,
                    colBuilder
                ));
            }
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError("Constraints List", "Can't read constraint list", e);
        }
    }

    private void buildPartition(StringBuilder sb, CubridTable table) throws DBException {
        List<CubridPartition> partitions = table.getPartitions(monitor);
        if (partitions == null || partitions.isEmpty()) {
            return;
        }

        String type = partitions.get(0).getTableType().toUpperCase();
        String key = partitions.get(0).getExpression();
        CubridTableColumn column = (CubridTableColumn) table.getAttribute(monitor, key);

        sb.append(String.format(
            "ALTER CLASS %s PARTITION BY %s (%s)",
            wrapTable(table),
            type,
            wrapString(key)
        ));

        if ("HASH".equals(type)) {
            sb.append(" PARTITIONS ").append(partitions.size()).append(";\n\n");
            return;
        }

        sb.append(" (");
        for (CubridPartition partition : partitions) {
            String value = partition.getExpressionValues();

            sb.append("\n\tPARTITION ").append(wrapString(partition.getPartitionName()));

            if ("RANGE".equals(type)) {
                sb.append(" VALUES LESS THAN ");
                if ("MAXVALUE".equalsIgnoreCase(value)) {
                    sb.append("MAXVALUE");
                } else {
                    sb.append("(").append(DBPDataKind.NUMERIC == column.getDataKind()
                        ? value
                        : SQLUtils.quoteString(partition, value)
                    ).append(")");
                }
            } else {
                sb.append(" VALUES IN ");
                sb.append("(").append(DBPDataKind.NUMERIC == column.getDataKind()
                    ? value
                    : "'" + value.replaceAll(",\\s*", "', '") + "'"
                ).append(")");
            }

            if (!CommonUtils.isEmpty(partition.getDescription())) {
                sb.append(" COMMENT ").append(SQLUtils.quoteString(partition, partition.getDescription()));
            }

            sb.append(",");
        }

        sb.deleteCharAt(sb.length() - 1).append(" );\n\n");
    }

    private void buildSerial(StringBuilder sb, List<CubridTable> tables, List<CubridSequence> serials) {
        // ALTER SERIAL START WITH
        for (CubridSequence serial : serials) {
            CubridLoadDBRepository.SerialExtraInfo extra = repo.loadSerialExtraInfo(serial);
            String className = extra.className;
            boolean isStarted = extra.started;

            if (className == null) {
                continue;
            }
            if (!settings.isExportStartValue()) {
                continue;
            }

            CubridTable matchedTable = null;
            for (CubridTable table : tables) {
                if (className.equalsIgnoreCase(table.getName())) {
                    matchedTable = table;
                    break;
                }
            }
            if (matchedTable == null) {
                continue;
            }
            if (isMultiSchema && !matchedTable.getSchema().equals(serial.getOwner())) {
                continue;
            }

            sb.append(String.format(
                "ALTER SERIAL %s START WITH %s;\n",
                serialUniqueName(serial),
                serial.getStartValue()
            ));
            if (isStarted) {
                sb.append(String.format("SELECT %s.NEXT_VALUE;\n", serialUniqueName(serial)));
            }
            sb.append(System.lineSeparator());
        }

        // CREATE SERIAL
        for (CubridSequence serial : serials) {
            CubridLoadDBRepository.SerialExtraInfo extra = repo.loadSerialExtraInfo(serial);
            if (extra.className != null) {
                continue;
            }

            sb.append(String.format(
                "CREATE SERIAL %s \n\tSTART WITH %s \n\tINCREMENT BY %s \n\tMINVALUE %s \n\tMAXVALUE %s \n\t%s \n\t%s%s;\n",
                serialUniqueName(serial),
                serial.getStartValue(),
                serial.getIncrementBy(),
                serial.getMinValue(),
                serial.getMaxValue(),
                serial.getCycle() ? "CYCLE" : "NOCYCLE",
                serial.getCachedNum() == 0 ? "NOCACHE" : "CACHE " + serial.getCachedNum(),
                (serial.getDescription() == null || serial.getDescription().isEmpty())
                    ? ""
                    : " \n\tCOMMENT " + SQLUtils.quoteString(dataSource, CommonUtils.notEmpty(serial.getDescription()))
            ));

            if (!isMultiSchema) {
                sb.append(String.format(
                    "call [change_serial_owner] ('%s', '%s') on class [db_serial];\n",
                    serial.getName(),
                    serial.getOwner().getName()
                ));
            }

            sb.append(System.lineSeparator());
        }
    }

    public void buildForeignKey(StringBuilder sb, List<CubridTable> tables) {
        for (CubridTable table : tables) {
            String query = "SELECT * FROM db_index WHERE is_foreign_key = 'YES' AND class_name = ?"
                    + (isMultiSchema ? " AND owner_name = ?" : "");
            query = dataSource.wrapShardQuery(query);
            try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource, "Load Foreign Key")) {
                try (JDBCPreparedStatement dbStat = session.prepareStatement(query)) {
                    dbStat.setString(1, table.getName());
                    if (isMultiSchema) {
                        dbStat.setString(2, table.getSchema().getName());
                    }
    
                    try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                        while (dbResult.next()) {
                            String fkName = JDBCUtils.safeGetString(dbResult, "index_name");
                            GenericTableForeignKey fk = table.getAssociation(monitor, fkName);
                            if (fk == null) {
                                continue;
                            }
                            String deleteRule = fk.getDeleteRule().getName().toUpperCase();
                            String updateRule = fk.getUpdateRule().getName().toUpperCase();
                            CubridTable refTab = (CubridTable) fk.getReferencedTable();
    
                            List<GenericTableForeignKeyColumnTable> fkCols = fk.getAttributeReferences(monitor);
                            if (fkCols == null || fkCols.isEmpty()) {
                                continue;
                            }
                         
                            StringBuilder fkColsBuilder = new StringBuilder();
                            for (int i = 0; i < fkCols.size(); i++) {
                                fkColsBuilder.append(wrapString(fkCols.get(i).getName()));
                                if (i != fkCols.size() - 1) {
                                    fkColsBuilder.append(", ");
                                }
                            }

                            StringBuilder refColsBuilder = new StringBuilder();
                            for (GenericUniqueKey key : refTab.getConstraints(monitor)) {
                                if (key.getConstraintType() == DBSEntityConstraintType.PRIMARY_KEY) {
                                    List<GenericTableConstraintColumn> refCols = key.getAttributeReferences(monitor);
                                    for (int i = 0; i < refCols.size(); i++) {
                                        refColsBuilder.append(wrapString(refCols.get(i).getName()));
                                        if (i != refCols.size() - 1) {
                                            refColsBuilder.append(", ");
                                        }
                                    }
                                    break;
                                }
                            }
    
                            sb.append(String.format(
                                "ALTER CLASS %s ADD CONSTRAINT %s FOREIGN KEY (%s)%s REFERENCES %s(%s) ON DELETE %s ON UPDATE %s;\n\n",
                                wrapTable(table),
                                wrapString(fk.getName()),
                                fkColsBuilder,
                                isMultiSchema ? " WITH DEDUPLICATE=0" : "",
                                wrapTable(refTab),
                                refColsBuilder,
                                deleteRule,
                                updateRule
                            ));
                        }
                    }
                }
            } catch (Exception e) {
            	DBWorkbench.getPlatformUI().showError("Foreign Key List", "Can't read foreign key list", e);
            }
        }
    }

    public void buildView(StringBuilder sb, List<CubridUser> users) {
        try {
            for (CubridUser user : users) {
                for (CubridView view : user.getViews(monitor)) {
                    sb.append(String.format("CREATE VCLASS %s;\n", wrapTable(view)));
                    if (!isMultiSchema) {
                        sb.append(String.format(
                            "call [change_owner]('%s', '%s') on class [db_root];\n",
                            view.getName(),
                            view.getSchema().getName()
                        ));
                    }
                    sb.append(System.lineSeparator());
                }
            }
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError("View List", "Can't read view list", e);
        }
    }

    public void buildViewQuerySpec(StringBuilder sb, List<CubridUser> users) {
        try {
            for (CubridUser user : users) {
                for (CubridView view : user.getViews(monitor)) {
                    appendViewAttributes(sb, view);
                }
            }
            for (CubridUser user : users) {
                for (CubridView view : user.getViews(monitor)) {
                    appendViewQuery(sb, view);
                }
            }
        } catch (Exception e) {
            DBWorkbench.getPlatformUI().showError("View List", "Can't read view metadata", e);
        }
    }

    private void appendViewAttributes(StringBuilder sb, CubridView view) {
        try {
            Map<String, String> collationByAttr = repo.loadCollationByAttr(view);
            @SuppressWarnings("unchecked")
            List<CubridTableColumn> columns = (List<CubridTableColumn>) view.getAttributes(monitor);

            sb.append(String.format("ALTER VCLASS %s ADD ATTRIBUTE", wrapTable(view)));
            for (CubridTableColumn column : columns) {
                String collation = collationByAttr.get(column.getName());
                sb.append(String.format("\n\t%s %s", wrapString(column.getName()), column.getFullTypeName()));
                if (collation != null && !collation.equalsIgnoreCase("Not applicable")) {
                    sb.append(String.format(" COLLATE %s", collation));
                }
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append(";\n\n");
        } catch (Exception e) {
            DBWorkbench.getPlatformUI().showError("View Attribute", "Can't read view metadata", e);
        }
    }

    private void appendViewQuery(StringBuilder sb, CubridView view) {
        try {
            String query = repo.loadCreateViewDDL(view);
            if (query == null) {
                return;
            }
            sb.append(String.format("ALTER VCLASS %s ADD QUERY %s;\n\n", wrapTable(view), query));
        } catch (Exception e) {
            DBWorkbench.getPlatformUI().showError("View Query", "Can't read view metadata", e);
        }
    }

    public void buildIndex(StringBuilder sb, CubridTable table) {
        try {
            List<CubridTableIndex> indexes = table.getIndexes(monitor);
            if (indexes == null || indexes.isEmpty()) {
                return;
            }

            for (GenericTableIndex index : indexes) {
                if (index.isUnique()) {
                    continue;
                }
                List<GenericTableIndexColumn> cols = index.getAttributeReferences(monitor);

                StringBuilder colBuilder = new StringBuilder();
                for (int i = 0; i < cols.size(); i++) {
                    String rule = cols.get(i).isAscending() ? "" : " DESC";
                    colBuilder.append(wrapString(cols.get(i).getName())).append(rule);
                    if (i != cols.size() - 1) {
                        colBuilder.append(", ");
                    }
                }

                sb.append(String.format(
                    "CREATE INDEX %s ON %s(%s)%s;\n\n",
                    wrapString(index.getName()),
                    wrapTable(table),
                    colBuilder,
                    isMultiSchema ? " WITH DEDUPLICATE=0" : ""
                ));
            }
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError("Index List", "Can't read Index list", e);
        }
    }

    public void buildTrigger(StringBuilder sb, List<CubridUser> users) {
        try {
            for (CubridUser user : users) {
                @SuppressWarnings("unchecked")
                List<CubridTrigger> triggers = (List<CubridTrigger>) user.getTriggers(monitor);
                if (triggers == null || triggers.isEmpty()) {
                    continue;
                }

                for (CubridTrigger trigger : triggers) {
                    String triggerUniqueName = isMultiSchema
                        ? wrapString(trigger.getOwner().getName()) + "." + wrapString(trigger.getName())
                        : wrapString(trigger.getName());

                    sb.append(String.format(
                        "CREATE TRIGGER %s \n  %s \n  PRIORITY %s \n  %s ",
                        triggerUniqueName,
                        trigger.getActive() ? "STATUS ACTIVE" : "STATUS INACTIVE",
                        trigger.getPriority(),
                        trigger.getActionTime()
                    ));

                    if ("COMMIT".equals(trigger.getEvent()) || "ROLLBACK".equals(trigger.getEvent())) {
                        sb.append(trigger.getEvent());
                    } else {
                        sb.append(trigger.getEvent());
                        sb.append(" ON ").append(wrapTable(trigger.getTable()));
                        if (trigger.getEvent().contains("UPDATE") && trigger.getTargetColumn() != null) {
                            sb.append("(").append(wrapString(trigger.getTargetColumn())).append(")");
                        }
                    }

                    if (trigger.getCondition() != null) {
                        sb.append("\nIF ").append(trigger.getCondition());
                    }

                    sb.append("\n  EXECUTE ");

                    if ("REJECT".equals(trigger.getActionType()) || "INVALIDATE TRANSACTION".equals(trigger.getActionType())) {
                        sb.append(trigger.getActionType());
                    } else if ("PRINT".equals(trigger.getActionType())) {
                        sb.append(trigger.getActionType()).append(" ");
                        sb.append(trigger.getActionDefinition() == null
                            ? ""
                            : SQLUtils.quoteString(dataSource, trigger.getActionDefinition())
                        );
                    } else {
                        sb.append(trigger.getActionDefinition() == null ? "" : trigger.getActionDefinition());
                    }

                    if (trigger.getDescription() != null && !trigger.getDescription().isEmpty()) {
                        sb.append(" COMMENT ").append(SQLUtils.quoteString(dataSource, trigger.getDescription()));
                    }

                    sb.append(";\n");

                    if (!isMultiSchema) {
                        sb.append(String.format(
                            "call [change_trigger_owner]('%s', '%s') on class [db_root];\n",
                            trigger.getName(),
                            trigger.getOwner().getName()
                        ));
                    }

                    sb.append(System.lineSeparator());
                }
            }
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError("Trigger List", "Can't read trigger list", e);
        }
    }

    public void buildData(StringBuilder sb, CubridTable table) {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource.getParentObject(), "Load data")) {
            String query = dataSource.wrapShardQuery("select * from " + wrapTable(table));
            try (JDBCPreparedStatement dbStat = session.prepareStatement(query);
                JDBCResultSet dbResult = dbStat.executeQuery()) {

                ResultSetMetaData metaData = dbResult.getMetaData();
                int columnCount = metaData.getColumnCount();

                boolean[] numericColumns = new boolean[columnCount];
                String[] columnNames = new String[columnCount];

                for (int i = 1; i <= columnCount; i++) {
                    String name = metaData.getColumnName(i);
                    columnNames[i - 1] = name;
                    var attr = table.getAttribute(monitor, name);
                    numericColumns[i - 1] = attr != null && attr.getDataKind() == DBPDataKind.NUMERIC;
                }

                sb.append("%class ").append(wrapTable(table)).append(" (");
                for (int i = 1; i <= columnCount; i++) {
                    sb.append(wrapString(columnNames[i - 1]));
                    if (i != columnCount) sb.append(" ");
                }
                sb.append(")\n");

                while (dbResult.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String value = JDBCUtils.safeGetString(dbResult, i);
                        if (value == null) {
                            sb.append("NULL");
                        } else if (numericColumns[i - 1]) {
                            sb.append(value);
                        } else {
                            sb.append("'").append(value).append("'");
                        }

                        if (i != columnCount) sb.append(" ");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            DBWorkbench.getPlatformUI().showError("Load Data", "Can't read data", e);
        }
    }

    private String wrapString(String str) {
        return "[" + str + "]";
    }

    private String wrapTable(GenericTableBase table) {
        if (isMultiSchema) {
            return wrapString(table.getSchema().getName()) + "." + wrapString(table.getName());
        }
        return wrapString(table.getName());
    }

    private String serialUniqueName(CubridSequence serial) {
        if (isMultiSchema) {
            return wrapString(serial.getOwner().getName()) + "." + wrapString(serial.getName());
        }
        return wrapString(serial.getName());
    }
}
