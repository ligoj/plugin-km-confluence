/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.km.confluence;

import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;

/**
 * Confluence Curl processor.
 */
public class ConfluenceCurlProcessor extends CurlProcessor {

	/**
	 * Special callback for Confluence login check.
	 */
	public static final HttpResponseCallback LOGIN_CALLBACK = new ConfluenceLoginHttpResponseCallback();

	@Override
	protected boolean process(final CurlRequest request) {
		// Add headers for SSO
		request.getHeaders().put("X-Atlassian-Token", "nocheck");
		return super.process(request);
	}

}
