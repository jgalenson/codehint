package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
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
    private Table table;
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
		this.table = null;
		this.expressions = null;
		this.worker = worker;
		this.skeleton = null;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		
		String message = "Give an expression skeleton that describes the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names.";
		skeletonInput = createInput(composite, message, initialSkeletonText, skeletonValidator, new SkeletonModifyHandler(), "skeleton");

		Composite topButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchButton = createButton(topButtonComposite, searchButtonID, "Search", true);
		searchButton.setEnabled(pdspecIsValid && skeletonIsValid);
		
		table = new Table(composite, SWT.BORDER | SWT.CHECK);
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
	    	worker.synthesize(this);
        } else if (buttonId == IDialogConstants.OK_ID) {
         	results = new ArrayList<EvaluatedExpression>();
         	for (int i = 0; i < table.getItemCount(); i++)
         		if (table.getItem(i).getChecked())
         			results.add(expressions.get(i));
    	}
        super.buttonPressed(buttonId);
    }
    
    @Override
    public void setExpressions(ArrayList<EvaluatedExpression> exprs) {
    	expressions = exprs;
		showResults();
    }
	
	// Logic code
    
    public ExpressionSkeleton getSkeleton() {
    	return skeleton;
    }
    
    // Table code
    
    private void showResults() {
    	table.removeAll();
    	(new TableColumn(table, SWT.NONE)).setText("Expression");
    	(new TableColumn(table, SWT.NONE)).setText("Value");
    	for (EvaluatedExpression e: expressions) {
    		TableItem item = new TableItem(table, SWT.NONE);
    		item.setText(0, e.getSnippet());
    		item.setText(1, Synthesizer.getValue(e, stack));
    	}
    	table.getColumn(0).pack();
    	table.getColumn(1).pack();
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
