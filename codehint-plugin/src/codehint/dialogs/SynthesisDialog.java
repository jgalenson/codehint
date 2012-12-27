package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.internal.debug.ui.JDIContentAssistPreference;
import org.eclipse.jdt.internal.debug.ui.contentassist.CurrentFrameContext;
import org.eclipse.jdt.internal.debug.ui.contentassist.JavaDebugContentAssistProcessor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

import codehint.Activator;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public abstract class SynthesisDialog extends ModelessDialog {
	
	private final String varTypeName;
	protected final IJavaType varType;
	
	private int comboIndex;

    protected static final int MESSAGE_WIDTH = 1000;
	protected PropertyDialog propertyDialog;
	private Composite pdspecComposite;
	protected StyledText pdspecInput;
	protected boolean pdspecIsValid;
	private IHandlerService fService;
	private IHandlerActivation fActivation;

    protected Button okButton;

    protected final IJavaStackFrame stack;

    protected ArrayList<FullyEvaluatedExpression> results;
    protected Property property;

    protected SynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog) {
		super(parentShell);
		this.varTypeName = varTypeName;
		this.varType = varType;
		this.comboIndex = -1;
		this.propertyDialog = propertyDialog;
		this.pdspecIsValid = false;
		this.okButton = null;
		this.stack = stack;
		this.results = null;
		this.property = null;
	}
	
	// Layout code

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);

		makeCombo(composite);

		pdspecComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 1);
		pdspecInput = createInput(pdspecComposite, propertyDialog.getPdspecMessage(), propertyDialog.getInitialPdspecText(), propertyDialog.getPdspecValidator(), getPropertyModifyHandler(), propertyDialog.getHelpID());
		pdspecInput.setFocus();
		pdspecInput.selectAll();
		
		return composite;
	}

	private void makeCombo(Composite composite) {
		Composite comboComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 2);
		
		Label comboLabel = new Label(comboComposite, SWT.NONE);
		comboLabel.setText("Select pdspec type: ");
		comboLabel.setFont(comboComposite.getFont());
		
		final Combo combo = new Combo(comboComposite, SWT.READ_ONLY);
		combo.setItems(new String[] { "Demonstrate value", "Demonstrate type", "Demonstrate state property"/*, "Demonstrate lambda property"*/ });
		combo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = combo.getSelectionIndex();
				if (index == comboIndex)
					return;
				String curText = pdspecInput.getText();
				boolean curTextIsTypeName = varTypeName != null && (curText.equals(varTypeName) || curText.equals(EclipseUtils.getUnqualifiedName(varTypeName)));
				if (index == 0) {
					if (varType == null)
						propertyDialog = new ValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, curTextIsTypeName ? "" : curText, propertyDialog.getExtraMessage());
					else if (EclipseUtils.isObject(varType))
						propertyDialog = new ObjectValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, curTextIsTypeName ? "" : curText, propertyDialog.getExtraMessage());
					else if (EclipseUtils.isArray(varType))
						propertyDialog = new ArrayValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, curTextIsTypeName ? "" : curText, propertyDialog.getExtraMessage());
					else
						propertyDialog = new PrimitiveValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, curTextIsTypeName ? "" : curText, propertyDialog.getExtraMessage());
				} else if (index == 1)
					propertyDialog = new TypePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, curText.equals("") ? varTypeName : curText, propertyDialog.getExtraMessage());
				else if (index == 2)
					propertyDialog = new StatePropertyDialog(propertyDialog.getVarName(), stack, curTextIsTypeName ? "" : curText, propertyDialog.getExtraMessage());
				/*else if (index == 3)
					propertyDialog = new LambdaPropertyDialog(propertyDialog.getVarName(), varTypeName, varType, stack, pdspecInput.getText(), propertyDialog.getExtraMessage());*/
				else
					throw new IllegalArgumentException();
				comboIndex = index;
				for (Control c: pdspecComposite.getChildren())
					c.dispose();
				pdspecInput = createInput(pdspecComposite, propertyDialog.getPdspecMessage(), propertyDialog.getInitialPdspecText(), propertyDialog.getPdspecValidator(), getPropertyModifyHandler(), propertyDialog.getHelpID());
				pdspecInput.setFocus();
				pdspecInput.selectAll();
				pdspecComposite.layout(true);
				pdspecComposite.getParent().layout(true);
				propertyTypeChanged();
			}
		});
		
		if (propertyDialog instanceof ValuePropertyDialog)
			comboIndex = 0;
		else if (propertyDialog instanceof TypePropertyDialog)
			comboIndex = 1;
		else if (propertyDialog instanceof StatePropertyDialog)
			comboIndex = 2;
		/*else if (propertyDialog instanceof LambdaPropertyDialog)
			comboIndex = 3;*/
		else
			throw new IllegalArgumentException();
		combo.select(comboIndex);
	}
	
	// For buttons, pass in 0 for numColumns, as createButtons increments it.
	protected static Composite makeChildComposite(Composite parent, int horizStyle, int numColumns) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = numColumns;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(horizStyle | GridData.VERTICAL_ALIGN_CENTER));
		composite.setFont(parent.getFont());
		return composite;
	}
	
	private static class MySourceViewerConfiguration extends JavaSourceViewerConfiguration {
		
		private MyContentAssistant contentAssistant;

		public MySourceViewerConfiguration(IColorManager colorManager, IPreferenceStore preferenceStore, ITextEditor editor, String partitioning) {
			super(colorManager, preferenceStore, editor, partitioning);
			this.contentAssistant = null;
		}
		
		public MyContentAssistant getContentAssistant() {
			return contentAssistant;
		}

        // The below code was mostly copied from DisplayViewerConfiguration.
		// An alternative would be to subclass it, but I would still have to override getContentAssistant to have it use my class.

		public IContentAssistProcessor getContentAssistantProcessor() {
			return new JavaDebugContentAssistProcessor(new CurrentFrameContext());
		}

		@Override
		public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
			MyContentAssistant assistant = new MyContentAssistant();
			assistant.setContentAssistProcessor(getContentAssistantProcessor(), IDocument.DEFAULT_CONTENT_TYPE);
			JDIContentAssistPreference.configure(assistant, getColorManager());
			assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
			assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
			contentAssistant = assistant;
			return assistant;
		}
	}

	private static class MyContentAssistant extends ContentAssistant {

		// Override the visibility to make this method public.
		@Override
		public boolean isProposalPopupActive(){
			return super.isProposalPopupActive();
		}
		
	}
	
	protected StyledText createInput(Composite composite, String message, String initialText, final IInputValidator validator, final ModifyHandler modifyListener, String helpID) {
		Label label = new Label(composite, SWT.WRAP);
		label.setText(message);
		label.setFont(composite.getFont());
		GridData gridData = new GridData();
		gridData.widthHint = MESSAGE_WIDTH;
		label.setLayoutData(gridData);

		// This code was adapted from ExpressionInputDialog.
		final SourceViewer sv = new SourceViewer(composite, null, SWT.SINGLE | SWT.BORDER);
		JavaTextTools tools = new JavaTextTools(PreferenceConstants.getPreferenceStore());
		Document doc = new Document(initialText);
		sv.setDocument(doc);
        tools.setupJavaDocumentPartitioner(doc, IJavaPartitions.JAVA_PARTITIONING);
        MySourceViewerConfiguration config = new MySourceViewerConfiguration(JavaUI.getColorManager(), PreferenceConstants.getPreferenceStore(), null, null);
		sv.configure(config);
		// Register content assist.  Without this it comes up automatically but not when the user presses ctrl-space.
		final IHandler handler = new AbstractHandler() {
			@Override
			public Object execute(ExecutionEvent event) {
				sv.doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
				return null;
			}
		};
		sv.getControl().addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
		    	deactivateHandler();
			}
			@Override
			public void focusGained(FocusEvent e) {
		    	deactivateHandler();
				fService = (IHandlerService)PlatformUI.getWorkbench().getAdapter(IHandlerService.class);
				fActivation = fService.activateHandler(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS, handler);
			}
		});
		// Ensure that pressing enter when there is an autocomplete popup open does not start the search.
		final MyContentAssistant contentAssistant = config.getContentAssistant();
		sv.getControl().addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN && contentAssistant.isProposalPopupActive()) {
					e.doit = false;
					e.detail = SWT.TRAVERSE_NONE;
				}
			}
			
		});
		// Enable undo/redo.
		final KeyStroke undoTrigger = getKeyStrokeForCommand("org.eclipse.ui.edit.undo");
		final KeyStroke redoTrigger = getKeyStrokeForCommand("org.eclipse.ui.edit.redo");
		sv.getControl().addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (matches(e, undoTrigger))
					sv.doOperation(ITextOperationTarget.UNDO);
				else if (matches(e, redoTrigger))
					sv.doOperation(ITextOperationTarget.REDO);
			}
			@Override
			public void keyReleased(KeyEvent e) {
			}
			
		});
		IUndoManager undoManager = config.getUndoManager(sv);
		sv.setUndoManager(undoManager);
		undoManager.connect(sv);
		// Miscellaneous.
		sv.getControl().setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        PlatformUI.getWorkbench().getHelpSystem().setHelp(sv.getControl(), Activator.PLUGIN_ID + "." + helpID);
		
		final Text errorText = new Text(composite, SWT.READ_ONLY | SWT.WRAP);
		errorText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		errorText.setBackground(errorText.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		
		sv.getDocument().addDocumentListener(new IDocumentListener() {
            @Override
			public void documentAboutToBeChanged(DocumentEvent event) { }
            @Override
			public void documentChanged(DocumentEvent event) {
            	modifyListener.handle(sv.getDocument().get(), validator, errorText);
            }
        });
    	modifyListener.handle(initialText, validator, errorText);
        
		return sv.getTextWidget();
	}
	
	/**
	 * Gets the KeyStroke that triggers the command with the
	 * given id, if any.
	 * @param id The id of the command.
	 * @return The KeyStroke that triggers the command with the
	 * given id.
	 */
	private static KeyStroke getKeyStrokeForCommand(String id) {
		IBindingService bindingService = (IBindingService)PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		TriggerSequence seq = bindingService.getBestActiveBindingFor(id);
		if (seq.getTriggers().length == 1 && seq.getTriggers()[0] instanceof KeyStroke)
			return (KeyStroke)seq.getTriggers()[0];
		else
			return null;
	}
	
	/**
	 * Checks whether the given KeyEvent matches the given KeyStroke.
	 * @param e The KeyEvent.
	 * @param trigger The KeyStroke
	 * @return Whether the given event matches the given trigger.
	 */
	private static boolean matches(KeyEvent e, KeyStroke trigger) {
		return trigger != null && trigger.getModifierKeys() == e.stateMask
				&& Character.toLowerCase((char)trigger.getNaturalKey()) == Character.toLowerCase((char)e.keyCode);
	}
	
	protected abstract class ModifyHandler {

		public void handle(String curText, IInputValidator validator, Text errorText) {
			boolean hasError = setErrorMessage(errorText, validator.isValid(curText));
            inputChanged(hasError);
		}
        
        public abstract void inputChanged(boolean hasError);
        
	}
	
	protected class PropertyModifyHandler extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			pdspecIsValid = !hasError;
        }
		
	}
	
	protected PropertyModifyHandler getPropertyModifyHandler() {
		return new PropertyModifyHandler();
	}
    
    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID)
            okButton = button;
    	return button;
    }

    private static boolean setErrorMessage(Text errorText, String errorMessage) {
		boolean hasError = errorMessage != null;
    	if (!errorText.isDisposed()) {
    		errorText.setText(errorMessage == null ? "" : errorMessage);
    		errorText.setEnabled(hasError);
    		errorText.setVisible(hasError);
    		errorText.getParent().update();
    	}
    	return hasError;
    }
    
    /**
     * Called when the user changes the type of the pdspec
     * they intend to give.
     */
    protected void propertyTypeChanged() {
    	
    }
    
    // Logic code
    
    public ArrayList<FullyEvaluatedExpression> getExpressions() {
     	return results;
    }
    
    public Property getProperty() {
    	return property;
    }
    
    // Cleanup 
    
    @Override
	protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("CodeHint");
     }

	public void cleanup() {
    	deactivateHandler();
		propertyDialog = null;
		pdspecComposite = null;
		pdspecInput = null;
		fService = null;
		fActivation = null;
		results = null;
		property = null;
	}

	/**
	 * Deactivates the handler.  If there is more than one
	 * copy activated at any time, there is an error and
	 * neither is triggered.
	 */
	private void deactivateHandler() {
		if (fService != null && fActivation != null)
    		fService.deactivateHandler(fActivation);
	}

}
