package codehint.ast;

public class BooleanLiteral extends Expression {
	
	private final boolean value;

	public BooleanLiteral(boolean value) {
		this.value = value;
	}

	public boolean booleanValue() {
		return value;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return String.valueOf(value);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (value ? 1231 : 1237);
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
		BooleanLiteral other = (BooleanLiteral) obj;
		if (value != other.value)
			return false;
		return true;
	}

}
