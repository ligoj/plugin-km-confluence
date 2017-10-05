package org.ligoj.app.plugin.km.confluence;

import org.ligoj.bootstrap.core.model.AbstractNamedBusinessEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * A Confluence space.
 */
@Getter
@Setter
public class Space extends AbstractNamedBusinessEntity<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The last activity on Confluence.
	 */
	private SpaceActivity activity;

}
