//
// This file is part of Smoothie.
//
// Copyright (C) 2013-2015 Flowy Apps GmbH <hello@flowyapps.com>
//
// Smoothie is free software: you can redistribute it and/or modify it under the
// terms of the GNU Lesser General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option) any
// later version.
//
// Smoothie is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
// A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
// details.You should have received a copy of the GNU Lesser General Public
// License along with Smoothie.  If not, see <http://www.gnu.org/licenses/>.
//
////////////////////////////////////////////////////////////////////////////////

"use strict";

var StackController = require("StackController");

var regexExternalUrl = /^\w+:|\/\//; 

exports = function() {
    StackController.call(this);
};

exports.prototype = Object.create(StackController.prototype);

Object.defineProperty(exports.prototype, "currentReadyState", {'get': function() {
    return this.currentId ? this.$stack[this.currentId].readyState : null;
}});

exports.prototype.$setReadyStateForId = function(state, id) {
    this.$stack[id].readyState = state;
    this.node.dispatchEvent(new CustomEvent("readystatechange", {"detail":id}));
};

exports.prototype.loadId = function(id, fragment, callback) {
    var request;   
    request = new XMLHttpRequest();
    if (!this.$stack[id])
        this.$stack[id] = {};
    if (!this.$stack[id].readyState) {
        this.$setReadyStateForId("loading", id);
        if (this.$stack[id].node===undefined) {
            request.onload = onLoad.bind(this);
            request.open("GET", "/views/"+id+".php", true);
            request.send();
        }
        require("views/"+id, afterRequire.bind(this));
    }
    else {
        this.$setReadyStateForId(this.$stack[id].readyState, id);
        setTimeout(function(){if (callback) callback(id, fragment);}, 0);
    }

    function onLoad() {
        var clone;
        if (request.status == 200) {
            clone = this.node.cloneNode(false);	
            clone.innerHTML = request.responseText;
            this.$stack[id].node = clone.removeChild(clone.firstElementChild);
            if (this.$stack[id].controller!==undefined)
                finalize.call(this);
        }
    }

    function afterRequire(module) {
        this.$stack[id].controller = module;
        if (module.loading)
            module.loading();
        if (this.$stack[id].node!==undefined)
            finalize.call(this);
    }

    function finalize() {
        this.node.addEventListener("readystatechange", onReadystatechange.bind(this), false);
        this.$setReadyStateForId("ready", id);
        this.node.dispatchEvent(new CustomEvent("add", {'detail':this.$stack[id].node}));
        this.$stack[id].node.dispatchEvent(new Event("add"));
        if (callback)
            callback(id, fragment);
    }

    function onReadystatechange(evt) {
        if (evt.detail == id) {
            switch (this.$stack[id].readyState) {
                case "ready":
                    if (this.$stack[id].controller.ready)
                        this.$stack[id].controller.ready(this.$stack[id].node);
                    break;
                case "interactive":
                    if (this.$stack[id].controller.interactive)
                        this.$stack[id].controller.interactive(this.$stack[id].node);
                    break;
                case "complete":
                    if (this.$stack[id].controller.complete)
                        this.$stack[id].controller.complete(this.$stack[id].node);
                    this.node.removeEventListener("readystatechange", onReadystatechange);
                    break;
                default:
                    throw "NavigationController: unknown readyState '"+document.readyState+"'";
            }
        }
    }
};

exports.prototype.showId = function(id, fragment) {
    var loadLock, doScrollToFragment, watchForScrollTimeout, scripts, script, images, i;
    loadLock = 0;
    doScrollToFragment = true;
    watchForScrollTimeout = null;

    if (this.$stack[id].readyState == "ready") {
        this.hideId(this.currentId);
        this.node.appendChild(this.$stack[id].node);
        this.currentId = id;
        this.$setReadyStateForId("interactive", id);

        scripts = this.$stack[id].node.getElementsByTagName("SCRIPT");
        for (i = 0; i < scripts.length; i++) {
            script = document.createElement("SCRIPT");
            script.text = scripts[i].text;
            if (scripts[i].src !== "") {
                loadLock++;
                script.addEventListener("load", onLoadOrError.bind(this));
                script.addEventListener("error", onLoadOrError.bind(this));
                script.src = scripts[i].src;
            }
            scripts[i].parentNode.replaceChild(script, scripts[i]);
        }
        images = this.$stack[id].node.getElementsByTagName("IMG");
        for (i = 0; i < images.length; i++) {
            if ((images[i].src !== "") && !images[i].complete) {
                loadLock++;
                images[i].addEventListener("load", onLoadOrError.bind(this));
                images[i].addEventListener("error", onLoadOrError.bind(this));
            }
        }
        loadLock++;
        window.scrollTo(0,0);
        onLoadOrError.call(this);
    }
    else {
        StackController.prototype.showId.call(this, id);
        this.autoplayVideos();
        window.scrollTo(0,0);
        this.scrollToFragment(fragment);
    
    }
    // NOTE This watcher stops scrollToFragment calls after a manual scroll action
    //      occurred. This should prevent unwanted "scroll jumps" while loading
    //      a view.
    function onScroll(evt) {
        console.log("watchForScroll");
        doScrollToFragment = false;
    }

    function onLoadOrError(evt) {
        // NOTE Remove event listeners, which aren't needed anymore, to allow garbage collection.
        if (evt) {
            evt.target.removeEventListener("load", onLoadOrError.bind(this));
            evt.target.removeEventListener("error", onLoadOrError.bind(this));
        }
        // NOTE We want that only manual scroll actions block further scrollToFragment
        //      calls. Therefore we have to disable the scroll listener, because
        //      scrollToFragment also causes the scroll event to be fired. 
        window.removeEventListener("scroll", onScroll);
        clearTimeout(watchForScrollTimeout);
        if (doScrollToFragment)
            this.scrollToFragment(fragment);
        if (--loadLock === 0) {
            this.autoplayVideos();
            this.$setReadyStateForId("complete", id);
            this.node.dispatchEvent(new CustomEvent("show", {'detail':this.$stack[id].node}));
            this.$stack[id].node.dispatchEvent(new Event("show"));
        }
        else {
            // NOTE Scrolling doesn't happen immediately, so we have to disable the
            //      onScroll listener until the next frame starts. 
            watchForScrollTimeout = setTimeout(function(){window.addEventListener("scroll", onScroll, false);}, 0);
        }
    }
};

exports.prototype.autoplayVideos = function() {
    var videos, i;
    videos = this.node.querySelectorAll("video[autoplay]");
    for (i=0; i<videos.length; i++)
        videos[i].play();
};

exports.prototype.scrollToFragment = function(fragment) {
    var anchor, top, left;
    if (fragment) {
        anchor = this.node.getElementsByTagName("A")[fragment];
        // NOTE The attribute check is necessary to ensure that the anchor
        //      really has an explicitely defined id. Otherwise a numeric
        //      fragment might match with some auto-generated anchor id. 
        if (anchor && (anchor.getAttribute("id") == fragment)) {
            // NOTE getBoundingClientRect calculates the wrong size, when
            //      loading a view from cache in FF41. Therefore we use go
            //      the oldschool offsetTop/Left way here. 
            top = 0;
            left = 0;
            do {
                top += anchor.offsetTop;
                left += anchor.offsetLeft;
                anchor = anchor.offsetParent;
            } while (anchor);
            window.scrollTo(left, top);
        }
    }
};

// TODO Handle query strings somehow instead of just ignoring them.
// TODO Apply pushState after successful loading
// TODO Move HEAD request into loadId
exports.prototype.openUrl = function(url, noPushState) {
    var purl, request;
    if (regexExternalUrl.test(url)) {
        console.log("external url");
        location.href = url;
    }
    else {
        if (!noPushState)
            history.pushState(null, null, url);
        purl = this.parseUrl(url);
        if (purl[1] != this.currentId) {
            // NOTE The head request is necessary to log the page impressions.
            if (!noPushState) {
                request = new XMLHttpRequest();
                request.open("HEAD", url, true);
                request.send();
            }
            this.loadId(purl[1], purl[3], this.showId.bind(this));
        }
        else {
            this.scrollToFragment(purl[3]);
        }
    }
};

exports.prototype.parseUrl = function(url) {
    var purl;
    purl = url.match(/(?:^(?:\w+:)?\/\/[^\/]*)?\/?([^\?#]*[^\/\?#])?\/?(?:\?([^#]*))?(?:#(.*))?/);
    purl[1] = purl[1].toLowerCase();
    return purl;
};

exports.prototype.watchAnchorsInsideNode = function(node) {
    node.addEventListener("click", onClick.bind(this), false);

    function onClick(evt) {
        var node = evt.target;
        if (evt.defaultPrevented)
            return;
        while (node && node.tagName!="A")
            node = node.parentNode;
        if (node && node.hasAttribute("href")) {
            evt.preventDefault();
            evt.stopPropagation();
            this.openUrl(node.getAttribute("href"));
        }
    }
};

exports.prototype.interactive = function(node) {
    StackController.prototype.interactive.call(this, node);
    this.watchAnchorsInsideNode(this.node);
    // NOTE Not pushing history state
    this.openUrl(location.pathname+location.search+location.hash, true);

    window.addEventListener("popstate", function(evt) {
        // NOTE Not pushing history state
        this.openUrl(location.pathname+location.search+location.hash, true);
    }.bind(this), false);
};
