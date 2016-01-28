/*
 * Copyright 2015-2016 NETHead (NETHead@gmx.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tinymediamanager.scraper.aebn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.tinymediamanager.scraper.MediaGenres;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchOptions.SearchParam;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MediaType;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;

// TODO: Auto-generated Javadoc
/**
 * A test class for scraping aebn.net.
 *
 * @author NETHead (NETHead@gmx.net)
 * @version 0.3
 * @see AebnMetadataProvider
 *
 */
public class AebnMetadataProviderTest {

	/**
	 * Test search.
	 *
	 * @throws Exception
	 *             the exception
	 */
	@Test
	public void testSearch() throws Exception {
		try {
			IMovieMetadataProvider aebn = new AebnMetadataProvider();
			MediaSearchOptions options = new MediaSearchOptions(MediaType.MOVIE);

			options.set(SearchParam.QUERY, "Erotic Massage Stories 5");
			List<MediaSearchResult> results = aebn.search(options);
			assertThat(results.size()).isEqualTo(60);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			Assert.fail();
		}
	}


	/**
	 * Test scrape data.
	 *
	 * @throws Exception
	 *             the exception
	 */
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
			assertThat(md.getStringValue(MediaMetadata.PLOT)).isEqualTo(
					"Prepare for relaxation and satisfaction in 5 hot scenes that have been captured in the highest quality. Adriana Chechik, Emily Grey, Sabrina Banks, Sierra Nevadah, and Victoria Rae Black are ready for a special deep tissue massage to work out all of their tight little kinks. You'll definitely want a rub down after witnessing this set of steamy erotic massage stories.");
			assertThat(md.getStringValue(MediaMetadata.TAGLINE)).isEqualTo(
					"Prepare for relaxation and satisfaction in 5 hot scenes that have been captured in the highest quality.");
			assertThat(md.getStringValue(MediaMetadata.PRODUCTION_COMPANY)).isEqualTo("Pure Passion");
			assertThat(md.getStringValue(MediaMetadata.TMDB)).isEqualTo("");
			assertThat(md.getStringValue(MediaMetadata.IMDB)).isEqualTo("");
			assertThat(md.getStringValue(MediaMetadata.RUNTIME)).isEqualTo("144");
			assertThat(md.getGenres().size()).isEqualTo(7);
			assertThat(md.getGenres().contains(MediaGenres.EROTIC)).isTrue();
			assertThat(md.getStringValue(MediaMetadata.COLLECTION_NAME)).isEqualTo("Erotic Massage Stories");
			assertThat(md.getCastMembers().size()).isEqualTo(8); // Don't forget
																	// to
																	// include
																	// actor +
																	// director
																	// in this
																	// number!
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			Assert.fail();
		}
	}
}
