package codehint.dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.internal.debug.ui.JDIContentAssistPreference;
import org.eclipse.jdt.internal.debug.ui.contentassist.CurrentFrameContext;
import org.eclipse.jdt.internal.debug.ui.contentassist.JavaDebugContentAssistProcessor;
import org.eclipse.jdt.ui.JavaElementLabels;
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
import org.eclipse.jface.resource.JFaceResources;
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
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

import codehint.Activator;
import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.ast.ASTNode;
import codehint.ast.ASTVisitor;
import codehint.ast.Expression;
import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluationManager;
import codehint.expreval.NativeHandler;
import codehint.expreval.StaticEvaluator;
import codehint.expreval.TimeoutChecker;
import codehint.exprgen.DeterministicExpressionGenerator;
import codehint.exprgen.ExpressionEvaluator;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.ExpressionMaker;
import codehint.exprgen.ExpressionSkeleton;
import codehint.exprgen.ExpressionSorter;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.ValueCache;
import codehint.exprgen.Weights;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

public abstract class SynthesisDialog extends ModelessDialog {
	
	private final String varTypeName;
	protected final IJavaType varType;
	
	private int comboIndex;

    protected static final int MESSAGE_WIDTH = 800;
	protected PropertyDialog propertyDialog;
	private Composite pdspecComposite;
	protected StyledText pdspecInput;
	protected boolean pdspecIsValid;
	private IHandlerService fService;
	private IHandlerActivation fActivation;

    protected Button okButton;

    protected final IJavaStackFrame stack;

    protected ArrayList<Expression> results;
    protected Property property;
	
	private final String initialSkeletonText;
	private StyledText skeletonInput;
	protected boolean skeletonIsValid;
	private final IInputValidator skeletonValidator;
	private String skeletonResult;
	private Button searchConstructorsButton;
	private Button searchOperatorsButton;
	protected Button searchNativeCalls;
	protected Button handleSideEffects;
	private boolean blockedNativeCalls;
	private boolean handledSideEffects;

    private static final int searchCancelButtonID = IDialogConstants.CLIENT_ID;
    protected Button searchCancelButton;
    protected boolean amSearching;
    protected int numSearches;
    private boolean shouldContinue;
    protected int searchButtonId;
    private Composite monitorComposite;
    private ProgressMonitorPart monitor;
    private static final int MONITOR_WIDTH = 350;
    private JavadocPrefetcher javadocPrefetcher;
    private SorterWorker sorterWorker;
    private Label monitorLabel;

    private static final int TABLE_WIDTH = MESSAGE_WIDTH;
    private static final int TABLE_HEIGHT = 300;
    private Table table;
    private int maxExprLen;
    private int maxResultLen;
    private int maxToStringLen;
    private int maxEffectsLen;
    private boolean needsToStringColumn;
    protected ArrayList<Expression> expressions;
    private ArrayList<Expression> lastExpressions;
    protected ArrayList<Expression> filteredExpressions;
    private Button checkAllButton;
    private Button uncheckAllButton;
    private static final int checkSelectedButtonID = IDialogConstants.CLIENT_ID + 2;
    private Button checkSelectedButton;
    private static final int uncheckSelectedButtonID = IDialogConstants.CLIENT_ID + 3;
    private Button uncheckSelectedButton;
    
    protected Composite filterComposite;
    private Text filterText;
    private static final int filterButtonID = IDialogConstants.CLIENT_ID + 4;
    private static final int clearButtonID = IDialogConstants.CLIENT_ID + 5;
    private FilterWorker filterWorker;
    private boolean amFiltering;
    protected final SynthesisWorker synthesisWorker;
    private final IJavaProject project;
    private final IJavaDebugTarget target;
    protected final IJavaThread thread;
    private final IAstEvaluationEngine evaluationEngine;
    private TypeCache typeCache;
    private ValueCache valueCache;
    private SubtypeChecker subtypeChecker;
    protected TimeoutChecker timeoutChecker;
    protected NativeHandler nativeHandler;
    protected SideEffectHandler sideEffectHandler;
    private ExpressionMaker expressionMaker;
    private ExpressionEvaluator expressionEvaluator;
    protected EvaluationManager evalManager;
    private StaticEvaluator staticEvaluator;
    private ExpressionGenerator expressionGenerator;
    private ExpressionSkeleton skeleton;
    private ExpressionSorter expressionSorter;
    
    //private SideEffectStartStopper sideEffectStartStopper;

	public SynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, SynthesisWorker synthesisWorker) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.varTypeName = varTypeName;
		this.varType = varType;
		this.comboIndex = -1;
		this.propertyDialog = propertyDialog;
		this.pdspecIsValid = false;
		this.fService = (IHandlerService)PlatformUI.getWorkbench().getAdapter(IHandlerService.class);
		this.okButton = null;
		this.stack = stack;
		this.results = null;
		this.property = null;
		IAstEvaluationEngine engine = EclipseUtils.getASTEvaluationEngine(stack);
		this.initialSkeletonText = ExpressionSkeleton.HOLE_SYNTAX;
		this.skeletonIsValid = false;
		this.skeletonValidator = new ExpressionSkeletonValidator(stack, varTypeName, engine);
		this.skeletonResult = null;
		this.blockedNativeCalls = false;
		this.handledSideEffects = false;
		this.searchCancelButton = null;
		this.amSearching = false;
		this.numSearches = 0;
		this.shouldContinue = true;
		this.searchButtonId = -1;
		this.monitor = null;
		this.table = null;
		this.maxExprLen = 0;
		this.maxResultLen = 0;
		this.maxToStringLen = 0;
		this.maxEffectsLen = 0;
		this.needsToStringColumn = false;
		this.expressions = null;
		this.lastExpressions = null;
		this.filteredExpressions = null;
		this.amFiltering = false;
		this.synthesisWorker = synthesisWorker;
		this.project = EclipseUtils.getProject(stack);
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.thread = (IJavaThread)stack.getThread();
		this.evaluationEngine = engine;
		this.typeCache = new TypeCache();
		this.valueCache = new ValueCache(target);
		this.subtypeChecker = new SubtypeChecker(stack, target, typeCache);
		this.timeoutChecker = new TimeoutChecker(thread, stack, target, typeCache);
		this.nativeHandler = new NativeHandler(thread, stack, target, typeCache);
		this.sideEffectHandler = new SideEffectHandler(stack, project);
		expressionEvaluator = new ExpressionEvaluator(stack, valueCache, typeCache, subtypeChecker, timeoutChecker, nativeHandler, sideEffectHandler, Synthesizer.getMetadata());
		this.expressionMaker = new ExpressionMaker(stack, expressionEvaluator, valueCache);
		this.evalManager = new EvaluationManager(varType == null, true, stack, expressionEvaluator, subtypeChecker, typeCache, valueCache, timeoutChecker);
		this.staticEvaluator = new StaticEvaluator(stack, expressionEvaluator, typeCache, valueCache);
		Weights weights = new Weights();
		this.expressionGenerator = new DeterministicExpressionGenerator(target, stack, sideEffectHandler, expressionMaker, expressionEvaluator, subtypeChecker, typeCache, evalManager, staticEvaluator, weights);
		//this.expressionGenerator = new codehint.exprgen.StochasticExpressionGenerator(target, stack, sideEffectHandler, expressionMaker, expressionEvaluator, subtypeChecker, typeCache, evalManager, staticEvaluator, weights);
		this.skeleton = null;
		this.expressionSorter = new ExpressionSorter(expressionEvaluator, weights);
	}
	
	// Layout code

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		useGridLayout(composite);

		makeCombo(composite);

		pdspecComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 1);
		pdspecInput = createInput(pdspecComposite, propertyDialog.getPdspecMessage(), propertyDialog.getInitialPdspecText(), propertyDialog.getPdspecValidator(), new SynthesisPropertyModifyListener(), propertyDialog.getHelpID());
		pdspecInput.setFocus();
		pdspecInput.selectAll();

		String message = "Give a skeleton describing the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names and " + ExpressionSkeleton.LIST_HOLE_SYNTAX + "s for an unknown number of arguments.";
		Composite skeletonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 1);
		skeletonInput = createInput(skeletonComposite, message, initialSkeletonText, skeletonValidator, new SkeletonModifyHandler(), "skeleton");
		
		Composite skeletonButtonComposite = makeChildComposite(skeletonComposite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchConstructorsButton = createCheckBoxButton(skeletonButtonComposite, "Search top-level constructors");
		if (!EclipseUtils.isObject(varType))
			searchConstructorsButton.setEnabled(false);
		searchOperatorsButton = createCheckBoxButton(skeletonButtonComposite, "Search operators");
		
		Composite searchOptionsComposite = makeChildComposite(skeletonComposite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchNativeCalls = createCheckBoxButton(searchOptionsComposite, "Call non-standard native methods (fast but dangerous)");
		searchNativeCalls.setSelection(true);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(searchNativeCalls, Activator.PLUGIN_ID + "." + "search-native-calls");
		handleSideEffects = createCheckBoxButton(searchOptionsComposite, "Log and undo side effects (sound but slow)");
		handleSideEffects.setSelection(false);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(handleSideEffects, Activator.PLUGIN_ID + "." + "handle-side-effects");
        if (!sideEffectHandler.canHandleSideEffects())
        	handleSideEffects.setEnabled(false);
		
		Composite topButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		addSearchButtons(topButtonComposite);
		
		monitorComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 1);
		monitorLabel = null;
		
		table = new Table(composite, SWT.BORDER | SWT.CHECK | SWT.MULTI | SWT.VIRTUAL);
		TableViewer tableViewer = new TableViewer(table);
		table.addListener(SWT.SetData, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableItem item = (TableItem)event.item;
				int index = event.index;
				Expression expr = filteredExpressions.get(index);
				item.setData(expr);
				String exprLabel = getExpressionLabel(expr);
				String resultLabel = getValueLabel(expr);
				String toStringLabel = getToStringLabel(expr);
				String effectsLabel = getEffectsLabel(expr);
				item.setText(new String[] { exprLabel, resultLabel, toStringLabel, effectsLabel });
				if (exprLabel.length() > maxExprLen) {
					table.getColumn(0).pack();
					maxExprLen = exprLabel.length();
				}
				if (resultLabel.length() > maxResultLen) {
					table.getColumn(1).pack();
					maxResultLen = resultLabel.length();
				}
				if (needsToStringColumn && toStringLabel.length() > maxToStringLen) {
					table.getColumn(2).pack();
					maxToStringLen = toStringLabel.length();
				}
				if (effectsLabel.length() > maxEffectsLen) {
					table.getColumn(3).pack();
					maxEffectsLen = effectsLabel.length();
				}
			}
		});
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
                	if (((TableItem)e.item).getChecked())
                		okButton.setEnabled(true);
                	else
                		enableDisableOKButton();
                }
            }
        });
    	TableColumn column1 = addColumn("Expression", 0, TABLE_WIDTH / 3);
    	(new TableViewerColumn(tableViewer, column1)).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getToolTipText(Object element) {
				Expression expr = (Expression)element;
				String javadoc = getJavadocs(expr, expressionEvaluator, true);
				if (javadoc == null)
					return null;
				javadoc = javadoc.trim();
				return javadoc;
			}
			
			@Override
			public int getToolTipDisplayDelayTime(Object object) {
				return 100;
			}
		});
    	addColumn("Result", 1, TABLE_WIDTH / 3);
    	addColumn("toString", 2, TABLE_WIDTH / 3);
    	addColumn("Effects", 3, 0);
    	ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
    	tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				TableItem clickedItem = table.getItem(table.getSelectionIndex());
				Object doubleClickedElement = ((IStructuredSelection)event.getSelection()).getFirstElement();
				if (clickedItem.getData() == doubleClickedElement) {
					clickedItem.setChecked(!clickedItem.getChecked());
					if (clickedItem.getChecked())
						okButton.setEnabled(true);
					else
						enableDisableOKButton();
				}
			}
		});
        PlatformUI.getWorkbench().getHelpSystem().setHelp(table, Activator.PLUGIN_ID + "." + "candidate-selector");
        table.setItemCount(0);

		Composite bottomButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
        checkAllButton = createButton(bottomButtonComposite, IDialogConstants.SELECT_ALL_ID, "Check all", false);
        checkAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
                setAllChecked(true);
            }
        });
        checkAllButton.setEnabled(false);
        uncheckAllButton = createButton(bottomButtonComposite, IDialogConstants.DESELECT_ALL_ID, "Uncheck all", false);
        uncheckAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
                setAllChecked(false);
            }
        });
        uncheckAllButton.setEnabled(false);
        checkSelectedButton = createButton(bottomButtonComposite, checkSelectedButtonID, "Check selected", false);
        checkSelectedButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
            	setSelectedChecked(true);
            }
        });
        checkSelectedButton.setEnabled(false);
        uncheckSelectedButton = createButton(bottomButtonComposite, uncheckSelectedButtonID, "Uncheck selected", false);
        uncheckSelectedButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
            	setSelectedChecked(false);
            }
        });
        uncheckSelectedButton.setEnabled(false);
        
        filterComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 2);
        createLabel(filterComposite, "Filter expressions, results, and Javadoc by words:", SWT.BEGINNING, 325);
		filterText = new Text(filterComposite, SWT.BORDER | SWT.WRAP | SWT.SINGLE);
		filterText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		filterText.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				filterExpressions(filterText.getText());
			}
		});
		// Override the default behavior of pressing the default button when you hit enter.
		filterText.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN) {
					e.doit = false;
					e.detail = SWT.TRAVERSE_NONE;
				}
			}
		});
		Button filterButton = createButton(filterComposite, filterButtonID, "Filter", false);
		filterButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				filterExpressions(filterText.getText());
			}
		});
		Button clearButton = createButton(filterComposite, clearButtonID, "Clear", false);
		clearButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				filterText.setText("");
				filterExpressions("");
			}
		});
        filterComposite.setVisible(false);
		
		return composite;
	}

	protected abstract void addSearchButtons(Composite topButtonComposite);

	protected void addSearchCancelButton(Composite topButtonComposite, boolean isDefault) {
		searchCancelButton = createButton(topButtonComposite, searchCancelButtonID, "Search", isDefault);
		searchCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
	}

	private void makeCombo(Composite composite) {
		Composite comboComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 2);
		
		Label comboLabel = new Label(comboComposite, SWT.NONE);
		comboLabel.setText("Select pdspec type: ");
		comboLabel.setFont(comboComposite.getFont());
		
		final Combo combo = new Combo(comboComposite, SWT.READ_ONLY);
		combo.setItems(new String[] { "Demonstrate value", "Demonstrate type", "Demonstrate state property" });
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
				else
					throw new IllegalArgumentException();
				comboIndex = index;
				for (Control c: pdspecComposite.getChildren())
					c.dispose();
				pdspecInput = createInput(pdspecComposite, propertyDialog.getPdspecMessage(), propertyDialog.getInitialPdspecText(), propertyDialog.getPdspecValidator(), new SynthesisPropertyModifyListener(), propertyDialog.getHelpID());
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
		else
			throw new IllegalArgumentException();
		combo.select(comboIndex);
	}
	
	// For buttons, pass in 0 for numColumns, as createButtons increments it.
	protected static Composite makeChildComposite(Composite parent, int horizStyle, int numColumns) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = useGridLayout(composite);
		layout.numColumns = numColumns;
		composite.setLayoutData(new GridData(horizStyle | GridData.VERTICAL_ALIGN_CENTER));
		composite.setFont(parent.getFont());
		return composite;
	}
	
	private static GridLayout useGridLayout(Composite composite) {
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = 0;
		composite.setLayout(layout);
		return layout;
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
		createLabel(composite, message, SWT.BEGINNING, MESSAGE_WIDTH);

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
		sv.getControl().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (matches(e, undoTrigger))
					sv.doOperation(ITextOperationTarget.UNDO);
				else if (matches(e, redoTrigger))
					sv.doOperation(ITextOperationTarget.REDO);
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

	protected Label createLabel(Composite composite, String message, int horizAlign, int width) {
		Label label = new Label(composite, SWT.WRAP);
		label.setText(message);
		label.setFont(composite.getFont());
		GridData gridData = new GridData();
		gridData.widthHint = width;
		gridData.horizontalAlignment = horizAlign;
		label.setLayoutData(gridData);
		return label;
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
	
	private abstract class ModifyHandler {

		public void handle(String curText, IInputValidator validator, Text errorText) {
			boolean hasError = setErrorMessage(errorText, validator.isValid(curText));
            inputChanged(hasError);
		}
        
        public void inputChanged(@SuppressWarnings("unused") boolean hasError) {
        	if (searchCancelButton != null && !amSearching && !amFiltering)
        		enableDisableSearchButtons(false, pdspecIsValid && skeletonIsValid);
        	ensureNoContinue();
        }
        
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
    
    // Logic code
    
    public ArrayList<Expression> getExpressions() {
     	return results;
    }
    
    public Property getProperty() {
    	return property;
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
	
	private class SkeletonModifyHandler extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
        	skeletonIsValid = !hasError;
        	super.inputChanged(hasError);
        }
		
	}
	
	private class SynthesisPropertyModifyListener extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			pdspecIsValid = !hasError;
        	super.inputChanged(hasError);
        }
		
	}
	
	protected void enableDisableSearchButtons(boolean isStartingSearch, boolean pdspecAndSkeletonAreValid) {
		searchCancelButton.setEnabled(isStartingSearch || pdspecAndSkeletonAreValid);
	}
    
    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	if (id == IDialogConstants.OK_ID)  // We want the search button to be the default, but our superclass Dialog makes the OK button default.
    		defaultButton = false;
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID)
            okButton = button;
    	if (id == IDialogConstants.OK_ID)
    		button.setEnabled(false);
    	return button;
    }
    
    // Cherry-picked from Dialog.createButton.
    private Button createCheckBoxButton(Composite parent, String label) {
		((GridLayout)parent.getLayout()).numColumns++;
    	Button button = new Button(parent, SWT.CHECK);
		button.setText(label);
		button.setFont(JFaceResources.getDialogFont());
		setButtonLayoutData(button);
		button.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
            	ensureNoContinue();
            }
        });
		return button;
    }
    
    protected void ensureNoContinue() {
    	 numSearches = 0;
    	 shouldContinue = false;
     	if (searchCancelButton != null && !amSearching && !amFiltering)
     		setButtonText(searchCancelButton, "Search");
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == searchCancelButtonID) {
        	searchCancelButtonPressed(buttonId);
        } else if (buttonId == IDialogConstants.OK_ID) {
         	results = new ArrayList<Expression>();
         	for (int i = 0; i < table.getItemCount(); i++)
         		if (table.getItem(i).getChecked())
         			results.add((Expression)table.getItem(i).getData());
    	}
        super.buttonPressed(buttonId);
    }

	protected void searchCancelButtonPressed(int buttonId) {
		if (!amSearching && !amFiltering)
			startSearch(propertyDialog.computeProperty(pdspecInput.getText(), typeCache), buttonId);
		else
			monitor.setCanceled(true);
	}

	private void startSearch(Property prop, int buttonId) {
		boolean isAutomatic = buttonId == -1;
		searchButtonId = buttonId;
		property = prop;
		skeletonResult = skeletonInput.getText();
		skeleton = ExpressionSkeleton.fromString(skeletonResult, target, stack, expressionMaker, expressionEvaluator, evaluationEngine, subtypeChecker, typeCache, valueCache, evalManager, staticEvaluator, expressionGenerator, sideEffectHandler);
		startEndSynthesis(isAutomatic ? SynthesisState.AUTO_START : SynthesisState.START);
		lastExpressions = expressions;
		showResults(new ArrayList<Expression>());
		valueCache.allowCollectionOfDisabledObjects();  // Allow collection of objects from previous search.
		this.shouldContinue = !isAutomatic && searchButtonId == searchCancelButtonID;
		// Reset column sort indicators.
		table.setSortDirection(SWT.NONE);
		table.setSortColumn(null);
		// Reset column widths.
		table.getColumn(0).setWidth(TABLE_WIDTH / 3);
		table.getColumn(1).setWidth(TABLE_WIDTH / 3);
		table.getColumn(2).setWidth(TABLE_WIDTH / 3);
		table.getColumn(3).setWidth(handleSideEffects.getSelection() ? TABLE_WIDTH / 4 : 0);
		maxExprLen = maxResultLen = maxToStringLen = maxEffectsLen = 0;
		needsToStringColumn = false;
		// Start the synthesis
		boolean blockNatives = isAutomatic || !searchNativeCalls.getSelection();
		nativeHandler.enable(blockNatives);
		sideEffectHandler.enable(handleSideEffects.getSelection());
		blockedNativeCalls = blockNatives;
		handledSideEffects = handleSideEffects.getSelection();
		doWork();
	}
	
	public void endSynthesis(final SynthesisState state) {
		if (state == SynthesisState.END && sorterWorker != null) {
			IProgressMonitor curMonitor = SubMonitor.convert(monitor, "Sorting results", IProgressMonitor.UNKNOWN);
			try {
				sorterWorker.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} finally {
				curMonitor.done();
			}
		} else if (state == SynthesisState.UNFINISHED && sorterWorker != null)
			sorterWorker.cancel();
		sorterWorker = null;
		Display.getDefault().asyncExec(new Runnable(){
			@Override
			public void run() {
				startEndSynthesis(state);
			}
    	});
	}

	protected void startEndSynthesis(SynthesisState state) {
		boolean isStarting = isStart(state);
        getButton(IDialogConstants.CANCEL_ID).setEnabled(!isStarting);
		if (state == SynthesisState.END && searchButtonId == searchCancelButtonID && shouldContinue)
			numSearches++;
        startOrEndWork(isStarting, state == SynthesisState.END && expressions.isEmpty() ? "No expressions found.  You may press \"Continue Search\" to search further or change some search options." : null);
    	amSearching = isStarting;
    	if (state == SynthesisState.END) {
    		javadocPrefetcher = new JavadocPrefetcher(this.filteredExpressions, this.expressionEvaluator);
    		javadocPrefetcher.setPriority(Job.DECORATE);
    		javadocPrefetcher.schedule();
    		if (expressions.isEmpty() && numSearches == 1 && !handledSideEffects)  // Automatically continue search if no results were found at depth 1.
    			startSearch(property, searchButtonId);
    		else
    			searchButtonId = -1;
    	} else if (state == SynthesisState.UNFINISHED && lastExpressions != null) {
    		showResults(lastExpressions);
    		searchButtonId = -1;
    	}
    	if (!isStarting)
    		filterText.setText("");
    	filterComposite.setVisible(!isStarting && !expressions.isEmpty());
    }
	
	protected void startOrEndWork(boolean isStarting, String message) {
		enableDisableSearchButtons(isStarting, pdspecIsValid && skeletonIsValid);
    	setButtonText(searchCancelButton, isStarting ? "Cancel" : shouldContinue && numSearches > 0 ? "Continue search" : "Search");
    	if (!isStarting && message != null)
			monitorLabel = createLabel(monitorComposite, message, SWT.CENTER, SWT.DEFAULT);
    	else if (monitorLabel != null)
			monitorLabel.dispose();
    	if (isStarting) {  // Enable progress monitor.
    		monitor = new SynthesisProgressMonitor(monitorComposite, monitorComposite.getLayout());
    		//monitor.attachToCancelComponent(searchButton);
    		GridData gridData = new GridData();
    		gridData.widthHint = MONITOR_WIDTH;
    		monitor.setLayoutData(gridData);
    	} else if (monitor != null) // Disable progress monitor.
    		monitor.dispose();
		monitorComposite.getParent().layout(true);
	}
	
	protected void doWork() {
		if (searchButtonId == searchCancelButtonID)
			synthesisWorker.synthesize(this, evalManager, numSearches, timeoutChecker, nativeHandler.isEnabled(), sideEffectHandler);
	}
	
	protected void showResults(ArrayList<Expression> exprs) {
		expressions = exprs;
		filteredExpressions = expressions;
		showResults();
	}

	// Prefetch the Javadocs to reduce the waiting time (which can be noticeable without this) when the user hovers over an expression.
	// Copy fields used to avoid a race with closing the dialog.
	private final class JavadocPrefetcher extends Job {
		
		private final ArrayList<Expression> expressions;
		private final ExpressionEvaluator expressionEvaluator;

		private JavadocPrefetcher(ArrayList<Expression> expressions, ExpressionEvaluator expressionEvaluator) {
			super("Javadoc prefetch");
			this.expressions = expressions;
			this.expressionEvaluator = expressionEvaluator;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			int num = Math.min(expressions.size(), 20);
			monitor.beginTask("Javadoc prefetch", num);
			for (int i = 0; i < num; i++) {
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				getJavadocs(expressions.get(i), expressionEvaluator, false);
				monitor.worked(1);
			}
			monitor.done();
			return Status.OK_STATUS;
		}
		
	}
	
	private String getJavadoc(Expression expr, ExpressionEvaluator expressionEvaluator, boolean prettify) {
		try {
			int id = expr.getID();
			Method method = expressionEvaluator.getMethod(id);
			if (method != null) {
				IMethod imethod = EclipseUtils.getIMethod(method, project);
				if (imethod != null)
					return getJavadocFast(imethod, prettify);
			}
			Field field = expressionEvaluator.getField(id);
			if (field != null) {
				IField ifield = EclipseUtils.getIField(field, project);
				if (ifield != null)
					return getJavadocFast(ifield, prettify);
			}
			return null;
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	// This is adapted from org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2.getHTMLContentFromSource.
	private static String getJavadocFast(IMember member, boolean prettify) throws JavaModelException {
		IBuffer buffer = member.getOpenable().getBuffer();
		if (buffer == null)
			return getJavadocSlow(member, prettify);
		ISourceRange javadocRange = member.getJavadocRange();
		if (javadocRange == null)
			return getJavadocSlow(member, prettify);
		String javadocText = buffer.getText(javadocRange.getOffset(), javadocRange.getLength());
		if (prettify) {
			javadocText = javadocText.replaceAll("^/[*][*][ \t]*\n?", "");  // Filter starting /**
			javadocText = javadocText.replaceAll("\n?[ \t]*[*]/$", "");  // Filter ending */
			javadocText = javadocText.replaceAll("^\\s*[*]", "\n");  // Trim leading whitespace.
			javadocText = javadocText.replaceAll("\n\\s*[*]", "\n");  // Trim whitespace at beginning of line.
			javadocText = javadocText.replaceAll("<[^>]*>", "");  // Remove html tags.
			javadocText = javadocText.replaceAll("[{]@code([^}]*)[}]", "$1");  // Replace {@code foo} blocks with foo.
			javadocText = javadocText.replaceAll("&nbsp;", " ").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"");  // Replace html formatting.
		}
		javadocText = Flags.toString(member.getFlags()) + " " + JavaElementLabels.getElementLabel(member, JavaElementLabels.M_PRE_RETURNTYPE | JavaElementLabels.M_PARAMETER_NAMES | JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.F_PRE_TYPE_SIGNATURE) + "\n" + javadocText;
		return javadocText;
	}
	
	private static String getJavadocSlow(IMember member, boolean prettify) throws JavaModelException {
		String javadoc = member.getAttachedJavadoc(null);
		if (javadoc != null && prettify) {
			javadoc = javadoc.replaceAll("<H3>([^<]|\n)*</H3>\n?", "");
			javadoc = javadoc.replaceAll("<DL>|<DD>|<DT>", "\n");
			javadoc = javadoc.replaceAll("<[^>]*>", "");
			javadoc = javadoc.replaceAll("&nbsp;", " ").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"");
			javadoc = javadoc.replaceAll("[\n\r]{2,}", "\n\n");
		}
		return javadoc;
	}
	
	private String getJavadocs(Expression expr, final ExpressionEvaluator expressionEvaluator, final boolean prettify) {
    	final StringBuffer javadocs = new StringBuffer();
    	expr.accept(new ASTVisitor() {
    		@Override
    		public void postVisit(ASTNode node) {
    			if (node instanceof Expression) {
    				String javadoc = getJavadoc((Expression)node, expressionEvaluator, prettify);
    				if (javadoc != null) {
    					if (javadocs.length() > 0)
    						javadocs.append("\n\n-----\n\n");
    					javadocs.append(javadoc.trim());
    				}
    			}
    		}
    	});
    	return javadocs.length() == 0 ? null : javadocs.toString();
    }

	@Override
	protected void cancelPressed() {
		if (javadocPrefetcher != null) {
			javadocPrefetcher.cancel();
			javadocPrefetcher = null;
		}
		super.cancelPressed();
	}
	
	public enum SynthesisState { START, END, UNFINISHED, AUTO_START };
	
	private static boolean isStart(SynthesisState state) {
		return state == SynthesisState.START || state == SynthesisState.AUTO_START;
	}
	
	protected void setButtonText(Button button, String text) {
		if (!text.equals(button.getText())) {
			button.setText(text);
			setButtonLayoutData(button);
			button.getParent().getParent().layout(true);
		}
	}

	private void enableDisableOKButton() {
		for (int i = 0; i < table.getItemCount(); i++) {
			if (table.getItem(i).getChecked()) {
				okButton.setEnabled(true);
				return;
			}
		}
		okButton.setEnabled(false);
	}
    
    public IProgressMonitor getProgressMonitor() {
    	return monitor;
    }
    
    private static class SynthesisProgressMonitor extends ProgressMonitorPart {

		public SynthesisProgressMonitor(Composite parent, Layout layout) {
			super(parent, layout);
		}

		@Override
		public void beginTask(final String name, final int totalWork) {
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					SynthesisProgressMonitor.super.beginTask(name, totalWork);
				}
        	});
	    }

		@Override
	    public void setTaskName(final String name) {
	    	Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					SynthesisProgressMonitor.super.setTaskName(name);
				}
        	});
	    }

		@Override
		public void worked(final int work) {
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					SynthesisProgressMonitor.super.worked(work);
				}
        	});
		}

	    @Override
		public void done() {
	    	Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					SynthesisProgressMonitor.super.done();
				}
        	});
	    }
    	
    }
    
    public void addExpressions(ArrayList<Expression> foundExprs) {
    	expressions.addAll(foundExprs);
    	if (sorterWorker != null)
    		sorterWorker.cancel();
		sorterWorker = new SorterWorker(expressions);
		sorterWorker.setPriority(Job.LONG);
		sorterWorker.schedule();
    }

	private final class SorterWorker extends Job {
		
		private final ArrayList<Expression> expressions;

		private SorterWorker(ArrayList<Expression> expressions) {
			super("Result sorter");
			this.expressions = expressions;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
	    	Collections.sort(expressions, expressionSorter);
	    	if (monitor.isCanceled())
	    		return Status.CANCEL_STATUS;
	    	Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					showResults();
				}
	    	});
			return Status.OK_STATUS;
		}
		
	}
	
	// Logic code
    
    public ExpressionSkeleton getSkeleton() {
    	return skeleton;
    }
    
    public boolean searchConstructors() {
    	return searchConstructorsButton.getSelection();
    }
    
    public boolean searchOperators() {
    	return searchOperatorsButton.getSelection();
    }
    
    public boolean blockedNativeCalls() {
    	return blockedNativeCalls;
    }
    
    public boolean handledSideEffects() {
    	return handledSideEffects;
    }
    
    // Table code
    
    protected void showResults() {
		// Set and show the results.
    	table.setItemCount(filteredExpressions.size());
    	needsToStringColumn = needsToStringColumn || needsToStringColumn(filteredExpressions);
		table.getColumn(2).setWidth(needsToStringColumn ? Math.max(table.getColumn(2).getWidth(), TABLE_WIDTH / 3) : 0);
    	table.clearAll();
    	// Enable/Disable check/selection buttons.
    	boolean haveResults = !filteredExpressions.isEmpty();
    	checkAllButton.setEnabled(haveResults);
    	uncheckAllButton.setEnabled(haveResults);
    	checkSelectedButton.setEnabled(haveResults);
    	uncheckSelectedButton.setEnabled(haveResults);
    }
    
    private TableColumn addColumn(String label, final int index, int width) {
    	final TableColumn column = new TableColumn(table, SWT.NONE);
    	column.setText(label);
    	column.setWidth(width);
    	column.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
            	if (filteredExpressions == null)
            		return;
            	final int direction = table.getSortColumn() == column ? (table.getSortDirection() == SWT.DOWN ? SWT.UP : SWT.DOWN) : SWT.DOWN;
            	Collections.sort(filteredExpressions, new Comparator<Expression>() {
					@Override
					public int compare(Expression e1, Expression e2) {
			    		int result;
			    		if (index == 0)
			    			result = getExpressionLabel(e1).compareTo(getExpressionLabel(e2));
			    		else if (index == 1 || index == 2) {
			    			IJavaValue e1Value = expressionEvaluator.getValue(e1, Collections.<Effect>emptySet());
			    			IJavaValue e2Value = expressionEvaluator.getValue(e2, Collections.<Effect>emptySet());
			    			if (e1 instanceof IJavaPrimitiveValue && e2 instanceof IJavaPrimitiveValue)
			    				result = (int)(((IJavaPrimitiveValue)e1Value).getDoubleValue() - ((IJavaPrimitiveValue)e2Value).getDoubleValue());
			    			else if (index == 1)
			    				result = getValueLabel(e1).compareTo(getValueLabel(e2));
			    			else// if (index == 2)
			    				result = getToStringLabel(e1).compareTo(getToStringLabel(e2));
			    		} else if (index == 3)
		    				result = getEffectsLabel(e1).compareTo(getEffectsLabel(e2));
			    		else
			    			throw new RuntimeException("Unexpected column: " + index);
			    		if (direction == SWT.UP)
			    			result = -result;
			    		return result;
					}
            		
            	});
				table.setSortDirection(direction);
				table.setSortColumn(column);
            	table.clearAll();
            }
        });
    	return column;
    }
    
    private static String getExpressionLabel(Expression e) {
    	return e.toString();
    }
    
    private String getValueLabel(Expression e) {
    	try {
			return EclipseUtils.javaStringOfValue(expressionEvaluator.getValue(e, Collections.<Effect>emptySet()), stack, false);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
    }
    
    private String getToStringLabel(Expression e) {
    	return expressionEvaluator.getResultString(e);
    }
    
    private String getEffectsLabel(Expression e) {
    	return expressionEvaluator.getResult(e, Collections.<Effect>emptySet()).getResultString("");
    }
    
    private static boolean needsToStringColumn(ArrayList<Expression> expressions) {
    	for (Expression expr: expressions)
    		if (needsToStringColumn(expr.getStaticType()))
    			return true;
    	return false;
    }
    
    private static boolean needsToStringColumn(IJavaType type) {
    	try {
    		if (type instanceof IJavaArrayType)
    			return needsToStringColumn(((IJavaArrayType)type).getComponentType());
    		else
    			return EclipseUtils.isObject(type) && type != null && !"Ljava/lang/String;".equals(type.getSignature());
    	} catch (DebugException e) {
    		throw new RuntimeException(e);
    	}
    }
    
	private void setAllChecked(boolean state) {
		for (TableItem item: table.getItems())
			if (item.getChecked() != state)
				item.setChecked(state);
		okButton.setEnabled(state && table.getItemCount() > 0);
	}
    
	private void setSelectedChecked(boolean state) {
		TableItem[] selected = table.getSelection();
		for (TableItem item: selected)
			item.setChecked(state);
		if (selected.length > 0) {
			if (state)
				okButton.setEnabled(true);
			else {
				for (TableItem item: table.getItems()) {
					if (item.getChecked()) {
						okButton.setEnabled(true);
						return;
					}
				}
				okButton.setEnabled(false);
			}
		}
	}
	
	private void filterExpressions(String text) {
		if (text.isEmpty()) {
			filteredExpressions = expressions;
			if (monitorLabel != null) {
				monitorLabel.dispose();
				monitorComposite.getParent().layout(true);
			}
			showResults();
		} else {
			if (filterWorker != null && amFiltering) {
				monitor.setCanceled(true);
				try {
					filterWorker.join();  // Wait for the child job to finish and cleanup (e.g., dispose its monitor).
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (javadocPrefetcher != null)  // Cancel the prefetcher for efficiency.
				javadocPrefetcher.cancel();
			amFiltering = true;
			startOrEndWork(true, null);
			filteredExpressions = new ArrayList<Expression>();
			showResults();
			filterWorker = new FilterWorker(text, expressions, expressionEvaluator);
			filterWorker.setPriority(Job.LONG);
			filterWorker.schedule();
		}
    }

	// TODO: This messes up the sort order since I sort the filteredExpressions.  So if you filter, sort, then unfilter, the table looks like it has the new filter but it actually has the old one.  The best thing is to probably sort both the filtered and unfiltered expressions each time, but another option is to save/restore the old table look when filtering/reseting.
	private final class FilterWorker extends Job {
		
		private final String[] filterWords;
		private final ArrayList<Expression> expressions;
		private final ExpressionEvaluator expressionEvaluator;

		private FilterWorker(String filterText, ArrayList<Expression> expressions, ExpressionEvaluator expressionEvaluator) {
			super("Result filter");
			this.filterWords = filterText.toLowerCase().split(" +");
			this.expressions = expressions;
			this.expressionEvaluator = expressionEvaluator;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			final ArrayList<Expression> filteredExpressions = SynthesisDialog.this.filteredExpressions;
			SynthesisDialog.this.monitor.beginTask("Result filter", expressions.size());
			long lastUpdate = 0;
			int lastUpdateSize = 0;
			exprLoop: for (Expression expr: expressions) {
				if (SynthesisDialog.this.monitor.isCanceled())
					return finished(false);
				long curTime = System.currentTimeMillis();
				if (curTime - lastUpdate > 1000 && filteredExpressions.size() > lastUpdateSize) {  // Update the screen at most every second.
					Display.getDefault().asyncExec(new Runnable(){
						@Override
						public void run() {
							showResults();
						}
					});
					lastUpdate = curTime;
					lastUpdateSize = filteredExpressions.size();
				}
				SynthesisDialog.this.monitor.worked(1);
				String exprString = expr.toString().toLowerCase();
				String javadocs = null;
				boolean readJavadocs = false;
				for (String filterWord: filterWords) {
					if (exprString.contains(filterWord))
						continue;
					if (expressionEvaluator.getResultString(expr).toLowerCase().contains(filterWord))
						continue;
					if (!readJavadocs) {  // Lazily initialize for efficiency.
						javadocs = getJavadocs(expr, expressionEvaluator, false);
						readJavadocs = true;
						if (javadocs != null)
							javadocs = javadocs.toLowerCase();
					}
					if (javadocs == null || !javadocs.contains(filterWord))
						continue exprLoop;  // We count things without Javadocs as not matching.
				}
				filteredExpressions.add(expr);
			}
			SynthesisDialog.this.monitor.done();
	    	return finished(true);
		}

		private IStatus finished(final boolean finished) {
			Display.getDefault().syncExec(new Runnable(){  // We do this synchronously since we need it for cancellation and it doesn't matter otherwise as we're just about to end.
				@Override
				public void run() {
					if (!finished)  // Restore the original list of expressions if we cancelled.
						filteredExpressions = expressions;
					startOrEndWork(false, filteredExpressions.isEmpty() ? "All expressions have been filtered away." : null);
					amFiltering = false;
					showResults();
				}
	    	});
			return finished ? Status.OK_STATUS : Status.CANCEL_STATUS;
		}
		
	}
	
	/*
	 * The commented code below tries to automatically install
	 * side-effect-handling breakpoints when the CodeHint dialog opens
	 * and deletes them when it closes.
	 * To use it, uncomment the SideEffectStarterStopper class and all
	 * the code that uses it.  Also in Synthesize comment out the calls
	 * to SideEffectHandler.{start,stop}.
	 * Note that this is somewhat buggy w.r.t. the GUI.  Disabling the
	 * search button until the breakpoints are installed is broken (as
	 * we just disable it, but anything else that can enable it will,
	 * e.g., by typing a new pdspec) and when users close the GUI we
	 * block the SWT thread on the closing, which stops the progress
	 * monitor from updating.
	 */
	
	/*private final class SideEffectStartStopper extends Job {
		
		private final boolean isStart;

		public SideEffectStartStopper(boolean isStart) {
			super("Side effect " + (isStart ? "setup" : "stop"));
			this.isStart = isStart;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			sideEffectHandler.enable(true);
			if (isStart)
				sideEffectHandler.start(monitor);
			else
				sideEffectHandler.stop(monitor);
			if (isStart)
				Display.getDefault().asyncExec(new Runnable(){
					@Override
					public void run() {
						searchCancelButton.setEnabled(true);
					}
		    	});
			return Status.OK_STATUS;
		}
		
		public void start() {
			setPriority(Job.LONG);
			schedule();
		}
		
	}*/
	
	@Override
    protected void opened() {
		automaticallyStartSynthesisIfPossible();
		/*searchCancelButton.setEnabled(false);
		sideEffectStartStopper = new SideEffectStartStopper(true);
		sideEffectStartStopper.start();*/
	}
	
	/*@Override
	public boolean close() {
		if (sideEffectStartStopper != null)
			try {
				sideEffectStartStopper.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		sideEffectStartStopper = new SideEffectStartStopper(false);
		sideEffectStartStopper.start();
		return super.close();
	}*/

    /**
     * Called when the user changes the type of the pdspec
     * they intend to give.
     */
    protected void propertyTypeChanged() {
		automaticallyStartSynthesisIfPossible();
	}
	
	private void automaticallyStartSynthesisIfPossible() {
		/*if (expressions == null)
			startSearch(StateProperty.fromPropertyString(propertyDialog.getVarName(), "true"), true);*/
	}
	
	public ExpressionEvaluator getExpressionEvaluator() {
		return expressionEvaluator;
	}
	
	@Override
	protected Point getInitialLocation(Point initialSize) {
		// Put the dialog relatively near the bottom of the screen.
		Rectangle shellBounds = getParentShell().getBounds();
		return new Point(shellBounds.x + shellBounds.width / 2 - initialSize.x / 2, shellBounds.y + shellBounds.height / 2 - initialSize.y / 3);
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
		if (sideEffectHandler.isEnabled())
			sideEffectHandler.emptyDisabledCollections();
		valueCache.allowCollectionOfDisabledObjects();  // Allow collection of objects from the last search.
		table.dispose();
		if (monitor != null)
			monitor.dispose();
		skeletonInput = null;
		skeletonResult = null;
		searchCancelButton = null;
		monitorComposite = null;
		monitor = null;
		table = null;
		expressions = null;
		lastExpressions = null;
		filteredExpressions = null;
		checkAllButton = null;
		uncheckAllButton = null;
		checkSelectedButton = null;
		uncheckSelectedButton = null;
		subtypeChecker = null;
		typeCache = null;
		valueCache = null;
		timeoutChecker = null;
		nativeHandler = null;
		sideEffectHandler = null;
		expressionMaker = null;
		expressionEvaluator = null;
		evalManager = null;
		staticEvaluator = null;
		expressionGenerator = null;
		skeleton = null;
		expressionSorter = null;
	}
    
    // Expression validator

	private static class ExpressionSkeletonValidator implements IInputValidator {

	    private final IJavaStackFrame stackFrame;
	    private final IAstEvaluationEngine evaluationEngine;
	    private final String varTypeName;
	    
	    public ExpressionSkeletonValidator(IJavaStackFrame stackFrame, String varTypeName, IAstEvaluationEngine evaluationEngine) {
	    	this.stackFrame = stackFrame;
	    	this.evaluationEngine = evaluationEngine;
	    	this.varTypeName = varTypeName;
	    }
	    
	    @Override
		public String isValid(String newText) {
	    	return ExpressionSkeleton.isLegalSkeleton(newText, varTypeName, stackFrame, evaluationEngine);
	    }
	}

}
