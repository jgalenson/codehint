package codehint.dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.resource.JFaceResources;
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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
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

import com.sun.jdi.Field;
import com.sun.jdi.Method;

import codehint.Activator;
import codehint.Synthesizer.SynthesisWorker;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluationManager;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.expreval.NativeHandler;
import codehint.expreval.StaticEvaluator;
import codehint.expreval.TimeoutChecker;
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

public class InitialSynthesisDialog extends SynthesisDialog {
	
	private final String initialSkeletonText;
	private StyledText skeletonInput;
	private boolean skeletonIsValid;
	private final IInputValidator skeletonValidator;
	private String skeletonResult;
	private Button searchConstructorsButton;
	private Button searchOperatorsButton;
	private Button searchNativeCalls;
	private Button handleSideEffects;

    private static final int searchCancelButtonID = IDialogConstants.CLIENT_ID;
    private Button searchCancelButton;
    private boolean amSearching;
    private int numSearches;
    private boolean shouldContinue;
    private Composite monitorComposite;
    private ProgressMonitorPart monitor;
    private static final int MONITOR_WIDTH = 300;
    private JavadocPrefetcher javadocPrefetcher;
    private SorterWorker sorterWorker;
    private Label monitorLabel;

    private static final int TABLE_WIDTH = MESSAGE_WIDTH;
    private static final int TABLE_HEIGHT = 300;
    private Table table;
    private int maxExprLen;
    private int maxResultLen;
    private ArrayList<FullyEvaluatedExpression> expressions;
    private ArrayList<FullyEvaluatedExpression> lastExpressions;
    private ArrayList<FullyEvaluatedExpression> filteredExpressions;
    private Button checkAllButton;
    private Button uncheckAllButton;
    private static final int checkSelectedButtonID = IDialogConstants.CLIENT_ID + 2;
    private Button checkSelectedButton;
    private static final int uncheckSelectedButtonID = IDialogConstants.CLIENT_ID + 3;
    private Button uncheckSelectedButton;
    
    private Composite filterComposite;
    private static final int filterButtonID = IDialogConstants.CLIENT_ID + 4;
    private static final int clearButtonID = IDialogConstants.CLIENT_ID + 5;
    private FilterWorker filterWorker;
    private boolean amFiltering;

    private final IJavaProject project;
    private final IJavaDebugTarget target;
    private final IAstEvaluationEngine evaluationEngine;
	private final SynthesisWorker worker;
    private TypeCache typeCache;
    private ValueCache valueCache;
    private SubtypeChecker subtypeChecker;
    private TimeoutChecker timeoutChecker;
    private NativeHandler nativeHandler;
    private SideEffectHandler sideEffectHandler;
    private ExpressionMaker expressionMaker;
    private EvaluationManager evalManager;
    private StaticEvaluator staticEvaluator;
    private ExpressionGenerator expressionGenerator;
    private ExpressionSkeleton skeleton;
    private ExpressionSorter expressionSorter;

	public InitialSynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, SynthesisWorker worker) {
		super(parentShell, varTypeName, varType, stack, propertyDialog);
		IAstEvaluationEngine engine = EclipseUtils.getASTEvaluationEngine(stack);
		this.initialSkeletonText = ExpressionSkeleton.HOLE_SYNTAX;
		this.skeletonIsValid = false;
		this.skeletonValidator = new ExpressionSkeletonValidator(stack, varTypeName, engine);
		this.skeletonResult = null;
		this.searchCancelButton = null;
		this.amSearching = false;
		this.numSearches = 0;
		this.shouldContinue = true;
		this.monitor = null;
		this.table = null;
		this.maxExprLen = 0;
		this.maxResultLen = 0;
		this.expressions = null;
		this.lastExpressions = null;
		this.filteredExpressions = null;
		this.amFiltering = false;
		this.worker = worker;
		this.project = EclipseUtils.getProject(stack);
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.evaluationEngine = engine;
		this.typeCache = new TypeCache();
		this.valueCache = new ValueCache(target);
		this.subtypeChecker = new SubtypeChecker(stack, target, typeCache);
		IJavaThread thread = (IJavaThread)stack.getThread();
		this.timeoutChecker = new TimeoutChecker(thread, stack, target, typeCache);
		this.nativeHandler = new NativeHandler(thread, stack, target, typeCache);
		this.sideEffectHandler = new SideEffectHandler(stack, project);
		this.expressionMaker = new ExpressionMaker(stack, valueCache, typeCache, timeoutChecker, nativeHandler, sideEffectHandler);
		this.evalManager = new EvaluationManager(varType == null, stack, expressionMaker, subtypeChecker, typeCache, valueCache, timeoutChecker);
		this.staticEvaluator = new StaticEvaluator(stack, typeCache, valueCache);
		Weights weights = new Weights();
		this.expressionGenerator = new ExpressionGenerator(target, stack, sideEffectHandler, expressionMaker, subtypeChecker, typeCache, valueCache, evalManager, staticEvaluator, weights);
		this.skeleton = null;
		this.expressionSorter = new ExpressionSorter(expressionMaker, weights);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		
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
		
		Composite topButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchCancelButton = createButton(topButtonComposite, searchCancelButtonID, "Search", true);
		searchCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
		
		monitorComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 1);
		monitorLabel = null;
		
		table = new Table(composite, SWT.BORDER | SWT.CHECK | SWT.MULTI | SWT.VIRTUAL);
		TableViewer tableViewer = new TableViewer(table);
		table.addListener(SWT.SetData, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableItem item = (TableItem)event.item;
				int index = event.index;
				FullyEvaluatedExpression expr = filteredExpressions.get(index);
				item.setData(expr);
				String exprLabel = getExpressionLabel(expr);
				String resultLabel = getValueLabel(expr);
				item.setText(new String[] { exprLabel, resultLabel });
				if (exprLabel.length() > maxExprLen) {
					table.getColumn(0).pack();
					maxExprLen = exprLabel.length();
				}
				if (resultLabel.length() > maxResultLen) {
					table.getColumn(1).pack();
					maxResultLen = resultLabel.length();
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
    	TableColumn column1 = addColumn("Expression", 0, TABLE_WIDTH / 2);
    	(new TableViewerColumn(tableViewer, column1)).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getToolTipText(Object element) {
				FullyEvaluatedExpression expr = (FullyEvaluatedExpression)element;
				String javadoc = getJavadocs(expr.getExpression(), expressionMaker, true);
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
    	addColumn("Result", 1, TABLE_WIDTH / 2);
    	ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
    	tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				TableItem clickedItem = table.getItem(table.getSelectionIndex());
				Object doubleClickedElement = ((IStructuredSelection)event.getSelection()).getFirstElement();
				if (clickedItem.getData() == doubleClickedElement)
					clickedItem.setChecked(!clickedItem.getChecked());
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
		final Text filterText = new Text(filterComposite, SWT.BORDER | SWT.WRAP | SWT.SINGLE);
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

	private class SkeletonModifyHandler extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
        	skeletonIsValid = !hasError;
        	if (searchCancelButton != null && !amSearching && !amFiltering)
        		searchCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
        	ensureNoContinue();
        }
		
	}
	
	private class InitialSynthesisPropertyModifyListener extends PropertyModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			super.inputChanged(hasError);
        	if (searchCancelButton != null && !amSearching && !amFiltering)
        		searchCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
        	ensureNoContinue();
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
    
    private void ensureNoContinue() {
    	 numSearches = 0;
    	 shouldContinue = false;
     	if (searchCancelButton != null && !amSearching && !amFiltering)
     		setSearchCancelButtonText("Search");
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == searchCancelButtonID) {
        	if (!amSearching && !amFiltering)
        		startSearch(propertyDialog.computeProperty(pdspecInput.getText(), typeCache), false);
        	else
        		monitor.setCanceled(true);
        } else if (buttonId == IDialogConstants.OK_ID) {
         	results = new ArrayList<FullyEvaluatedExpression>();
         	for (int i = 0; i < table.getItemCount(); i++)
         		if (table.getItem(i).getChecked())
         			results.add((FullyEvaluatedExpression)table.getItem(i).getData());
    	}
        super.buttonPressed(buttonId);
    }

	private void startSearch(Property prop, boolean isAutomatic) {
		property = prop;
		skeletonResult = skeletonInput.getText();
		skeleton = ExpressionSkeleton.fromString(skeletonResult, target, stack, expressionMaker, evaluationEngine, subtypeChecker, typeCache, valueCache, evalManager, staticEvaluator, expressionGenerator, sideEffectHandler);
		startEndSynthesis(isAutomatic ? SynthesisState.AUTO_START : SynthesisState.START);
		lastExpressions = expressions;
		expressions = new ArrayList<FullyEvaluatedExpression>();
		filteredExpressions = expressions;
		showResults();  // Clears any existing results.
		valueCache.allowCollectionOfDisabledObjects();  // Allow collection of objects from previous search.
		this.shouldContinue = !isAutomatic;
		// Reset column sort indicators.
		table.setSortDirection(SWT.NONE);
		table.setSortColumn(null);
		// Start the synthesis
		boolean blockNatives = isAutomatic || !searchNativeCalls.getSelection();
		nativeHandler.enable(blockNatives);
		sideEffectHandler.enable(handleSideEffects.getSelection());
		worker.synthesize(this, evalManager, numSearches, timeoutChecker, blockNatives, sideEffectHandler);
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

	private void startEndSynthesis(SynthesisState state) {
		boolean isStarting = isStart(state);
        getButton(IDialogConstants.CANCEL_ID).setEnabled(!isStarting);
		if (state == SynthesisState.END && shouldContinue)
			numSearches++;
        startOrEndWork(isStarting, state == SynthesisState.END && expressions.isEmpty() ? "No expressions found.  You may press \"Continue Search\" to search further or change some search options." : null);
    	amSearching = isStarting;
    	filterComposite.setVisible(!isStarting && !expressions.isEmpty());
    	if (state == SynthesisState.END) {
    		javadocPrefetcher = new JavadocPrefetcher(this.filteredExpressions, this.expressionMaker);
    		javadocPrefetcher.setPriority(Job.DECORATE);
    		javadocPrefetcher.schedule();
    		if (expressions.isEmpty() && numSearches == 1)  // Automatically continue search if no results were found at depth 1.
    			startSearch(property, false);
    	} else if (state == SynthesisState.UNFINISHED && lastExpressions != null) {
    		filteredExpressions = expressions = lastExpressions;
    		showResults();
    	}
    }
	
	private void startOrEndWork(boolean isStarting, String message) {
    	searchCancelButton.setEnabled(isStarting || (pdspecIsValid && skeletonIsValid));
    	setSearchCancelButtonText(isStarting ? "Cancel" : shouldContinue && numSearches > 0 ? "Continue search" : "Search");
    	if (!isStarting && message != null)
			monitorLabel = createLabel(monitorComposite, message, SWT.CENTER, SWT.DEFAULT);
    	else if (monitorLabel != null)
			monitorLabel.dispose();
    	if (isStarting) {  // Enable progress monitor.
    		monitor = new SynthesisProgressMonitor(monitorComposite, null);
    		//monitor.attachToCancelComponent(searchButton);
    		GridData gridData = new GridData();
    		gridData.widthHint = MONITOR_WIDTH;
    		monitor.setLayoutData(gridData);
    	} else  // Disable progress monitor.
    		monitor.dispose();
		monitorComposite.getParent().layout(true);
	}

	// Prefetch the Javadocs to reduce the waiting time (which can be noticeable without this) when the user hovers over an expression.
	// Copy fields used to avoid a race with closing the dialog.
	private final class JavadocPrefetcher extends Job {
		
		private final ArrayList<FullyEvaluatedExpression> expressions;
		private final ExpressionMaker expressionMaker;

		private JavadocPrefetcher(ArrayList<FullyEvaluatedExpression> expressions, ExpressionMaker expressionMaker) {
			super("Javadoc prefetch");
			this.expressions = expressions;
			this.expressionMaker = expressionMaker;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			int num = Math.min(expressions.size(), 20);
			monitor.beginTask("Javadoc prefetch", num);
			for (int i = 0; i < num; i++) {
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				getJavadocs(expressions.get(i).getExpression(), expressionMaker, false);
				monitor.worked(1);
			}
			monitor.done();
			return Status.OK_STATUS;
		}
		
	}
	
	private String getJavadoc(Expression expr, ExpressionMaker expressionMaker, boolean prettify) {
		try {
			Method method = expressionMaker.getMethod(expr);
			if (method != null) {
				IMethod imethod = EclipseUtils.getIMethod(method, project);
				if (imethod != null)
					return getJavadocFast(imethod, prettify);
			}
			Field field = expressionMaker.getField(expr);
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
	
	private String getJavadocs(Expression expr, final ExpressionMaker expressionMaker, final boolean prettify) {
    	final StringBuffer javadocs = new StringBuffer();
    	expr.accept(new ASTVisitor() {
    		@Override
    		public void postVisit(ASTNode node) {
    			if (node instanceof Expression) {
    				String javadoc = getJavadoc((Expression)node, expressionMaker, prettify);
    				if (javadoc != null) {
    					if (javadocs.length() > 0)
    						javadocs.append("\n\n-----\n\n");
    					javadocs.append(javadoc);
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
	
	private void setSearchCancelButtonText(String text) {
		if (!text.equals(searchCancelButton.getText())) {
			searchCancelButton.setText(text);
			setButtonLayoutData(searchCancelButton);
			searchCancelButton.getParent().getParent().layout(true);
		}
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
    
    public void addExpressions(ArrayList<FullyEvaluatedExpression> foundExprs) {
    	expressions.addAll(foundExprs);
    	if (sorterWorker != null)
    		sorterWorker.cancel();
		sorterWorker = new SorterWorker(expressions);
		sorterWorker.setPriority(Job.LONG);
		sorterWorker.schedule();
    }

	private final class SorterWorker extends Job {
		
		private final ArrayList<FullyEvaluatedExpression> expressions;

		private SorterWorker(ArrayList<FullyEvaluatedExpression> expressions) {
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
    
    // Table code
    
    private void showResults() {
		// Set and show the results.
    	table.setItemCount(filteredExpressions.size());
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
            	final int direction = table.getSortColumn() == column ? (table.getSortDirection() == SWT.DOWN ? SWT.UP : SWT.DOWN) : SWT.DOWN;
            	Collections.sort(filteredExpressions, new Comparator<FullyEvaluatedExpression>() {
					@Override
					public int compare(FullyEvaluatedExpression e1, FullyEvaluatedExpression e2) {
			    		int result;
			    		if (index == 0)
			    			result = getExpressionLabel(e1).compareTo(getExpressionLabel(e2));
			    		else if (index == 1) {
			    			if (e1.getValue() instanceof IJavaPrimitiveValue && e2.getValue() instanceof IJavaPrimitiveValue)
			    				result = (int)(((IJavaPrimitiveValue)e1.getValue()).getDoubleValue() - ((IJavaPrimitiveValue)e2.getValue()).getDoubleValue());
			    			else
			    				result = getValueLabel(e1).compareTo(getValueLabel(e2));
			    		} else
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
    
    private static String getExpressionLabel(FullyEvaluatedExpression e) {
    	return e.getSnippet();
    }
    
    private static String getValueLabel(FullyEvaluatedExpression e) {
    	return e.getResult().getResultString(e.getResultString());
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
			filteredExpressions = new ArrayList<FullyEvaluatedExpression>();
			showResults();
			filterWorker = new FilterWorker(text, expressions, expressionMaker);
			filterWorker.setPriority(Job.LONG);
			filterWorker.schedule();
		}
    }

	// TODO: This messes up the sort order since I sort the filteredExpressions.  So if you filter, sort, then unfilter, the table looks like it has the new filter but it actually has the old one.  The best thing is to probably sort both the filtered and unfiltered expressions each time, but another option is to save/restore the old table look when filtering/reseting.
	private final class FilterWorker extends Job {
		
		private final String[] filterWords;
		private final ArrayList<FullyEvaluatedExpression> expressions;
		private final ExpressionMaker expressionMaker;

		private FilterWorker(String filterText, ArrayList<FullyEvaluatedExpression> expressions, ExpressionMaker expressionMaker) {
			super("Result filter");
			this.filterWords = filterText.toLowerCase().split(" +");
			this.expressions = expressions;
			this.expressionMaker = expressionMaker;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			final ArrayList<FullyEvaluatedExpression> filteredExpressions = InitialSynthesisDialog.this.filteredExpressions;
			InitialSynthesisDialog.this.monitor.beginTask("Result filter", expressions.size());
			long lastUpdate = 0;
			int lastUpdateSize = 0;
			exprLoop: for (FullyEvaluatedExpression expr: expressions) {
				if (InitialSynthesisDialog.this.monitor.isCanceled())
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
				InitialSynthesisDialog.this.monitor.worked(1);
				String exprString = expr.getExpression().toString().toLowerCase();
				String javadocs = null;
				boolean readJavadocs = false;
				for (String filterWord: filterWords) {
					if (exprString.contains(filterWord))
						continue;
					if (expr.getResultString().contains(filterWord))
						continue;
					if (!readJavadocs) {  // Lazily initialize for efficiency.
						javadocs = getJavadocs(expr.getExpression(), expressionMaker, false);
						readJavadocs = true;
						if (javadocs != null)
							javadocs = javadocs.toLowerCase();
					}
					if (javadocs == null || !javadocs.contains(filterWord))
						continue exprLoop;  // We count things without Javadocs as not matching.
				}
				filteredExpressions.add(expr);
			}
			InitialSynthesisDialog.this.monitor.done();
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
	
	@Override
    protected void opened() {
		automaticallyStartSynthesisIfPossible();
	}
	
	@Override
    protected void propertyTypeChanged() {
		automaticallyStartSynthesisIfPossible();
	}
	
	private void automaticallyStartSynthesisIfPossible() {
		/*if (expressions == null)
			startSearch(StateProperty.fromPropertyString(propertyDialog.getVarName(), "true"), true);*/
	}
	
	@Override
	protected Point getInitialLocation(Point initialSize) {
		// Put the dialog relatively near the bottom of the screen.
		Rectangle shellBounds = getParentShell().getBounds();
		return new Point(shellBounds.x + shellBounds.width / 2 - initialSize.x / 2, shellBounds.y + shellBounds.height / 2 - initialSize.y / 3);
	}

	@Override
	public void cleanup() {
		super.cleanup();
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
