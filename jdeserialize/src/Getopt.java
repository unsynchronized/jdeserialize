package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Simple getopt()-like implementation.
 */
public class Getopt {
    private Map<String, Integer> options;
    private List<String> otherargs;
    private Map<String, String> descriptions;
    private Map<String, List<String>> optvals;

    public class OptionParseException extends Exception {
        public static final long serialVersionUID = 2898924890585885551L;
        public OptionParseException(String message) {
            super(message);
        }
    }

    /**
     * Gets the list arguments specified that were *not* options or their arguments, in
     * the order they were specified.
     * 
     * @return the list of non-option String arguments
     */
    public List<String> getOtherArguments() {
        return otherargs;
    }

    /**
     * Gets the set of all options specified, as well as the list of their arguments.
     *
     * @return a map of all options specified; values are lists of arguments
     */
    public Map<String, List<String>> getOptionValues() {
        return optvals;
    }

    /**
     * Constructor.  
     *
     * @param options Map of options to parse.  The key should be an option string (including
     * any initial dashes), and the value should be an Integer representing the number of
     * arguments to parse following the option.
     * @param descriptions Map of option descriptions.
     */
    public Getopt(Map<String, Integer> options, Map<String, String> descriptions) {
        this.options = options;
        this.descriptions = descriptions;
    }
    /**
     * Constructor.  
     */
    public Getopt() {
        this.options = new HashMap<String, Integer>();
        this.descriptions = new HashMap<String, String>();
    }

    /**
     * Determines whether or not the option was specified when the arguments were parsed.
     *
     * @return true iff the argument was specified (with the correct number of arguments).
     */
    public boolean hasOption(String opt) {
        return optvals.containsKey(opt);
    }

    /**
     * Gets the list of arguments for a given option, or null if the option wasn't
     * specified.
     * 
     * @param option the option 
     * @return the list of arguments for option
     */
    public List<String> getArguments(String option) {
        return optvals.get(option);
    }

    /**
     * Add an option to the internal set, including the number of arguments and the
     * description. 
     *
     * @param option option string, including any leading dashes
     * @param arguments number of arguments
     * @param description description of the option
     */
    public void addOption(String option, int arguments, String description) {
        options.put(option, arguments);
        descriptions.put(option, description);
    }

    /**
     * Do the parsing/validation.
     * @param args arguments to parse
     * @throws OptionParseException if a parse error occurs (the exception message will
     * have details)
     */
    public void parse(String[] args) throws OptionParseException {
        this.otherargs = new ArrayList<String>();
        this.optvals = new HashMap<String, List<String>>();

        for(int i = 0; i < args.length; i++) {
            if(optvals != null) {
                Integer count = options.get(args[i]);
                if(count != null) {
                    ArrayList<String> al = new ArrayList<String>(count.intValue());
                    for(int j = 0; j < count; j++) {
                        if(i+1+j >= args.length) {
                            throw new OptionParseException("expected " + count + " arguments after " + args[i]);
                        }
                        al.add(args[i+1+j]);
                    }
                    List<String> oldal = optvals.get(args[i]);
                    if(oldal == null) {
                        optvals.put(args[i], al);
                    } else {
                        oldal.addAll(al);
                    }
                    i += count;
                    continue;
                }
            }
            otherargs.add(args[i]);
        }
    }

    /**
     * Get a tabular description of all options and their descriptions, one per line.
     */
    public String getDescriptionString() {
        String linesep = System.getProperty("line.separator");
        StringBuffer sb = new StringBuffer();
        if(options != null && options.size() > 0) {
            sb.append("Options:").append(linesep);
            TreeSet<String> opts = new TreeSet<String>(this.options.keySet());
            for(String opt: opts) {
                sb.append("    ").append(opt);
                for(int i = 0; i < options.get(opt); i++) {
                    sb.append(" arg").append(i+1);
                }
                sb.append(": ").append(descriptions.get(opt)).append(linesep);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            HashMap<String, Integer> options = new HashMap<String, Integer>();
            Getopt go = new Getopt();
            go.addOption("-optzero", 0, "zero-arg constructor");
            go.addOption("-optone", 1, "one-arg constructor");
            go.addOption("-opttwo", 2, "two-arg constructor");
            go.parse(args);
            System.out.println(go.getDescriptionString());
            System.out.println("options:");
            Map<String, List<String>> optvals = go.getOptionValues();
            for(String opt: optvals.keySet()) {
                System.out.print("    " + opt);
                for(String optval: optvals.get(opt)) {
                    System.out.print(" " + optval);
                }
                System.out.println("");
            }
            System.out.println("");
            System.out.println("otherargs:");
            for(String arg: go.getOtherArguments()) {
                System.out.println("    " + arg);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
