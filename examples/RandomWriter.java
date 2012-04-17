import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import codehint.CodeHint;

public class RandomWriter {
	
	/*
	 * Make a model that computes, for each string of size order in text (called a seed),
	 * all the characters that follow that seed.
	 * As an example, given "abacabc", with order 1, "a" is followed by "b", "b", and "c";
	 * with size 2, "ab" is followed by "a" and "c", and so forth.
	 * Note that this is case-sensitive.
	 */
	private static Map<String, List<Character>> makeMarkovModel(String text, int order) {
		Map<String, List<Character>> model = new HashMap<String, List<Character>>();
		// Loop over all the substrings of length order and update the information about the characters that follow them.
		for (int i = 0; i < text.length() - order; i++) {
			int j = i + order;
			
			String seed = null;
			System.out.println("Task: Find me the seed string.");
			
			char follower = 0;
			System.out.println("Task: Find me the character that follows the seed.");
			
			// Add this following character to the list of characters we've seen that follow this seed.
			if (!model.containsKey(seed))
				model.put(seed, new ArrayList<Character>());
			model.get(seed).add(follower);
		}
		return model;
	}
	
	/*
	 * Find the seed that occurs most often in the model.
	 * Ties are broken arbitrarily.
	 */
	private static String getMostCommonSeed(Map<String, List<Character>> model) {
		String best = null;
		
		Collection<String> allKeys = null;
		System.out.println("Task: Find me a Collection of all the keys in the model.");
		
		// Given the set of keys, find one that occurs the most often.
		for (String seed: allKeys)
			if (best == null || model.get(seed).size() > model.get(best).size())
				best = seed;
		return best;
	}
	
	/*
	 * Randomly generate a string similar to the original input text by starting
	 * with the most commonly-occurring seed and updating that with a character chosen
	 * based on the probabilities of what followed that seed in the original text.
	 */
	private static String doRandomWrite(Map<String, List<Character>> model, int length) {
		String seed = null;
		System.out.println("Task: Get me one of the seeds that occurs the most in the input string / model.");
		
		String text = seed;
		Random random = new Random();
		// Generate the next character of the text and update the seed to include it.
		while (text.length() < length && model.containsKey(seed)) {
			List<Character> followers = model.get(seed);
			
			int randomIndex = Integer.MIN_VALUE;
			System.out.println("Task: Get a random index inside the list of followers.");
			
			char next = followers.get(randomIndex);
			text += next;
			seed = seed.substring(1) + next;
		}
		return text;
	}
	
	public static void main(String[] args) {
		Map<String, List<Character>> model = makeMarkovModel("This is a test of the emergency broadcast system.", 2);
		String commonSeed = getMostCommonSeed(model);
		System.out.println(doRandomWrite(model, 100));
	}

}
