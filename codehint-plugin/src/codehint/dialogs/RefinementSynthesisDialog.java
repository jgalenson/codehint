package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import codehint.Synthesizer.RefinementWorker;
import codehint.Synthesizer.SynthesisWorker;
import codehint.expreval.FullyEvaluatedExpression;

public class RefinementSynthesisDialog extends SynthesisDialog {
	
	private final ArrayList<FullyEvaluatedExpression> initialExprs;
	private final RefinementWorker worker;

    private static final int refineCancelButtonID = IDialogConstants.CLIENT_ID + 100;
    private static final int clearButtonID = IDialogConstants.CLIENT_ID + 101;
	private Button refineCancelButton;
	private Button clearButton;

	public RefinementSynthesisDialog(ArrayList<FullyEvaluatedExpression> initialExprs, Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, SynthesisWorker synthesisWorker, RefinementWorker worker) {
		super(parentShell, varTypeName, varType, stack, propertyDialog, synthesisWorker);
		this.initialExprs = initialExprs;
		this.worker = worker;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		
		showResults(initialExprs);  // Initialize the tree to show the results from the previous synthesis.
		filterComposite.setVisible(!initialExprs.isEmpty());
		
		return composite;
	}

	@Override
	protected void addSearchButtons(Composite topButtonComposite) {
		refineCancelButton = createButton(topButtonComposite, refineCancelButtonID, "Refine", true);
		refineCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
		clearButton = createButton(topButtonComposite, clearButtonID, "Clear", false);
		clearButton.setEnabled(false);
		addSearchCancelButton(topButtonComposite, false);
	}
	
	@Override
	protected void enableDisableSearchButtons(boolean isStartingSearch, boolean pdspecAndSkeletonAreValid) {
		super.enableDisableSearchButtons(isStartingSearch, pdspecAndSkeletonAreValid);
		refineCancelButton.setEnabled(isStartingSearch || pdspecAndSkeletonAreValid);
	}

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == refineCancelButtonID) {
        	searchCancelButtonPressed(buttonId);
        } else if (buttonId == clearButtonID) {
        	showResults(initialExprs);
        	clearButton.setEnabled(false);
        } else
        	super.buttonPressed(buttonId);
    }

	@Override
	protected void startOrEndWork(boolean isStarting, String message) {
		super.startOrEndWork(isStarting, message);
		setButtonText(refineCancelButton, isStarting ? "Cancel" : "Refine");
		clearButton.setEnabled(!isStarting && !(initialExprs.size() == expressions.size() && initialExprs.equals(expressions)));
	}

	@Override
	protected void doWork() {
		if (searchButtonId == refineCancelButtonID)
			worker.refine(this, evalManager);
		else
			super.doWork();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		initialExprs.clear();
		refineCancelButton = null;
		clearButton = null;
	}

}
