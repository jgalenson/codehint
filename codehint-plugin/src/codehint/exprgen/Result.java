package codehint.exprgen;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.effects.Effect;
import codehint.utils.Utils;

public class Result {
	
	private final Value value;
	private final Set<Effect> effects;
	
	public Result(IJavaValue value, Set<Effect> effects, ValueCache valueCache, IJavaThread thread) {
		this.value = Value.makeValue(value, valueCache, thread);
		this.effects = effects;
	}

	public Result(IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		this(value, Collections.<Effect>emptySet(), valueCache, thread);
	}

	public Value getValue() {
		return value;
	}

	public Set<Effect> getEffects() {
		return effects;
	}
	
	public String getResultString(String valueToString) {
		if (effects.isEmpty())
			return valueToString;
		StringBuilder sb = new StringBuilder();
		sb.append(valueToString);
		for (Effect effect: effects)
			sb.append(", ").append(effect.toString());
		return Utils.getPrintableString(sb.toString());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		result = prime * result + ((effects == null) ? 0 : effects.hashCode());
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
		Result other = (Result) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		if (effects == null) {
			if (other.effects != null)
				return false;
		} else if (!effects.equals(other.effects))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return value.toString() + " " + effects.toString();
	}

}
