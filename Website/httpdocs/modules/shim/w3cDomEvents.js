// SPEC http://www.w3.org/TR/dom/#events
// FIXES IE9, IE10, IE11

"use strict";

try {
    new window.Event("TestEvent");
}
catch (e) {
    console.warn("w3cDomEvents: creating Event object failed - using shim ("+e+")");
    try {
        // NOTE Check whether document can create Event objects or not.
        document.createEvent("Event");

        var Event = function(name, params) {
            params = params || {bubbles:false, cancelable: false};
            var evt = document.createEvent("Event");
            evt.initEvent(name, params.bubbles, params.cancelable);
            return evt;
        };
        Event.prototype = window.Event.prototype;
        // NOTE This could be a bad idea. Further testing needed.
        window.$Event = window.Event;
        window.Event = Event;
    }
    catch (e) {
        console.error("w3cDomEvents: unable to create Event shim ("+e+")");
    }
}

try {
    new window.CustomEvent("CustomTestEvent");
}
catch (e) {
    console.warn("w3cDomEvents: Creating CustomEvent object failed - using shim ("+e+")");
    try {
        // NOTE Check whether document can create CustomEvent objects or not.
        document.createEvent("CustomEvent");

        var CustomEvent = function(name, params) {
            params = params || {bubbles:false, cancelable:false, detail:undefined};
            var evt = document.createEvent('CustomEvent');
            evt.initCustomEvent(name, params.bubbles, params.cancelable, params.detail);
            return evt;
        };
        CustomEvent.prototype = window.CustomEvent.prototype;
        // NOTE This could be a bad idea. Further testing needed.
        window.$CustomEvent = window.CustomEvent;
        window.CustomEvent = CustomEvent;
    }
    catch (e) {
        try {
        // NOTE Check whether document can create Event objects or not.
            document.createEvent("Event");

            console.warn("w3cDomEvents: using Event for CustomEvent shim ("+e+")");
            var CustomEvent = function(name, params) {
                params = params || {bubbles:false, cancelable:false, detail:undefined};
                var evt = document.createEvent('Event');
                evt.initEvent(name, params.bubbles, params.cancelable);
                evt.detail = params.detail;
                return evt;
            };
            CustomEvent.prototype = window.Event.prototype;
            window.CustomEvent = CustomEvent;
        }
        catch (e) {
            console.error("w3cDomEvents: unable to create CustomEvent shim ("+e+")");
        }
    }
}
