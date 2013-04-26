package codehint.exprgen;

import java.util.Comparator;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

/**
 * Sorts expressions based on how often their methods and fields
 * appear in our database.
 */
public class ExpressionSorter implements Comparator<TypedExpression> {
	
	private final ExpressionMaker expressionMaker;
	private final Weights weights;
	
	public ExpressionSorter(ExpressionMaker expressionMaker, Weights weights) {
		this.expressionMaker = expressionMaker;
		this.weights = weights;
	}

	@Override
	public int compare(TypedExpression e1, TypedExpression e2) {
		double p1 = weigh(e1.getExpression());
		double p2 = weigh(e2.getExpression());
		//System.out.println(e1.getExpression() + " " + p1);
		//System.out.println(e2.getExpression() + " " + p2);
		if (p1 < p2)
			return 1;
		else if (p1 > p2)
			return -1;
		else
			return e1.getExpression().toString().length() - e2.getExpression().toString().length();
	}
	
	private double weigh(Expression expr) {
		CallVisitor visitor = new CallVisitor();
		expr.accept(visitor);
		return visitor.getProbability();
	}
	
	private class CallVisitor extends ASTVisitor {
		
		private double prob;
		
		public CallVisitor() {
			prob = 1;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			Method method = expressionMaker.getMethod(node);
			prob *= weights.getWeight(method.declaringType().name(), Weights.getMethodKey(method));
			return true;
		}

		@Override
		public boolean visit(ClassInstanceCreation node) {
			Method method = expressionMaker.getMethod(node);
			prob *= weights.getWeight(method.declaringType().name(), Weights.getMethodKey(method));
			return true;
		}
		
		@Override
		public boolean visit(FieldAccess node) {
			Field field = expressionMaker.getField(node);
			if (field == null)  // Array length
				prob *= weights.getAverageWeight();
			else
				visitField(field);
			return true;
		}
		
		@Override
		public boolean visit(SimpleName node) {
			Field field = expressionMaker.getField(node);
			if (field != null && field.isPublic())  // Don't count unqualified non-public field accesses.
				visitField(field);
			return true;
		}
		
		private void visitField(Field field) {
			if (field != null) {
				prob *= weights.getWeight(field.declaringType().name(), field.name());
				// We bias fields by how often they occur compared to methods: P(field) / P(method).
				prob *= 0.14;  // TODO: Use the actually-computed value once I update the weights file to add it.
			}
		}
		
		@Override
		public boolean visit(InfixExpression node) {
			prob *= weights.getAverageWeight();
			return true;
		}
		
		@Override
		public boolean visit(PrefixExpression node) {
			prob *= weights.getAverageWeight();
			return true;
		}
		
		@Override
		public boolean visit(ArrayAccess node) {
			prob *= weights.getAverageWeight();
			return true;
		}
		
		public double getProbability() {
			return prob;
		}
		
	}

}
