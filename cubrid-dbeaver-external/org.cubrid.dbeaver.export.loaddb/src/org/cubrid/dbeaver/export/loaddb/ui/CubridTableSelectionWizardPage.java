package org.cubrid.dbeaver.export.loaddb.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.ext.generic.model.GenericSchema;
import org.jkiss.dbeaver.ext.generic.model.GenericTable;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.CustomSashForm;

public class CubridTableSelectionWizardPage extends WizardPage {

    private Table tableTables;
    private Button selectAllButton;
    private Composite stack;
    private StackLayout stackLayout;
    private Composite tablePanel;
    private Composite emptyPanel;
    private Label emptyLabel;
    private List<CubridTable> checkedTables = new ArrayList<>();
    private CubridLoadDBExportWizard wizard;
    private CubridUser selectedSchema;

    public CubridTableSelectionWizardPage(CubridLoadDBExportWizard wizard, CubridUser selectedSchema) {
        super("Choose tables to export");
        setTitle("Choose tables");
        setDescription("Select the tables you want to export");
        this.wizard = wizard;
        this.selectedSchema = selectedSchema;
    }

    @Override
    public void createControl(Composite parent) {
        Composite composite = UIUtils.createPlaceholder(parent, 1);
        setPageComplete(false);

        Group objectsGroup = UIUtils.createControlGroup(composite, "Tables", 1, GridData.FILL_HORIZONTAL, 0);
        objectsGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

        SashForm sash = new CustomSashForm(objectsGroup, SWT.VERTICAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));

        Composite catPanel = UIUtils.createComposite(sash, 1);
        catPanel.setLayoutData(new GridData(GridData.FILL_BOTH));

        stack = new Composite(catPanel, SWT.NONE);
        stack.setLayoutData(new GridData(GridData.FILL_BOTH));
        stackLayout = new StackLayout();
        stack.setLayout(stackLayout);

        tablePanel = new Composite(stack, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(1).applyTo(tablePanel);
        tablePanel.setLayoutData(new GridData(GridData.FILL_BOTH));

        tableTables = new Table(tablePanel, SWT.BORDER | SWT.CHECK);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.heightHint = 50;
        tableTables.setLayoutData(gd);

        emptyPanel = new Composite(stack, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(1).applyTo(emptyPanel);
        emptyPanel.setLayoutData(new GridData(GridData.FILL_BOTH));

        emptyLabel = new Label(emptyPanel, SWT.CENTER);
        emptyLabel.setText("No tables found in this schema");
        emptyLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

        stackLayout.topControl = emptyPanel;
        stack.layout(true, true);

        selectAllButton = new Button(catPanel, SWT.CHECK);
        selectAllButton.setText("Select All");
        selectAllButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        selectAllButton.setEnabled(false);

        selectAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean checked = selectAllButton.getSelection();
                for (TableItem item : tableTables.getItems()) {
                    item.setChecked(checked);
                }
                updateState();
            }
        });

        tableTables.addListener(SWT.Selection, event -> {
            if (event.detail == SWT.CHECK) {
                updateSelectAllState(selectAllButton);
                updateState();
            }
        });

        loadTables();
        setControl(composite);
    }

    private void loadTables() {
        new AbstractJob("Load tables") {
            {
                setUser(true);
            }

            @Override
            protected IStatus run(DBRProgressMonitor monitor) {
                try {
                    final List<? extends GenericTable> tables;

                    if (selectedSchema != null) {
                        tables = selectedSchema.getPhysicalTables(monitor);
                    } else {
                        List<GenericTable> all = new ArrayList<>();
                        for (GenericSchema schema : wizard.getSettings().getDataSource().getCubridUsers(monitor)) {
                            all.addAll(schema.getPhysicalTables(monitor));
                        }
                        tables = all;
                    }

                    UIUtils.syncExec(() -> {
                        if (tableTables == null || tableTables.isDisposed()) {
                            return;
                        }

                        tableTables.removeAll();

                        for (GenericTable t : tables) {
                            CubridTable ct = (CubridTable) t;

                            TableItem item = new TableItem(tableTables, SWT.NONE);
                            item.setImage(DBeaverIcons.getImage(DBIcon.TREE_TABLE));
                            item.setText(ct.getUniqueName());
                            item.setData(ct);
                            item.setChecked(checkedTables.contains(ct));
                        }

                        boolean hasTables = tableTables.getItemCount() > 0;

                        selectAllButton.setEnabled(hasTables);
                        selectAllButton.setSelection(false);

                        stackLayout.topControl = hasTables ? tablePanel : emptyPanel;
                        stack.layout(true, true);

                        if (!hasTables) {
                            checkedTables.clear();
                            wizard.getSettings().setTables(new ArrayList<>());
                            setPageComplete(false);
                        }
                    });

                    return Status.OK_STATUS;

                } catch (Exception e) {
                    UIUtils.syncExec(() ->
                        DBWorkbench.getPlatformUI().showError("Table List", "Can't read Table list", e)
                    );
                    return Status.CANCEL_STATUS;
                }
            }
        }.schedule();
    }

    protected void updateState() {
        updateCheckedTables();
        setPageComplete(!checkedTables.isEmpty());
    }

    private void updateCheckedTables() {
        checkedTables.clear();
        List<String> tables = new ArrayList<>();

        for (TableItem item : tableTables.getItems()) {
            if (item.getChecked()) {
                checkedTables.add((CubridTable) item.getData());
                tables.add(item.getText());
            }
        }
        this.wizard.getSettings().setTables(tables);
    }

    private void updateSelectAllState(Button selectAllButton) {
        TableItem[] items = tableTables.getItems();
        if (items.length == 0) {
            selectAllButton.setSelection(false);
            return;
        }

        for (TableItem item : items) {
            if (!item.getChecked()) {
                selectAllButton.setSelection(false);
                return;
            }
        }
        selectAllButton.setSelection(true);
    }
}