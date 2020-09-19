"use strict";

var NavigationController = require("NavigationController");

function setMailtoAnchorsInsideNode(node) {
    var anchors, i;
    anchors = node.querySelectorAll("a.E");
    for (i=0; i<anchors.length; i++)
        anchors[i].href += "@letorbi.com";
}

exports.loading = function() {
    var blockPopstateEvent;
    blockPopstateEvent = document.readyState!="complete";
    // NOTE Chrome fires the onpopstate event also when the document has been
    //      loaded. This is not intended, so we block popstate events until the
    //      the first event loop cicle after document has been loaded.
    window.addEventListener("load", function() {
        setTimeout(function(){blockPopstateEvent=false;},0);
    }, false);
    // NOTE However, since the document's readyState is already on "complete"
    //      when Chrome fires onpopstate erroneously, we allow opopstate events,
    //      which have been fired before document loading has been finished.
    window.addEventListener("popstate", function(evt) {
        if (blockPopstateEvent && document.readyState=="complete") {
            evt.preventDefault();
            evt.stopImmediatePropagation();
        }
    }, false);
};

exports.interactive = function(node) {
    exports.viewport = new NavigationController();
    exports.viewport.interactive(document.getElementById("Viewport"));

    exports.viewport.node.addEventListener("add", function(evt) {
        setMailtoAnchorsInsideNode(evt.detail);
    });
};
