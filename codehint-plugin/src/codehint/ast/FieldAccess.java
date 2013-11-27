package codehint.ast;

public class FieldAccess extends Expression {
	
	private final Expression expression;
	private final SimpleName name;
	
	public FieldAccess(Expression expression, SimpleName name) {
		this.expression = expression;
		this.name = name;
	}

	public Expression getExpression() {
		return expression;
	}

	public SimpleName getName() {
		return name;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, expression);
			acceptChild(visitor, name);
		}
	}
	
	@Override
	public String toString() {
		return expression.toString() + "." + name.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((expression == null) ? 0 : expression.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		FieldAccess other = (FieldAccess) obj;
		if (expression == null) {
			if (other.expression != null)
				return false;
		} else if (!expression.equals(other.expression))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
