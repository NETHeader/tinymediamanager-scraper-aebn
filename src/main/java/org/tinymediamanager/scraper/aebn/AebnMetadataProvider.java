/*
 * Copyright 2015-206 NETHead <NETHead@gmx.net>
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

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.Certification;
import org.tinymediamanager.scraper.MediaArtwork;
import org.tinymediamanager.scraper.MediaArtwork.FanartSizes;
import org.tinymediamanager.scraper.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.MediaCastMember;
import org.tinymediamanager.scraper.MediaCastMember.CastType;
import org.tinymediamanager.scraper.MediaGenres;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MediaType;
import org.tinymediamanager.scraper.UnsupportedMediaTypeException;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.mediaprovider.IMediaArtworkProvider;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;

import net.xeoh.plugins.base.annotations.PluginImplementation;

/**
 * A meta data provider class for scraping aebn.net.
 *
 * Implements scraping of meta data, artwork and trailers.
 *
 * @author NETHead <NETHead@gmx.net>
 * @version 0.3
 * @see IMediaMetadataProvider
 * @see IMediaArtworkProvider
 *
 */
@PluginImplementation
public class AebnMetadataProvider implements IMovieMetadataProvider, IMediaArtworkProvider {

	private static AebnMetadataProvider instance;
	private static MediaProviderInfo providerInfo = createMediaProviderInfo();
	private static final Logger LOGGER = LoggerFactory.getLogger(AebnMetadataProvider.class);
	private static final String AEBNID = "aebn";
	private static final String BASE_DATAURL = "http://theater.aebn.net";
	private static final String BASE_IMGURL = "http://pic.aebn.net";
	private static final Integer SEARCH_COUNT = 60;


	public static synchronized AebnMetadataProvider getInstance() {
		if (instance == null) {
			instance = new AebnMetadataProvider();
		}
		return instance;
	}


	public AebnMetadataProvider() {
		// configure/load settings
		// providerInfo.getConfig().addBoolean("useTmdb", false);
		// providerInfo.getConfig().addBoolean("scrapeCollectionInfo", false);

		// providerInfo.getConfig().load();
	}


	@Override
	public MediaProviderInfo getProviderInfo() {
		return providerInfo;
	}


	private static MediaProviderInfo createMediaProviderInfo() {
		MediaProviderInfo providerInfo = new MediaProviderInfo(AEBNID, "aebn.net",
				"<html><h3>Adult Entertainment Broadcast Network</h3><br />An adult movie database.<br />"
						+ "This scraper is able to scrape metadata and artwork.</html>",
				AebnMetadataProvider.class.getResource("/aebn_net.png"));
		providerInfo.setVersion(AebnMetadataProvider.class);
		return providerInfo;
	}


	/**
	 * Search at aebn.net
	 *
	 */
	@Override
	public List<MediaSearchResult> search(MediaSearchOptions query) throws Exception {
		LOGGER.debug("AEBN: search() {}", query);

		// check type
		if (query.getMediaType() == MediaType.MOVIE) {
			return searchMovies(query);
		}

		if (query.getMediaType() == MediaType.MOVIE_SET) {
			return searchMovies(query);
		}

		throw new UnsupportedMediaTypeException(query.getMediaType());
	}


	public List<MediaSearchResult> searchMovies(MediaSearchOptions query) throws Exception {

		LOGGER.debug("AEBN: searchMovies() {}", query);
		List<MediaSearchResult> resultList = new ArrayList<MediaSearchResult>();
		Elements movies = null;
		String searchString = "";

		// get search string from query
		if (StringUtils.isNotEmpty(query.get(MediaSearchOptions.SearchParam.QUERY))) {
			searchString = query.get(MediaSearchOptions.SearchParam.QUERY);
		}
		if (StringUtils.isEmpty(searchString)) {
			LOGGER.debug("AEBN: empty searchString");
			return resultList;
		}

		// sanitize search string
		searchString = MetadataUtil.removeNonSearchCharacters(searchString);

		// concatenate search query url
		String searchUrl = BASE_DATAURL + "/dispatcher/fts?userQuery="
				+ URLEncoder.encode(cleanSearchQuery(searchString), "UTF-8")
				+ "&targetSearchMode=basic&isAdvancedSearch=true&isFlushAdvancedSearchCriteria=false" + "&count="
				+ SEARCH_COUNT.toString() + "&imageType=Large&sortType=Relevance";

		// Search
		try {
			LOGGER.info("========= BEGIN AEBN Scraper Search for: {}", searchString);
			Url url = new Url(searchUrl);
			InputStream in = url.getInputStream();
			Document doc = Jsoup.parse(in, "UTF-8", "");
			in.close();

			// only look for movie links like
			// <a id="FTSMovieSearch_link_title_detail_30" ... </a>
			movies = doc.getElementsByAttributeValueMatching("id", "FTSMovieSearch_link_title_detail_\\d+");
			LOGGER.debug("AEBN: found {} search results", movies.size());
		} catch (Exception e) {
			LOGGER.error("AEBN: failed to search for {}: ", searchString, e);
		}

		if (movies == null || movies.isEmpty()) {
			LOGGER.debug("AEBN: no movie found");
			return resultList;
		}

		// there are search results, so fill media data structure
		HashSet<String> foundResultUrls = new HashSet<String>();
		for (Element anchor : movies) {
			try {
				String movieUrl = BASE_DATAURL + StrgUtils.substr(anchor.toString(), "href=\\\"(.*?)\\\"");
				String movieId = StrgUtils.substr(anchor.toString(), "movieId=(\\d+)");
				String movieName = StringEscapeUtils.unescapeHtml4(anchor.text());
				String posterUrl = BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + movieId + "_160w.jpg";
				LOGGER.debug("AEBN: found movie {} (id{})", movieName, movieId);

				// check if it is a valid AEBN id
				if (!isValidAebnId(Integer.parseInt(movieId))) {
					LOGGER.error("AEBN: id({}) is not a valid aebn id", movieId);
				}

				MediaSearchResult sr = new MediaSearchResult(providerInfo.getId());
				sr.setId(movieId);
				// sr.setIMDBId("");
				sr.setTitle(movieName);
				sr.setOriginalTitle(movieName);
				// sr.setYear not possible, no data at this point
				sr.setYear(null);
				sr.setMediaType(MediaType.MOVIE);
				sr.setUrl(movieUrl);
				sr.setPosterUrl(posterUrl);

				// compare score based on names
				float score = MetadataUtil.calculateScore(searchString, movieName);
				if (posterUrl.isEmpty() || posterUrl.contains("nopicture")) {
					LOGGER.debug("AEBN: no poster - downgrading score by 0.01");
					score = score - 0.01f;
				}
				sr.setScore(score);

				// check if result has at least a title and id
				if (StringUtils.isBlank(sr.getTitle()) || StringUtils.isBlank(sr.getId())) {
					LOGGER.warn("AEBN: no title nor id, skipping");
					continue;
				}

				// check if the movie has been already added to the search results
				if (foundResultUrls.contains(sr.getUrl())) {
					continue;
				}
				foundResultUrls.add(sr.getUrl());

				// populate extra arguments (deprecated)
				// MetadataUtil.copySearchQueryToSearchResult(query, sr);

				resultList.add(sr);
			} catch (Exception e) {
				LOGGER.warn("AEBN: error parsing search result: {}", e);
			}
		}

		Collections.sort(resultList);
		Collections.reverse(resultList);

		return resultList;
	}


	/**
	 * Get movie meta data from aebn.net.
	 *
	 */
	@Override
	public MediaMetadata getMetadata(MediaScrapeOptions options) throws Exception {
		LOGGER.debug("AEBN: getMetadata() {}", options);

		// check if there is already meta data present in the result
		if ((options.getResult() != null) && (options.getResult().getMediaMetadata() != null)) {
			LOGGER.debug("AEBN: return metadata from cache");
			return options.getResult().getMediaMetadata();
		}

		MediaMetadata md = new MediaMetadata(providerInfo.getId());
		Elements elements = null;
		Element element = null;
		Integer aebnId = 0;

		// get AebnId from previous search result
		if ((options.getResult() != null) && (options.getResult().getId() != null)) {
			aebnId = Integer.parseInt(options.getResult().getId());
			LOGGER.debug("AEBN: aebnId() from previous search result = {}", aebnId);
			// preset some values from search result (if there is one)
			// Use core.Utils.RemoveSortableName() if you want e.g. "Bourne Legacy, The" -> "The Bourne Legacy".
			md.storeMetadata(MediaMetadata.ORIGINAL_TITLE,
					StrgUtils.removeCommonSortableName(options.getResult().getOriginalTitle()));
			md.storeMetadata(MediaMetadata.TITLE, StrgUtils.removeCommonSortableName(options.getResult().getTitle()));
		}

		// or get AebnId from options
		if (!isValidAebnId(aebnId) && (options.getId(AEBNID) != null)) {
			LOGGER.debug("AEBN: aebnId() from options = {}", options.getId(AEBNID));
			aebnId = Integer.parseInt(options.getId(AEBNID));
		}

		if (!isValidAebnId(aebnId)) {
			LOGGER.warn("AEBN: no or incorrect aebnId, aborting");
			return md;
		}

		// ID
		md.setId(providerInfo.getId(), aebnId);
		LOGGER.debug("AEBN: aebnId({})", aebnId);

		// Base download url for data scraping
		String downloadUrl = BASE_DATAURL + "/dispatcher/movieDetail?movieId=" + aebnId;
		String locale = options.getLanguage().name();
		if (!StringUtils.isBlank(locale)) {
			downloadUrl = downloadUrl + "&locale=" + locale;
			LOGGER.debug("AEBN: used locale({})", locale);
		}

		// begin download and scrape
		try {
			LOGGER.debug("AEBN: download movie detail page");
			Url url = new Url(downloadUrl);
			InputStream in = url.getInputStream();
			Document document = Jsoup.parse(in, "UTF-8", "");
			in.close();

			// Title
			// <h1 itemprop="name" class="md-movieTitle" >Titelname</h1>
			LOGGER.debug("AEBN: parse title");
			elements = document.getElementsByAttributeValue("class", "md-movieTitle");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: {} elements found (should be one!)", elements.size());
				element = elements.first();
				String movieTitle = cleanString(element.text());
				LOGGER.debug("AEBN: title({})", movieTitle);
				md.storeMetadata(MediaMetadata.TITLE, movieTitle);
			}

			// Poster
			// front cover:
			// http://pic.aebn.net/Stream/Movie/Boxcovers/a66568_xlf.jpg
			String posterUrl = BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString() + "_xlf.jpg";
			md.storeMetadata(MediaMetadata.POSTER_URL, posterUrl);

			// Fanart/Background
			// Attention: this is non-standard, since TMM cannot get fanarts without a valid id
			// http://pic.aebn.net/Stream/Movie/Scenes/a113324_s534541.jpg
			// <img class="sceneThumbnail" alt="Scene Thumbnail" title="Scene Thumbnail" onError="..."
			// src="http://pic.aebn.net/Stream/Movie/Scenes/a113324_s534544.jpg" onclick="..." />
			LOGGER.debug("AEBN: parse fanart / scene thumbs");
			elements = document.getElementsByAttributeValue("class", "SceneThumbnail");
			LOGGER.debug("AEBN: {} elements found", elements.size());
			int i = 1;
			for (Element anchor : elements) {
				String backgroundUrl = anchor.attr("src");
				LOGGER.debug("AEBN: backgroundUrl{}({})", i, backgroundUrl);
				md.storeMetadata("backgroundUrl" + Integer.valueOf(i).toString(), backgroundUrl);
				i++;
			}

			// Runtime
			LOGGER.debug("AEBN: parse runtime");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=duration]");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: " + elements.size() + " elements found (should be one!)");
				element = elements.first();
				String movieRuntime = cleanString(element.attr("content"));
				movieRuntime = StrgUtils.substr(movieRuntime, "PT(\\d+)M");
				LOGGER.debug("AEBN: runtime({})", movieRuntime);
				md.storeMetadata(MediaMetadata.RUNTIME, movieRuntime);
			}

			// Year
			LOGGER.debug("AEBN: parse year");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=datePublished]");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: " + elements.size() + " elements found (should be one!)");
				element = elements.first();
				String movieYear = cleanString(element.attr("content"));
				movieYear = StrgUtils.substr(movieYear, "(\\d+)-");
				LOGGER.debug("AEBN: year({})", movieYear);
				md.storeMetadata(MediaMetadata.YEAR, movieYear);
			}

			// Series (Collection)
			LOGGER.debug("AEBN: parse collection");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[class=series]");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: {} elements found (should be one!)", elements.size());
				element = elements.first();
				String movieCollection = cleanString(element.text());

				// Fake a TMDB_SET based on the hash value of the collection name
				int movieCollectionHash = movieCollection.hashCode();

				md.storeMetadata(MediaMetadata.COLLECTION_NAME, movieCollection);
				md.storeMetadata(MediaMetadata.TMDB_SET, movieCollectionHash);
				LOGGER.debug("AEBN: collection({}), hashcode({})", movieCollection, movieCollectionHash);
			}

			// Studio
			LOGGER.debug("AEBN: parse studio");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=productionCompany]");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: {} elements found (should be one!)", elements.size());
				String movieStudio = cleanString(elements.first().text());
				LOGGER.debug("AEBN: studio({})", movieStudio);
				md.storeMetadata(MediaMetadata.PRODUCTION_COMPANY, movieStudio);
			}

			// Genre
			LOGGER.debug("AEBN: parse genre");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=genre]");
			for (Element g : elements) {
				md.addGenre(getTmmGenre(g.text()));
			}
			// add basic genre, since all genres at AEBN could be summarised
			// into this one
			md.addGenre(MediaGenres.EROTIC);

			// Certification
			// no data scrapeable---but obviously it's adult only, so simply
			// generate it
			String movieCertification = null;
			Certification certification = null;
			String country = options.getCountry().getAlpha2();
			LOGGER.debug("AEBN: generate certification for {}", country);
			// @formatter:off
            if (country.equals("DE")) {	movieCertification = "FSK 18"; }
            if (country.equals("US")) {	movieCertification = "NC-17"; }
            if (country.equals("GB")) {	movieCertification = "R18"; }
            if (country.equals("FR")) { movieCertification = "18"; }
            if (country.equals("ES")) { movieCertification = "PX"; }
            if (country.equals("JP")) { movieCertification = "R18+"; }
            if (country.equals("IT")) { movieCertification = "V.M.18"; }
            if (country.equals("NL")) {	movieCertification = "16"; }
            // @formatter:on
			certification = Certification.getCertification(options.getCountry(), movieCertification);
			if (certification != null) {
				LOGGER.debug("AEBN: certification({})", certification);
				md.addCertification(certification);
			}

			// Plot and Tagline
			LOGGER.debug("AEBN: parse plot");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=about]");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: {} elements found (should be one!)", elements.size());
				String moviePlot = cleanString(elements.first().text());
				md.storeMetadata(MediaMetadata.PLOT, moviePlot);
				// no separate tagline available, so extract the first sentence
				// from the movie plot
				String movieTagline = StrgUtils.substr(moviePlot, "^(.*?[.!?:])");
				LOGGER.debug("AEBN: tagline(" + movieTagline + ")");
				md.storeMetadata(MediaMetadata.TAGLINE, movieTagline);
			}

			// Actors
			LOGGER.debug("AEBN: parse actors");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=actor]");
			LOGGER.debug("AEBN: {} actors found", elements.size());
			for (Element anchor : elements) {
				String actorid = StrgUtils.substr(anchor.toString(), "starId=(\\d+)");
				String actorname = cleanString(anchor.select("[itemprop=name]").first().text());
				String actordetailsurl = BASE_DATAURL + anchor.attr("href");
				if (!actorname.isEmpty()) {
					LOGGER.debug("AEBN: add actor id({}), name({}), details({})", actorid, actorname, actordetailsurl);
					MediaCastMember cm = new MediaCastMember();
					cm.setType(MediaCastMember.CastType.ACTOR);
					cm.setName(actorname);
					if (!actorid.isEmpty()) {
						cm.setId(actorid);
					}

					// Actor detail page
					try {
						Url starurl = new Url(actordetailsurl);
						InputStream starurlstream = starurl.getInputStream();
						Document stardocument = Jsoup.parse(starurlstream, "UTF-8", "");
						starurlstream.close();
						Elements elements2 = stardocument.getElementsByAttributeValue("class", "StarInfo");
						if (elements2.size() == 0) {
							LOGGER.debug("AEBN: no additional actor details found");
						} else {
							// Actor image
							String actorimage = elements2.select("[itemprop=image]").first().attr("src");
							LOGGER.debug("AEBN: actor image({})", actorimage);
							if (!actorimage.isEmpty()) {
								cm.setImageUrl(actorimage);
							}
							// Actor 'fanart' images
							// unsure if this is ever shown in tmm
							elements2 = stardocument.getElementsByAttributeValue("class", "StarDetailGallery")
									.select("a");
							LOGGER.debug("AEBN: {} gallery images found", elements2.size());
							for (Element thumbnail : elements2) {
								LOGGER.debug("AEBN: add fanart image({})", thumbnail.attr("href"));
								cm.addFanart(thumbnail.attr("href"));
							}
						}
					} catch (Exception e) {
						LOGGER.error("AEBN: Error downloading {}: {}", actordetailsurl, e);
					}

					md.addCastMember(cm);
				}
			}

			// Director
			LOGGER.debug("AEBN: parse director");
			elements = document.getElementsByAttributeValue("id", "md-details").select("[itemprop=director]");
			if (elements.size() > 0) {
				LOGGER.debug("AEBN: {} elements found (should be one!)", elements.size());
				String directorid = StrgUtils.substr(elements.toString(), "directorID=(\\d+)");
				String directorname = cleanString(elements.select("[itemprop=name]").first().text());
				if (!directorname.isEmpty()) {
					MediaCastMember cm = new MediaCastMember(CastType.DIRECTOR);
					cm.setName(directorname);
					if (!directorid.isEmpty()) {
						cm.setId(directorid);
					}
					cm.setImageUrl("");
					md.addCastMember(cm);
					LOGGER.debug("AEBN: add director id({}), name({})", directorid, directorname);
				}
			}

			// Original Title
			// if we have no original title, just copy the title
			if (StringUtils.isBlank(md.getStringValue(MediaMetadata.ORIGINAL_TITLE))) {
				md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, md.getStringValue(MediaMetadata.TITLE));
			}
		} catch (Exception e) {
			LOGGER.error("AEBN: Error parsing {}: {}", options.getResult().getUrl(), e);
		}

		return md;
	}


	/**
	 * Get movie artwork from aebn.net.
	 *
	 */
	@Override
	public List<MediaArtwork> getArtwork(MediaScrapeOptions options) throws Exception {
		LOGGER.debug("AEBN: getArtwork() {}", options);
		List<MediaArtwork> artwork = new ArrayList<MediaArtwork>();
		MediaMetadata md;
		Integer aebnId = 0;

		// get aebnId from options
		if (options.getId(AEBNID) != null) {
			aebnId = Integer.parseInt(options.getId(AEBNID));
			LOGGER.debug("AEBN: got aebnId({}) from options", aebnId);
		}
		if (!isValidAebnId(aebnId)) {
			LOGGER.info("AEBN: could not scrape artwork, no or incorrect aebn id");
			return artwork;
		}

		// Poster
		if ((options.getArtworkType() == MediaArtworkType.ALL)
				|| (options.getArtworkType() == MediaArtworkType.POSTER)) {
			// http://pic.aebn.net/Stream/Movie/Boxcovers/a136807_xlf.jpg
			// http://pic.aebn.net/Stream/Movie/Boxcovers/a136807_bf.jpg
			// http://pic.aebn.net/Stream/Movie/Boxcovers/a136807_160w.jpg
			MediaArtwork ma = new MediaArtwork();
			ma.setProviderId(providerInfo.getId());
			String posterpreviewUrl = AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString()
					+ "_xlf.jpg";
			String posterUrl = posterpreviewUrl;
			ma.setDefaultUrl(posterUrl);
			ma.setPreviewUrl(posterpreviewUrl);
			ma.addImageSize(380, 540,
					AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString() + "_xlf.jpg");
			ma.addImageSize(220, 313,
					AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString() + "_bf.jpg");
			ma.addImageSize(160, 227,
					AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString() + "_160w.jpg");
			ma.setSizeOrder(FanartSizes.MEDIUM.getOrder());
			ma.setLanguage(options.getLanguage().name());
			ma.setType(MediaArtworkType.POSTER);

			// categorize image size and write default url
			prepareDefaultPoster(ma, options);

			artwork.add(ma);
			LOGGER.debug("AEBN: add poster({})", posterpreviewUrl);
		}

		// Poster Back (stored as Disc)
		if ((options.getArtworkType() == MediaArtworkType.ALL) || (options.getArtworkType() == MediaArtworkType.DISC)) {
			// http://pic.aebn.net/Stream/Movie/Boxcovers/a136807_xlb.jpg
			// http://pic.aebn.net/Stream/Movie/Boxcovers/a136807_bb.jpg
			MediaArtwork ma = new MediaArtwork();
			ma.setProviderId(providerInfo.getId());
			String posterpreviewUrl = AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString()
					+ "_xlb.jpg";
			String posterUrl = posterpreviewUrl;
			ma.setDefaultUrl(posterUrl);
			ma.setPreviewUrl(posterpreviewUrl);
			ma.addImageSize(380, 540,
					AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString() + "_xlb.jpg");
			ma.addImageSize(220, 313,
					AebnMetadataProvider.BASE_IMGURL + "/Stream/Movie/Boxcovers/a" + aebnId.toString() + "_bb.jpg");
			ma.setSizeOrder(FanartSizes.MEDIUM.getOrder());
			ma.setLanguage(options.getLanguage().name());
			ma.setType(MediaArtworkType.DISC);
			artwork.add(ma);
			LOGGER.debug("AEBN: add poster({})", posterpreviewUrl);
		}

		// Movie scenes (stored as Background)
		if ((options.getArtworkType() == MediaArtworkType.ALL)
				|| (options.getArtworkType() == MediaArtworkType.BACKGROUND)) {

			// Need to scrape movie metadata first (see getMetaData() -> Fanart/Background)
			md = getMetadata(options);
			LOGGER.debug("AEBN: return from media metadata scraping");
			aebnId = Integer.parseInt(options.getId(AebnMetadataProvider.AEBNID));
			int i = 1;
			while (!md.getStringValue("backgroundUrl" + Integer.valueOf(i).toString()).isEmpty()) {
				MediaArtwork ma = new MediaArtwork();
				ma.setProviderId(providerInfo.getId());
				String backgroundUrl = md.getStringValue("backgroundUrl" + Integer.valueOf(i).toString());
				ma.setDefaultUrl(backgroundUrl);
				ma.setPreviewUrl(backgroundUrl.replace("_179_101", ""));
				ma.addImageSize(179, 101, backgroundUrl);
				ma.addImageSize(120, 68, backgroundUrl.replace("_179_101", ""));
				ma.setSizeOrder(FanartSizes.SMALL.getOrder());
				ma.setLanguage(options.getLanguage().name());
				ma.setType(MediaArtworkType.BACKGROUND);
				// ma.setLikes(image.likes); // unsupported

				// categorize image size and write default url
				prepareDefaultFanart(ma, options);

				artwork.add(ma);
				LOGGER.debug("AEBN: add background({})", backgroundUrl);
				i++;
			}
		}
		return artwork;
	}


	private void prepareDefaultPoster(MediaArtwork ma, MediaScrapeOptions options) {
		for (MediaArtwork.ImageSizeAndUrl image : ma.getImageSizes()) {
			// LARGE
			if (image.getWidth() >= 1000) {
				if (options.getPosterSize().getOrder() >= MediaArtwork.PosterSizes.LARGE.getOrder()) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.PosterSizes.LARGE.getOrder());
					break;
				}
				continue;
			}
			// BIG
			if (image.getWidth() >= 500) {
				if (options.getPosterSize().getOrder() >= MediaArtwork.PosterSizes.BIG.getOrder()) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.PosterSizes.BIG.getOrder());
					break;
				}
				continue;
			}
			// MEDIUM
			if (image.getWidth() >= 342) {
				if (options.getPosterSize().getOrder() >= MediaArtwork.PosterSizes.MEDIUM.getOrder()) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.PosterSizes.MEDIUM.getOrder());
					break;
				}
				continue;
			}
			// SMALL
			if (image.getWidth() >= 185) {
				if (options.getPosterSize() == MediaArtwork.PosterSizes.SMALL) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.PosterSizes.SMALL.getOrder());
					break;
				}
				continue;
			}
		}
	}


	private void prepareDefaultFanart(MediaArtwork ma, MediaScrapeOptions options) {
		for (MediaArtwork.ImageSizeAndUrl image : ma.getImageSizes()) {
			// LARGE
			if (image.getWidth() >= 1920) {
				if (options.getFanartSize().getOrder() >= MediaArtwork.FanartSizes.LARGE.getOrder()) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.FanartSizes.LARGE.getOrder());
					break;
				}
				continue;
			}
			// MEDIUM
			if (image.getWidth() >= 1280) {
				if (options.getFanartSize().getOrder() >= MediaArtwork.FanartSizes.MEDIUM.getOrder()) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.FanartSizes.MEDIUM.getOrder());
					break;
				}
				continue;
			}
			// SMALL
			if (image.getWidth() >= 300) {
				if (options.getFanartSize().getOrder() >= MediaArtwork.FanartSizes.SMALL.getOrder()) {
					ma.setDefaultUrl(image.getUrl());
					ma.setSizeOrder(MediaArtwork.FanartSizes.SMALL.getOrder());
					break;
				}
				continue;
			}
		}
	}


	/**
	 * Maps scraper genres to internal TMM genres.
	 *
	 * @param genre
	 *            genre name to map
	 * @return MediaGenres genre
	 */
	private MediaGenres getTmmGenre(String genre) {
		LOGGER.debug("AEBN: getTmmGenre() {}", genre);
		MediaGenres g = null;
		if (genre.isEmpty()) {
			return g;
		}

		if (g == null) {
			g = MediaGenres.getGenre(genre);
		}
		return g;
	}


	/**
	 * Sanitizes a string (remove non breaking spaces and trim).
	 *
	 * @param oldString
	 *            string to clean
	 * @return the cleaned string
	 */
	private String cleanString(String oldString) {
		if (StringUtils.isEmpty(oldString)) {
			return "";
		}
		// remove non breaking spaces
		String newString = oldString.replace(String.valueOf((char) 160), " ");
		// and trim
		return StringUtils.trim(newString);
	}


	/**
	 * Sanitizes the search query by removing
	 * <ul>
	 * <li>stop words (a, the, der, die, das, la, le, il),
	 * <li>digit values (e.g. year),
	 * <li>punctuation marks,
	 * <li>multiple spaces.
	 * </ul>
	 *
	 * @param query
	 *            search query string to clean
	 * @return the cleaned search query string
	 */
	private String cleanSearchQuery(String query) {
		if (StringUtils.isEmpty(query)) {
			return "";
		}
		// use default function first
		// String newString = MetadataUtil.removeNonSearchCharacters(oldString);

		// prepare for easier regex ...
		String newString = " " + query + " ";

		// ... and sanitize, TODO: multiple stopwords
		newString = newString.replaceAll("(?i)( a | the | der | die | das | la | le | il |\\(\\d+\\))", " ");
		newString = newString.replaceAll("[\\.#&:!?,]", " ");
		newString = newString.replaceAll("\\s{2,}", " ");
		return StringUtils.trim(newString);
	}


	/**
	 * Validates an AEBN id.
	 *
	 * @param aebnId
	 *            the AEBN id to be validated
	 * @return true if is a valid AEBN id, false otherwise
	 */
	private static boolean isValidAebnId(Integer aebnId) {
		return ((aebnId != null) && (aebnId.intValue() > 0) && (aebnId.intValue() < 1000000));
	}

}
