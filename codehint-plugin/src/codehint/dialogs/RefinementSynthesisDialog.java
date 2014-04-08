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
import codehint.ast.Expression;
import codehint.ast.Statement;

public class RefinementSynthesisDialog extends SynthesisDialog {
	
	private ArrayList<Statement> initialStmts;
	private final RefinementWorker worker;
	private final boolean blockedNatives;
	private final boolean handledEffects;

    private static final int refineCancelButtonID = IDialogConstants.CLIENT_ID + 100;
    private static final int clearButtonID = IDialogConstants.CLIENT_ID + 101;
	private Button refineCancelButton;
	private Button clearButton;

	public RefinementSynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, SynthesisWorker synthesisWorker, RefinementWorker worker, boolean blockedNatives, boolean handledEffects) {
		super(parentShell, varTypeName, varType, stack, propertyDialog, synthesisWorker);
		this.worker = worker;
		this.blockedNatives = blockedNatives;
		this.handledEffects = handledEffects;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);

		searchNativeCalls.setSelection(!blockedNatives);
		handleSideEffects.setSelection(handledEffects);
		
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
		refineCancelButton.setEnabled(isStartingSearch || pdspecAndSkeletonAreValid || initialStmts == null);
	}

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == refineCancelButtonID)
        	searchCancelButtonPressed(buttonId);
        else if (buttonId == clearButtonID) {
        	showResults(initialStmts);
        	clearButton.setEnabled(false);
        } else
        	super.buttonPressed(buttonId);
    }

	@Override
	protected void searchCancelButtonPressed(int buttonId) {
        if (!amSearching && initialStmts == null)
			evalCandidates();
        else
        	super.searchCancelButtonPressed(buttonId);
	}

	@Override
	protected void startOrEndWork(boolean isStarting, String message) {
		super.startOrEndWork(isStarting, message);
		setButtonText(refineCancelButton, isStarting ? "Cancel" : initialStmts == null ? "Init" : "Refine");
		clearButton.setEnabled(!isStarting && initialStmts != null && !(initialStmts.size() == statements.size() && initialStmts.equals(statements)));
	}

	public void setInitialRefinementStatements(ArrayList<Statement> stmts) {
		initialStmts = new ArrayList<Statement>(stmts);
		endSynthesis(SynthesisState.END);
	}

	@Override
	protected void doWork() {
		if (searchButtonId == refineCancelButtonID)
			worker.refine(this, evalManager);
		else
			super.doWork();
	}
	
	@Override
    protected void opened() {
		super.opened();
		evalCandidates();
	}
	
    private void evalCandidates() {
		filteredStatements = statements = new ArrayList<Statement>();
		startEndSynthesis(SynthesisState.START);
		searchCancelButton.setEnabled(false);
		worker.evaluateLine(blockedNatives, handledEffects, this, stack, thread);
	}

	@Override
	public void cleanup() {
		super.cleanup();
		initialStmts.clear();
		refineCancelButton = null;
		clearButton = null;
	}
}
