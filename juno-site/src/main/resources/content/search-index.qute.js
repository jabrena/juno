---
title: "Search index"
layout: null
---
{! Generated at build time from every page's title, description and text. Loaded on first use by the search box
   in templates/partials/juno/topbar.html. A JavaScript array literal, so the trailing commas are valid. !}
window.JUNO_SEARCH_INDEX = [
{#for p in site.pages}
{#if p.source.isTargetHtml && !p.source.isSiteIndex}
  {
    "url": "{p.url.path}",
    "title": "{p.title.jsEscape}",
    "description": "{p.description.jsEscape}",
    "text": "{p.content.stripHtml(true).jsEscape}"
  },
{/if}
{/for}
];
