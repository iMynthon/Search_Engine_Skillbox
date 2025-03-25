package searchengine.until;

import org.jsoup.Jsoup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnippetGenerator {

    public static String generatedSnippet(String query, String content) {

        String text = Jsoup.parse(content).text();

        String[] words = query.split("\\s+");

        int index = findFirstOccurrenceIndex(text, words);

        if (index == -1) {
            return text.length() > 100 ? text.substring(0, 100) + " " : text;
        }

        int snippetLength = query.length() + 100;
        int start = Math.max(0, index - 50);
        int end = Math.min(text.length(), start + snippetLength);

        return highlightWords(text.substring(start, end), words);
    }

    private static int findFirstOccurrenceIndex(String text, String[] words) {

        String regex = String.join("|", words);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        return matcher.find() ? matcher.start() : -1;
    }

    private static String highlightWords(String text, String[] words) {

        String regex = "(" + String.join("|", words) + ")";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, "<b>" + matcher.group() + "</b>");
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
