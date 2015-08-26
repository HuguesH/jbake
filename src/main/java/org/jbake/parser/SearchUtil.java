package org.jbake.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jbake.app.ConfigUtil;
import org.jbake.app.DBUtil;
import org.jbake.model.DocumentTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/**
 * Created by hugues.hivert on 20/08/2015.
 */
public final class SearchUtil{


  private final static Logger LOGGER = LoggerFactory.getLogger(SearchUtil.class);

  private boolean             activate;

  public boolean isActivate() {
    return activate;
  }

  private static Class        analyzerClazz = StandardAnalyzer.class;

  private Analyzer            analyzer;

  private ODatabaseDocumentTx db;

  public SearchUtil(ODatabaseDocumentTx db, CompositeConfiguration config) {
    // Contrôle de lexistence de lucene dans le classpath.
    Class luceneClass = Analyzer.class;
    if(luceneClass != null){
      // ConfigurationAnalyzer
      try{
        Class configAnalyzerClazz = Class.forName(config.getString(ConfigUtil.Keys.CLASS_LUCENEANALYZER));
        analyzer = (Analyzer) configAnalyzerClazz.newInstance();
        analyzerClazz = configAnalyzerClazz;


      }catch(Exception e){
        LOGGER.error("No good class lucene Analyzer for key " + ConfigUtil.Keys.CLASS_LUCENEANALYZER, e);
        LOGGER.warn("Use Lucene StandardAnalyzer ");
        analyzer = new StandardAnalyzer();
      }
      activate = true;
      this.db = db;
    }else{
      LOGGER.debug("No lucene classloader dependency found ");
      activate = false;
    }

  }


  public List<String> tokenizeString(String string) {
    if(activate){

      List<String> result = new ArrayList<String>();
      try{
        TokenStream stream = analyzer.tokenStream(null, new StringReader(string));
        stream.reset();
        while(stream.incrementToken()){
          result.add(stream.getAttribute(CharTermAttribute.class).toString());
        }
        // New instance of the lucene Analyzer, for each bean the Strategy could be different.
        analyzer = (Analyzer) analyzerClazz.newInstance();

      }catch(IOException e){
        // not thrown b/c we're using a string reader...
        throw new RuntimeException(e);
      }catch(InstantiationException e){
        e.printStackTrace();
      }catch(IllegalAccessException e){
        e.printStackTrace();
      }
      return result;
    }else{
      LOGGER.warn("TokensAnalyzer not activated");
      return null;
    }
  }


  public void tokenizerPublishDocument() {
    if(isActivate()){
      List<ODocument> publishedContent = new ArrayList<ODocument>();
      String[] documentTypes = DocumentTypes.getDocumentTypes();
      for(String docType : documentTypes){
        List<ODocument> query = db.query(new OSQLSynchQuery<ODocument>("select * from " + docType
            + " where status='published' order by date desc"));
        publishedContent.addAll(query);
      }

      for(ODocument document : publishedContent){
        Object[] args = new Object[3];
        Map<String, Object> mDoc = DBUtil.documentToModel(document);
        //TODO remplacer par l'ensemble du contenu, avec un lecteur HMTML.
        document.field("tokens", tokenizeString((String) document.field("summary")));
        document.field("tokenstitle", tokenizeString((String) document.field("title")));
        document.save();
      }
    }

  }
}
