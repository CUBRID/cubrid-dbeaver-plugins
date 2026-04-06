package org.cubrid.dbeaver.export.loaddb.handler;

import org.cubrid.dbeaver.export.loaddb.ui.CubridLoadDBExportWizard;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseFolder;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseItem;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;

public class CubridLoadDBExportHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        DBNNode node = NavigatorUtils.getSelectedNode(HandlerUtil.getCurrentSelection(event));
        CubridDataSource dataSource = null;
        CubridUser schema = null;
        if (node instanceof DBNDataSource dsNode) {
            DataSourceDescriptor descriptor = (DataSourceDescriptor) dsNode.getDataSourceContainer();
            dataSource = (CubridDataSource) descriptor.getDataSource();
        } else if (node instanceof DBNDatabaseItem dbNode && dbNode.getObject() instanceof CubridUser user) {
            dataSource = (CubridDataSource) user.getDataSource();
            schema = user;
        } else if (node instanceof DBNDatabaseFolder folder) {
            dataSource = (CubridDataSource) folder.getDataSource();
        }

        if (dataSource != null) {
            Shell shell = UIUtils.getActiveWorkbenchShell();
            CubridLoadDBExportWizard wizard = new CubridLoadDBExportWizard(dataSource, schema);
            return new WizardDialog(shell, wizard).open();
        }
        return null;
    }

}
