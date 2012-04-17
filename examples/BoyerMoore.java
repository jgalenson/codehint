import codehint.CodeHint;

// See http://en.wikipedia.org/wiki/Boyer%E2%80%93Moore_string_search_algorithm.
@SuppressWarnings("unused")
public class BoyerMoore {
    
    private static int[] makeCharTable(char[] needle) {
        final int ALPHABET_SIZE = 256;
        int[] table = new int[ALPHABET_SIZE];
        for (int i = 0; i < table.length; i++)
        	table[i] = needle.length;
        for (int i = 0; i < needle.length - 1; i++) {
        	char cur = needle[i];  // Can synthesize with depth=1.
        	int len = needle.length - (i + 1);  // Can synthesize with depth=2.
        	table[cur] = len;
        	System.out.println(i);
        }
        return table;
      }
    
    private static int[] makeOffsetTable(char[] needle) {
    	int needleLen = needle.length;
        int[] table = new int[needleLen];
        int lastPrefixPosition = needleLen;
        for (int i = needleLen - 1; i >= 0; i--) {
        	if (isPrefix(needle, i + 1))
        		lastPrefixPosition = i + 1;
        	int pos = needle.length - (i + 1);  // Can synthesize with depth=2.
        	int val = (lastPrefixPosition + needleLen) - (i + 1);  // Can synthesize with depth=2 and needleLen variable.
        	table[pos] = val;
        }
        for (int i = 0; i < needleLen - 1; i++) {
        	int slen = suffixLength(needle, i);  // Can synthesize with depth=1.
        	table[slen] = needleLen - 1 - i + slen;  // Should be able to synthesize with depth=2 and needleLen variable.
        }
        return table;
  	}
    
    /**
     * Is needle[p:end] a prefix of needle?
     */
    private static boolean isPrefix(char[] needle, int p) {
    	for (int i = p, j = 0; i < needle.length; ++i, ++j) {
    		if (needle[i] != needle[j])
    			return false;
    	}
    	return true;
    }
   
    /**
     * Returns the maximum length of the substring ends at p and is a suffix.
     */
    private static int suffixLength(char[] needle, int p) {
    	int len = 0;
    	for (int i = p, j = needle.length - 1; i >= 0 && needle[i] == needle[j]; --i, --j)
    		len += 1;
    	return len;
    }

	public static void main(String[] args) {
		//makeCharTable("ANPANMAN".toCharArray());
		makeOffsetTable("ANPANMAN".toCharArray());
	}

}
