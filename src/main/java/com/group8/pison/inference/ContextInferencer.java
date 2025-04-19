package com.group8.pison.inference;

/**
 * Tries to infer if a chunk head starts inside a JSON string.
 */
public class ContextInferencer {
    public enum Status { IN, OUT, UNKNOWN }

    private static final int K = 64;  // bytes to scan

    /**
     * Hypothesis: parse first K bytes as if inside or outside a string;
     * if tokenization fails, contradiction obtained.
     */
    public static Status infer(byte[] chunk, int offset) {
        byte[] head = new byte[Math.min(K, chunk.length - offset)];
        System.arraycopy(chunk, offset, head, 0, head.length);

        for (Status hypothesis : new Status[]{Status.IN, Status.OUT}) {
            try {
                tokenize(head, hypothesis);
                // no contradiction
                continue;
            } catch (TokenizationException e) {
                // contradiction: hypothesis false
                return opposite(hypothesis);
            }
        }
        return Status.UNKNOWN;
    }

    private static void tokenize(byte[] data, Status hyp) throws TokenizationException {
        // Simplified tokenization: attempts to recognize JSON tokens
        // Throws TokenizationException if invalid sequence encountered.
        int i = 0, depth = hyp == Status.IN ? 1 : 0;
        while (i < data.length) {
            byte b = data[i++];
            switch (b) {
                case '"':
                    // flip in-string state if not escaped
                    if (!isEscaped(data, i-1)) depth ^= 1;
                    break;
                case '{': case '[':
                    if (depth == 0) depth++;
                    break;
                case '}': case ']':
                    if (depth > 0) depth--;
                    break;
                default:
                    // validate primitives (e.g., digits, letters)
                    if (depth == 0 && !isValidOutside(b)) {
                        throw new TokenizationException();
                    }
            }
        }
        // if we never threw, tokenization passed
    }

    private static boolean isEscaped(byte[] data, int idx) {
        // count consecutive backslashes immediately before idx
        int cnt = 0;
        for (int j = idx-1; j >= 0 && data[j] == '\\'; j--) cnt++;
        return (cnt & 1) != 0;
    }

    private static boolean isValidOutside(byte b) {
        // allow whitespace, digits, letters (true/false/null), punct
        return (b >= '0' && b <= '9')
            || (b >= 'a' && b <= 'z')
            || (b == ' '||b=='\n'||b=='\r'||b=='\t')
            || b == ':' || b == ',' || b == '{' || b == '}' || b=='['||b==']';
    }

    private static Status opposite(Status s) {
        return s == Status.IN ? Status.OUT : Status.IN;
    }

    private static class TokenizationException extends Exception {}
}