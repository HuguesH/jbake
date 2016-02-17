package org.jbake.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.ElisionFilter;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;

/**
 * Created by hugues.hivert on 21/08/2015.
 */
public class FrenchDocAnalyzer extends StopwordAnalyzerBase{

  public static final CharArraySet DEFAULT_ARTICLES = CharArraySet.unmodifiableSet(new CharArraySet(Arrays
                                                        .asList(new String[]{"l", "m", "t", "qu", "n", "s", "j", "d",
      "c", "jusqu", "quoiqu", "lorsqu", "puisqu"        }), true));
  private final CharArraySet       excltable;

  public static CharArraySet getDefaultStopSet() {
    return FrenchDocAnalyzer.DefaultSetHolder.DEFAULT_STOP_SET;
  }

  public FrenchDocAnalyzer() {
    this(FrenchDocAnalyzer.DefaultSetHolder.DEFAULT_STOP_SET);
  }

  public FrenchDocAnalyzer(CharArraySet stopwords) {
    this(stopwords, CharArraySet.EMPTY_SET);
  }

  public FrenchDocAnalyzer(CharArraySet stopwords, CharArraySet stemExclutionSet) {
    super(stopwords);
    this.excltable = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclutionSet));
  }

  protected TokenStreamComponents createComponents(String fieldName) {
    Object source;
    if(this.getVersion().onOrAfter(Version.LUCENE_5_3_0)){
      source = new StandardTokenizer();
    }else{
      throw new RuntimeException("Bad lucene version please use version after " + Version.LUCENE_5_2_1);
    }

    StandardFilter result = new StandardFilter((TokenStream) source);
    ElisionFilter result2 = new ElisionFilter(result, DEFAULT_ARTICLES);
    LowerCaseFilter result3 = new LowerCaseFilter(result2);
    TokenStream result4 = new StopFilter(result3, this.stopwords);

    if(!this.excltable.isEmpty()) {
      result4 = new SetKeywordMarkerFilter(result4, this.excltable);
    }
    FrenchLightStemFilter result1 = new FrenchLightStemFilter(result4);

    return new TokenStreamComponents((Tokenizer) source, result1);
  }

  private static class DefaultSetHolder{

    static CharArraySet DEFAULT_STOP_SET;

    private DefaultSetHolder() {}

    static{
      try{

            CharArraySet docSet = WordlistLoader.getSnowballWordSet(IOUtils.getDecodingReader(SnowballFilter.class,
            "french_stop.txt", StandardCharsets.UTF_8));

        docSet.addAll(StopAnalyzer.ENGLISH_STOP_WORDS_SET);
        DEFAULT_STOP_SET = docSet;
      }catch(IOException var1){
        throw new RuntimeException("Unable to load default stopword set");
      }
    }
  }
}
