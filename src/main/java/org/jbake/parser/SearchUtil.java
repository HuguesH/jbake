package org.jbake.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import org.apache.commons.configuration.CompositeConfiguration;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jbake.app.ConfigUtil;
import org.jbake.app.ContentStore;
import org.jbake.app.DBUtil;
import org.jbake.model.DocumentTypes;
import org.json.simple.JSONValue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/**
 * Created by hugues.hivert on 20/08/2015.
 */
public final class SearchUtil {


    private final static Logger LOGGER = LoggerFactory.getLogger(SearchUtil.class);

    private boolean activate;

    public boolean isActivate() {
        return activate;
    }

    private static Class analyzerClazz = StandardAnalyzer.class;

    private Analyzer analyzer;

    private ContentStore db;

    private Map<String, LinkedHashSet<Integer>> allWords = new HashMap<String, LinkedHashSet<Integer>>();

    private static List<String> tabUri = new ArrayList<String>();

    private static final String INDEX_URI = "_SEARCH_INDEX_URI_";

    public SearchUtil(ContentStore db, CompositeConfiguration config) {
        // Contrôle de lexistence de lucene dans le classpath.
        Class luceneClass = Analyzer.class;
        if (luceneClass != null) {
            // ConfigurationAnalyzer
            try {
                Class configAnalyzerClazz = Class.forName(config.getString(ConfigUtil.Keys.CLASS_LUCENEANALYZER));
                analyzer = (Analyzer) configAnalyzerClazz.newInstance();
                analyzerClazz = configAnalyzerClazz;


            } catch (Exception e) {
                LOGGER.error("No good class lucene Analyzer for key " + ConfigUtil.Keys.CLASS_LUCENEANALYZER, e);
                LOGGER.warn("Use Lucene StandardAnalyzer ");
                analyzer = new StandardAnalyzer();
            }
            activate = true;
            updateSchema(db);
            this.db = db;
        } else {
            LOGGER.debug("No lucene classloader dependency found ");
            activate = false;
        }

    }

    public static void updateSchema(final ContentStore db) {
        OSchema schema = db.getSchema();
        if (schema.getClass("words") == null) {
            // create the sha1 signatures class
            OClass dico = schema.createClass("word");
            dico.createProperty("word", OType.STRING).setNotNull(true);
            dico.createProperty("indexId", OType.EMBEDDEDSET).setNotNull(true);
        }
    }


    public List<String> tokenizeString(String string) {
        if (activate) {

            List<String> result = new ArrayList<String>();
            try {
                TokenStream stream = analyzer.tokenStream(null, new StringReader(string));
                stream.reset();
                while (stream.incrementToken()) {
                    result.add(stream.getAttribute(CharTermAttribute.class).toString());
                }
                // New instance of the lucene Analyzer, for each bean the Strategy could be different.
                analyzer = (Analyzer) analyzerClazz.newInstance();

            } catch (IOException e) {
                // not thrown b/c we're using a string reader...
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return result;
        } else {
            LOGGER.warn("TokensAnalyzer not activated");
            return null;
        }
    }


    private void addWord(String word, int indexDocId) {
        if (word.length() > 2) {
            LinkedHashSet<Integer> listId;
            if (!allWords.containsKey(word)) {
                listId = new LinkedHashSet<Integer>();
                allWords.put(word, listId);
            } else {
                listId = allWords.get(word);
            }
            listId.add(indexDocId);
        }
    }


    public void tokenizerPublishDocuments() {
        if (activate) {
            List<ODocument> publishedContent = new ArrayList<ODocument>();
            String[] documentTypes = DocumentTypes.getDocumentTypes();
            for (String docType : documentTypes) {
                List<ODocument> query = db.query("select * from " + docType + " where status='published' order by date desc");
                publishedContent.addAll(query);
            }

            extractWordsFrom(publishedContent);

            dbSaveWords();

            LOGGER.debug("Created index of   " + allWords.size());
        }

    }

    private void extractWordsFrom(List<ODocument> publishedContent) {
        int documentIndexId = 0;
        for (ODocument document : publishedContent) {
            String docURI =  document.field("uri");

            LOGGER.debug("Work on {} for indexId : {}", docURI, documentIndexId);
            tabUri.add(docURI);
            //Work on tags words
            String[] tags = DBUtil.toStringArray(document.field("tags"));
            if (tags != null) {
                for (String tag : tags) {
                    addWord(tag, documentIndexId);
                }
            }
            //Work on title words
            List<String> tokenstitle = tokenizeString((String) document.field("title"));
            addWords(documentIndexId, tokenstitle);

            //With HTML reader all Lightweight markup language
            Document docHtml = Jsoup.parseBodyFragment((String) document.field("body"));
            List<String> tokensbody = tokenizeString(docHtml.body().text());
            addWords(documentIndexId, tokensbody);

            documentIndexId++;


        }
    }

    private void addWords(int documentIndexId, List<String> tokenstitle) {
        for (String newWord : tokenstitle) {
            addWord(newWord, documentIndexId);
        }
    }

    private void dbSaveWords() {
        for (String word : allWords.keySet()) {
            ODocument oWord = new ODocument("words");
            oWord.field("word", word);
            oWord.field("indexId", allWords.get(word));
            oWord.save();
        }
    }

    public static Map<String, Object> wrapWordsValues(Iterator<ODocument> docs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        while (docs.hasNext()) {
            ODocument next = docs.next();
            result.put((String) next.field("word"), next.field("indexId"));
        }

        result.put(INDEX_URI, tabUri);
        return result;
    }


    public static String searchTokensToJSon(List<ODocument> docs) {
        Map<String, Object> indexAndWords = wrapWordsValues(docs.iterator());
        return JSONValue.toJSONString(indexAndWords);

    }


}
