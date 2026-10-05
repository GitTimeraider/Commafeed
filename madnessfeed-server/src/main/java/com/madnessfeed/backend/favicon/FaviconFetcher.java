package com.madnessfeed.backend.favicon;

import com.madnessfeed.backend.model.Feed;

public interface FaviconFetcher {

    Favicon fetch(Feed feed);
}
