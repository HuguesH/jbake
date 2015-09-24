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

    private ODatabaseDocumentTx db;

    private Map<String, LinkedHashSet<Integer>> allWords = new HashMap<String, LinkedHashSet<Integer>>();

    private LinkedHashSet<String> tabUri = new LinkedHashSet<String>();

    public SearchUtil(ODatabaseDocumentTx db, CompositeConfiguration config) {
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

    public static void updateSchema(final ODatabaseDocumentTx db) {
        OSchema schema = db.getMetadata().getSchema();
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
        LinkedHashSet<Integer> listId;
        if (!allWords.containsKey(word)) {
            listId = new LinkedHashSet<Integer>();
            allWords.put(word, listId);
        } else {
            listId = allWords.get(word);
        }
        listId.add(indexDocId);
    }


    public void tokenizerPublishDocument() {
        if (activate) {
            List<ODocument> publishedContent = new ArrayList<ODocument>();


            String[] documentTypes = DocumentTypes.getDocumentTypes();
            for (String docType : documentTypes) {
                List<ODocument> query = db.query(new OSQLSynchQuery<ODocument>("select * from " + docType
                        + " where status='published' order by date desc"));
                publishedContent.addAll(query);
            }
            int documentIndexId = 0;
            for (ODocument document : publishedContent) {
                tabUri.add((String) document.field("uri"));
                //Work on tags words
                String[] tags = DBUtil.toStringArray(document.field("tags"));
                if (tags != null) {
                    for (String tag : tags) {
                        addWord(tag, documentIndexId);
                    }
                }
                //Work on title words
                List<String> tokenstitle = tokenizeString((String) document.field("title"));
                for (String newWord : tokenstitle) {
                    addWord(newWord, documentIndexId);
                }

                //With HTML reader all Lightweight markup language
                Document docHtml = Jsoup.parseBodyFragment((String) document.field("body"));
                List<String> tokensbody = tokenizeString(docHtml.body().text());
                for (String newWord : tokensbody) {
                    addWord(newWord, documentIndexId);
                }

                documentIndexId++;


            }

            //SAuvegarde les mots

            for (String word : allWords.keySet()) {
                ODocument oWord = new ODocument("words");
                oWord.field("word", word);
                oWord.field("indexId", allWords.get(word));
                oWord.save();
            }

            ODocument oIndexUris = new ODocument("words");
            oIndexUris.field("word", "_SEARCH_INDEX_URI_");
            oIndexUris.field("indexId", tabUri);
            oIndexUris.save();


            LOGGER.debug("Created index of   " + allWords.size());
        }

    }

    public static Map<String, Object> wrapWordsValues(Iterator<ODocument> docs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        while (docs.hasNext()) {
            ODocument next = docs.next();
            String word = next.field("word");
            if (!"_SEARCH_INDEX_URI_".equals(word)) {
                result.put((String) next.field("word"),  next.field("indexId"));
            }
        }
        return result;
    }


    public static String searchTokensToJSon(List<ODocument> docs) {
        ODocument indexUri = docs.remove(docs.size() - 1);
        Set<String> setUri = indexUri.field("indexId");
        List<String> listeUri = new ArrayList<String>();
        for (String uri : setUri){
            listeUri.add(uri);
        }
        StringBuilder resultBuilder = new StringBuilder();
        Map<String, Object> indexAndWords = wrapWordsValues(docs.iterator());
        indexAndWords.put((String)indexUri.field("word"), listeUri );
        resultBuilder.append(JSONValue.toJSONString(listeUri)).append(",");
        resultBuilder.append(JSONValue.toJSONString(wrapWordsValues(docs.iterator())));

        return JSONValue.toJSONString(indexAndWords);

    }


}
