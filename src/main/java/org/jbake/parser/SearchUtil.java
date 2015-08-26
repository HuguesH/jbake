package org.jbake.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jbake.app.ConfigUtil;
import org.jbake.model.DocumentTypes;
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
            this.db = db;
        } else {
            LOGGER.debug("No lucene classloader dependency found ");
            activate = false;
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


    public void tokenizerPublishDocument() {
        if (activate) {
            List<ODocument> publishedContent = new ArrayList<ODocument>();
            Set<String> dico = new HashSet<String>();
            String[] documentTypes = DocumentTypes.getDocumentTypes();
            for (String docType : documentTypes) {
                List<ODocument> query = db.query(new OSQLSynchQuery<ODocument>("select * from " + docType
                        + " where status='published' order by date desc"));
                publishedContent.addAll(query);
            }

            for (ODocument document : publishedContent) {
                Document docHtml = Jsoup.parseBodyFragment((String) document.field("body"));
                List<String> tokensbody = tokenizeString(docHtml.body().text());
                List<String> tokenstitle = tokenizeString((String) document.field("title"));

                document.field("tokensbody", tokensbody);
                document.field("tokenstitle", tokenstitle);
                document.save();

                String[] tags = document.field("tags");
                if (tags != null) {
                    for (String tag : tags) {
                        dico.add(tag);
                    }
                }
                dico.addAll(tokenstitle);

            }

            LOGGER.debug("Created dictionnary with words count : " + dico.size());
            LOGGER.debug("Created dictionnary  " + dico.toString());
        }

    }
}
