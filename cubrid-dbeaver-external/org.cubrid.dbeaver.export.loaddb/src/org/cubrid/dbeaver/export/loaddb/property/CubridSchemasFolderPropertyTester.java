package org.cubrid.dbeaver.export.loaddb.property;

import org.eclipse.core.expressions.PropertyTester;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseFolder;

public class CubridSchemasFolderPropertyTester extends PropertyTester {

    public static final String PROP_IS_SCHEMAS_FOLDER = "isSchemasFolder";

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        if (!PROP_IS_SCHEMAS_FOLDER.equals(property) || !(receiver instanceof DBNDatabaseFolder folder)) {
            return false;
        }
        String nodeName = folder.getName();
        return nodeName != null && nodeName.equalsIgnoreCase("Schemas");
    }
}
