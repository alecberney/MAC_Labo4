package ch.heigvd.iict.mac.evaluation;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class Evaluation {
    private static void readFile(String filename, Function<String, Void> parseLine)
            throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(filename),
                        StandardCharsets.UTF_8)
        )) {
            String line = br.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    parseLine.apply(line);
                }
                line = br.readLine();
            }
        }
    }

    /*
     * Reading CACM queries and creating a list of queries.
     */
    private static List<String> readingQueries() throws IOException {
        final String QUERY_SEPARATOR = "\t";

        List<String> queries = new ArrayList<>();

        readFile("evaluation/query.txt", line -> {
            String[] query = line.split(QUERY_SEPARATOR);
            queries.add(query[1]);
            return null;
        });
        return queries;
    }

    /*
     * Reading stopwords
     */
    private static List<String> readingCommonWords() throws IOException {
        List<String> commonWords = new ArrayList<>();

        readFile("common_words.txt", line -> {
            commonWords.add(line);
            return null;
        });
        return commonWords;
    }


    /*
     * Reading CACM qrels and creating a map that contains list of relevant
     * documents per query.
     */
    private static Map<Integer, List<Integer>> readingQrels() throws IOException {
        final String QREL_SEPARATOR = ";";
        final String DOC_SEPARATOR = ",";

        Map<Integer, List<Integer>> qrels = new HashMap<>();

        readFile("evaluation/qrels.txt", line -> {
            String[] qrel = line.split(QREL_SEPARATOR);
            int query = Integer.parseInt(qrel[0]);

            List<Integer> docs = qrels.get(query);
            if (docs == null) {
                docs = new ArrayList<>();
            }

            String[] docsArray = qrel[1].split(DOC_SEPARATOR);
            for (String doc : docsArray) {
                docs.add(Integer.parseInt(doc));
            }

            qrels.put(query, docs);
            return null;
        });
        return qrels;
    }

    public static void main(String[] args) throws IOException {
        ///
        /// Reading queries and queries relations files
        ///
        List<String> queries = readingQueries();
        System.out.println("Number of queries: " + queries.size());

        Map<Integer, List<Integer>> qrels = readingQrels();
        System.out.println("Number of qrels: " + qrels.size());

        double avgQrels = 0.0;
        for (List<Integer> rels : qrels.values()) {
            avgQrels += rels.size();
        }
        avgQrels /= qrels.size();
        System.out.println("Average number of relevant docs per query: " + avgQrels);

        // TODO student: use this when doing the english analyzer + common words
        List<String> commonWords = readingCommonWords();

        ///
        ///  Part I - Create the analyzers
        ///
        // TODO student: replace the null values with the correct analyzers
        var analyzers = List.of(
              new NamedAnalyzer("Standard", new StandardAnalyzer()),
              new NamedAnalyzer("Whitespace", new WhitespaceAnalyzer()),
              new NamedAnalyzer("English", new EnglishAnalyzer()),
              new NamedAnalyzer("English with custom stopwords",
                      new EnglishAnalyzer(new CharArraySet(commonWords, true)))
        );

        for(NamedAnalyzer na : analyzers) {
            String analyzerName = na.getAnalyzerName();
            Analyzer analyzer = na.getAnalyzer();

            if (analyzer == null) {
                System.err.printf("The analyzer \"%s\" has not been implemented%n", analyzerName);
            } else {
                System.out.printf("%n=== Using analyzer: %s%n", analyzerName);

                ///
                ///  Part I - Create the index
                ///
                LabIndex labIndex = new LabIndex(analyzer);
                labIndex.index("documents/cacm.txt");
                evaluateMetrics(labIndex, queries, qrels);
            }
        }

    }

    private static void evaluateMetrics(LabIndex labIndex, List<String> queries, Map<Integer, List<Integer>> qrels) {
        ///
        ///  Part II and III:
        ///  Execute the queries and assess the performance of the
        ///  selected analyzer using performance metrics like F-measure,
        ///  precision, recall,...
        ///

        // TODO student
        //  compute the metrics asked in the instructions
        //  you may want to call these methods to get:
        //  -  The query results returned by Lucene i.e. computed/empirical
        //     documents retrieved
        //         List<Integer> queryResults = lab2Index.search(query);
        //  - The true query results from qrels file i.e. genuine documents
        //    returned matching a query
        //         List<Integer> qrelResults = qrels.get(queryNumber);

        int queryNumber = queries.size();
        int totalRelevantDocs = 0;
        int totalRetrievedDocs = 0;
        int totalRetrievedRelevantDocs = 0;
        double avgPrecision = 0.0;
        double avgRPrecision = 0.0;
        double avgRecall = 0.0;
        double meanAveragePrecision = 0.0;
        double fMeasure = 0.0;

        // Average precision at the 11 recall levels (0,0.1,0.2,...,1) over all queries
        double[] avgPrecisionAtRecallLevels = createZeroedRecalls();

        for (int i = 0; i < queries.size(); ++i) {

            int totalRetrievedRelevantDocsQuery = 0;
            int lastPosition = 0;
            double avgMeanPrecisionQuery = 0.0;
            double actualPrecisionQuery = 0.0;

            // Max Precision at the 11 recall levels (0,0.1,0.2,...,1) over all queries
            double[] precisionAtRecallLevels = createZeroedRecalls();

            // Récupère les documents touchés par la requête
            List<Integer> queryResult = labIndex.search(queries.get(i));

            // Récupère les documents attendus
            List<Integer> qrelResults = qrels.get(i+1);

            if (qrelResults != null) {
                // Vérifie pour chaque document trouvé s'il est correct
                for (int j = 0; j < queryResult.size(); ++j) {
                    if (qrelResults.contains(queryResult.get(j))) {
                        ++totalRetrievedRelevantDocsQuery;
                        avgMeanPrecisionQuery += getActualPrecision(totalRetrievedRelevantDocsQuery, j + 1);;
                    }

                    actualPrecisionQuery = getActualPrecision(totalRetrievedRelevantDocsQuery, j + 1);

                    fillPrecisionAtRecallLevels(
                            getActualRecall(totalRetrievedRelevantDocsQuery, qrelResults.size()),
                            actualPrecisionQuery,
                            precisionAtRecallLevels);

                    // Ajoute la Ranking Precision
                    if (j + 1 == qrelResults.size()) {
                        avgRPrecision += actualPrecisionQuery;
                    }
                }

                avgMeanPrecisionQuery /= qrelResults.size();
            }

            // Ajout aux valeurs globales
            // qrelResults.size() = Number of relevants documents
            // queryResult.size() = Number of retrieved documents
            totalRelevantDocs += qrelResults != null ? qrelResults.size() : 0;
            totalRetrievedDocs += queryResult.size();
            totalRetrievedRelevantDocs += totalRetrievedRelevantDocsQuery;
            avgPrecision += getActualPrecision(totalRetrievedRelevantDocsQuery, queryResult.size());
            meanAveragePrecision += avgMeanPrecisionQuery;
            avgRecall += qrelResults != null ? getActualRecall(totalRetrievedRelevantDocsQuery, qrelResults.size()) : 0;

            // Ajoute le max pour chaque level
            for (int u = 0; u < avgPrecisionAtRecallLevels.length; ++u) {
                avgPrecisionAtRecallLevels[u] += precisionAtRecallLevels[u];
            }
        }

        avgPrecision /= queryNumber;
        meanAveragePrecision /= queryNumber;
        avgRecall /= queryNumber;
        avgRPrecision /= queryNumber;

        // Calcul la moyenne pour chaque level
        for (int i = 0; i < avgPrecisionAtRecallLevels.length; ++i) {
            avgPrecisionAtRecallLevels[i] /= queryNumber;
        }

        fMeasure = (2 * avgRecall * avgPrecision) / (avgRecall + avgPrecision);

        ///
        ///  Part IV - Display the metrics
        ///

        // TODO student implement what is needed (i.e. the metrics) to be able
        //  to display the results
        displayMetrics(totalRetrievedDocs, totalRelevantDocs,
                totalRetrievedRelevantDocs, avgPrecision, avgRecall, fMeasure,
                meanAveragePrecision, avgRPrecision,
                avgPrecisionAtRecallLevels);
    }

    private static double getActualRecall(double actualRetrievedRelevantDocsQuery, double NumberOfRelevantsDocuments) {
        return actualRetrievedRelevantDocsQuery / NumberOfRelevantsDocuments;
    }

    private static double getActualPrecision(double actualRetrievedRelevantDocsQuery, double actualNumberOfDocuments) {
        return actualRetrievedRelevantDocsQuery / actualNumberOfDocuments;
    }

    private static void fillPrecisionAtRecallLevels(double actualRecall,
                                                    double actualPrecision,
                                                    double[] precisionAtRecallLevels) {
        for(int i = 0; i < precisionAtRecallLevels.length; ++i) {
            if (actualRecall * 10 >= i && actualPrecision > precisionAtRecallLevels[i]) {
                precisionAtRecallLevels[i] = actualPrecision;
            }
        }
    }

    private static void displayMetrics(
            int totalRetrievedDocs,
            int totalRelevantDocs,
            int totalRetrievedRelevantDocs,
            double avgPrecision,
            double avgRecall,
            double fMeasure,
            double meanAveragePrecision,
            double avgRPrecision,
            double[] avgPrecisionAtRecallLevels
    ) {
        System.out.println("Number of retrieved documents: " + totalRetrievedDocs);
        System.out.println("Number of relevant documents: " + totalRelevantDocs);
        System.out.println("Number of relevant documents retrieved: " + totalRetrievedRelevantDocs);

        System.out.println("Average precision: " + avgPrecision);
        System.out.println("Average recall: " + avgRecall);

        System.out.println("F-measure: " + fMeasure);

        System.out.println("MAP: " + meanAveragePrecision);

        System.out.println("Average R-Precision: " + avgRPrecision);

        System.out.println("Average precision at recall levels: ");
        for (int i = 0; i < avgPrecisionAtRecallLevels.length; i++) {
            System.out.printf("\t%s: %s%n", i, avgPrecisionAtRecallLevels[i]);
        }
    }

    private static double[] createZeroedRecalls() {
        double[] recalls = new double[11];
        Arrays.fill(recalls, 0.0);
        return recalls;
    }
}
