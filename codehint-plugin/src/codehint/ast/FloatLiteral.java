package codehint.ast;

public class FloatLiteral extends NumberLiteral {
	
	private final float number;
	
	public FloatLiteral(float n) {
		this.number = n;
	}

	public float getNumber() {
		return number;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return String.valueOf(number);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(number);
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
		FloatLiteral other = (FloatLiteral) obj;
		if (Float.floatToIntBits(number) != Float.floatToIntBits(other.number))
			return false;
		return true;
	}

}
