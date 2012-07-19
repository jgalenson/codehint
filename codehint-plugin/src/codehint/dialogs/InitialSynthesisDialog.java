package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import codehint.Activator;
import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.ExpressionSkeleton;
import codehint.utils.EclipseUtils;

public class InitialSynthesisDialog extends SynthesisDialog {
	
	private final String initialSkeletonText;
	private Text skeletonInput;
	private boolean skeletonIsValid;
	private final IInputValidator skeletonValidator;
	private String skeletonResult;

    private static final int searchButtonID = IDialogConstants.CLIENT_ID;
    private Button searchButton;

    private static final int TABLE_WIDTH = 500;
    private static final int TABLE_HEIGHT = 300;
    private TableViewer tableViewer;
    private Table table;
    private SynthesisResultComparator synthesisResultComparator;
    private ArrayList<EvaluatedExpression> expressions;

	private final SynthesisWorker worker;
    private ExpressionSkeleton skeleton;

	public InitialSynthesisDialog(Shell parentShell, String varName, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, SynthesisWorker worker) {
		super(parentShell, varTypeName, varType, stack, propertyDialog);
		ExpressionSkeleton lastCrashedSkeleton = Synthesizer.getLastCrashedSkeleton(varName);
		if (lastCrashedSkeleton == null)
			this.initialSkeletonText = ExpressionSkeleton.HOLE_SYNTAX;
		else
			this.initialSkeletonText = lastCrashedSkeleton.getSugaredString();
		this.skeletonIsValid = false;
		this.skeletonValidator = new ExpressionSkeletonValidator(stack, varTypeName);
		this.skeletonResult = null;
		this.searchButton = null;
		this.tableViewer = null;
		this.table = null;
		this.synthesisResultComparator = null;
		this.expressions = null;
		this.worker = worker;
		this.skeleton = null;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		
		String message = "Give an expression skeleton that describes the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names.";
		Composite skeletonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 1);
		skeletonInput = createInput(skeletonComposite, message, initialSkeletonText, skeletonValidator, new SkeletonModifyHandler(), "skeleton");

		Composite topButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchButton = createButton(topButtonComposite, searchButtonID, "Search", true);
		searchButton.setEnabled(pdspecIsValid && skeletonIsValid);
		
		tableViewer = new TableViewer(composite, SWT.BORDER | SWT.CHECK);
		table = tableViewer.getTable();
        GridData tableData = new GridData(GridData.FILL_BOTH);
        tableData.widthHint = TABLE_WIDTH;
        tableData.heightHint = TABLE_HEIGHT;
        table.setLayoutData(tableData);
    	table.setLinesVisible(true);
    	table.setHeaderVisible(true);
    	table.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
                if (e.detail == SWT.CHECK) {
                 	for (int i = 0; i < table.getItemCount(); i++) {
                 		if (table.getItem(i).getChecked()) {
                 			okButton.setEnabled(true);
                 			return;
                 		}
                 	}
                 	okButton.setEnabled(false);
                }
            }
        });
    	TableViewerColumn column1 = addColumn("Expression", 0);
    	column1.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return getExpressionLabel((EvaluatedExpression)element);
			}
			
			/*@Override
			public String getToolTipText(Object element) {
				return "";
			}
			
			@Override
			public int getToolTipDisplayDelayTime(Object object) {
				return 100;
			}*/
		});
    	TableViewerColumn column2 = addColumn("Value", 1);
    	column2.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return getValueLabel((EvaluatedExpression)element);
			}
		});
    	tableViewer.setContentProvider(ArrayContentProvider.getInstance());
    	synthesisResultComparator = new SynthesisResultComparator();
    	//ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(table, Activator.PLUGIN_ID + "." + "candidate-selector");

		Composite bottomButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
        Button selectButton = createButton(bottomButtonComposite, IDialogConstants.SELECT_ALL_ID, "Select All", false);
        selectButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
                setAllChecked(true);
            }
        });
        Button deselectButton = createButton(bottomButtonComposite,  IDialogConstants.DESELECT_ALL_ID, "Deselect All", false);
        deselectButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
                setAllChecked(false);
            }
        });
		
		return composite;
	}
	
	private class SkeletonModifyHandler extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
        	skeletonIsValid = !hasError;
        	if (searchButton != null)
        		searchButton.setEnabled(pdspecIsValid && skeletonIsValid);
        }
		
	}
	
	private class InitialSynthesisPropertyModifyListener extends PropertyModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			super.inputChanged(hasError);
        	if (searchButton != null)
        		searchButton.setEnabled(pdspecIsValid && skeletonIsValid);
        }
		
	}
	
	@Override
	protected PropertyModifyHandler getPropertyModifyHandler() {
		return new InitialSynthesisPropertyModifyListener();
	}
    
    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	if (id == IDialogConstants.OK_ID)  // We want the search button to be the default, but our superclass Dialog makes the OK button default.
    		defaultButton = false;
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID)
    		button.setEnabled(false);
    	return button;
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == searchButtonID) {
        	skeletonResult = skeletonInput.getText();
            property = propertyDialog.computeProperty(pdspecInput.getText());
            skeleton = ExpressionSkeleton.fromString(skeletonResult);
            enableCancel(false);
            expressions = new ArrayList<EvaluatedExpression>();
        	// Reset column sort indicators.
        	tableViewer.setComparator(null);  // We want to use the order in which we add elements as the initial sort.
        	table.setSortDirection(SWT.NONE);
    		table.setSortColumn(null);
    		// Start the synthesis
	    	worker.synthesize(this);
        } else if (buttonId == IDialogConstants.OK_ID) {
         	results = new ArrayList<EvaluatedExpression>();
         	for (int i = 0; i < table.getItemCount(); i++)
         		if (table.getItem(i).getChecked())
         			results.add(expressions.get(i));
    	}
        super.buttonPressed(buttonId);
    }
    
    public void addExpressions(ArrayList<EvaluatedExpression> foundExprs) {
    	expressions.addAll(foundExprs);
		showResults();
    }
	
	// Logic code
    
    public ExpressionSkeleton getSkeleton() {
    	return skeleton;
    }
    
    // Table code
    
    private void showResults() {
		// Set and show the results.
    	tableViewer.setInput(expressions);
    	table.getColumn(0).pack();  // For some reason I need these two lines to update the screen.
    	table.getColumn(1).pack();
    }
    
    private TableViewerColumn addColumn(String label, final int index) {
    	TableViewerColumn columnViewer = new TableViewerColumn(tableViewer, SWT.NONE);
    	final TableColumn column = columnViewer.getColumn();
    	column.setText(label);
    	column.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
				synthesisResultComparator.setColumn(index);
				table.setSortDirection(synthesisResultComparator.getDirection());
				table.setSortColumn(column);
            	tableViewer.setComparator(synthesisResultComparator);
				tableViewer.refresh();
            }
        });
    	return columnViewer;
    }
    
    private static String getExpressionLabel(EvaluatedExpression e) {
    	return e.getSnippet();
    }
    
    private static String getValueLabel(EvaluatedExpression e) {
    	return e.getResultString();
    }
    
    private class SynthesisResultComparator extends ViewerComparator {

    	private int column;
    	private int direction;

    	public SynthesisResultComparator() {
    		this.column = -1;
    		this.direction = SWT.DOWN;
    	}

    	public int getDirection() {
    		return direction;
    	}

    	public void setColumn(int column) {
    		if (column == this.column)
    			direction = direction == SWT.DOWN ? SWT.UP : SWT.DOWN;
    		else {
    			this.column = column;
    			direction = SWT.DOWN;
    		}
    	}

    	@Override
    	public int compare(Viewer viewer, Object o1, Object o2) {
    		EvaluatedExpression e1 = (EvaluatedExpression)o1;
    		EvaluatedExpression e2 = (EvaluatedExpression)o2;
    		int result;
    		if (column == 0)
    			result = getExpressionLabel(e1).compareTo(getExpressionLabel(e2));
    		else if (column == 1) {
    			if (e1.getResult() instanceof IJavaPrimitiveValue && e2.getResult() instanceof IJavaPrimitiveValue)
    				result = (int)(((IJavaPrimitiveValue)e1.getResult()).getDoubleValue() - ((IJavaPrimitiveValue)e2.getResult()).getDoubleValue());
    			else
    				result = getValueLabel(e1).compareTo(getValueLabel(e2));
    		} else
    			throw new RuntimeException("Unexpected column: " + column);
    		if (direction == SWT.UP)
    			result = -result;
    		return result;
    	}
    	
    }
    
	private void setAllChecked(boolean state) {
		for (TableItem item: table.getItems())
			if (item.getChecked() != state)
				item.setChecked(state);
		okButton.setEnabled(state && table.getItemCount() > 0);
	}
    
    // Expression validator

	private static class ExpressionSkeletonValidator implements IInputValidator {
		
	    private final IJavaStackFrame stackFrame;
	    private final IAstEvaluationEngine evaluationEngine;
	    private final String varTypeName;
	    
	    public ExpressionSkeletonValidator(IJavaStackFrame stackFrame, String varTypeName) {
	    	this.stackFrame = stackFrame;
	    	this.evaluationEngine = EclipseUtils.getASTEvaluationEngine(stackFrame);
	    	this.varTypeName = varTypeName;
	    }
	    
	    @Override
		public String isValid(String newText) {
	    	return ExpressionSkeleton.isLegalSkeleton(newText, varTypeName, stackFrame, evaluationEngine);
	    }
	}

}
