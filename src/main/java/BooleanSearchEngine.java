import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BooleanSearchEngine implements SearchEngine {
    private final TreeMap<String, List<PageEntry>> indexes;
    private final TreeSet<String> stopList;

    public BooleanSearchEngine(File pdfsDir) throws IOException {
        indexes = new TreeMap<>();
        stopList = new TreeSet<>();
        try (FileReader stopListFile = new FileReader("stop-ru.txt")) {
            BufferedReader stopReader = new BufferedReader(stopListFile);
            String stopWord;
            while ((stopWord = stopReader.readLine()) != null) {
                stopList.add(stopWord.toLowerCase());
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        List<File> filesInDir = null;
        try (Stream<Path> streamDir = Files.walk(pdfsDir.toPath())) {
            filesInDir = streamDir.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        for (File file : filesInDir) {
            var doc = new PdfDocument(new PdfReader(file));
            for (int pageNum = 1; pageNum < doc.getNumberOfPages() + 1; pageNum++) {
                String text = PdfTextExtractor.getTextFromPage(doc.getPage(pageNum));
                String[] words = text.split("\\P{IsAlphabetic}+");
                Map<String, Integer> counter = new HashMap<>();
                for (String word : words) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    word = word.toLowerCase();
                    counter.put(word, counter.getOrDefault(word, 0) + 1);
                }
                for (Map.Entry<String, Integer> entry : counter.entrySet()) {
                    if (!indexes.containsKey(entry.getKey())) {
                        List<PageEntry> pageEntries = new ArrayList<>();
                        pageEntries.add(new PageEntry(file.getName(), pageNum, entry.getValue()));
                        indexes.put(entry.getKey(), pageEntries);
                    } else {
                        List<PageEntry> list = indexes.get(entry.getKey());
                        list.add(new PageEntry(file.getName(), pageNum, entry.getValue()));
                        indexes.put(entry.getKey(), list);
                    }
                }
            }
        }
    }

    @Override
    public List<PageEntry> search(String word) {
        String[] request = word.split("\\P{IsAlphabetic}+");
        List<PageEntry> result = null;
        if (request.length == 1) {
            result = indexes.get(word.toLowerCase());
        } else {
            ArrayList<String> filteredRequest = new ArrayList<>();
            for (String entry : request) {
                if (!stopList.contains(entry)) {
                    filteredRequest.add(entry.toLowerCase());
                }
            }
            if (filteredRequest.size() == 1) {
                result = indexes.get(filteredRequest.get(0).toLowerCase());
            } else if (filteredRequest.size() > 1) {
                Map<Map.Entry<String, Integer>, Integer> counter = new HashMap<>();
                for (String filteredEntry : filteredRequest) {
                    List<PageEntry> wordResult = indexes.get(filteredEntry.toLowerCase());
                    for (PageEntry wordEntry : wordResult) {
                        if (wordEntry == null) {
                            continue;
                        }
                        Map.Entry<String, Integer> pdfFilePage;
                        pdfFilePage = new AbstractMap.SimpleEntry<>(wordEntry.getPdfName(), wordEntry.getPage());
                        counter.put(pdfFilePage, counter.getOrDefault(pdfFilePage, 0) + wordEntry.getCount());
                    }
                }
                result = new ArrayList<>();
                for (Map.Entry<Map.Entry<String, Integer>, Integer> filePage : counter.entrySet()) {
                    String pdfFileName = filePage.getKey().getKey();
                    int pageNum = filePage.getKey().getValue();
                    int count = filePage.getValue();
                    result.add(new PageEntry(pdfFileName, pageNum, count));
                }
            } else {
                result = indexes.get(word.toLowerCase());
            }
        }
        if (!(result == null)) {
            Collections.sort(result);
        }
        return result;
    }
}
