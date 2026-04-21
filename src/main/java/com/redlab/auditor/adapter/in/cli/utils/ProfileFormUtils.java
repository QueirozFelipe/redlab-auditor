package com.redlab.auditor.adapter.in.cli.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileFormUtils {

    private Scanner scanner;

    public ProfileFormUtils() {
        this.scanner = new Scanner(System.in);
    }

    public String askUniqueName(String label, Set<String> existingNames) {
        while (true) {
            String name = askRequired(label, null);
            if (!existingNames.contains(name)) {
                return name;
            }
            System.out.println("[INVALID] A profile named '" + name + "' already exists. Please choose another name.");
        }
    }

    public String askRequired(String label, String currentValue) {
        while (true) {
            String suffix = (currentValue != null && !currentValue.isBlank()) ? " [" + currentValue + "]" : "";
            System.out.print(label + suffix + ": ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty() && currentValue != null) return currentValue;
            if (!input.isEmpty()) return input;

            System.out.println("[INVALID] " + label + " cannot be empty.");
        }
    }

    public String askUrl(String label, String currentValue) {
        while (true) {
            String input = askRequired(label, currentValue);
            try {
                new URL(input);
                return input;
            } catch (MalformedURLException e) {
                System.out.println("[INVALID] Please enter a valid URL (e.g., https://projectmanager.company.com)");
            }
        }
    }

    public String askRegex(String label, String currentValue) {
        while (true) {
            System.out.print(label + (currentValue != null ? " [" + currentValue + "]" : "") + ": ");
            String input = scanner.nextLine().trim();
            String val = input.isEmpty() ? currentValue : input;

            if (val == null || val.isEmpty()) return "";
            try {
                Pattern.compile(val);
                return val;
            } catch (PatternSyntaxException e) {
                System.out.println("[INVALID] Invalid Regex pattern: " + e.getDescription());
            }
        }
    }

    public <T> T askEnum(String label, Function<Integer, T> resolver) {
        while (true) {
            try {
                System.out.print(label);
                int option = Integer.parseInt(scanner.nextLine());
                T result = resolver.apply(option);
                if (result != null) return result;
                System.out.println("[INVALID] Option not recognized.");
            } catch (NumberFormatException e) {
                System.out.println("[INVALID] Please select a valid numeric option.");
            } catch (Exception e) {
                System.out.println("[INVALID] An error occurred. Please try again.");
            }
        }
    }

    public <T> T askEnumWithDefault(String label, T currentValue, Function<Integer, T> resolver) {
        while (true) {
            try {
                String suffix = (currentValue != null) ? " [" + currentValue.toString() + "]" : "";
                System.out.print(label + suffix + ": ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty() && currentValue != null) {
                    return currentValue;
                }
                int option = Integer.parseInt(input);
                T result = resolver.apply(option);
                if (result != null) return result;
                System.out.println("[INVALID] Option not recognized.");
            } catch (NumberFormatException e) {
                System.out.println("[INVALID] Please select a valid numeric option or press Enter to keep current.");
            } catch (Exception e) {
                System.out.println("[INVALID] An error occurred. Please try again.");
            }
        }
    }

    public <T> Set<T> parseSet(String label, String currentValues, Function<String, T> mapper) {
        return parseCollection(label, currentValues, mapper, Collectors.toSet());
    }

    public <T> List<T> parseList(String label, String currentValues, Function<String, T> mapper) {
        return parseCollection(label, currentValues, mapper, Collectors.toList());
    }

    private <T, C extends Collection<T>> C parseCollection(
            String label,
            String currentValues,
            Function<String, T> mapper,
            Collector<T, ?, C> collector) {

        while (true) {
            String suffix = (currentValues != null && !currentValues.isEmpty()) ? " [" + currentValues + "]" : "";
            System.out.print(label + suffix + ": ");

            String input = scanner.nextLine().trim();
            String finalInput = input.isEmpty() ? currentValues : input;

            if (finalInput == null || finalInput.isBlank()) {
                return Stream.<T>empty().collect(collector);
            }

            try {
                return Arrays.stream(finalInput.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(mapper)
                        .collect(collector);
            } catch (Exception e) {
                System.out.println("[INVALID] One or more values in '" + label + "' are invalid. Please try again.");
            }
        }
    }

    public int askInt(String label, int defaultValue) {
        while (true) {
            System.out.print(label + " [" + defaultValue + "]: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return defaultValue;
            try {
                int value = Integer.parseInt(input);
                if (value > 0) return value;
                System.out.println("[INVALID] Value must be greater than zero.");
            } catch (NumberFormatException e) {
                System.out.println("[INVALID] Please enter a valid integer.");
            }
        }
    }

    public String askToken(String label, String currentToken) {
        String masked = mask(currentToken);
        System.out.print(label + " [" + masked + "]: ");
        String input = scanner.nextLine().trim();
        return input.isBlank() ? currentToken : input;
    }

    public String mask(String token) {
        if (token == null || token.isBlank()) return "NOT SET";
        if (token.length() < 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    public String toCommaString(Collection<?> collection) {
        if (collection == null || collection.isEmpty()) return "";
        return collection.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

}
