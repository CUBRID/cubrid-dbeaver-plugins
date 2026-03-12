package org.cam.dbeaver.cubrid.export.excel.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;

public class TableDefinitionFetcher {
	private static final DBRProgressMonitor monitor = new VoidProgressMonitor();

	public static List<CubridTable> getTables(CubridDataSource dataSource) {
    	List<CubridTable> tables = new ArrayList<>();
    	try {
			for (GenericSchema schema : dataSource.getCubridUsers(monitor)) {
				if (schema instanceof CubridUser user) {
					tables.addAll(user.getPhysicalTables(monitor));
				}
			}
		} catch (DBException e) {
			e.printStackTrace();
		}
    	return tables;
    }

	public static List<CubridTableColumn> getColumns(CubridTable table) {
		List<CubridTableColumn> columns = new ArrayList<>();
		try {
			columns = table.getAttributes(monitor);
		} catch (DBException e) {
			e.printStackTrace();
		}
		return columns;
	}
	
	public static boolean isPrimaryKey(CubridTable table, CubridTableColumn column) {
		for (GenericUniqueKey pk : getConstraints(table)) {
			for (GenericTableConstraintColumn pkColumn : pk.getAttributeReferences(monitor)) {
				if (column.equals(pkColumn.getAttribute()) && pk.getConstraintType().equals(DBSEntityConstraintType.PRIMARY_KEY)) {
					return true;
				}
			}
		}
		return false;
	}

	public static List<GenericUniqueKey> getConstraints(CubridTable table) {
		List<GenericUniqueKey> constraints = new ArrayList<>();
		try {
			constraints = table.getConstraints(monitor);
		} catch (DBException e) {
			e.printStackTrace();
		}
		return constraints;
	}

	public static List<IndexKey> getIndexes(CubridTable table) {
		List<IndexKey> indexColumns = new ArrayList<>();
		boolean isSupportMultiSchema = table.getDataSource().getSupportMultiSchema();
		String query = "SELECT k.*, k.key_order + 1 AS ordering FROM db_index_key k\n"
				+ "JOIN db_index i ON k.index_name = i.index_name\n"
				+ (isSupportMultiSchema ? "AND k.owner_name = i.owner_name\n" : "")
				+ "AND k.class_name = i.class_name\n"
				+ "AND i.is_foreign_key = 'NO'\n"
				+ "WHERE k.class_name = ?\n"
				+ (isSupportMultiSchema ? "AND k.owner_name = ?\n" : "")
				+ "ORDER BY k.index_name";
		query = table.getDataSource().wrapShardQuery(query);
		try (JDBCSession session = DBUtils.openMetaSession(monitor, table, "Load Indexes")) {
	        try (JDBCPreparedStatement dbStat = session.prepareStatement(query)) {
	        	dbStat.setString(1, table.getName());
	            if (isSupportMultiSchema) {
	                dbStat.setString(2, table.getSchema().getName());
	            }
			    try (JDBCResultSet dbResult = dbStat.executeQuery()) {
			        while (dbResult.next()) {
			        	String indexName = JDBCUtils.safeGetString(dbResult, "index_name");
			        	String columnName = JDBCUtils.safeGetString(dbResult, "key_attr_name");
			        	int ordering = JDBCUtils.safeGetInteger(dbResult, "ordering");

			        	IndexKey index = indexColumns.stream().filter(k -> k.getIndexName().equals(indexName)).findFirst().orElse(null);
			        	if (index != null) {
			        		index.addColumn(columnName, ordering);
			        	} else {
			        		index = new IndexKey(indexName);
			        		index.addColumn(columnName, ordering);
			        		indexColumns.add(index);
			        	}
			        }
			    }
	        } catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (DBCException e) {
			e.printStackTrace();
		}
		return indexColumns;
	}

	public static String getDDL(CubridTable table) {
		Map<String, Object> options = new HashMap<>();
		options.put("ddl.source", true);
		options.put("ddl.separateForeignKeys", false);
		try {
			String ddl = table.getObjectDefinitionText(monitor, options);
			if (ddl == null || ddl.isBlank()) {
				return "";
			}
		    return Arrays.stream(ddl.split("\\r?\\n")).skip(4)
                    .collect(Collectors.joining(System.lineSeparator())).trim();
		} catch (DBException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	public static class IndexKey {
		private String indexName;
		private List<IndexColumn> columns = new ArrayList<>();
	    
	    public IndexKey(String indexName) {
	        this.indexName = indexName;
	    }
	    
	    public void addColumn(String columnName, int ordering) {
	        this.columns.add(new IndexColumn(columnName, ordering));
	    }
	    public String getIndexName() { return indexName; }
	    public List<IndexColumn> getColumns() { return columns; }

	}
	
	 public static class IndexColumn {
        private String columnName;
        private int ordering;

        public IndexColumn(String columnName, int ordering) {
            this.columnName = columnName;
            this.ordering = ordering;
        }

        public String getColumnName() { return columnName; }
        public int getOrdering() { return ordering; }
    }
}
