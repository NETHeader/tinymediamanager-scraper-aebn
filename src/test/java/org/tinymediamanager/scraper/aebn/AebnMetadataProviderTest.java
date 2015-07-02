package org.tinymediamanager.scraper.aebn;

import org.junit.Assert;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.tinymediamanager.scraper.*;
import org.tinymediamanager.scraper.MediaSearchOptions.SearchParam;

/**
 * A test class for scraping aebn.net.
 *
 * @author NETHead <NETHead@gmx.net>
 * @version 0.1
 * @see AebnMetadataProvider
 *
 */
public class AebnMetadataProviderTest {

@Test
public void testSearch() throws Exception {
    try {
        IMovieMetadataProvider aebn = new AebnMetadataProvider();
        MediaSearchOptions options = new MediaSearchOptions(MediaType.MOVIE);

        options.set(SearchParam.QUERY, "Erotic Massage Stories 5");
        List<MediaSearchResult> results = aebn.search(options);
        assertThat(results.size()).isEqualTo(60);
    }
    catch (Exception e) {
    	System.err.println(e.getMessage());
        e.printStackTrace();
        Assert.fail();
    }
}

@Test
public void testScrapeData() throws Exception {
    try {
      IMovieMetadataProvider aebn = new AebnMetadataProvider();
      MediaScrapeOptions options = new MediaScrapeOptions(MediaType.MOVIE);

      options.setId("AebnID", "183071");
      MediaMetadata md = aebn.getMetadata(options);

      assertThat(md).isNotNull();
      assertThat(md.getStringValue(MediaMetadata.TITLE)).isEqualTo("Erotic Massage Stories 5");
      assertThat(md.getStringValue(MediaMetadata.YEAR)).isEqualTo("2014");
      assertThat(md.getStringValue(MediaMetadata.COLLECTION_NAME)).isEqualTo("Erotic Massage Stories");
      assertThat(md.getStringValue(MediaMetadata.PLOT)).isEqualTo("Prepare for relaxation and satisfaction in 5 hot scenes that have been captured in the highest quality. Adriana Chechik, Emily Grey, Sabrina Banks, Sierra Nevadah, and Victoria Rae Black are ready for a special deep tissue massage to work out all of their tight little kinks. You'll definitely want a rub down after witnessing this set of steamy erotic massage stories.");
      assertThat(md.getStringValue(MediaMetadata.TAGLINE)).isEqualTo("Prepare for relaxation and satisfaction in 5 hot scenes that have been captured in the highest quality.");
      assertThat(md.getStringValue(MediaMetadata.PRODUCTION_COMPANY)).isEqualTo("Pure Passion");
      assertThat(md.getStringValue(MediaMetadata.TMDBID)).isEqualTo("");
      assertThat(md.getStringValue(MediaMetadata.IMDBID)).isEqualTo("");
      assertThat(md.getStringValue(MediaMetadata.RUNTIME)).isEqualTo("145");
      assertThat(md.getGenres().size()).isEqualTo(7);
      assertThat(md.getGenres().contains(MediaGenres.EROTIC)).isTrue();
      assertThat(md.getStringValue(MediaMetadata.COLLECTION_NAME)).isEqualTo("Erotic Massage Stories");
      assertThat(md.getCastMembers().size()).isEqualTo(8);	// Don't forget to add actor + director to this number!
    }
    catch (Exception e) {
    	System.err.println(e.getMessage());
        e.printStackTrace();
        Assert.fail();
    }
}
}
