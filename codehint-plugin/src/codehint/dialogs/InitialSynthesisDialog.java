package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;
import com.sun.jdi.Method;

import codehint.Activator;
import codehint.Synthesizer.SynthesisWorker;
import codehint.expreval.EvaluationManager;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.expreval.NativeHandler;
import codehint.expreval.StaticEvaluator;
import codehint.expreval.TimeoutChecker;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.ExpressionMaker;
import codehint.exprgen.ExpressionSkeleton;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.ValueCache;
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

    private static final int searchCancelButtonID = IDialogConstants.CLIENT_ID;
    private Button searchCancelButton;
    private boolean amSearching;
    private int numSearches;
    private boolean shouldContinue;
    private Composite monitorComposite;
    private ProgressMonitorPart monitor;
    private static final int MONITOR_WIDTH = 300;
    private JavadocPrefetcher javadocPrefetcher;

    private static final int TABLE_WIDTH = 500;
    private static final int TABLE_HEIGHT = 300;
    private TableViewer tableViewer;
    private Table table;
    private SynthesisResultComparator synthesisResultComparator;
    private ArrayList<FullyEvaluatedExpression> expressions;  // This does not reflect the sort order of the expressions in the table, so get items from the table directly.
    private Button checkAllButton;
    private Button uncheckAllButton;
    private static final int checkSelectedButtonID = IDialogConstants.CLIENT_ID + 2;
    private Button checkSelectedButton;
    private static final int uncheckSelectedButtonID = IDialogConstants.CLIENT_ID + 3;
    private Button uncheckSelectedButton;

    private final IJavaProject project;
    private final IJavaDebugTarget target;
    private final IAstEvaluationEngine evaluationEngine;
	private final SynthesisWorker worker;
    private SubtypeChecker subtypeChecker;
    private TypeCache typeCache;
    private ValueCache valueCache;
    private TimeoutChecker timeoutChecker;
    private NativeHandler nativeHandler;
    private ExpressionMaker expressionMaker;
    private EvaluationManager evalManager;
    private StaticEvaluator staticEvaluator;
    private ExpressionGenerator expressionGenerator;
    private ExpressionSkeleton skeleton;

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
		this.tableViewer = null;
		this.table = null;
		this.synthesisResultComparator = null;
		this.expressions = null;
		this.worker = worker;
		this.project = EclipseUtils.getProject(stack);
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.evaluationEngine = engine;
		this.subtypeChecker = new SubtypeChecker();
		this.typeCache = new TypeCache();
		this.valueCache = new ValueCache(target);
		IJavaThread thread = (IJavaThread)stack.getThread();
		this.timeoutChecker = new TimeoutChecker(thread, stack, target, typeCache);
		this.nativeHandler = new NativeHandler(thread, stack, target, typeCache);
		this.expressionMaker = new ExpressionMaker(valueCache, timeoutChecker, nativeHandler);
		this.evalManager = new EvaluationManager(stack, expressionMaker, subtypeChecker, typeCache, valueCache, timeoutChecker);
		this.staticEvaluator = new StaticEvaluator(stack, typeCache, valueCache);
		this.expressionGenerator = new ExpressionGenerator(target, stack, expressionMaker, subtypeChecker, typeCache, valueCache, evalManager, staticEvaluator);
		this.skeleton = null;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		
		String message = "Give a skeleton describing the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names and " + ExpressionSkeleton.LIST_HOLE_SYNTAX + "s for an unknown number of arguments.";
		Composite skeletonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 1);
		skeletonInput = createInput(skeletonComposite, message, initialSkeletonText, skeletonValidator, new SkeletonModifyHandler(), "skeleton");
		
		Composite skeletonButtonComposite = makeChildComposite(skeletonComposite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchConstructorsButton = createCheckBoxButton(skeletonButtonComposite, "Search constructors");
		if (!EclipseUtils.isObject(varType))
			searchConstructorsButton.setEnabled(false);
		searchOperatorsButton = createCheckBoxButton(skeletonButtonComposite, "Search operators");
		
		Composite searchOptionsComposite = makeChildComposite(skeletonComposite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchNativeCalls = createCheckBoxButton(searchOptionsComposite, "Call non-standard native methods (fast but dangerous)");
		searchNativeCalls.setSelection(true);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(searchNativeCalls, Activator.PLUGIN_ID + "." + "search-native-calls");
		
		Composite topButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
		searchCancelButton = createButton(topButtonComposite, searchCancelButtonID, "Search", true);
		searchCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
		
		monitorComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 1);
		
		tableViewer = new TableViewer(composite, SWT.BORDER | SWT.CHECK | SWT.MULTI);
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
				return getExpressionLabel((FullyEvaluatedExpression)element);
			}
			
			@Override
			public String getToolTipText(Object element) {
				// TODO: Handle fields and expressions with multiple methods.
				FullyEvaluatedExpression expr = (FullyEvaluatedExpression)element;
				String javadoc = getJavadoc(expr, expressionMaker);
				if (javadoc == null)
					return null;
				javadoc = javadoc.replaceAll("<H3>([^<]|\n)*</H3>\n?", "");
				javadoc = javadoc.replaceAll("<DL>|<DD>|<DT>", "\n");
				javadoc = javadoc.replaceAll("<[^>]*>", "");
				javadoc = javadoc.replaceAll("&nbsp;", " ").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"");
				javadoc = javadoc.replaceAll("[\n\r]{2,}", "\n\n");
				javadoc = javadoc.trim();
				return javadoc;
			}
			
			@Override
			public int getToolTipDisplayDelayTime(Object object) {
				return 100;
			}
		});
    	TableViewerColumn column2 = addColumn("Value", 1);
    	column2.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return getValueLabel((FullyEvaluatedExpression)element);
			}
		});
    	tableViewer.setContentProvider(ArrayContentProvider.getInstance());
    	synthesisResultComparator = new SynthesisResultComparator();
    	ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(table, Activator.PLUGIN_ID + "." + "candidate-selector");

		Composite bottomButtonComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 0);
        checkAllButton = createButton(bottomButtonComposite, IDialogConstants.SELECT_ALL_ID, "Check All", false);
        checkAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
			public void widgetSelected(SelectionEvent e) {
                setAllChecked(true);
            }
        });
        checkAllButton.setEnabled(false);
        uncheckAllButton = createButton(bottomButtonComposite, IDialogConstants.DESELECT_ALL_ID, "Uncheck All", false);
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
		
		return composite;
	}

	private class SkeletonModifyHandler extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
        	skeletonIsValid = !hasError;
        	if (searchCancelButton != null && !amSearching)
        		searchCancelButton.setEnabled(pdspecIsValid && skeletonIsValid);
        	ensureNoContinue();
        }
		
	}
	
	private class InitialSynthesisPropertyModifyListener extends PropertyModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			super.inputChanged(hasError);
        	if (searchCancelButton != null && !amSearching)
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
     	if (searchCancelButton != null && !amSearching)
     		setSearchCancelButtonText("Search");
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == searchCancelButtonID) {
        	if (!amSearching)
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
		skeleton = ExpressionSkeleton.fromString(skeletonResult, target, stack, expressionMaker, evaluationEngine, subtypeChecker, typeCache, valueCache, evalManager, staticEvaluator, expressionGenerator);
		startEndSynthesis(isAutomatic ? SynthesisState.AUTO_START : SynthesisState.START);
		expressions = new ArrayList<FullyEvaluatedExpression>();
		showResults();  // Clears any existing results.
		valueCache.allowCollectionOfNewStrings();  // Allow collection of strings from previous search.
		this.shouldContinue = !isAutomatic;
		// Reset column sort indicators.
		tableViewer.setComparator(null);  // We want to use the order in which we add elements as the initial sort.
		table.setSortDirection(SWT.NONE);
		table.setSortColumn(null);
		// Set up progress monitor
		monitor = new SynthesisProgressMonitor(monitorComposite, null);
		//monitor.attachToCancelComponent(searchButton);
		GridData gridData = new GridData();
		gridData.widthHint = MONITOR_WIDTH;
		monitor.setLayoutData(gridData);
		monitorComposite.getParent().layout(true);
		// Start the synthesis
		nativeHandler.enable(isAutomatic || !searchNativeCalls.getSelection());
		worker.synthesize(this, evalManager, numSearches, timeoutChecker);
	}

	public void startEndSynthesis(SynthesisState state) {
		boolean isStarting = isStart(state);
        getButton(IDialogConstants.CANCEL_ID).setEnabled(!isStarting);
    	searchCancelButton.setEnabled(isStarting || (pdspecIsValid && skeletonIsValid));
    	amSearching = isStarting;
    	setSearchCancelButtonText(amSearching ? "Cancel" : "Search");
    	if (!isStarting) {
    		monitor.dispose();
    		monitorComposite.getParent().layout(true);
    		if (state == SynthesisState.END && shouldContinue) {
    			numSearches++;
    			setSearchCancelButtonText("Continue search");
    		}
    		javadocPrefetcher = new JavadocPrefetcher(this.expressions, this.expressionMaker);
    		javadocPrefetcher.setPriority(Job.DECORATE);
    		javadocPrefetcher.schedule();
    	}
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
			for (int i = 0; i < expressions.size() && i < 100; i++) {
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				getJavadoc(expressions.get(i), expressionMaker);
			}
			return Status.OK_STATUS;
		}
		
	}
	
	private String getJavadoc(FullyEvaluatedExpression expr, ExpressionMaker expressionMaker) {
		try {
			Method method = expressionMaker.getMethod(expr.getExpression());
			if (method == null)
				return null;
			IMethod imethod = EclipseUtils.getIMethod(method, project);
			if (imethod == null)
				return null;
			return imethod.getAttachedJavadoc(null);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void cancelPressed() {
		if (javadocPrefetcher != null)
			javadocPrefetcher.cancel();
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
		showResults();
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
    	tableViewer.setInput(expressions);
    	table.getColumn(0).pack();  // For some reason I need these two lines to update the screen.
    	table.getColumn(1).pack();
    	// Enable/Disable check/selection buttons.
    	boolean haveResults = !expressions.isEmpty();
    	checkAllButton.setEnabled(haveResults);
    	uncheckAllButton.setEnabled(haveResults);
    	checkSelectedButton.setEnabled(haveResults);
    	uncheckSelectedButton.setEnabled(haveResults);
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
    
    private static String getExpressionLabel(FullyEvaluatedExpression e) {
    	return e.getSnippet();
    }
    
    private static String getValueLabel(FullyEvaluatedExpression e) {
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
    		FullyEvaluatedExpression e1 = (FullyEvaluatedExpression)o1;
    		FullyEvaluatedExpression e2 = (FullyEvaluatedExpression)o2;
    		int result;
    		if (column == 0)
    			result = getExpressionLabel(e1).compareTo(getExpressionLabel(e2));
    		else if (column == 1) {
    			if (e1.getValue() instanceof IJavaPrimitiveValue && e2.getValue() instanceof IJavaPrimitiveValue)
    				result = (int)(((IJavaPrimitiveValue)e1.getValue()).getDoubleValue() - ((IJavaPrimitiveValue)e2.getValue()).getDoubleValue());
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
		valueCache.allowCollectionOfNewStrings();  // Allow collection of strings from the last search.
		table.dispose();
		if (monitor != null)
			monitor.dispose();
		skeletonInput = null;
		skeletonResult = null;
		searchCancelButton = null;
		monitorComposite = null;
		monitor = null;
		tableViewer = null;
		table = null;
		synthesisResultComparator = null;
		expressions = null;
		checkAllButton = null;
		uncheckAllButton = null;
		checkSelectedButton = null;
		uncheckSelectedButton = null;
		subtypeChecker = null;
		typeCache = null;
		valueCache = null;
		timeoutChecker = null;
		nativeHandler = null;
		expressionMaker = null;
		evalManager = null;
		staticEvaluator = null;
		expressionGenerator = null;
		skeleton = null;
		super.cleanup();
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
