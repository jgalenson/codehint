package codehint.expreval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.swt.widgets.Display;

import com.sun.jdi.Method;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.exprgen.ExpressionMaker;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.TypedExpression;
import codehint.exprgen.Value;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.StateProperty;
import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;

/**
 * Class for evaluating expressions.
 */
public final class EvaluationManager {
	
	private final static int BATCH_SIZE = 100;
	//private final static int MIN_NUM_BATCHES = 4;

	private final static String IMPL_NAME = "codehint.CodeHintImpl";
	private final static String IMPL_QUALIFIER = IMPL_NAME + ".";
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
	
	public static class EvaluationError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public EvaluationError(String msg) {
			super(msg);
		}
		
	}
	
	private final IJavaStackFrame stack;
	private final IAstEvaluationEngine engine;
	private final IJavaDebugTarget target;
	private final SubtypeChecker subtypeChecker;
	private final IJavaClassType implType;
	private final IJavaFieldVariable validField;
	private final IJavaFieldVariable toStringsField;
	private final IJavaFieldVariable valueCountField;
	private final IJavaFieldVariable fullCountField;
	// As an optimization, we cache expressions that crash and do not evaluate them again.
	private final Set<String> crashingExpressions;
	
	private InitialSynthesisDialog synthesisDialog;
	private IProgressMonitor monitor;
	private String validVal;
	private String preVarsString;
	private String propertyPreconditions;
	
	public EvaluationManager(IJavaStackFrame stack, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		this.stack = stack;
		this.engine = EclipseUtils.getASTEvaluationEngine(stack);
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.subtypeChecker = subtypeChecker;
		this.implType = (IJavaClassType)EclipseUtils.getTypeAndLoadIfNeeded(IMPL_NAME, stack, target, typeCache);
		try {
			this.validField = implType.getField("valid");
			this.toStringsField = implType.getField("toStrings");
			this.valueCountField = implType.getField("valueCount");
			this.fullCountField = implType.getField("fullCount");
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.crashingExpressions = new HashSet<String>();
	}

	/**
	 * Evaluates the given expressions and returns a list
	 * of non-crashing expressions that satisfy the given
	 * property (or all that do not crash if it is null).
	 * @param exprs The expressions to evaluate.
	 * @param property The desired property, or null if there is none.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor a progress monitor, or null if progress reporting and
	 * cancellation are not desired.
     * @return a list of non-crashing expressions that satisfy
     * the given property (or all that do not crash if it is null).
	 */
	public ArrayList<FullyEvaluatedExpression> evaluateExpressions(ArrayList<? extends TypedExpression> exprs, Property property, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		try {
			this.synthesisDialog = synthesisDialog;
			this.monitor = monitor;
			//batchSize = exprs.size() >= 2 * BATCH_SIZE ? BATCH_SIZE : exprs.size() >= MIN_NUM_BATCHES ? exprs.size() / MIN_NUM_BATCHES : 1;
			validVal = property == null ? "true" : property.getReplacedString("_$curValue", stack);
			preVarsString = getPreVarsString(stack, property);
			PropertyPreconditionFinder pf = new PropertyPreconditionFinder();
    		EclipseUtils.parseExpr(parser, validVal).accept(pf);
    		propertyPreconditions = property instanceof ValueProperty ? "" : pf.getPreconditions();  // TODO: This will presumably fail if the user does their own null check.
    		boolean validateStatically = property == null || property instanceof PrimitiveValueProperty || property instanceof TypeProperty || (property instanceof ValueProperty && ((ValueProperty)property).getValue().isNull()) || (property instanceof ObjectValueProperty && "java.lang.String".equals(((ObjectValueProperty)property).getValue().getJavaType().getName()));
			Map<String, ArrayList<TypedExpression>> expressionsByType = getNonKnownCrashingExpressionByType(exprs);
			ArrayList<FullyEvaluatedExpression> validExprs = new ArrayList<FullyEvaluatedExpression>(exprs.size());
			for (Map.Entry<String, ArrayList<TypedExpression>> expressionsOfType: expressionsByType.entrySet()) {
				String type = EclipseUtils.sanitizeTypename(expressionsOfType.getKey());
				String valuesArrayName = getValuesArrayName(type);
				IJavaFieldVariable valuesField = implType.getField(valuesArrayName);
				evaluateExpressions(expressionsOfType.getValue(), validExprs, type, property, validateStatically, valuesField, 0);
			}
			return validExprs;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Splits the given expressions by their type, either object or primitives.
	 * We do this so that we can evaluate as many expressions together as possible
	 * by storing them into a shared array.  If we used an array of objects for
	 * everything, we would box primitives, which modifies them.  So we put all objects
	 * in an array of objects and primitives in arrays of their own.
	 * It also filters out expressions that we know will crash.
	 */
	private Map<String, ArrayList<TypedExpression>> getNonKnownCrashingExpressionByType(ArrayList<? extends TypedExpression> exprs) throws DebugException {
		Map<String, ArrayList<TypedExpression>> expressionsByType = new HashMap<String, ArrayList<TypedExpression>>();
		for (TypedExpression expr: exprs) {
    		if (!crashingExpressions.contains(expr.getExpression().toString())) {
				IJavaType type = expr.getType();
				String typeName = type == null ? null : EclipseUtils.isPrimitive(type) ? type.getName() : "Object";
				if (!expressionsByType.containsKey(typeName))
					expressionsByType.put(typeName, new ArrayList<TypedExpression>());
				expressionsByType.get(typeName).add(expr);
    		}
		}
		// Nulls have a null type, so put them with the objects.
		if (expressionsByType.containsKey(null)) {
			ArrayList<TypedExpression> nulls = expressionsByType.remove(null);
			if (!expressionsByType.containsKey("Object"))
				expressionsByType.put("Object", new ArrayList<TypedExpression>());
			expressionsByType.get("Object").addAll(nulls);
		}
		return expressionsByType;
	}

	/**
	 * Gets a string whose evaluation will cache the results of
	 * pre-state variables used in the given pdspec.
	 * @param stack The current stack frame.
	 * @param property The current pdspec.
	 * @return A string whose evaluation will cache the results
	 * of pre-state variables used in the given pdspec.
	 * @throws DebugException
	 */
	private static String getPreVarsString(IJavaStackFrame stack, Property property) throws DebugException {
		if (property instanceof StateProperty) {
			StringBuilder expressionsStr = new StringBuilder();
			for (String preVar: ((StateProperty)property).getPreVariables(stack))
				expressionsStr.append(stack.findVariable(preVar).getJavaType().getName()).append(" ").append(StateProperty.getRenamedVar(preVar)).append(" = ").append(preVar).append(";\n");
			return expressionsStr.toString();
		} else
			return "";
	}
	
	/**
	 * Gets the names of the array of values in CodeHintImpl
	 * to use for the given type.
	 * @param type The name of the type (either Object or a primitive).
	 * @return The name of the array to use.
	 */
	private static String getValuesArrayName(String type) {
		if ("Object".equals(type))
			return "objects";
		else
			return type + 's';
	}
	
	/**
	 * Evaluates the given expressions.
	 * @param exprs The expressions to evaluate
	 * @param validExprs The results of the evaluations of the given
	 * expressions that do not crash and satisfy the current pdspec.
	 * @param type The static type of the desired expression.
	 * @param property The pdspec.
	 * @param validateStatically Whether we can evaluate the pdspec
	 * ourselves our must have the child evaluation do it.
	 * @param valuesField The field of the proper type to store the
	 * results of the given expressions.
	 * @param startIndex The index at which to start evaluation.
	 */
	private void evaluateExpressions(ArrayList<TypedExpression> exprs, ArrayList<FullyEvaluatedExpression> validExprs, String type, Property property, boolean validateStatically, IJavaFieldVariable valuesField, int startIndex) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
		boolean hasPropertyPrecondition = propertyPreconditions.length() > 0;
		boolean arePrimitives = !"Object".equals(type);
		ArrayList<Integer> evalExprIndices = new ArrayList<Integer>();
		int numEvaluated = 0;
		StringBuilder expressionsStr = new StringBuilder();
		
		try {
			String valuesArrayName = valuesField.getName();
			
			expressionsStr.append(preVarsString);
			int i;
	    	for (i = startIndex; i < exprs.size() && numEvaluated < BATCH_SIZE; i++) {
	    		TypedExpression curTypedExpr = exprs.get(i);
	    		Expression curExpr = curTypedExpr.getExpression();
	    		String curExprStr = curExpr.toString();
	    		IJavaValue curValue = curTypedExpr.getValue();
	    		if (curValue == null || !validateStatically) {
		    		NormalPreconditionFinder pf = new NormalPreconditionFinder();
		    		curExpr.accept(pf);
		    		String preconditions = pf.getPreconditions();
		    		StringBuilder curString = new StringBuilder();
		    		// TODO: If the user has variables with the same names as the ones I introduce, this will crash....
		    		String curRHSStr = curExprStr;
		    		if (arePrimitives && curValue != null)
		    			curRHSStr = EclipseUtils.javaStringOfValue(curValue, stack);
		    		curString.append(" _$curValue = ").append(curRHSStr).append(";\n ");
		    		if (!validateStatically) {
		    			curString.append(IMPL_QUALIFIER).append("valueCount = ").append(numEvaluated + 1).append(";\n ");
			    		if (hasPropertyPrecondition)
			    			curString.append("if (" + propertyPreconditions + ") {\n ");
			    		curString.append("_$curValid = ").append(validVal).append(";\n ");
			    		curString.append(IMPL_QUALIFIER).append("valid[").append(numEvaluated).append("] = _$curValid;\n ");
		    		}
		    		curString.append(IMPL_QUALIFIER).append(valuesArrayName).append("[").append(numEvaluated).append("] = _$curValue;\n ");
		    		if (!arePrimitives) {
		    			if (!validateStatically)
		    				curString.append("if (_$curValid)\n  ");
		    			curString.append(IMPL_QUALIFIER).append("toStrings[").append(numEvaluated).append("] = ").append(getToStringGetter(curTypedExpr)).append(";\n ");
		    		}
		    		if (hasPropertyPrecondition && !validateStatically)
		    			curString.append(" }\n ");
		    		curString.append(IMPL_QUALIFIER).append("fullCount = ").append(numEvaluated + 1).append(";\n");
		    		if (preconditions.length() > 0 && curValue == null) {  // if the value is non-null, I know the execution won't crash.
		    			expressionsStr.append("if (" + preconditions + ") {\n");
			    		curString.append("}\n");
		    		} else
		    			expressionsStr.append("\n");
					expressionsStr.append(curString.toString());
					evalExprIndices.add(i);
					numEvaluated++;
	    		}
	    	}
	    	DebugException error = null;
	    	if (numEvaluated > 0) {
		    	String newTypeString = "[" + numEvaluated + "]";
	            if (type.contains("[]")) {  // If this is an array type, we must specify our new size as the first array dimension, not the last one.
	                int index = type.indexOf("[]");
	                newTypeString = type.substring(0, index) + newTypeString + type.substring(index); 
	            } else
	                newTypeString = type + newTypeString;
	            StringBuilder prefix = new StringBuilder();
	            prefix.append("{\n").append(IMPL_QUALIFIER).append(valuesArrayName).append(" = new ").append(newTypeString).append(";\n").append(IMPL_QUALIFIER).append("valid = new boolean[").append(numEvaluated).append("];\n");
	            if (!arePrimitives)
	            	prefix.append(IMPL_QUALIFIER).append("toStrings = new String[").append(numEvaluated).append("];\n");
	            else
	            	prefix.append(IMPL_QUALIFIER).append("toStrings = null;\n");
	        	prefix.append(IMPL_QUALIFIER).append("valueCount = 0;\n");
	        	prefix.append(IMPL_QUALIFIER).append("fullCount = 0;\n");
	        	prefix.append(type).append(" _$curValue;\n");
	        	if (!validateStatically)
		        	prefix.append("boolean _$curValid;\n");
				expressionsStr.insert(0, prefix.toString());
		    	expressionsStr.append("}");
		    	
		    	String finalStr = expressionsStr.toString();
		    	ICompiledExpression compiled = engine.getCompiledExpression(finalStr, stack);
		    	if (compiled.hasErrors()) {
		    		// Check the expressions one-by-one and remove those that crash.
		    		// We can crash thanks to generics and erasure (e.g., by passing an Object to List<String>.set).
		    		boolean deleted = false;
		    		for (int j = i - 1; j >= startIndex; j--) {
		    			if (engine.getCompiledExpression(exprs.get(j).getExpression().toString(), stack).hasErrors()) {
		    				exprs.remove(j);
		    				deleted = true;
		    			}
		    		}
		    		if (!deleted)  // In this case, the error is probably our fault and not due to erasure.
		    			throw new EvaluationError("Evaluation error: " + "The following errors were encountered during evaluation.\n\n" + EclipseUtils.getCompileErrors(compiled));
		    		evaluateExpressions(exprs, validExprs, type, property, validateStatically, valuesField, startIndex);
		    		return;
		    	}
		    	IEvaluationResult result = Evaluator.evaluateExpression(compiled, engine, stack);
		    	error = result.getException();
	    	}

	    	int work = i - startIndex;
	    	int numToSkip = 0;
	    	if (error != null) {
				int fullCount = ((IJavaPrimitiveValue)fullCountField.getValue()).getIntValue();
				int valueCount = ((IJavaPrimitiveValue)valueCountField.getValue()).getIntValue();
	    		int crashingIndex = evalExprIndices.get(fullCount);
	    		Expression crashedExpr = exprs.get(crashingIndex).getExpression();
				if (valueCount == fullCount || validateStatically)
					crashingExpressions.add(crashedExpr.toString());
	    		work = crashingIndex - startIndex;
	    		numToSkip = skipLikelyCrashes(exprs, error, crashingIndex, crashedExpr);
	    	}
	    	if (work > 0) {
	    		ArrayList<FullyEvaluatedExpression> newResults = getResultsFromArray(exprs, property, valuesField, startIndex, work, numEvaluated);
		    	reportResults(newResults);
		    	validExprs.addAll(newResults);
	    	}
	    	/*System.out.println("Evaluated " + count + " expressions.");
	    	if (hasError)
	    		System.out.println("Crashed on " + exprs.get(startIndex + count));*/
	    	monitor.worked(work);
	    	int nextStartIndex = startIndex + work + numToSkip;
	    	if (nextStartIndex < exprs.size())
	    		evaluateExpressions(exprs, validExprs, type, property, validateStatically, valuesField, nextStartIndex);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Gets a string whose evaluation will get a String
	 * representation of the result of the given expression.
	 * @param expr The expression of whose result we want
	 * a string representation.
	 * @return A String representation of the value of the
	 * given expression.
	 * @throws DebugException 
	 */
	private static String getToStringGetter(TypedExpression expr) throws DebugException {
		String nullCheck = "_$curValue == null ? \"null\" : ";
		if (expr.getType() instanceof IJavaArrayType)
			return nullCheck + "java.util.Arrays.toString((" + EclipseUtils.sanitizeTypename(expr.getType().getName()) + ")_$curValue)";
		else
			return nullCheck + "_$curValue.toString()";
	}
	
	/**
	 * Gets the fully evaluated expressions of those expressions
	 * whose execution did not crash and that produce a
	 * valid result.  See evaluateExpressions for the
	 * string on whose evaluation this is called.
	 * @param exprs The expressions that were evaluated.
	 * @param valuesField The field of the proper type to store the
	 * results of the given expressions.
	 * @param count The number of expressions that were successfully
	 * evaluated.
	 * @return a list of expressions containing those that satisfy the
	 * given property (or all that do not crash if it is null).
	 * @throws DebugException
	 */
	private ArrayList<FullyEvaluatedExpression> getResultsFromArray(ArrayList<TypedExpression> exprs, Property property, IJavaFieldVariable valuesField, int startIndex, int count, int numEvaluated) throws DebugException {
		ArrayList<FullyEvaluatedExpression> validExprs = new ArrayList<FullyEvaluatedExpression>();
		IJavaValue valuesFieldValue = (IJavaValue)valuesField.getValue();
		IJavaValue[] values = numEvaluated == 0 || valuesFieldValue.isNull() ? null : ((IJavaArray)valuesFieldValue).getValues();
		IJavaValue validFieldValue = (IJavaValue)validField.getValue();
		IJavaValue[] valids = numEvaluated == 0 || validFieldValue.isNull() ? null : ((IJavaArray)validFieldValue).getValues();
		IJavaValue toStringsFieldValue = (IJavaValue)toStringsField.getValue();
		IJavaValue[] toStrings = numEvaluated == 0 || toStringsFieldValue.isNull() ? null : ((IJavaArray)toStringsFieldValue).getValues();
		int evalIndex = 0;
		for (int i = 0; i < count; i++) {
			TypedExpression typedExpr = exprs.get(startIndex + i);
			IJavaValue curValue = typedExpr.getValue() != null ? typedExpr.getValue() : values[evalIndex];
			boolean valid = false;
			String resultString = null;
			if (property == null) {
				valid = true;
				resultString = getResultString(curValue, toStrings, evalIndex);
			} else if (property instanceof PrimitiveValueProperty) {
				valid = curValue.toString().equals(((PrimitiveValueProperty)property).getValue().toString());
				resultString = EclipseUtils.javaStringOfValue(curValue, stack);
    		} else if (property instanceof TypeProperty) {
				valid = !curValue.isNull() && subtypeChecker.isSubtypeOf(curValue.getJavaType(), ((TypeProperty)property).getType());  // null is not instanceof Object
				resultString = EclipseUtils.javaStringOfValue(curValue, stack);
    		} else if (property instanceof ValueProperty && ((ValueProperty)property).getValue().isNull()) {
    			valid = curValue.isNull();
    			resultString = "null";
    		} else if (property instanceof ObjectValueProperty && "java.lang.String".equals(((ObjectValueProperty)property).getValue().getJavaType().getName())) {  // The property's value cannot be null because of the previous special case.
    			valid = ((ObjectValueProperty)property).getValue().toString().equals(curValue.toString());
    			resultString = EclipseUtils.javaStringOfValue(curValue, stack);
    		} else {
    			valid = "true".equals(valids[evalIndex].toString());
				resultString = getResultString(curValue, toStrings, evalIndex);
    		}
			if (valid)
				validExprs.add(new FullyEvaluatedExpression(typedExpr.getExpression(), typedExpr.getType(), new Value(curValue), resultString));
			if (typedExpr.getValue() == null || !(property instanceof PrimitiveValueProperty || property instanceof TypeProperty))
				evalIndex++;
		}
		return validExprs;
	}
	
	/**
	 * Gets a String representing the given value.  We use the
	 * given array of computed toStrings if it is non-null,
	 * and we enclose Strings in quotes when necessary.
	 * @param curValue The value whose String representation we want.
	 * @param toStrings An array of the computed toStrings of value,
	 * or null if we did not compute anything.
	 * @param evalIndex The index of the current value in the toStrings
	 * array if it is non-null.
	 * @return A String representation of the given value.
	 * @throws DebugException
	 */
	private String getResultString(IJavaValue curValue, IJavaValue[] toStrings, int evalIndex) throws DebugException {
		if (toStrings == null)
			return EclipseUtils.javaStringOfValue(curValue, stack);
		else {
			String result = toStrings[evalIndex].getValueString();
			if (!curValue.isNull() && "java.lang.String".equals(curValue.getJavaType().getName()))
				result = "\"" + result + "\"";
			return result;
		}
	}

	/**
	 * Records the given expressions in the synthesis dialog,
	 * or does nothing if there is no dialog.
	 * @param results The expressions to record.
	 */
	private void reportResults(final ArrayList<FullyEvaluatedExpression> results) {
		if (synthesisDialog != null && !results.isEmpty()) {
			final InitialSynthesisDialog dialog = synthesisDialog;  // We make a copy of the reference to avoid a race condition if someone later does an evaluation where it is null.
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					dialog.addExpressions(results);
				}
			});
		}
	}

	/**
	 * Heuristically skips expressions that are similar to an
	 * expression that just crashed and thus are likely to
	 * crash themselves.
	 * @param exprs All of the expressions being evaluated.
	 * @param error The exception thrown by the execution of
	 * the current expression.
	 * @param crashingIndex The index of the crashing expression.
	 * @param crashedExpr The expression whose execution crashed,
	 * @return The number of expressions starting from crashingIndex
	 * that should be skipped.  This includes the expression that
	 * crashed and so will always be at least one.
	 */
	private static int skipLikelyCrashes(ArrayList<TypedExpression> exprs, DebugException error, int crashingIndex, Expression crashedExpr) {
		//System.out.println("Evaluation of " + crashedExpr.toString() + " crashed with message: " + EclipseUtils.getExceptionMessage(error) + ".");
		int numToSkip = 1;
		Method crashedMethod = ExpressionMaker.getMethod(crashedExpr);
		String errorName = EclipseUtils.getExceptionName(error);
		int numNulls = getNumNulls(crashedExpr);
		// Only skip method calls that we think will throw a NPE and that have at least one subexpression we know is null.
		if (crashedMethod != null && "java.lang.NullPointerException".equals(errorName) && numNulls > 0) {
			while (crashingIndex + numToSkip < exprs.size()) {
				Expression newExpr = exprs.get(crashingIndex + numToSkip).getExpression();
				// Skip calls to the same method with at least as much known nulls.
				if (crashedMethod.equals(ExpressionMaker.getMethod(newExpr)) && getNumNulls(newExpr) >= numNulls) {
					//System.out.println("Skipping " + newExpr.toString() + ".");
					numToSkip++;
				} else
					break;
			}
		}
		return numToSkip;
	}
    
    /**
     * Finds the number of subexpressions of the
     * given expression that are known to be null.
     * @param expr The expression to search.
     * @return The number of subexpressions of the
     * given expression known to be null.
     */
    private static int getNumNulls(Expression expr) {
    	final int[] numNulls = new int[] { 0 };
    	expr.accept(new ASTVisitor() {
    		@Override
    		public void postVisit(ASTNode node) {
    			if (node instanceof Expression) {
    				if (node instanceof NullLiteral)
    					numNulls[0]++;
    				else {
    					IJavaValue value = ExpressionMaker.getExpressionValue((Expression)node);
    					if (value != null && value.isNull())
        					numNulls[0]++;
    				}
    			}
    		}
    	});
    	return numNulls[0];
    }
	
	/**
	 * Nulls out the CodeHintImpl fields used during evaluation
	 * to free up memory.
	 */
	public void resetFields() {
		try {
			EclipseUtils.evaluate(IMPL_QUALIFIER + "reset()", stack);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static class Evaluator {
		
		private static final Semaphore semaphore = new Semaphore(0);
		private static final IEvaluationResult[] results = new IEvaluationResult[] { null };
		private static final EvaluationListener evaluationListener = new EvaluationListener(results, semaphore);
	
		/**
		 * Evaluates the given expression synchronously.
		 * @param compiled The compiled expression to evaluate.
		 * @param engine The evaluation engine.
		 * @param stack The current stack frame.
		 * @return The result of the evaluation.
		 */
	    public static IEvaluationResult evaluateExpression(ICompiledExpression compiled, IAstEvaluationEngine engine, IJavaStackFrame stack) {
			try {
				engine.evaluateExpression(compiled, stack, evaluationListener, DebugEvent.EVALUATION_IMPLICIT, false);
				semaphore.acquire();
				return results[0];
			} catch (InterruptedException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Thread interrupted.");
			} catch (DebugException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Debug Exception.");
			}
	    }
	    
	    /**
	     * Class to wait for evaluations to finish.
	     */
	    private static class EvaluationListener implements IEvaluationListener {
	
	    	private final IEvaluationResult[] results;
	    	private final Semaphore semaphore;
	    	
	    	public EvaluationListener(IEvaluationResult[] results, Semaphore semaphore) {
	    		this.results = results;
	    		this.semaphore = semaphore;
	    	}
	
			@Override
			public void evaluationComplete(IEvaluationResult result) {
				results[0] = result;
				semaphore.release();  // Up the semaphore to mark that the evaluation finished.
			}
	    	
	    }
	    
	}
    
    /**
     * Finds the preconditions under which we can evaluate the
     * given expression without it crashing, or notes that we
     * can never be sure of this.
     */
    private static class PreconditionFinder extends ASTVisitor {
    	
    	private String preconditions;
    	
    	public PreconditionFinder() {
    		preconditions = "";
    	}
    	
    	protected void add(String str) {
    		if (preconditions.length() == 0)
    			preconditions = str;
    		else
    			preconditions = str + " && " + preconditions;
    	}
    	
    	/**
    	 * Finds the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 * @return the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 */
    	public String getPreconditions() {
    		return preconditions;
    	}
    	
    }
    
    /**
     * Finds the preconditions under which we can evaluate the
     * given expression without it crashing, or notes that we
     * can never be sure of this.
     */
    private static class NormalPreconditionFinder extends PreconditionFinder {
    	
    	//private boolean canThrowException;
    	
    	public NormalPreconditionFinder() {
    		//canThrowException = false;
    	}
    	
    	// a[i] -> a != null && i >= 0 && i < a.length 
    	@Override
    	public boolean visit(ArrayAccess node) {
    		String arrStr = node.getArray().toString();
    		String indexStr = node.getIndex().toString();
    		add("(" + arrStr + " != null && " + indexStr + " >= 0 && " + indexStr + " < " + arrStr + ".length)");
    		return true;
    	}
    	
    	// p.q -> p != null
    	@Override
    	public boolean visit(FieldAccess node) {
    		/*if (node.getExpression().getProperty("isStatic") == null)  // Ignore static field accesses like Test.foo.  But this won't work since at refinement time we don't have this information.
    			add(node.getExpression() + " != null");*/
    		if ((IJavaValue)node.getProperty("value") != null)
    			return true;  // The node can be evaluated safely because we already computed its value (so it must have one).
    		//canThrowException = true;
    		IJavaValue exprValue = (IJavaValue)node.getExpression().getProperty("value");
    		if (exprValue != null && exprValue.isNull())
    			add("false");  // We know the expression is null and this will crash.
    		return true;
    	}
    	
    	// x/y -> y != 0
    	// x%y -> y != 0
    	@Override
    	public boolean visit(InfixExpression node) {
    		if (node.getOperator() == InfixExpression.Operator.DIVIDE || node.getOperator() == InfixExpression.Operator.REMAINDER)
    			add(node.getRightOperand() + " != 0");
    		return true;
    	}
    	
    	// x.foo() -> x != null
    	@Override
    	public boolean visit(MethodInvocation node) {
    		/*if (node.getExpression() != null && node.getExpression().getProperty("isStatic") == null && !(node.getExpression() instanceof QualifiedName))  // If the expression is a Qualified name, it is a package name and not a variable.  But this won't work since at refinement time we don't have this information.
    			add(node.getExpression() + " != null");*/
    		if ((IJavaValue)node.getProperty("value") != null)
    			return true;  // The node can be evaluated safely because we already computed its value (so it must have one).
    		//canThrowException = true;
    		IJavaValue exprValue = node.getExpression() == null ? null : (IJavaValue)node.getExpression().getProperty("value");
    		if (exprValue != null && exprValue.isNull())
    			add("false");  // We know the expression is null and this will crash.
    		return true;
    	}
    	
    	// Constructor calls can always throw and we can't (currently) statically evaluate them.
    	@Override
		public boolean visit(ClassInstanceCreation node) {
    		//canThrowException = true;
    		return true;
    	}

    	// We don't want to visit the body of the conditional expression, since one will not be taken.
    	// TODO: We would visit the condition, though.
    	@Override
    	public boolean visit(ConditionalExpression node) {
    		return false;
    	}
    	
    	/**
    	 * Checks whether this expression can throw an exception
    	 * even if the precondition is true.
    	 * @return whether this expression can throw an exception
    	 * even if the precondition is true.
    	 */
    	/*public boolean canThrowException() {
    		return canThrowException;
    	}*/
    	
    }

    // TODO: This doesn't work for lambda properties with types, since they always insert a cast.
    /**
     * Gets a simple precondition for a pdspec.
     * It ensures that we do not evaluate the pdspecs
     * for some pdspecs that we know will crash.
     */
    private static class PropertyPreconditionFinder extends PreconditionFinder {
    	
    	@Override
    	public boolean visit(FieldAccess node) {
    		checkForSyntheticVar(node.getExpression());
    		return true;
    	}
    	
    	@Override
    	public boolean visit(MethodInvocation node) {
    		checkForSyntheticVar(node.getExpression());
    		return true;
    	}
    	
    	/**
    	 * If the given node is a synthetic variable,
    	 * we ensure that it is not null before
    	 * evaluating the pdspec. 
    	 * @param node The node to check.
    	 */
    	private void checkForSyntheticVar(Expression node) {
    		if (node instanceof SimpleName) {
    			String name = ((SimpleName)node).getIdentifier();
    			if (name.startsWith("_$"))
    				add(name + " != null");
    		}
    	}
    	
    }

}