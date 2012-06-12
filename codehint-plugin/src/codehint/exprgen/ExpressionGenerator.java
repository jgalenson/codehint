package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIType;

import codehint.EclipseUtils;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.property.Property;
import codehint.property.ValueProperty;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;


/**
 * Class for generating expressions that can evaluation to a given value.
 */
public class ExpressionGenerator {
	
	private final static AST ast = AST.newAST(AST.JLS4);
	
	private final static InfixExpression.Operator[] INT_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.LESS, InfixExpression.Operator.LESS_EQUALS, InfixExpression.Operator.GREATER, InfixExpression.Operator.GREATER_EQUALS };
	private final static InfixExpression.Operator[] BOOLEAN_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_OR  };
	private final static InfixExpression.Operator[] REF_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS };

	private final static Set<String> classBlacklist = new HashSet<String>();
	private final static Map<String, Set<String>> methodBlacklist = new HashMap<String, Set<String>>();
	
	public static void initBlacklist() {
		classBlacklist.add("codehint.CodeHint");
		methodBlacklist.put("java.io.File", new HashSet<String>(Arrays.asList("createNewFile", "delete", "mkdir", "mkdirs", "renameTo", "setLastModified", "setReadOnly")));
	}
	
	public static void clearBlacklist() {
		classBlacklist.clear();
		methodBlacklist.clear();
	}
	
	/**
	 * Generates all the expressions (up to a certain depth) whose value
	 * in the current stack frame is that of the demonstration.
	 * @param target The debug target.
	 * @param stack The current stack frame.
	 * @param demonstration The value entered by the user.
	 * @param property The property entered by the user.
	 * @param typeConstraint The constraint on the type of the expressions
	 * being generated.
	 * @param subtypeChecker The subtype checker.
	 * @param monitor Progress monitor.
	 * @param maxExprDepth The maximum depth of expressions to search.
	 * @return A list containing strings of all the expressions (up
	 * to a certain depth) whose value in the current stack frame is
	 * that of the demonstration.
	 */
	public static ArrayList<EvaluatedExpression> generateExpression(IJavaDebugTarget target, IJavaStackFrame stack, Property property, TypeConstraint typeConstraint, SubtypeChecker subtypeChecker, IProgressMonitor monitor, int maxExprDepth) {
		monitor.beginTask("Expression generation and evaluation", IProgressMonitor.UNKNOWN);
		
		try {
			IJavaThread thread = (IJavaThread)stack.getThread();
			Map<IJavaValue, Set<EvaluatedExpression>> equivalences = new HashMap<IJavaValue, Set<EvaluatedExpression>>();
			IJavaValue demonstration = property instanceof ValueProperty ? ((ValueProperty)property).getValue() : null;
	
			long startTime = System.currentTimeMillis();
			
			List<TypedExpression> allTypedExprs = genAllExprs(target, demonstration, typeConstraint, stack, thread, subtypeChecker, equivalences, 0, maxExprDepth, monitor);
			
			/*for (Map.Entry<IJavaValue, Set<EvaluatedExpression>> entry : equivalences.entrySet())
				System.out.println(entry.getKey() + " -> " + entry.getValue().toString());*/
			 
			ArrayList<EvaluatedExpression> evaluatedExprs = new ArrayList<EvaluatedExpression>();
			ArrayList<TypedExpression> unevaluatedExprs = new ArrayList<TypedExpression>();
	    	for (TypedExpression e : allTypedExprs)
	    		if (e.getValue() == null)
	    			unevaluatedExprs.add(e);
	    		else if (!"V".equals(e.getValue().getSignature()))
	    			evaluatedExprs.add(new EvaluatedExpression(e.getExpression(), e.getValue(), e.getType()));
	    	
	    	EclipseUtils.log("Generated " + allTypedExprs.size() + " potential expressions, of which " + evaluatedExprs.size() + " already have values and " + unevaluatedExprs.size() + " still need to be evaluated.");
			
	    	ArrayList<EvaluatedExpression> results = EvaluationManager.filterExpressions(evaluatedExprs, stack, property);
	    	if (unevaluatedExprs.size() > 0) {
	    		SubMonitor evalMonitor = SubMonitor.convert(monitor, "Expression evaluation", unevaluatedExprs.size());
	    		results.addAll(EvaluationManager.evaluateExpressions(unevaluatedExprs, stack, property, evalMonitor));
	    	}
			for (EvaluatedExpression e : results)
				setExpressionValue(e.getExpression(), e.getResult());
	    	//List<EvaluatedExpression> allEvaluatedExprs = expandEquivalences(evaluatedExprs, equivalences);
			
			EclipseUtils.log("Expression generation found " + results.size() + " valid expressions and took " + (System.currentTimeMillis() - startTime) + " milliseconds.");
			
	    	monitor.done();
	    	return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Recursively generates all expressions whose value in the
	 * current stack frame is that of the demonstration.
	 * @param target The debug target.
	 * @param demonstration The value entered by the user.
	 * @param typeBound An upper bound on the type of the desired value.
	 * @param stack The current stack frame.
	 * @param thread The calling thread.
	 * @param supertypesMap Cached supertype information.
	 * @param depth The current depth, counting up from 0.
	 * @param maxDepth The maximum depth to search (inclusive).
	 * @param monitor Progress monitor.
	 * @return all expressions whose value in the
	 * current stack frame is that of the demonstration.
	 */
	private static List<TypedExpression> genAllExprs(IJavaDebugTarget target, IJavaValue demonstration, TypeConstraint typeConstraint, IJavaStackFrame stack, IJavaThread thread, SubtypeChecker subtypeChecker, Map<IJavaValue, Set<EvaluatedExpression>> equivalences, int depth, int maxDepth, IProgressMonitor monitor) {
		if (depth > maxDepth)
			return new ArrayList<TypedExpression>(0);
		try {
			IJavaType thisType = stack.getReferenceType();
			IJavaType intType = EclipseUtils.getFullyQualifiedType("int", target);
			IJavaType booleanType = EclipseUtils.getFullyQualifiedType("boolean", target);
			IJavaValue zero = makeIntValue(target, 0);
			IJavaValue one = makeIntValue(target, 1);
			IJavaValue two = makeIntValue(target, 2);
			
    		List<TypedExpression> nextLevel = genAllExprs(target, demonstration, typeConstraint, stack, thread, subtypeChecker, equivalences, depth + 1, maxDepth, monitor);
    		List<TypedExpression> curLevel = new ArrayList<TypedExpression>();
			Set<IJavaType> objectInterfaceTypes = new HashSet<IJavaType>();
			IJavaType[] constraintTypes = typeConstraint.getTypes(target);
    		
    		// Get constants (but only at the top-level).
    		if (depth == 0 && demonstration != null && isInt(demonstration.getJavaType()) && !"0".equals(demonstration.toString()))
    			addUniqueExpressionToList(curLevel, makeNumber(demonstration.toString(), makeIntValue(target, Integer.parseInt(demonstration.toString())), intType), depth, maxDepth, equivalences);
    		if (depth == 0 && demonstration != null && isBoolean(demonstration.getJavaType()))
    			addUniqueExpressionToList(curLevel, makeBoolean(Boolean.parseBoolean(demonstration.toString()), makeBooleanValue(target, Boolean.parseBoolean(demonstration.toString())), booleanType), depth, maxDepth, equivalences);
    		// Add calls to the desired type's constructors (but only at the top-level).
    		if (depth == 0)
    			for (IJavaType type: constraintTypes)
    				if (type instanceof IJavaClassType)
    					addMethodCalls(new TypedExpression(null, type, null), nextLevel, typeConstraint, curLevel, thisType, thread, target, stack, subtypeChecker, equivalences, depth, maxDepth);
    		// Add zero and null (but only at the bottom level)
    		if (depth == maxDepth) {
    			boolean hasInt = false;
    			boolean hasObject = false;
    			for (IJavaType type: constraintTypes) {
    				if (isInt(type))
    					hasInt = true;
    				else if (EclipseUtils.isObject(type.getSignature()))
    					hasObject = true;
    			}
    			if (depth > 0 || hasInt)
    				addUniqueExpressionToList(curLevel, makeNumber("0", zero, intType), depth, maxDepth, equivalences);
    			if (depth > 0 || hasObject)
    				addUniqueExpressionToList(curLevel, makeNull(target), depth, maxDepth, equivalences);
    		}
    		
    		// Copy over the stuff from the next level.
    		for (TypedExpression e : nextLevel)
    			if (depth > 0 || isHelpfulType(e.getType(), typeConstraint, subtypeChecker, target, depth))  // Note that this relies on the fact that something helpful for depth>=2 will be helpful for depth>=1.  If this changes, we'll need to call it again.
    				curLevel.add(e);
    		
    		if (nextLevel.isEmpty()) {
	    		// Get variables of helpful types.
				IJavaVariable[] locals = stack.getLocalVariables();
				for (IJavaVariable l : locals) {
					IJavaType lType = l.getJavaType();
					if (isHelpfulType(lType, typeConstraint, subtypeChecker, target, depth))
						addUniqueExpressionToList(curLevel, makeVar(l.getName(), (IJavaValue)l.getValue(), lType, false), depth, maxDepth, equivalences);
				}
				// Add "this" if we're not in a static context.
				if (isHelpfulType(thisType, typeConstraint, subtypeChecker, target, depth)
						&& !stack.isStatic())
					addUniqueExpressionToList(curLevel, makeThis(stack.getThis(), thisType), depth, maxDepth, equivalences);
    		} else {
				// Get binary ops.
				// We use string comparisons to avoid duplicates, e.g., x+y and y+x.
	    		for (TypedExpression l : nextLevel) {
	    			if (monitor.isCanceled())
	    				throw new OperationCanceledException();
	        		for (TypedExpression r : nextLevel) {
	        			// Arithmetic operations, e.g., +,*.
						if (isInt(l.getType()) && isInt(r.getType()) && isHelpfulType(intType, typeConstraint, subtypeChecker, target, depth)
								&& l.getExpression().getProperty("isConstant") == null && r.getExpression().getProperty("isConstant") == null) {
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
								addUniqueExpressionToList(curLevel, makeInfix(target, l, InfixExpression.Operator.PLUS, r, intType), depth, maxDepth, equivalences);
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) <= 0)
								addUniqueExpressionToList(curLevel, makeInfix(target, l, InfixExpression.Operator.TIMES, r, intType), depth, maxDepth, equivalences);
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) != 0)
								addUniqueExpressionToList(curLevel, makeInfix(target, l, InfixExpression.Operator.MINUS, r, intType), depth, maxDepth, equivalences);
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) != 0
									&& (r.getValue() == null || !r.getValue().getValueString().equals("0")))  // Don't divide by things we know are 0.
								addUniqueExpressionToList(curLevel, makeInfix(target, l, InfixExpression.Operator.DIVIDE, r, intType), depth, maxDepth, equivalences);
						}
						// Integer comparisons, e.g., ==,<.
						if (isHelpfulType(booleanType, typeConstraint, subtypeChecker, target, depth) && isInt(l.getType()) && isInt(r.getType()))
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0
									&& (!(l.getExpression() instanceof PrefixExpression) || !(r.getExpression() instanceof PrefixExpression)))
								for (InfixExpression.Operator op : INT_COMPARE_OPS)
									addUniqueExpressionToList(curLevel, makeInfix(target, l, op, r, booleanType), depth, maxDepth, equivalences);
						// Boolean connectives, &&,||.
						if (isHelpfulType(booleanType, typeConstraint, subtypeChecker, target, depth) && isBoolean(l.getType()) && isBoolean(r.getType()))
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
								for (InfixExpression.Operator op : BOOLEAN_COMPARE_OPS)
									addUniqueExpressionToList(curLevel, makeInfix(target, l, op, r, booleanType), depth, maxDepth, equivalences);
						// Array access, a[i].
						if (l.getType() instanceof IJavaArrayType && isInt(r.getType())) {
							IJavaType elemType = getArrayElementType(l);
							if (elemType != null && isHelpfulType(getArrayElementType(l), typeConstraint, subtypeChecker, target, depth)) {
								// Get the value if we can and skip things with null arrays or out-of-bounds indices.
								boolean isNull = false;
								IJavaValue value = null;
								if (r.getValue() != null) {
									int index = Integer.parseInt(r.getValue().getValueString());
									if (index < 0)
										isNull = true;
									else if (l.getValue() != null) {
										if (l.getValue().isNull())
											isNull = true;
										else {
											IJavaArray array = (IJavaArray)l.getValue();
											if (array.isNull() || index >= array.getLength())
												isNull = true;
											else
												value = array.getValue(index);
										}
									}
								}
								if (!isNull)
									addUniqueExpressionToList(curLevel, makeArrayAccess(l, r, value), depth, maxDepth, equivalences);
							}
						}
						// Object/array comparisons
						if (l.getType() instanceof IJavaReferenceType && r.getType() instanceof IJavaReferenceType
								&& isHelpfulType(booleanType, typeConstraint, subtypeChecker, target, depth)
								&& (subtypeChecker.isSubtypeOf(l.getType(), r.getType()) || subtypeChecker.isSubtypeOf(r.getType(), l.getType())))
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
								for (InfixExpression.Operator op : REF_COMPARE_OPS)
									addUniqueExpressionToList(curLevel, makeInfix(target, l, op, r, booleanType), depth, maxDepth, equivalences);
							
	        		}
	    		}
	    		// Get unary ops
	    		for (TypedExpression e : nextLevel) {
	    			// Arithmetic with constants.
	    			if (isInt(e.getType()) && isHelpfulType(intType, typeConstraint, subtypeChecker, target, depth)
	    					&& e.getExpression().getProperty("isConstant") == null) {
	    				addUniqueExpressionToList(curLevel, makeInfix(target, e, InfixExpression.Operator.PLUS, makeNumber("1", one, intType), intType), depth, maxDepth, equivalences);
	    				addUniqueExpressionToList(curLevel, makeInfix(target, e, InfixExpression.Operator.TIMES, makeNumber("2", two, intType), intType), depth, maxDepth, equivalences);
	    				addUniqueExpressionToList(curLevel, makeInfix(target, e, InfixExpression.Operator.MINUS, makeNumber("1", one, intType), intType), depth, maxDepth, equivalences);
	    				addUniqueExpressionToList(curLevel, makeInfix(target, e, InfixExpression.Operator.DIVIDE, makeNumber("2", two, intType), intType), depth, maxDepth, equivalences);
	    			}
	    			// Field accesses to non-static fields from non-static scope.
	    			if (e.getType() instanceof IJavaClassType
	    					&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
	    				addFieldAccesses(e, typeConstraint, curLevel, thisType, target, subtypeChecker, equivalences, depth, maxDepth);
	    			// Boolean negation.
	    			if (isBoolean(e.getType()) && isHelpfulType(booleanType, typeConstraint, subtypeChecker, target, depth)
	    					&& !(e.getExpression() instanceof PrefixExpression) && !(e.getExpression() instanceof InfixExpression)
	    					&& e.getExpression().getProperty("isConstant") == null)  // Disallow things like !(x < y) and !(!x).
	    				addUniqueExpressionToList(curLevel, makePrefix(target, e, PrefixExpression.Operator.NOT, booleanType), depth, maxDepth, equivalences);
	    			// Integer negation.
	    			if (isInt(e.getType()) && isHelpfulType(intType, typeConstraint, subtypeChecker, target, depth)
	    					&& !(e.getExpression() instanceof PrefixExpression) && !(e.getExpression() instanceof InfixExpression)
	    					&& e.getExpression().getProperty("isConstant") == null)  // Disallow things like -(-x) and -(x + y).
	    				addUniqueExpressionToList(curLevel, makePrefix(target, e, PrefixExpression.Operator.MINUS, intType), depth, maxDepth, equivalences);
	    			// Array length (which uses the field access AST).
	    			if (e.getType() instanceof IJavaArrayType && isHelpfulType(intType, typeConstraint, subtypeChecker, target, depth)
	    					&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
	    				addUniqueExpressionToList(curLevel, makeFieldAccess(e, "length", intType, e.getValue() != null ? makeIntValue(target, ((IJavaArray)e.getValue()).getLength()) : null), depth, maxDepth, equivalences);
	    			// Method calls to non-static methods from non-static scope.
	    			if (isObjectOrInterface(e.getType())
	    					&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
	    				addMethodCalls(e, nextLevel, typeConstraint, curLevel, thisType, thread, target, stack, subtypeChecker, equivalences, depth, maxDepth);
	    			// Collect the class and interface types we've seen.
	    			if (isObjectOrInterface(e.getType()))
	    				objectInterfaceTypes.add(e.getType());
	    		}
	    		// Extra things
	    		{
	    			IImportDeclaration[] imports = ((ICompilationUnit)EclipseUtils.getProject(stack).findElement(new Path(stack.getSourcePath()))).getImports();
	    			Set<String> importsSet = new HashSet<String>(imports.length);
	    			for (IImportDeclaration imp : imports)
	    				importsSet.add(imp.getElementName());
	    			// Field accesses from static scope.
	    			if (stack.isStatic() && !stack.getReceivingTypeName().contains("<"))  // TODO: Allow referring to generic classes (and below).
	    				addFieldAccesses(makeStaticName(stack.getReceivingTypeName(), thisType), typeConstraint, curLevel, thisType, target, subtypeChecker, equivalences, depth, maxDepth);
	    			// Method calls from static scope.
	    			if (stack.isStatic() && !stack.getReceivingTypeName().contains("<"))
	    				addMethodCalls(makeStaticName(stack.getReceivingTypeName(), thisType), nextLevel, typeConstraint, curLevel, thisType, thread, target, stack, subtypeChecker, equivalences, depth, maxDepth);
	    			// Accesses/calls to static fields/methods.
	    			for (IJavaType type : objectInterfaceTypes) {
	    				String typeName = type.getName();
	    				// If we have imported the type or it is an inner class of the this type, use the unqualified typename for brevity.
	    				if (importsSet.contains(typeName) || (typeName.contains("$") && thisType.getName().equals(typeName.substring(0, typeName.lastIndexOf('$')))))
	    					typeName = EclipseUtils.getUnqualifiedName(EclipseUtils.sanitizeTypename(typeName));
	    				addFieldAccesses(makeStaticName(typeName, type), typeConstraint, curLevel, thisType, target, subtypeChecker, equivalences, depth, maxDepth);
	    				addMethodCalls(makeStaticName(typeName, type), nextLevel, typeConstraint, curLevel, thisType, thread, target, stack, subtypeChecker, equivalences, depth, maxDepth);
	    			}
	    			// Calls to static methods and fields of imported classes.
					for (IImportDeclaration imp : imports) {
						String fullName = imp.getElementName();
						String shortName = EclipseUtils.getUnqualifiedName(fullName);  // Use the unqualified typename for brevity.
						if (!imp.isOnDemand()) {  // TODO: What should we do with import *s?  It might be too expensive to try all static methods.  This ignores them.
							IJavaType[] importedTypes = target.getJavaTypes(fullName);
							if (importedTypes != null) {
								if (!objectInterfaceTypes.contains(importedTypes[0])) {  // We've already handled these above.
									addFieldAccesses(makeStaticName(shortName, importedTypes[0]), typeConstraint, curLevel, thisType, target, subtypeChecker, equivalences, depth, maxDepth);
									addMethodCalls(makeStaticName(shortName, importedTypes[0]), nextLevel, typeConstraint, curLevel, thisType, thread, target, stack, subtypeChecker, equivalences, depth, maxDepth);
								}
							} else  // TODO: Handle this case, where the class is not yet loaded in the child VM.  I can either try to get the VM to load the class or get the IType from EclipseUtils.getProject(stack).findType(name) and use that interface instead.
								;//System.err.println("I cannot get the class of the import " + fullName);
						}
					}
	    		}
    		}
    		/*System.out.println("Exploring " + result.size() + " possible expressions.");
    		for (TypedExpression e : result)
    			System.out.println(e.toString());*/
    		return curLevel;
		} catch (DebugException e) {
			e.printStackTrace();
        	EclipseUtils.showError("Error", "An error occurred during expression generation.", e);
			throw new RuntimeException("I cannot compute all valid expressions.");
		} catch (JavaModelException ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex.getMessage());
		}
		
	}
	
	// TODO: Convert field/method code to use the public API?  I can use IType to get fields/methods (but they only get declared ones, so I'd have to walk the supertype chain), IType to get their signature, Signature.getSignature{Qualifier,SimpleName} to get type names, and then EclipseUtils.getType-like code to get the IType back.
	
	public static List<Field> getFields(IJavaType type) {
		try {
			if (type != null && EclipseUtils.isObject(type.getSignature())) {
				List<?> untypedFields = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleFields();
				ArrayList<Field> fields = new ArrayList<Field>(untypedFields.size());
				for (Object o: untypedFields)
					fields.add((Field)o);
				return fields;
			} else
				return new ArrayList<Field>(0);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static void addFieldAccesses(TypedExpression e, TypeConstraint typeConstraint, List<TypedExpression> ops, IJavaType thisType, IJavaDebugTarget target, SubtypeChecker subtypeChecker, Map<IJavaValue, Set<EvaluatedExpression>> equivalences, int depth, int maxDepth) throws DebugException {
		// We could use the public Eclipse API here, but it isn't as clean and works on objects not types, so wouldn't work with our static accesses, which we give a null value.  Note that as below with methods, we must now be careful converting between jdi types and Eclipse types. 
		IJavaObject obj = e.getValue() != null ? (IJavaObject)e.getValue() : null;
		Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		Type thisTypeImpl = ((JDIType)thisType).getUnderlyingType();
		boolean isStatic = e.getExpression().getProperty("isStatic") != null;
		String objTypeName = isStatic ? e.getExpression().toString() : objTypeImpl.name();
		for (Field field: getFields(e.getType())) {
			if ((!field.isPublic() && !field.declaringType().equals(thisTypeImpl)) || (isStatic != field.isStatic()) || field.isSynthetic())
				continue;
			IJavaType fieldType = EclipseUtils.getFullyQualifiedTypeIfExists(field.typeName(), target);
			/*if (fieldType == null)
				System.err.println("I cannot get the class of " + objTypeImpl.name() + "." + field.name() + "(" + field.typeName() + ")");*/
			if (fieldType != null && isHelpfulType(fieldType, typeConstraint, subtypeChecker, target, depth)) {
				TypedExpression receiver = e;
				if (e.getExpression() instanceof ThisExpression || e.getType().equals(thisType))
					receiver = null;  // Don't use a receiver if it is null or the this type.
				else if (field.isStatic())
					receiver = makeStaticName(EclipseUtils.sanitizeTypename(objTypeName), e.getType());
				IJavaValue fieldValue = null;
				if (obj != null)
					fieldValue = (IJavaValue)obj.getField(field.name(), !field.declaringType().equals(objTypeImpl)).getValue();
				else if (field.isStatic())
					fieldValue = (IJavaValue)((IJavaReferenceType)e.getType()).getField(field.name()).getValue();
				TypedExpression fieldExpr = receiver == null ? makeVar(field.name(), fieldValue, fieldType, true) : makeFieldAccess(receiver, field.name(), fieldType, fieldValue); 
				addUniqueExpressionToList(ops, fieldExpr, depth, maxDepth, equivalences);
			}
		}
	}

	public static List<Method> getMethods(IJavaType type) {
		try {
			if (type != null && EclipseUtils.isObject(type.getSignature())) {
				List<?> untypedMethods = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleMethods();
				ArrayList<Method> methods = new ArrayList<Method>(untypedMethods.size());
				for (Object o: untypedMethods)
					methods.add((Method)o);
				return methods;
			} else
				return new ArrayList<Method>(0);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static boolean isLegalMethod(Method method, IJavaType thisType, boolean isConstructor) {
		return ((method.isPublic() || method.declaringType().equals(((JDIType)thisType).getUnderlyingType())) && (!method.isConstructor() || !method.isPackagePrivate()))  // Constructors are not marked as public.
				&& isConstructor == method.isConstructor() && !method.isSynthetic() && !method.isStaticInitializer() && !method.declaringType().name().equals("java.lang.Object");
	}
	
	private static void addMethodCalls(TypedExpression e, List<TypedExpression> nextLevel, TypeConstraint typeConstraint, List<TypedExpression> ops, IJavaType thisType, IJavaThread thread, IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, Map<IJavaValue, Set<EvaluatedExpression>> equivalences, int depth, int maxDepth) throws DebugException {
		// The public API doesn't tell us the methods of a class, so we need to use the jdi.  Note that we must now be careful converting between jdi types and Eclipse types.
		Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		if (classBlacklist.contains(objTypeImpl.name()))
			return;
		boolean isConstructor = e.getExpression() == null;
		boolean isStatic = !isConstructor && e.getExpression().getProperty("isStatic") != null;
		String objTypeName = isStatic ? e.getExpression().toString() : objTypeImpl.name();
		Method stackMethod = ((JDIStackFrame)stack).getUnderlyingMethod();
		// This code is so much nicer in a functional language.</complain>
		List<Method> visibleMethods = getMethods(e.getType());
		List<Method> legalMethods = new ArrayList<Method>(visibleMethods.size());
		Map<String, Map<Integer, List<Method>>> methodsByName = new HashMap<String, Map<Integer, List<Method>>>();
		for (Method method: visibleMethods) {
			String name = method.name();
			if (methodBlacklist.containsKey(objTypeName) && methodBlacklist.get(objTypeName).contains(method.name()))
				continue;
			legalMethods.add(method);
			if (!methodsByName.containsKey(name))
				methodsByName.put(name, new HashMap<Integer, List<Method>>());
			Map<Integer, List<Method>> methodsWithName = methodsByName.get(name);
			int numArgs = method.argumentTypeNames().size();
			if (!methodsWithName.containsKey(numArgs))
				methodsWithName.put(numArgs, new ArrayList<Method>());
			methodsWithName.get(numArgs).add(method);
		}
		for (Method method : legalMethods) {
			// Filter out java.lang.Object methods and fake methods like "<init>".  Note that if we don't filter out Object's methods we do getClass() and then call reflective methods, which is bad times.
			// TODO: Allow calling protected and package-private things when it's legal.
			if (!isLegalMethod(method, thisType, isConstructor) || (isStatic != method.isStatic()) || method.equals(stackMethod))  // Disable explicit recursion (that is, calling the current method), since it is definitely not yet complete.
				continue;
			if (method.returnTypeName().equals("void"))
				continue;
			IJavaType returnType = EclipseUtils.getFullyQualifiedTypeIfExists(method.returnTypeName(), target);
			/*if (returnType == null)
				System.err.println("I cannot get the class of the return type of " + objTypeImpl.name() + "." + method.name() + "() (" + method.returnTypeName() + ")");*/
			if (returnType != null && (isHelpfulType(returnType, typeConstraint, subtypeChecker, target, depth) || method.isConstructor())) {  // Constructors have void type... 
				List<?> argumentTypeNames = method.argumentTypeNames();
				// TODO: Improve overloading detection.
				boolean[] allHaveSameType = new boolean[argumentTypeNames.size()];
				Arrays.fill(allHaveSameType, true);
				for (Method m : methodsByName.get(method.name()).get(argumentTypeNames.size()))
					for (int i = 0; i < argumentTypeNames.size(); i++)
						if (!m.argumentTypeNames().get(i).equals(argumentTypeNames.get(i)))
							allHaveSameType[i] = false;
				ArrayList<ArrayList<TypedExpression>> allPossibleActuals = new ArrayList<ArrayList<TypedExpression>>(argumentTypeNames.size());
				Iterator<?> aIt = argumentTypeNames.iterator();
				while (aIt.hasNext()) {
					IJavaType argType = EclipseUtils.getFullyQualifiedTypeIfExists((String)aIt.next(), target);
					if (argType == null) {
						//System.err.println("I cannot get the class of the arguments to " + objTypeImpl.name() + "." + method.name() + "()");
						break;
					}
					ArrayList<TypedExpression> curPossibleActuals = new ArrayList<TypedExpression>();
					// TODO (low priority): This can get called multiple times if there are multiple args with the same type (or even different methods with args of the same type), but this has a tiny effect compared to the general state space explosion problem.
					for (TypedExpression a : nextLevel)
						if (isHelpfulType(a.getType(), new SupertypeBound(argType), subtypeChecker, target, 0)) {  // TODO: This doesn't work for generic methods.
							if (!allHaveSameType[allPossibleActuals.size()] && !argType.equals(a.getType()))  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
								a = makeCast(a, argType, a.getValue());
							curPossibleActuals.add(a);
						}
					allPossibleActuals.add(curPossibleActuals);
				}
				if (allPossibleActuals.size() == argumentTypeNames.size()) {
					TypedExpression receiver = e;
					if (method.isStatic())
						receiver = makeStaticName(EclipseUtils.sanitizeTypename(objTypeName), e.getType());
					makeAllCalls(method, method.name(), receiver, returnType, thisType, thread, ops, equivalences, target, allPossibleActuals, new ArrayList<TypedExpression>(allPossibleActuals.size()), depth, maxDepth);
				}
			}
		}
	}
	
	/**
	 * Adds the given expression to the given list
	 * if it has the right depth and if it is unique
	 * wrt UniqueASTChecker.
	 * We need to check the depth since genAllExprs
	 * returns is cumulative, so when the max depth is 2,
	 * at depth 0 nextLevel will be a superset of the
	 * nextLevel at depth 1 and so we will generate the same
	 * expressions again.
	 * @param list List to which to add unique expressions.
	 * @param e Expression to add if it is unique.
	 */
	private static void addUniqueExpressionToList(List<TypedExpression> list, TypedExpression e, int depth, int maxDepth, Map<IJavaValue, Set<EvaluatedExpression>> equivalences) {
		if (e != null && getDepth(e) == (maxDepth - depth) && isUnique(e))
			list.add(e);
			//addWithEquivalenceCheck(list, e, equivalences);
	}
	/*private static void addWithEquivalenceCheck(List<TypedExpression> list, TypedExpression e, Map<IJavaValue, Set<EvaluatedExpression>> equivalences) {
		if (e != null) {
			if (equivalences.containsKey(e.getValue()))
				equivalences.get(e.getValue()).add(new EvaluatedExpression(e.getExpression(), e.getValue()));
			else {
				if (e.getValue() != null) {
					Set<EvaluatedExpression> set = new HashSet<EvaluatedExpression>();
					set.add(new EvaluatedExpression(e.getExpression(), e.getValue()));
					equivalences.put(e.getValue(), set);
				}
				list.add(e);
			}
		}
	}*/
	
	/**
	 * Determines whether an expression of the given type can be
	 * useful to us.
	 * We currently assume that anything can be useful until the
	 * outermost depth, since there are probably methods that
	 * can take in anything, but that only the desired type
	 * is helpful otherwise.
	 * TODO (lowish priority): We could be smarter here, e.g.,
	 * by pruning ints if the target is not an int and there
	 * are no methods with int arguments and no arrays.
	 * @param curType The type to test.
	 * @param demonstrationType The type of the value entered by the user.
	 * @param varStaticType The declared type of the variable
	 * modified by the user.  This can be a supertype of the
	 * type of the demonstrated value.  If the demonstrated value
	 * is null, this is the only type information we have.
	 * @param depth The current depth.
	 * @return Whether an expression of the given type can be useful to us.
	 * @throws DebugException 
	 */
	private static boolean isHelpfulType(IJavaType curType, TypeConstraint typeConstraint, SubtypeChecker subtypeChecker, IJavaDebugTarget target, int depth) throws DebugException {
		if (curType != null && "V".equals(curType.getSignature()))  // Void things never return anything useful.
			return false;
		// TODO: The commented parts in {DesiredType,SupertypeBound}.fulfillsConstraint disallow downcasting things, e.g., (Foo)x.getObject(), which could be legal, but is unlikely to be.
		if (depth > 0)
			return true;
		return typeConstraint.isFulfilledBy(curType, subtypeChecker, target);
	}
	
	/**
	 * Crates all possible calls using the given actuals.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param receiver The receiving object.
	 * @param returnType The return type of the function.
	 * @param thread The calling thread.
	 * @param ops The list to add the unique calls created. 
	 * @param possibleActuals A list of all the possible actuals for each argument.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 * @throws DebugException 
	 */
	private static void makeAllCalls(Method method, String name, TypedExpression receiver, IJavaType returnType, IJavaType thisType, IJavaThread thread, List<TypedExpression> ops, Map<IJavaValue, Set<EvaluatedExpression>> equivalences, IJavaDebugTarget target, ArrayList<ArrayList<TypedExpression>> possibleActuals, ArrayList<TypedExpression> curActuals, int depth, int maxDepth) throws DebugException {
		if (curActuals.size() == possibleActuals.size())
			addUniqueExpressionToList(ops, makeCall(name, receiver, curActuals, returnType, thisType, method, thread, target), depth, maxDepth, equivalences);
		else {
			int argNum = curActuals.size();
			for (TypedExpression e : possibleActuals.get(argNum)) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, returnType, thisType, thread, ops, equivalences, target, possibleActuals, curActuals, depth, maxDepth);
				curActuals.remove(argNum);
			}
		}
	}
	/*private static void makeAllCalls(String name, Expression receiver, List<Expression> result, ArrayList<Iterable<EvaluatedExpression>> possibleActuals, ArrayList<Expression> curActuals) {
		if (curActuals.size() == possibleActuals.size())
			result.add(makeCall(name, receiver, curActuals));
		else {
			int depth = curActuals.size();
			for (EvaluatedExpression e : possibleActuals.get(depth)) {
				curActuals.add(e.getExpression());
				makeAllCalls(name, receiver, result, possibleActuals, curActuals);
				curActuals.remove(depth);
			}
		}
	}*/
	
	/*
	// TODO: The above misses some things: if x=y=z, we don't get x*x or x+x, so we don't get y*z or y+z.  We also have too many things, e.g., 0*x.  Possible solution: Remove those optimizations from generation (toString comparisons) and don't add constants to equivalence classes (add directly to curLevel).
	private static List<EvaluatedExpression> expandEquivalences(ArrayList<EvaluatedExpression> evaluatedExprs, Map<IJavaValue, Set<EvaluatedExpression>> equivalences) {
		Set<EvaluatedExpression> newlyExpanded = new HashSet<EvaluatedExpression>();
		for (EvaluatedExpression expr : evaluatedExprs)
			equivalences.get(expr.getResult()).add(expr);
		IJavaValue value = evaluatedExprs.get(0).getResult();
		for (EvaluatedExpression expr : new ArrayList<EvaluatedExpression>(equivalences.get(value)))
			expandEquivalencesRec(expr.getExpression(), equivalences, newlyExpanded, true);
		return new ArrayList<EvaluatedExpression>(equivalences.get(evaluatedExprs.get(0).getResult()));
	}
	
	private static void expandEquivalencesRec(Expression expr, Map<IJavaValue, Set<EvaluatedExpression>> equivalences, Set<EvaluatedExpression> newlyExpanded, boolean isTopLevel) {
		IJavaValue value = getExpressionValue(expr);
		if (value == null)
			return;
		EvaluatedExpression evaled = new EvaluatedExpression(expr, value);
		if (newlyExpanded.contains(evaled))
			return;
		int curDepth = getDepth(expr);
		if (!equivalences.containsKey(value))
			equivalences.put(value, new HashSet<EvaluatedExpression>());
		Set<EvaluatedExpression> curEquivalences = equivalences.get(value);
		if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ParenthesizedExpression || expr instanceof ThisExpression)
			curEquivalences.add(evaled);
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			expandEquivalencesRec(infix.getLeftOperand(), equivalences, newlyExpanded, false);
			expandEquivalencesRec(infix.getRightOperand(), equivalences, newlyExpanded, false);
			for (EvaluatedExpression l : new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(infix.getLeftOperand()))))
				for (EvaluatedExpression r : new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(infix.getRightOperand()))))
					if (getDepth(l.getExpression()) < curDepth && getDepth(r.getExpression()) < curDepth)
						curEquivalences.add(new EvaluatedExpression(makeInfix(l.getExpression(), infix.getOperator(), r.getExpression()), value));
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			expandEquivalencesRec(array.getArray(), equivalences, newlyExpanded, false);
			expandEquivalencesRec(array.getIndex(), equivalences, newlyExpanded, false);
			for (EvaluatedExpression a : new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(array.getArray()))))
				for (EvaluatedExpression i : new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(array.getIndex()))))
					if (getDepth(a.getExpression()) < curDepth && getDepth(i.getExpression()) < curDepth)
						curEquivalences.add(new EvaluatedExpression(makeArrayAccess(a.getExpression(), i.getExpression()), value));
		} else if (expr instanceof FieldAccess) {
			FieldAccess field = (FieldAccess)expr;
			expandEquivalencesRec(field.getExpression(), equivalences, newlyExpanded, false);
			for (EvaluatedExpression e : new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(field.getExpression()))))
				if (getDepth(e.getExpression()) < curDepth)
					curEquivalences.add(new EvaluatedExpression(makeFieldAccess(e.getExpression(), field.getName().getIdentifier()), value));
		} else if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)expr;
			expandEquivalencesRec(prefix.getOperand(), equivalences, newlyExpanded, false);
			for (EvaluatedExpression o : new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(prefix.getOperand()))))
				if (getDepth(o.getExpression()) < curDepth)
					curEquivalences.add(new EvaluatedExpression(makePrefix(o.getExpression(), prefix.getOperator()), value));
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			expandEquivalencesRec(call.getExpression(), equivalences, newlyExpanded, false);
			ArrayList<Iterable<EvaluatedExpression>> arguments = new ArrayList<Iterable<EvaluatedExpression>>(call.arguments().size());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				expandEquivalencesRec(curArg, equivalences, newlyExpanded, false);
				ArrayList<EvaluatedExpression> allCurArgPossibilities = new ArrayList<EvaluatedExpression>();
				for (EvaluatedExpression arg : equivalences.get(getExpressionValue(curArg)))
					if (getDepth(arg.getExpression()) < curDepth)
						allCurArgPossibilities.add(arg);
				arguments.add(allCurArgPossibilities);
			}
			List<Expression> newCalls = new ArrayList<Expression>();
			List<EvaluatedExpression> allPossibleExpressions = null;
			if (call.getExpression() == null || getExpressionValue(call.getExpression()) == null)
				allPossibleExpressions = Collections.singletonList(new EvaluatedExpression(call.getExpression(), value));
			else
				allPossibleExpressions = new ArrayList<EvaluatedExpression>(equivalences.get(getExpressionValue(call.getExpression())));
			for (EvaluatedExpression e : allPossibleExpressions)
				if (getDepth(e.getExpression()) < curDepth)
					makeAllCalls(call.getName().getIdentifier(), e.getExpression(), newCalls, arguments, new ArrayList<Expression>(arguments.size()));
			for (Expression newCall : newCalls)
				curEquivalences.add(new EvaluatedExpression(newCall, value));
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
		newlyExpanded.add(evaled);
	}
	
	private static int getDepth(Expression expr) {
		if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ParenthesizedExpression || expr instanceof ThisExpression)
			return 1;
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			return Math.max(getDepth(infix.getLeftOperand()), getDepth(infix.getRightOperand())) + 1;
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			return Math.max(getDepth(array.getArray()), getDepth(array.getIndex())) + 1;
		} else if (expr instanceof FieldAccess) {
			FieldAccess field = (FieldAccess)expr;
			return getDepth(field.getExpression()) + 1;
		} else if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)expr;
			return getDepth(prefix.getOperand()) + 1;
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			int maxDepth = getDepth(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++)
				maxDepth = Math.max(maxDepth, getDepth((Expression)call.arguments().get(i)));
			return maxDepth + 1;
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
	}*/
    
    /**
     * Class that checks whether an expression is unique.
     * An expression is unique iff it has no repeated variables
     * or integer constants.
     * TODO: Note that this disallows good expressions such as (x*2)-(y/2),
     * but also bad ones like (x+1)+1.
     */
    private static class UniqueASTChecker extends ASTVisitor {
    	
    	private final Set<String> seen;
    	private boolean isUnique;
    	
    	public UniqueASTChecker() {
    		seen = new HashSet<String>();
    		isUnique = true;
    	}
    	
    	@Override
    	public boolean visit(SimpleName node) {
    		visit(node.getIdentifier());
    		return true;
    	}
    	
    	@Override
    	public boolean visit(NumberLiteral node) {
    		visit(node.getToken());
    		return true;
    	}
    	
    	private void visit(String s) {
    		if (seen.contains(s))
    			isUnique = false;
    		else
    			seen.add(s);
    	}
    	
    	public boolean isUnique() {
    		return isUnique;
    	}
    	
    }
    
    /**
     * Checks whether a given expression is unique wrt UniqueASTChecker.
     * @param e The expression to check.
     * @return Whether or not the expression is unique wrt UniqueASTChecker.
     */
    private static boolean isUnique(TypedExpression e) {
    	UniqueASTChecker checker = new UniqueASTChecker();
    	e.getExpression().accept(checker);
    	return checker.isUnique();
    }
    
    private static int getDepth(TypedExpression expr) {
    	Object depthProp = expr.getExpression().getProperty("depth");
    	if (depthProp != null)
    		return ((Integer)depthProp).intValue();
    	else
    		return getDepth(expr.getExpression());
    }
    
    private static int getDepth(Expression expr) {
    	if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral)
			return 0;
    	if (expr instanceof ParenthesizedExpression)
			return getDepth(((ParenthesizedExpression)expr).getExpression());
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			return Math.max(getDepth(infix.getLeftOperand()), getDepth(infix.getRightOperand())) + 1;
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			return Math.max(getDepth(array.getArray()), getDepth(array.getIndex())) + 1;
		} else if (expr instanceof FieldAccess) {
			return getDepth(((FieldAccess)expr).getExpression()) + 1;
		} else if (expr instanceof PrefixExpression) {
			return getDepth(((PrefixExpression)expr).getOperand()) + 1;
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepth(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				int curArgDepth = getDepth(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			return maxChildDepth + 1;
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepth(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				int curArgDepth = getDepth(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			return maxChildDepth + 1;
		} else if (expr instanceof CastExpression) {
			return getDepth(((CastExpression)expr).getExpression());
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
    }
    
    private static boolean isInt(IJavaType type) throws DebugException {
    	return type != null && "I".equals(type.getSignature());
    }
    
    private static boolean isBoolean(IJavaType type) throws DebugException {
    	return type != null && "Z".equals(type.getSignature());
    }
    
    private static boolean isLong(IJavaType type) throws DebugException {
    	return type != null && "J".equals(type.getSignature());
    }
    
    private static boolean isByte(IJavaType type) throws DebugException {
    	return type != null && "B".equals(type.getSignature());
    }
    
    private static boolean isChar(IJavaType type) throws DebugException {
    	return type != null && "C".equals(type.getSignature());
    }
    
    private static boolean isShort(IJavaType type) throws DebugException {
    	return type != null && "S".equals(type.getSignature());
    }
    
    private static boolean isFloat(IJavaType type) throws DebugException {
    	return type != null && "F".equals(type.getSignature());
    }
    
    private static boolean isDouble(IJavaType type) throws DebugException {
    	return type != null && "D".equals(type.getSignature());
    }
    
    private static boolean isObjectOrInterface(IJavaType type) {
    	return type instanceof IJavaClassType || type instanceof IJavaInterfaceType;
    }
    
    // Evaluation helpers that compute IJavaValues and IJavaTypes.
    
    private static IJavaValue makeIntValue(IJavaDebugTarget target, int n) {
    	return target.newValue(n);
    }
    
    private static IJavaValue makeBooleanValue(IJavaDebugTarget target, boolean b) {
    	return target.newValue(b);
    }
    
    private static IJavaValue computeInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right, IJavaType type) throws NumberFormatException, DebugException {
    	if (isInt(type))
    		return computeIntInfixOp(target, left, op, right);
    	else if (isBoolean(type))
    		return computeBooleanInfixOp(target, left, op, right);
    	else if (type instanceof IJavaReferenceType)
    		return computeRefInfixOp(target, left, op, right);
    	else
    		throw new RuntimeException("Unexpected type.");
    }
    
    private static IJavaValue computeIntInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws NumberFormatException, DebugException {
    	if (left == null || right == null)
    		return null;
    	int l = Integer.parseInt(left.getValueString());
    	int r = Integer.parseInt(right.getValueString());
    	if (op == InfixExpression.Operator.PLUS)
    		return makeIntValue(target, l + r);
    	if (op == InfixExpression.Operator.MINUS)
    		return makeIntValue(target, l - r);
    	if (op == InfixExpression.Operator.TIMES)
    		return makeIntValue(target, l * r);
    	if (op == InfixExpression.Operator.DIVIDE) {
    		assert r != 0;
    		return makeIntValue(target, l / r);
    	}
    	if (op == InfixExpression.Operator.EQUALS)
    		return makeBooleanValue(target, l == r);
    	if (op == InfixExpression.Operator.NOT_EQUALS)
    		return makeBooleanValue(target, l != r);
    	if (op == InfixExpression.Operator.LESS)
    		return makeBooleanValue(target, l < r);
    	if (op == InfixExpression.Operator.LESS_EQUALS)
    		return makeBooleanValue(target, l <= r);
    	if (op == InfixExpression.Operator.GREATER)
    		return makeBooleanValue(target, l > r);
    	if (op == InfixExpression.Operator.GREATER_EQUALS)
    		return makeBooleanValue(target, l >= r);
    	throw new RuntimeException("Unknown infix operation: " + op.toString());
    }
    
    private static IJavaValue computeBooleanInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws DebugException {
    	if (left == null || right == null)
    		return null;
    	boolean l = Boolean.parseBoolean(left.getValueString());
    	boolean r = Boolean.parseBoolean(right.getValueString());
    	if (op == InfixExpression.Operator.EQUALS)
    		return makeBooleanValue(target, l == r);
    	if (op == InfixExpression.Operator.NOT_EQUALS)
    		return makeBooleanValue(target, l != r);
    	if (op == InfixExpression.Operator.CONDITIONAL_AND)
    		return makeBooleanValue(target, l && r);
    	if (op == InfixExpression.Operator.CONDITIONAL_OR)
    		return makeBooleanValue(target, l || r);
    	throw new RuntimeException("Unknown infix operation: " + op.toString());
    }
    
    private static IJavaValue computeRefInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws DebugException {
    	if (left == null || right == null)
    		return null;
    	IJavaObject l = (IJavaObject)left;
    	IJavaObject r = (IJavaObject)right;
    	if (op == InfixExpression.Operator.EQUALS)
    		return makeBooleanValue(target, l.getUniqueId() == r.getUniqueId());
    	if (op == InfixExpression.Operator.NOT_EQUALS)
    		return makeBooleanValue(target, l.getUniqueId() != r.getUniqueId());
    	throw new RuntimeException("Unknown infix operation: " + op.toString());
    }
    
    private static IJavaValue computePrefixOp(IJavaDebugTarget target, IJavaValue e, PrefixExpression.Operator op) throws DebugException {
    	if (e == null )
    		return null;
    	if (op == PrefixExpression.Operator.MINUS)
    		return makeIntValue(target, -Integer.parseInt(e.getValueString()));
    	if (op == PrefixExpression.Operator.NOT)
    		return makeBooleanValue(target, !Boolean.parseBoolean(e.getValueString()));
    	throw new RuntimeException("Unknown prefix operation: " + op.toString());
    }
    
    /*
     * If we call this code, things that crash trigger breakpoints for some reason.
     * Some things work when I modify the ChoiceBreakpointListener to resume the
     * thread rather than handle things normally, but some things break....
     */
    private static IJavaValue computeCall(final Method method, final IJavaValue receiver, ArrayList<TypedExpression> args, final IJavaThread thread, IJavaDebugTarget target, final JDIType receiverType) {
		final IJavaValue[] argValues = new IJavaValue[args.size()];
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i).getValue() == null)
				return null;
			argValues[i] = args.get(i).getValue();
		}
		try {
			//System.out.println("Calling " + (receiver != null ? receiver : receiverType) + "." + method.name() + " with args " + Arrays.toString(argValues));
			IJavaValue value = null;
	    	if (receiver == null && "<init>".equals(method.name()))
    			value = ((IJavaClassType)receiverType).newInstance(method.signature(), argValues, thread);
	    	else if (receiver == null)
	    		value = ((IJavaClassType)receiverType).sendMessage(method.name(), method.signature(), argValues, thread);
	    	else
	    		value = ((IJavaObject)receiver).sendMessage(method.name(), method.signature(), argValues, thread, !method.declaringType().equals(receiverType.getUnderlyingType()));
			//System.out.println("Got " + value);
			return value;
		} catch (DebugException e) {
			//System.out.println("Crashed.");
			return target.voidValue();
		}
    }
    
    // Helper methods to create AST nodes, as they don't seem to have useful constructors.
    
    /*
     * My nodes can have the following properties:
     * isStatic: Exists (and is true) iff the expression is a static name.
     * value: The value of the expression, or null if it is unknown..  All nodes should have this.
     * isConstant: Exists (and is true) iff the expression is a constant (e.g., 0, true, null).
     */
    
    // Pass in cached int type for efficiency.
    private static TypedExpression makeNumber(String val, IJavaValue value, IJavaType type) {
    	Expression e = ast.newNumberLiteral(val);
    	e.setProperty("isConstant", true);
    	setExpressionValue(e, value);
    	return new TypedExpression(e, type, value);
    }
    
    // Pass in cached boolean type for efficiency.
    private static TypedExpression makeBoolean(boolean val, IJavaValue value, IJavaType type) {
    	Expression e = ast.newBooleanLiteral(val);
    	e.setProperty("isConstant", true);
    	setExpressionValue(e, value);
    	return new TypedExpression(e, type, value);
    }

    private static TypedExpression makeNull(IJavaDebugTarget target) {
    	Expression e = ast.newNullLiteral();
    	e.setProperty("isConstant", true);
    	setExpressionValue(e, target.nullValue());
    	return new TypedExpression(e, null, target.nullValue());
    }
    
    private static TypedExpression makeVar(String name, IJavaValue value, IJavaType type, boolean isFieldAccess) {
    	Expression e = ast.newSimpleName(name);
    	setExpressionValue(e, value);
    	if (isFieldAccess)
    		e.setProperty("depth", 1);
    	return new TypedExpression(e, type, value);
    }
    
    private static Expression newStaticName(String name) {
    	Expression e = ast.newName(name);
    	e.setProperty("isStatic", true);
    	setExpressionValue(e, null);
    	return e;
    }
    
    private static TypedExpression makeStaticName(String name, IJavaType type) {
    	return new TypedExpression(newStaticName(name), type, null);
    }
    
    private static TypedExpression makeThis(IJavaValue value, IJavaType type) {
    	ThisExpression e = ast.newThisExpression();
    	setExpressionValue(e, value);
    	return new TypedExpression(e, type, value);
    }
    
    private static TypedExpression makeInfix(IJavaDebugTarget target, TypedExpression left, InfixExpression.Operator op, TypedExpression right, IJavaType type) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left.getExpression(), op, right.getExpression());
		IJavaValue value = computeInfixOp(target, left.getValue(), op, right.getValue(), left.getType());
    	setExpressionValue(e, value);
		return new TypedExpression(e, type, value);
    }
    
    private static InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r) {
    	InfixExpression e = ast.newInfixExpression();
		e.setLeftOperand(parenIfNeeded(copyExpr(l)));
		e.setOperator(op);
		e.setRightOperand(parenIfNeeded(copyExpr(r)));
		return e;
    }
    
    private static TypedExpression makeArrayAccess(TypedExpression array, TypedExpression index, IJavaValue value) {
    	ArrayAccess e = makeArrayAccess(array.getExpression(), index.getExpression());
    	setExpressionValue(e, value);
		return new TypedExpression(e, getArrayElementType(array), value);
    }
    
    private static ArrayAccess makeArrayAccess(Expression array, Expression index) {
    	ArrayAccess e = ast.newArrayAccess();
    	e.setArray(copyExpr(array));
    	e.setIndex(copyExpr(index));
    	return e;
    }
    
    private static TypedExpression makeFieldAccess(TypedExpression obj, String name, IJavaType fieldType, IJavaValue value) {
		FieldAccess e = makeFieldAccess(obj.getExpression(), name);
    	setExpressionValue(e, value);
		return new TypedExpression(e, fieldType, value);
    }
    
    private static FieldAccess makeFieldAccess(Expression obj, String name) {
		FieldAccess e = ast.newFieldAccess();
		e.setExpression(copyExpr(obj));
		e.setName(ast.newSimpleName(name));
    	return e;
    }
    
    private static TypedExpression makePrefix(IJavaDebugTarget target, TypedExpression operand, PrefixExpression.Operator op, IJavaType type) throws DebugException {
    	PrefixExpression e = makePrefix(operand.getExpression(), op);
		IJavaValue value = computePrefixOp(target, operand.getValue(), op);
    	setExpressionValue(e, value);
		return new TypedExpression(e, type, value);
    }
    
    private static PrefixExpression makePrefix(Expression operand, PrefixExpression.Operator op) {
    	PrefixExpression e = ast.newPrefixExpression();
		e.setOperand(parenIfNeeded(copyExpr(operand)));
		e.setOperator(op);
		return e;
    }
    
    private static TypedExpression makeCall(String name, TypedExpression receiver, ArrayList<TypedExpression> args, IJavaType returnType, IJavaType thisType, Method method, IJavaThread thread, IJavaDebugTarget target) throws DebugException {
    	//IJavaValue value = computeCall(method, receiver.getValue(), args, thread, target, ((JDIType)receiver.getType()));
    	IJavaValue value = null;
    	if (receiver.getExpression() == null) {
    		assert "<init>".equals(name);
    		return makeNewObject(receiver.getType(), args, value);
    	}
		if (receiver.getExpression() instanceof ThisExpression || receiver.getType().equals(thisType))
			receiver = null;  // Don't use a receiver if it is null or the this type.
    	return makeCall(name, receiver == null ? null : copyExpr(receiver.getExpression()), args, returnType, value);
    }
    /*private static TypedExpression makeCall(String name, String classname, ArrayList<TypedExpression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
    private static TypedExpression makeCall(String name, Expression receiver, ArrayList<TypedExpression> args, IJavaType returnType, IJavaValue value) {
    	MethodInvocation e = ast.newMethodInvocation();
    	e.setName(ast.newSimpleName(name));
    	e.setExpression(receiver);
    	for (TypedExpression ex: args)
    		e.arguments().add(copyExpr(ex.getExpression()));
    	setExpressionValue(e, value);
    	return new TypedExpression(e, returnType, value);
    }
    /*private static MethodInvocation makeCall(String name, Expression receiver, ArrayList<Expression> args) {
    	MethodInvocation e = ast.newMethodInvocation();
    	e.setName(ast.newSimpleName(name));
    	e.setExpression(copyExpr(receiver));
    	for (Expression ex: args)
    		e.arguments().add(copyExpr(ex));
    	return e;
    }*/
    
    private static TypedExpression makeCast(TypedExpression obj, IJavaType targetType, IJavaValue value) throws DebugException {
    	CastExpression e = makeCast(obj.getExpression(), targetType);
    	setExpressionValue(e, value);
		return new TypedExpression(e, targetType, value);
    }

    private static CastExpression makeCast(Expression obj, IJavaType targetType) throws DebugException {
		CastExpression e = ast.newCastExpression();
		e.setExpression(copyExpr(obj));
		e.setType(makeType(targetType));
    	return e;
    }
    
    private static TypedExpression makeNewObject(IJavaType type, ArrayList<TypedExpression> args, IJavaValue value) throws DebugException {
    	ClassInstanceCreation e = ast.newClassInstanceCreation();
    	e.setType(ast.newSimpleType(ast.newName(EclipseUtils.sanitizeTypename(type.getName()))));
    	for (TypedExpression ex: args)
    		e.arguments().add(copyExpr(ex.getExpression()));
    	setExpressionValue(e, value);
    	return new TypedExpression(e, type, value);
    }
    
    private static org.eclipse.jdt.core.dom.Type makeType(IJavaType type) throws DebugException {
    	if (type instanceof IJavaArrayType)
    		return ast.newArrayType(makeType(((IJavaArrayType)type).getComponentType()));
    	else if (type instanceof IJavaReferenceType)
    		return ast.newSimpleType(ast.newName(type.getName()));
    	else if (isInt(type))
    		return ast.newPrimitiveType(PrimitiveType.INT);
    	else if (isBoolean(type))
    		return ast.newPrimitiveType(PrimitiveType.BOOLEAN);
    	else if (isLong(type))
    		return ast.newPrimitiveType(PrimitiveType.LONG);
    	else if (isByte(type))
    		return ast.newPrimitiveType(PrimitiveType.BYTE);
    	else if (isChar(type))
    		return ast.newPrimitiveType(PrimitiveType.CHAR);
    	else if (isShort(type))
    		return ast.newPrimitiveType(PrimitiveType.SHORT);
    	else if (isFloat(type))
    		return ast.newPrimitiveType(PrimitiveType.FLOAT);
    	else if (isDouble(type))
    		return ast.newPrimitiveType(PrimitiveType.DOUBLE);
    	else
    		throw new RuntimeException("Unexpected type " + type);
    }
    
    private static void setExpressionValue(Expression e, IJavaValue v) {
    	e.setProperty("value", v);
    }
    
    private static IJavaValue getExpressionValue(Expression e) {
    	return (IJavaValue)e.getProperty("value");
    }
    
    /**
     * Parenthesize an expression if it is an infix or prefix expression.
     * @param e The expression.
     * @return The given expression parenthesized if it is infix
     * or prefix, and the expression itself otherwise.
     */
    private static Expression parenIfNeeded(Expression e) {
    	if (e instanceof InfixExpression || e instanceof PrefixExpression) {
			ParenthesizedExpression p = ast.newParenthesizedExpression();
			p.setExpression(e);
			setExpressionValue(p, getExpressionValue(e));
			return p;
    	} else
    		return e;
    }
    
    /**
     * Gets the type of an array.
     * @param array The array whose type we want to get.
     * @return The type of the given array.
     */
    private static IJavaType getArrayElementType(TypedExpression array) {
    	try {
			return ((IJavaArrayType)array.getType()).getComponentType();
		} catch (DebugException e) {
			if (e.getCause() instanceof ClassNotLoadedException) {
				//System.err.println("I cannot get the class of the array " + array.getExpression());
				return null;
			} else
				throw new RuntimeException(e);
		}
    }
    
    /**
     * Makes a copy of an expression so that the DOM methods
     * don't give us errors about bad parents.
     * @param e The expression to copy.
     * @return A copy of the given expression.
     */
    private static Expression copyExpr(Expression e) {
    	if (e == null)
    		return null;
    	Expression copy = (Expression)ASTNode.copySubtree(e.getAST(), e);
    	// Thanks for not copying properties...
    	Iterator<?> it = e.properties().entrySet().iterator();
    	while (it.hasNext()) {
    		Map.Entry<?,?> property = (Map.Entry<?,?>)it.next();
    		copy.setProperty((String)property.getKey(), property.getValue());
    	}
    	return copy;
    }

}
