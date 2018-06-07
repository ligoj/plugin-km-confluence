/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.km.confluence;

import org.ligoj.app.resource.plugin.OnlyRedirectHttpResponseCallback;

/**
 * Confluence login response handler.
 */
public class ConfluenceLoginHttpResponseCallback extends OnlyRedirectHttpResponseCallback {

	@Override
	protected boolean acceptLocation(final String location) {
		return super.acceptLocation(location) && !location.endsWith("dologin.action");
	}
}