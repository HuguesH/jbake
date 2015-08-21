package org.jbake.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jbake.app.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by hugues.hivert on 20/08/2015.
 */
public final class SearchUtil{


  private final static Logger LOGGER = LoggerFactory.getLogger(SearchUtil.class);

  private boolean             activate;

  public boolean isActivate() {
    return activate;
  }

  private Analyzer analyzer;

  public SearchUtil(String luceneAnalyzerConfig) {
    // Contrôle de lexistence de lucene dans le classpath.
    Class luceneClass = Analyzer.class;
    if(luceneClass != null){
      // ConfigurationAnalyzer
      try{
        Class luceneAnlyzer = Class.forName(luceneAnalyzerConfig);
        analyzer = (Analyzer) luceneAnlyzer.newInstance();

      }catch(Exception e){
        LOGGER.error("No good class lucene Analyzer for key " + ConfigUtil.Keys.CLASS_LUCENEANALYZER, e);
        LOGGER.warn("Use Lucene StandardAnalyzer ");
        analyzer = new StandardAnalyzer();
      }
      activate = true;
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
      }catch(IOException e){
        // not thrown b/c we're using a string reader...
        throw new RuntimeException(e);
      }
      return result;
    }else{
      LOGGER.warn("TokensAnalyzer not activated");
      return null;
    }
  }


}
