package org.ligoj.app.plugin.km.confluence;

import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.CurlRequest;
import org.ligoj.app.resource.plugin.HttpResponseCallback;

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
