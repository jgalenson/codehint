package codehint.ast;

public class TypeLiteral extends Expression {
	
	private final Type type;

	public TypeLiteral(Type type) {
		super(type.getStaticType());
		this.type = type;
	}

	public Type getType() {
		return type;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChild(visitor, type);
	}
	
	@Override
	public String toString() {
		return type.toString() + ".class";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		TypeLiteral other = (TypeLiteral) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

}
