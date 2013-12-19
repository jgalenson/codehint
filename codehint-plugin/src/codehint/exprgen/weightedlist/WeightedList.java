package codehint.exprgen.weightedlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A class that represents a weighted list of elements.
 * These elements can be extracted with probability
 * proportional to their weights.
 *
 * @param <T> The type of the elements in the list.
 */
public abstract class WeightedList<T> {
	
	private final ArrayList<T> elems;
	private final ArrayList<Double> cumulativeWeights;
	private double maxWeight;
	private static final Random random = new Random();;
	
	/**
	 * Creates an empty list.
	 */
	protected WeightedList() {
		elems = new ArrayList<T>();
		cumulativeWeights = new ArrayList<Double>();
		maxWeight = 0d;
	}
	
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
	public void add(T elem) {
		elems.add(elem);
		maxWeight += getWeight(elem);
		cumulativeWeights.add(maxWeight);
	}
	
	/**
	 * Gets an element from the list according to
	 * the weights of the elements.
	 * @return An element chosen from this list
	 * according to the weights.
	 */
	public T get() {
		if (elems.isEmpty())
			return null;
		double rand = random.nextDouble() * maxWeight;
		int index = Collections.binarySearch(cumulativeWeights, rand);
		if (index < 0)  // If we happen to pick a number in the list, we use it.
			index = -index - 1;
		return elems.get(index);
	}
	
	/**
	 * Adds all the given elements to this list.
	 * @param elems The elements to add.
	 */
	public void addAll(List<T> elems) {
		for (T elem: elems)
			add(elem);
	}
	
	/**
	 * Returns a read-only view of the elements in the list.
	 * @return A read-only view of the elements in the list.
	 */
	public List<T> readElems() {
		return Collections.unmodifiableList(elems);
	}
	
	/**
	 * Gets the size of the list.
	 * @return The size of the list.
	 */
	public int size() {
		return elems.size();
	}
	
	@Override
	public String toString() {
		int numElems = elems.size();
		if (numElems == 0)
			return "";
		StringBuilder sb = new StringBuilder();
		double cumulativeWeight = 0d;
		for (int i = 0; true; i++) {
			double curCumulativeWeight = cumulativeWeights.get(i);
			sb.append(elems.get(i)).append("(").append(curCumulativeWeight - cumulativeWeight).append(")");
			if (i == numElems - 1)
				break;
			cumulativeWeight = curCumulativeWeight;
			sb.append(", ");
		}
		return sb.toString();
	}

}
