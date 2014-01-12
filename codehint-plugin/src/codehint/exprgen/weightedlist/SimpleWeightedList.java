package codehint.exprgen.weightedlist;

import java.util.List;

/**
 * A weighted list of elements that knows how to
 * compute the weight of each element.
 * 
 * @param <T> The type of the elements in the list.
 */
public abstract class SimpleWeightedList<T> extends WeightedList<T> {

	/**
	 * Computes the weight of elements in the list.
	 * @param elem An element.
	 * @return The weight of the element.
	 */
	public abstract double getWeight(T elem);
	
	/**
	 * Adds the given element to the list.
	 * @param elem The element to add.
	 */
	public void addWeighted(T elem) {
		addWeighted(elem, getWeight(elem));
	}
	
	/**
	 * Adds all the given elements to this list.
	 * @param elems The elements to add.
	 */
	public void addAllWeighted(List<T> elems) {
		for (T elem: elems)
			addWeighted(elem);
	}

}
