import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import codehint.CodeHint;

@SuppressWarnings("unused")
public class Parse {
    
	/*
	 * Builds a Map that, for each line in the string, has whatever is
	 * before the separator map to whatever is after it.
	 * For example, given the email below, it will map "Subject" to
	 * "[cs-grads-food] Pizza" and "Date" to "Mon, 27 Feb 2012 15:28:03 -0800".
	 */
    private static Map<String, String> parse(String email, String seperator) {
    	String[] lines = email.split("\n");
    	Map<String, String> parts = new HashMap<String, String>(lines.length);
    	for (String line : lines) {
    		int index = -1;
    		System.out.println("Task: Find the index of the seperator string in the line.");

    		String header = null;
    		System.out.println("Task: Find everything that comes before the separator in the line.");
    		
    		String body = null;
    		System.out.println("Task: Find everything that comes after the separator in the line.");
    		
    		parts.put(header,  body);
    	}
    	System.out.println(parts);
    	return parts;
    }
    
    /*
     * Find the argument that comes offset arguments after the target string, if it is in the array.
     * For example, getArgument(new String[] {"a", "b", c", "d"}, "a", 2) returns "c".
     */
    private static String getArgument(String[] argsArr, String target, int offset) {
    	List<String> argsList = null;
    	System.out.println("Task: Convert the array of arguments into a list.");
    	
    	int index = -1;
    	System.out.println("Task: Get the index of the target string in the array/list.");
    	
    	if (index == -1)
    		return null;
    	int newIndex = index + offset;
    	if (newIndex >= argsArr.length)
    		return null;
    	return argsList.get(newIndex);
    }
    
    private static final String TEST_EMAIL = "Message-ID: <4F4C1183.3030805@cs.berkeley.edu>\nDate: Mon, 27 Feb 2012 15:28:03 -0800\nFrom: Nick Hay <nickjhay@cs.berkeley.edu>\nUser-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:10.0.2) Gecko/20120216 Thunderbird/10.0.2\nMIME-Version: 1.0\nTo: cs-grads-food@eecs.berkeley.edu\nSubject: [cs-grads-food] Pizza";

	public static void main(String[] args) {
		parse(TEST_EMAIL, ": ");
		getArgument(new String[] { "--foo", "--bar", "--baz", "42" }, "--baz", 1);
		getArgument(new String[] { "--foo", "--bar", "--baz", "42" }, "--foo", 0);
		getArgument(new String[] { "--foo", "--bar", "--baz", "42" }, "--bar", 0);
	}

}
