package org.cam.dbeaver.cubrid.export.excel.handler;

import org.cam.dbeaver.cubrid.export.excel.ui.ExcelExportDialog;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;

public class ExcelExportHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final Shell activeShell = HandlerUtil.getActiveShell(event);
        final ISelection selection = HandlerUtil.getCurrentSelection(event);
        final DBNNode node = NavigatorUtils.getSelectedNode(selection);
        if (node instanceof DBNDataSource dataSourceNode) {
            DataSourceDescriptor descriptor = (DataSourceDescriptor) dataSourceNode.getDataSourceContainer();
            DBPDataSource dataSource = descriptor.getDataSource();
            if (!(dataSource instanceof CubridDataSource cubrid)) {
                return null;
            }
            try {
                ExcelExportDialog dialog = new ExcelExportDialog(activeShell, cubrid);
                dialog.open();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
	}

}
