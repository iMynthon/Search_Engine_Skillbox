package searchengine.until;

import org.jsoup.Jsoup;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnippetGenerator {

    private static final int CONTEXT_RADIUS = 200;

    public static String generatedSnippet(String query, String content) {

        String text = Jsoup.parse(content).text();

        String[] words = query.split("\\s+");

        int phraseIndex = findPhraseFind(text,query);

        int index = phraseIndex != -1 ? phraseIndex : findFirstOccurrenceIndex(text, words);

        if (index == -1) {
            return text.length() > 100 ? text.substring(0, 300) : text;
        }

        int start = index - 50 <= 0 ? 0 : startSnippet(text,index);
        int end = index + CONTEXT_RADIUS > text.length() ? text.length() : endSnippet(text,index);

        return highlightWords(text.substring(start, end), words);
    }

    private static int startSnippet(String text,int index){
        for(int i = index;i > 0;i--){
            if(Character.isUpperCase(text.charAt(i)) || text.charAt(i - 1) == '.'
                    || text.charAt(i - 1) == '!' || text.charAt(i - 1) == '?'){
                return i;
            }
        }
        return index;
    }

    private static int endSnippet(String text,int index){
        for(int i = index + CONTEXT_RADIUS;i < text.length();i++){
            if(Character.isWhitespace(text.charAt(i)) || text.charAt(i - 1) == '.'
                    || text.charAt(i - 1) == '!' || text.charAt(i - 1) == '?'){
                return i - 1;
            }
        }
        return text.length();
    }

    private static int findPhraseFind(String text,String phrase){
        Pattern pattern = Pattern.compile(phrase);
        Matcher matcher = pattern.matcher(text);

        return matcher.find() ? matcher.start() : -1;
    }

    private static int findFirstOccurrenceIndex(String text, String[] words) {

       return Arrays.stream(words).map(word-> {
           Pattern pattern = Pattern.compile(word,Pattern.CASE_INSENSITIVE);
           Matcher matcher = pattern.matcher(text);
           return matcher.find() ? matcher.start() : - 1;
       }).filter(index -> index != -1)
               .findFirst()
               .orElse(-1);
    }

    private static String highlightWords(String text, String[] words) {

        String regex = "(" + String.join("|", words) + ")";
        Pattern pattern = Pattern.compile(regex,Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, "<b>" + matcher.group() + "</b>");
        }
        matcher.appendTail(result);
        char lastChar = result.charAt(result.length() - 1);
        if (lastChar != '.' && lastChar != '!' && lastChar != '?') {
            result.append("...");
        }
        return result.toString();
    }
}
