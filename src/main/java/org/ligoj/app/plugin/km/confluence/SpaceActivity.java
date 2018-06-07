package org.ligoj.app.plugin.km.confluence;

import java.io.Serializable;

import org.ligoj.app.iam.SimpleUser;

import lombok.Getter;
import lombok.Setter;

/**
 * A Confluence activity. Note there is no REST API for Confluence's activity.
 */
@Getter
@Setter
public class SpaceActivity implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The raw moment as Confluence has calculated, translated into the locale
	 * of user used to query Confluence. May not be the one of the principal
	 * user.
	 */
	private String moment;

	/**
	 * Author, built either from the IAM provider if found, either from the
	 * Confluence's data.
	 */
	private SimpleUser author;

	/**
	 * Author avatar URL
	 */
	private String authorAvatar;

	/**
	 * The related updated page name
	 */
	private String page;

	/**
	 * The related updated page URL
	 */
	private String pageUrl;

}
