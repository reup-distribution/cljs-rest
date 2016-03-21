#! /usr/bin/env phantomjs

// Convenience function: wait for some value from lookup fn, call ready
// callback with value when available.
function waitFor(lookup, ready) {
    var result, interval;

    interval = setInterval(function() {
        result = lookup();

        if (result !== undefined && result !== null) {
            clearInterval(interval);
            ready(result);
        }
    }, 100);
};

var server = require('webserver').create();

server.listen(4001, function(request, response) {
    response.statusCode = 200;
    response.write('<!DOCTYPE html><html><body></body></html>');
    response.close();
});

var page = require('webpage').create();
page.settings.localToRemoteUrlAccessEnabled = true;
page.settings.webSecurityEnabled = false;

page.onConsoleMessage = function(x) {
    console.log(x);
};

page.open('http://localhost:4001/', function(status) {
    page.injectJs(phantom.args[0]);

    // Wait for tests to complete, then exit with appropriate error code.
    waitFor(
        function() {
            return page.evaluate(function() {
                if (typeof cljs_rest !== 'undefined') {
                    var val = cljs_rest.runner.complete();

                    if (val) {
                        return cljs.core.clj__GT_js(val);
                    }
                }
            });
        },
        function(result) {
            var success = result.fail === 0 && result.error === 0;
            var exitCode = success ? 0 : 1;
            page.close();
            phantom.exit(exitCode);
        }
    );

    // Run tests.
    page.evaluate(function() {
        if (typeof cljs_rest !== 'undefined') {
            cljs_rest.runner.run();
        }
    });
});
