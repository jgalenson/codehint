package codehint.ast;

public class ThisExpression extends Expression {
	
	private final Name qualifier;

	public ThisExpression(Name qualifier) {
		this.qualifier = qualifier;
	}
	
	public ThisExpression() {
		this(null);
	}

	public Name getQualifier() {
		return qualifier;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChild(visitor, qualifier);
	}
	
	@Override
	public String toString() {
		if (qualifier != null)
			return qualifier.toString() + ".this";
		else
			return "this";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((qualifier == null) ? 0 : qualifier.hashCode());
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
		ThisExpression other = (ThisExpression) obj;
		if (qualifier == null) {
			if (other.qualifier != null)
				return false;
		} else if (!qualifier.equals(other.qualifier))
			return false;
		return true;
	}

}
