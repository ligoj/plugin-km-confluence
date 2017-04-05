define(['cascade'], function ($cascade) {
	var current = {
		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:km:confluence:space', 'service/km/confluence/');
		},

		/**
		 * Render Confluence key.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:km:confluence:space');
		},

		/**
		 * Render Confluence data.
		 */
		renderFeatures: function (subscription) {
			var result = current.$super('renderServicelink')('home', subscription.parameters['service:km:confluence:url'] + '/display/' + encodeURIComponent(subscription.parameters['service:km:confluence:space']), 'service:km:confluence:space', undefined, ' target="_blank"');
			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:km:help');
			return result;
		},

		/**
		 * Render a global without context.
		 * @param {jquery} $target Target container where global will be rendered
		 */
		renderGlobal: function ($view, configuration) {
			_('confluence-links').closest('.global-configuration').remove();
			_('extra-menu').append('<img class="nav-icon visible-retracted" src="main/plugin/km/confluence/img/confluence.png" alt="confluence" title="" data-toggle="tooltip" data-container="body" data-original-title="Confluence">').append($view);
			_('confluence-links').prepend(current.$super('toIcon')({
				id: 'service:km:confluence'
			}, 'x64'));
			$view.find('.confluence-login').attr('href', configuration.node.parameters['service:km:confluence:url']);
			$view.find('.confluence-refresh').on('click', function () {
				current.refreshLinks($view, configuration);
			});
			current.refreshLinks($view, configuration);
		},

		/**
		 * Render a global without context.
		 * @param {jquery} $target Target container where global will be rendered
		 */
		refreshLinks: function ($view, configuration) {
			var $errors = $view.find('.confluence-warning').addClass('hidden');
			var $links = $view.find('.confluence-resolved-links').removeClass('hidden').empty();
			$cascade.appendSpin($links.empty());

			$.ajax({
				contentType: 'application/json; charset=utf-8',
				dataType: 'json',
				url: configuration.node.parameters['service:km:confluence:url'] + '/' + configuration.parameters.query,
				type: 'GET',
				crosdomain: true,
				global: false,
				success: function (data) {
					current.fillConfluenceLinks(data, $links);
				},
				error: function () {
					// Current user is not loaded, display a warning and login message
					$errors.removeClass('hidden');
				},
				complete: function () {
					$cascade.removeSpin($links);
				}
			});
		},

		fillConfluenceLinks: function (data, $target) {
			var spaces = data.spaces;
			for (var index = 0; index < spaces.length; index++) {
				var space = spaces[index];
				$target.append($('<li><a target="blank" href="' + space.link[1].href + '"><i class="fa fa-chevron-right"></i>' + space.name + '</a></li>'));
			}
		}
	};
	return current;
});