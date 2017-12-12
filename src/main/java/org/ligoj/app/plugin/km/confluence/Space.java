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
	 * The last activity on Confluence.
	 */
	private SpaceActivity activity;

}
