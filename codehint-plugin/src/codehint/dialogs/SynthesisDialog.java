package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import codehint.Activator;
import codehint.Synthesizer;
import codehint.Synthesizer.DialogWorker;
import codehint.Synthesizer.SynthesisWorker;
import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.ExpressionSkeleton;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public abstract class SynthesisDialog extends ModelessDialog {
	
	private Text pdspecInput;
	private boolean pdspecIsValid;
	private String pdspecResult;
	
	private final String initialSkeletonText;
	private Text skeletonInput;
	private boolean skeletonIsValid;
	private final IInputValidator skeletonValidator;
	private String skeletonResult;

	private final DialogWorker worker;
	private final boolean isInitialSynthesis;
    private static final int searchButtonID = IDialogConstants.CLIENT_ID;
    private Button searchButton;
    private final IJavaStackFrame stack;

    private Button okButton;
    private static final int TABLE_WIDTH = 250;
    private static final int TABLE_HEIGHT = 300;
    private Table table;
    private ArrayList<EvaluatedExpression> expressions;
    private ArrayList<EvaluatedExpression> results;
    private Property property;
    private ExpressionSkeleton skeleton;

	protected SynthesisDialog(Shell parentShell, String varName, String varTypeName, IJavaStackFrame stack, DialogWorker worker) {
		super(parentShell);
		this.pdspecIsValid = false;
		this.pdspecResult = null;
		ExpressionSkeleton lastCrashedSkeleton = Synthesizer.getLastCrashedSkeleton(varName);
		if (lastCrashedSkeleton == null)
			this.initialSkeletonText = ExpressionSkeleton.HOLE_SYNTAX;
		else
			this.initialSkeletonText = lastCrashedSkeleton.getSugaredString();
		this.skeletonIsValid = false;
		this.skeletonValidator = new ExpressionSkeletonValidator(stack, varTypeName);
		this.skeletonResult = null;
		this.worker = worker;
		this.isInitialSynthesis = worker instanceof SynthesisWorker;
		this.searchButton = null;
		this.stack = stack;
		this.okButton = null;
		this.table = null;
		this.expressions = null;
		this.results = null;
		this.property = null;
		this.skeleton = null;
	}
	
	// Layout code

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);

		pdspecInput = createInput(composite, getPdspecMessage(), getInitialPdspecText(), getPdspecValidator(), getHelpID());
		
		if (isInitialSynthesis) {
			String message = "Give an expression skeleton that describes the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names.";
			skeletonInput = createInput(composite, message, initialSkeletonText, skeletonValidator, "skeleton");

			Composite topButtonComposite = makeButtonComposite(composite);
			searchButton = createButton(topButtonComposite, searchButtonID, "Search", true);
			searchButton.setEnabled(pdspecIsValid && skeletonIsValid);
			
			table = new Table(composite, SWT.BORDER | SWT.CHECK);
	        GridData tableData = new GridData(GridData.FILL_BOTH);
	        tableData.widthHint = TABLE_WIDTH;
	        tableData.heightHint = TABLE_HEIGHT;
	        table.setLayoutData(tableData);
	    	table.setLinesVisible(true);
	    	table.setHeaderVisible(true);

			Composite bottomButtonComposite = makeButtonComposite(composite);
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
		}
		
		return composite;
	}
	
	private static Composite makeButtonComposite(Composite composite) {
		Composite buttonComposite = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 0;
		buttonComposite.setLayout(layout);
		buttonComposite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER | GridData.VERTICAL_ALIGN_CENTER));
		buttonComposite.setFont(composite.getFont());
		return buttonComposite;
	}
	
	private Text createInput(Composite composite, String message, String initialText, final IInputValidator validator, String helpID) {
		Label label = new Label(composite, SWT.NONE);
		label.setText(message);
		label.setFont(composite.getFont());
		
		final Text input = new Text(composite, SWT.SINGLE | SWT.BORDER);
		input.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		input.setText(initialText);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(input, Activator.PLUGIN_ID + "." + helpID);
		
		final Text errorText = new Text(composite, SWT.READ_ONLY | SWT.WRAP);
		errorText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		errorText.setBackground(errorText.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		
		input.addModifyListener(new ModifyListener() {
            @Override
			public void modifyText(ModifyEvent e) {
                boolean hasError = setErrorMessage(errorText, validator.isValid(input.getText()));
                if (input == pdspecInput)
                	pdspecIsValid = !hasError;
                else
                	skeletonIsValid = !hasError;
        		if (searchButton != null)
        			searchButton.setEnabled(pdspecIsValid && skeletonIsValid);
        		else if (okButton != null)
        			okButton.setEnabled(pdspecIsValid);
            }
        });
		boolean hasError = setErrorMessage(errorText, validator.isValid(input.getText()));
		if (validator instanceof ExpressionSkeletonValidator)
			skeletonIsValid = !hasError;
		else
			pdspecIsValid = !hasError;
        
		return input;
	}

    private static boolean setErrorMessage(Text errorText, String errorMessage) {
		boolean hasError = errorMessage != null;
    	if (!errorText.isDisposed()) {
    		errorText.setText(errorMessage == null ? " \n " : errorMessage);
    		errorText.setEnabled(hasError);
    		errorText.setVisible(hasError);
    		errorText.getParent().update();
    	}
    	return hasError;
    }
    
    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID && !isInitialSynthesis) {
            okButton = button;
            okButton.setEnabled(pdspecIsValid);
    	}
    	return button;
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == searchButtonID) {
            pdspecResult = pdspecInput.getText();
            if (skeletonInput != null)
            	skeletonResult = skeletonInput.getText();
            property = computeProperty();
            skeleton = computeSkeleton();
            enableOKCancel(false);
	    	worker.synthesize(this);
        } else if (buttonId == IDialogConstants.OK_ID) {
        	if (isInitialSynthesis) {
	         	results = new ArrayList<EvaluatedExpression>();
	         	for (int i = 0; i < table.getItemCount(); i++)
	         		if (table.getItem(i).getChecked())
	         			results.add(expressions.get(i));
        	} else {
                pdspecResult = pdspecInput.getText();
                property = computeProperty();
    	    	worker.synthesize(this);
        	}
    	}
        super.buttonPressed(buttonId);
    }
    
    public void setExpressions(ArrayList<EvaluatedExpression> exprs) {
    	if (isInitialSynthesis) {
	    	expressions = exprs;
    		setResults();
    	} else
    		results = exprs;
    }
    
    public void enableOKCancel(boolean flag) {
        getButton(IDialogConstants.OK_ID).setEnabled(flag);
        getButton(IDialogConstants.CANCEL_ID).setEnabled(flag);
    }
    
    // Table code
    
    private void setResults() {
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
	}
    
    // Logic code
    
    protected String getPdspecText() {
    	return pdspecResult;
    }
    
    protected static String getFullMessage(String message, String extraMessage) {
    	if (extraMessage == null)
    		return message;
    	else
    		return message + System.getProperty("line.separator") + extraMessage;
    }
    
    protected abstract String getPdspecMessage();
    
    protected abstract String getInitialPdspecText();
    
    protected abstract IInputValidator getPdspecValidator();
    
    protected abstract String getHelpID();
    
    protected abstract Property computeProperty();
    
    private ExpressionSkeleton computeSkeleton() {
    	if (skeletonResult == null)
    		return null;
    	else
    		return ExpressionSkeleton.fromString(skeletonResult);
    }
    
    public ArrayList<EvaluatedExpression> getExpressions() {
     	return results;
    }
    
    public Property getProperty() {
    	return property;
    }
    
    public ExpressionSkeleton getSkeleton() {
    	return skeleton;
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
