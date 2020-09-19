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

require("shim/w3cDomEvents"); // IE11

exports = function() {
    this.$stack = {};
    this.currentId = null;
    this.node = null;
};

exports.prototype.interactive = function(node) {
    var child, id, next;
    this.node = node;
    child = this.node.firstChild;
    while (child) {
        id = child.getAttribute && child.getAttribute("data-stack-id");
        next = child.nextSibling;
        if (id)
            this.addIdWithNodeAndController(id, child, undefined);
        if (child != this.node.firstElementChild)
                this.node.removeChild(child);
        child = next;
    }
};

exports.prototype.addIdWithNodeAndController = function(id, node, controller) {
    this.$stack[id] = {'node':node, 'controller':controller};
    this.node.dispatchEvent(new CustomEvent("add", {'detail':this.$stack[id].node}));
    this.$stack[id].node.dispatchEvent(new Event("add"));
};

exports.prototype.removeId = function(id) {
    var ctrl;
    if (this.$stack[id]) {
        ctrl = this.$stack[id].controller;
        this.hideId(id);
        delete this.$stack[id];
        this.node.dispatchEvent(new CustomEvent("remove", {'detail':this.$stack[id].node}));
        this.$stack[id].node.dispatchEvent(new Event("remove"));
    }
};

exports.prototype.showId = function(id) {
    if (this.$stack[id] && this.$stack[id].node) {
        this.hideId(this.currentId);
        this.node.appendChild(this.$stack[id].node);
        this.currentId = id;
        this.node.dispatchEvent(new CustomEvent("show", {'detail':this.$stack[id].node}));
        this.$stack[id].node.dispatchEvent(new Event("show"));
    }
};

exports.prototype.hideId = function(id) {
    if (this.$stack[id] && this.$stack[id].node && (this.$stack[id].node.parentNode == this.node)) {
        this.node.dispatchEvent(new CustomEvent("hide", {'detail':this.$stack[id].node}));
        this.$stack[id].node.dispatchEvent(new Event("hide"));
        this.node.removeChild(this.$stack[id].node);
        this.currentId = null;
    }
};
