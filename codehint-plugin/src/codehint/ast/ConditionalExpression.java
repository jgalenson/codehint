package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public class ConditionalExpression extends Expression {

	private final Expression expression;
	private final Expression thenExpression;
	private final Expression elseExpression;
	
	public ConditionalExpression(IJavaType staticType, Expression expression, Expression thenExpression, Expression elseExpression) {
		super(staticType);
		this.expression = expression;
		this.thenExpression = thenExpression;
		this.elseExpression = elseExpression;
	}

	public Expression getExpression() {
		return expression;
	}

	public Expression getThenExpression() {
		return thenExpression;
	}

	public Expression getElseExpression() {
		return elseExpression;
	}

	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, expression);
			acceptChild(visitor, thenExpression);
			acceptChild(visitor, elseExpression);
		}
	}
	
	@Override
	public String toString() {
		return expression.toString() + " ? " + thenExpression.toString() + " : " + elseExpression.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((elseExpression == null) ? 0 : elseExpression.hashCode());
		result = prime * result + ((expression == null) ? 0 : expression.hashCode());
		result = prime * result + ((thenExpression == null) ? 0 : thenExpression.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConditionalExpression other = (ConditionalExpression) obj;
		if (elseExpression == null) {
			if (other.elseExpression != null)
				return false;
		} else if (!elseExpression.equals(other.elseExpression))
			return false;
		if (expression == null) {
			if (other.expression != null)
				return false;
		} else if (!expression.equals(other.expression))
			return false;
		if (thenExpression == null) {
			if (other.thenExpression != null)
				return false;
		} else if (!thenExpression.equals(other.thenExpression))
			return false;
		return true;
	}

}
